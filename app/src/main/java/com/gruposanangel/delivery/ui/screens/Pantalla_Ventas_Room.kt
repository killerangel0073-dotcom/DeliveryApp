package com.gruposanangel.delivery.ui.screens

import android.content.Context
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.gruposanangel.delivery.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.util.Log
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.isSystemInDarkTheme

data class VentaConFotoUI(val venta: VentaEntity, val fotoCliente: String)
data class VentasRoomUiState(val isLoading: Boolean = false, val ventasConFoto: List<VentaConFotoUI> = emptyList())

class VentasRoomViewModel(private val ventaRepository: VentaRepository, context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(VentasRoomUiState())
    val uiState: StateFlow<VentasRoomUiState> = _uiState.asStateFlow()
    private val db = AppDatabase.getDatabase(context); private val clienteDao = db.clienteDao()
    private val usuarioDao = db.usuarioDao()
    
    private val _isAdminOverride = MutableStateFlow<Boolean?>(null)

    fun configurarModo(esAdmin: Boolean) {
        _isAdminOverride.value = esAdmin
    }

    fun cargarVentas(inicio: Long, fin: Long, clienteIdFiltro: String? = null) {
        viewModelScope.launch {
            val usuario = usuarioDao.obtenerUsuarioActual()
            val uid = usuario?.uid ?: ""
            val almid = usuario?.ultimoAlmacenNombre ?: ""
            val rNom = usuario?.ultimaRutaNombre ?: ""
            val rId = usuario?.ultimaRutaId ?: ""
            val puesto = usuario?.puestoTrabajo?.trim() ?: ""
            
            val esAdminReal = puesto.contains("CEO", true) || puesto.contains("Gerente", true) || puesto.contains("Supervisor", true)
            val esVendedor = (puesto.contains("Vendedor", ignoreCase = true) || puesto.contains("Suplente", ignoreCase = true)) || 
                             (_isAdminOverride.value == false && esAdminReal)
            
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // 🔥 MEJORA: Si filtramos por cliente, disparamos descarga de 90 días en segundo plano
            if (clienteIdFiltro != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        ventaRepository.descargarHistorialCliente90Dias(clienteIdFiltro)
                        // Una vez descargado, refrescamos la lista local
                        val ventasActualizadas = ventaRepository.obtenerTodoHistorialCliente(clienteIdFiltro)
                        val listaActualizada = ventasActualizadas.map { VentaConFotoUI(it, clienteDao.getClientePorId(it.clienteId)?.fotografiaUrl ?: "") }
                        _uiState.value = VentasRoomUiState(isLoading = false, ventasConFoto = listaActualizada)
                    } catch (e: Exception) {
                        Log.e("VentasRoomVM", "Error descargando historial cloud", e)
                    }
                }
            }

            val ventasRaw = if (clienteIdFiltro != null) {
                // Obtenemos todo lo que hay en local para este cliente
                ventaRepository.obtenerTodoHistorialCliente(clienteIdFiltro)
            } else {
                if (esVendedor && (almid.isNotEmpty() || rNom.isNotEmpty())) {
                    ventaRepository.obtenerVentasPorUnidadPeriodo(almid, rNom, rId, inicio, fin)
                } else {
                    ventaRepository.obtenerVentasPorPeriodo(if(esAdminReal && _isAdminOverride.value != false) "" else uid, inicio, fin)
                }
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
    clienteId: String? = null,
    isAdminOverride: Boolean? = null
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
        
        LaunchedEffect(isAdminOverride) {
            if (isAdminOverride != null) {
                vm.configurarModo(isAdminOverride)
            }
        }
        
        LaunchedEffect(fechaSeleccionada, clienteId, isAdminOverride) {
            cal.time = fechaSeleccionada; cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); val ini = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); val fin = cal.timeInMillis; 
            vm.cargarVentas(ini, fin, clienteId)
        }
        
        val isDark = ThemeConfig.isActuallyDark
        DeliveryTheme(darkTheme = isDark) {
            PantallaVentasRoomContent(
                uiState = uiState, 
                fecha = fechaSeleccionada, 
                onFechaChange = { fechaSeleccionada = it }, 
                onVentaClick = { v -> 
                    navController?.navigate("detalle_venta_admin/${v.id}?mostrarAcciones=false")
                }, 
                onBack = { navController?.popBackStack() },
                isFiltradoPorCliente = clienteId != null
            )
        }
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
    isFiltradoPorCliente: Boolean = false
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
                        Text(if (isFiltradoPorCliente) "HISTORIAL DEL CLIENTE" else "HISTORIAL DE VENTAS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!isFiltradoPorCliente) {
                            Text(fmtFecha.format(fecha).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Black, color = DelisaRed)
                        } else if (uiState.ventasConFoto.isNotEmpty()) {
                            Text(uiState.ventasConFoto.first().venta.clienteNombre.uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Black, color = DelisaRed, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } 
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed) } },
                actions = { 
                    if (!isFiltradoPorCliente) {
                        IconButton(onClick = { cal.time = fecha; cal.add(Calendar.DAY_OF_MONTH, -1); onFechaChange(cal.time) }) { Icon(Icons.Default.ChevronLeft, null, tint = DelisaRed) }
                        IconButton(onClick = { cal.time = fecha; cal.add(Calendar.DAY_OF_MONTH, 1); onFechaChange(cal.time) }) { Icon(Icons.Default.ChevronRight, null, tint = DelisaRed) } 
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            if (uiState.ventasConFoto.isNotEmpty()) {
                val total = uiState.ventasConFoto.filter { it.venta.estado != "CANCELADA" }.sumOf { it.venta.total }
                Card(shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), modifier = Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(24.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column { Text("VENTAS", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold); Text("${uiState.ventasConFoto.count { it.venta.estado != "CANCELADA" }} Tickets", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface) }
                        Column(horizontalAlignment = Alignment.End) { Text("TOTAL RECAUDADO", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold); Text(fmtMoneda.format(total), fontWeight = FontWeight.Black, color = DelisaRed, fontSize = 24.sp) }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (uiState.isLoading) { Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator(color = DelisaRed) } }
        else if (uiState.ventasConFoto.isEmpty()) { 
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { 
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Text("Sin historial disponible", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium) 
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
                            .shadow(if (isPressed) 1.dp else 2.dp, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp)) 
                            .clickable(
                                interactionSource = interactionSource,
                                indication = rememberRipple(bounded = true, color = DelisaRed.copy(alpha = 0.12f)),
                                onClick = { onVentaClick(item.venta) }
                            ), 
                        shape = RoundedCornerShape(24.dp), 
                        colors = CardDefaults.cardColors(
                            containerColor = if (esCancelada) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        )
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
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .then(if (esCancelada) Modifier.graphicsLayer(alpha = 0.5f) else Modifier), 
                                    contentScale = ContentScale.Crop
                                )
                                if (esMismoDia && !esCancelada) {
                                    Surface(color = Color(0xFF4CAF50), shape = CircleShape, modifier = Modifier.size(12.dp).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)) {}
                                }
                            }
                            
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (isFiltradoPorCliente) SimpleDateFormat("dd 'de' MMMM", Locale("es", "MX")).format(fechaVenta) else item.venta.clienteNombre, 
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 15.sp, 
                                    color = if (esCancelada) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, 
                                    maxLines = 1, 
                                    overflow = TextOverflow.Ellipsis,
                                    style = if (esCancelada) MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough) else MaterialTheme.typography.bodyMedium
                                )
                                Text(fmtHora.format(fechaVenta), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = fmtMoneda.format(item.venta.total), 
                                    color = if (esCancelada) MaterialTheme.colorScheme.onSurfaceVariant else if (item.venta.total > 0) DelisaRed else MaterialTheme.colorScheme.onSurfaceVariant, 
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 17.sp,
                                    style = if (esCancelada) androidx.compose.ui.text.TextStyle(textDecoration = TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                                )
                                if (item.venta.total == 0.0) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(item.venta.motivoVisita ?: "VISITA", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp), 
                                        color = if (item.venta.sincronizado) DelisaGreen.copy(alpha = 0.1f) else DelisaRed.copy(alpha = 0.1f)
                                    ) { 
                                        Text(
                                            text = if (item.venta.sincronizado) "SINCRONIZADA" else "PENDIENTE", 
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), 
                                            fontSize = 8.sp, 
                                            fontWeight = FontWeight.Black, 
                                            color = if (item.venta.sincronizado) DelisaGreenDark else DelisaRed
                                        ) 
                                    }
                                }
                                if (esCancelada) {
                                    Spacer(Modifier.height(4.dp))
                                    Surface(color = DelisaRed, shape = RoundedCornerShape(8.dp)) {
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
    DeliveryTheme(darkTheme = false) {
        PantallaVentasRoomContent(VentasRoomUiState(ventasConFoto = items), Date(), {}, {}, {}) 
    }
}
