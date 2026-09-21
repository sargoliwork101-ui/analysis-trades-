package com.pulse.market.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pulse.market.R

private val PulseColors = darkColorScheme(
    primary = Color(0xFF22C55E),
    onPrimary = Color(0xFF05230F),
    secondary = Color(0xFF38BDF8),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF111C31),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF16233C),
    onSurfaceVariant = Color(0xFF8B9AB1),
    error = Color(0xFFF43F5E)
)

/** فونت وزیرمتن — از res/font خوانده می‌شود */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

private val pulseTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Vazirmatn),
        displayMedium = t.displayMedium.copy(fontFamily = Vazirmatn),
        displaySmall = t.displaySmall.copy(fontFamily = Vazirmatn),
        headlineLarge = t.headlineLarge.copy(fontFamily = Vazirmatn),
        headlineMedium = t.headlineMedium.copy(fontFamily = Vazirmatn),
        headlineSmall = t.headlineSmall.copy(fontFamily = Vazirmatn),
        titleLarge = t.titleLarge.copy(fontFamily = Vazirmatn),
        titleMedium = t.titleMedium.copy(fontFamily = Vazirmatn),
        titleSmall = t.titleSmall.copy(fontFamily = Vazirmatn),
        bodyLarge = t.bodyLarge.copy(fontFamily = Vazirmatn),
        bodyMedium = t.bodyMedium.copy(fontFamily = Vazirmatn),
        bodySmall = t.bodySmall.copy(fontFamily = Vazirmatn),
        labelLarge = t.labelLarge.copy(fontFamily = Vazirmatn),
        labelMedium = t.labelMedium.copy(fontFamily = Vazirmatn),
        labelSmall = t.labelSmall.copy(fontFamily = Vazirmatn)
    )
}

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PulseColors,
        typography = pulseTypography,
        content = content
    )
}
