package com.gruposanangel.delivery.ui.screens

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import kotlinx.coroutines.delay

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
    permissionLauncher: ActivityResultLauncher<Array<String>>,
    permissionsToRequest: Array<String>,
    onAllRequiredChecksPassed: @Composable () -> Unit
) {
    val context = LocalContext.current
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val lifecycleOwner = LocalLifecycleOwner.current

    var areAllManifestPermissionsGranted by remember { mutableStateOf(false) }
    var isGpsEnabled by remember { mutableStateOf(false) }
    var isIgnoringBattery by remember { mutableStateOf(false) }
    var batteryDialogOpened by remember { mutableStateOf(false) }

    // Observador para detectar que la actividad vuelve a primer plano (onResume)
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                // Fuerza re-evaluación de permisos y estados
                areAllManifestPermissionsGranted = PermisosManager.todosLosPermisosConcedidos(context)
                isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                isIgnoringBattery = PermisosManager.ignoraOptimizacionBateria(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Polling reactivo para cambios de permisos, GPS y batería
    LaunchedEffect(Unit) {
        while (true) {
            areAllManifestPermissionsGranted = PermisosManager.todosLosPermisosConcedidos(context)
            isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            isIgnoringBattery = PermisosManager.ignoraOptimizacionBateria(context)

            delay(if (areAllManifestPermissionsGranted && isGpsEnabled && isIgnoringBattery) 2000 else 1000)
        }
    }

    val currentAction: PermissionMessage? = remember(
        areAllManifestPermissionsGranted,
        isGpsEnabled,
        isIgnoringBattery,
        batteryDialogOpened
    ) {
        val activity = context.findActivity()

        // 1️⃣ Revisar permisos
        if (!areAllManifestPermissionsGranted) {
            val requiredButNotGranted = permissionsToRequest.firstOrNull { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
            }

            if (requiredButNotGranted != null) {
                val needsManualSettings = !activity.shouldShowRequestPermissionRationale(requiredButNotGranted)
                val permissionName = when (requiredButNotGranted) {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Ubicación (rastreo)"
                    Manifest.permission.POST_NOTIFICATIONS -> "Notificaciones"
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN -> "Dispositivos Cercanos (Bluetooth)"
                    Manifest.permission.CAMERA -> "Cámara"
                    else -> requiredButNotGranted.split(".").last()
                }

                return@remember if (needsManualSettings) {
                    PermissionMessage(
                        "⚠️ Permiso de $permissionName Denegado",
                        "El permiso de **$permissionName** es crucial para el funcionamiento de la app. Por favor, actívelo manualmente desde Ajustes.",
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
                        "Necesitamos el permiso de **$permissionName** para poder continuar con el rastreo y la funcionalidad del sistema.",
                        "Conceder $permissionName"
                    ) {
                        permissionLauncher.launch(arrayOf(requiredButNotGranted))
                    }
                }
            }
        }

        // 2️⃣ Revisar GPS
        if (!isGpsEnabled) {
            return@remember PermissionMessage(
                "📍 GPS Desactivado",
                "El servicio de rastreo requiere que el GPS del dispositivo esté activo y en **Alta Precisión**.",
                "Abrir Configuración GPS"
            ) {
                context.startActivity(PermisosManager.getIntentParaActivarGps())
            }
        }

        // 3️⃣ Revisar batería
        if (!isIgnoringBattery && !batteryDialogOpened) {
            batteryDialogOpened = true
            return@remember PermissionMessage(
                "🔋 Optimización de Batería",
                "Para evitar que el sistema detenga el servicio de rastreo, debes **desactivar la optimización de batería** para esta aplicación.",
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
