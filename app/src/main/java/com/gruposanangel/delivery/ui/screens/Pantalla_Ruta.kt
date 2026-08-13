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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.ui.theme.*
import com.gruposanangel.delivery.utilidades.MedidorDeMetaPremium
import com.gruposanangel.delivery.utilidades.PreferenciasMetas
import kotlinx.coroutines.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class TicketVenta(
    val id: String, 
    val cliente: String, 
    val total: Double, 
    val fecha: Date, 
    val sincronizado: Boolean, 
    val fotoCliente: String = "",
    val estado: String = "pagada",
    val intentosSync: Int = 0,
    val ultimoError: String? = null
)

@Composable
fun PaginaVentaScreen(
    navController: NavController, 
    ventaRepository: VentaRepository,
    isAdminOverride: Boolean? = null
) {
    val context = LocalContext.current; val isPreview = LocalInspectionMode.current
    val db = AppDatabase.getDatabase(context); val repoUsuario = RepositoryUsuario(FirebaseDataSource(), db.usuarioDao())
    val viewModel: VentaViewModel = viewModel(
        factory = VentaViewModelFactory(
            repositoryInventario = RepositoryInventario(FirebaseDataSource(), db.productoDao(), db.VentaDao(), db.movimientoInventarioDao()), 
            ventaRepository = ventaRepository, 
            repositoryUsuario = repoUsuario,
            clienteDao = db.clienteDao()
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ticketsHoy by viewModel.ticketsHoyFlow.collectAsStateWithLifecycle()

    val isDark = ThemeConfig.isActuallyDark

    // 🔥 SINCRONIZAR CON EL MODO (ADMIN/RUTA) DE LA PANTALLA PRINCIPAL
    LaunchedEffect(isAdminOverride) {
        if (isAdminOverride != null) {
            viewModel.sobreescribirAdmin(isAdminOverride)
            viewModel.sincronizarVentasDia(FirebaseAuth.getInstance().currentUser?.uid ?: "")
        }
    }

    // 🛡️ RESET PROACTIVO AL SALIR DE LA PANTALLA
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetFiltro()
        }
    }

    DeliveryTheme(darkTheme = isDark) {
        PaginaVentaContent(
            ticketsHoy = ticketsHoy, 
            uiState = uiState,
            onTicketClick = { navController.navigate("detalle_venta_admin/${it.id}") },
            onFiltroRutaChanged = { viewModel.cambiarFiltroRuta(it) },
            onDateRangeSelected = { inicio, fin -> viewModel.actualizarRangoFechas(inicio, fin) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaginaVentaContent(
    ticketsHoy: List<TicketVenta>, 
    uiState: VentaUiState,
    onTicketClick: (TicketVenta) -> Unit,
    onFiltroRutaChanged: (String) -> Unit,
    onDateRangeSelected: (Long, Long) -> Unit
) {
    val context = LocalContext.current; val fmtMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    val fmtFecha = SimpleDateFormat("EEEE d 'de' MMMM, hh:mm a", Locale.forLanguageTag("es-MX"))
    val prefs = remember { PreferenciasMetas(context) }; val metaVals = remember { prefs.obtenerValores(6500.0, 30) }
    var meta by remember { mutableStateOf(metaVals.first) }; var cliTarget by remember { mutableStateOf(metaVals.second) }
    
    // 🔥 USAMOS VALORES DEL VIEWMODEL PARA EVITAR PARPADEOS
    val totalPeriodo = uiState.totalVentaPeriodo
    val visitasPeriodo = uiState.visitasCount
    
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = if (uiState.fechaInicio > 0) uiState.fechaInicio else System.currentTimeMillis(),
            initialSelectedEndDateMillis = if (uiState.fechaFin > 0) uiState.fechaFin else System.currentTimeMillis()
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null) {
                            val c = Calendar.getInstance(); c.timeInMillis = start; c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); val i = c.timeInMillis
                            val f = if (end != null) { val ce = Calendar.getInstance(); ce.timeInMillis = end; ce.set(Calendar.HOUR_OF_DAY, 23); ce.set(Calendar.MINUTE, 59); ce.timeInMillis } else { i + 86399999L }
                            onDateRangeSelected(i, f)
                        }
                        showDatePicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
                ) {
                    Text("ACEPTAR", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                showModeToggle = false,
                modifier = Modifier.weight(1f),
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    headlineContentColor = DelisaRed,
                    selectedDayContainerColor = DelisaRed,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = DelisaRed,
                    todayContentColor = DelisaRed,
                    dayInSelectionRangeContainerColor = DelisaRed.copy(alpha = 0.15f),
                    dayInSelectionRangeContentColor = DelisaRed
                )
            )
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(10.dp))
            
            if (uiState.mostrarFiltrosAdmin && uiState.rutasDisponibles.isNotEmpty()) {
                val indexSeleccionado = uiState.rutasDisponibles.indexOf(uiState.filtroRutaAdmin ?: "TODAS").coerceAtLeast(0)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                    ) {
                        val totalRutas = uiState.rutasDisponibles.size
                        if (totalRutas > 0) {
                            val tabWidth = maxWidth / totalRutas
                            
                            val indicatorOffset by animateDpAsState(
                                targetValue = tabWidth * indexSeleccionado,
                                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioLowBouncy),
                                label = "routeIndicator"
                            )

                            Box(
                                modifier = Modifier
                                    .offset(x = indicatorOffset)
                                    .width(tabWidth)
                                    .fillMaxHeight()
                                    .padding(3.dp)
                                    .shadow(3.dp, RoundedCornerShape(11.dp))
                                    .background(DelisaRed, RoundedCornerShape(11.dp))
                            )

                            Row(modifier = Modifier.fillMaxSize()) {
                                uiState.rutasDisponibles.forEachIndexed { index, ruta ->
                                    val isSelected = index == indexSeleccionado
                                    val textColor by animateColorAsState(
                                        targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        label = "routeTextColor"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { onFiltroRutaChanged(ruta) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ruta.uppercase(),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                            color = textColor,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            MedidorDeMetaPremium(
                metaDelDia = meta * uiState.numDiasFiltro, 
                totalClientes = cliTarget * uiState.numDiasFiltro, 
                clientesVisitados = visitasPeriodo, 
                avance = totalPeriodo, 
                numDias = uiState.numDiasFiltro,
                isLoading = uiState.cargandoDashboard,
                onUpdateMeta = { meta = it; prefs.guardarValores(meta, cliTarget) }, 
                onUpdateClientes = { cliTarget = it; prefs.guardarValores(meta, cliTarget) },
                onCalendarClick = { showDatePicker = true }
            )
            Spacer(Modifier.height(16.dp))
            
            if (uiState.cargandoDashboard) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = DelisaRed) }
            } else if (ticketsHoy.isEmpty()) { 
                Box(Modifier.fillMaxSize(), Alignment.Center) { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ReceiptLong, null, tint = MaterialTheme.colorScheme.outline.copy(0.3f), modifier = Modifier.size(64.dp))
                        Text("Sin ventas registradas hoy", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                } 
            } else { 
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) { items(ticketsHoy, key = { it.id }) { t -> CardTicketRuta(t, fmtFecha, fmtMoneda, onTicketClick) } } 
            }
        }
        
        // 🔥 Solo mostrar resumen si no estamos procesando para evitar el flash de ceros
        if (!uiState.cargandoDashboard) {
            ResumenVentasCard(visitasPeriodo, totalPeriodo, fmtMoneda)
        }
    }
}

