package com.gruposanangel.delivery.SegundoPlano

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.gruposanangel.delivery.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            Log.d("BootReceiver", "BOOT_COMPLETED recibido, verificando rol para LocationService")

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val usuario = db.usuarioDao().obtenerUsuarioActual()

                if (usuario?.puestoTrabajo == "Vendedor de Ruta") {
                    val serviceIntent = Intent(context, LocationService::class.java).apply {
                        action = LocationService.ACTION_START
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d("BootReceiver", "🚀 Servicio iniciado para Vendedor de Ruta")
                } else {
                    Log.d("BootReceiver", "🛑 Ignorado: Usuario no es Vendedor de Ruta")
                }
            }
        }
    }
}
