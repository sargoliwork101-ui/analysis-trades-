package com.pulse.market.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PumpAiReviewerTest {

    @Test
    fun endpointAcceptsBaseV1AndFullUrls() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            PumpAiReviewer.chatCompletionsEndpoint("https://example.com")
        )
        assertEquals(
            "https://example.com/v1/chat/completions",
            PumpAiReviewer.chatCompletionsEndpoint("https://example.com/v1/")
        )
        assertEquals(
            "https://example.com/custom/chat/completions",
            PumpAiReviewer.chatCompletionsEndpoint("https://example.com/custom/chat/completions")
        )
    }

    @Test
    fun reviewParsesCautiousResultAndOnlyValidNewsLinks() {
        val review = PumpAiReviewer.parseReview(
            """
            {
              "verdict":"محتاط‌تر",
              "recommendation":"فعلاً وارد نشو؛ قیمت را تعقیب نکن",
              "reason":"حجم غیرعادی است و خبر هنوز تأیید گسترده ندارد.",
              "confidence":84,
              "news":[
                {"title":"خبر معتبر","url":"https://news.example/item","relation":"افزایش حجم","source":"Example"},
                {"title":"لینک خطرناک","url":"file:///secret","relation":"نامعتبر"}
              ]
            }
            """.trimIndent()
        )
        assertEquals("فعلاً وارد نشو؛ قیمت را تعقیب نکن", review.recommendation)
        assertEquals(84, review.confidence)
        assertEquals(1, review.news.size)
        assertEquals("https://news.example/item", review.news.single().url)
        assertEquals("news.example", review.news.single().host)
    }

    @Test
    fun directBuyAdviceIsRejected() {
        val review = PumpAiReviewer.parseReview(
            """{"verdict":"همسو","recommendation":"فعلاً فقط زیر نظر بگیر","reason":"حتماً بخر؛ سود تضمینی است","confidence":99,"news":[]}"""
        )
        assertEquals("صبر کن؛ ورود عجولانه نکن", review.recommendation)
        assertTrue(review.reason.contains("برای ایمنی رد شد"))
    }

    @Test
    fun malformedResponseFallsBackToWait() {
        val review = PumpAiReviewer.parseReview("پاسخ آزاد و بدون ساختار")
        assertEquals("صبر کن؛ ورود عجولانه نکن", review.recommendation)
        assertEquals("نامطمئن", review.verdict)
        assertNull(review.confidence)
    }
}
