package com.pulse.market.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * تست‌های زبان مسیریابی JSON — همان چیزی که همه‌ی منابع رویش سوارند.
 * اگر روزی JsonPath خراب شود، هیچ منبعی قیمت نمی‌دهد؛ پس اینجا قفل می‌شود.
 */
class JsonPathTest {

    @Test
    fun readsNestedField() {
        val root = JSONObject("""{"closingPriceInfo":{"pClosing":12345.5}}""")
        assertEquals(12345.5, JsonPath.readDouble(root, "closingPriceInfo.pClosing")!!, 1e-9)
    }

    @Test
    fun readsArrayIndexAtRoot() {
        val root = JSONArray("""[{"lastValue":2000}]""")
        assertEquals(2000.0, JsonPath.readDouble(root, "[0].lastValue")!!, 1e-9)
    }

    @Test
    fun alternativePathsReturnFirstHit() {
        // مثل شاخص کل بورس: هر سه شکل پاسخ باید پشتیبانی شود
        val root = JSONObject("""{"lastValue":7}""")
        val path = "[0].lastValue | indexB1LastAll[0].lastValue | lastValue"
        assertEquals(7.0, JsonPath.readDouble(root, path)!!, 1e-9)
    }

    @Test
    fun readsTgjuStyleStringNumbers() {
        // TGJU عدد را رشته‌ی «قشنگ‌شده» با کاما می‌دهد
        val root = JSONObject("""{"current":{"ons":{"p":"4,310.94","dp":1.08}}}""")
        assertEquals(4310.94, JsonPath.readDouble(root, "current.ons.p")!!, 1e-6)
        assertEquals(1.08, JsonPath.readDouble(root, "current.ons.dp")!!, 1e-9)
    }

    @Test
    fun readsDoubleListAndSkipsJunk() {
        val root = JSONObject("""{"spark":[1,"2",null,"x",4]}""")
        assertEquals(listOf(1.0, 2.0, 4.0), JsonPath.readDoubleList(root, "spark"))
    }

    @Test
    fun missingPathIsNull() {
        assertNull(JsonPath.readDouble(JSONObject("""{"a":1}"""), "b.c"))
        assertNull(JsonPath.readDouble(JSONObject("""{"a":1}"""), ""))
    }
}
