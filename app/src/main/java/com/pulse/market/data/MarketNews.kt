package com.pulse.market.data

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

@Serializable
enum class NewsCategory(val label: String) {
    CRYPTO("کریپتو"),
    GOLD("طلا و سکه"),
    CURRENCY("ارز"),
    STOCKS("بورس"),
    ECONOMY("اقتصاد")
}

@Serializable
enum class NewsRegion(val label: String) {
    IRAN("ایران"),
    WORLD("جهان")
}

enum class NewsSortOrder(val label: String) {
    NEWEST("جدیدترین"),
    OLDEST("قدیمی‌ترین"),
    IMPORTANT("مهم‌ترین")
}

/** یک خبرِ پاک‌سازی‌شده؛ متن AI فقط غنی‌سازی است و لینک/منبع اصلی را عوض نمی‌کند. */
@Serializable
data class MarketNewsItem(
    val id: String,
    val title: String,
    /** چکیدهٔ خام RSS؛ فقط ورودی تحلیل است و تا فارسی/کامل نشود در کارت نمایش داده نمی‌شود. */
    val sourceSummary: String,
    val url: String,
    val source: String,
    val publishedAt: Long,
    val category: NewsCategory,
    val region: NewsRegion,
    /** امتیاز تقریبی ۰..۱۰۰ برای مرتب‌سازی؛ در صورت AI با برآورد مدل غنی می‌شود. */
    val importance: Int,
    /** عنوان فارسیِ تولیدشده از همان خبر؛ عنوان/لینک اصلی دست‌نخورده می‌ماند. */
    val aiTitle: String = "",
    val aiOutlook: String = "",
    val marketImpact: String = "",
    val historicalContext: String = "",
    val aiSummary: String = ""
) {
    val displayTitle: String
        get() = aiTitle.ifBlank { title }

    /** فقط کارتِ دارای پنج بخش فارسی و کامل اجازه‌ی نمایش دارد. */
    val hasCompleteAiAnalysis: Boolean
        get() = aiTitle.isNotBlank() && aiOutlook.isNotBlank() &&
            marketImpact.isNotBlank() && historicalContext.isNotBlank() && aiSummary.isNotBlank()

    /** میزبان واقعی لینک؛ نام منبعِ RSS به‌تنهایی قابل اعتماد نیست. */
    val host: String
        get() = runCatching { URI(url).host.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")

    /** برچسب «تازه» برای خبرهای منتشرشده در ۲۴ ساعت اخیر؛ ساعت آینده/نامعتبر تازه نیست. */
    fun isFresh(now: Long = System.currentTimeMillis()): Boolean =
        publishedAt > 0L && now - publishedAt in 0L..FRESH_NEWS_MS
}

internal const val FRESH_NEWS_MS = 24L * 60L * 60L * 1000L

/** تاریخ نامشخص همیشه آخر می‌ماند؛ حتی در مرتب‌سازی قدیمی‌ترین. */
fun List<MarketNewsItem>.sortedFor(order: NewsSortOrder): List<MarketNewsItem> = when (order) {
    NewsSortOrder.NEWEST -> sortedWith(
        compareByDescending<MarketNewsItem> { it.publishedAt > 0L }
            .thenByDescending { it.publishedAt }
            .thenByDescending { it.importance }
    )
    NewsSortOrder.OLDEST -> sortedWith(
        compareByDescending<MarketNewsItem> { it.publishedAt > 0L }
            .thenBy { if (it.publishedAt > 0L) it.publishedAt else Long.MAX_VALUE }
            .thenByDescending { it.importance }
    )
    NewsSortOrder.IMPORTANT -> sortedWith(
        compareByDescending<MarketNewsItem> { it.publishedAt > 0L }
            .thenByDescending { it.importance }
            .thenByDescending { it.publishedAt }
    )
}

data class MarketNewsFeed(
    val items: List<MarketNewsItem>,
    val fetchedAt: Long,
    val failedSources: List<String>
)

/**
 * تجمیع خبرهای بازار از RSSهای مستقل ایران/جهان. یک منبع خراب کل صفحه را از کار نمی‌اندازد؛
 * خبرها محدود، پاک‌سازی، دسته‌بندی، dedupe و بر اساس تازگی/اثر احتمالی مرتب می‌شوند.
 */
object MarketNews {
    private data class Source(
        val name: String,
        val url: String,
        val region: NewsRegion,
        val fallbackCategory: NewsCategory? = null,
        /** فقط منابع کاملاً تخصصی اجازه دارند تیترِ بدون کلیدواژه را هم وارد کنند. */
        val includeUnmatched: Boolean = false,
        val maxItems: Int = 14
    )

    private fun encoded(query: String): String =
        URLEncoder.encode(query, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private val sources: List<Source> = listOf(
        Source(
            "اخبار ایران",
            "https://news.google.com/rss/search?q=" +
                encoded("(دلار OR ارز OR طلا OR سکه OR بورس OR سهام OR رمزارز OR بیت کوین OR بانک مرکزی) when:3d") +
                "&hl=fa&gl=IR&ceid=IR:fa",
            NewsRegion.IRAN,
            maxItems = 18
        ),
        Source(
            "نبض بورس",
            "https://nabzebourse.com/fa/rss/allnews",
            NewsRegion.IRAN,
            fallbackCategory = NewsCategory.STOCKS,
            includeUnmatched = true,
            maxItems = 12
        ),
        Source(
            "اقتصاد ۲۴",
            "https://eghtesaad24.ir/fa/rss/allnews",
            NewsRegion.IRAN,
            fallbackCategory = NewsCategory.ECONOMY,
            maxItems = 12
        ),
        Source(
            "اقتصاد آنلاین",
            "https://www.eghtesadonline.com/fa/updates/allnews",
            NewsRegion.IRAN,
            fallbackCategory = NewsCategory.ECONOMY,
            maxItems = 12
        ),
        Source(
            "بازارهای جهان",
            "https://news.google.com/rss/search?q=" +
                encoded("(cryptocurrency OR bitcoin OR gold OR forex OR stock market OR central bank OR oil) when:3d") +
                "&hl=en-US&gl=US&ceid=US:en",
            NewsRegion.WORLD,
            maxItems = 18
        ),
        Source(
            "CoinDesk",
            "https://www.coindesk.com/arc/outboundfeeds/rss/",
            NewsRegion.WORLD,
            fallbackCategory = NewsCategory.CRYPTO,
            includeUnmatched = true,
            maxItems = 12
        ),
        Source(
            "CNBC Finance",
            "https://www.cnbc.com/id/10000664/device/rss/rss.html",
            NewsRegion.WORLD,
            fallbackCategory = NewsCategory.ECONOMY,
            includeUnmatched = true,
            maxItems = 12
        )
    )

    private val categoryTerms: Map<NewsCategory, List<String>> = mapOf(
        NewsCategory.CRYPTO to listOf(
            "crypto", "cryptocurrency", "bitcoin", "btc", "ethereum", "ether", "blockchain",
            "stablecoin", "token", "defi", "رمزارز", "ارز دیجیتال", "بیت کوین", "بیت‌کوین",
            "اتریوم", "تتر", "صرافی ارز دیجیتال"
        ),
        NewsCategory.GOLD to listOf(
            "gold", "bullion", "precious metal", "ounce", "xau", "طلا", "سکه", "اونس",
            "مثقال", "طلای ۱۸", "نیم سکه", "ربع سکه"
        ),
        NewsCategory.CURRENCY to listOf(
            "forex", "currency", "currencies", "dollar", "euro", "exchange rate", "rial", "dxy",
            "ارز", "دلار", "یورو", "درهم", "ریال", "نرخ تسعیر", "بازار متشکل"
        ),
        NewsCategory.STOCKS to listOf(
            "stock", "stocks", "shares", "equities", "wall street", "nasdaq", "s&p", "dow jones",
            "بورس", "فرابورس", "سهام", "شاخص کل", "عرضه اولیه", "کدال", "وال استریت"
        ),
        NewsCategory.ECONOMY to listOf(
            "economy", "economic", "inflation", "interest rate", "central bank", "federal reserve",
            "fed", "tariff", "sanction", "oil", "opec", "gdp", "اقتصاد", "تورم", "نرخ بهره",
            "بانک مرکزی", "تحریم", "نفت", "اوپک", "تجارت", "بودجه"
        )
    )

    private val importantTerms = listOf(
        "central bank", "federal reserve", "interest rate", "rate cut", "rate hike", "inflation",
        "sanction", "war", "attack", "ceasefire", "regulation", "sec ", "etf", "hack", "bankrupt",
        "liquidation", "record high", "crash", "tariff", "opec", "بانک مرکزی", "نرخ بهره", "تورم",
        "تحریم", "جنگ", "حمله", "آتش بس", "آتش‌بس", "قانون", "هک", "ورشکست", "سقف تاریخی",
        "ریزش", "رکورد", "عرضه اولیه", "تصویب", "ممنوعیت"
    )

    suspend fun fetch(): MarketNewsFeed = supervisorScope {
        val results = sources.map { source ->
            async(Dispatchers.IO) {
                try {
                    val xml = Http.getText(
                        source.url,
                        userAgent = Http.UA_DESKTOP,
                        accept = "application/rss+xml, application/atom+xml, application/xml, text/xml, */*",
                        maxBytes = 768L * 1024
                    )
                    source to parseFeed(
                        xml, source.name, source.url, source.region,
                        source.fallbackCategory, source.includeUnmatched
                    ).take(source.maxItems)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    source to null
                }
            }
        }.awaitAll()

        val failed = results.filter { it.second == null }.map { it.first.name }
        val merged = deduplicate(results.flatMap { it.second.orEmpty() })
            .sortedWith(compareByDescending<MarketNewsItem> { it.importance }
                .thenByDescending { it.publishedAt })
            .take(40)
        MarketNewsFeed(merged, System.currentTimeMillis(), failed)
    }

    /** خبرهای قدیمی‌تر تا این مدت نگه داشته می‌شوند، حتی وقتی از فید منبع بیرون رفته‌اند. */
    const val HISTORY_KEEP_MS = 7L * 24L * 60L * 60L * 1000L

    /** سقف کل فهرست (تازه + نگه‌داشته‌شده) تا حافظه و کش از کنترل خارج نشود. */
    const val HISTORY_MAX_ITEMS = 80

    /**
     * فید RSS فقط یک پنجره‌ی کوچک از آخرین خبرهاست؛ با هر تازه‌سازی، خبرهای دیروز از
     * آن بیرون می‌افتند. قبلاً همان‌ها از برنامه هم پاک می‌شدند و کاربر خبری را که
     * خوانده بود دیگر پیدا نمی‌کرد. اینجا خبرهای تازه با خبرهای قبلی ادغام می‌شوند:
     * تحلیل AI و امتیاز قبلی حفظ می‌شود، تکراری‌ها یکی می‌شوند و فقط خبرهای کهنه‌تر
     * از یک هفته (یا بیرون از سقف فهرست) کنار گذاشته می‌شوند.
     */
    fun mergeWithHistory(
        existing: List<MarketNewsItem>,
        fresh: List<MarketNewsItem>,
        now: Long = System.currentTimeMillis(),
        keepMillis: Long = HISTORY_KEEP_MS,
        maxItems: Int = HISTORY_MAX_ITEMS
    ): List<MarketNewsItem> {
        if (fresh.isEmpty()) return existing
        val previous = existing.associateBy { it.id }
        val refreshed = fresh.map { item ->
            val old = previous[item.id] ?: return@map item
            if (!old.hasCompleteAiAnalysis) item else item.copy(
                aiTitle = old.aiTitle,
                aiOutlook = old.aiOutlook,
                marketImpact = old.marketImpact,
                historicalContext = old.historicalContext,
                aiSummary = old.aiSummary,
                importance = maxOf(item.importance, old.importance)
            )
        }
        val freshIds = refreshed.mapTo(hashSetOf()) { it.id }
        // خبرِ بدون تاریخ معتبر هم نگه داشته می‌شود؛ نبودن تاریخ دلیل حذف نیست.
        val kept = existing.filter { old ->
            old.id !in freshIds &&
                (old.publishedAt <= 0L || now - old.publishedAt <= keepMillis)
        }
        return (refreshed + kept)
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<MarketNewsItem> { it.publishedAt > 0L }
                    .thenByDescending { it.publishedAt }
                    .thenByDescending { it.importance }
            )
            .take(maxItems.coerceAtLeast(fresh.size))
    }

    /** parser خالص RSS/Atom برای تست؛ فقط URL امن HTTPS وارد مدل می‌شود. */
    internal fun parseFeed(
        xml: String,
        sourceName: String,
        feedUrl: String,
        region: NewsRegion,
        fallbackCategory: NewsCategory? = null,
        includeUnmatched: Boolean = false
    ): List<MarketNewsItem> {
        val doc = Jsoup.parse(xml, feedUrl, Parser.xmlParser())
        return doc.select("item, entry").mapNotNull { node ->
            val title = childText(node, "title").cleanText(240)
            if (title.isBlank()) return@mapNotNull null
            val linkNode = child(node, "link")
            val rawLink = linkNode?.attr("href")?.takeIf { it.isNotBlank() }
                ?: linkNode?.text().orEmpty()
            val url = safeArticleUrl(rawLink, feedUrl) ?: return@mapNotNull null
            val rawSummary = childText(node, "description", "summary", "content", "content:encoded")
            val sourceSummary = htmlText(rawSummary).cleanText(650)
                .takeUnless { normalizeTitle(it) == normalizeTitle(title) }.orEmpty()
            val published = parseDate(childText(node, "pubDate", "published", "updated", "dc:date"))
            val itemSource = childText(node, "source").cleanText(80).ifBlank { sourceName.take(80) }
            val combinedText = "$title $sourceSummary"
            val category = classify(combinedText, fallbackCategory)
            if (!includeUnmatched && !isRelevant(combinedText)) return@mapNotNull null
            val importance = importance(title, sourceSummary, published)
            MarketNewsItem(
                id = stableId(url, title),
                title = title,
                sourceSummary = sourceSummary,
                url = url,
                source = itemSource,
                publishedAt = published,
                category = category,
                region = region,
                importance = importance
            )
        }
    }

    internal fun classify(text: String, fallback: NewsCategory? = null): NewsCategory {
        val lower = normalizedText(text)
        val scored = categoryTerms.mapValues { (_, terms) -> terms.count { containsTerm(lower, it) } }
        val best = scored.maxByOrNull { it.value }
        return if (best != null && best.value > 0) best.key else fallback ?: NewsCategory.ECONOMY
    }

    internal fun isRelevant(text: String): Boolean {
        val lower = normalizedText(text)
        return categoryTerms.values.flatten().any { containsTerm(lower, it) }
    }

    internal fun deduplicate(items: List<MarketNewsItem>): List<MarketNewsItem> {
        val byKey = linkedMapOf<String, MarketNewsItem>()
        for (item in items.sortedByDescending { it.importance }) {
            val titleKey = normalizeTitle(item.title)
            val key = titleKey.take(180).ifBlank { item.url }
            val previous = byKey[key]
            if (previous == null || item.sourceSummary.length > previous.sourceSummary.length) {
                byKey[key] = item
            }
        }
        return byKey.values.toList()
    }

    private fun child(node: Element, vararg names: String): Element? = node.children().firstOrNull { c ->
        names.any { name -> c.tagName().equals(name, ignoreCase = true) }
    }

    private fun childText(node: Element, vararg names: String): String =
        child(node, *names)?.text().orEmpty()

    private fun htmlText(raw: String): String = if (raw.isBlank()) "" else
        Jsoup.parseBodyFragment(raw).text()

    private fun String.cleanText(limit: Int): String =
        replace('\u200c', ' ')
            // کنترل‌های نامرئی و bidi نباید نام منبع/تیتر را جعل یا جهت لینک را گمراه کنند.
            .replace(Regex("[\\u0000-\\u001F\\u007F\\u202A-\\u202E\\u2066-\\u2069]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(limit)

    private fun normalizedText(text: String): String = text
        .lowercase(Locale.ROOT)
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('\u200c', ' ')

    private fun normalizeTitle(text: String): String = normalizedText(text)
        .replace(Regex("[–—|-]\\s*[^–—|-]{2,40}$"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun safeArticleUrl(raw: String, feedUrl: String): String? = runCatching {
        val resolved = URI(feedUrl).resolve(raw.trim())
        resolved.takeIf {
            it.scheme.equals("https", true) && !it.host.isNullOrBlank() && it.userInfo == null
        }?.toASCIIString()
    }.getOrNull()

    private fun stableId(url: String, title: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest("$url|${normalizeTitle(title)}".toByteArray(StandardCharsets.UTF_8))
        return bytes.take(10).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun importance(title: String, summary: String, publishedAt: Long): Int {
        val text = normalizedText("$title $summary")
        val age = if (publishedAt > 0L) (System.currentTimeMillis() - publishedAt).coerceAtLeast(0L) else Long.MAX_VALUE
        val freshness = when {
            age <= 6 * 60 * 60_000L -> 32
            age <= 24 * 60 * 60_000L -> 24
            age <= 3 * 24 * 60 * 60_000L -> 14
            else -> 4
        }
        val impact = importantTerms.count { containsTerm(text, it) }.coerceAtMost(4) * 8
        val detail = if (summary.length >= 80) 7 else if (summary.isNotBlank()) 3 else 0
        return (30 + freshness + impact + detail).coerceIn(20, 98)
    }

    /** واژه‌های کوتاه لاتین باید مرز داشته باشند؛ مثلاً gold نباید Goldman را «طلا» حساب کند. */
    private fun containsTerm(text: String, rawTerm: String): Boolean {
        val term = rawTerm.trim()
        val shortAsciiWord = term.length <= 4 && term.all { it in 'a'..'z' || it in '0'..'9' }
        return if (shortAsciiWord) {
            Regex("(?<![a-z0-9])${Regex.escape(term)}(?![a-z0-9])").containsMatchIn(text)
        } else {
            text.contains(term)
        }
    }

    private val datePatterns = listOf(
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss z",
        "dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    internal fun parseDate(raw: String): Long {
        val clean = raw.trim()
        if (clean.isBlank()) return 0L
        for (pattern in datePatterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(clean)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return 0L
    }
}
