package com.elo.trackerhoras

import android.content.Context
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Exportar / importar el historial.
 * Formato: fecha,trabajo_horas,mandarin_horas,dinero_soles,nota
 * (también acepta el formato viejo sin la columna de dinero)
 * Acepta también ';' como separador y coma decimal (como guarda Excel en español).
 */
object Csv {
    private val dateFormats = listOf("yyyy-MM-dd", "d/M/yyyy", "d-M-yyyy", "d/M/yy", "yyyy/M/d")
        .map { DateTimeFormatter.ofPattern(it) }

    fun export(days: Map<LocalDate, Day>): String {
        val sb = StringBuilder("fecha,trabajo_horas,mandarin_horas,dinero_soles,nota\n")
        for (date in days.keys.sorted()) {
            val d = days.getValue(date)
            sb.append(date).append(',')
                .append(hours(d.work)).append(',')
                .append(hours(d.zh)).append(',')
                .append(String.format(Locale.US, "%.2f", d.money)).append(',')
                .append('"').append(d.note.replace("\"", "\"\"")).append("\"\n")
        }
        return sb.toString()
    }

    /** Devuelve cuántos días se importaron. Los días importados reemplazan a los existentes. */
    fun import(c: Context, text: String): Int {
        val days = Store.load(c)
        var count = 0
        for (raw in text.lines()) {
            val line = raw.removePrefix("﻿").trim()
            if (line.isEmpty()) continue
            val sep = if (';' in line) ';' else ','
            val parts = line.split(sep, limit = 5)
            val date = parseDate(clean(parts[0])) ?: continue
            val work = num(parts.getOrNull(1))
            val zh = num(parts.getOrNull(2))
            val hasMoney = parts.size >= 5 || (parts.size == 4 && clean(parts[3]).replace(',', '.').toDoubleOrNull() != null)
            val money = if (hasMoney) num(parts.getOrNull(3)) else 0.0
            val noteRaw = if (hasMoney) parts.getOrNull(4) else parts.getOrNull(3)
            val note = noteRaw?.let { clean(it).replace("\"\"", "\"") } ?: ""
            days[date] = Day((work * 3600).toLong(), (zh * 3600).toLong(), note, money)
            count++
        }
        Store.save(c, days)
        return count
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
