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
object PermisosManager {

    // Permisos base que casi siempre se requieren, independientemente de la versión de Android.
    // NOTA: Se ha eliminado Manifest.permission.WRITE_EXTERNAL_STORAGE para evitar conflictos con Scoped Storage (Android 10+).
    private val BASE_PERMISSIONS: Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
    )

    // Lista final de permisos que se requerirán en el PermissionGate
    val PERMISOS_REQUERIDOS: Array<String> = BASE_PERMISSIONS.let {
        val mutableList = it.toMutableList()

        // Permisos condicionales por versión de Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Permiso de Ubicación en Segundo Plano (se pide por separado, pero debe chequearse)
            mutableList.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Permiso de Notificaciones (Android 13+)
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