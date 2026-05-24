// PermissionGate.kt
package com.gruposanangel.delivery.ui.screens

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.gruposanangel.delivery.utilidades.PermisosManager

private data class PermissionMessage(
    val title: String,
    val body: String,
    val buttonText: String,
    val buttonAction: () -> Unit
)

private fun Context.findActivity(): ComponentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    throw IllegalStateException("PermissionGate debe usarse dentro del contexto de una Activity")
}

@Composable
fun PermissionGate(
    onAllRequiredChecksPassed: @Composable () -> Unit
) {
    val context = LocalContext.current
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val lifecycleOwner = LocalLifecycleOwner.current

    // Estado local para permisos, ubicación y batería
    var areAllManifestPermissionsGranted by remember {
        mutableStateOf(PermisosManager.todosLosPermisosConcedidos(context))
    }

    var isLocationEnabled by remember { mutableStateOf(PermisosManager.isUbicacionActivada(context)) }
    var isGpsEnabled by remember { mutableStateOf(PermisosManager.isGpsActivado(context)) }
    var isIgnoringBattery by remember { mutableStateOf(PermisosManager.ignoraOptimizacionBateria(context)) }
    var batteryDialogOpened by rememberSaveable { mutableStateOf(false) }

    var isBackgroundLocationGranted by remember {
        mutableStateOf(PermisosManager.tieneUbicacionSegundoPlano(context))
    }

    // ------------------------------
    // Launchers para permisos y ubicación en segundo plano
    // ------------------------------
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        areAllManifestPermissionsGranted = PermisosManager.todosLosPermisosConcedidos(context)
        isGpsEnabled = PermisosManager.isGpsActivado(context)
        isIgnoringBattery = PermisosManager.ignoraOptimizacionBateria(context)
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isBackgroundLocationGranted = PermisosManager.tieneUbicacionSegundoPlano(context)
    }

    // ------------------------------
    // Observador onResume para re-chequear permisos, ubicación y batería
    // ------------------------------
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                areAllManifestPermissionsGranted = PermisosManager.todosLosPermisosConcedidos(context)
                isLocationEnabled = PermisosManager.isUbicacionActivada(context)
                isGpsEnabled = PermisosManager.isGpsActivado(context)
                isIgnoringBattery = PermisosManager.ignoraOptimizacionBateria(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    isBackgroundLocationGranted = PermisosManager.tieneUbicacionSegundoPlano(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ------------------------------
    // Listener para cambios de estado de ubicación en tiempo real
    // ------------------------------
    // Listener para cambios de estado de ubicación en tiempo real
    DisposableEffect(locationManager) {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: android.location.Location) {}
            override fun onProviderEnabled(provider: String) {
                isLocationEnabled = PermisosManager.isUbicacionActivada(context)
            }
            override fun onProviderDisabled(provider: String) {
                isLocationEnabled = PermisosManager.isUbicacionActivada(context)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }

        // ✅ SOLO registrar listener si tenemos permisos
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    listener
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        0L,
                        0f,
                        listener
                    )
                }
            } catch (e: SecurityException) {
                // Esto nunca debería pasar porque chequeamos permisos, pero lo capturamos por seguridad
                e.printStackTrace()
            }
        }

        onDispose {
            locationManager.removeUpdates(listener)
        }
    }

    // ------------------------------
    // Control de diálogo de batería
    // ------------------------------
    val shouldShowBatteryDialog = !isIgnoringBattery && !batteryDialogOpened
    LaunchedEffect(shouldShowBatteryDialog) {
        if (shouldShowBatteryDialog) batteryDialogOpened = true
    }

    // ------------------------------
    // Evaluar qué acción mostrar
    // ------------------------------
    val currentAction: PermissionMessage? = run {
        val activity = context.findActivity()

        // 1️⃣ Permisos faltantes
        val requiredButNotGranted = if (!areAllManifestPermissionsGranted) {
            PermisosManager.PERMISOS_REQUERIDOS.firstOrNull { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }
        } else null

        if (requiredButNotGranted != null) {
            val needsManualSettings = !activity.shouldShowRequestPermissionRationale(requiredButNotGranted)
            val permissionName = when (requiredButNotGranted) {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION -> "Ubicación"
                Manifest.permission.POST_NOTIFICATIONS -> "Notificaciones"
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN -> "Dispositivos Cercanos (Bluetooth)"
                Manifest.permission.CAMERA -> "Cámara"
                else -> requiredButNotGranted.split(".").last()
            }

            return@run if (needsManualSettings) {
                PermissionMessage(
                    "⚠️ Permiso de $permissionName Denegado",
                    "El permiso de $permissionName es crucial para el funcionamiento de la app. Por favor, actívelo manualmente desde Ajustes.",
                    "Abrir Configuración App"
                ) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            } else {
                PermissionMessage(
                    "⚠️ Permiso Requerido: $permissionName",
                    "Necesitamos el permiso de $permissionName para poder continuar con el rastreo y la funcionalidad del sistema.",
                    "Conceder $permissionName"
                ) {
                    permissionLauncher.launch(PermisosManager.PERMISOS_REQUERIDOS)
                }
            }
        }

        // 2️⃣ Ubicación desactivada (detecta instantáneamente)
        if (!isLocationEnabled) {
            return@run PermissionMessage(
                "📍 Ubicación Desactivada",
                "El servicio de rastreo requiere que la ubicación del dispositivo esté activa.",
                "Abrir Configuración"
            ) {
                context.startActivity(PermisosManager.getIntentParaActivarGps())
            }
        }

        // 3️⃣ Ubicación en segundo plano (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            areAllManifestPermissionsGranted &&
            isGpsEnabled &&
            !isBackgroundLocationGranted
        ) {
            return@run PermissionMessage(
                "📍 Ubicación en segundo plano",
                "Para que el rastreo funcione correctamente incluso con la app cerrada, debes permitir la ubicación en segundo plano.\n\n" +
                        "En la siguiente pantalla selecciona:\n\"Permitir todo el tiempo\"",
                "Abrir Configuración"
            ) {
                backgroundLocationLauncher.launch(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            }
        }

        // 4️⃣ Optimización de batería
        if (!isIgnoringBattery && !batteryDialogOpened) {
            return@run PermissionMessage(
                "🔋 Optimización de Batería",
                "Para evitar que el sistema detenga el servicio de rastreo, debes desactivar la optimización de batería para esta aplicación.",
                "Desactivar Optimización"
            ) {
                context.startActivity(PermisosManager.getIntentParaIgnorarOptimizacion(context))
            }
        }

        null
    }

    if (currentAction == null) {
        onAllRequiredChecksPassed()
    } else {
        FullScreenPermissionDialog(currentAction, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun FullScreenPermissionDialog(
    message: PermissionMessage,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                message.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Red,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                message.body,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = message.buttonAction,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text(message.buttonText)
            }
        }
    }
}
