package com.pulse.market.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URI
import java.util.Locale
import kotlin.random.Random

/** نظر دوم اختیاری از هر سرویس OpenAI-compatible؛ تصمیم پایه‌ی برنامه را جایگزین نمی‌کند. */
object PumpAiReviewer {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val allowedRecommendations = PumpScanner.Recommendation.entries.map { it.label }

    /** مسیرهایی مثل /v1، /v1beta یا /v2alpha که فقط «chat/completions» کم دارند. */
    private val VERSIONED_PATH = Regex("(?i)/v\\d+(?:beta|alpha)?\\d*$")

    @kotlinx.serialization.Serializable
    data class NewsItem(
        val title: String,
        val url: String,
        val relation: String,
        val source: String = "",
        val publishedAt: String = "",
        /** میزبان واقعی URL؛ مستقل از نامی که مدل ادعا می‌کند. */
        val host: String = ""
    )

    @kotlinx.serialization.Serializable
    data class Review(
        val verdict: String,
        val recommendation: String,
        val reason: String,
        val confidence: Int?,
        val news: List<NewsItem>,
        val providerSearchRequested: Boolean,
        /** «الان چه کار کنم»: خرید پله‌ای، انتظار، فروش پله‌ای، خروج… */
        val action: String = "",
        /** محدوده‌ی قیمتی ورود با عدد */
        val entry: String = "",
        val stopLoss: String = "",
        val targets: String = "",
        /** افق زمانی سناریو (مثلاً ۲۴ تا ۷۲ ساعت) */
        val timeframe: String = "",
        /** چه اتفاقی سناریو را باطل می‌کند */
        val invalidation: String = "",
        /** تحلیل تکنیکال فشرده (ساختار قیمت، ایچیموکو، حجم) */
        val technical: String = "",
        /** پشت این کوین چه کسی/شرکتی است، کاربرد و توکنومیکس */
        val project: String = "",
        /** محرک‌های خبری پیش‌رو */
        val catalysts: String = "",
        val risks: String = "",
        /** نظر کارشناسی خلاصه (۲ تا ۳ جمله) */
        val summary: String = ""
    )

    data class Outcome(val review: Review? = null, val error: String? = null)

    /** پاسخ متنی عمومی برای قابلیت‌هایی مثل خلاصه‌سازی خبر؛ از همان تنظیم امن AI استفاده می‌کند. */
    data class CompletionOutcome(val content: String? = null, val error: String? = null)

    /** یک مسیر قابل امتحان: آدرس نهایی، بدنه و اینکه پاسخ به سبک Gemini خوانده شود یا OpenAI. */
    internal data class Route(
        val endpoint: String,
        val payload: JsonObject,
        val nativeGemini: Boolean,
        val label: String,
        /** سقف زمان همین مسیر (ثانیه) — مسیر اول بیشترین فرصت را دارد */
        val timeoutSeconds: Int = REQUEST_TIMEOUT_SECONDS,
        val nativeAnthropic: Boolean = false,
        /** آیا همین payload واقعاً ابزار جست‌وجوی provider را درخواست کرده است؟ */
        val providerSearchRequested: Boolean = false
    )

    /**
     * مسیرهای موجود برای این پیکربندی، به‌ترتیب اولویت — هیچ مسیری جایگزین دیگری نشده:
     * مسیر بومی Gemini و مسیر سازگار با OpenAI هر دو نگه داشته شده‌اند و اگر اولی
     * پاسخ بی‌متن (مثل MALFORMED_FUNCTION_CALL) بدهد، همان درخواست از مسیر دوم می‌رود.
     */
    internal fun reviewRoutes(config: PumpAiConfig, coin: PumpScanner.PumpCoin): List<Route> {
        val system = systemPrompt()
        val user = userPrompt(coin)
        val noThinking = supportsThinkingBudget(config.model)
        if (isAnthropicHost(config.endpoint)) {
            return listOf(
                Route(
                    endpoint = anthropicEndpoint(config.endpoint),
                    payload = anthropicBody(config.model, system, user, 2048, temperature = 0.2),
                    nativeGemini = false,
                    label = "Claude بومی",
                    timeoutSeconds = REQUEST_TIMEOUT_SECONDS,
                    nativeAnthropic = true
                )
            )
        }
        if (isGeminiNative(config.endpoint)) {
            val native = geminiEndpoint(config.endpoint, config.model)
            val compat = geminiCompatEndpoint()
            return listOf(
                Route(
                    native,
                    geminiBody(system, user, 2048, jsonOutput = true, disableThinking = noThinking),
                    true,
                    "Gemini بومی",
                    REQUEST_TIMEOUT_SECONDS
                ),
                Route(
                    native,
                    geminiBody(system, user, 2048, disableThinking = noThinking),
                    true,
                    "Gemini بومی (متن ساده)",
                    FALLBACK_TIMEOUT_SECONDS
                ),
                Route(
                    compat,
                    requestBody(config, coin, compat),
                    false,
                    "Gemini سازگار OpenAI",
                    FALLBACK_TIMEOUT_SECONDS
                )
            )
        }
        val chat = chatCompletionsEndpoint(config.endpoint)
        val routes = mutableListOf<Route>()
        val jsonMode = supportsJsonMode(chat)
        val withSearch = config.providerSearch && supportsProviderSearch(chat)
        if (withSearch) {
            // برخی مدل‌ها/حساب‌ها plugin وب را قبول نمی‌کنند؛ مسیر بعدی همان درخواست
            // بدون plugin است تا ۴۰۰ یک قابلیت اختیاری کل تحلیل را خراب نکند.
            routes += Route(
                chat,
                requestBody(
                    config, coin, chat,
                    includeProviderSearch = true,
                    jsonMode = jsonMode
                ),
                false,
                if (jsonMode) "سرویس + وب + JSON" else "سرویس + جست‌وجوی وب",
                providerSearchRequested = true
            )
        }
        if (jsonMode) {
            routes += Route(
                chat,
                requestBody(
                    config, coin, chat,
                    includeProviderSearch = false,
                    jsonMode = true
                ),
                false, "سرویس بدون وب + JSON",
                if (withSearch) FALLBACK_TIMEOUT_SECONDS else REQUEST_TIMEOUT_SECONDS
            )
        }
        routes += Route(
            chat,
            requestBody(
                config, coin, chat,
                includeProviderSearch = false,
                jsonMode = false
            ),
            false,
            "سرویس متن ساده",
            if (!jsonMode && !withSearch) REQUEST_TIMEOUT_SECONDS else FALLBACK_TIMEOUT_SECONDS
        )
        if (isGoogleHost(config.endpoint)) {
            // آدرس سازگار با OpenAI گوگل داده شده؛ مسیر بومی به‌عنوان پشتیبان می‌ماند.
            val native = geminiEndpoint(GOOGLE_BASE, config.model)
            routes += Route(
                native,
                geminiBody(system, user, 2048, jsonOutput = true, disableThinking = noThinking),
                true,
                "Gemini بومی",
                FALLBACK_TIMEOUT_SECONDS
            )
        }
        return routes
    }

