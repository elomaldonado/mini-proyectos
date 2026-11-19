package com.elo.trackerhoras

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Chronometer
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import kotlin.math.abs

/**
 * Pantalla de Progreso encima del bloqueo (opción 4).
 * Al prender la pantalla aparece sobre el bloqueo de Samsung, que queda detrás borroso
 * (con tu fondo de ese momento). Muestra la hora, el resumen de hoy (Trabajo y 中文, con el
 * cronómetro en vivo en el que está corriendo) y los tres mapas en grande, con ▶/⏸.
 * Deslizar hacia arriba → pide la huella / PIN y desbloquea.
 */
class LockActivity : Activity() {

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    /** Un cuadro del resumen de hoy (Trabajo o 中文). */
    private class Tile(
        val caption: TextView,
        val total: TextView,
        val chrono: Chronometer,
        val bg: GradientDrawable,
    )

    private val tiles = HashMap<Cat, Tile>()
    private val heatmaps = ArrayList<HeatmapView>()
    private val toggles = HashMap<Cat, Button>()
    private lateinit var gestures: GestureDetector

    private val white = Color.WHITE
    private val soft = Color.parseColor("#C8CCD2")

    /** Si se desbloquea con la huella mientras esta pantalla está arriba, se cierra sola. */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            finish()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(14), dp(56), dp(14), dp(24))

        // Hora y fecha
        val time = TextClock(this)
        time.format12Hour = "h:mm"
        time.format24Hour = "HH:mm"
        time.textSize = 64f
        time.setTextColor(white)
        time.typeface = Fonts.medium(this)
        time.gravity = Gravity.CENTER
        root.addView(time, LinearLayout.LayoutParams(WRAP, WRAP))
        val date = TextClock(this)
        date.format12Hour = "EEEE, d 'de' MMMM"
        date.format24Hour = "EEEE, d 'de' MMMM"
        date.textSize = 16f
        date.setTextColor(white)
        date.typeface = Fonts.regular(this)
        root.addView(date, LinearLayout.LayoutParams(WRAP, WRAP))

        // Tarjeta del dashboard
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(16), dp(14), dp(16), dp(10))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(26).toFloat()
        bg.setColor(Color.parseColor("#E0121418"))
        card.background = bg
        val cardLp = LinearLayout.LayoutParams(MATCH, WRAP)
        cardLp.topMargin = dp(24)
        root.addView(card, cardLp)

        // Resumen de hoy: Trabajo y 中文. El que está corriendo muestra el cronómetro en vivo.
        val hoy = text(12f, soft)
        hoy.text = "Hoy"
        card.addView(hoy)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val rowLp = LinearLayout.LayoutParams(MATCH, WRAP)
        rowLp.topMargin = dp(6)
        card.addView(row, rowLp)
        tiles[Cat.WORK] = summaryTile(row, Cat.WORK, "#1C2A44", "#8FB0E8", last = false)
        tiles[Cat.ZH] = summaryTile(row, Cat.ZH, "#1B3324", "#8FD3A0", last = true)

        // Mapas (sin edición al tocar: eso se hace dentro de la app)
        for (t in Track.values()) {
            val name = text(12f, soft)
            name.text = t.label
            name.setPadding(0, dp(12), 0, dp(2))
            card.addView(name)
            val hm = HeatmapView(this, t, true) { }
            heatmaps.add(hm)
            card.addView(hm, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        // Botones
        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        val actLp = LinearLayout.LayoutParams(MATCH, WRAP)
        actLp.topMargin = dp(8)
        card.addView(actions, actLp)
        for (cat in Cat.values()) {
            val b = flatButton("") {
                Store.toggle(this, cat)
                Notifier.update(this)
                refresh()
            }
            toggles[cat] = b
            actions.addView(b, LinearLayout.LayoutParams(0, WRAP, 1f))
        }

        // Espacio y aviso para desbloquear
        val spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(MATCH, 0, 1f))
        val hint = text(13f, soft)
        hint.text = "⌃\nDesliza hacia arriba para desbloquear"
        hint.gravity = Gravity.CENTER
        root.addView(hint, LinearLayout.LayoutParams(MATCH, WRAP))

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val dy = (e1?.y ?: e2.y) - e2.y
                if (dy > dp(80) && abs(vy) > abs(vx)) {
                    unlock()
                    return true
                }
                return false
            }
        })
        setContentView(root)

        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(unlockReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(unlockReceiver)
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) {
            finish()
            return
        }
        refresh()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // El deslizamiento funciona aunque empiece sobre la tarjeta.
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                finish()
            }
        })
    }

    private fun refresh() {
        val days = Store.load(this)
        val run = Store.running(this)
        for ((cat, tile) in tiles) {
            val secs = Store.todaySecs(this, cat)
            if (run == cat) {
                tile.caption.text = "${cat.label} · en curso"
                tile.chrono.base = SystemClock.elapsedRealtime() - secs * 1000
                tile.chrono.visibility = View.VISIBLE
                tile.chrono.start()
                tile.total.visibility = View.GONE
                tile.bg.setStroke(dp(2), if (cat == Cat.WORK) Color.parseColor("#3A78D8") else Color.parseColor("#34A853"))
            } else {
                tile.caption.text = cat.label
                tile.chrono.stop()
                tile.chrono.visibility = View.GONE
                tile.total.text = fmtH(secs)
                tile.total.visibility = View.VISIBLE
                tile.bg.setStroke(0, Color.TRANSPARENT)
            }
        }
        for (hm in heatmaps) hm.setDays(days)
        for ((cat, b) in toggles) b.text = if (run == cat) "⏸ ${cat.label}" else "▶ ${cat.label}"
    }

    // ---------- piezas ----------

    private fun text(size: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.textSize = size
        t.setTextColor(color)
        t.typeface = if (bold) Fonts.medium(this) else Fonts.regular(this)
        return t
    }

    private fun summaryTile(row: LinearLayout, cat: Cat, bgColor: String, capColor: String, last: Boolean): Tile {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(12), dp(8), dp(12), dp(8))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(16).toFloat()
        bg.setColor(Color.parseColor(bgColor))
        box.background = bg
        val caption = text(12f, Color.parseColor(capColor))
        caption.text = cat.label
        box.addView(caption)
        val total = text(24f, white, bold = true)
        total.maxLines = 1
        box.addView(total)
        val chrono = Chronometer(this)
        chrono.textSize = 24f
        chrono.setTextColor(white)
        chrono.typeface = Fonts.medium(this)
        chrono.visibility = View.GONE
        box.addView(chrono)
        val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
        if (!last) lp.marginEnd = dp(8)
        row.addView(box, lp)
        return Tile(caption, total, chrono, bg)
    }

    private fun flatButton(label: String, onClick: () -> Unit): Button {
        val b = Button(this, null, android.R.attr.borderlessButtonStyle)
        b.text = label
        b.isAllCaps = false
        b.textSize = 14f
        b.setTextColor(Color.parseColor("#8AB4F8"))
        b.typeface = Typeface.create(Fonts.medium(this), Typeface.NORMAL)
        b.setOnClickListener { onClick() }
        return b
    }
}
