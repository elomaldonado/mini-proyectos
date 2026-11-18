package com.elo.trackerhoras

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import java.time.LocalDate

/** Mapa de calor dentro de la app. Toca un día para editarlo. */
@SuppressLint("ViewConstructor")
class HeatmapView(
    context: Context,
    private val track: Track,
    private val night: Boolean,
    private val onTap: (LocalDate) -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val monthH = 16 * density
    private var cell = 0f
    private var days: Map<LocalDate, Day> = emptyMap()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10 * density
        color = if (night) 0xFF9AA4AF.toInt() else 0xFF656D76.toInt()
        typeface = Fonts.regular(context)
    }
    private val outline = if (night) 0xFFFFFFFF.toInt() else 0xFF1F2328.toInt()

    fun setDays(d: Map<LocalDate, Day>) {
        days = d
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        cell = width / HeatmapRenderer.WEEKS.toFloat()
        setMeasuredDimension(width, (monthH + cell * 7).toInt() + 1)
    }

    override fun onDraw(canvas: Canvas) {
        HeatmapRenderer.draw(canvas, days, track, 0f, 0f, cell, monthH, textPaint, outline, night)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                if (cell <= 0f) return true
                val col = (event.x / cell).toInt()
                val row = ((event.y - monthH) / cell).toInt()
                if (event.y >= monthH && col in 0 until HeatmapRenderer.WEEKS && row in 0 until 7) {
                    val date = HeatmapRenderer.firstMonday().plusWeeks(col.toLong()).plusDays(row.toLong())
                    if (!date.isAfter(Store.today())) onTap(date)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
