package com.elo.trackerhoras

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.widget.RemoteViews

/**
 * La tarjeta fija de la pantalla de bloqueo, encima de tu fondo de pantalla:
 *  - dinero ganado hoy y en el mes (S/)
 *  - horas de hoy de trabajo y 中文 (con cronómetro si está corriendo)
 *  - los dos mapas de calor de 6 meses
 *  - botones ▶/⏸ Trabajo, ▶/⏸ 中文 y "+ Dinero" (se escribe el monto ahí mismo)
 */
object Notifier {
    private const val CHANNEL = "progreso"
    private const val ID = 1
    const val MONEY_KEY = "monto"

    private fun ensureChannel(c: Context): NotificationManager {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Progreso del día", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Horas, dinero y mapa de calor en la pantalla de bloqueo"
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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

    fun update(c: Context) {
        val nm = ensureChannel(c)
        val days = Store.load(c)
        val run = Store.running(c)
        val work = Store.todaySecs(c, Cat.WORK)
        val zh = Store.todaySecs(c, Cat.ZH)
        val moneyToday = Store.moneyToday(c)
        val moneyMonth = Store.moneyThisMonth(days)

        val hoursLine = buildString {
            append("Trabajo ").append(fmtH(work))
            if (run == Cat.WORK) append(" ▶")
            append("  ·  中文 ").append(fmtH(zh))
            if (run == Cat.ZH) append(" ▶")
        }
        val title = if (run == null) "Hoy: ${fmtSoles(moneyToday)}" else "${run.label} en curso · ${fmtSoles(moneyToday)} hoy"

        val small = RemoteViews(c.packageName, R.layout.notif_small)
        small.setTextViewText(R.id.n_title, title)
        small.setTextViewText(R.id.n_text, hoursLine)

        val big = RemoteViews(c.packageName, R.layout.notif_big)
        big.setTextViewText(R.id.n_money_today, fmtSoles(moneyToday))
        big.setTextViewText(R.id.n_money_month, fmtSoles(moneyMonth))
        big.setTextViewText(R.id.n_hours, hoursLine)
        big.setImageViewBitmap(R.id.n_heatmap, HeatmapRenderer.bitmapForNotification(c, days))

        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )

        val b = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(hoursLine)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            // En Android 14+ se puede deslizar para quitarla: la volvemos a poner.
            .setDeleteIntent(broadcast(c, ActionReceiver.REPOST))

        if (run == null) {
            b.setShowWhen(false).setCategory(Notification.CATEGORY_STATUS)
        } else {
            val runningTotal = Store.todaySecs(c, run)
            b.setShowWhen(true)
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - runningTotal * 1000)
                .setCategory(Notification.CATEGORY_STOPWATCH)
            // Android 16 (One UI 8): pide mostrarse como "Live Update" (Now Bar).
            b.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        }

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

        try {
            nm.notify(ID, b.build())
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: la app sigue funcionando, solo no se ve en la pantalla de bloqueo.
        }
    }
}
