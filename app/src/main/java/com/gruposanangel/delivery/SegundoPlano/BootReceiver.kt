package com.gruposanangel.delivery.SegundoPlano

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat


// Receptor para iniciar el servicio después de que el dispositivo se reinicie
class BootReceiver : BroadcastReceiver() {
    // Se llama cuando se recibe una emisión
    override fun onReceive(context: Context, intent: Intent) {
        // Verificar si la acción es BOOT_COMPLETED
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Leer las preferencias compartidas para verificar si el permiso de ubicación fue concedido
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val locationPermissionGranted = prefs.getBoolean("locationPermissionGranted", false)

            // Si el permiso fue concedido, iniciar el servicio de ubicación en primer plano
            if (locationPermissionGranted) {
                val serviceIntent = Intent(context, LocationService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
