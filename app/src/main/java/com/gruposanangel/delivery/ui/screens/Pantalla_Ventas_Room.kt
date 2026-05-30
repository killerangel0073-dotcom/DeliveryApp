package com.gruposanangel.delivery.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    fun cargarVentas(inicio: Long, fin: Long) {
        viewModelScope.launch {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            _uiState.value = _uiState.value.copy(isLoading = true)
            val lista = ventaRepository.obtenerVentasPorPeriodo(uid, inicio, fin).map { VentaConFotoUI(it, clienteDao.getClientePorId(it.clienteId)?.fotografiaUrl ?: "") }
            _uiState.value = VentasRoomUiState(isLoading = false, ventasConFoto = lista)
        }
    }
}

class VentasRoomViewModelFactory(private val repo: VentaRepository, private val context: Context) : ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = VentasRoomViewModel(repo, context) as T }

@Composable
fun VentasRoomScreen(context: Context, navController: NavController? = null, ventaRepository: VentaRepository) {
    val isPreview = LocalInspectionMode.current
    if (isPreview) {
        PantallaVentasRoomContent(VentasRoomUiState(ventasConFoto = listOf(VentaConFotoUI(VentaEntity("1", "c1", "Tienda Mary", null, 150.0, "Efectivo", "v1", System.currentTimeMillis(), true), ""))), Date(), {}, {}, {})
    } else {
        val vm: VentasRoomViewModel = viewModel(factory = VentasRoomViewModelFactory(ventaRepository, context))
        val uiState by vm.uiState.collectAsState()
        var fechaSeleccionada by remember { mutableStateOf(Date()) }
        val cal = Calendar.getInstance()
        LaunchedEffect(fechaSeleccionada) {
            cal.time = fechaSeleccionada; cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); val ini = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); val fin = cal.timeInMillis; vm.cargarVentas(ini, fin)
        }
        PantallaVentasRoomContent(uiState, fechaSeleccionada, { fechaSeleccionada = it }, { v -> navController?.navigate("detalle_venta_admin/${v.id}") }, { navController?.popBackStack() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaVentasRoomContent(uiState: VentasRoomUiState, fecha: Date, onFechaChange: (Date) -> Unit, onVentaClick: (VentaEntity) -> Unit, onBack: () -> Unit) {
    val fmtMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    val fmtFecha = SimpleDateFormat("EEEE d 'de' MMMM", Locale.forLanguageTag("es-MX"))
    val fmtHora = SimpleDateFormat("hh:mm a", Locale.forLanguageTag("es-MX"))
    val cal = Calendar.getInstance()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("HISTORIAL DE VENTAS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.Gray); Text(fmtFecha.format(fecha).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.Red) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Red) } },
                actions = { IconButton(onClick = { cal.time = fecha; cal.add(Calendar.DAY_OF_MONTH, -1); onFechaChange(cal.time) }) { Icon(Icons.Default.ChevronLeft, null, tint = Color.Red) }; IconButton(onClick = { cal.time = fecha; cal.add(Calendar.DAY_OF_MONTH, 1); onFechaChange(cal.time) }) { Icon(Icons.Default.ChevronRight, null, tint = Color.Red) } },
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
        else if (uiState.ventasConFoto.isEmpty()) { Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("Sin ventas registradas", color = Color.Gray) } }
        else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(uiState.ventasConFoto) { item ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { onVentaClick(item.venta) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = item.fotoCliente, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = null, modifier = Modifier.size(65.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF1F2F6)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.venta.clienteNombre, fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.Black)
                                Text(fmtHora.format(Date(item.venta.fecha)), fontSize = 12.sp, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fmtMoneda.format(item.venta.total), color = Color.Red, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Surface(shape = RoundedCornerShape(8.dp), color = if (item.venta.sincronizado) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)) { Text(if (item.venta.sincronizado) "OK" else "PEND", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (item.venta.sincronizado) Color(0xFF2E7D32) else Color(0xFFC62828)) }
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
    val items = listOf(VentaConFotoUI(VentaEntity("1", "c1", "Abarrotes Doña Mary", null, 450.0, "Efectivo", "v1", System.currentTimeMillis(), true), ""))
    DeliveryTheme { PantallaVentasRoomContent(VentasRoomUiState(ventasConFoto = items), Date(), {}, {}, {}) }
}
