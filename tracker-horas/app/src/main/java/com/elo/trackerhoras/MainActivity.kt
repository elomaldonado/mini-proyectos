package com.elo.trackerhoras

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {

    private companion object {
        const val REQ_EXPORT = 10
        const val REQ_IMPORT = 11
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private val handler = Handler(Looper.getMainLooper())
    private val totalViews = HashMap<Cat, TextView>()
    private val startButtons = HashMap<Cat, Button>()
    private val heatmaps = HashMap<Cat, HeatmapView>()
    private lateinit var dateView: TextView
    private lateinit var noteEdit: EditText
    private lateinit var statsView: TextView
    private lateinit var moneyTodayView: TextView
    private lateinit var moneyMonthView: TextView
    private var night = false
    private val es = Locale("es")

    private val tick = object : Runnable {
        override fun run() {
            refreshTotals()
            handler.postDelayed(this, 1000)
        }
    }

    private val fg get() = if (night) Color.parseColor("#E6E6E6") else Color.parseColor("#1F2328")
    private val muted get() = if (night) Color.parseColor("#9AA4AF") else Color.parseColor("#656D76")
    private val cardBg get() = if (night) Color.parseColor("#22272E") else Color.parseColor("#EEF3FB")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(12), dp(16), dp(40))

        dateView = tv(22f, bold = true)
        root.addView(dateView)

        root.addView(moneyCard())
        for (cat in Cat.values()) root.addView(categoryCard(cat))

        root.addView(label("Nota del día"))
        noteEdit = EditText(this)
        noteEdit.hint = "Pingtung, examen, drained…"
        noteEdit.setTextColor(fg)
        noteEdit.setHintTextColor(muted)
        noteEdit.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) saveNote() }
        root.addView(noteEdit)

        statsView = tv(14f)
        statsView.setTextColor(muted)
        statsView.setPadding(0, dp(12), 0, 0)
        root.addView(statsView)

        val title = tv(18f, bold = true)
        title.text = "Últimos 6 meses"
        title.setPadding(0, dp(24), 0, dp(2))
        root.addView(title)
        val hint = tv(13f)
        hint.text = "Toca un día para editar sus horas o su nota."
        hint.setTextColor(muted)
        root.addView(hint)

        for (cat in Cat.values()) {
            root.addView(label(cat.label))
            val hm = HeatmapView(this, cat, night) { date -> editDay(date) }
            heatmaps[cat] = hm
            root.addView(hm, LinearLayout.LayoutParams(MATCH, WRAP))
            root.addView(legend(cat))
        }

        val io = LinearLayout(this)
        io.orientation = LinearLayout.HORIZONTAL
        io.setPadding(0, dp(24), 0, 0)
        io.addView(button("Exportar CSV") { exportCsv() }, LinearLayout.LayoutParams(0, WRAP, 1f))
        io.addView(button("Importar CSV") { importCsv() }, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(io)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        handleSharedFile(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedFile(intent)
    }

    /** CSV compartido desde WhatsApp, Drive, etc. ("Compartir → Progreso" o "Abrir con → Progreso"). */
    private fun handleSharedFile(i: Intent?) {
        if (i == null) return
        val uri: Uri? = when (i.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") (i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            else -> null
        }
        val sharedText = if (i.action == Intent.ACTION_SEND && uri == null) i.getStringExtra(Intent.EXTRA_TEXT) else null
        if (uri == null && sharedText.isNullOrBlank()) return
        i.action = null // para que no se vuelva a importar al girar la pantalla
        try {
            val text = sharedText ?: contentResolver.openInputStream(uri!!)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return
            val n = Csv.import(this, text)
            toast(if (n > 0) "$n días importados" else "No encontré días para importar en ese archivo")
            refreshAll()
        } catch (e: Exception) {
            toast("No pude leer el archivo: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAll()
        handler.post(tick)
        ActionReceiver.scheduleMidnight(this)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        saveNote()
    }

    // ---------- UI ----------

    private fun tv(size: Float, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.textSize = size
        t.setTextColor(fg)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    private fun label(s: String): TextView {
        val t = tv(14f, bold = true)
        t.text = s
        t.setPadding(0, dp(16), 0, dp(6))
        return t
    }

    private fun button(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun categoryCard(cat: Cat): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(16), dp(14), dp(16), dp(10))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(18).toFloat()
        bg.setColor(cardBg)
        card.background = bg
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(14)
        card.layoutParams = lp

        val name = tv(15f)
        name.text = cat.label
        name.setTextColor(muted)
        card.addView(name)

        val total = tv(38f, bold = true)
        totalViews[cat] = total
        card.addView(total)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val start = button("▶  Empezar") {
            Store.toggle(this, cat)
            refreshAll()
        }
        startButtons[cat] = start
        row.addView(start, LinearLayout.LayoutParams(0, WRAP, 1.7f))

        for ((text, mins) in listOf("−15" to -15, "+15" to 15, "+30" to 30)) {
            row.addView(button(text) {
                Store.addTo(this, Store.today(), cat, mins * 60L)
                refreshAll()
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        card.addView(row)
        return card
    }

    private fun moneyCard(): View {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(16), dp(12), dp(12), dp(12))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(18).toFloat()
        bg.setColor(if (night) Color.parseColor("#27500A") else Color.parseColor("#EAF3DE"))
        card.background = bg
        val lp = LinearLayout.LayoutParams(MATCH, WRAP)
        lp.topMargin = dp(14)
        card.layoutParams = lp

        val labelColor = if (night) Color.parseColor("#C0DD97") else Color.parseColor("#3B6D11")
        val valueColor = if (night) Color.parseColor("#EAF3DE") else Color.parseColor("#27500A")
        fun column(caption: String): TextView {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val cap = tv(12f)
            cap.text = caption
            cap.setTextColor(labelColor)
            col.addView(cap)
            val value = tv(24f, bold = true)
            value.setTextColor(valueColor)
            col.addView(value)
            card.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
            return value
        }
        moneyTodayView = column("Dinero hoy")
        moneyMonthView = column("Este mes")
        card.addView(button("+ Dinero") { askMoney() }, LinearLayout.LayoutParams(WRAP, WRAP))
        return card
    }

    private fun askMoney() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
            InputType.TYPE_NUMBER_FLAG_SIGNED
        input.hint = "Ej: 85 (usa -20 para restar)"
        val box = LinearLayout(this)
        box.setPadding(dp(22), dp(8), dp(22), 0)
        box.addView(input, LinearLayout.LayoutParams(MATCH, WRAP))
        AlertDialog.Builder(this)
            .setTitle("Dinero ganado hoy (S/)")
            .setView(box)
            .setPositiveButton("Sumar") { _, _ ->
                val v = parseSoles(input.text.toString())
                if (v != null) {
                    Store.addMoney(this, Store.today(), v)
                    refreshAll()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun legend(cat: Cat): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(12), 0, 0)
        val names = HeatmapRenderer.legend(cat)
        for (i in names.indices) {
            val swatch = View(this)
            val d = GradientDrawable()
            d.cornerRadius = dp(3).toFloat()
            d.setColor(HeatmapRenderer.color(cat, i, night))
            swatch.background = d
            val lp = LinearLayout.LayoutParams(dp(14), dp(14))
            lp.marginStart = if (i == 0) 0 else dp(10)
            lp.marginEnd = dp(4)
            row.addView(swatch, lp)
            val t = tv(12f)
            t.text = names[i]
            t.setTextColor(muted)
            row.addView(t)
        }
        return row
    }

    // ---------- Datos ----------

    private fun refreshAll() {
        val today = Store.today()
        val pretty = today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", es))
        dateView.text = "Hoy · $pretty"
        val days = Store.load(this)
        if (!noteEdit.hasFocus()) noteEdit.setText(days[today]?.note ?: "")
        for (hm in heatmaps.values) hm.setDays(days)
        statsView.text = stats(days)
        moneyTodayView.text = fmtSoles(days[today]?.money ?: 0.0)
        moneyMonthView.text = fmtSoles(Store.moneyThisMonth(days))
        refreshTotals()
        Notifier.update(this)
    }

    private fun refreshTotals() {
        val run = Store.running(this)
        for (cat in Cat.values()) {
            totalViews[cat]?.text = fmtH(Store.todaySecs(this, cat), withSeconds = run == cat)
            startButtons[cat]?.text = if (run == cat) "⏸  Parar" else "▶  Empezar"
        }
    }

    private fun stats(days: Map<LocalDate, Day>): String {
        val today = Store.today()
        fun sum(cat: Cat, n: Int): Long {
            var s = 0L
            for (i in 0 until n) s += days[today.minusDays(i.toLong())]?.get(cat) ?: 0L
            return s
        }
        var streak = 0
        var d = if ((days[today]?.total() ?: 0L) > 0L) today else today.minusDays(1)
        while ((days[d]?.total() ?: 0L) > 0L) {
            streak++
            d = d.minusDays(1)
        }
        var money30 = 0.0
        for (i in 0 until 30) money30 += days[today.minusDays(i.toLong())]?.money ?: 0.0
        return "Últimos 7 días:  Trabajo ${hrs(sum(Cat.WORK, 7))} · 中文 ${hrs(sum(Cat.ZH, 7))}\n" +
            "Últimos 30 días:  Trabajo ${hrs(sum(Cat.WORK, 30))} · 中文 ${hrs(sum(Cat.ZH, 30))}\n" +
            "Dinero últimos 30 días: ${fmtSoles(money30)}\n" +
            "Racha: $streak ${if (streak == 1) "día" else "días"} seguidos"
    }

    private fun hrs(secs: Long) = String.format(Locale.US, "%.1fh", secs / 3600.0)

    private fun saveNote() {
        val days = Store.load(this)
        val today = Store.today()
        val text = noteEdit.text.toString().trim()
        val current = days[today]?.note ?: ""
        if (text == current) return
        days.getOrPut(today) { Day() }.note = text
        Store.save(this, days)
        for (hm in heatmaps.values) hm.setDays(days)
    }

    private fun numField(secs: Long): EditText {
        val e = EditText(this)
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        e.hint = "0"
        if (secs > 0L) {
            e.setText(String.format(Locale.US, "%.2f", secs / 3600.0).trimEnd('0').trimEnd('.'))
        }
        return e
    }

    private fun parseHours(s: String): Long {
        val h = s.replace(',', '.').trim().toDoubleOrNull() ?: 0.0
        return (h * 3600).toLong().coerceAtLeast(0L)
    }

    private fun editDay(date: LocalDate) {
        saveNote()
        val d = Store.load(this)[date] ?: Day()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(22), dp(8), dp(22), 0)

        val work = numField(d.work)
        val zh = numField(d.zh)
        val money = EditText(this)
        money.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        money.hint = "0"
        if (d.money > 0.0) money.setText(String.format(Locale.US, "%.2f", d.money).trimEnd('0').trimEnd('.'))
        val note = EditText(this)
        note.setText(d.note)
        note.hint = "Nota"

        for ((caption, field) in listOf("Horas de trabajo" to work, "Horas de 中文" to zh, "Dinero ganado (S/)" to money, "Nota" to note)) {
            val t = TextView(this)
            t.text = caption
            t.setPadding(0, dp(8), 0, 0)
            box.addView(t)
            box.addView(field)
        }

        AlertDialog.Builder(this)
            .setTitle(date.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", es)))
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val all = Store.load(this)
                val day = all.getOrPut(date) { Day() }
                day.work = parseHours(work.text.toString())
                day.zh = parseHours(zh.text.toString())
                day.note = note.text.toString().trim()
                day.money = (parseSoles(money.text.toString()) ?: 0.0).coerceAtLeast(0.0)
                Store.save(this, all)
                if (date == Store.today()) noteEdit.setText(day.note)
                refreshAll()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- CSV ----------

    private fun exportCsv() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_TITLE, "progreso-${Store.today()}.csv")
        startActivityForResult(i, REQ_EXPORT)
    }

    private fun importCsv() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel"),
            )
        startActivityForResult(i, REQ_IMPORT)
    }

    @Deprecated("Activity API clásica; suficiente para esta app sin dependencias")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                REQ_EXPORT -> {
                    val csv = Csv.export(Store.load(this))
                    contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                    toast("Historial exportado")
                }
                REQ_IMPORT -> {
                    val text = contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: return
                    val n = Csv.import(this, text)
                    toast("$n días importados")
                    refreshAll()
                }
            }
        } catch (e: Exception) {
            toast("Error: ${e.message}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
