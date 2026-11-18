package com.elo.trackerhoras

import android.app.AlarmManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Recibe los botones de la notificación, el reinicio del teléfono y la medianoche. */
class ActionReceiver : BroadcastReceiver() {

    companion object {
        const val TOGGLE_WORK = "com.elo.trackerhoras.TOGGLE_WORK"
        const val TOGGLE_ZH = "com.elo.trackerhoras.TOGGLE_ZH"
        const val ADD_MONEY = "com.elo.trackerhoras.ADD_MONEY"
        const val REPOST = "com.elo.trackerhoras.REPOST"
        const val MIDNIGHT = "com.elo.trackerhoras.MIDNIGHT"

        /** A la medianoche se refresca la tarjeta para que el día nuevo empiece en cero. */
        fun scheduleMidnight(c: Context) {
            val am = c.getSystemService(AlarmManager::class.java)
            val at = Store.today().plusDays(1).atStartOfDay(Store.zone()).toInstant().toEpochMilli() + 5_000
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, Notifier.broadcast(c, MIDNIGHT))
        }
    }

    override fun onReceive(c: Context, intent: Intent) {
        when (intent.action) {
            TOGGLE_WORK -> Store.toggle(c, Cat.WORK)
            TOGGLE_ZH -> Store.toggle(c, Cat.ZH)
            ADD_MONEY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(Notifier.MONEY_KEY)?.toString()
                val amount = text?.let { parseSoles(it) }
                if (amount != null) Store.addMoney(c, Store.today(), amount)
            }
        }
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            LiveService.start(c) // vuelve a poner la píldora al prender el teléfono
        }
        Notifier.update(c)
        scheduleMidnight(c)
    }
}
