package com.pulse.market.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.pulse.market.R
import com.pulse.market.data.Quote
import com.pulse.market.data.MarketStatus
import com.pulse.market.data.WidgetConfig
import com.pulse.market.data.WidgetTheme
import com.pulse.market.ui.MainActivity
import com.pulse.market.ui.Sparkline
import com.pulse.market.ui.Format
import com.pulse.market.ui.QuoteText

/** ساخت ظاهر ویجت — کاملاً داینامیک با اندازه‌ی ویجت، اندازه‌ی فونت و مقادیر انتخابی هر ویجت */
object WidgetRenderer {

    private const val COLOR_UP = 0xFF22C55E.toInt()
    private const val COLOR_DOWN = 0xFFF43F5E.toInt()
    private const val COLOR_FLAT = 0xFF94A3B8.toInt()

    /** اندازه‌ی پایه‌ی فونت‌ها (sp) قبل از اعمال ضریب کاربر و اندازه‌ی ویجت */
    private const val SZ_TITLE = 12f
    private const val SZ_TIME = 10f
    private const val SZ_STATUS = 9f
    private const val SZ_LABEL = 12.5f
    private const val SZ_SUB = 9.5f
    private const val SZ_PRICE = 14.5f
    private const val SZ_CHANGE = 9.5f

    /** رنگ‌ها و پس‌زمینه‌ی هر تم — شامل دو رنگ متناوب برای جداکننده‌ی ردیف‌ها */
    private data class Palette(
        val bgRes: Int,
        val rowA: Int,
        val rowB: Int,
        val text: Int,
        val sub: Int,
        val divider: Int
    )

    private fun palette(theme: WidgetTheme): Palette = when (theme) {
        WidgetTheme.DARK -> Palette(
            R.drawable.widget_bg_dark, R.drawable.row_bg_dark_a, R.drawable.row_bg_dark_b,
            0xFFF1F5F9.toInt(), 0xFF8B9AB1.toInt(), 0xFF1B2740.toInt()
        )

        WidgetTheme.LIGHT -> Palette(
            R.drawable.widget_bg_light, R.drawable.row_bg_light_a, R.drawable.row_bg_light_b,
            0xFF0F172A.toInt(), 0xFF64748B.toInt(), 0xFFE2E8F0.toInt()
        )

        WidgetTheme.GLASS -> Palette(
            R.drawable.widget_bg_glass, R.drawable.row_bg_glass_a, R.drawable.row_bg_glass_b,
            0xFFEAF2FF.toInt(), 0xFF9DB4D4.toInt(), 0xFF3A4E73.toInt()
        )

        WidgetTheme.AURORA -> Palette(
            R.drawable.widget_bg_aurora, R.drawable.row_bg_aurora_a, R.drawable.row_bg_aurora_b,
            0xFFFFFFFF.toInt(), 0xFFDDD6FE.toInt(), 0xFF6D5BD0.toInt()
        )

        WidgetTheme.NEON -> Palette(
            R.drawable.widget_bg_neon, R.drawable.row_bg_neon_a, R.drawable.row_bg_neon_b,
            0xFFF5F3FF.toInt(), 0xFF67E8F9.toInt(), 0xFF3B0764.toInt()
        )
    }

    fun render(
        context: Context,
        widgetId: Int,
        cfg: WidgetConfig,
        quotes: List<Quote>,
        live: Boolean,
        updatedAt: Long,
        sourceTitle: String = ""
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_stock)
        val pal = palette(cfg.theme)

