package com.gruposanangel.delivery.utilidades

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VendorSpeedIndicator2(
    speed: Double,
    modifier: Modifier = Modifier
) {
    val velocidadFloat = speed.toFloat()

    val velocidadAnimada by animateFloatAsState(
        targetValue = velocidadFloat,
        animationSpec = tween(600),
        label = "velocidadAnimada"
    )

    val colorTesla = when {
        velocidadFloat < 20 -> Color(0xFF00C853)
        velocidadFloat < 60 -> Color(0xFFFFA000)
        else -> Color(0xFFFF0000)
    }

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
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = colorAnimado,
                    startAngle = 135f,
                    sweepAngle = 270f * progreso,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = colorAnimado,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = velocidadAnimada.toInt().toString(),
            style = MaterialTheme.typography.titleLarge,
            color = colorAnimado,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "km/h",
            style = MaterialTheme.typography.labelSmall,
            color = colorAnimado.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun VendorSpeedIndicator(
    speed: Double,
    modifier: Modifier = Modifier
) {
    val targetColor = when {
        speed < 20 -> Color(0xFF1E8E3E)
        speed < 50 -> Color(0xFFF29900)
        else -> Color(0xFFD93025)
    }
    val color by animateColorAsState(targetValue = targetColor, animationSpec = tween(500), label = "speedColor")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(4.dp)) {
        Icon(Icons.Default.DirectionsCar, "Velocidad", tint = color, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "${"%.1f".format(speed)} km/h", style = MaterialTheme.typography.bodyMedium, color = color)
        Text(text = "Velocidad", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.85f))
    }
}

@Composable
fun VendorGpsIndicator(
    accuracy: Float,
    modifier: Modifier = Modifier
) {
    val targetColor = when {
        accuracy < 5 -> Color(0xFF1E8E3E)
        accuracy < 15 -> Color(0xFFF29900)
        else -> Color(0xFFD93025)
    }
    val color by animateColorAsState(targetValue = targetColor, animationSpec = tween(500), label = "gpsColor")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(4.dp)) {
        Icon(if (accuracy < 5) Icons.Default.GpsFixed else Icons.Default.MyLocation, "GPS", tint = color, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "${accuracy.toInt()} m", style = MaterialTheme.typography.bodyMedium, color = color)
        Text(text = "GPS", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.85f))
    }
}

@Composable
fun VendorBatteryIndicator(
    batteryLevel: Int,
    modifier: Modifier = Modifier,
    isCharging: Boolean = false
) {
    // 🎨 Color dinámico: Azul intenso si carga, o semáforo según nivel
    val color = when {
        isCharging -> Color(0xFF007AFF) // Azul Apple/Tesla moderno
        batteryLevel < 15 -> Color(0xFFE53935)
        batteryLevel < 40 -> Color(0xFFFB8C00)
        batteryLevel < 65 -> Color(0xFFFDD835)
        batteryLevel < 85 -> Color(0xFF7CB342)
        else -> Color(0xFF43A047)
    }

    // 🔄 Animación de "Pulso" para el modo carga
    val infiniteTransition = rememberInfiniteTransition(label = "charging")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // 🔄 Llenado secuencial
    val animatedSegmentsFloat by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "segmentAnimation"
    )
    val animatedSegments = animatedSegmentsFloat.toInt()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.padding(top = 4.dp)) {
            // Terminal (tapon)
            Box(
                Modifier
                    .size(6.dp, 2.dp)
                    .offset(y = (-2).dp)
                    .background(color, RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
            )
            
            // Cuerpo de la batería
            Column(
                modifier = Modifier
                    .size(16.dp, 26.dp)
                    .border(1.5.dp, color, RoundedCornerShape(3.dp))
                    .padding(1.5.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                if (isCharging) {
                    repeat(4) { i ->
                        val isActive = (3 - i) < animatedSegments
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.5.dp)
                                .padding(vertical = 0.5.dp)
                                .clip(RoundedCornerShape(0.5.dp))
                                .background(if (isActive) color else color.copy(alpha = 0.2f))
                        )
                    }
                } else {
                    val filledSegments = (batteryLevel / 25).coerceAtLeast(if (batteryLevel > 0) 1 else 0)
                    repeat(4) { i ->
                        val isActive = (3 - i) < filledSegments
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.5.dp)
                                .padding(vertical = 0.5.dp)
                                .clip(RoundedCornerShape(0.5.dp))
                                .background(if (isActive) color else Color.Transparent)
                        )
                    }
                }
            }
            
            if (isCharging) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = alphaAnim),
                    modifier = Modifier.size(14.dp).align(Alignment.Center)
                )
            }
        }
        
        Text(
            text = "$batteryLevel%",
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = color
        )
    }
}
