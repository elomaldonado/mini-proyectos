package com.elo.trackerhoras

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings

/**
 * Servicio en primer plano:
 *  - mantiene siempre el dashboard de Progreso (cronómetro + dinero + mapas) como notificación;
 *  - si está activada la opción, al prender la pantalla abre LockActivity encima del bloqueo.
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

    private var registered = false

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!Store.lockScreenEnabled(c)) return
            if (!Settings.canDrawOverlays(c)) return // sin este permiso Android no deja abrirla
            val km = c.getSystemService(KeyguardManager::class.java)
            if (!km.isKeyguardLocked) return
            // No tapar una llamada entrante o en curso.
            val audio = c.getSystemService(AudioManager::class.java)
            if (audio.mode == AudioManager.MODE_RINGTONE || audio.mode == AudioManager.MODE_IN_CALL ||
                audio.mode == AudioManager.MODE_IN_COMMUNICATION
            ) return
            try {
                c.startActivity(
                    Intent(c, LockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                )
            } catch (_: Exception) {
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
        if (!registered) {
            val f = IntentFilter(Intent.ACTION_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOn, f, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenOn, f)
            registered = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) {
            try {
                unregisterReceiver(screenOn)
            } catch (_: Exception) {
            }
            registered = false
        }
        super.onDestroy()
    }
}