        // ── اندازه‌ی واقعی ویجت ← تعداد ردیف، نمایش نمودار و ضریب فونت ──
        val opts = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val sizeH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 }
            ?: opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
        val sizeW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).takeIf { it > 0 }
            ?: opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)

        // فونت‌ها و محتوا کاملاً با اندازه‌ی ویجت بزرگ/کوچک می‌شوند + ضریب دلخواه کاربر
        val scale = cfg.fontScale.coerceIn(0.75f, 1.5f) * sizeFactor(sizeW, sizeH)

        val rowCapacity = (((sizeH - 84f) / 50f) + 1f).toInt().coerceIn(1, 6)
        val maxRows = minOf(cfg.rows.coerceIn(1, 6), rowCapacity)
        // نمودار مینیاتوری: همه یا هیچ — یا برای همه‌ی نمادها و همه‌ی اندازه‌های ویجت
        // (کوچک/متوسط/بزرگ) هست یا برای هیچ‌کدام؛ عرضش با پهنای ویجت تنظیم می‌شود
        val sparkVisible = cfg.showSparkline
        val sparkWdp = (sizeW * 0.16f).coerceIn(24f, 52f)
        val sparkHdp = (sparkWdp * 0.46f).coerceIn(12f, 24f)
        val subVisible = sizeW >= 120

        // چشمک LED: با هر به‌روزرسانی، فاز روشن/کم‌نور عوض می‌شود (مثل چشمک زدن)
        val blinkOn = (updatedAt / 1000L) % 2 == 0L
        // اگر مدت زیادی از آخرین به‌روزرسانی سالم گذشته، همه‌ی LEDها قرمز می‌شوند
        val dataStale = updatedAt > 0 &&
                System.currentTimeMillis() - updatedAt > (cfg.intervalSec * 2 + 45) * 1000L

        // ── پوسته بر اساس تم ──
        views.setInt(R.id.widget_root, "setBackgroundResource", pal.bgRes)
        views.setTextColor(R.id.txt_title, pal.text)
        views.setTextColor(R.id.txt_time, pal.sub)
        views.setTextColor(R.id.txt_status, pal.sub)
        views.setInt(R.id.divider, "setBackgroundColor", pal.divider)
        views.setImageViewResource(R.id.dot_live, if (live) R.drawable.dot_live else R.drawable.dot_idle)

        // ── فونت داینامیک همه‌ی متن‌ها ──
        sp(views, R.id.txt_title, SZ_TITLE, scale)
        sp(views, R.id.txt_time, SZ_TIME, scale)
        sp(views, R.id.txt_status, SZ_STATUS, scale)

        // ── سرصفحه: عنوان دلخواه/منبع + زمان آخرین به‌روزرسانی + وضعیت بازارهای همین ویجت ──
        val title = cfg.title.ifBlank { sourceTitle.ifBlank { defaultTitle(cfg.sourceId) } }
        views.setTextViewText(R.id.txt_title, text(title, cfg))
        val updText = when {
            updatedAt <= 0 -> "به‌روزرسانی نشده"
            sizeW < 200 -> "به‌روز ${Format.time(updatedAt)}"
            else -> "آخرین به‌روزرسانی ${Format.time(updatedAt)}"
        }
        // وضعیت هر بازارِ فعالِ همین ویجت (بورس/کریپتو/آمریکا/طلا و ارز) — اگر کاربر خواسته
        // و ویجت به‌اندازه‌ی کافی پهن است؛ در ویجت باریک‌تر شکل کوتاه (فقط نام + چراغ) نشان داده می‌شود
        val marketText =
            if (cfg.showMarketStatus && sizeW >= 170)
                MarketStatus.headerFor(cfg.activeSourceIds, short = sizeW < 280)
            else ""
        // ساعت و وضعیت بازار مستقل از هم خاموش/روشن می‌شوند
        val headerText = listOf(updText, marketText)
            .filter { it.isNotEmpty() }
            .joinToString(" • ")
        views.setTextViewText(R.id.txt_time, text(headerText, cfg))
        views.setViewVisibility(
            R.id.txt_time,
            if (cfg.showTime || marketText.isNotEmpty()) View.VISIBLE else View.GONE
        )
        // متن ساعت/وضعیت با چند بازار طولانی می‌شود — سقف عرض تا از سرصفحه بیرون نزند
        runCatching {
            val titleDp = (title.length * 5.8f * scale).coerceAtMost(150f)
            val timeMaxDp = (sizeW - titleDp - 78f).coerceAtLeast(50f)
            views.setInt(
                R.id.txt_time, "setMaxWidth",
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, timeMaxDp, context.resources.displayMetrics
                ).toInt()
            )
        }

        // ── ردیف‌ها به شکل کارت‌های جدا با پس‌زمینه‌ی متناوب ──
        views.removeAllViews(R.id.rows)
        val shown = quotes.take(maxRows)
        if (shown.isEmpty()) {
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            applyRowChrome(row, pal, cfg, 0, scale)
            row.setImageViewResource(R.id.row_led, R.drawable.led_gray)
            row.setTextViewText(R.id.row_label, text("منتظر داده…", cfg))
            row.setTextViewText(R.id.row_sub, text("دکمه‌ی رفرش را بزن", cfg))
            row.setTextViewText(R.id.row_price, "—")
            row.setViewVisibility(R.id.row_change, View.GONE)
            row.setViewVisibility(R.id.row_unit, View.GONE)
            row.setViewVisibility(R.id.row_spark, View.GONE)
            views.addView(R.id.rows, row)
        } else {
            shown.forEachIndexed { i, q ->
                views.addView(
                    R.id.rows,
                    buildRow(
                        context, q, cfg, pal, i, sparkVisible, sparkWdp, sparkHdp, subVisible,
                        scale, blinkOn, dataStale,
                        unitInline = sizeW >= 200
                    )
                )
            }
        }

        // ── نوار وضعیت (قابل خاموش شدن) — «الان در چه وضعیتی هستیم؟» ──
        val errors = quotes.count { it.error != null && it.price == null }
        // نمادهایی که این نوبت تازه نشدند ولی آخرین قیمت سالم‌شان روی ویجت مانده (چراغ قرمز)
        val staleCount = quotes.count { it.stale && it.price != null }
        val activeAlerts = cfg.alerts.count { it.enabled }
        val alertInfo = if (activeAlerts > 0) " • 🔔 $activeAlerts هشدار" else ""
        // بازه‌ی ساعتی تازه‌سازی — کاربر بفهمد چرا گاهی فقط آخرین داده می‌ماند
        val windowInfo = if (cfg.refreshWindowEnabled && cfg.refreshFromMinute != cfg.refreshToMinute)
            " • ⏰ ${time2d(cfg.refreshFromMinute)}–${time2d(cfg.refreshToMinute)}" else ""
        val status = when {
            quotes.isEmpty() -> "داده‌ای نیست — روی رفرش بزن"
            errors > 0 -> "$errors نماد بدون داده$alertInfo$windowInfo"
            // بدون اینترنت یا توقف تازه‌سازی: داده پاک نمی‌شود، فقط چراغ‌ها قرمز می‌شوند
            staleCount > 0 -> "آفلاین — آخرین قیمت‌ها نگه داشته شد$alertInfo$windowInfo"
            dataStale -> (if (live) "داده‌ها قدیمی" else "به‌روزرسانی خاموش") +
                    " — آخرین داده ${Format.time(updatedAt)}$alertInfo$windowInfo"
            live -> "زنده • هر ${cfg.intervalSec} ثانیه$alertInfo$windowInfo"
            else -> "دستی — آخرین داده‌ها روی ویجت مانده$alertInfo$windowInfo"
        }
        views.setTextViewText(
            R.id.txt_status,
            if (cfg.persianDigits) Format.toPersianDigits(status) else status
        )
        views.setViewVisibility(R.id.txt_status, if (cfg.showStatus) View.VISIBLE else View.GONE)

        // ── کلیک‌ها ──
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshIntent(context, widgetId))
        views.setOnClickPendingIntent(R.id.txt_title, toggleLiveIntent(context, widgetId))
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, widgetId))

        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views)
    }

    /** ظاهر مشترک هر ردیف: رنگ‌ها، فونت داینامیک و پس‌زمینه‌ی متناوب */
    private fun applyRowChrome(
        row: RemoteViews,
        pal: Palette,
        cfg: WidgetConfig,
        index: Int,
        scale: Float
    ) {
        row.setTextColor(R.id.row_label, pal.text)
        row.setTextColor(R.id.row_sub, pal.sub)
        row.setTextColor(R.id.row_price, pal.text)
        sp(row, R.id.row_label, SZ_LABEL, scale)
        sp(row, R.id.row_sub, SZ_SUB, scale)
        sp(row, R.id.row_price, SZ_PRICE, scale)
        sp(row, R.id.row_change, SZ_CHANGE, scale)
        // رنگ زمینه‌ی متناوب ردیف‌ها — نمادها با رنگ زمینه از هم جدا می‌شوند
        if (cfg.rowSeparation) {
            row.setInt(
                R.id.row_root, "setBackgroundResource",
                if (index % 2 == 0) pal.rowA else pal.rowB
            )
        }
    }

    private fun buildRow(
        context: Context,
        q: Quote,
        cfg: WidgetConfig,
        pal: Palette,
        index: Int,
        sparkVisible: Boolean,
        sparkWdp: Float,
        sparkHdp: Float,
        subVisible: Boolean,
        scale: Float,
        blinkOn: Boolean,
        dataStale: Boolean,
        unitInline: Boolean
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_row)
        applyRowChrome(row, pal, cfg, index, scale)

        // ── LED وضعیت هر نماد: سبز=به‌روز شد (چشمک) | قرمز=نشد ولی آخرین مقدار مانده | خاکستری=بدون داده ──
        row.setImageViewResource(R.id.row_led, ledRes(q, blinkOn, dataStale))

        row.setTextViewText(
            R.id.row_label,
            if (cfg.persianDigits) Format.toPersianDigits(q.label) else q.label
        )
        val sub = subLabel(q, cfg)
        row.setTextViewText(R.id.row_sub, text(sub, cfg))
        row.setViewVisibility(
            R.id.row_sub,
            if (subVisible && sub.isNotEmpty()) View.VISIBLE else View.GONE
        )

        // واحد هر نماد: در ویجت پهن جلوی عدد قیمت، در ویجت باریک زیر عدد — نه زیر نام نماد
        // ── قیمت و واحد — از ماژول مرجع QuoteText؛ واحدِ هر منبعی همین‌جا می‌نشیند ──
        val unit = QuoteText.unit(q)
        val priceText = Format.price(q.price, cfg.persianDigits, cfg.compactNumbers)
        val inlineUnit = unitInline && unit.isNotEmpty() && q.price != null
        row.setTextViewText(
            R.id.row_price,
            if (inlineUnit)
                text(QuoteText.priceWithUnit(q, cfg.persianDigits, cfg.compactNumbers), cfg)
            else priceText
        )
        row.setTextColor(R.id.row_price, if (q.price != null) pal.text else pal.sub)
        if (!inlineUnit && unit.isNotEmpty() && q.price != null) {
            row.setTextViewText(R.id.row_unit, text(unit, cfg))
            row.setTextColor(R.id.row_unit, pal.sub)
            sp(row, R.id.row_unit, SZ_SUB, scale)
            row.setViewVisibility(R.id.row_unit, View.VISIBLE)
        } else {
            row.setViewVisibility(R.id.row_unit, View.GONE)
        }

        val ch = q.changePct
        if (!cfg.showChange || ch == null || q.price == null) {
            row.setViewVisibility(R.id.row_change, View.GONE)
        } else {
            row.setViewVisibility(R.id.row_change, View.VISIBLE)
            val up = ch > 0.0001
            val down = ch < -0.0001
            row.setTextViewText(
                R.id.row_change,
                text(Format.pct(ch, cfg.persianDigits), cfg)
            )
            row.setTextColor(
                R.id.row_change,
                if (up) COLOR_UP else if (down) COLOR_DOWN else COLOR_FLAT
            )
            row.setInt(
                R.id.row_change, "setBackgroundResource",
                if (up) R.drawable.badge_up else if (down) R.drawable.badge_down else R.drawable.badge_flat
            )
        }

        // ── نمودار مینیاتوری: چند نقطه از انتهای سری (تعدادش را کاربر تعیین می‌کند) ──
        val points = q.spark.takeLast(cfg.sparkPoints.coerceIn(6, 60))
        if (sparkVisible && points.size >= 3) {
            val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, sparkWdp * scale, context.resources.displayMetrics
            ).toInt()
            val py = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, sparkHdp * scale, context.resources.displayMetrics
            ).toInt()
            val color = when {
                (ch ?: 0.0) > 0.0001 -> COLOR_UP
                (ch ?: 0.0) < -0.0001 -> COLOR_DOWN
                else -> COLOR_FLAT
            }
            val bmp = Sparkline.bitmap(points, px, py, color)
            if (bmp != null) {
                row.setViewVisibility(R.id.row_spark, View.VISIBLE)
                row.setImageViewBitmap(R.id.row_spark, bmp)
            } else {
                row.setViewVisibility(R.id.row_spark, View.GONE)
            }
        } else {
            row.setViewVisibility(R.id.row_spark, View.GONE)
        }

        return row
    }

    /** رنگ LED بر اساس وضعیت به‌روزرسانی هر نماد — چشمک با فاز روشن/کم‌نور */
    private fun ledRes(q: Quote, blinkOn: Boolean, dataStale: Boolean): Int {
        // بدون داده اصلاً — خاکستری
        if (q.price == null && q.error == null) return R.drawable.led_gray
        // به‌روز شده و سالم — سبز چشمک‌زن
        val fresh = q.price != null && q.error == null && !q.stale && !dataStale
        return when {
            fresh -> if (blinkOn) R.drawable.led_green else R.drawable.led_green_dim
            else -> if (blinkOn) R.drawable.led_red else R.drawable.led_red_dim
        }
    }

    /** خط دوم هر نماد: کد نماد + حجم معاملات — واحد دیگر اینجا نمی‌شیند، کنار عدد قیمت است */
    private fun subLabel(q: Quote, cfg: WidgetConfig): String {
        // خطا فقط وقتی نشان داده می‌شود که مقداری برای نمایش نداشته باشیم؛
        // در حالت stale (آخرین مقدار سالم) عدد می‌ماند و فقط LED قرمز می‌شود
        if (q.error != null && q.price == null) return "⚠ ${q.error}"
        val parts = mutableListOf<String>()
        if (cfg.showCode && q.code.isNotBlank()) parts += q.code
        if (cfg.showVolume && q.volume != null) {
            parts += "حجم ${Format.volume(q.volume, cfg.persianDigits)}"
        }
        return parts.joinToString(" • ")
    }

    /** HH:MM از دقیقه‌ی روز — برای نمایش بازه‌ی تازه‌سازی روی ویجت */
    private fun time2d(minute: Int) = "${minute / 60}:${(minute % 60).toString().padStart(2, '0')}"

    /** ضریب اندازه بر اساس فضای واقعی ویجت — هرچه بزرگ‌تر، فونت درشت‌تر */
    private fun sizeFactor(sizeW: Int, sizeH: Int): Float = when {
        sizeH >= 230 || sizeW >= 340 -> 1.18f
        sizeH >= 170 -> 1.08f
        sizeH <= 62 -> 0.82f
        sizeH <= 95 -> 0.92f
        else -> 1.0f
    }

    private fun sp(views: RemoteViews, id: Int, base: Float, scale: Float) {
        views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, base * scale)
    }

    private fun text(s: String, cfg: WidgetConfig) = if (cfg.persianDigits) Format.toPersianDigits(s) else s

    private fun defaultTitle(sourceId: String): String =
        com.pulse.market.data.SourceCatalog.byId(sourceId)?.title?.substringBefore(" —") ?: "نبض بازار"

    // ── PendingIntent ها ──

    private fun refreshIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, StockWidgetProvider::class.java).apply {
            action = StockWidgetProvider.ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getBroadcast(
            context, 1000 + widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun toggleLiveIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, StockWidgetProvider::class.java).apply {
            action = StockWidgetProvider.ACTION_TOGGLE_LIVE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getBroadcast(
            context, 2000 + widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openAppIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getActivity(
            context, 3000 + widgetId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun allWidgetIds(context: Context): IntArray {
        val mgr = AppWidgetManager.getInstance(context)
        // هر سه اندازه‌ی ویجت (کوچک/متوسط/بزرگ)
        return PROVIDER_CLASSES.flatMap { mgr.getAppWidgetIds(ComponentName(context, it)).toList() }
            .toIntArray()
    }

    private val PROVIDER_CLASSES = listOf(
        StockWidgetProvider::class.java,
        SmallWidgetProvider::class.java,
        LargeWidgetProvider::class.java
    )
}
