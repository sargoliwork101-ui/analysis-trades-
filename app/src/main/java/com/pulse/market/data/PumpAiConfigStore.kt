package com.pulse.market.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * یک سرویس پشتیبانِ هوش مصنوعی: آدرس، مدل و کلید کاملاً مستقل از سرویس اصلی.
 *
 * چرا: اگر سرویس اصلی (مثلاً به‌خاطر محدودیت جغرافیایی، اتمام سهمیه یا خطای ۴۰۱)
 * جواب ندهد، برنامه بدون دخالت کاربر سراغ سرویس بعدی می‌رود.
 */
data class PumpAiBackup(
    val endpoint: String = "",
    val model: String = "",
    val apiKey: String = "",
    val providerSearch: Boolean = false,
    /** خاموش‌کردن یک پشتیبان بدون پاک‌کردن کلیدش. */
    val active: Boolean = true
) {
    /** آیا کاربر چیزی در این ردیف نوشته است؟ (برای تشخیص ردیف خالی) */
    val configured: Boolean
        get() = endpoint.isNotBlank() || model.isNotBlank() || apiKey.isNotBlank()

    fun matches(preset: PumpAiConfig.Preset): Boolean =
        endpoint.trim().trimEnd('/').equals(preset.endpoint, ignoreCase = true) &&
                model.trim().equals(preset.model, ignoreCase = true)
}

/**
 * تنظیمات سراسریِ سرویس هوش مصنوعی پامپ.
 * کلید خارج از WidgetConfig/بکاپ و به‌صورت AES-GCM با Android Keystore ذخیره می‌شود.
 *
 * علاوه بر سرویس اصلی، تا [MAX_BACKUPS] سرویس پشتیبان هم می‌شود تعریف کرد؛
 * [chain] ترتیب واقعی تلاش است (اصلی، بعد پشتیبان‌های فعال و کامل).
 */
