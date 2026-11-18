package com.elo.trackerhoras

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Servicio en primer plano que mantiene siempre el dashboard de Progreso
 * (cronómetro + dinero + mapas) en la pantalla de bloqueo.
 * Se arranca al abrir la app, al prender el teléfono y al actualizar la app.
 */
class LiveService : Service() {

    companion object {
        fun start(c: Context) {
            try {
                c.startForegroundService(Intent(c, LiveService::class.java))
            } catch (_: Exception) {
                // Android puede negar el arranque desde segundo plano; se reintenta al abrir la app.
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifier.buildCard(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID, n)
        }
        Notifier.update(this)
        return START_STICKY
    }
}
