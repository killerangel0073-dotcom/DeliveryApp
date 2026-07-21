package com.gruposanangel.delivery.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class VentaConFotoUI(val venta: VentaEntity, val fotoCliente: String)
data class VentasRoomUiState(val isLoading: Boolean = false, val ventasConFoto: List<VentaConFotoUI> = emptyList())

class VentasRoomViewModel(private val ventaRepository: VentaRepository, context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(VentasRoomUiState())
    val uiState: StateFlow<VentasRoomUiState> = _uiState.asStateFlow()
    private val db = AppDatabase.getDatabase(context); private val clienteDao = db.clienteDao()
    private val usuarioDao = db.usuarioDao()

    fun cargarVentas(inicio: Long, fin: Long, clienteIdFiltro: String? = null) {
        viewModelScope.launch {
            val usuario = usuarioDao.obtenerUsuarioActual()
            val uid = usuario?.uid ?: ""
            val puesto = usuario?.puestoTrabajo?.trim() ?: ""
            val idParaQuery = if (puesto == "Vendedor de Ruta" || puesto == "Suplente de Ruta") uid else ""
            
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // 🔥 Sincronizamos con el servidor solo si no hay filtro de cliente o para el cliente específico
            if (clienteIdFiltro == null) {
                ventaRepository.sincronizarVentasPeriodo(idParaQuery, inicio, fin)
            }
            
            val ventasRaw = if (clienteIdFiltro != null) {
                // Si hay filtro de cliente, traemos sus ventas sin importar el rango de fecha (historial completo local)
                ventaRepository.obtenerVentasPorPeriodo(idParaQuery, 0, System.currentTimeMillis())
                    .filter { it.clienteId == clienteIdFiltro }
            } else {
                ventaRepository.obtenerVentasPorPeriodo(idParaQuery, inicio, fin)
            }

            val lista = ventasRaw.map { VentaConFotoUI(it, clienteDao.getClientePorId(it.clienteId)?.fotografiaUrl ?: "") }

            _uiState.value = VentasRoomUiState(isLoading = false, ventasConFoto = lista)
        }
    }
}

class VentasRoomViewModelFactory(private val repo: VentaRepository, private val context: Context) : ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = VentasRoomViewModel(repo, context) as T }

