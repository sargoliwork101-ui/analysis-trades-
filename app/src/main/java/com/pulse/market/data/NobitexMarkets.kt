package com.pulse.market.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/**
 * «آیا این کوین در نوبیتکس معامله می‌شود؟»
 *
 * از API عمومی نوبیتکس (`/market/stats`) استفاده می‌شود: اگر بازارِ نماد در برابر
 * تومان یا تتر وجود داشته باشد، پاسخ برای آن جفت آمار برمی‌گرداند. نتیجه ۲۴ ساعت
 * روی گوشی کش می‌شود تا برای هر بار باز کردن صفحه درخواست تازه نرود.
 *
 * هیچ کلید یا اطلاعات کاربری فرستاده نمی‌شود؛ فقط نماد عمومی کوین.
 */
object NobitexMarkets {

    private const val PREF = "pulse_nobitex"
    private const val TTL_MS = 24L * 60 * 60 * 1000
    private const val ENDPOINT = "https://api.nobitex.ir/market/stats"

    /** وضعیت پشتیبانی نماد در نوبیتکس */
    enum class State { AVAILABLE, UNAVAILABLE, UNKNOWN }

    data class Result(
        val state: State,
        /** جفت‌های موجود مثل TMN و USDT */
        val pairs: List<String> = emptyList(),
        val checkedAt: Long = 0L
    ) {
        val label: String
            get() = when (state) {
                State.AVAILABLE -> if (pairs.isEmpty()) "در نوبیتکس معامله می‌شود"
                else "در نوبیتکس معامله می‌شود (${pairs.joinToString("، ")})"
                State.UNAVAILABLE -> "در نوبیتکس معامله نمی‌شود"
                State.UNKNOWN -> "وضعیت نوبیتکس مشخص نشد"
            }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** فقط نمادهای ساده‌ی حروف/عدد به API فرستاده می‌شوند. */
    internal fun normalizeSymbol(symbol: String): String =
        symbol.trim().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(12)

    /** خواندن نتیجه‌ی کش‌شده‌ی معتبر (بدون شبکه) */
    fun cached(context: Context, symbol: String, now: Long = System.currentTimeMillis()): Result? {
        val key = normalizeSymbol(symbol).ifEmpty { return null }
        val raw = prefs(context).getString(key, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val at = obj.optLong("at", 0L)
            if (!TimePolicy.isFresh(now, at, TTL_MS)) return null
            val state = State.valueOf(obj.optString("state", State.UNKNOWN.name))
            val pairs = obj.optString("pairs").split(',').filter { it.isNotBlank() }
            Result(state, pairs, at)
        }.getOrNull()
    }

    /**
     * بررسی نماد در نوبیتکس. اگر نتیجه‌ی تازه در کش باشد، همان برگردانده می‌شود.
     * خطای شبکه = UNKNOWN (و کش نمی‌شود) تا دفعه‌ی بعد دوباره تلاش شود.
     */
    suspend fun check(
        context: Context,
        symbol: String,
        force: Boolean = false
    ): Result = withContext(Dispatchers.IO) {
        val key = normalizeSymbol(symbol)
        if (key.isEmpty()) return@withContext Result(State.UNKNOWN)
        if (!force) cached(context, key)?.let { return@withContext it }
        val result = try {
            val body = Http.getText(
                url = "$ENDPOINT?srcCurrency=$key&dstCurrency=rls,usdt",
                userAgent = Http.UA_DESKTOP,
                maxBytes = 256L * 1024
            )
            parse(body, key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result(State.UNKNOWN)
        }
        if (result.state != State.UNKNOWN) {
            val now = System.currentTimeMillis()
            prefs(context).edit().putString(
                key,
                JSONObject()
                    .put("at", now)
                    .put("state", result.state.name)
                    .put("pairs", result.pairs.joinToString(","))
                    .toString()
            ).apply()
            return@withContext result.copy(checkedAt = now)
        }
        result
    }

    /**
     * پاسخ نوبیتکس به شکل {"status":"ok","stats":{"btc-rls":{...},"btc-usdt":{...}}} است.
     * نبودن کلیدِ نماد یعنی آن بازار روی نوبیتکس نیست.
     */
    internal fun parse(body: String, symbol: String): Result {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return Result(State.UNKNOWN)
        if (!root.optString("status").equals("ok", true)) return Result(State.UNKNOWN)
        val stats = root.optJSONObject("stats") ?: return Result(State.UNKNOWN)
        val pairs = mutableListOf<String>()
        val keys = stats.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            if (!name.startsWith("$symbol-", ignoreCase = true)) continue
            val quote = name.substringAfter('-').lowercase(Locale.ROOT)
            val market = stats.optJSONObject(name) ?: continue
            // بازارهای غیرفعال هم گاهی برمی‌گردند؛ نبودن قیمت یعنی عملاً معامله‌ای نیست.
            val hasPrice = listOf("latest", "bestSell", "bestBuy", "dayClose").any { field ->
                market.optString(field).toDoubleOrNull()?.let { it > 0.0 } == true
            }
            if (!hasPrice) continue
            pairs += when (quote) {
                "rls" -> "تومان"
                "usdt" -> "تتر"
                else -> quote.uppercase(Locale.ROOT)
            }
        }
        return if (pairs.isEmpty()) Result(State.UNAVAILABLE)
        else Result(State.AVAILABLE, pairs.distinct())
    }
}
