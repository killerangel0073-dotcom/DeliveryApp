package com.gruposanangel.delivery.SegundoPlano

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.gruposanangel.delivery.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val WATCHDOG_REQUEST_CODE = 1001
        // ⏱ Intervalo más sano: suficiente protección sin molestar al GPS
        private const val WATCHDOG_INTERVAL_MS = 20 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("Watchdog", "🐶 Watchdog check...")

        // 🔹 Solo reinicia si el usuario es Vendedor de Ruta
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val usuario = db.usuarioDao().obtenerUsuarioActual()
            
            if (usuario?.puestoTrabajo == "Vendedor de Ruta") {
                val serviceIntent = Intent(context, LocationService::class.java).apply {
                    action = LocationService.ACTION_START
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                Log.d("Watchdog", "🐶 Ignorado: Usuario no es Vendedor de Ruta")
            }
        }

        // 🔹 Preparamos alarma del Watchdog
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(context, WatchdogReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WATCHDOG_REQUEST_CODE,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAt, pendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                    Log.d("Watchdog", "⏰ Watchdog exact alarm programada")
                } else {
                    Log.w("Watchdog", "Permiso exact alarm no concedido, degradando...")
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                    Log.d("Watchdog", "⏰ Watchdog degradada con setExactAndAllowWhileIdle")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                Log.d("Watchdog", "⏰ Watchdog programada con setExactAndAllowWhileIdle (M+)")
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                Log.d("Watchdog", "⏰ Watchdog programada con setExact (<M)")
            }
        } catch (e: Exception) {
            Log.e("Watchdog", "❌ Error al reactivar Watchdog", e)
        }
    }


}
