package com.gruposanangel.delivery.utilidades

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Clase de utilidad para gestionar todos los chequeos de permisos y requisitos del sistema.
 */

 /* PermisosManager:
 * - Verifica si los permisos necesarios, GPS y batería están correctamente configurados.
 * - Proporciona Intents para abrir ajustes del sistema si algo falta.
 * - No muestra UI, solo chequea estados y devuelve información.
 */
object PermisosManager {


    fun tieneUbicacionSegundoPlano(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // Permisos base que casi siempre se requieren, independientemente de la versión de Android.
    // NOTA: Se ha eliminado Manifest.permission.WRITE_EXTERNAL_STORAGE para evitar conflictos con Scoped Storage (Android 10+).
    // ❌ NO incluyas background aquí
    private val BASE_PERMISSIONS: Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
    }.toTypedArray()


    val PERMISOS_REQUERIDOS: Array<String> = BASE_PERMISSIONS.let {
        val mutableList = it.toMutableList()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mutableList.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        mutableList.toTypedArray()
    }


    /**
     * Chequea si todos los permisos definidos en PERMISOS_REQUERIDOS han sido concedidos.
     */
    fun todosLosPermisosConcedidos(context: Context): Boolean {
        return PERMISOS_REQUERIDOS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isUbicacionActivada(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9+ detecta si el switch de ubicación está activado (cualquier proveedor)
            locationManager.isLocationEnabled
        } else {
            // Android <9: chequea GPS o Network Provider
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }


    /**
     * Chequea si el GPS (servicios de localización) está activo.
     */
    fun isGpsActivado(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /**
     * Chequea si la aplicación está ignorando las optimizaciones de batería.
     */
    fun ignoraOptimizacionBateria(context: Context): Boolean {
        val packageName = context.packageName
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    /**
     * Obtiene el Intent para abrir la configuración del GPS.
     */
    fun getIntentParaActivarGps(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    }

    /**
     * Obtiene el Intent para abrir la configuración de ignorar optimizaciones de batería.
     */
    fun getIntentParaIgnorarOptimizacion(context: Context): Intent {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = android.net.Uri.parse("package:${context.packageName}")
        return intent
    }
}