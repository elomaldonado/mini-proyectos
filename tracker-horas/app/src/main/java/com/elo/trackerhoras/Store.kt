package com.elo.trackerhoras

import android.content.Context
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Las dos cosas que se cuentan. */
enum class Cat(val key: String, val label: String) {
    WORK("work", "Trabajo"),
    ZH("zh", "中文"),
}

/** Lo que se puede marcar de ejercicio cada día (se guarda como bits). */
object Ex {
    const val STEPS = 1 // 8k pasos
    const val CALI = 2 // 20 min calistenia
    const val RUN = 4 // correr / saltar soga

    /** 1: 8k · 2: 8k + calistenia · 3: 8k + correr/soga · 4: 8k + correr/soga + calistenia */
    fun level(bits: Int): Int = when {
        bits and RUN != 0 && bits and CALI != 0 -> 4
        bits and RUN != 0 -> 3
        bits and CALI != 0 -> 2
        bits and STEPS != 0 -> 1
        else -> 0
    }
}

/** Un día: segundos de trabajo y de mandarín, dinero ganado (soles), ejercicio y una nota libre. */
class Day(
    var work: Long = 0,
    var zh: Long = 0,
    var note: String = "",
    var money: Double = 0.0,
    var ex: Int = 0,
) {
    fun get(cat: Cat): Long = if (cat == Cat.WORK) work else zh

    fun add(cat: Cat, secs: Long) {
        if (cat == Cat.WORK) work = maxOf(0L, work + secs) else zh = maxOf(0L, zh + secs)
    }

    fun total(): Long = work + zh
    fun isEmpty(): Boolean = work == 0L && zh == 0L && note.isBlank() && money == 0.0 && ex == 0
}

/** Guarda todo en el teléfono (SharedPreferences, como JSON). Sin internet, sin cuentas. */
object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("tracker", Context.MODE_PRIVATE)

    fun zone(): ZoneId = ZoneId.systemDefault()
    fun today(): LocalDate = LocalDate.now(zone())

    fun load(c: Context): MutableMap<LocalDate, Day> {
        val out = HashMap<LocalDate, Day>()
        val raw = prefs(c).getString("days", null) ?: return out
        val o = JSONObject(raw)
        for (k in o.keys()) {
            val d = o.getJSONObject(k)
            out[LocalDate.parse(k)] = Day(d.optLong("w"), d.optLong("z"), d.optString("n"), d.optDouble("m", 0.0), d.optInt("e", 0))
        }
        return out
    }

    fun save(c: Context, days: Map<LocalDate, Day>) {
        val o = JSONObject()
        for ((k, d) in days) {
            if (!d.isEmpty()) o.put(k.toString(), JSONObject().put("w", d.work).put("z", d.zh).put("n", d.note).put("m", d.money).put("e", d.ex))
        }
        prefs(c).edit().putString("days", o.toString()).commit()
    }

    fun running(c: Context): Cat? {
        val k = prefs(c).getString("run", null) ?: return null
        return Cat.values().firstOrNull { it.key == k }
    }

    fun runStart(c: Context): Long = prefs(c).getLong("runStart", 0L)

    fun start(c: Context, cat: Cat) {
        stop(c)
        prefs(c).edit().putString("run", cat.key).putLong("runStart", System.currentTimeMillis()).commit()
    }

    /** Detiene el cronómetro y reparte el tiempo por día (si pasó la medianoche, se divide). */
    fun stop(c: Context) {
        val cat = running(c) ?: return
        val end = System.currentTimeMillis()
        val days = load(c)
        var t = runStart(c)
        while (t < end) {
            val date = Instant.ofEpochMilli(t).atZone(zone()).toLocalDate()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli()
            val segEnd = minOf(end, nextMidnight)
            days.getOrPut(date) { Day() }.add(cat, (segEnd - t) / 1000)
            t = segEnd
        }
        save(c, days)
        prefs(c).edit().remove("run").remove("runStart").commit()
    }

    fun toggle(c: Context, cat: Cat) {
        if (running(c) == cat) stop(c) else start(c, cat)
    }

    fun addTo(c: Context, date: LocalDate, cat: Cat, secs: Long) {
        val days = load(c)
        days.getOrPut(date) { Day() }.add(cat, secs)
        save(c, days)
    }

    fun addMoney(c: Context, date: LocalDate, soles: Double) {
        val days = load(c)
        val d = days.getOrPut(date) { Day() }
        d.money = maxOf(0.0, d.money + soles)
        save(c, days)
    }

    fun toggleEx(c: Context, date: LocalDate, bit: Int) {
        val days = load(c)
        val d = days.getOrPut(date) { Day() }
        d.ex = d.ex xor bit
        save(c, days)
    }

    fun moneyToday(c: Context): Double = load(c)[today()]?.money ?: 0.0

    fun moneyThisMonth(days: Map<LocalDate, Day>): Double {
        val t = today()
        return days.filterKeys { it.year == t.year && it.month == t.month }.values.sumOf { it.money }
    }

    /** Segundos de hoy para una categoría, incluyendo el cronómetro si está corriendo. */
    fun todaySecs(c: Context, cat: Cat): Long {
        val saved = load(c)[today()]?.get(cat) ?: 0L
        if (running(c) != cat) return saved
        val midnight = today().atStartOfDay(zone()).toInstant().toEpochMilli()
        val from = maxOf(runStart(c), midnight)
        return saved + (System.currentTimeMillis() - from) / 1000
    }
}

fun fmtH(secs: Long, withSeconds: Boolean = false): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return when {
        withSeconds && h > 0 -> String.format("%dh %02dm %02ds", h, m, s)
        withSeconds -> String.format("%dm %02ds", m, s)
        h > 0 -> String.format("%dh %02dm", h, m)
        else -> "${m}m"
    }
}

fun fmtSoles(v: Double): String =
    if (v == Math.floor(v)) String.format(java.util.Locale.US, "S/ %,.0f", v)
    else String.format(java.util.Locale.US, "S/ %,.2f", v)

/** Lee "85", "85.5", "85,50" o "S/ 85" como número. */
fun parseSoles(s: String): Double? =
    s.replace("S/", "", ignoreCase = true).replace(" ", "").replace(',', '.').toDoubleOrNull()
