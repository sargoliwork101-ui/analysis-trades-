package com.pulse.market.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetThemeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun removedThemesMigrateToModernReplacements() {
        val aurora = json.decodeFromString(WidgetConfig.serializer(), """{"theme":"AURORA"}""")
        val neon = json.decodeFromString(WidgetConfig.serializer(), """{"theme":"NEON"}""")

        assertEquals(WidgetTheme.OCEAN, aurora.theme)
        assertEquals(WidgetTheme.MOCHA, neon.theme)
    }

    @Test
    fun themeListContainsOnlyCurrentChoices() {
        val names = WidgetTheme.entries.map { it.name }
        assertEquals(listOf("DARK", "LIGHT", "GLASS", "OCEAN", "MOCHA"), names)
        assertFalse("AURORA" in names)
        assertFalse("NEON" in names)
    }

    @Test
    fun newThemesSurviveConfigRoundTrip() {
        for (theme in listOf(WidgetTheme.OCEAN, WidgetTheme.MOCHA)) {
            val encoded = json.encodeToString(WidgetConfig.serializer(), WidgetConfig(theme = theme))
            val restored = json.decodeFromString(WidgetConfig.serializer(), encoded)
            assertEquals(theme, restored.theme)
        }
    }
}
