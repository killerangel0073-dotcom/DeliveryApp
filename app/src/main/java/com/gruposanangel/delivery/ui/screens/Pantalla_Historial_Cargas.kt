package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gruposanangel.delivery.model.Plantila_carga
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaHistorialCargas(
    navController: NavController
) {
    val vm: HistorialCargasViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val formatoFechaSimple = SimpleDateFormat("dd/MM/yyyy", Locale("es", "MX"))

    var showVendedorFilter by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
    ) {
        // --- CABECERA ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("HISTORIAL DE CARGAS", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.DarkGray)
                    Text("Surtido a Vendedores", fontSize = 12.sp, color = Color.Gray)
                }
                
                IconButton(onClick = { vm.cargarHistorial() }) {
                    Icon(Icons.Default.Refresh, null, tint = Color.Red)
                }
            }
        }

        // --- FILTROS ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Filtro Vendedor
            Box(modifier = Modifier.weight(1.2f)) {
                OutlinedCard(
                    onClick = { showVendedorFilter = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("RUTA / DESTINO", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text(uiState.filtroVendedor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.Red, maxLines = 1)
                        }
                        Icon(Icons.Default.FilterList, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    }
                }
                DropdownMenu(expanded = showVendedorFilter, onDismissRequest = { showVendedorFilter = false }) {
                    uiState.listaVendedores.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v) },
                            onClick = { 
                                vm.actualizarFiltroVendedor(v)
                                showVendedorFilter = false 
                            }
                        )
                    }
                }
            }

            // Filtro Fecha
            OutlinedCard(
                modifier = Modifier.weight(1f),
                onClick = { showDateRangePicker = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PERIODO", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        val startStr = formatoFechaSimple.format(Date(uiState.fechaInicio))
                        val endStr = formatoFechaSimple.format(Date(uiState.fechaFin))
                        val label = if (startStr == endStr) startStr else "$startStr al $endStr"
                        Text(
                            text = label, 
                            fontSize = 10.sp, 
                            fontWeight = FontWeight.ExtraBold, 
                            color = Color.Black, 
                            maxLines = 1
                        )
                    }
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- RESUMEN DE TOTALES ---
        if (uiState.cargas.isNotEmpty() && !uiState.isLoading) {
            val totalMonto = uiState.cargas.sumOf { it.montoTotal }
            val totalPiezas = uiState.cargas.sumOf { it.totalPiezas }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)), // Negro Elegante
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 1. CARGAS (Izquierda)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "CARGAS", 
                            color = Color.White.copy(alpha = 0.5f), 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "${uiState.cargas.size}", 
                            color = Color.White, 
                            fontSize = 20.sp, 
                            fontWeight = FontWeight.Black
                        )
                    }
                    
                    Box(Modifier.width(1.dp).height(35.dp).background(Color.White.copy(0.15f)))

                    // 2. TOTAL (Centro)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "TOTAL EN PERIODO", 
                            color = Color.White.copy(alpha = 0.5f), 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = formatoMoneda.format(totalMonto), 
                            color = Color.Red, 
                            fontSize = 24.sp, 
                            fontWeight = FontWeight.Black
                        )
                    }
                    
                    Box(Modifier.width(1.dp).height(35.dp).background(Color.White.copy(0.15f)))

                    // 3. PIEZAS (Derecha)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "PIEZAS", 
                            color = Color.White.copy(alpha = 0.5f), 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "$totalPiezas", 
                            color = Color.White, 
                            fontSize = 20.sp, 
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // --- LISTA DE CARGAS ---
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Red)
            }
        } else if (uiState.cargas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text("No hay cargas en este periodo", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.cargas) { carga ->
                    ItemHistorialCarga(carga, formatoMoneda) {
                        val objCarga = Plantila_carga(
                            id = carga.id,
                            nombreCarga = "Carga a ${carga.destino}",
                            aceptada = carga.estado == "ACEPTADA",
                            plantillaProductos = carga.productos
                        )
                        navController.currentBackStackEntry?.savedStateHandle?.set("carga", objCarga)
                        navController.navigate("DETALLE_CARGA")
                    }
                }
            }
        }
    }

    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = uiState.fechaInicio,
            initialSelectedEndDateMillis = uiState.fechaFin
        )
        
        // --- TEMA ROJO DELISA PARA EL CALENDARIO ---
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Color.Red,
                onPrimary = Color.White,
                surface = Color.White,
                onSurface = Color.Black,
                secondaryContainer = Color(0xFFFFEBEE), // Rosa suave para el rango seleccionado
                onSecondaryContainer = Color.Red
            )
        ) {
            DatePickerDialog(
                onDismissRequest = { showDateRangePicker = false },
                confirmButton = {
                    Button(
                        onClick = {
                            val start = dateRangePickerState.selectedStartDateMillis
                            val end = dateRangePickerState.selectedEndDateMillis
                            
                            if (start != null) {
                                val cal = Calendar.getInstance()
                                cal.timeInMillis = start
                                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                                val inicioFinal = cal.timeInMillis
                                
                                val finFinal = if (end != null) {
                                    val calEnd = Calendar.getInstance()
                                    calEnd.timeInMillis = end
                                    calEnd.set(Calendar.HOUR_OF_DAY, 23); calEnd.set(Calendar.MINUTE, 59); calEnd.set(Calendar.SECOND, 59)
                                    calEnd.timeInMillis
                                } else {
                                    inicioFinal + 86399999L
                                }
                                
                                vm.actualizarFechas(inicioFinal, finFinal)
                            }
                            showDateRangePicker = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) { 
                        Text("APLICAR FILTRO", fontWeight = FontWeight.ExtraBold) 
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDateRangePicker = false }) { 
                        Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold) 
                    }
                }
            ) {
                DateRangePicker(
                    state = dateRangePickerState,
                    title = { 
                        Text(
                            "CONSULTA DE MOVIMIENTOS", 
                            modifier = Modifier.padding(16.dp), 
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        ) 
                    },
                    headline = { 
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        val label = if (start != null && end != null) {
                            "${formatoFechaSimple.format(Date(start))} - ${formatoFechaSimple.format(Date(end))}"
                        } else if (start != null) {
                            "Desde: ${formatoFechaSimple.format(Date(start))}"
                        } else {
                            "Selecciona Rango"
                        }
                        Text(
                            text = label, 
                            modifier = Modifier.padding(horizontal = 16.dp),
                            fontWeight = FontWeight.Black,
                            color = Color.Red,
                            fontSize = 18.sp
                        ) 
                    },
                    showModeToggle = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ItemHistorialCarga(
    carga: CargaResumen, 
    formato: NumberFormat,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 🎭 Animación de Escala Tactil (Premium)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "itemScale"
    )

    // 🛡️ Lógica de Colores de Estado Inteligente
    // Si dice ACEPTADA pero viene de carga manual, es equivalente a COMPLETADA.
    val estadoVisible = when(carga.estado) {
        "COMPLETADA" -> "COMPLETADA"
        "ACEPTADA" -> if (carga.origen.contains("MANUAL")) "CARGA DIRECTA" else "ACEPTADA"
        "PENDIENTE" -> "PENDIENTE"
        else -> carga.estado
    }

    val statusColor = when (carga.estado) {
        "COMPLETADA" -> Color(0xFF2E7D32) // Verde
        "ACEPTADA" -> if (carga.origen.contains("MANUAL")) Color(0xFF2E7D32) else Color(0xFF1976D2) // Verde si es manual, Azul si es normal aceptada
        "PENDIENTE" -> Color(0xFFF57C00) // Naranja
        "RECHAZADA" -> Color.Red
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isPressed) 1.dp else 4.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color.Black.copy(alpha = 0.2f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(
                    bounded = true, 
                    color = Color.Red.copy(alpha = 0.1f)
                ),
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icono de Estado con efecto de profundidad
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(0.08f))
                    .border(0.5.dp, statusColor.copy(0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(carga.estado) {
                        "COMPLETADA" -> Icons.Default.CheckCircle
                        "PENDIENTE" -> Icons.Default.Inventory
                        else -> Icons.Default.LocalShipping
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = carga.destino, 
                    fontWeight = FontWeight.Black, 
                    fontSize = 15.sp, 
                    color = Color(0xFF1A1A1A),
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = carga.fechaFormateada, 
                    fontSize = 11.sp, 
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = statusColor.copy(0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = estadoVisible,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${carga.totalPiezas} pzas", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 13.sp, 
                    color = Color.Gray
                )
                Text(
                    text = formato.format(carga.montoTotal),
                    fontWeight = FontWeight.Black, 
                    fontSize = 17.sp, 
                    color = Color.Red,
                    letterSpacing = (-0.5).sp
                )
            }
            
            Icon(
                Icons.Default.ChevronRight, 
                null, 
                tint = Color.LightGray, 
                modifier = Modifier.padding(start = 12.dp).size(20.dp)
            )
        }
    }
}
