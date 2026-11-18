package com.elo.trackerhoras

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/**
 * El dashboard de la pantalla de bloqueo: UNA sola tarjeta, encima de tu fondo de pantalla.
 *
 *  - Arriba, lo que estás haciendo y el cronómetro corriendo en grande ("中文  0:42:10").
 *    Si no hay nada corriendo: "Hoy: Trabajo 2h · 中文 1h30".
 *  - Dinero de hoy, del año y total (S/).
 *  - Los tres mapas de calor: Trabajo, 中文 y Ejercicio.
 *  - Botones ▶/⏸ Trabajo, ▶/⏸ 中文 y "+ Dinero".
 *
 * Es la notificación del servicio en primer plano (LiveService), así que se mantiene
 * siempre y vuelve sola al prender el teléfono. Canal de importancia normal pero sin
 * sonido: Samsung no muestra las "silenciosas" en la pantalla de bloqueo.
 */
object Notifier {
    const val ID = 1
    const val MONEY_KEY = "monto"
    private const val CHANNEL = "progreso_tarjeta"
    private val OLD_CHANNELS = listOf("progreso", "progreso_live")
    private const val OLD_LIVE_ID = 2

    private fun ensureChannel(c: Context): NotificationManager {
        val nm = c.getSystemService(NotificationManager::class.java)
        for (old in OLD_CHANNELS) {
            if (nm.getNotificationChannel(old) != null) nm.deleteNotificationChannel(old)
        }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Dashboard en la pantalla de bloqueo", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Cronómetro, dinero y mapas de calor (sin sonido)"
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            ch.setSound(null, null)
            ch.enableVibration(false)
            ch.enableLights(false)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        return nm
    }

    fun broadcast(c: Context, action: String, mutable: Boolean = false): PendingIntent =
        PendingIntent.getBroadcast(
            c,
            action.hashCode(),
            Intent(c, ActionReceiver::class.java).setAction(action),
            (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE) or
                PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun short(secs: Long): String {
        val h = secs / 3600
        val m = (secs % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h${String.format("%02d", m)}"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun buildCard(c: Context): Notification {
        ensureChannel(c)
        val days = Store.load(c)
        val run = Store.running(c)
        val work = Store.todaySecs(c, Cat.WORK)
        val zh = Store.todaySecs(c, Cat.ZH)
        val moneyToday = Store.moneyToday(c)
        val moneyYear = Store.moneyThisYear(days)
        val moneyTotal = Store.moneyTotal(days)
        val todayLine = "Hoy: Trabajo ${short(work)} · 中文 ${short(zh)}"

        // Base del cronómetro: el total de hoy de lo que está corriendo.
        val runningTotal = if (run != null) Store.todaySecs(c, run) else 0L
        val chronoBase = SystemClock.elapsedRealtime() - runningTotal * 1000

        // Vista cerrada: lo que corre + cronómetro, o el resumen del día; y el dinero de hoy.
        val small = RemoteViews(c.packageName, R.layout.notif_small)
        // Vista abierta: el dashboard completo.
        val big = RemoteViews(c.packageName, R.layout.notif_big)

        if (run != null) {
            val other = if (run == Cat.WORK) Cat.ZH else Cat.WORK
            small.setTextViewText(R.id.n_title, "${run.label} · ${fmtSoles(moneyToday)} hoy")
            small.setChronometer(R.id.n_chrono, chronoBase, null, true)
            small.setViewVisibility(R.id.n_chrono, View.VISIBLE)

            big.setTextViewText(R.id.n_state, "En curso")
            big.setTextViewText(R.id.n_label, run.label)
            big.setChronometer(R.id.n_chrono, chronoBase, null, true)
            big.setViewVisibility(R.id.n_chrono, View.VISIBLE)
            big.setTextViewText(R.id.n_summary, "Hoy también: ${other.label} ${short(Store.todaySecs(c, other))}")
            big.setViewVisibility(R.id.n_summary, View.VISIBLE)
        } else {
            small.setTextViewText(R.id.n_title, "$todayLine · ${fmtSoles(moneyToday)}")
            small.setChronometer(R.id.n_chrono, chronoBase, null, false)
            small.setViewVisibility(R.id.n_chrono, View.GONE)

            big.setTextViewText(R.id.n_state, "Hoy")
            big.setTextViewText(R.id.n_label, "Trabajo ${short(work)} · 中文 ${short(zh)}")
            big.setChronometer(R.id.n_chrono, chronoBase, null, false)
            big.setViewVisibility(R.id.n_chrono, View.GONE)
            big.setViewVisibility(R.id.n_summary, View.GONE)
        }
        big.setTextViewText(R.id.n_money_today, fmtSoles(moneyToday))
        big.setTextViewText(R.id.n_money_year, fmtSoles(moneyYear))
        big.setTextViewText(R.id.n_money_total, fmtSoles(moneyTotal))
        big.setImageViewBitmap(R.id.n_heatmap, HeatmapRenderer.bitmapForNotification(c, days))

        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )

        val b = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (run != null) "${run.label} en curso" else todayLine)
            .setContentText("Dinero hoy ${fmtSoles(moneyToday)} · Año ${fmtSoles(moneyYear)} · Total ${fmtSoles(moneyTotal)}")
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(if (run != null) Notification.CATEGORY_STOPWATCH else Notification.CATEGORY_STATUS)
            .setContentIntent(open)
            // En Android 14+ se puede deslizar para quitarla: la volvemos a poner.
            .setDeleteIntent(broadcast(c, ActionReceiver.REPOST))

        val icon = Icon.createWithResource(c, R.drawable.ic_stat)
        for (cat in Cat.values()) {
            val label = if (run == cat) "⏸ ${cat.label}" else "▶ ${cat.label}"
            val action = if (cat == Cat.WORK) ActionReceiver.TOGGLE_WORK else ActionReceiver.TOGGLE_ZH
            b.addAction(Notification.Action.Builder(icon, label, broadcast(c, action)).build())
        }
        val moneyInput = RemoteInput.Builder(MONEY_KEY).setLabel("Monto en soles (S/)").build()
        b.addAction(
            Notification.Action.Builder(icon, "+ Dinero", broadcast(c, ActionReceiver.ADD_MONEY, mutable = true))
                .addRemoteInput(moneyInput)
                .build(),
        )
        return b.build()
    }

    fun update(c: Context) {
        val nm = ensureChannel(c)
        try {
            nm.cancel(OLD_LIVE_ID) // la píldora de la versión anterior ya no se usa
            nm.notify(ID, buildCard(c))
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: la app sigue funcionando, solo no se ve en la pantalla de bloqueo.
        }
    }
}
