package com.gruposanangel.delivery.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.data.VentaRepository
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// --- MODELO UI PARA INCLUIR LA FOTO ---
data class VentaConFotoUI(
    val venta: VentaEntity,
    val fotoCliente: String
)

// --- VIEW MODEL ACTUALIZADO ---
class VentasRoomViewModel(
    private val ventaRepository: VentaRepository,
    private val context: Context
) : ViewModel() {
    private val _ventasConFoto = MutableStateFlow<List<VentaConFotoUI>>(emptyList())
    val ventasConFoto: StateFlow<List<VentaConFotoUI>> = _ventasConFoto.asStateFlow()

    private val db = AppDatabase.getDatabase(context)
    private val clienteDao = db.clienteDao()

    fun cargarVentas(inicio: Long, fin: Long) {
        viewModelScope.launch {
            val listaVentas = ventaRepository.obtenerVentasPorPeriodo(inicio, fin)
            // Mapeamos cada venta para buscar la foto del cliente en el DAO
            val listaConFotos = listaVentas.map { venta ->
                val cliente = clienteDao.getClientePorId(venta.clienteId)
                VentaConFotoUI(
                    venta = venta,
                    fotoCliente = cliente?.fotografiaUrl ?: ""
                )
            }
            _ventasConFoto.value = listaConFotos
        }
    }

    suspend fun obtenerProductosVenta(ventaId: String): List<Plantilla_Producto> {
        val detalles = ventaRepository.obtenerDetallesDeVenta(ventaId)
        return detalles.map {
            Plantilla_Producto(id = it.productoId, nombre = it.nombre, precio = it.precio, cantidad = it.cantidad)
        }
    }
}

class VentasRoomViewModelFactory(private val ventaRepository: VentaRepository, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VentasRoomViewModel(ventaRepository, context) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentasRoomScreen(
    context: Context,
    navController: NavController? = null,
    ventaRepository: VentaRepository
) {
    val viewModel: VentasRoomViewModel = viewModel(factory = VentasRoomViewModelFactory(ventaRepository, context))
    val ventasConFoto by viewModel.ventasConFoto.collectAsState()

    val formatoMoneda = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val formatoFechaHora = remember { SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale("es", "MX")) }
    val formatoFechaHeader = remember { SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "MX")) }

    var fechaSeleccionada by remember { mutableStateOf(Date()) }
    var ventaSeleccionada by remember { mutableStateOf<VentaEntity?>(null) }
    var productosVenta by remember { mutableStateOf<List<Plantilla_Producto>>(emptyList()) }
    var mostrarDialog by remember { mutableStateOf(false) }
    var mostrarDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = fechaSeleccionada.time)

    val cal = Calendar.getInstance()

    fun cargarVentasDia() {
        cal.time = fechaSeleccionada
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val inicio = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
        val fin = cal.timeInMillis
        viewModel.cargarVentas(inicio, fin)
    }

    LaunchedEffect(fechaSeleccionada) { cargarVentasDia() }

    LaunchedEffect(ventaSeleccionada) {
        ventaSeleccionada?.let { venta ->
            productosVenta = withContext(Dispatchers.IO) { viewModel.obtenerProductosVenta(venta.id) }
            mostrarDialog = true
        }
    }

    Scaffold(
        bottomBar = {
            if (ventasConFoto.isNotEmpty()) {
                val totalDinero = ventasConFoto.sumOf { it.venta.total }
                ResumenVentasInferior(ventasConFoto.size, totalDinero, formatoMoneda)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8F9FA))
        ) {
            // HEADER SELECTOR FECHA
            Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        cal.time = fechaSeleccionada
                        cal.add(Calendar.DAY_OF_MONTH, -1)
                        fechaSeleccionada = cal.time
                    }) {
                        Icon(painterResource(id = android.R.drawable.ic_media_previous), contentDescription = null, tint = Color.Red)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { mostrarDatePicker = true }
                    ) {
                        Text(
                            text = formatoFechaHeader.format(fechaSeleccionada).replaceFirstChar { it.uppercase() },
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF333333)
                        )
                        Text("Toca para cambiar fecha", fontSize = 10.sp, color = Color.Gray)
                    }

                    IconButton(onClick = {
                        cal.time = fechaSeleccionada
                        cal.add(Calendar.DAY_OF_MONTH, 1)
                        fechaSeleccionada = cal.time
                    }) {
                        Icon(painterResource(id = android.R.drawable.ic_media_next), contentDescription = null, tint = Color.Red)
                    }
                }
            }

            if (ventasConFoto.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay ventas para este día", color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ventasConFoto) { item ->
                        CardTicketVentaItem(item, formatoFechaHora, formatoMoneda) {
                            ventaSeleccionada = item.venta
                        }
                    }
                }
            }
        }
    }

    // DIALOGOS
    if (mostrarDatePicker) {
        DatePickerDialog(
            onDismissRequest = { mostrarDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { fechaSeleccionada = Date(it) }
                    mostrarDatePicker = false
                }) { Text("ACEPTAR", color = Color.Red, fontWeight = FontWeight.Bold) }
            }
        ) { DatePicker(state = datePickerState) }
    }

    if (mostrarDialog) {
        AlertDialog(
            onDismissRequest = { mostrarDialog = false },
            confirmButton = {
                TextButton(onClick = { mostrarDialog = false }) { Text("CERRAR", color = Color.Red) }
            },
            title = { Text("Ticket #${ventaSeleccionada?.id}") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(productosVenta) { prod ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                            Text("${prod.cantidad}x ${prod.nombre}", Modifier.weight(1f), fontSize = 14.sp)
                            Text(formatoMoneda.format(prod.precio * prod.cantidad), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun CardTicketVentaItem(
    item: VentaConFotoUI,
    formatoFecha: SimpleDateFormat,
    formatoMoneda: NumberFormat,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

            // --- LÓGICA DE IMAGEN DEL EJEMPLO ---
            val imageModel = remember(item.fotoCliente) {
                if (item.fotoCliente.isNotBlank()) {
                    val file = File(item.fotoCliente)
                    if (file.exists()) Uri.fromFile(file) else item.fotoCliente
                } else null
            }

            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.repartidor),
                error = painterResource(R.drawable.repartidor),
                modifier = Modifier.size(65.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F2F6))
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text("Ticket #${item.venta.id}", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(item.venta.clienteNombre, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color(0xFF2D3436), maxLines = 1)
                Text(formatoFecha.format(Date(item.venta.fecha)), fontSize = 11.sp, color = Color.Gray)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(formatoMoneda.format(item.venta.total), color = Color(0xFFE30613), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = (if (item.venta.sincronizado) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                ) {
                    Text(
                        text = if (item.venta.sincronizado) "SINCRONIZADO" else "PENDIENTE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = if (item.venta.sincronizado) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }
        }
    }
}

@Composable
fun ResumenVentasInferior(totalTickets: Int, totalDinero: Double, formatoMoneda: NumberFormat) {
    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Ventas realizadas", fontSize = 12.sp, color = Color.Gray)
                Text("$totalTickets Tickets", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Monto Total", fontSize = 12.sp, color = Color.Gray)
                Text(formatoMoneda.format(totalDinero), fontWeight = FontWeight.Black, color = Color.Red, fontSize = 20.sp)
            }
        }
    }
}