    suspend fun review(
        config: PumpAiConfig,
        coin: PumpScanner.PumpCoin
    ): Outcome = withContext(Dispatchers.IO) {
        if (!config.enabled) return@withContext Outcome(error = "بررسی هوش مصنوعی خاموش است")
        if (!config.isReady) return@withContext Outcome(error = "آدرس API و نام مدل را کامل کن")
        val routes = try {
            reviewRoutes(config, coin)
        } catch (invalid: IllegalArgumentException) {
            return@withContext Outcome(error = invalid.message ?: "آدرس یا نام مدل نامعتبر است")
        }
        var lastError: String? = null
        for (route in routes) {
            val raw = try {
                executeAiRoute(
                    config = config,
                    route = route,
                    timeoutSeconds = route.timeoutSeconds,
                    maxBytes = 512L * 1024
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (http: Http.HttpException) {
                lastError = httpErrorText(http)
                // خطای کلید/دسترسی/مسدودی با مسیر دیگر هم درست نمی‌شود.
                if (isFatalServiceError(http, lastError)) return@withContext Outcome(error = lastError)
                continue
            } catch (failure: Exception) {
                lastError = networkErrorText(failure)
                continue
            }
            val content = extractRouteContent(route, raw)
            if (content != null) {
                return@withContext Outcome(
                    review = parseReview(content, route.providerSearchRequested)
                )
            }
            lastError = emptyContentReason(raw)
        }
        Outcome(error = lastError ?: "ارتباط با سرویس AI یا خواندن پاسخ ممکن نشد")
    }

    /**
     * تکمیل متنی عمومی با همان endpoint/model/key امنِ تحلیل پامپ. این مسیر عمداً افزونه‌ی
     * جست‌وجوی وب را فعال نمی‌کند: محتوای خبر و لینک واقعی را خود برنامه می‌دهد تا مدل
     * منبع جعلی نسازد یا خبر دیگری را با آن قاطی نکند.
     */
    suspend fun complete(
        config: PumpAiConfig,
        system: String,
        user: String,
        maxTokens: Int = 2400,
        /** مهلت اختصاصی قابلیت‌های سنگین؛ null یعنی زمان پیش‌فرض همان مسیر. */
        timeoutSeconds: Int? = null,
        /** در سرویس‌های پشتیبان، schema/JSON mode فعال و مسیر ساده هم حفظ می‌شود. */
        responseSchema: JsonObject? = null
    ): CompletionOutcome = withContext(Dispatchers.IO) {
        if (!config.enabled) return@withContext CompletionOutcome(error = "بررسی هوش مصنوعی خاموش است")
        if (!config.isReady) return@withContext CompletionOutcome(error = "آدرس API و نام مدل را کامل کن")
        val routes = try {
            completionRoutes(
                config, system, user, maxTokens.coerceIn(128, 4096), responseSchema
            )
        } catch (invalid: IllegalArgumentException) {
            return@withContext CompletionOutcome(error = invalid.message ?: "آدرس یا نام مدل نامعتبر است")
        }
        var lastError: String? = null
        for (route in routes) {
            val raw = try {
                executeAiRoute(
                    config = config,
                    route = route,
                    timeoutSeconds = completionTimeout(route.timeoutSeconds, timeoutSeconds),
                    maxBytes = 512L * 1024
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (http: Http.HttpException) {
                lastError = httpErrorText(http)
                if (isFatalServiceError(http, lastError)) {
                    return@withContext CompletionOutcome(error = lastError)
                }
                continue
            } catch (failure: Exception) {
                lastError = networkErrorText(failure)
                continue
            }
            val content = extractRouteContent(route, raw)
            if (!content.isNullOrBlank()) return@withContext CompletionOutcome(content = content)
            lastError = emptyContentReason(raw)
        }
        CompletionOutcome(error = lastError ?: "ارتباط با سرویس AI یا خواندن پاسخ ممکن نشد")
    }

    /** مسیرهای تکمیل عمومی؛ بدون ابزار وب، با structured-output اختیاری و fallback ساده. */
    internal fun completionRoutes(
        config: PumpAiConfig,
        system: String,
        user: String,
        maxTokens: Int,
        responseSchema: JsonObject? = null
    ): List<Route> {
        fun chatPayload(jsonMode: Boolean): JsonObject = buildJsonObject {
            put("model", config.model)
            put("temperature", 0.15)
            put("max_tokens", maxTokens)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
            if (jsonMode) {
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
        }
        if (isAnthropicHost(config.endpoint)) {
            return listOf(
                Route(
                    endpoint = anthropicEndpoint(config.endpoint),
                    payload = anthropicBody(config.model, system, user, maxTokens, temperature = 0.15),
                    nativeGemini = false,
                    label = "Claude بومی",
                    timeoutSeconds = REQUEST_TIMEOUT_SECONDS,
                    nativeAnthropic = true
                )
            )
        }
        val noThinking = supportsThinkingBudget(config.model)
        if (isGeminiNative(config.endpoint)) {
            val routes = mutableListOf(
                Route(
                    geminiEndpoint(config.endpoint, config.model),
                    geminiBody(
                        system, user, maxTokens,
                        jsonOutput = responseSchema != null,
                        disableThinking = noThinking,
                        responseSchema = responseSchema
                    ),
                    true,
                    if (responseSchema != null) "Gemini بومی + JSON ساختاری" else "Gemini بومی",
                    REQUEST_TIMEOUT_SECONDS
                )
            )
            if (responseSchema != null) {
                routes += Route(
                    geminiEndpoint(config.endpoint, config.model),
                    geminiBody(system, user, maxTokens, disableThinking = noThinking),
                    true, "Gemini بومی (متن ساده)", FALLBACK_TIMEOUT_SECONDS
                )
                routes += Route(
                    geminiCompatEndpoint(), chatPayload(jsonMode = true), false,
                    "Gemini سازگار OpenAI + JSON", FALLBACK_TIMEOUT_SECONDS
                )
            } else {
                routes += Route(
                    geminiCompatEndpoint(), chatPayload(jsonMode = false), false,
                    "Gemini سازگار OpenAI", FALLBACK_TIMEOUT_SECONDS
                )
            }
            return routes
        }
        val chat = chatCompletionsEndpoint(config.endpoint)
        val routes = mutableListOf<Route>()
        if (responseSchema != null && supportsJsonMode(chat)) {
            routes += Route(
                chat, chatPayload(jsonMode = true), false,
                "سازگار OpenAI + JSON", REQUEST_TIMEOUT_SECONDS
            )
            routes += Route(
                chat, chatPayload(jsonMode = false), false,
                "سازگار OpenAI (متن ساده)", FALLBACK_TIMEOUT_SECONDS
            )
        } else {
            routes += Route(
                chat, chatPayload(jsonMode = false), false,
                "سازگار OpenAI", REQUEST_TIMEOUT_SECONDS
            )
        }
        if (isGoogleHost(config.endpoint)) {
            routes += Route(
                geminiEndpoint(GOOGLE_BASE, config.model),
                geminiBody(
                    system, user, maxTokens,
                    jsonOutput = responseSchema != null,
                    disableThinking = noThinking,
                    responseSchema = responseSchema
                ),
                true,
                if (responseSchema != null) "Gemini بومی + JSON ساختاری" else "Gemini بومی",
                FALLBACK_TIMEOUT_SECONDS
            )
        }
        return routes
    }

    /** خطایی که امتحان مسیر دیگر هم آن را حل نمی‌کند. */
    internal fun isFatalServiceError(http: Http.HttpException, message: String): Boolean =
        http.code == 401 || http.code == 403 || http.code == 429 ||
                message.contains("محدودیت جغرافیایی")

    /** آدرس پایه یا آدرس کامل هر دو پذیرفته می‌شوند. */
    fun chatCompletionsEndpoint(raw: String): String {
        require(PumpAiConfig.isValidEndpoint(raw)) { "آدرس API نامعتبر است" }
        val clean = raw.trim().trimEnd('/')
        return when {
            clean.endsWith("/chat/completions", ignoreCase = true) -> clean
            // «…/v1»، «…/v1beta/openai» (Gemini) و هر مسیر نسخه‌دار دیگری آماده‌ی افزودن مسیر نهایی است.
            clean.endsWith("/openai", ignoreCase = true) ||
                    VERSIONED_PATH.containsMatchIn(clean) -> "$clean/chat/completions"
            else -> "$clean/v1/chat/completions"
        }
    }

    /** زمان درخواستیِ قابلیت سنگین فقط می‌تواند مهلت مسیر را بیشتر کند، نه کمتر. */
    internal fun completionTimeout(defaultSeconds: Int, requestedSeconds: Int?): Int =
        requestedSeconds?.coerceIn(3, 300)?.let { maxOf(defaultSeconds, it) } ?: defaultSeconds

    /** سقف زمان یک درخواست AI (ثانیه) — مدل‌های کند و جست‌وجوی وب وقت بیشتری می‌خواهند. */
    internal const val REQUEST_TIMEOUT_SECONDS = 180

    /** سقف زمان مسیرهای پشتیبان — کاربر نباید سه بار ۱۸۰ ثانیه منتظر بماند. */
    internal const val FALLBACK_TIMEOUT_SECONDS = 60

    private const val MAX_AI_RETRIES = 1
    private const val MAX_RETRY_AFTER_MS = 60_000L
    private const val MIN_RETRY_CALL_WINDOW_MS = 3_000L

    /** فقط خطاهای گذرا retry می‌شوند؛ خطای اعتبار/مدل/صورتحساب دوباره‌کاری نمی‌شود. */
    internal fun isRetryableHttp(http: Http.HttpException): Boolean {
        if (http.retryAfterMillis != null && http.retryAfterMillis > MAX_RETRY_AFTER_MS) return false
        val detail = http.message.orEmpty().lowercase(Locale.ROOT)
        if (detail.contains("insufficient_quota") || detail.contains("billing") ||
            detail.contains("credit balance")
        ) return false
        return http.code == 408 || http.code == 425 || http.code == 429 ||
            http.code in 500..504
    }

    internal fun retryDelayMillis(
        attempt: Int,
        retryAfterMillis: Long?,
        jitterMillis: Long
    ): Long {
        retryAfterMillis?.let { return it.coerceIn(0L, MAX_RETRY_AFTER_MS) }
        val exponential = 750L * (1L shl attempt.coerceIn(0, 4))
        return (exponential + jitterMillis.coerceIn(0L, 250L)).coerceAtMost(MAX_RETRY_AFTER_MS)
    }

    /** اجرای مسیر AI با یک retry محدود، backoff+jitter و احترام به Retry-After. */
    private suspend fun executeAiRoute(
        config: PumpAiConfig,
        route: Route,
        timeoutSeconds: Int,
        maxBytes: Long
    ): String {
        val request = buildRequest(config, route.endpoint, route.payload)
        val deadlineNanos = System.nanoTime() + timeoutSeconds.toLong() * 1_000_000_000L
        var attempt = 0
        while (true) {
            val attemptTimeout = if (attempt == 0) {
                timeoutSeconds
            } else {
                ((deadlineNanos - System.nanoTime()) / 1_000_000_000L)
                    .coerceAtMost(timeoutSeconds.toLong()).toInt()
            }
            try {
                return Http.execute(request, maxBytes, attemptTimeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (http: Http.HttpException) {
                if (attempt >= MAX_AI_RETRIES || !isRetryableHttp(http)) throw http
                val wait = retryDelayMillis(
                    attempt, http.retryAfterMillis, Random.nextLong(0L, 251L)
                )
                if (!hasRetryWindow(deadlineNanos, wait)) throw http
                delay(wait)
                if (!hasRetryWindow(deadlineNanos, 0L)) throw http
                attempt++
            } catch (io: IOException) {
                if (attempt >= MAX_AI_RETRIES) throw io
                val wait = retryDelayMillis(attempt, null, Random.nextLong(0L, 251L))
                if (!hasRetryWindow(deadlineNanos, wait)) throw io
                delay(wait)
                if (!hasRetryWindow(deadlineNanos, 0L)) throw io
                attempt++
            }
        }
    }

    /** backoff نیز داخل سقف کلی همان route است؛ retry زمان پنج‌دقیقه‌ای را دوبرابر نمی‌کند. */
    private fun hasRetryWindow(deadlineNanos: Long, waitMillis: Long): Boolean {
        val remainingMillis = (deadlineNanos - System.nanoTime()) / 1_000_000L
        return remainingMillis >= waitMillis + MIN_RETRY_CALL_WINDOW_MS
    }

    internal fun buildRequest(config: PumpAiConfig, endpoint: String, payload: JsonObject): Request =
        Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .apply {
                if (config.apiKey.isNotBlank()) {
                    when {
                        isGeminiNative(endpoint) -> {
                            // کلید AI Studio فقط با این هدر پذیرفته می‌شود.
                            header("X-goog-api-key", config.apiKey)
                        }
                        isAnthropicHost(endpoint) -> {
                            // API رسمی Claude Bearer/OpenAI-compatible نیست؛ Messages API
                            // فقط x-api-key + نسخه‌ی پروتکل را می‌خواهد.
                            header("x-api-key", config.apiKey)
                            header("anthropic-version", "2023-06-01")
                        }
                        else -> {
                            header("Authorization", "Bearer ${config.apiKey}")
                            if (hostOf(endpoint).endsWith("llmsrelay.com")) {
                                header("x-api-key", config.apiKey)
                                header("anthropic-version", "2023-06-01")
                            }
                        }
                    }
                }
            }
            .build()

    /** API رسمی Anthropic از POST /v1/messages استفاده می‌کند، نه chat/completions. */
    internal fun isAnthropicHost(endpoint: String): Boolean {
        val host = hostOf(endpoint)
        return host == "api.anthropic.com" || host.endsWith(".anthropic.com")
    }

    internal fun anthropicEndpoint(raw: String): String {
        require(PumpAiConfig.isValidEndpoint(raw)) { "آدرس API نامعتبر است" }
        val clean = raw.trim().trimEnd('/')
        return when {
            clean.endsWith("/messages", ignoreCase = true) -> clean
            clean.endsWith("/v1", ignoreCase = true) -> "$clean/messages"
            else -> "$clean/v1/messages"
        }
    }

    internal fun anthropicBody(
        model: String,
        system: String,
        user: String,
        maxTokens: Int,
        temperature: Double
    ): JsonObject = buildJsonObject {
        put("model", model.trim())
        put("max_tokens", maxTokens)
        put("temperature", temperature)
        put("system", system)
        put("messages", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("content", user)
            })
        })
    }

    /** plugin وب فقط برای دو payload شناخته‌شده فرستاده می‌شود و همیشه fallback ساده دارد. */
    internal fun supportsProviderSearch(endpoint: String): Boolean {
        val host = hostOf(endpoint)
        return host == "api.openai.com" || host == "openrouter.ai" ||
            host.endsWith(".openrouter.ai")
    }

    /** JSON mode شناخته‌شده؛ endpoint دلخواه با پارامتر ناسازگار خراب نمی‌شود. */
    internal fun supportsJsonMode(endpoint: String): Boolean {
        val host = hostOf(endpoint)
        return supportsProviderSearch(endpoint) || host.endsWith("generativelanguage.googleapis.com")
    }

    /**
     * کلیدهای Google AI Studio روی مسیر بومی Gemini کار می‌کنند
     * (`/v1beta/models/{model}:generateContent` + هدر `X-goog-api-key`).
     * اگر کاربر آدرس گوگل را بدون بخش `/openai` بدهد، همین مسیر استفاده می‌شود.
     */
    internal fun isGeminiNative(endpoint: String): Boolean =
        hostOf(endpoint).endsWith("generativelanguage.googleapis.com") &&
                !endpoint.contains("/openai", ignoreCase = true)

    private val MODEL_SAFE = Regex("[^A-Za-z0-9._-]")

    /** مدل‌هایی که پارامتر بودجه‌ی تفکر را می‌پذیرند (خانواده‌ی ۲٫۵ به بعد). */
    internal fun supportsThinkingBudget(model: String): Boolean {
        val name = model.lowercase(Locale.ROOT)
        return name.contains("2.5") || name.contains("2-5") ||
                name.contains("flash-latest") || name.contains("pro-latest") ||
                name.contains("gemini-3")
    }

    /** آدرس پایه‌ی رسمی Gemini؛ برای ساختن مسیر پشتیبان استفاده می‌شود. */
    internal const val GOOGLE_BASE = "https://generativelanguage.googleapis.com/v1beta"

    internal fun isGoogleHost(endpoint: String): Boolean =
        hostOf(endpoint).endsWith("generativelanguage.googleapis.com")

    /** مسیر سازگار با OpenAI گوگل — همان چیزی که پیش‌تر هم پشتیبانی می‌شد. */
    internal fun geminiCompatEndpoint(): String = "$GOOGLE_BASE/openai/chat/completions"

    /** ساخت آدرس نهایی مسیر بومی Gemini از روی آدرس پایه و نام مدل. */
    internal fun geminiEndpoint(raw: String, model: String): String {
        require(PumpAiConfig.isValidEndpoint(raw)) { "آدرس API نامعتبر است" }
        val clean = raw.trim().trimEnd('/')
        val base = when {
            VERSIONED_PATH.containsMatchIn(clean) -> clean
            clean.endsWith("/models", ignoreCase = true) -> clean.removeSuffix("/models")
            else -> "$clean/v1beta"
        }
        val name = MODEL_SAFE.replace(model.trim().removePrefix("models/"), "")
        require(name.isNotEmpty()) { "نام مدل نامعتبر است" }
        return "$base/models/$name:generateContent"
    }

    /** بدنه‌ی مسیر بومی Gemini (contents/systemInstruction/generationConfig). */
    internal fun geminiBody(
        system: String,
        user: String,
        maxTokens: Int,
        /**
         * خروجی ساختاریافته. مدل‌های 2.5 وقتی JSON را «در متن» از آن‌ها بخواهی گاهی
         * به‌جای متن، یک فراخوانی تابعِ خراب تولید می‌کنند و پاسخ با
         * MALFORMED_FUNCTION_CALL بی‌متن برمی‌گردد؛ با responseSchema این اتفاق نمی‌افتد.
         */
        jsonOutput: Boolean = false,
        /** خاموش‌کردن فاز «تفکر» مدل‌های 2.5 — سرعت پاسخ چند برابر می‌شود. */
        disableThinking: Boolean = false,
        /** schema اختصاصی قابلیت؛ null + jsonOutput یعنی schema تحلیل پامپ. */
        responseSchema: JsonObject? = null
    ): JsonObject = buildJsonObject {
        put("systemInstruction", buildJsonObject {
            put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
        })
        put("contents", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("parts", buildJsonArray { add(buildJsonObject { put("text", user) }) })
            })
        })
        put("generationConfig", buildJsonObject {
            put("temperature", 0.2)
            put("maxOutputTokens", maxTokens)
            if (disableThinking) {
                put("thinkingConfig", buildJsonObject { put("thinkingBudget", 0) })
            }
            if (jsonOutput || responseSchema != null) {
                put("responseMimeType", "application/json")
                put("responseSchema", responseSchema ?: reviewSchema())
            }
        })
    }

    /** فیلدهای متنی تحلیل — هم در schema گوگل و هم در parse استفاده می‌شوند. */
    private val TEXT_FIELDS = listOf(
        "action", "entry", "stopLoss", "targets", "timeframe",
        "invalidation", "technical", "project", "catalysts", "risks", "summary"
    )

    /** ساختار پاسخ برای حالت خروجی ساختاریافته‌ی Gemini (زیرمجموعه‌ی OpenAPI). */
    private fun reviewSchema(): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        put("properties", buildJsonObject {
            put("verdict", buildJsonObject { put("type", "STRING") })
            put("recommendation", buildJsonObject { put("type", "STRING") })
            put("reason", buildJsonObject { put("type", "STRING") })
            put("confidence", buildJsonObject { put("type", "INTEGER") })
            for (field in TEXT_FIELDS) {
                put(field, buildJsonObject { put("type", "STRING") })
            }
            put("news", buildJsonObject {
                put("type", "ARRAY")
                put("items", buildJsonObject {
                    put("type", "OBJECT")
                    put("properties", buildJsonObject {
                        put("title", buildJsonObject { put("type", "STRING") })
                        put("url", buildJsonObject { put("type", "STRING") })
                        put("relation", buildJsonObject { put("type", "STRING") })
                        put("source", buildJsonObject { put("type", "STRING") })
                        put("publishedAt", buildJsonObject { put("type", "STRING") })
                    })
                    put("required", buildJsonArray { add(JsonPrimitive("title")); add(JsonPrimitive("url")) })
                })
            })
        })
        put("required", buildJsonArray {
            add(JsonPrimitive("verdict"))
            add(JsonPrimitive("recommendation"))
            add(JsonPrimitive("reason"))
        })
    }

