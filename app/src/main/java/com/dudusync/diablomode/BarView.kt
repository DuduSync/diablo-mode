package com.dudusync.diablomode

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Barra de progresso arredondada, desenhada na mão (sem XML). */
class BarView(context: Context) : View(context) {

    var fraction: Float = 0f
        set(value) {
            field = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
            invalidate()
        }

    var barColor: Int = 0xFFFF3B30.toInt()
        set(value) { field = value; invalidate() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2A2A30.toInt() }
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barColor }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val r = h / 2f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, bgPaint)
        val f = fraction
        if (f > 0f) {
            fgPaint.color = barColor
            rect.set(0f, 0f, (w * f).coerceAtLeast(h), h)
            canvas.drawRoundRect(rect, r, r, fgPaint)
        }
    }
}
