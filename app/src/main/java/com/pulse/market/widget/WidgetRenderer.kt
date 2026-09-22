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
import com.pulse.market.data.WidgetConfig
import com.pulse.market.data.WidgetTheme
import com.pulse.market.ui.MainActivity
import com.pulse.market.ui.Sparkline
import com.pulse.market.ui.Format
import kotlin.math.abs

/** ساخت ظاهر ویجت */
object WidgetRenderer {

    private const val COLOR_UP = 0xFF22C55E.toInt()
    private const val COLOR_DOWN = 0xFFF43F5E.toInt()
    private const val COLOR_FLAT = 0xFF94A3B8.toInt()

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

        // ── اندازه‌ی واقعی ویجت ← تعداد ردیف و نمایش نمودار ──
        val opts = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
        val sizeH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 }
            ?: opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
        val sizeW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).takeIf { it > 0 }
            ?: opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val rowCapacity = (((sizeH - 70f) / 42f) + 1f).toInt().coerceIn(1, 4)
        val maxRows = minOf(cfg.rows.coerceIn(1, 4), rowCapacity)
        val sparkVisible = cfg.showSparkline && sizeW >= 170

        // ── پوسته بر اساس تم ──
        val bgRes = when (cfg.theme) {
            WidgetTheme.DARK -> R.drawable.widget_bg_dark
            WidgetTheme.LIGHT -> R.drawable.widget_bg_light
            WidgetTheme.NEON -> R.drawable.widget_bg_neon
            WidgetTheme.AURORA -> R.drawable.widget_bg_aurora
            WidgetTheme.MOCHA -> R.drawable.widget_bg_mocha
        }
        val textColor = when (cfg.theme) {
            WidgetTheme.DARK -> 0xFFF1F5F9.toInt()
            WidgetTheme.LIGHT -> 0xFF0F172A.toInt()
            WidgetTheme.NEON -> 0xFFE4F8FF.toInt()
            WidgetTheme.AURORA -> 0xFFEDEAFF.toInt()
            WidgetTheme.MOCHA -> 0xFFF3E9DD.toInt()
        }
        val subColor = when (cfg.theme) {
            WidgetTheme.DARK -> 0xFF8B9AB1.toInt()
            WidgetTheme.LIGHT -> 0xFF64748B.toInt()
            WidgetTheme.NEON -> 0xFF67E8F9.toInt()
            WidgetTheme.AURORA -> 0xFFA5B4FC.toInt()
            WidgetTheme.MOCHA -> 0xFFC8A98E.toInt()
        }
        val dividerColor = when (cfg.theme) {
            WidgetTheme.DARK -> 0xFF1B2740.toInt()
            WidgetTheme.LIGHT -> 0xFFE2E8F0.toInt()
            WidgetTheme.NEON -> 0xFF155E75.toInt()
            WidgetTheme.AURORA -> 0xFF3E2E78.toInt()
            WidgetTheme.MOCHA -> 0xFF3E3138.toInt()
        }

        views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)
        views.setTextColor(R.id.txt_title, textColor)
        views.setTextColor(R.id.txt_time, subColor)
        views.setTextColor(R.id.txt_status, subColor)
        views.setInt(R.id.divider, "setBackgroundColor", dividerColor)
        views.setImageViewResource(R.id.dot_live, if (live) R.drawable.dot_live else R.drawable.dot_idle)

        val title = sourceTitle.ifBlank { defaultTitle(cfg.sourceId) }
        views.setTextViewText(R.id.txt_title, text(title, cfg))
        views.setTextViewText(R.id.txt_time, text(Format.time(updatedAt), cfg))

        // ── ردیف‌ها ──
        views.removeAllViews(R.id.rows)
        val shown = quotes.take(maxRows)
        if (shown.isEmpty()) {
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            row.setTextViewText(R.id.row_label, "منتظر داده…")
            row.setTextViewText(R.id.row_sub, "دکمه‌ی رفرش را بزن")
            row.setTextViewText(R.id.row_price, "—")
            row.setViewVisibility(R.id.row_change, View.GONE)
            row.setViewVisibility(R.id.row_spark, View.GONE)
            row.setTextColor(R.id.row_label, textColor)
            row.setTextColor(R.id.row_sub, subColor)
            row.setTextColor(R.id.row_price, textColor)
            views.addView(R.id.rows, row)
        } else {
            shown.forEach { q -> views.addView(R.id.rows, buildRow(context, q, cfg, textColor, subColor, sparkVisible)) }
        }

        // ── نوار وضعیت ──
        val errors = quotes.count { it.error != null }
        val activeAlerts = cfg.alerts.count { it.enabled }
        val alertInfo = if (activeAlerts > 0) " • 🔔 $activeAlerts هشدار" else ""
        val status = when {
            errors == 0 && quotes.isNotEmpty() -> (if (live) "زنده" else "دستی") +
                    " • هر ${cfg.intervalSec} ثانیه$alertInfo"
            quotes.isEmpty() -> "داده‌ای نیست — روی رفرش بزن"
            else -> "$errors مورد خطا$alertInfo"
        }
        views.setTextViewText(
            R.id.txt_status,
            if (cfg.persianDigits) Format.toPersianDigits(status) else status
        )

        // ── کلیک‌ها ──
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshIntent(context, widgetId))
        views.setOnClickPendingIntent(R.id.txt_title, toggleLiveIntent(context, widgetId))
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, widgetId))

        AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views)
    }

    private fun buildRow(
        context: Context,
        q: Quote,
        cfg: WidgetConfig,
        textColor: Int,
        subColor: Int,
        sparkVisible: Boolean
    ): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_row)

        row.setTextViewText(
            R.id.row_label,
            if (cfg.persianDigits) Format.toPersianDigits(q.label) else q.label
        )
        row.setTextViewText(R.id.row_sub, subColorLabel(q))

        val priceText = withUnit(q)
        row.setTextViewText(
            R.id.row_price,
            if (cfg.persianDigits) Format.toPersianDigits(priceText) else priceText
        )
        row.setTextColor(R.id.row_label, textColor)
        row.setTextColor(R.id.row_sub, subColor)
        row.setTextColor(R.id.row_price, if (q.price != null) textColor else subColor)

        val ch = q.changePct
        if (ch == null || q.price == null) {
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

        if (sparkVisible && q.spark.size >= 3) {
            val px = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 52f, context.resources.displayMetrics
            ).toInt()
            val py = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics
            ).toInt()
            val color = when {
                (ch ?: 0.0) > 0.0001 -> COLOR_UP
                (ch ?: 0.0) < -0.0001 -> COLOR_DOWN
                else -> COLOR_FLAT
            }
            val bmp = Sparkline.bitmap(q.spark, px, py, color)
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

    private fun subColorLabel(q: Quote): String = when {
        q.error != null -> "⚠ ${q.error}"
        q.unit.isNotEmpty() -> q.unit
        else -> q.code
    }

    private fun withUnit(q: Quote): String {
        val p = Format.price(q.price, false)
        return if (q.price == null) "—" else p
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
