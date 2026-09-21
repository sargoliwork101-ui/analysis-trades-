package com.pulse.market.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/** نمودار مینیاتوری قیمت (به‌صورت Bitmap برای RemoteViews) */
object Sparkline {

    fun bitmap(values: List<Double>, widthPx: Int, heightPx: Int, color: Int): Bitmap? {
        if (values.size < 3 || widthPx <= 2 || heightPx <= 2) return null

        val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val min = values.minOrNull() ?: return null
        val max = values.maxOrNull() ?: return null
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0

        val pad = heightPx * 0.14f
        val usable = heightPx - pad * 2
        val dx = widthPx.toFloat() / (values.size - 1).toFloat()

        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * dx
            val y = pad + (usable - ((v - min) / span * usable)).toFloat()
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        // پُر کردن ملایم زیر نمودار
        val fill = Path(line).apply {
            lineTo(widthPx.toFloat(), heightPx.toFloat())
            lineTo(0f, heightPx.toFloat())
            close()
        }

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, heightPx.toFloat(),
                Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(fill, fillPaint)

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = (heightPx * 0.09f).coerceAtLeast(2f)
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(line, stroke)

        // نقطه‌ی آخر
        val lastX = (values.size - 1) * dx
        val lastY = pad + (usable - ((values.last() - min) / span * usable)).toFloat()
        canvas.drawCircle(
            lastX.coerceAtMost(widthPx - stroke.strokeWidth), lastY,
            stroke.strokeWidth * 1.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        )

        return bmp
    }
}
