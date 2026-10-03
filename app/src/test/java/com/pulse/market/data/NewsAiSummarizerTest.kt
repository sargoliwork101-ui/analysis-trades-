package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsAiSummarizerTest {

    @Test
    fun parsesFencedJsonAndClampsImportance() {
        val raw = """
            ```json
            {"items":[
              {"id":"n1","summary":"  خلاصه   فارسی  ","marketImpact":"اثر مشروط بر طلا","importance":140},
              {"id":"n2","summary":"خبر دوم","marketImpact":"","importance":-5}
            ]}
            ```
        """.trimIndent()

        val result = NewsAiSummarizer.parse(raw)

        assertEquals(2, result.size)
        assertEquals("خلاصه فارسی", result[0].summary)
        assertEquals(100, result[0].importance)
        assertEquals(0, result[1].importance)
    }

    @Test
    fun ignoresBlankAndDuplicateIds() {
        val raw = """
            {"items":[
              {"id":"","summary":"بدون شناسه"},
              {"id":"x","summary":"اول"},
              {"id":"x","summary":"دوم"},
              {"id":"y","summary":""}
            ]}
        """.trimIndent()

        val result = NewsAiSummarizer.parse(raw)
        assertEquals(1, result.size)
        assertEquals("اول", result.single().summary)
        assertNull(result.single().importance)
    }

    @Test
    fun malformedAnswerIsSafeEmptyResult() {
        assertTrue(NewsAiSummarizer.parse("not json").isEmpty())
    }
}
