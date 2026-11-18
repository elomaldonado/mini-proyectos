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

    fun firstMonday(weeks: Int = WEEKS): LocalDate =
        Store.today().with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())

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
        weeks: Int = WEEKS,
    ): Float {
        val start = firstMonday(weeks)
        val today = Store.today()
        val gap = cell * 0.18f
        val radius = cell * 0.2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, gap * 0.8f)
            color = outline
        }
        if (monthLabelH > 0f) drawMonthLabels(canvas, start, x0, y0, cell, monthLabelH, textPaint, weeks)

        for (col in 0 until weeks) {
            val monday = start.plusWeeks(col.toLong())
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

    /**
     * Nombres de los meses sobre la columna donde empieza cada uno. Si dos quedan
     * demasiado juntos (p. ej. "may" en la primera columna y "jun" en la segunda),
     * se queda solo el mes que empieza de verdad ahí, para que no se lea "majun".
     */
    private fun drawMonthLabels(
        canvas: Canvas,
        start: LocalDate,
        x0: Float,
        y0: Float,
        cell: Float,
        monthLabelH: Float,
        textPaint: Paint,
        weeks: Int = WEEKS,
    ) {
        val es = Locale("es")
        val labels = mutableListOf<Pair<Float, String>>() // x, texto
        var lastMonth = -1
        for (col in 0 until weeks) {
            val monday = start.plusWeeks(col.toLong())
            if (monday.monthValue != lastMonth) {
                lastMonth = monday.monthValue
                val name = monday.month.getDisplayName(TextStyle.SHORT, es).trimEnd('.')
                val x = x0 + col * cell
                // No dibujar si no cabe antes del borde derecho.
                if (x + textPaint.measureText(name) > x0 + weeks * cell) continue
                val prev = labels.lastOrNull()
                if (prev != null && prev.first + textPaint.measureText(prev.second) + cell * 0.5f > x) {
                    labels.removeAt(labels.size - 1) // el anterior era un resto del mes pasado: se quita
                }
                labels.add(x to name)
            }
        }
        for ((x, name) in labels) canvas.drawText(name, x, y0 + monthLabelH * 0.75f, textPaint)
    }

    /**
     * Imagen con los tres mapas para la tarjeta de la pantalla de bloqueo.
     * Android limita la altura de la tarjeta abierta, así que se aprovecha el ancho:
     * nombre a la izquierda de cada mapa, una sola fila de meses arriba y 28 semanas.
     * La imagen tiene la proporción del espacio disponible para que se vea grande y centrada.
     */
    fun bitmapForNotification(c: Context, days: Map<LocalDate, Day>): Bitmap {
        val night = isNight(c)
        val d = c.resources.displayMetrics.density
        val weeks = 28
        val width = 294 * d
        val labelW = 54 * d
        val cell = (width - labelW) / weeks
        val monthH = 12 * d
        val spacing = 7 * d
        val tracks = Track.values()
        val height = monthH + tracks.size * 7 * cell + (tracks.size - 1) * spacing

        val bmp = Bitmap.createBitmap(width.toInt(), height.toInt() + 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val muted = if (night) 0xFFB0B8C0.toInt() else 0xFF5F6670.toInt()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12 * d
            color = if (night) 0xFFE6E6E6.toInt() else 0xFF1F2328.toInt()
            typeface = Fonts.medium(c)
        }
        val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f * d
            color = muted
            typeface = Fonts.regular(c)
        }
        val outline = if (night) 0xFFFFFFFF.toInt() else 0xFF1F2328.toInt()

        // Fila de meses, una sola vez, alineada con las grillas.
        drawMonthLabels(canvas, firstMonday(weeks), labelW, 0f, cell, monthH, monthPaint, weeks)

        var y = monthH
        for (t in tracks) {
            val gridH = 7 * cell
            // Nombre centrado verticalmente a la izquierda del mapa.
            canvas.drawText(t.label, 0f, y + gridH / 2 + titlePaint.textSize * 0.35f, titlePaint)
            draw(canvas, days, t, labelW, y, cell, 0f, monthPaint, outline, night, showNoteDots = false, weeks = weeks)
            y += gridH + spacing
        }
        return bmp
    }
}
