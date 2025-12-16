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
        private const val WATCHDOG_INTERVAL_MS = 2 * 60 * 1000L // 2 minutos
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("Watchdog", "Watchdog ejecutado")

        // 1) Si el servicio NO está corriendo → arrancarlo (con ACTION_START para que reprograme)
        if (!LocationService.isRunning) {
            Log.d("Watchdog", "Servicio muerto, reiniciando")
            val startIntent = Intent(context, LocationService::class.java).apply {
                action = LocationService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, startIntent)
            } else {
                context.startService(startIntent)
            }
        } else {
            Log.d("Watchdog", "Servicio activo")
            // ping para que el service reafirme programación en caso necesario
            val pingIntent = Intent(context, LocationService::class.java).apply {
                action = LocationService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, pingIntent)
            } else {
                context.startService(pingIntent)
            }
        }

        // 2) Reprogramar el próximo Watchdog (AlarmManager)
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val alarmIntent = Intent(context, WatchdogReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                WATCHDOG_REQUEST_CODE,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextTrigger = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pending)
            Log.d("Watchdog", "Watchdog reprogramado para +${WATCHDOG_INTERVAL_MS / 1000}s")
        } catch (e: Exception) {
            Log.e("Watchdog", "Error reprogramando Watchdog", e)
        }
    }
}