data class PumpAiConfig(
    val enabled: Boolean = false,
    val endpoint: String = "",
    val model: String = "",
    val apiKey: String = "",
    val providerSearch: Boolean = true,
    /** سرویس‌های جایگزین؛ فقط وقتی استفاده می‌شوند که سرویس قبلی پاسخ ندهد. */
    val backups: List<PumpAiBackup> = emptyList()
) {
    val endpointValid: Boolean
        get() = isValidEndpoint(endpoint)

    val insecureKeyTransport: Boolean
        get() = endpoint.trim().startsWith("http://", ignoreCase = true) && apiKey.isNotBlank()

    val isReady: Boolean
        get() = endpointValid && model.isNotBlank() && !insecureKeyTransport

    /** همین سرویس به‌تنهایی (بدون پشتیبان‌ها) — واحدِ کارِ لایه‌ی شبکه. */
    val primary: PumpAiConfig
        get() = if (backups.isEmpty()) this else copy(backups = emptyList())

    /** یک پشتیبان را به یک پیکربندی مستقل تبدیل می‌کند. */
    fun asConfig(backup: PumpAiBackup): PumpAiConfig = PumpAiConfig(
        enabled = enabled,
        endpoint = backup.endpoint,
        model = backup.model,
        apiKey = backup.apiKey,
        providerSearch = backup.providerSearch
    )

    /** همه‌ی سرویس‌های تنظیم‌شده (حتی ناقص) به‌ترتیب اولویت؛ برای تست و پیام خطا. */
    val configuredChain: List<PumpAiConfig>
        get() = buildList {
            add(primary)
            for (backup in backups) {
                if (backup.active && backup.configured) add(asConfig(backup))
            }
        }

    /** ترتیب واقعی تلاش: فقط سرویس‌هایی که آدرس و مدل سالم دارند. */
    val chain: List<PumpAiConfig>
        get() = configuredChain.filter { it.isReady }

    /** حداقل یک سرویس (اصلی یا پشتیبان) آماده‌ی استفاده است. */
    val anyReady: Boolean
        get() = chain.isNotEmpty()

    /** شمار سرویس‌های پشتیبانِ فعال و کامل. */
    val readyBackupCount: Int
        get() = (chain.size - (if (primary.isReady) 1 else 0)).coerceAtLeast(0)

    /** آیا این پیکربندی دقیقاً روی یکی از سرویس‌های آماده تنظیم شده است؟ */
    fun matches(preset: Preset): Boolean =
        endpoint.trim().trimEnd('/').equals(preset.endpoint, ignoreCase = true) &&
                model.trim().equals(preset.model, ignoreCase = true)

    /** تنظیمات پیش‌فرض یک سرویس آماده (Gemini/Claude/OpenAI) */
    data class Preset(
        val id: String,
        val title: String,
        val endpoint: String,
        val model: String,
        val hint: String,
        /** آیا جست‌وجوی وب سمت سرویس برای این ارائه‌دهنده معنا دارد؟ */
        val providerSearch: Boolean
    )

    companion object {
        /** سقف سرویس‌های پشتیبان؛ یعنی حداکثر چهار سرویس در زنجیره. */
        const val MAX_BACKUPS = 3

        /** سرویس‌های آماده؛ Gemini و Claude از API بومی خودشان و بقیه از OpenAI-compatible. */
        val PRESETS: List<Preset> = listOf(
            Preset(
                id = "avalai",
                title = "آوالای (ایران، بدون فیلترشکن)",
                endpoint = "https://api.avalai.ir/v1",
                model = "gpt-4o-mini",
                hint = "درگاه ایرانی و سازگار با OpenAI؛ کلید aa-… را از avalai.ir بگیر. " +
                        "از داخل ایران بدون فیلترشکن کار می‌کند و مدل‌های GPT، Claude و Gemini " +
                        "را با همین یک کلید می‌دهد. اگر اینترنت داخلی ناپایدار بود، آدرس " +
                        "https://api.avalapis.ir/v1 را هم امتحان کن.",
                providerSearch = false
            ),
            Preset(
                id = "gapgpt",
                title = "گپ‌جی‌پی‌تی (ایران)",
                endpoint = "https://api.gapgpt.app/v1",
                model = "gpt-4o-mini",
                hint = "درگاه ایرانی دیگر و سازگار با OpenAI؛ کلید را از gapgpt.app بگیر. " +
                        "بدون فیلترشکن و با پرداخت ریالی کار می‌کند؛ گزینهٔ خوبی برای سرویس پشتیبان است.",
                providerSearch = false
            ),
            Preset(
                id = "gemini",
                title = "Gemini (گوگل)",
                endpoint = "https://generativelanguage.googleapis.com/v1beta",
                model = "gemini-flash-latest",
                hint = "کلید رایگان از aistudio.google.com؛ از مسیر بومی Gemini و هدر X-goog-api-key استفاده می‌شود (همان روش نمونه‌ی curl گوگل). توجه: Gemini در ایران مسدود است و بدون تغییر مسیر شبکه خطای «محدودیت جغرافیایی» می‌دهد.",
                providerSearch = false
            ),
            Preset(
                id = "gemini_openai",
                title = "Gemini (سازگار OpenAI)",
                endpoint = "https://generativelanguage.googleapis.com/v1beta/openai",
                model = "gemini-2.0-flash",
                hint = "همان کلید AI Studio، ولی از مسیر سازگار با OpenAI گوگل. اگر مسیر بومی " +
                        "پاسخ بی‌متن داد، برنامه خودکار همین مسیر را هم امتحان می‌کند.",
                providerSearch = false
            ),
            Preset(
                id = "claude",
                title = "Claude (کلاد)",
                endpoint = "https://api.anthropic.com/v1",
                model = "claude-sonnet-4-5",
                hint = "کلید از console.anthropic.com؛ برنامه مستقیم از Messages API رسمی Claude با هدرهای x-api-key و anthropic-version استفاده می‌کند.",
                providerSearch = false
            ),
            Preset(
                id = "llmsrelay",
                title = "LLMsRelay (Claude)",
                endpoint = "https://api.llmsrelay.com/v1",
                model = "claude-sonnet-4.6",
                hint = "کلید sk-cs4-* از LLMsRelay؛ مسیر سازگار با OpenAI همین سرویس استفاده می‌شود " +
                        "و هدرهای Anthropic هم فرستاده می‌شوند. مدل Opus هم از همین مسیر در دسترس است.",
                providerSearch = false
            ),
            Preset(
                id = "openai",
                title = "OpenAI",
                endpoint = "https://api.openai.com/v1",
                model = "gpt-4o-mini",
                hint = "کلید از platform.openai.com؛ جست‌وجوی وب سرویس هم پشتیبانی می‌شود.",
                providerSearch = true
            ),
            Preset(
                id = "openrouter",
                title = "OpenRouter",
                endpoint = "https://openrouter.ai/api/v1",
                model = "openai/gpt-4o-mini",
                hint = "یک کلید برای چند مدل؛ افزونه‌ی جست‌وجوی وب OpenRouter فعال می‌شود.",
                providerSearch = true
            )
        )

        fun isValidEndpoint(value: String): Boolean = runCatching {
            val uri = URI(value.trim())
            (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                    !uri.host.isNullOrBlank() && uri.userInfo == null &&
                    uri.rawQuery == null && uri.rawFragment == null
        }.getOrDefault(false)
    }
}

object PumpAiConfigStore {
    private const val PREF = "pulse_pump_ai"
    private const val KEY_CONFIG = "config"
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var pendingSave: Job? = null

    @Synchronized
    fun load(context: Context): PumpAiConfig {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CONFIG, null) ?: return PumpAiConfig()
        return runCatching {
            val obj = JSONObject(raw)
            val cipherText = obj.optString("apiKeyCipher")
            // مهاجرت یک‌باره از ۱٫۱۸: مقدار قدیمی plaintext خوانده و بلافاصله
            // با Keystore بازنویسی می‌شود؛ در شکست رمزگذاری، plaintext حذف می‌شود.
            val legacyPlainText = obj.optString("apiKey")
            // decrypt برای ciphertext خالی رشته‌ی خالی برمی‌گرداند؛ پس فقط مقدار
            // واقعاً رمزگشایی‌شده جایگزین plaintext قدیمی می‌شود، وگرنه کلید کاربر
            // در مهاجرت از نسخه‌ی ۱٫۱۸ گم می‌شد.
            val decrypted = PumpAiSecretCipher.decrypt(cipherText)?.takeIf { it.isNotEmpty() }
            val apiKey = decrypted ?: legacyPlainText
            // هر مقدار plaintext باقی‌مانده روی دیسک باید با نسخه‌ی رمزشده بازنویسی شود.
            val needsMigration = legacyPlainText.isNotEmpty()
            // پشتیبان‌ها هم مثل سرویس اصلی کلیدشان رمزشده ذخیره می‌شود؛ هر مقدار
            // plaintext قدیمی/دست‌کاری‌شده با همان مسیر مهاجرت بازنویسی می‌شود.
            var backupPlainText = false
            val backups = mutableListOf<PumpAiBackup>()
            val rawBackups = obj.optJSONArray("backups")
            if (rawBackups != null) {
                for (index in 0 until minOf(rawBackups.length(), PumpAiConfig.MAX_BACKUPS)) {
                    val item = rawBackups.optJSONObject(index) ?: continue
                    val legacyBackupKey = item.optString("apiKey")
                    if (legacyBackupKey.isNotEmpty()) backupPlainText = true
                    val backupKey = PumpAiSecretCipher.decrypt(item.optString("apiKeyCipher"))
                        ?.takeIf { it.isNotEmpty() } ?: legacyBackupKey
                    backups += PumpAiBackup(
                        endpoint = item.optString("endpoint"),
                        model = item.optString("model"),
                        apiKey = backupKey,
                        providerSearch = item.optBoolean("providerSearch", false),
                        active = item.optBoolean("active", true)
                    )
                }
            }
            val config = sanitize(
                PumpAiConfig(
                    enabled = obj.optBoolean("enabled", false),
                    endpoint = obj.optString("endpoint"),
                    model = obj.optString("model"),
                    apiKey = apiKey,
                    providerSearch = obj.optBoolean("providerSearch", true),
                    backups = backups
                )
            )
            if ((needsMigration || backupPlainText) && !save(context, config)) {
                // اگر Keystore در دسترس نبود، پیکربندی غیرحساس را نگه دار ولی plaintext را حذف کن.
                val withoutKey = config.copy(
                    apiKey = "",
                    backups = config.backups.map { it.copy(apiKey = "") }
                )
                save(context, withoutKey)
                return@runCatching withoutKey
            }
            config
        }.getOrDefault(PumpAiConfig())
    }

    /**
     * ذخیره‌ی خارج از thread اصلی با debounce؛ با خروج از صفحه لغو نمی‌شود و Keystore
     * روی هر حرف تایپ‌شده اجرا نمی‌شود.
     */
    @Synchronized
    fun saveDebounced(
        context: Context,
        config: PumpAiConfig,
        onResult: (Boolean) -> Unit = {}
    ) {
        pendingSave?.cancel()
        val appContext = context.applicationContext
        pendingSave = saveScope.launch {
            delay(400)
            val result = save(appContext, config)
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    /** false یعنی Keystore کلید تازه را نپذیرفت؛ هیچ plaintextی روی دیسک نوشته نمی‌شود. */
    @Synchronized
    fun save(context: Context, config: PumpAiConfig): Boolean {
        val safe = sanitize(config)
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_CONFIG, null)?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()
        }
        // رمزگذاری دوباره‌ی کلید بدون تغییر لازم نیست؛ ciphertext موجود دوباره استفاده می‌شود.
        val knownCiphers = HashMap<String, String>()
        fun remember(cipherText: String) {
            if (cipherText.isEmpty()) return
            PumpAiSecretCipher.decrypt(cipherText)?.takeIf { it.isNotEmpty() }?.let { plain ->
                if (!knownCiphers.containsKey(plain)) knownCiphers[plain] = cipherText
            }
        }
        remember(stored?.optString("apiKeyCipher").orEmpty())
        stored?.optJSONArray("backups")?.let { array ->
            for (index in 0 until array.length()) {
                remember(array.optJSONObject(index)?.optString("apiKeyCipher").orEmpty())
            }
        }
        /** رشته‌ی خالی یعنی «کلیدی نیست»؛ null یعنی Keystore شکست خورد. */
        fun cipherFor(plainKey: String): String? = when {
            plainKey.isEmpty() -> ""
            knownCiphers.containsKey(plainKey) -> knownCiphers[plainKey]
            else -> runCatching { PumpAiSecretCipher.encrypt(plainKey) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
        }

        val cipher = cipherFor(safe.apiKey) ?: return false
        val backupsJson = JSONArray()
        for (backup in safe.backups) {
            val backupCipher = cipherFor(backup.apiKey) ?: return false
            backupsJson.put(
                JSONObject()
                    .put("endpoint", backup.endpoint)
                    .put("model", backup.model)
                    .put("apiKeyCipher", backupCipher)
                    .put("providerSearch", backup.providerSearch)
                    .put("active", backup.active)
            )
        }
        val raw = JSONObject()
            .put("enabled", safe.enabled)
            .put("endpoint", safe.endpoint)
            .put("model", safe.model)
            .put("apiKeyCipher", cipher)
            .put("providerSearch", safe.providerSearch)
            .put("backups", backupsJson)
            .toString()
        prefs.edit().putString(KEY_CONFIG, raw).apply()
        return true
    }

    internal fun sanitize(config: PumpAiConfig): PumpAiConfig = config.copy(
        endpoint = config.endpoint.trim().take(500),
        model = config.model.trim().take(150),
        apiKey = sanitizeKey(config.apiKey),
        backups = config.backups.take(PumpAiConfig.MAX_BACKUPS).map { backup ->
            backup.copy(
                endpoint = backup.endpoint.trim().take(500),
                model = backup.model.trim().take(150),
                apiKey = sanitizeKey(backup.apiKey)
            )
        }
    )

    /**
     * کلید باید دقیقاً همان رشته‌ی ASCII سرویس باشد.
     *
     * چرا: کپی‌کردن کلید از پیام‌رسان/مرورگر فارسی خیلی وقت‌ها فاصله، خط‌جدید،
     * نیم‌فاصله (U+200C) یا کاراکتر جهت‌دهی راست‌به‌چپ را هم می‌آورد. چنین کلیدی
     * یا هدر HTTP را خراب می‌کند یا سرویس آن را با ۴۰۱ رد می‌کند، درحالی‌که کاربر
     * مطمئن است کلیدش درست است. پیشوند «Bearer» هم اگر مانده باشد حذف می‌شود چون
     * خودِ برنامه آن را اضافه می‌کند.
     */
    internal fun sanitizeKey(raw: String): String {
        val trimmed = raw.trim()
        // «Bearer»/«Token» فقط وقتی حذف می‌شود که واقعاً پیشوندِ جدا باشد؛ کلیدی که
        // اتفاقاً با همین حروف شروع شود دست‌کاری نمی‌شود.
        val withoutScheme = when {
            trimmed.startsWith("bearer ", ignoreCase = true) -> trimmed.drop(7)
            trimmed.startsWith("token ", ignoreCase = true) -> trimmed.drop(6)
            else -> trimmed
        }
        return withoutScheme
            .filterNot { char ->
                char.isWhitespace() || char.code < 32 || char.code == 127 ||
                        char == '\u00A0' || char == '\u061C' || char == '\uFEFF' ||
                        char in '\u200B'..'\u200F' || char in '\u202A'..'\u202E' ||
                        char in '\u2066'..'\u2069'
            }
            .take(1000)
    }
}
