package com.gruposanangel.delivery.utilidades



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
import androidx.compose.ui.unit.dp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset

@Composable
fun VendorSpeedIndicator2(
    speed: Double,
    modifier: Modifier = Modifier
) {
    val velocidadFloat = speed.toFloat()

    // Animación de la cifra numérica
    val velocidadAnimada by animateFloatAsState(
        targetValue = velocidadFloat,
        animationSpec = tween(600),
        label = "velocidadAnimada"
    )

    // Lógica de colores estilo Tesla/App
    val colorTesla = when {
        velocidadFloat < 20 -> Color(0xFF00C853) // Verde (Normal)
        velocidadFloat < 60 -> Color(0xFFFFA000) // Naranja (Precaución)
        else -> Color(0xFFFF0000)                // Rojo (Exceso)
    }

    // Animación suave del color para que no cambie de golpe
    val colorAnimado by animateColorAsState(
        targetValue = colorTesla,
        animationSpec = tween(500),
        label = "colorAnimado"
    )

    val maxVelocidad = 100f
    val progreso = (velocidadAnimada / maxVelocidad).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14f
                val radius = size.minDimension / 2f - strokeWidth

                // Fondo Gris del aro (fijo)
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Arco Activo (color dinámico)
                drawArc(
                    color = colorAnimado,
                    startAngle = 135f,
                    sweepAngle = 270f * progreso,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Icono del Carrito (color dinámico)
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = colorAnimado,
                modifier = Modifier.size(32.dp)
            )
        }

        // 🔥 Número de velocidad (ahora con color dinámico)
        Text(
            text = "${velocidadAnimada.toInt()}",
            style = MaterialTheme.typography.titleLarge,
            color = colorAnimado, // <--- CAMBIO AQUÍ
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        // 🔥 Texto km/h (ahora con color dinámico suave)
        Text(
            text = "km/h",
            style = MaterialTheme.typography.labelSmall,
            color = colorAnimado.copy(alpha = 0.8f) // <--- CAMBIO AQUÍ
        )
    }
}



@Composable
fun VendorSpeedIndicator(
    speed: Double, // km/h
    modifier: Modifier = Modifier
) {
    val targetColor = when {
        speed < 20 -> Color(0xFF1E8E3E) // verde lento/normal
        speed < 50 -> Color(0xFFF29900) // amarillo moderado
        else -> Color(0xFFD93025) // rojo rápido
    }

    val color by animateColorAsState(targetValue = targetColor, animationSpec = tween(500))

    val icon = when {
        speed < 20 -> Icons.Default.DirectionsCar
        speed < 50 -> Icons.Default.DirectionsCar
        else -> Icons.Default.DirectionsCar // podrías usar un icono de alerta
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(4.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = "Velocidad",
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "${"%.1f".format(speed)} km/h", style = MaterialTheme.typography.bodyMedium, color = color)
        Text(text = "Velocidad", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.85f))
    }
}


@Composable
fun VendorGpsIndicator(
    accuracy: Float, // precisión en metros
    modifier: Modifier = Modifier
) {
    val targetColor = when {
        accuracy < 5 -> Color(0xFF1E8E3E) // verde excelente
        accuracy < 15 -> Color(0xFFF29900) // amarillo
        else -> Color(0xFFD93025) // rojo mala señal
    }

    val color by animateColorAsState(targetValue = targetColor, animationSpec = tween(500))

    val icon = when {
        accuracy < 5 -> Icons.Default.GpsFixed
        accuracy < 15 -> Icons.Default.MyLocation
        else -> Icons.Default.GpsFixed // podrías usar otro icono si quieres
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(4.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = "GPS",
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "${accuracy.toInt()} m", style = MaterialTheme.typography.bodyMedium, color = color)
        Text(text = "GPS", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.85f))
    }
}
@Composable
fun VendorBatteryIndicator(
    batteryLevel: Int,
    isCharging: Boolean = false, // por si luego lo mandas desde Firebase
    modifier: Modifier = Modifier
) {

    // 🎨 Color dinámico
    val targetColor = when {
        isCharging -> Color(0xFF1A73E8)
        batteryLevel < 20 -> Color(0xFFD93025)
        batteryLevel < 35 -> Color(0xFFF29900)
        else -> Color(0xFF1E8E3E)
    }

    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(500),
        label = "batteryColor"
    )

    // 🔁 Pulso solo si estuviera cargando
    val isPulsing = isCharging && batteryLevel < 100
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

    // 🔋 Icono dinámico
    val icon = when {
        isCharging -> Icons.Filled.BatteryChargingFull
        batteryLevel >= 90 -> Icons.Filled.BatteryFull
        batteryLevel >= 60 -> Icons.Filled.Battery6Bar
        batteryLevel >= 40 -> Icons.Filled.Battery4Bar
        batteryLevel >= 20 -> Icons.Filled.Battery2Bar
        else -> Icons.Filled.BatteryAlert
    }

    val estadoTexto = when {
        isCharging && batteryLevel == 100 -> "Cargado"
        isCharging -> "Cargando"
        else -> null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(4.dp)
    ) {
        // Icono arriba
        Box(
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Batería vendedor",
                tint = color,
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    }
            )

            if (isCharging) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = "Cargando",
                    tint = color.copy(alpha = boltAlpha),
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        }
                        .align(Alignment.TopEnd)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Porcentaje debajo
        Text(
            text = "$batteryLevel%",
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )

        // Estado de carga (si aplica)
        estadoTexto?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.85f)
            )
        }
    }
}
