package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gruposanangel.delivery.model.Plantila_carga
import com.gruposanangel.delivery.ui.theme.*
import android.widget.Toast
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaHistorialCargas(
    navController: NavController
) {
    val context = LocalContext.current
    val db = com.gruposanangel.delivery.data.AppDatabase.getDatabase(context)
    val repoUsuario = com.gruposanangel.delivery.RepositoryUsuario(com.gruposanangel.delivery.data.FirebaseDataSource(), db.usuarioDao())
    
    val vm: HistorialCargasViewModel = viewModel(
        factory = HistorialCargasViewModelFactory(repoUsuario)
    )
    val uiState by vm.uiState.collectAsState()
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val formatoFechaSimple = SimpleDateFormat("dd/MM/yyyy", Locale("es", "MX"))

    var showVendedorFilter by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var tabIndex by remember { mutableIntStateOf(0) }
    
    // --- ESTADOS PARA CANCELACIÓN ---
    var cargaSeleccionadaParaCancelar by remember { mutableStateOf<CargaResumen?>(null) }
    var motivoCancelacion by remember { mutableStateOf("") }
    val userRole = vm.userRole.trim()
    val esAdminAutorizado = userRole in listOf("CEO", "Gerente General")
    val esEncargadoAlmacen = userRole in listOf("Encargado Almacen", "Auxiliar de almacen")
    val puedeEditar = esAdminAutorizado || esEncargadoAlmacen

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- CABECERA ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(2.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed)
                }
                
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("HISTORIAL OPERATIVO", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Auditoría de Movimientos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                IconButton(onClick = { vm.cargarHistorial() }) {
                    Icon(Icons.Default.Refresh, null, tint = DelisaRed)
                }
            }
        }

        // --- TABS DE NAVEGACIÓN ---
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = DelisaRed,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[tabIndex]),
                    color = DelisaRed
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
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("RUTA / DESTINO", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(uiState.filtroVendedor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = DelisaRed, maxLines = 1)
                        }
                        Icon(Icons.Default.FilterList, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DropdownMenu(
                    expanded = showVendedorFilter, 
                    onDismissRequest = { showVendedorFilter = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    uiState.listaVendedores.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v, color = MaterialTheme.colorScheme.onSurface) },
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
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PERIODO", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val startStr = formatoFechaSimple.format(Date(uiState.fechaInicio))
                        val endStr = formatoFechaSimple.format(Date(uiState.fechaFin))
                        val label = if (startStr == endStr) startStr else "$startStr..."
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- RESUMEN DE TOTALES ---
        val listaActual = if (tabIndex == 0) uiState.cargas else uiState.arqueos
        if (listaActual.isNotEmpty() && !uiState.isLoading) {
            val listaFiltrada = if (tabIndex == 0) listaActual.filter { it.estado != "CANCELADA" } else listaActual
            
            val totalMonto = if (tabIndex == 0) listaFiltrada.sumOf { it.montoTotal } else 0.0
            val totalPiezas = if (tabIndex == 0) listaFiltrada.sumOf { it.totalPiezas } else listaActual.sumOf { it.totalPiezas }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(DelisaRed, DelisaRedDark)))
                        .padding(vertical = 20.dp, horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 1. IZQUIERDA: CANTIDAD DE EVENTOS
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(if (tabIndex == 0) "CARGAS" else "ARQUEOS", color = Color.White.copy(0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(text = "${if (tabIndex == 0) listaFiltrada.size else listaActual.size}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                        
                        Box(Modifier.width(1.dp).height(35.dp).background(Color.White.copy(0.2f)))

                        // 2. CENTRO: VALOR MONETARIO O PIEZAS NETAS
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.5f)) {
                            Text(if (tabIndex == 0) "TOTAL PERIODO" else "RESULTADO NETO", color = Color.White.copy(0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            val valorTexto = if (tabIndex == 0) formatoMoneda.format(totalMonto) else "$totalPiezas pzas"
                            Text(text = valorTexto, color = if (totalPiezas < 0 && tabIndex == 1) Color.Yellow else Color.White, fontSize = if (tabIndex == 0) 24.sp else 22.sp, fontWeight = FontWeight.Black)
                        }
                        
                        Box(Modifier.width(1.dp).height(35.dp).background(Color.White.copy(0.2f)))

                        // 3. DERECHA: TOTAL PIEZAS
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("PIEZAS", color = Color.White.copy(0.6f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(text = "${if (tabIndex == 0) totalPiezas else listaActual.sumOf { it.productos.size }}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
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
                    ItemHistorialCarga(
                        carga = item, 
                        formato = formatoMoneda, 
                        esAdmin = esAdminAutorizado,
                        puedeEditar = puedeEditar,
                        onCancel = { cargaSeleccionadaParaCancelar = item },
                        onEdit = {
                            navController.navigate("LISTA PRODUCTOS?editOrderId=${item.id}")
                        },
                        onClick = {
                            val objCarga = Plantila_carga(id = item.id, nombreCarga = if (tabIndex == 0) "Carga a ${item.destino}" else "Arqueo de ${item.destino}", aceptada = true, plantillaProductos = item.productos)
                            navController.currentBackStackEntry?.savedStateHandle?.set("carga", objCarga)
                            if (tabIndex == 1) {
                                navController.navigate("DETALLE_ARQUEO")
                            } else {
                                navController.navigate("DETALLE_CARGA")
                            }
                        }
                    )
                }
            }
        }
    }

    if (cargaSeleccionadaParaCancelar != null) {
        AlertDialog(
            onDismissRequest = { cargaSeleccionadaParaCancelar = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "ANULAR CARGA", 
                    fontWeight = FontWeight.Black, 
                    color = DelisaRed,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Esta acción cancelará permanentemente la orden de transferencia a ${cargaSeleccionadaParaCancelar?.destino}.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = motivoCancelacion,
                        onValueChange = { motivoCancelacion = it },
                        label = { Text("Motivo de cancelación") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DelisaRed,
                            focusedLabelColor = DelisaRed,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (motivoCancelacion.length < 5) {
                            Toast.makeText(context, "Escribe un motivo más detallado", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val cargaId = cargaSeleccionadaParaCancelar?.id ?: return@Button
                        vm.cancelarCarga(cargaId, motivoCancelacion) { exito, msg ->
                            if (exito) {
                                Toast.makeText(context, "Carga anulada correctamente", Toast.LENGTH_SHORT).show()
                                cargaSeleccionadaParaCancelar = null
                                motivoCancelacion = ""
                            } else {
                                Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CONFIRMAR ANULACIÓN", fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { cargaSeleccionadaParaCancelar = null }) {
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (showDateRangePicker) {
        val dateRangePickerState = rememberDateRangePickerState(initialSelectedStartDateMillis = uiState.fechaInicio, initialSelectedEndDateMillis = uiState.fechaFin)
        val isDark = ThemeConfig.isDarkTheme.value ?: isSystemInDarkTheme()
        
        DeliveryTheme(darkTheme = isDark) {
            DatePickerDialog(
                onDismissRequest = { showDateRangePicker = false }, 
                confirmButton = { 
                    Button(
                        onClick = {
                            val start = dateRangePickerState.selectedStartDateMillis
                            val end = dateRangePickerState.selectedEndDateMillis
                            if (start != null) {
                                val c = Calendar.getInstance(); c.timeInMillis = start; c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); val i = c.timeInMillis
                                val f = if (end != null) { val ce = Calendar.getInstance(); ce.timeInMillis = end; ce.set(Calendar.HOUR_OF_DAY, 23); ce.set(Calendar.MINUTE, 59); ce.timeInMillis } else { i + 86399999L }
                                vm.actualizarFechas(i, f)
                            }
                            showDateRangePicker = false
                        }, 
                        colors = ButtonDefaults.buttonColors(containerColor = DelisaRed), 
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("APLICAR", fontWeight = FontWeight.ExtraBold, color = Color.White) } 
                }, 
                dismissButton = { 
                    TextButton(onClick = { showDateRangePicker = false }) { 
                        Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant) 
                    } 
                },
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                DateRangePicker(
                    state = dateRangePickerState,
                    title = { Text("PERIODO", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
                    headline = {
                        val s = dateRangePickerState.selectedStartDateMillis; val e = dateRangePickerState.selectedEndDateMillis
                        val text = if (s != null && e != null) "${formatoFechaSimple.format(Date(s))} - ${formatoFechaSimple.format(Date(e))}" else "Selecciona Rango"
                        Text(text, modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Black, color = DelisaRed, fontSize = 18.sp)
                    },
                    showModeToggle = false,
                    modifier = Modifier.weight(1f),
                    colors = DatePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        headlineContentColor = DelisaRed,
                        selectedDayContainerColor = DelisaRed,
                        selectedDayContentColor = Color.White,
                        dayInSelectionRangeContainerColor = DelisaRed.copy(alpha = 0.15f),
                        dayInSelectionRangeContentColor = DelisaRed,
                        todayContentColor = DelisaRed,
                        todayDateBorderColor = DelisaRed,
                        navigationContentColor = MaterialTheme.colorScheme.onSurface,
                        weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        yearContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        currentYearContentColor = DelisaRed,
                        selectedYearContainerColor = DelisaRed,
                        selectedYearContentColor = Color.White
                    )
                )
            }
        }
    }
}

@Composable
fun ItemHistorialCarga(
    carga: CargaResumen, 
    formato: NumberFormat, 
    esAdmin: Boolean,
    puedeEditar: Boolean = false,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1f, animationSpec = spring(0.6f, 300f), label = "")
    
    val esCancelada = carga.estado == "CANCELADA"
    val esPendiente = carga.estado == "PENDIENTE"
    
    val statusColor = when (carga.estado) { 
        "ACEPTADA", "ARQUEADO", "COMPLETADA" -> DelisaGreen
        "PENDIENTE" -> WarningOrange
        "CANCELADA" -> DelisaRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant 
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (isPressed) 1.dp else 4.dp, RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource, 
                indication = androidx.compose.material.ripple.rememberRipple(bounded = true, color = DelisaRed.copy(alpha = 0.1f)), 
                onClick = onClick
            ), 
        shape = RoundedCornerShape(24.dp), 
        colors = CardDefaults.cardColors(
            containerColor = if (esCancelada) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(statusColor.copy(0.08f)).border(0.5.dp, statusColor.copy(0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when(carga.estado) {
                        "PENDIENTE" -> Icons.Default.Inventory
                        "CANCELADA" -> Icons.Default.Cancel
                        else -> Icons.Default.CheckCircle
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
                    color = if (esCancelada) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    style = if (esCancelada) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                )
                Text(text = carga.fechaFormateada, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Surface(color = statusColor.copy(0.1f), shape = RoundedCornerShape(8.dp)) { 
                    Text(text = carga.estado, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = statusColor) 
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${carga.totalPiezas} pzas", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 13.sp, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = if (esCancelada) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                )
                if (carga.montoTotal > 0) {
                    Text(
                        text = formato.format(carga.montoTotal), 
                        fontWeight = FontWeight.Black, 
                        fontSize = 17.sp, 
                        color = if (esCancelada) MaterialTheme.colorScheme.onSurfaceVariant else DelisaRed,
                        style = if (esCancelada) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                    )
                }
            }

            if (esPendiente) {
                Row {
                    if (puedeEditar) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, "Editar", tint = DelisaBlue)
                        }
                    }
                    if (esAdmin) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.DeleteForever, null, tint = DelisaRed)
                        }
                    }
                }
            } else {
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(start = 12.dp).size(20.dp))
            }
        }
    }
}
