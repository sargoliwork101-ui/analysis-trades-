package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsAiSummarizerTest {

    @Test
    fun parsesCompletePersianAnalysisAndClampsImportance() {
        val raw = """
            ```json
            {"items":[
              {"id":"n1","persianTitle":"افزایش نرخ بهره آمریکا","outlook":"فشار بر دارایی‌های پرریسک ممکن است بیشتر شود.","marketImpact":"دلار می‌تواند تقویت و طلا یا سهام تضعیف شود.","historicalContext":"در دوره‌های افزایش نرخ، معمولاً نوسان سهام بیشتر شده است.","summary":"بانک مرکزی نرخ بهره را افزایش داد.","importance":140},
              {"id":"n2","persianTitle":"خبر دوم بازار","outlook":"واکنش بازار به جزئیات بعدی وابسته است.","marketImpact":"اثر اصلی می‌تواند بر بازار ارز باشد.","historicalContext":"سابقهٔ قابل‌اتکایی برای مقایسه در دسترس نیست.","summary":"  خلاصه   فارسی  ","importance":-5}
            ]}
            ```
        """.trimIndent()

        val result = NewsAiSummarizer.parse(raw)

        assertEquals(2, result.size)
        assertEquals("افزایش نرخ بهره آمریکا", result[0].persianTitle)
        assertEquals("خلاصه فارسی", result[1].summary)
        assertEquals(100, result[0].importance)
        assertEquals(0, result[1].importance)
    }

    @Test
    fun ignoresIncompleteAndDuplicateIds() {
        fun complete(id: String, summary: String) =
            """{"id":"$id","persianTitle":"عنوان فارسی","outlook":"سناریوی محتمل مشروط است","marketImpact":"بازار ارز ممکن است نوسان کند","historicalContext":"سابقهٔ قابل اتکایی در دسترس نیست","summary":"$summary"}"""
        val raw = """
            {"items":[
              ${complete("", "بدون شناسه")},
              ${complete("x", "خلاصه اول")},
              ${complete("x", "خلاصه دوم")},
              {"id":"y","persianTitle":"عنوان ناقص","summary":"خلاصه ناقص"}
            ]}
        """.trimIndent()

        val result = NewsAiSummarizer.parse(raw)
        assertEquals(1, result.size)
        assertEquals("خلاصه اول", result.single().summary)
        assertNull(result.single().importance)
    }

    @Test
    fun rejectsPredominantlyEnglishOutput() {
        val raw = """
            {"items":[{"id":"x","persianTitle":"Bitcoin market update ترجمه",
            "outlook":"Market prices will probably fall تحلیل",
            "marketImpact":"Crypto assets may decline بازار","historicalContext":"Past events were volatile سابقه",
            "summary":"The market moved after the decision خبر"}]}
        """.trimIndent()

        assertTrue(NewsAiSummarizer.parse(raw).isEmpty())
        assertTrue(NewsAiSummarizer.isPersianEnough("قیمت بیت کوین ممکن است نوسان کند"))
    }

    @Test
    fun malformedAnswerIsSafeEmptyResult() {
        assertTrue(NewsAiSummarizer.parse("not json").isEmpty())
    }

    private fun newsItem(id: String, title: String, published: Long = 1_700_000_000_000L) = MarketNewsItem(
        id = id,
        title = title,
        sourceSummary = "متن کامل خبر که در مسیر جمع‌بندی اصلاً فرستاده نمی‌شود و فقط تیتر می‌رود.",
        url = "https://example.com/$id",
        source = "CoinDesk",
        publishedAt = published,
        category = NewsCategory.CRYPTO,
        region = NewsRegion.WORLD,
        importance = 70
    )

    @Test
    fun briefingKeepsOnlyRealNewsIdsAndPersianText() {
        val raw = """
            ```json
            {"headline":"ریسک‌پذیری بازار کم شده است",
             "summary":"چند خبر هم‌زمان از سخت‌گیری پولی و خروج نقدینگی خبر می‌دهند و فشار روی دارایی‌های پرریسک را بیشتر کرده‌اند.",
             "outlook":"اگر داده‌های تورمی هفتهٔ بعد بالا بماند، فشار ادامه می‌یابد؛ در غیر این صورت اصلاح کوتاه‌مدت محتمل است.",
             "marketImpact":"کریپتو و سهام محتمل‌ترین بازارهای اثرپذیرند و دلار می‌تواند تقویت شود.",
             "watch":"نشست بانک مرکزی و داده‌های تورم را دنبال کن.",
             "basedOn":[{"id":"n1","why":"مستقیم‌ترین خبر دربارهٔ سیاست پولی است."},
                        {"id":"ghost","why":"خبری که در فهرست نبود"},
                        {"id":"n2","why":"خروج نقدینگی از صندوق‌ها را نشان می‌دهد."},
                        {"id":"n1","why":"تکراری"}]}
            ```
        """.trimIndent()

        val parsed = NewsAiSummarizer.parseBriefing(raw, setOf("n1", "n2"))

        assertTrue(parsed != null && parsed.usable)
        assertEquals(listOf("n1", "n2"), parsed!!.sources.map { it.id })
        assertTrue(parsed.headline.contains("ریسک‌پذیری"))
        assertTrue(parsed.watch.isNotBlank())
    }

    @Test
    fun briefingRejectsEnglishOrEmptyAnswers() {
        assertNull(
            NewsAiSummarizer.parseBriefing(
                """{"headline":"Market risk is down","summary":"Several stories point to tighter policy.","basedOn":[]}""",
                setOf("n1")
            )
        )
        assertNull(NewsAiSummarizer.parseBriefing("پاسخ آزاد بدون ساختار", setOf("n1")))
    }

    @Test
    fun briefingPromptSendsOnlyHeadlinesAndStaysSmall() {
        val items = (1..25).map { newsItem("n$it", "Headline number $it about the market") }
        val now = 1_700_003_600_000L
        val prompt = NewsAiSummarizer.briefingUserPrompt(items, now)

        // فقط تیتر می‌رود، نه متن خبر و نه لینک.
        assertTrue(!prompt.contains("متن کامل خبر"))
        assertTrue(!prompt.contains("https://example.com"))
        assertTrue(prompt.contains("Headline number 1"))
        // سقف تعداد رعایت می‌شود تا هزینه ثابت بماند.
        assertTrue(!prompt.contains("Headline number 19"))
        assertTrue(prompt.contains("\"ageHours\":1"))
        assertTrue(prompt.length < 3_000)

        // امضا با تغییر فهرست عوض می‌شود و با همان فهرست ثابت می‌ماند.
        assertEquals(NewsAiSummarizer.signature(items), NewsAiSummarizer.signature(items))
        assertTrue(NewsAiSummarizer.signature(items) != NewsAiSummarizer.signature(items.drop(1)))
    }

    @Test
    fun briefingInstructionsForbidInventedSourcesAndAdvice() {
        val system = NewsAiSummarizer.briefingSystemPrompt()
        assertTrue(system.contains("basedOn"))
        assertTrue(system.contains("لینک تازه نساز"))
        assertTrue(system.contains("توصیهٔ خرید یا فروش نده"))
    }
}