@Composable
fun CardTicketRuta(ticket: TicketVenta, fmtFecha: SimpleDateFormat, fmtMoneda: NumberFormat, onClick: (TicketVenta) -> Unit) {
    val esCancelada = ticket.estado == "CANCELADA"
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f, 
        animationSpec = spring(dampingRatio = 0.5f),
        label = "cardScale"
    )
    
    Card(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (isPressed) 2.dp else 4.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, color = DelisaRed.copy(alpha = 0.12f)),
                onClick = { onClick(ticket) }
            ), 
        shape = RoundedCornerShape(24.dp), 
        colors = CardDefaults.cardColors(
            containerColor = if (esCancelada) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ticket.fotoCliente, 
                contentDescription = null, 
                placeholder = painterResource(R.drawable.repartidor), 
                error = painterResource(R.drawable.repartidor), 
                contentScale = ContentScale.Crop, 
                modifier = Modifier
                    .size(65.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .then(if (esCancelada) Modifier.graphicsLayer(alpha = 0.5f) else Modifier)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("FOLIO #${ticket.id.takeLast(6).uppercase()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = ticket.cliente, 
                    fontWeight = FontWeight.Black, 
                    fontSize = 17.sp, 
                    color = if (esCancelada) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, 
                    maxLines = 1,
                    style = if (esCancelada) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                )
                Text(fmtFecha.format(ticket.fecha), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = fmtMoneda.format(ticket.total), 
                    color = if (esCancelada) MaterialTheme.colorScheme.onSurfaceVariant else DelisaRed, 
                    fontWeight = FontWeight.Black, 
                    fontSize = 18.sp,
                    style = if (esCancelada) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                )
                
                val colorContenedor = when {
                    ticket.sincronizado -> DelisaGreen.copy(alpha = 0.1f)
                    ticket.intentosSync > 0 -> WarningOrange.copy(alpha = 0.1f)
                    else -> DelisaRed.copy(alpha = 0.1f)
                }
                val colorTexto = when {
                    ticket.sincronizado -> DelisaGreenDark
                    ticket.intentosSync > 0 -> WarningOrange
                    else -> DelisaRed
                }
                val textoEstado = when {
                    ticket.sincronizado -> "SINCRONIZADO"
                    ticket.intentosSync > 0 -> "REINTENTANDO"
                    else -> "PENDIENTE"
                }

                Surface(
                    shape = RoundedCornerShape(8.dp), 
                    color = colorContenedor,
                    modifier = Modifier.width(90.dp)
                ) { 
                    Text(
                        text = textoEstado, 
                        modifier = Modifier.padding(vertical = 2.dp).fillMaxWidth(), 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Black, 
                        color = colorTexto,
                        textAlign = TextAlign.Center
                    ) 
                }
                
                if (!ticket.sincronizado && ticket.ultimoError != null) {
                    Text(
                        text = ticket.ultimoError.take(15) + "...",
                        fontSize = 8.sp,
                        color = WarningOrange,
                        maxLines = 1,
                        textAlign = TextAlign.End
                    )
                }
                if (esCancelada) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = DelisaRed, 
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.width(90.dp)
                    ) {
                        Text(
                            text = "ANULADA", 
                            color = Color.White, 
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold, 
                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResumenVentasCard(count: Int, total: Double, fmt: NumberFormat) {
    Card(
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), 
        modifier = Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(24.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column { Text("PRODUCTIVIDAD", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold); Text("$count Visitas", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface) }
            Column(horizontalAlignment = Alignment.End) { Text("RECAUDADO", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold); Text(fmt.format(total), fontWeight = FontWeight.Black, color = DelisaRed, fontSize = 24.sp) }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PaginaRutaPreview() {
    val tickets = listOf(TicketVenta("1", "Abarrotes Don Pepe", 1250.0, Date(), true), TicketVenta("2", "Mini Super El Sol", 450.0, Date(), false))
    DeliveryTheme { PaginaVentaContent(tickets, uiState = VentaUiState(), onTicketClick = {}, onFiltroRutaChanged = {}, onDateRangeSelected = {_,_ ->}) }
}
