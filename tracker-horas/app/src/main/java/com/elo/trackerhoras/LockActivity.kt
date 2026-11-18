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
 * (con tu fondo de ese momento). Muestra la hora, el cronómetro de lo que está corriendo,
 * el dinero (hoy · año · total) y los tres mapas en grande, con botones que funcionan.
 * Deslizar hacia arriba → pide la huella / PIN y desbloquea.
 */
class LockActivity : Activity() {

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }

    private lateinit var stateView: TextView
    private lateinit var labelView: TextView
    private lateinit var chrono: Chronometer
    private lateinit var summaryView: TextView
    private lateinit var moneyToday: TextView
    private lateinit var moneyYear: TextView
    private lateinit var moneyTotal: TextView
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

        // Cronómetro
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        stateView = text(12f, soft)
        labelView = text(18f, white, bold = true)
        left.addView(stateView)
        left.addView(labelView)
        top.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))
        chrono = Chronometer(this)
        chrono.textSize = 32f
        chrono.setTextColor(white)
        chrono.typeface = Fonts.medium(this)
        top.addView(chrono)
        card.addView(top)
        summaryView = text(13f, soft)
        card.addView(summaryView)

        // Dinero: hoy · año · total (tocar = editar el de hoy)
        val money = LinearLayout(this)
        money.orientation = LinearLayout.HORIZONTAL
        val moneyLp = LinearLayout.LayoutParams(MATCH, WRAP)
        moneyLp.topMargin = dp(12)
        card.addView(money, moneyLp)
        moneyToday = moneyTile(money, "Hoy", last = false)
        moneyYear = moneyTile(money, "Este año", last = false)
        moneyTotal = moneyTile(money, "Total", last = true)

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
        actions.addView(flatButton("+ Dinero") { MoneyDialogs.addToday(this) { refresh() } }, LinearLayout.LayoutParams(0, WRAP, 1f))

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
        val work = Store.todaySecs(this, Cat.WORK)
        val zh = Store.todaySecs(this, Cat.ZH)
        if (run != null) {
            val other = if (run == Cat.WORK) Cat.ZH else Cat.WORK
            stateView.text = "En curso"
            labelView.text = run.label
            chrono.base = SystemClock.elapsedRealtime() - Store.todaySecs(this, run) * 1000
            chrono.visibility = View.VISIBLE
            chrono.start()
            summaryView.text = "Hoy también: ${other.label} ${fmtH(Store.todaySecs(this, other))}"
        } else {
            chrono.stop()
            chrono.visibility = View.GONE
            stateView.text = "Hoy"
            labelView.text = "Trabajo ${fmtH(work)} · 中文 ${fmtH(zh)}"
            summaryView.text = "Toca ▶ para empezar a contar"
        }
        moneyToday.text = fmtSoles(days[Store.today()]?.money ?: 0.0)
        moneyYear.text = fmtSoles(Store.moneyThisYear(days))
        moneyTotal.text = fmtSoles(Store.moneyTotal(days))
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

    private fun moneyTile(row: LinearLayout, caption: String, last: Boolean): TextView {
        val tile = LinearLayout(this)
        tile.orientation = LinearLayout.VERTICAL
        tile.setPadding(dp(10), dp(6), dp(10), dp(6))
        val bg = GradientDrawable()
        bg.cornerRadius = dp(14).toFloat()
        bg.setColor(Color.parseColor("#E6F1FB"))
        tile.background = bg
        val cap = text(11f, Color.parseColor("#185FA5"))
        cap.text = caption
        tile.addView(cap)
        val value = text(17f, Color.parseColor("#0C447C"), bold = true)
        value.maxLines = 1
        tile.addView(value)
        tile.setOnClickListener { MoneyDialogs.editToday(this) { refresh() } }
        val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
        if (!last) lp.marginEnd = dp(6)
        row.addView(tile, lp)
        return value
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
