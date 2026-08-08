package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gruposanangel.delivery.data.PerfilVenta
import com.gruposanangel.delivery.ui.theme.DelisaGreen
import com.gruposanangel.delivery.ui.theme.DelisaGreenDark
import com.gruposanangel.delivery.ui.theme.DelisaRed
import java.text.NumberFormat

@Composable
fun PerfilVentaSelector(
    perfiles: List<PerfilVenta>,
    seleccionado: PerfilVenta?,
    onSeleccionar: (PerfilVenta) -> Unit,
    siempreMostrar: Boolean = false
) {
    if (perfiles.isEmpty() || (perfiles.size <= 1 && !siempreMostrar)) return

    val indexSeleccionado = perfiles.indexOfFirst { it.id == seleccionado?.id }.coerceAtLeast(0)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 0.dp) // 🔥 Espacio inferior eliminado
    ) {
        // Contenedor principal estilo Segmented Control
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp) // 🔥 Ligeramente más esbelto
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
        ) {
            val maxWidth = maxWidth
            val tabWidth = maxWidth / perfiles.size
            
            // Indicador deslizable (Burbuja)
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * indexSeleccionado,
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
                label = "indicator"
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .padding(3.dp) // Margen interno de la burbuja
                    .shadow(3.dp, RoundedCornerShape(11.dp))
                    .background(DelisaRed, RoundedCornerShape(11.dp))
            )

            // Opciones (Texto)
            Row(modifier = Modifier.fillMaxSize()) {
                perfiles.forEachIndexed { index, perfil ->
                    val isSelected = index == indexSeleccionado
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "textColor"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSeleccionar(perfil) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = perfil.nombre.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = textColor,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BreakdownPerfiles(
    breakdown: List<PerfilBreakdown>,
    formato: NumberFormat,
    metaFrituras: Int = 200,
    onMetaClick: (() -> Unit)? = null,
    mostrarTitulo: Boolean = true,
    compacto: Boolean = false
) {
    if (breakdown.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        if (mostrarTitulo) {
            Text(
                text = "DESGLOSE POR LÍNEA",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 8.dp, start = if (compacto) 0.dp else 24.dp)
            )
        }
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = if (compacto) 0.dp else 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            items(breakdown.size) { index ->
                val item = breakdown[index]
                val esFrituras = item.nombre.trim().equals("Frituras", ignoreCase = true)
                
                Card(
                    modifier = Modifier
                        .width(if (esFrituras && !compacto) 170.dp else 145.dp)
                        .height(if (compacto) 85.dp else 105.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (esFrituras && item.totalPiezas >= metaFrituras) DelisaGreen.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.nombre.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = DelisaRed,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = formato.format(item.total),
                            fontSize = if (compacto) 13.sp else 15.sp,
                            fontWeight = FontWeight.Black,
                            color = if (item.total > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )

                        if (esFrituras) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable(enabled = onMetaClick != null) { onMetaClick?.invoke() }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${item.totalPiezas}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (item.totalPiezas >= metaFrituras) DelisaGreenDark else DelisaRed
                                    )
                                    Text(
                                        text = " / $metaFrituras pzas",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val progreso = (item.totalPiezas.toFloat() / metaFrituras).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progreso },
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .fillMaxWidth(0.8f)
                                        .height(2.dp)
                                        .clip(CircleShape),
                                    color = if (progreso >= 1f) DelisaGreen else DelisaRed,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                )
                            }
                        } else {
                            Spacer(Modifier.height(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryHeader(titulo: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = titulo.uppercase(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = DelisaRed,
            letterSpacing = 1.sp
        )
    }
}