@Composable
fun VentasRoomScreen(
    context: Context, 
    navController: NavController? = null, 
    ventaRepository: VentaRepository,
    clienteId: String? = null // 🔥 Recibir ID opcional
) {
    val isPreview = LocalInspectionMode.current
    if (isPreview) {
        val dummyVenta = VentaEntity(
            id = "1", clienteId = "c1", clienteNombre = "Tienda Mary", clienteImagenUrl = null,
            total = 150.0, metodoPago = "Efectivo", vendedorId = "v1", vendedorNombre = null,
            almacenId = null, fecha = System.currentTimeMillis(), horaDispositivo = System.currentTimeMillis(),
            horaVerificada = System.currentTimeMillis(), alertaTiempo = false,
            latitudVenta = 0.0, longitudVenta = 0.0, fueraDeRango = false, fotoEvidenciaVisita = null,
            sincronizado = true, firestoreId = null
        )
        PantallaVentasRoomContent(VentasRoomUiState(ventasConFoto = listOf(VentaConFotoUI(dummyVenta, ""))), Date(), {}, {}, {}, clienteId != null)
    } else {
        val vm: VentasRoomViewModel = viewModel(factory = VentasRoomViewModelFactory(ventaRepository, context))
        val uiState by vm.uiState.collectAsState()
        var fechaSeleccionada by remember { mutableStateOf(Date()) }
        val cal = Calendar.getInstance()
        
        LaunchedEffect(fechaSeleccionada, clienteId) {
            cal.time = fechaSeleccionada; cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); val ini = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); val fin = cal.timeInMillis; 
            vm.cargarVentas(ini, fin, clienteId)
        }
        
        PantallaVentasRoomContent(
            uiState = uiState, 
            fecha = fechaSeleccionada, 
            onFechaChange = { fechaSeleccionada = it }, 
            onVentaClick = { v -> 
                // Navegar al detalle de venta SIN los botones de acción
                navController?.navigate("detalle_venta_admin/${v.id}?mostrarAcciones=false")
            }, 
            onBack = { navController?.popBackStack() },
            isFiltradoPorCliente = clienteId != null
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaVentasRoomContent(
    uiState: VentasRoomUiState, 
    fecha: Date, 
    onFechaChange: (Date) -> Unit, 
    onVentaClick: (VentaEntity) -> Unit, 
    onBack: () -> Unit,
    isFiltradoPorCliente: Boolean = false // 🔥 Nuevo
) {
    val fmtMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    val fmtFecha = SimpleDateFormat("EEEE d 'de' MMMM", Locale.forLanguageTag("es-MX"))
    val fmtHora = SimpleDateFormat("hh:mm a", Locale.forLanguageTag("es-MX"))
    val cal = Calendar.getInstance()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column { 
                        Text(if (isFiltradoPorCliente) "HISTORIAL DEL CLIENTE" else "HISTORIAL DE VENTAS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray)
                        if (!isFiltradoPorCliente) {
                            Text(fmtFecha.format(fecha).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.Red)
                        } else if (uiState.ventasConFoto.isNotEmpty()) {
                            Text(uiState.ventasConFoto.first().venta.clienteNombre.uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.Red, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } 
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Red) } },
                actions = { 
                    if (!isFiltradoPorCliente) {
                        IconButton(onClick = { cal.time = fecha; cal.add(Calendar.DAY_OF_MONTH, -1); onFechaChange(cal.time) }) { Icon(Icons.Default.ChevronLeft, null, tint = Color.Red) }
                        IconButton(onClick = { cal.time = fecha; cal.add(Calendar.DAY_OF_MONTH, 1); onFechaChange(cal.time) }) { Icon(Icons.Default.ChevronRight, null, tint = Color.Red) } 
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            if (uiState.ventasConFoto.isNotEmpty()) {
                val total = uiState.ventasConFoto.sumOf { it.venta.total }
                Card(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(24.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column { Text("TOTAL VENTAS", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold); Text("${uiState.ventasConFoto.size} Tickets", fontWeight = FontWeight.Black, fontSize = 20.sp) }
                        Column(horizontalAlignment = Alignment.End) { Text("RECAUDADO", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold); Text(fmtMoneda.format(total), fontWeight = FontWeight.Black, color = Color.Red, fontSize = 24.sp) }
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) { Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator(color = Color.Red) } }
        else if (uiState.ventasConFoto.isEmpty()) { 
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { 
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.History, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                    Text("Sin historial disponible", color = Color.Gray, fontWeight = FontWeight.Medium) 
                }
            } 
        }
        else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.ventasConFoto.sortedByDescending { it.venta.fecha }) { item ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1f, label = "cardScale")
                    val esCancelada = item.venta.estado == "CANCELADA"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(RoundedCornerShape(24.dp)) 
                            .clickable(
                                interactionSource = interactionSource,
                                indication = rememberRipple(bounded = true, color = Color.Red.copy(alpha = 0.12f)),
                                onClick = { onVentaClick(item.venta) }
                            ), 
                        shape = RoundedCornerShape(24.dp), 
                        colors = CardDefaults.cardColors(
                            containerColor = if (esCancelada) Color(0xFFEEEEEE) else Color.White
                        ), 
                        elevation = CardDefaults.cardElevation(if (isPressed) 1.dp else 2.dp)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            val fechaVenta = Date(item.venta.fecha)
                            val esMismoDia = SimpleDateFormat("ddMM", Locale.US).format(fechaVenta) == SimpleDateFormat("ddMM", Locale.US).format(Date())
                            
                            Box(contentAlignment = Alignment.BottomEnd) {
                                AsyncImage(
                                    model = item.fotoCliente, 
                                    placeholder = painterResource(R.drawable.repartidor), 
                                    error = painterResource(R.drawable.repartidor), 
                                    contentDescription = null, 
                                    modifier = Modifier
                                        .size(65.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFFF1F2F6))
                                        .then(if (esCancelada) Modifier.graphicsLayer(alpha = 0.5f) else Modifier), 
                                    contentScale = ContentScale.Crop
                                )
                                if (esMismoDia && !esCancelada) {
                                    Surface(color = Color(0xFF4CAF50), shape = CircleShape, modifier = Modifier.size(12.dp).border(2.dp, Color.White, CircleShape)) {}
                                }
                            }
                            
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (isFiltradoPorCliente) SimpleDateFormat("dd 'de' MMMM", Locale.forLanguageTag("es-MX")).format(fechaVenta) else item.venta.clienteNombre, 
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 17.sp, 
                                    color = if (esCancelada) Color.Gray else Color.Black, 
                                    maxLines = 1, 
                                    overflow = TextOverflow.Ellipsis,
                                    style = if (esCancelada) androidx.compose.ui.text.TextStyle(textDecoration = TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                                )
                                Text(fmtHora.format(fechaVenta), fontSize = 12.sp, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = fmtMoneda.format(item.venta.total), 
                                    color = if (esCancelada) Color.Gray else if (item.venta.total > 0) Color.Red else Color.Gray, 
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 18.sp,
                                    style = if (esCancelada) androidx.compose.ui.text.TextStyle(textDecoration = TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                                )
                                if (item.venta.total == 0.0) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFF1F2F6)) {
                                        Text(item.venta.motivoVisita ?: "VISITA", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp), 
                                        color = if (item.venta.sincronizado) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                    ) { 
                                        Text(
                                            text = if (item.venta.sincronizado) "SINCRONIZADA" else "PENDIENTE DE ENVÍO", 
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), 
                                            fontSize = 8.sp, 
                                            fontWeight = FontWeight.Black, 
                                            color = if (item.venta.sincronizado) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        ) 
                                    }
                                }
                                if (esCancelada) {
                                    Spacer(Modifier.height(4.dp))
                                    Surface(color = Color.Red, shape = RoundedCornerShape(8.dp)) {
                                        Text("ANULADA", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Ventas Historial - Lista")
@Composable
fun VentasRoomPreview() {
    val dummyVenta = VentaEntity(
        id = "1", clienteId = "c1", clienteNombre = "Abarrotes Doña Mary", clienteImagenUrl = null,
        total = 450.0, metodoPago = "Efectivo", vendedorId = "v1", vendedorNombre = null,
        almacenId = null, fecha = System.currentTimeMillis(), horaDispositivo = System.currentTimeMillis(),
        horaVerificada = System.currentTimeMillis(), alertaTiempo = false,
        latitudVenta = 0.0, longitudVenta = 0.0, fueraDeRango = false, fotoEvidenciaVisita = null,
        sincronizado = true, firestoreId = null
    )
    val items = listOf(VentaConFotoUI(dummyVenta, ""))
    DeliveryTheme { PantallaVentasRoomContent(VentasRoomUiState(ventasConFoto = items), Date(), {}, {}, {}) }
}
