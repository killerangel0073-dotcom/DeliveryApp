package com.gruposanangel.delivery.utilidades

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Modelo de Bloqueo de Seguridad (Hard-Lock).
 * Define la razón del bloqueo y la acción correctiva.
 */
sealed class SecurityBlock(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val actionIntent: Intent,
    val buttonText: String
) {
    // Bloqueo por falta de Permiso de Ubicación
    class PermissionDenied(val permission: String, context: Context) : SecurityBlock(
        title = "Ubicación Requerida",
        description = "Es obligatorio conceder el permiso de ubicación 'Siempre' o 'Mientras la app está en uso' para el rastreo de ruta.",
        icon = Icons.Default.GpsOff,
        buttonText = "CONFIGURAR EN AJUSTES",
        actionIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    )

    // Bloqueo por Sensor de GPS Apagado (Hardware)
    object GpsSensorOff : SecurityBlock(
        title = "GPS Desactivado",
        description = "El interruptor de ubicación de tu teléfono está apagado. Actívalo para permitir el rastreo satelital de la unidad.",
        icon = Icons.Default.LocationDisabled,
        buttonText = "ACTIVAR INTERRUPTOR GPS",
        actionIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    )

    // Otros permisos críticos (Cámara)
    class CameraDenied(context: Context) : SecurityBlock(
        title = "Cámara Requerida",
        description = "Necesitamos acceso a la cámara para que puedas capturar evidencias y fotos de tus clientes.",
        icon = Icons.Default.CameraAlt,
        buttonText = "CONCEDER PERMISO",
        actionIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    )

    // Notificaciones
    class NotificationsDenied(context: Context) : SecurityBlock(
        title = "Notificaciones Off",
        description = "Debes activar las notificaciones para recibir alertas importantes y mensajes del supervisor.",
        icon = Icons.Default.Notifications,
        buttonText = "ACTIVAR ALERTAS",
        actionIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    )
}

/**
 * Monitor de Seguridad Híbrido (Software + Hardware).
 */
class SecurityMonitor(private val context: Context) {

    fun getNextBlockingAction(): SecurityBlock? {
        // 1. Validar Permiso de Ubicación (Manifiesto)
        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocation) {
            return SecurityBlock.PermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION, context)
        }

        // 2. Validar Sensor de GPS (Hardware) - El interruptor físico
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = try {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        } catch (_: Exception) { false }

        if (!isGpsEnabled) {
            return SecurityBlock.GpsSensorOff
        }

        // 3. Validar Cámara
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return SecurityBlock.CameraDenied(context)
        }

        // 4. Validar Notificaciones (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return SecurityBlock.NotificationsDenied(context)
            }
        }

        return null
    }
}

/**
 * Wrapper de Seguridad Robusto.
 * Protege contra muerte de proceso y cambios de hardware en tiempo real.
 */
@Composable
fun HardLockPermissionWrapper(
    onLockActive: () -> Unit = {},
    onLockCleared: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val monitor = remember { SecurityMonitor(context.applicationContext) }
    
    var currentBlock by remember { mutableStateOf(monitor.getNextBlockingAction()) }

    // Disparamos callbacks según el estado inicial
    LaunchedEffect(currentBlock) {
        if (currentBlock != null) onLockActive() else onLockCleared()
    }

    // Monitoreo constante del ciclo de vida y eventos del sistema
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val next = monitor.getNextBlockingAction()
                if (next != currentBlock) {
                    currentBlock = next
                }
            }
        }

        // Receptor de eventos de cambio en el hardware de ubicación (Interruptor GPS)
        val gpsReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                currentBlock = monitor.getNextBlockingAction()
            }
        }

        context.registerReceiver(gpsReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                context.unregisterReceiver(gpsReceiver)
            } catch (_: Exception) { }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentBlock == null) {
            content()
        } else {
            PermissionLockScreen(currentBlock!!) {
                currentBlock = monitor.getNextBlockingAction()
            }
        }
    }
}

/**
 * UI de Bloqueo Absoluto con Estética Corporativa.
 */
@Composable
private fun PermissionLockScreen(
    block: SecurityBlock,
    onCheckAgain: () -> Unit
) {
    val context = LocalContext.current
    
    val infiniteTransition = rememberInfiniteTransition(label = "security_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        modifier = Modifier.fillMaxSize().zIndex(1000f),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(130.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.Red.copy(alpha = 0.08f)
                ) {}
                Icon(
                    imageVector = block.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    tint = Color.Red
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = block.title.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = block.description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = Color.DarkGray,
                lineHeight = 26.sp
            )

            Spacer(Modifier.height(56.dp))

            Button(
                onClick = {
                    try {
                        context.startActivity(block.actionIntent)
                    } catch (e: Exception) {
                        // Fallback por si el intent específico falla en algún modelo
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Icon(Icons.Default.Security, null)
                Spacer(Modifier.width(12.dp))
                Text(block.buttonText, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }

            Spacer(Modifier.height(20.dp))

            TextButton(onClick = onCheckAgain) {
                Text(
                    "REINTENTAR VALIDACIÓN",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(32.dp))
            
            Text(
                text = "Seguridad del Sistema Logístico Delisa\nID: APP-SEC-LOCK",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = Color.LightGray
            )
        }
    }
}
