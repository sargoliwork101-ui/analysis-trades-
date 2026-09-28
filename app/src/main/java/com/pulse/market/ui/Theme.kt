package com.pulse.market.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pulse.market.R

private val PulseDarkColors = darkColorScheme(
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

// تم روشن — برای گوشی‌هایی که در حالت Light هستند. رنگ‌های نشانه‌ای (سبز سود/قرمز
// ضرر/کهربایی هشدار) در کل برنامه ثابت‌اند و روی هر دو پس‌زمینه خوانا می‌مانند؛ اینجا
// فقط نقش‌های ساختاری (پس‌زمینه، سطح، متن) برای پس‌زمینه‌ی روشن تنظیم شده‌اند.
private val PulseLightColors = lightColorScheme(
    primary = Color(0xFF15803D),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF0284C7),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE7EDF5),
    onSurfaceVariant = Color(0xFF52607A),
    error = Color(0xFFDC2626)
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

/** گوشه‌های نرم‌تر و امروزی‌تر برای کل کارت‌ها، دکمه‌ها، چیپ‌ها و دیالوگ‌ها */
private val pulseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun PulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) PulseDarkColors else PulseLightColors,
        typography = pulseTypography,
        shapes = pulseShapes,
        content = content
    )
}