    /**
     * متن پاسخ مسیر بومی Gemini: candidates[0].content.parts[].text
     * بخش‌های «thought» (زنجیره‌ی تفکر مدل‌های 2.5) پاسخ نهایی نیستند و نادیده می‌روند.
     */
    internal fun extractGeminiContent(raw: String): String? {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val first = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val parts = (first["content"] as? JsonObject)?.get("parts") as? JsonArray ?: return null
        val text = parts.mapNotNull { element ->
            val part = element as? JsonObject ?: return@mapNotNull null
            val isThought = (part["thought"] as? JsonPrimitive)?.contentOrNull?.equals("true", true) == true
            if (isThought) null else (part["text"] as? JsonPrimitive)?.contentOrNull
        }.joinToString("\n").trim()
        return text.takeIf { it.isNotBlank() }
    }

    /** پاسخ رسمی Messages API کلاد: content[].type=text/content[].text. */
    internal fun extractAnthropicContent(raw: String): String? {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val blocks = root["content"] as? JsonArray ?: return null
        return blocks.mapNotNull { element ->
            val block = element as? JsonObject ?: return@mapNotNull null
            val type = (block["type"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (!type.equals("text", ignoreCase = true)) null
            else (block["text"] as? JsonPrimitive)?.contentOrNull
        }.joinToString("\n").trim().takeIf { it.isNotBlank() }
    }

    private fun extractRouteContent(route: Route, raw: String): String? = when {
        route.nativeGemini -> extractGeminiContent(raw)
        route.nativeAnthropic -> extractAnthropicContent(raw)
        else -> extractAssistantContent(raw)
    }

    /**
     * چرا پاسخ متن نداشت؟ متداول‌ترین حالت، تمام‌شدن سقف توکن روی مدل‌های «thinking»
     * است (کل بودجه صرف تفکر می‌شود و بخش متن خالی می‌ماند).
     */
    /** پاسخ بی‌متنی که تکرار درخواست ممکن است حلش کند (فراخوانی تابع خراب). */
    internal fun isRetryableEmptyAnswer(raw: String): Boolean {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return false
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return false
        val finish = (candidate["finishReason"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return finish.equals("MALFORMED_FUNCTION_CALL", true) || finish.equals("OTHER", true)
    }

    internal fun emptyContentReason(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return "پاسخ سرویس قابل خواندن نبود (JSON معتبر نبود)"
        val blockReason = ((root["promptFeedback"] as? JsonObject)?.get("blockReason")
                as? JsonPrimitive)?.contentOrNull
        if (!blockReason.isNullOrBlank()) {
            return "درخواست توسط فیلتر ایمنی سرویس رد شد ($blockReason)"
        }
        // Messages API کلاد: stop_reason و stop_details/refusal احتمالی.
        if ((root["type"] as? JsonPrimitive)?.contentOrNull.equals("message", true)) {
            val reason = (root["stop_reason"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val refusal = ((root["stop_details"] as? JsonObject)?.get("explanation")
                as? JsonPrimitive)?.contentOrNull
            return when {
                !refusal.isNullOrBlank() -> "مدل پاسخ‌دادن را رد کرد: ${refusal.take(120)}"
                reason.equals("max_tokens", true) -> "سقف طول پاسخ کلاد پر شد"
                reason.isNotBlank() -> "کلاد بدون متن پاسخ داد (دلیل پایان: $reason)"
                else -> "کلاد پاسخ داد ولی متن قابل‌خواندن نداشت"
            }
        }
        // مسیر سازگار با OpenAI: choices[0].finish_reason و refusal احتمالی
        ((root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)?.let { choice ->
            val refusal = ((choice["message"] as? JsonObject)?.get("refusal") as? JsonPrimitive)
                ?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
            if (refusal != null) return "مدل پاسخ‌دادن را رد کرد: ${refusal.take(120)}"
            val reason = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            return when {
                reason.equals("length", true) ->
                    "سقف طول پاسخ پر شد و مدل متنی برنگرداند؛ مدل دیگری را امتحان کن"
                reason.equals("content_filter", true) -> "پاسخ توسط فیلتر ایمنی سرویس حذف شد"
                reason.isNotBlank() -> "سرویس بدون متن پاسخ داد (دلیل پایان: $reason)"
                else -> "سرویس پاسخ داد ولی متنی در آن نبود"
            }
        }
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
        val finish = ((candidate?.get("finishReason") ?: candidate?.get("finish_reason"))
                as? JsonPrimitive)?.contentOrNull.orEmpty()
        return when {
            finish.equals("MAX_TOKENS", true) || finish.equals("length", true) ->
                "سقف طول پاسخ پر شد و مدل متنی برنگرداند — روی مدل‌های «thinking» مثل gemini-2.5 " +
                        "این اتفاق می‌افتد؛ مدل سبک‌تر (مثل gemini-2.0-flash) را امتحان کن"
            finish.equals("SAFETY", true) || finish.equals("content_filter", true) ->
                "پاسخ توسط فیلتر ایمنی سرویس حذف شد"
            finish.equals("MALFORMED_FUNCTION_CALL", true) ->
                "مدل به‌جای متن یک فراخوانی تابعِ خراب تولید کرد؛ برنامه یک بار دوباره تلاش کرد. " +
                        "اگر تکرار شد، مدل را به gemini-2.0-flash تغییر بده."
            finish.equals("RECITATION", true) ->
                "سرویس پاسخ را به‌خاطر شباهت به محتوای دارای حق نشر حذف کرد"
            candidate == null -> "سرویس هیچ پاسخی (candidate) برنگرداند؛ نام مدل را بررسی کن"
            finish.isNotBlank() -> "سرویس بدون متن پاسخ داد (دلیل پایان: $finish)"
            else -> "سرویس پاسخ داد ولی متنی در آن نبود"
        }
    }

    private fun hostOf(endpoint: String): String = runCatching {
        URI(endpoint).host.orEmpty().lowercase(Locale.ROOT)
    }.getOrDefault("")

    /** پیام خطای HTTP همراه با راهنمای رفع؛ متن سرویس هم (کوتاه و پاک‌سازی‌شده) نمایش داده می‌شود. */
    internal fun httpErrorText(http: Http.HttpException): String {
        val detail = serviceMessage(http.message.orEmpty().substringAfter("—", "").trim())
        knownServiceProblem(detail)?.let { return it }
        val hint = when (http.code) {
            400 -> "درخواست پذیرفته نشد؛ معمولاً نام مدل اشتباه است یا مدل این پارامترها را قبول ندارد"
            401 -> "کلید API پذیرفته نشد (۴۰۱) — کلید را دوباره کپی کن و مطمئن شو مربوط به همین سرویس است"
            403 -> "دسترسی رد شد (۴۰۳) — کلید به این مدل دسترسی ندارد یا سرویس از کشور تو مسدود است"
            404 -> "آدرس یا نام مدل پیدا نشد (۴۰۴) — آدرس پایه و نام دقیق مدل را بررسی کن"
            408 -> "زمان پاسخ سرویس تمام شد (۴۰۸)"
            413 -> "حجم درخواست بیش از حد مجاز سرویس بود (۴۱۳)"
            429 -> "سهمیه یا محدودیت درخواست پر شده است (۴۲۹) — کمی بعد دوباره امتحان کن"
            in 500..599 -> "سرویس AI خطای داخلی داد (${http.code})"
            else -> "سرویس AI پاسخ HTTP ${http.code} داد"
        }
        return if (detail.isBlank()) hint else "$hint — پاسخ سرویس: $detail"
    }

    /** از بدنه‌ی JSON خطا فقط متن قابل‌فهم بیرون کشیده می‌شود (Google/OpenAI/Anthropic). */
    internal fun serviceMessage(raw: String): String {
        if (raw.isBlank()) return ""
        val parsed = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        val node = (parsed?.get("error") as? JsonObject) ?: parsed
        val text = node?.let { obj ->
            (obj["message"] as? JsonPrimitive)?.contentOrNull
                ?: (obj["detail"] as? JsonPrimitive)?.contentOrNull
        }
        return (text ?: raw).replace(Regex("\\s+"), " ").trim().take(200)
    }

    /**
     * خطاهای پرتکرارِ شناخته‌شده که پیام خام‌شان برای کاربر گویا نیست.
     * مهم‌ترینشان محدودیت جغرافیایی Gemini است که کلید درست هم با آن کار نمی‌کند.
     */
    internal fun knownServiceProblem(detail: String): String? {
        val lower = detail.lowercase(Locale.ROOT)
        return when {
            lower.contains("user location is not supported") ||
                    lower.contains("location is not supported") ->
                "❗ این سرویس (Gemini/Google) از کشور تو در دسترس نیست — کلید درست است ولی " +
                        "درخواست به‌خاطر محدودیت جغرافیایی رد می‌شود. با تغییر مسیر شبکه به یک کشور " +
                        "پشتیبانی‌شده، یا با یک سرویس واسط مثل OpenRouter امتحان کن."
            lower.contains("api key not valid") || lower.contains("api_key_invalid") ->
                "کلید API معتبر نیست — مطمئن شو کلید را از Google AI Studio کپی کرده‌ای و " +
                        "فاصله یا کاراکتر اضافه‌ای در آن نمانده است."
            lower.contains("api key expired") ->
                "کلید API منقضی شده است؛ از Google AI Studio یک کلید تازه بساز."
            lower.contains("is not found for api version") || lower.contains("model not found") ->
                "نام مدل برای این سرویس درست نیست — مثلاً برای Gemini از gemini-2.5-flash " +
                        "یا gemini-2.0-flash استفاده کن."
            lower.contains("quota") || lower.contains("rate limit") ->
                "سهمیه یا محدودیت درخواست این کلید پر شده است؛ کمی بعد دوباره امتحان کن."
            lower.contains("permission") && lower.contains("denied") ->
                "این کلید اجازه‌ی استفاده از این مدل/سرویس را ندارد."
            else -> null
        }
    }

    /** خطای شبکه‌ای؛ پیام خام ممکن است آدرس یا کلید داشته باشد، پس دسته‌بندی می‌شود. */
    internal fun networkErrorText(failure: Exception): String {
        val raw = failure.message.orEmpty().lowercase(Locale.ROOT)
        return when {
            raw.contains("timeout") || raw.contains("timed out") ->
                "زمان پاسخ سرویس تمام شد. سه راه سریع: ۱) مدل سبک‌تر مثل gemini-2.0-flash یا " +
                        "claude-sonnet را انتخاب کن، ۲) کلید «درخواست جست‌وجوی وب از سرویس» را خاموش کن، " +
                        "۳) کیفیت اتصال/فیلترشکن را بررسی کن"
            raw.contains("unable to resolve host") || raw.contains("unknownhost") ->
                "آدرس سرویس پیدا نشد (DNS)؛ آدرس API و اتصال اینترنت را بررسی کن"
            raw.contains("ssl") || raw.contains("certpath") || raw.contains("handshake") ->
                "ارتباط امن (TLS) برقرار نشد؛ معمولاً به‌خاطر فیلترشکن یا ساعت اشتباه دستگاه است"
            raw.contains("econnreset") || raw.contains("connection reset") || raw.contains("connect") ->
                "اتصال به سرویس برقرار نشد؛ ممکن است دسترسی از ایران مسدود باشد (فیلترشکن لازم است)"
            raw.contains("ناامن") || raw.contains("تغییر مسیر") || raw.contains("حجم پاسخ") ->
                failure.message.orEmpty()
            else -> "ارتباط با سرویس AI یا خواندن پاسخ ممکن نشد"
        }
    }

    data class TestResult(
        val ok: Boolean,
        val message: String,
        val latencyMillis: Long? = null,
        val route: String = ""
    )

    internal fun connectionQuality(latencyMillis: Long): String = when {
        latencyMillis < 2_000L -> "عالی"
        latencyMillis < 8_000L -> "خوب"
        latencyMillis < 20_000L -> "متوسط"
        else -> "کند"
    }

    /**
     * تست اتصال: یک درخواست بسیار کوچک می‌فرستد تا معلوم شود آدرس، مدل و کلید
     * واقعاً کار می‌کنند. هیچ داده‌ی کوینی فرستاده نمی‌شود.
     */
    suspend fun testConnection(config: PumpAiConfig): TestResult = withContext(Dispatchers.IO) {
        if (config.endpoint.isBlank() || config.model.isBlank()) {
            return@withContext TestResult(false, "اول آدرس API و نام مدل را بنویس")
        }
        if (!config.endpointValid) {
            return@withContext TestResult(
                false,
                "آدرس API معتبر نیست؛ باید HTTPS و بدون query یا نام کاربری باشد"
            )
        }
        if (config.insecureKeyTransport) {
            return@withContext TestResult(
                false,
                "روی آدرس HTTP کلید فرستاده نمی‌شود؛ آدرس HTTPS بگذار"
            )
        }
        val routes = try {
            testRoutes(config)
        } catch (invalid: IllegalArgumentException) {
            return@withContext TestResult(false, "❌ " + (invalid.message ?: "آدرس یا نام مدل نامعتبر است"))
        }
        var lastMessage = "❌ ارتباط با سرویس AI ممکن نشد"
        for (route in routes) {
            val startedAt = System.nanoTime()
            val raw = try {
                executeAiRoute(
                    config = config,
                    route = route,
                    timeoutSeconds = 60,
                    maxBytes = 64L * 1024
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (http: Http.HttpException) {
                val message = httpErrorText(http)
                lastMessage = "❌ $message"
                if (isFatalServiceError(http, message)) return@withContext TestResult(false, lastMessage)
                continue
            } catch (failure: Exception) {
                lastMessage = "❌ " + networkErrorText(failure)
                continue
            }
            val content = extractRouteContent(route, raw)?.trim().orEmpty()
            if (content.isNotBlank()) {
                val latency = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                return@withContext TestResult(
                    ok = true,
                    message = "✅ اتصال ${connectionQuality(latency)} (${latency}ms) — " +
                        "مدل «${config.model}» از مسیر ${route.label} پاسخ داد: ${content.take(40)}",
                    latencyMillis = latency,
                    route = route.label
                )
            }
            lastMessage = "⚠️ " + emptyContentReason(raw)
        }
        TestResult(false, lastMessage)
    }

    /** همان مسیرهای بررسی، ولی با یک پیام خیلی کوتاه و بدون داده‌ی کوین. */
    internal fun testRoutes(config: PumpAiConfig): List<Route> {
        val system = "Answer with one short word."
        val user = "Reply with exactly: OK"
        fun chatPayload(): JsonObject = buildJsonObject {
            put("model", config.model)
            put("max_tokens", 16)
            put("temperature", 0.0)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }
        if (isAnthropicHost(config.endpoint)) {
            return listOf(
                Route(
                    endpoint = anthropicEndpoint(config.endpoint),
                    payload = anthropicBody(config.model, system, user, 16, temperature = 0.0),
                    nativeGemini = false,
                    label = "Claude بومی",
                    nativeAnthropic = true
                )
            )
        }
        if (isGeminiNative(config.endpoint)) {
            return listOf(
                Route(
                    geminiEndpoint(config.endpoint, config.model),
                    geminiBody(
                        system, user, 512,
                        disableThinking = supportsThinkingBudget(config.model)
                    ),
                    true,
                    "Gemini بومی"
                ),
                Route(geminiCompatEndpoint(), chatPayload(), false, "Gemini سازگار OpenAI")
            )
        }
        val chat = chatCompletionsEndpoint(config.endpoint)
        val routes = mutableListOf(Route(chat, chatPayload(), false, "سازگار OpenAI"))
        if (isGoogleHost(config.endpoint)) {
            routes += Route(
                geminiEndpoint(GOOGLE_BASE, config.model),
                geminiBody(
                    system, user, 512,
                    disableThinking = supportsThinkingBudget(config.model)
                ),
                true,
                "Gemini بومی"
            )
        }
        return routes
    }

    /** متن نقش سیستم — بین مسیر OpenAI-compatible و مسیر بومی Gemini مشترک است. */
    internal fun systemPrompt(): String =
        """
        نقش: تحلیل‌گر ارشد بازار رمزارز با تخصص هم‌زمان در تحلیل تکنیکال، جریان نقدینگی،
        فاندامنتال پروژه و تحلیل خبری. مخاطب یک معامله‌گر است، نه تازه‌کار.

        قواعد کیفیت (بسیار مهم):
        - کلی‌گویی ممنوع. جمله‌هایی مثل «بازار پرنوسان است» یا «با احتیاط عمل کنید» بی‌ارزش‌اند.
        - عددهایی که در ورودی آمده‌اند را دوباره توصیف نکن؛ آن‌ها را «تفسیر» کن و از آن‌ها نتیجه بساز.
        - هر ادعا باید یا از داده‌های ورودی استخراج شده باشد یا از خبر مشخص با لینک. اگر چیزی را نمی‌دانی
          صریح بنویس «نامشخص»؛ حدس بی‌پایه نزن و خبر جعلی نساز.
        - سطح‌های قیمتی را با عدد دلاری واقعی و متناسب با قیمت فعلی بده (نه درصد مبهم).

        در تحلیل تکنیکال از داده‌های داده‌شده استفاده کن: ساختار قیمت ۱ ساعته/۲۴ ساعته/۷ روزه/۳۰ روزه،
        جای قیمت در دامنه‌ی ۲۴ ساعته، فاصله تا ATH، گردش حجم به ارزش بازار، مرحله‌ی حرکت و وضعیت
        ایچیموکو (تنکان، کیجون و جای قیمت نسبت به ابر).

        در بخش پروژه بگو این کوین متعلق به چه تیم/شرکت/بنیادی است، روی چه شبکه‌ای کار می‌کند،
        کاربرد واقعی و منبع درآمدش چیست، سرمایه‌گذاران شاخص و وضعیت عرضه/آزادسازی توکن چگونه است.

        در بخش خبر، رویدادهای تازه و محرک‌های پیشِ رو (لیست‌شدن، آنلاک، آپدیت شبکه، شراکت، هک، دعوای حقوقی)
        را بیاور و ارتباطشان با حرکت قیمت را توضیح بده؛ فقط لینک HTTPS واقعی.

        تصمیم را صریح بگو: چه زمانی خرید منطقی است، در چه محدوده‌ای، با چه حد ضرری، تا چه هدفی،
        و در چه شرایطی باید فروخت یا اصلاً وارد نشد. سناریوی باطل‌کننده را هم بنویس.
        هیچ‌وقت سود تضمین نکن و بنویس که این تحلیل آموزشی است و ریسک با کاربر است.

        فقط JSON معتبر و بدون markdown برگردان، با همین کلیدها:
        {"verdict":"همسو|محتاط‌تر|نامطمئن","recommendation":"فعلاً فقط زیر نظر بگیر|صبر کن؛ ورود عجولانه نکن|فعلاً وارد نشو؛ قیمت را تعقیب نکن",
         "action":"کار پیشنهادی در یک جمله (مثلاً: ورود پله‌ای فقط پس از تثبیت بالای X، وگرنه بدون معامله)",
         "entry":"محدوده‌ی ورود با عدد دلاری","stopLoss":"حد ضرر با عدد","targets":"هدف‌ها با عدد (هدف ۱ و ۲)",
         "timeframe":"افق زمانی سناریو","invalidation":"چه اتفاقی سناریو را باطل می‌کند",
         "technical":"تحلیل تکنیکال فشرده و عددی","project":"پشتوانه، تیم/شرکت، شبکه، کاربرد و توکنومیکس",
         "catalysts":"محرک‌های خبری پیشِ رو","risks":"مهم‌ترین ریسک‌های مشخص","reason":"چرا این تصمیم",
         "summary":"نظر کارشناسی خلاصه در ۲ تا ۳ جمله","confidence":0,
         "news":[{"title":"...","url":"https://...","relation":"ارتباط خبر با قیمت","source":"...","publishedAt":"..."}]}
        """.trimIndent()

    /** داده‌های همین کوین برای مدل؛ هیچ اطلاعات شخصی‌ای فرستاده نمی‌شود. */
    internal fun userPrompt(coin: PumpScanner.PumpCoin): String {
        val advice = coin.advice
        val ichimoku = Ichimoku.of(coin.spark)
        return buildString {
            appendLine("کوین: ${coin.displayName} (شناسه: ${coin.id})")
            appendLine("رتبه بازار: ${coin.rank}")
            appendLine("قیمت فعلی: ${number(coin.price)} دلار")
            appendLine("تغییر ۱ ساعت: ${number(coin.change1h)}٪")
            appendLine("تغییر ۲۴ ساعت: ${number(coin.change24h)}٪")
            appendLine("تغییر ۷ روز: ${number(coin.change7d)}٪")
            appendLine("تغییر ۳۰ روز: ${number(coin.change30d)}٪")
            appendLine("سقف ۲۴ ساعته: ${number(coin.high24h)} دلار")
            appendLine("کف ۲۴ ساعته: ${number(coin.low24h)} دلار")
            coin.rangePosition24h?.let {
                appendLine("جای قیمت در دامنه‌ی ۲۴ ساعته: ${number(it * 100)}٪ (۰=کف، ۱۰۰=سقف)")
            }
            appendLine("بالاترین قیمت تاریخ: ${number(coin.ath)} دلار")
            appendLine("فاصله تا ATH: ${number(coin.athChangePct)}٪")
            appendLine("حجم ۲۴ ساعت: ${number(coin.volume)} دلار")
            appendLine("ارزش بازار: ${number(coin.marketCap)} دلار")
            appendLine("گردش حجم به ارزش بازار: ${number(coin.turnover * 100)}٪")
            appendLine("عرضه در گردش: ${number(coin.circulatingSupply)}")
            appendLine("کل عرضه: ${number(coin.totalSupply)}")
            appendLine("مرحله‌ی حرکت طبق محاسبه‌ی برنامه: ${coin.stage.label}")
            appendLine("بازار کم‌عمق: ${if (coin.thinMarket) "بله" else "خیر"}")
            appendLine("سطح ریسک برنامه: ${coin.risk.label}")
            if (ichimoku != null) {
                appendLine(
                    "ایچیموکو روی سری ۷ روزه — تنکان: ${number(ichimoku.lastTenkan)} • " +
                            "کیجون: ${number(ichimoku.lastKijun)} • " +
                            "سقف ابر: ${number(maxOfOrNull(ichimoku.currentSpanA, ichimoku.currentSpanB))} • " +
                            "کف ابر: ${number(minOfOrNull(ichimoku.currentSpanA, ichimoku.currentSpanB))}"
                )
                appendLine("وضعیت ایچیموکو: ${Ichimoku.summary(coin.price, ichimoku)}")
            }
            if (coin.spark.size >= 6) {
                // فقط ۸ نقطه‌ی نماینده فرستاده می‌شود؛ سری کامل پاسخ مدل را کند می‌کرد.
                val sample = PumpScanner.downsample(coin.spark, 8)
                appendLine("روند ۷ روزه (۸ نقطه، قدیم به جدید): " + sample.joinToString(", ") { number(it) })
            }
            appendLine("پیشنهاد پایه‌ی برنامه: ${advice.recommendation.label}")
            append("دلیل پایه: ${advice.reason}")
        }
    }

    private fun maxOfOrNull(a: Double?, b: Double?): Double? =
        if (a == null || b == null) (a ?: b) else maxOf(a, b)

    private fun minOfOrNull(a: Double?, b: Double?): Double? =
        if (a == null || b == null) (a ?: b) else minOf(a, b)

    private fun requestBody(
        config: PumpAiConfig,
        coin: PumpScanner.PumpCoin,
        endpoint: String,
        includeProviderSearch: Boolean = config.providerSearch,
        jsonMode: Boolean = false
    ): JsonObject {
        val system = systemPrompt()
        val user = userPrompt(coin)
        return buildJsonObject {
            put("model", config.model)
            put("temperature", 0.2)
            put("max_tokens", 1200)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
            if (jsonMode) {
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
            if (includeProviderSearch) {
                // افزونه‌ی جست‌وجوی دو ارائه‌دهنده‌ی رایج؛ سایر سرویس‌ها می‌توانند
                // جست‌وجو را با قابلیت داخلی خود مدل و بر اساس prompt انجام دهند.
                val host = runCatching {
                    URI(endpoint).host.orEmpty().lowercase(Locale.ROOT)
                }.getOrDefault("")
                when {
                    host == "api.openai.com" -> put(
                        "web_search_options",
                        buildJsonObject { put("search_context_size", "medium") }
                    )
                    host == "openrouter.ai" || host.endsWith(".openrouter.ai") -> put(
                        "plugins",
                        buildJsonArray {
                            add(buildJsonObject {
                                put("id", "web")
                                put("max_results", 5)
                            })
                        }
                    )
                }
            }
        }
    }

    private fun extractAssistantContent(raw: String): String? {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        root["choices"]?.let { choices ->
            val first = (choices as? JsonArray)?.firstOrNull() as? JsonObject
            val content = (first?.get("message") as? JsonObject)?.get("content")
            textOf(content)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        textOf(root["output_text"])?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun textOf(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.mapNotNull { part ->
            when (part) {
                is JsonPrimitive -> part.contentOrNull
                is JsonObject -> textOf(part["text"] ?: part["content"])
                else -> null
            }
        }.joinToString("\n")
        else -> null
    }

    /** parser خالص و قابل تست؛ پاسخ غیر JSON هم به‌صورت محتاطانه نمایش داده می‌شود. */
    fun parseReview(content: String, providerSearchRequested: Boolean = true): Review {
        val clean = content.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val objectText = clean.substringAfter('{', "").let { inner ->
            if (inner.isEmpty()) "" else "{" + inner.substringBeforeLast('}', inner) + "}"
        }
        val obj = runCatching { json.parseToJsonElement(objectText) as? JsonObject }.getOrNull()
        if (obj == null) {
            return Review(
                verdict = "نامطمئن",
                recommendation = PumpScanner.Recommendation.WAIT.label,
                reason = safeDisplayText(clean, 1200).ifBlank { "سرویس AI پاسخ قابل‌استفاده‌ای نداد." },
                confidence = null,
                news = emptyList(),
                providerSearchRequested = providerSearchRequested,
                summary = safeDisplayText(clean, 400)
            )
        }

        val rawReason = safeDisplayText(obj.string("reason"), 1200).ifBlank {
            "سرویس AI برای نتیجه‌ی خود دلیل روشنی ارائه نکرد."
        }
        val rawRecommendation = safeDisplayText(obj.string("recommendation"), 120)
        val rawVerdict = safeDisplayText(obj.string("verdict"), 80)
        // کاربر عمداً راهنمای ورود/خروج می‌خواهد؛ فقط ادعای «سود تضمینی» و شبیه آن رد می‌شود.
        val unsafe = Regex(
            "(?i)(سود\\s*(قطعی|تضمینی|صددرصد)|بدون\\s*ریسک|ضرر\\s*نمی\\s*کنی|" +
                    "guaranteed\\s+(profit|returns?)|risk[- ]?free|100%\\s*(sure|profit))"
        ).containsMatchIn("$rawVerdict $rawRecommendation $rawReason")
        val recommendation = if (unsafe) {
            PumpScanner.Recommendation.WAIT.label
        } else {
            normalizeRecommendation(rawRecommendation)
        }
        val reason = if (unsafe) {
            "پاسخ مدل ادعای «سود تضمینی/بدون ریسک» داشت و برای ایمنی رد شد؛ چنین تضمینی در بازار وجود ندارد."
        } else rawReason
        val confidence = (obj["confidence"] as? JsonPrimitive)
            ?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
            ?.coerceIn(0, 100)
        val news = (obj["news"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val url = item.string("url").trim().take(1000)
            val uri = runCatching { URI(url) }.getOrNull()
            val host = uri?.host?.lowercase(Locale.ROOT).orEmpty()
            val title = safeDisplayText(item.string("title"), 240)
            // خبر HTTP قابل دست‌کاری است و نام میزبان برای مقابله با عنوان/منبع جعلی نمایش داده می‌شود.
            if (title.isBlank() || !uri?.scheme.equals("https", true) || host.isBlank() ||
                uri?.userInfo != null
            ) return@mapNotNull null
            NewsItem(
                title = title,
                url = url,
                relation = safeDisplayText(item.string("relation"), 500),
                source = safeDisplayText(item.string("source"), 120),
                publishedAt = safeDisplayText(item.string("publishedAt"), 80),
                host = host.take(253)
            )
        }.take(5)
        return Review(
            verdict = normalizeVerdict(rawVerdict),
            recommendation = recommendation,
            reason = reason,
            confidence = confidence,
            news = news,
            providerSearchRequested = providerSearchRequested,
            action = safeDisplayText(obj.string("action"), 300),
            entry = safeDisplayText(obj.string("entry"), 160),
            stopLoss = safeDisplayText(obj.string("stopLoss"), 160),
            targets = safeDisplayText(obj.string("targets"), 240),
            timeframe = safeDisplayText(obj.string("timeframe"), 120),
            invalidation = safeDisplayText(obj.string("invalidation"), 300),
            technical = safeDisplayText(obj.string("technical"), 900),
            project = safeDisplayText(obj.string("project"), 900),
            catalysts = safeDisplayText(obj.string("catalysts"), 600),
            risks = safeDisplayText(obj.string("risks"), 600),
            summary = safeDisplayText(obj.string("summary"), 600)
        )
    }

    private fun normalizeRecommendation(raw: String): String =
        allowedRecommendations.firstOrNull { raw.contains(it) }
            ?: PumpScanner.Recommendation.WAIT.label

    private fun normalizeVerdict(raw: String): String = when {
        raw.contains("محتاط‌تر") -> "محتاط‌تر"
        raw.contains("همسو") -> "همسو"
        else -> "نامطمئن"
    }

    /** حذف control/bidi override از متن کنترل‌نشده‌ی مدل؛ newline معمولی حفظ می‌شود. */
    private fun safeDisplayText(raw: String, maxLength: Int): String = raw
        .filter { char ->
            (char == '\n' || char == '\t' || !Character.isISOControl(char)) &&
                    char != '\u061C' && char !in '\u200E'..'\u200F' &&
                    char !in '\u202A'..'\u202E' && char !in '\u2066'..'\u2069'
        }
        .trim()
        .take(maxLength)

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun number(value: Double?): String =
        value?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.4f", it) } ?: "ناموجود"
}
