package com.gruposanangel.delivery.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
    var tabIndex by remember { mutableIntStateOf(0) }

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
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Red)
                }
                
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("HISTORIAL OPERATIVO", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.DarkGray)
                    Text("Auditoría de Movimientos", fontSize = 12.sp, color = Color.Gray)
                }
                
                IconButton(onClick = { vm.cargarHistorial() }) {
                    Icon(Icons.Default.Refresh, null, tint = Color.Red)
                }
            }
        }

        // --- TABS DE NAVEGACIÓN ---
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color.White,
            contentColor = Color.Red,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                    color = Color.Red
                )
            },
            divider = {}
        ) {
            Tab(
                selected = tabIndex == 0,
                onClick = { tabIndex = 0 },
                text = { Text("CARGAS", fontWeight = FontWeight.Black, fontSize = 12.sp) }
            )
            Tab(
                selected = tabIndex == 1,
                onClick = { tabIndex = 1 },
                text = { Text("ARQUEOS", fontWeight = FontWeight.Black, fontSize = 12.sp) }
            )
        }

        Spacer(Modifier.height(16.dp))

        // --- FILTROS ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                        val label = if (startStr == endStr) startStr else "$startStr..."
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                    }
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- RESUMEN DE TOTALES ---
        val listaActual = if (tabIndex == 0) uiState.cargas else uiState.arqueos
        if (listaActual.isNotEmpty() && !uiState.isLoading) {
            val totalMonto = if (tabIndex == 0) listaActual.sumOf { it.montoTotal } else 0.0
            val totalPiezas = listaActual.sumOf { it.totalPiezas }
            
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 20.dp, horizontal = 12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 1. IZQUIERDA: CANTIDAD DE EVENTOS
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(if (tabIndex == 0) "CARGAS" else "ARQUEOS", color = Color.White.copy(0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(text = "${listaActual.size}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                    
                    Box(Modifier.width(1.dp).height(35.dp).background(Color.White.copy(0.15f)))

                    // 2. CENTRO: VALOR MONETARIO O PIEZAS NETAS
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.5f)) {
                        Text(if (tabIndex == 0) "TOTAL PERIODO" else "RESULTADO NETO", color = Color.White.copy(0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        val valorTexto = if (tabIndex == 0) formatoMoneda.format(totalMonto) else "$totalPiezas pzas"
                        Text(text = valorTexto, color = if (totalPiezas < 0 && tabIndex == 1) Color.Red else Color.White, fontSize = if (tabIndex == 0) 24.sp else 22.sp, fontWeight = FontWeight.Black)
                    }
                    
                    Box(Modifier.width(1.dp).height(35.dp).background(Color.White.copy(0.15f)))

                    // 3. DERECHA: TOTAL PIEZAS (Suma absoluta para cargas, cuenta en arqueos)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("PIEZAS", color = Color.White.copy(0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(text = "${if (tabIndex == 0) totalPiezas else listaActual.sumOf { it.productos.size }}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // --- LISTA ---
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.Red) }
        } else if (listaActual.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text(if (tabIndex == 0) "Sin cargas" else "Sin arqueos", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listaActual) { item ->
                    ItemHistorialCarga(item, formatoMoneda) {
                        val objCarga = Plantila_carga(id = item.id, nombreCarga = if (tabIndex == 0) "Carga a ${item.destino}" else "Arqueo de ${item.destino}", aceptada = true, plantillaProductos = item.productos)
                        navController.currentBackStackEntry?.savedStateHandle?.set("carga", objCarga)
                        if (tabIndex == 1) {
                            navController.navigate("DETALLE_ARQUEO")
                        } else {
                            navController.navigate("DETALLE_CARGA")
                        }
                    }
                }
            }
        }
    }

    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState(initialSelectedStartDateMillis = uiState.fechaInicio, initialSelectedEndDateMillis = uiState.fechaFin)
        MaterialTheme(colorScheme = lightColorScheme(primary = Color.Red, onPrimary = Color.White, surface = Color.White, onSurface = Color.Black, secondaryContainer = Color(0xFFFFEBEE), onSecondaryContainer = Color.Red)) {
            DatePickerDialog(onDismissRequest = { showDateRangePicker = false }, confirmButton = { Button(onClick = {
                val start = dateRangePickerState.selectedStartDateMillis
                val end = dateRangePickerState.selectedEndDateMillis
                if (start != null) {
                    val c = Calendar.getInstance(); c.timeInMillis = start; c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); val i = c.timeInMillis
                    val f = if (end != null) { val ce = Calendar.getInstance(); ce.timeInMillis = end; ce.set(Calendar.HOUR_OF_DAY, 23); ce.set(Calendar.MINUTE, 59); ce.timeInMillis } else { i + 86399999L }
                    vm.actualizarFechas(i, f)
                }
                showDateRangePicker = false
            }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), shape = RoundedCornerShape(12.dp)) { Text("APLICAR", fontWeight = FontWeight.ExtraBold) } }, dismissButton = { TextButton(onClick = { showDateRangePicker = false }) { Text("CANCELAR", color = Color.Gray) } }) {
                DateRangePicker(
                    state = dateRangePickerState,
                    title = { Text("PERIODO", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Black) },
                    headline = {
                        val s = dateRangePickerState.selectedStartDateMillis; val e = dateRangePickerState.selectedEndDateMillis
                        val text = if (s != null && e != null) "${formatoFechaSimple.format(Date(s))} - ${formatoFechaSimple.format(Date(e))}" else "Selecciona Rango"
                        Text(text, modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Black, color = Color.Red, fontSize = 18.sp)
                    },
                    showModeToggle = false,
                    modifier = Modifier.weight(1f),
                    colors = DatePickerDefaults.colors(
                        containerColor = Color.White,
                        titleContentColor = Color.DarkGray,
                        headlineContentColor = Color.Red,
                        selectedDayContainerColor = Color.Red,
                        selectedDayContentColor = Color.White,
                        dayInSelectionRangeContainerColor = Color.Red.copy(alpha = 0.15f),
                        dayInSelectionRangeContentColor = Color.Red,
                        todayContentColor = Color.Red,
                        todayDateBorderColor = Color.Red
                    )
                )
            }
        }
    }
}

@Composable
fun ItemHistorialCarga(carga: CargaResumen, formato: NumberFormat, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1f, animationSpec = spring(0.6f, 300f), label = "")
    val statusColor = when (carga.estado) { "ACEPTADA", "ARQUEADO", "COMPLETADA" -> Color(0xFF2E7D32); "PENDIENTE" -> Color(0xFFF57C00); else -> Color.Gray }
    Card(modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.shadow(if (isPressed) 1.dp else 4.dp, RoundedCornerShape(24.dp)).clickable(interactionSource = interactionSource, indication = androidx.compose.material.ripple.rememberRipple(bounded = true, color = Color.Red.copy(0.1f)), onClick = onClick), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(statusColor.copy(0.08f)).border(0.5.dp, statusColor.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(imageVector = if (carga.estado == "PENDIENTE") Icons.Default.Inventory else Icons.Default.CheckCircle, contentDescription = null, tint = statusColor, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = carga.destino, fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color(0xFF1A1A1A))
                Text(text = carga.fechaFormateada, fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                Surface(color = statusColor.copy(0.1f), shape = RoundedCornerShape(8.dp)) { Text(text = carga.estado, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = statusColor) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "${carga.totalPiezas} pzas", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
                if (carga.montoTotal > 0) Text(text = formato.format(carga.montoTotal), fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.Red)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.padding(start = 12.dp).size(20.dp))
        }
    }
}
