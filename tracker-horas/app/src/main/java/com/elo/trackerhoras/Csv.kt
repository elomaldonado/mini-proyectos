package com.elo.trackerhoras

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Exportar / importar el historial.
 * Formato: fecha,trabajo_horas,mandarin_horas,dinero_soles,ejercicio,nota
 *   ejercicio = cosas hechas ese día, separadas por "+": pasos+calistenia+correr
 * Las columnas se leen por el nombre de la cabecera, así que sirven también los archivos
 * más viejos (sin dinero o sin ejercicio). Acepta ';' como separador y coma decimal.
 */
object Csv {
    private val DEFAULT_COLUMNS = listOf("fecha", "trabajo_horas", "mandarin_horas", "dinero_soles", "nota")

    private val dateFormats = listOf("yyyy-MM-dd", "d/M/yyyy", "d-M-yyyy", "d/M/yy", "yyyy/M/d")
        .map { DateTimeFormatter.ofPattern(it) }

    fun export(days: Map<LocalDate, Day>): String {
        val sb = StringBuilder("fecha,trabajo_horas,mandarin_horas,dinero_soles,ejercicio,nota\n")
        for (date in days.keys.sorted()) {
            val d = days.getValue(date)
            sb.append(date).append(',')
                .append(hours(d.work)).append(',')
                .append(hours(d.zh)).append(',')
                .append(String.format(Locale.US, "%.2f", d.money)).append(',')
                .append(exToText(d.ex)).append(',')
                .append('"').append(d.note.replace("\"", "\"\"")).append("\"\n")
        }
        return sb.toString()
    }

    /** Devuelve cuántos días se importaron. Los días importados reemplazan a los existentes. */
    fun import(c: Context, text: String): Int {
        val days = Store.load(c)
        var columns = DEFAULT_COLUMNS
        var count = 0
        for (raw in text.lines()) {
            val line = raw.removePrefix("﻿").trim()
            if (line.isEmpty()) continue
            val sep = if (';' in line) ';' else ','

            if (line.lowercase().startsWith("fecha")) {
                columns = line.split(sep).map { clean(it).lowercase() }
                continue
            }
            // La nota (si existe) va al final y puede tener comas: se corta solo hasta ahí.
            val parts = line.split(sep, limit = columns.size)
            fun col(name: String): String? {
                val i = columns.indexOf(name)
                return if (i >= 0) parts.getOrNull(i) else null
            }

            val date = parseDate(clean(parts[0])) ?: continue
            val day = Day(
                work = (num(col("trabajo_horas")) * 3600).toLong(),
                zh = (num(col("mandarin_horas")) * 3600).toLong(),
                note = col("nota")?.let { clean(it).replace("\"\"", "\"") } ?: "",
                money = num(col("dinero_soles")),
                ex = col("ejercicio")?.let { textToEx(clean(it)) } ?: 0,
            )
            days[date] = day
            count++
        }
        Store.save(c, days)
        return count
    }

    private fun exToText(bits: Int): String {
        val names = mutableListOf<String>()
        if (bits and Ex.STEPS != 0) names.add("pasos")
        if (bits and Ex.CALI != 0) names.add("calistenia")
        if (bits and Ex.RUN != 0) names.add("correr")
        return names.joinToString("+")
    }

    private fun textToEx(s: String): Int {
        val t = s.lowercase()
        var bits = 0
        if ("paso" in t || "8k" in t) bits = bits or Ex.STEPS
        if ("cali" in t) bits = bits or Ex.CALI
        if ("corr" in t || "soga" in t) bits = bits or Ex.RUN
        return bits
    }

    private fun hours(secs: Long) = String.format(Locale.US, "%.2f", secs / 3600.0)

    private fun clean(s: String) = s.trim().removeSurrounding("\"").trim()

    private fun num(s: String?): Double =
        s?.let { clean(it).replace(',', '.').toDoubleOrNull() }?.coerceAtLeast(0.0) ?: 0.0

    private fun parseDate(s: String): LocalDate? {
        for (f in dateFormats) {
            try {
                return LocalDate.parse(s, f)
            } catch (_: Exception) {
            }
        }
        return null
    }
}
