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
 * Dos notificaciones:
 *
 * 1) La "píldora" (LIVE_ID): Live Update de Android 16 que One UI 8 muestra en la Now Bar,
 *    debajo de la hora. Solo texto corto: el cronómetro si hay algo corriendo, o
 *    "Hoy: Trabajo 2h · 中文 0h". La mantiene viva LiveService (servicio en primer plano).
 *
 * 2) La tarjeta con los mapas (ID), igual que antes:, encima de tu fondo de pantalla:
 *  - arriba, el cronómetro con lo que estás haciendo ("Progreso · 中文 · 0:42:10")
 *  - dinero ganado hoy y en el mes (S/)
 *  - los tres mapas de calor: Trabajo, 中文 y Ejercicio
 *  - botones ▶/⏸ Trabajo, ▶/⏸ 中文 y "+ Dinero" (se escribe el monto ahí mismo)
 */
object Notifier {
    private const val CHANNEL = "progreso"
    private const val ID = 1
    const val LIVE_ID = 2
    private const val LIVE_CHANNEL = "progreso_live"
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
        if (nm.getNotificationChannel(LIVE_CHANNEL) == null) {
            // Importancia normal (las de importancia mínima no se pueden promover), pero sin sonido.
            val ch = NotificationChannel(LIVE_CHANNEL, "Píldora en la pantalla de bloqueo", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Cronómetro u horas de hoy en la Now Bar (sin sonido)"
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            ch.setSound(null, null)
            ch.enableVibration(false)
            ch.enableLights(false)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        return nm
    }

    private fun short(secs: Long): String {
        val h = secs / 3600
        val m = (secs % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h${String.format("%02d", m)}"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    /**
     * Android 16: setRequestPromotedOngoing(true) y setShortCriticalText(...).
     * La app compila con el SDK 35, así que se llaman por reflexión si el teléfono los tiene,
     * y además se deja el extra estándar (lo mismo que guarda setRequestPromotedOngoing).
     */
    private fun promote(b: Notification.Builder, shortText: String?) {
        b.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        try {
            Notification.Builder::class.java
                .getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                .invoke(b, true)
        } catch (_: Exception) {
        }
        if (shortText != null) {
            try {
                Notification.Builder::class.java
                    .getMethod("setShortCriticalText", String::class.java)
                    .invoke(b, shortText)
            } catch (_: Exception) {
                b.addExtras(Bundle().apply { putString("android.shortCriticalText", shortText) })
            }
        }
    }

    /** La píldora: texto corto, sin vistas personalizadas (requisito para que Android la promueva). */
    fun buildLive(c: Context): Notification {
        ensureChannel(c)
        val run = Store.running(c)
        val work = Store.todaySecs(c, Cat.WORK)
        val zh = Store.todaySecs(c, Cat.ZH)
        val today = "Hoy: Trabajo ${short(work)} · 中文 ${short(zh)}"

        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val b = Notification.Builder(c, LIVE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setColorized(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setDeleteIntent(broadcast(c, ActionReceiver.REPOST))

        if (run != null) {
            val total = Store.todaySecs(c, run)
            b.setContentTitle(run.label)
                .setContentText(today)
                .setCategory(Notification.CATEGORY_STOPWATCH)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - total * 1000)
            promote(b, null) // sin texto corto: la píldora muestra el cronómetro
        } else {
            b.setContentTitle("Progreso")
                .setContentText(today)
                .setCategory(Notification.CATEGORY_STATUS)
                .setShowWhen(false)
            promote(b, today)
        }

        val icon = Icon.createWithResource(c, R.drawable.ic_stat)
        for (cat in Cat.values()) {
            val label = if (run == cat) "⏸ ${cat.label}" else "▶ ${cat.label}"
            val action = if (cat == Cat.WORK) ActionReceiver.TOGGLE_WORK else ActionReceiver.TOGGLE_ZH
            b.addAction(Notification.Action.Builder(icon, label, broadcast(c, action)).build())
        }
        return b.build()
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

        val title = "Dinero hoy ${fmtSoles(moneyToday)} · Este mes ${fmtSoles(moneyMonth)}"
        val hoursLine = "Trabajo ${fmtH(work)} · 中文 ${fmtH(zh)}" // solo para lectores de pantalla / vista mínima

        val small = RemoteViews(c.packageName, R.layout.notif_small)
        small.setTextViewText(R.id.n_title, title)
        small.setViewVisibility(R.id.n_text, android.view.View.GONE)

        val big = RemoteViews(c.packageName, R.layout.notif_big)
        big.setTextViewText(R.id.n_money_today, fmtSoles(moneyToday))
        big.setTextViewText(R.id.n_money_month, fmtSoles(moneyMonth))
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
                .setSubText(run.label) // "Progreso · 中文 · 0:42:10"
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - runningTotal * 1000)
                .setCategory(Notification.CATEGORY_STOPWATCH)
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
            nm.notify(LIVE_ID, buildLive(c))
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: la app sigue funcionando, solo no se ve en la pantalla de bloqueo.
        }
    }
}
