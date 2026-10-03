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
}
