package com.gruposanangel.delivery.SegundoPlano

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.gruposanangel.delivery.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.d("BootReceiver", "BOOT_COMPLETED recibido, verificando rol y permisos.")

            // Verificación de permisos básica antes de intentar arrancar FGS
            val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasLocation) {
                Log.w("BootReceiver", "Abortando: Permisos de ubicación no concedidos.")
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val usuario = db.usuarioDao().obtenerUsuarioActual()

                val puesto = usuario?.puestoTrabajo?.trim() ?: ""
                if (puesto == "Vendedor de Ruta" || puesto == "Suplente de Ruta") {
                    val serviceIntent = Intent(context, LocationService::class.java).apply {
                        action = LocationService.ACTION_START
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d("BootReceiver", "🚀 Servicio iniciado para $puesto")
                } else {
                    Log.d("BootReceiver", "🛑 Ignorado: Usuario no requiere rastreo activo ($puesto)")
                }
            }
        }
    }
}
