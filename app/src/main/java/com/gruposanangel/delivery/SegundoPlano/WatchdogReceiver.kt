package com.gruposanangel.delivery.SegundoPlano

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class WatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val WATCHDOG_REQUEST_CODE = 1001
        private const val WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("Watchdog", "🐶 Verificación de rutina iniciada")

        val serviceIntent = Intent(context, LocationService::class.java).apply {
            action = LocationService.ACTION_START
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("Watchdog", "⚠️ Error al despertar el servicio")
        }

        reprogramarWatchdog(context)
    }

    private fun reprogramarWatchdog(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val alarmIntent = Intent(context, WatchdogReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                WATCHDOG_REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextTrigger = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pending)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTrigger, pending)
            }
        } catch (e: Exception) {
            Log.e("Watchdog", "❌ Error de reprogramación", e)
        }
    }
}