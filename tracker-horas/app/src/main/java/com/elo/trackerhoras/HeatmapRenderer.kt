package com.elo.trackerhoras

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Los tres mapas de calor. */
enum class Track(val label: String) {
    WORK("Trabajo"),
    ZH("中文"),
    EX("Ejercicio"),
}

/**
 * Dibuja el mapa de calor (26 semanas, lunes arriba). Lo usan la pantalla de la app
 * y la tarjeta de la pantalla de bloqueo, así se ven idénticos.
 *
 *  Trabajo   (azules):     1h · 2–3h · 3–5h · 6h+
 *  中文       (verdes):     1h · 2h · 3h · 3h+
 *  Ejercicio (rojos suaves): 8k · 8k+calistenia · 8k+correr/soga · todo
 */
object HeatmapRenderer {
    const val WEEKS = 26

    private val BLUES = intArrayOf(0xFFC9D9F5.toInt(), 0xFF6E9EEA.toInt(), 0xFF3A78D8.toInt(), 0xFF1155CC.toInt())
    private val GREENS = intArrayOf(0xFFC6EBC5.toInt(), 0xFF7BCB7A.toInt(), 0xFF34A853.toInt(), 0xFF1B6E33.toInt())
    private val REDS = intArrayOf(0xFFFAD4D4.toInt(), 0xFFF4A6A6.toInt(), 0xFFE87C7C.toInt(), 0xFFD45555.toInt())

    fun legend(t: Track): List<String> = when (t) {
        Track.WORK -> listOf("0", "1h", "2–3h", "3–5h", "6h+")
        Track.ZH -> listOf("0", "1h", "2h", "3h", "3h+")
        Track.EX -> listOf("Nada", "8k pasos", "8k + calistenia", "8k + correr/soga", "8k + correr/soga + calistenia")
    }

    fun level(t: Track, day: Day?): Int {
        if (day == null) return 0
        if (t == Track.EX) return Ex.level(day.ex)
        val secs = if (t == Track.WORK) day.work else day.zh
        if (secs <= 0L) return 0
        val h = secs / 3600.0
        return if (t == Track.WORK) {
            when {
                h < 1.5 -> 1
                h < 3.5 -> 2
                h < 5.5 -> 3
                else -> 4
            }
        } else {
            when {
                h < 1.5 -> 1
                h < 2.5 -> 2
                h <= 3.0 -> 3
                else -> 4
            }
        }
    }

    fun color(t: Track, level: Int, night: Boolean): Int {
        if (level <= 0) return if (night) 0xFF3A414B.toInt() else 0xFFE4E8EE.toInt()
        val ramp = when (t) {
            Track.WORK -> BLUES
            Track.ZH -> GREENS
            Track.EX -> REDS
        }
        return ramp[minOf(level, 4) - 1]
    }

    fun firstMonday(): LocalDate =
        Store.today().with(DayOfWeek.MONDAY).minusWeeks((WEEKS - 1).toLong())

    fun isNight(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Dibuja la grilla con su fila de meses arriba. Devuelve la altura usada. */
    fun draw(
        canvas: Canvas,
        days: Map<LocalDate, Day>,
        t: Track,
        x0: Float,
        y0: Float,
        cell: Float,
        monthLabelH: Float,
        textPaint: Paint,
        outline: Int,
        night: Boolean,
        showNoteDots: Boolean = true,
    ): Float {
        val start = firstMonday()
        val today = Store.today()
        val gap = cell * 0.18f
        val radius = cell * 0.2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, gap * 0.8f)
            color = outline
        }
        val es = Locale("es")
        var lastMonth = -1

        for (col in 0 until WEEKS) {
            val monday = start.plusWeeks(col.toLong())
            if (monthLabelH > 0f && monday.monthValue != lastMonth) {
                if (col < WEEKS - 1) {
                    val name = monday.month.getDisplayName(TextStyle.SHORT, es).trimEnd('.')
                    canvas.drawText(name, x0 + col * cell, y0 + monthLabelH * 0.75f, textPaint)
                }
                lastMonth = monday.monthValue
            }
            for (row in 0 until 7) {
                val date = monday.plusDays(row.toLong())
                if (date.isAfter(today)) continue
                val left = x0 + col * cell
                val top = y0 + monthLabelH + row * cell
                val right = left + cell - gap
                val bottom = top + cell - gap
                val day = days[date]
                val lvl = level(t, day)
                fill.color = color(t, lvl, night)
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, fill)

                // Puntito si el día tiene nota (Pingtung, examen, drained…)
                if (showNoteDots && day != null && day.note.isNotBlank()) {
                    fill.color = if (lvl >= 2) 0xFFFFFFFF.toInt() else 0xFF555555.toInt()
                    canvas.drawCircle(right - cell * 0.22f, top + cell * 0.22f, cell * 0.09f, fill)
                }
                if (date == today) canvas.drawRoundRect(left, top, right, bottom, radius, radius, stroke)
            }
        }
        return monthLabelH + cell * 7
    }

    /** Imagen con los tres mapas, para la tarjeta de la pantalla de bloqueo. */
    fun bitmapForNotification(c: Context, days: Map<LocalDate, Day>): Bitmap {
        val night = isNight(c)
        val density = c.resources.displayMetrics.density
        val width = (300 * density).toInt()
        val cell = width / WEEKS.toFloat()
        val labelH = 15 * density
        val monthH = 12 * density
        val spacing = 6 * density
        val blockH = labelH + monthH + cell * 7
        val tracks = Track.values()
        val height = (blockH * tracks.size + spacing * (tracks.size - 1)).toInt() + 2

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val muted = if (night) 0xFFB0B8C0.toInt() else 0xFF5F6670.toInt()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12 * density
            color = muted
            typeface = Fonts.medium(c)
        }
        val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9 * density
            color = muted
            typeface = Fonts.regular(c)
        }
        val outline = if (night) 0xFFFFFFFF.toInt() else 0xFF1F2328.toInt()

        var y = 0f
        for (t in tracks) {
            canvas.drawText(t.label, 0f, y + labelH * 0.8f, titlePaint)
            y += labelH
            y += draw(canvas, days, t, 0f, y, cell, monthH, monthPaint, outline, night, showNoteDots = false)
            y += spacing
        }
        return bmp
    }
}
