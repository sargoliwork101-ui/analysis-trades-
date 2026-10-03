package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketNewsTest {

    @Test
    fun parsesRssCleansHtmlAndRejectsUnsafeLinks() {
        val xml = """
            <rss version="2.0"><channel>
              <item>
                <title>بانک مرکزی نرخ ارز را تغییر داد</title>
                <link>https://example.com/news/1</link>
                <description><![CDATA[<p>این تصمیم می‌تواند بر بازار <b>دلار و بورس</b> اثر بگذارد.</p>]]></description>
                <pubDate>Fri, 02 Oct 2026 10:30:00 GMT</pubDate>
                <source>منبع آزمون</source>
              </item>
              <item>
                <title>Bitcoin market update</title>
                <link>javascript:alert(1)</link>
                <description>Unsafe</description>
              </item>
            </channel></rss>
        """.trimIndent()

        val items = MarketNews.parseFeed(
            xml, "Feed", "https://example.com/rss", NewsRegion.IRAN
        )

        assertEquals(1, items.size)
        assertEquals("منبع آزمون", items[0].source)
        assertEquals("این تصمیم می تواند بر بازار دلار و بورس اثر بگذارد.", items[0].sourceSummary)
        assertEquals(NewsCategory.CURRENCY, items[0].category)
        assertTrue(items[0].publishedAt > 0L)
        assertTrue(items[0].url.startsWith("https://"))
    }

    @Test
    fun parsesAtomAndResolvesRelativeHttpsLink() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <title>Gold rises after interest rate decision</title>
                <link href="/markets/gold" />
                <summary>Gold and bullion markets reacted to the central bank.</summary>
                <updated>2026-10-02T12:00:00Z</updated>
              </entry>
            </feed>
        """.trimIndent()

        val item = MarketNews.parseFeed(
            xml, "World", "https://example.com/feed.xml", NewsRegion.WORLD
        ).single()

        assertEquals("https://example.com/markets/gold", item.url)
        assertEquals(NewsCategory.GOLD, item.category)
        assertEquals(NewsRegion.WORLD, item.region)
    }

    @Test
    fun filtersUnrelatedSearchResultsButAllowsSpecialistFallbackFeed() {
        val xml = """
            <rss><channel><item>
              <title>نتیجه مسابقه فوتبال</title>
              <link>https://example.com/sport</link>
              <description>خبر ورزشی روز</description>
            </item></channel></rss>
        """.trimIndent()

        assertTrue(
            MarketNews.parseFeed(xml, "Search", "https://example.com/rss", NewsRegion.IRAN).isEmpty()
        )
        val specialist = MarketNews.parseFeed(
            xml, "Economic", "https://example.com/rss", NewsRegion.IRAN,
            fallbackCategory = NewsCategory.ECONOMY,
            includeUnmatched = true
        )
        assertEquals(1, specialist.size)
        assertEquals(NewsCategory.ECONOMY, specialist.single().category)
    }

    @Test
    fun parsesIranianFeedDateWithoutWeekday() {
        assertTrue(MarketNews.parseDate("03 Oct 2026 09:23:17 +0330") > 0L)
    }

    @Test
    fun shortEnglishKeywordsRequireWordBoundaries() {
        assertEquals(NewsCategory.STOCKS, MarketNews.classify("Goldman Sachs stock outlook"))
        assertFalse(MarketNews.isRelevant("The Golden Globe movie awards"))
    }

    @Test
    fun onlyCompleteFivePartAiAnalysisIsDisplayReady() {
        val base = MarketNewsItem(
            id = "fa", title = "Original English title", sourceSummary = "English excerpt",
            url = "https://example.com/news", source = "Source", publishedAt = 1L,
            category = NewsCategory.ECONOMY, region = NewsRegion.WORLD, importance = 70
        )
        assertFalse(base.hasCompleteAiAnalysis)
        assertFalse(base.copy(aiTitle = "عنوان فارسی", aiSummary = "چکیده فارسی").hasCompleteAiAnalysis)
        assertTrue(
            base.copy(
                aiTitle = "عنوان فارسی",
                aiOutlook = "سناریوی محتمل بازار",
                marketImpact = "اثر احتمالی بر ارز",
                historicalContext = "الگوی تاریخی مشابه",
                aiSummary = "چکیده فارسی خبر"
            ).hasCompleteAiAnalysis
        )
    }

    @Test
    fun newsSortingKeepsUnknownDatesLastAndSupportsAllOrders() {
        fun item(id: String, publishedAt: Long, importance: Int) = MarketNewsItem(
            id = id, title = id, sourceSummary = "خلاصه بازار",
            url = "https://example.com/$id", source = "منبع", publishedAt = publishedAt,
            category = NewsCategory.ECONOMY, region = NewsRegion.IRAN, importance = importance
        )
        val news = listOf(
            item("old", 1_000L, 20),
            item("new", 3_000L, 50),
            item("important", 2_000L, 90),
            item("unknown", 0L, 100)
        )

        assertEquals(listOf("new", "important", "old", "unknown"), news.sortedFor(NewsSortOrder.NEWEST).map { it.id })
        assertEquals(listOf("old", "important", "new", "unknown"), news.sortedFor(NewsSortOrder.OLDEST).map { it.id })
        assertEquals(listOf("important", "new", "old", "unknown"), news.sortedFor(NewsSortOrder.IMPORTANT).map { it.id })
    }

    @Test
    fun freshBadgeUsesPublishedTimeAndRejectsFutureOrUnknown() {
        val now = 100L * FRESH_NEWS_MS
        val item = MarketNewsItem(
            id = "fresh", title = "خبر", sourceSummary = "خلاصه",
            url = "https://example.com/fresh", source = "منبع", publishedAt = now - 60_000L,
            category = NewsCategory.ECONOMY, region = NewsRegion.IRAN, importance = 50
        )
        assertTrue(item.isFresh(now))
        assertFalse(item.copy(publishedAt = now - FRESH_NEWS_MS - 1L).isFresh(now))
        assertFalse(item.copy(publishedAt = now + 1L).isFresh(now))
        assertFalse(item.copy(publishedAt = 0L).isFresh(now))
    }

    @Test
    fun deduplicateKeepsRicherCopy() {
        val base = MarketNewsItem(
            id = "a", title = "Bitcoin ETF approved", sourceSummary = "short",
            url = "https://a.example/news", source = "A", publishedAt = 1L,
            category = NewsCategory.CRYPTO, region = NewsRegion.WORLD, importance = 70
        )
        val richer = base.copy(
            id = "b", sourceSummary = "a much longer and more useful source summary",
            url = "https://b.example/news", source = "B", importance = 60
        )

        val result = MarketNews.deduplicate(listOf(base, richer))
        assertEquals(1, result.size)
        assertEquals("b", result.single().id)
        assertFalse(result.single().sourceSummary.isBlank())
    }
}
