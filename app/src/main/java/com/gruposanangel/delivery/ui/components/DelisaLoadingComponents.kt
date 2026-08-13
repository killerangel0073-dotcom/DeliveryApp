package com.gruposanangel.delivery.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.ui.theme.DelisaRed
import com.gruposanangel.delivery.ui.theme.DelisaGreen

/**
 * Modificador que añade un "Halo de Luz" animado alrededor de un componente.
 * La tarjeta se mantiene estática mientras la luz fluye por el borde.
 */
fun Modifier.delisaGlowBorder(
    isLoading: Boolean,
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 2.dp,
    glowRadius: Dp = 12.dp
): Modifier = composed {
    if (!isLoading) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "glowTransition")
    
    // Animación de rotación para la luz del borde
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    // Animación de pulso para el brillo exterior
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    this.drawWithContent {
        val shadowColor = DelisaRed.copy(alpha = alpha).toArgb()
        val transparentColor = Color.Transparent.toArgb()
        
        drawIntoCanvas { canvas ->
            // 1. DIBUJAR EL HALO (SOMBRA EXTERIOR)
            val shadowPaint = Paint().asFrameworkPaint().apply {
                color = transparentColor
                setShadowLayer(
                    glowRadius.toPx(),
                    0f,
                    0f,
                    shadowColor
                )
            }
            
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = when (outline) {
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Generic -> outline.path
            }
            
            canvas.nativeCanvas.drawPath(
                path.asAndroidPath(),
                shadowPaint
            )
        }

        // 2. DIBUJAR EL CONTENIDO ORIGINAL (ESTÁTICO)
        drawContent()

        // 3. DIBUJAR EL BORDE CON LUZ GIRATORIA (SIN ROTAR LA TARJETA)
        drawIntoCanvas { canvas ->
            val shader = android.graphics.SweepGradient(
                center.x, center.y,
                intArrayOf(
                    DelisaRed.toArgb(), 
                    DelisaGreen.toArgb(), 
                    Color.White.toArgb(), 
                    DelisaRed.toArgb()
                ),
                null
            )
            
            // Aplicar rotación solo al Shader mediante Matrix
            val matrix = android.graphics.Matrix()
            matrix.postRotate(angle, center.x, center.y)
            shader.setLocalMatrix(matrix)

            val borderPaint = Paint().apply {
                this.shader = shader
                this.style = PaintingStyle.Stroke
                this.strokeWidth = borderWidth.toPx()
                this.isAntiAlias = true
            }

            val outline = shape.createOutline(size, layoutDirection, this)
            canvas.drawOutline(outline, borderPaint)
        }
    }
}

@Composable
fun DelisaLogoPulse() {
    val infiniteTransition = rememberInfiniteTransition(label = "logoPulse")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .graphicsLayer { rotationZ = rotation }
                .border(
                    width = 4.dp,
                    brush = Brush.sweepGradient(
                        listOf(DelisaRed, DelisaGreen, DelisaRed)
                    ),
                    shape = CircleShape
                )
        )
        
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Delisa Logo",
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
        )
    }
}
