package com.gruposanangel.delivery.utilidades



import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gruposanangel.delivery.SegundoPlano.BatteryState





@Composable
fun BatteryIndicator(
    modifier: Modifier = Modifier
) {
    val battery by BatteryState.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var wasCharging by remember { mutableStateOf(false) }

    @SuppressLint("MissingPermission")
    LaunchedEffect(battery.isCharging) {
        if (battery.isCharging && !wasCharging) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40)
            }
        }
        wasCharging = battery.isCharging
    }

    val targetColor = when {
        battery.isCharging -> Color(0xFF1A73E8)
        battery.level < 20 -> Color(0xFFD93025)
        battery.level < 35 -> Color(0xFFF29900)
        else -> Color(0xFF1E8E3E)
    }

    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(500),
        label = "batteryColor"
    )

    val isPulsing = battery.isCharging && battery.level < 100
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPulsing) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val boltAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = if (isPulsing) 1f else 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "boltAlpha"
    )

    val icon = when {
        battery.isCharging -> Icons.Filled.BatteryChargingFull
        battery.level >= 90 -> Icons.Filled.BatteryFull
        battery.level >= 60 -> Icons.Filled.Battery6Bar
        battery.level >= 40 -> Icons.Filled.Battery4Bar
        battery.level >= 20 -> Icons.Filled.Battery2Bar
        else -> Icons.Filled.BatteryAlert
    }

    val estadoTexto = when {
        battery.isCharging && battery.level == 100 -> "• Cargado"
        battery.isCharging -> "• Cargando"
        else -> null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Batería",
            tint = color,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = "${battery.level}%",
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )

        if (battery.isCharging) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Bolt,
                contentDescription = "Cargando",
                tint = color.copy(alpha = boltAlpha),
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    }
            )
        }

        AnimatedVisibility(
            visible = estadoTexto != null,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Row {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = estadoTexto ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = color.copy(alpha = 0.85f)
                )
            }
        }
    }
}

