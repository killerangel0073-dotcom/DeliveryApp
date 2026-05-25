// PermissionGate.kt
package com.gruposanangel.delivery.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // Estado local para permisos, ubicación y batería
    var isLocationEnabled by remember { mutableStateOf(PermisosManager.isUbicacionActivada(context)) }
    var isGpsEnabled by remember { mutableStateOf(PermisosManager.isGpsActivado(context)) }
    var isIgnoringBattery by remember { mutableStateOf(PermisosManager.ignoraOptimizacionBateria(context)) }
    var isBackgroundLocationGranted by remember {
        mutableStateOf(PermisosManager.tieneUbicacionSegundoPlano(context))
    }

    // Launcher para permisos base
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        isGpsEnabled = PermisosManager.isGpsActivado(context)
        // Forzamos re-chequeo de ubicación en segundo plano tras otorgar permisos base
        isBackgroundLocationGranted = PermisosManager.tieneUbicacionSegundoPlano(context)
    }

    // Observador onResume: Es la forma más limpia y eficiente de detectar cambios
    // cuando el usuario regresa de la pantalla de Ajustes del Sistema.
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                isLocationEnabled = PermisosManager.isUbicacionActivada(context)
                isGpsEnabled = PermisosManager.isGpsActivado(context)
                isIgnoringBattery = PermisosManager.ignoraOptimizacionBateria(context)
                isBackgroundLocationGranted = PermisosManager.tieneUbicacionSegundoPlano(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ------------------------------
    // Lógica de Evaluación de Bloqueo
    // ------------------------------
    val currentAction: PermissionMessage? = run {
        val activity = context.findActivity()

        // 1️⃣ Permisos base en el Manifiesto (Ubicación Precisa, Cámara, etc.)
        val missingPermission = PermisosManager.PERMISOS_REQUERIDOS.firstOrNull { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermission != null) {
            val needsManualSettings = !activity.shouldShowRequestPermissionRationale(missingPermission)

            val (title, body) = when (missingPermission) {
                android.Manifest.permission.ACCESS_FINE_LOCATION -> 
                    "📍 Ubicación Precisa Obligatoria" to "Para rastrear tu ruta con exactitud, es indispensable permitir la ubicación 'Precisa'. La ubicación 'Aproximada' no es suficiente."
                android.Manifest.permission.POST_NOTIFICATIONS ->
                    "🔔 Notificaciones Requeridas" to "Necesitamos mostrarte alertas de velocidad y mensajes del supervisor en tiempo real."
                else -> "⚠️ Permiso Requerido" to "El permiso de ${missingPermission.split(".").last()} es vital para continuar."
            }

            return@run PermissionMessage(
                title, body, if (needsManualSettings) "Abrir Ajustes" else "Conceder Permiso"
            ) {
                if (needsManualSettings) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    })
                } else {
                    permissionLauncher.launch(PermisosManager.PERMISOS_REQUERIDOS)
                }
            }
        }

        // 2️⃣ Ubicación del sistema (GPS) desactivada
        if (!isLocationEnabled || !isGpsEnabled) {
            return@run PermissionMessage(
                "📍 GPS Desactivado",
                "El sistema de rastreo no puede funcionar si el sensor GPS está apagado. Por favor, actívalo.",
                "Activar GPS"
            ) {
                context.startActivity(PermisosManager.getIntentParaActivarGps())
            }
        }

        // 3️⃣ Ubicación en segundo plano (Android 10+)
        // Obligatorio para que el rastro no se corte al bloquear el teléfono
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isBackgroundLocationGranted) {
            return@run PermissionMessage(
                "🏃 Ubicación en Segundo Plano",
                "Para que tu rastro siga activo aunque guardes el teléfono, debes seleccionar:\n\n\"Permitir todo el tiempo\" \nen los ajustes de ubicación.",
                "Configurar Todo el Tiempo"
            ) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                })
            }
        }

        // 4️⃣ Optimización de batería
        if (!isIgnoringBattery) {
            return@run PermissionMessage(
                "🔋 Optimización de Batería",
                "El sistema de Android podría detener tu rastro para ahorrar batería. Debes desactivar la optimización para esta app.",
                "Desactivar Restricción"
            ) {
                context.startActivity(PermisosManager.getIntentParaIgnorarOptimizacion(context))
            }
        }

        null
    }

    // ------------------------------
    // Renderizado: Gatekeeper
    // ------------------------------
    if (currentAction == null) {
        // Solo si todo está perfecto se renderiza el contenido de la app
        onAllRequiredChecksPassed()
    } else {
        // Bloqueo total de la interfaz
        FullScreenPermissionDialog(currentAction)
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
