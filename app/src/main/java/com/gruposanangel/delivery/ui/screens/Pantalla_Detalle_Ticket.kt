package com.gruposanangel.delivery.ui.screens

import ProductoTicketDetalle
import TicketVentaCompleto
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import com.gruposanangel.delivery.utilidades.ImprimirTicket58mmCompleto
import kotlinx.coroutines.*
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DetalleTicketScreen(
    navController: NavController? = null,
    ticketId: String,
    ventaRepository: VentaRepository,
    impresoraBluetooth: BluetoothDevice? = null
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val db = AppDatabase.getDatabase(context)
    val viewModel: VentaViewModel = viewModel(factory = VentaViewModelFactory(RepositoryInventario(FirebaseDataSource(), db.productoDao(), db.VentaDao()), ventaRepository, RepositoryUsuario(FirebaseDataSource(), db.usuarioDao())))
    val scope = rememberCoroutineScope()
    var ticketState by remember { mutableStateOf<TicketVentaCompleto?>(null) }
    var isLoading by remember { mutableStateOf(!isPreview) }

    LaunchedEffect(ticketId) {
        if (!isPreview) {
            isLoading = true
            ticketState = viewModel.obtenerTicketCompleto(ticketId)
            isLoading = false
        }
    }

    DetalleTicketContent(
        ticket = ticketState,
        isLoading = isLoading,
        onBack = {
            navController?.navigate("delivery?screen=  Ruta  ") { launchSingleTop = true; popUpTo(0) { inclusive = true } }
        },
        onImprimir = {
            if (impresoraBluetooth != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val ventaEntity = viewModel.obtenerVentaPorId(ticketId)
                        val detalles = viewModel.obtenerDetallesDeVenta(ticketId)
                        val productosParaImprimir = detalles.map { d -> Plantilla_Producto(d.productoId, d.nombre, d.precio, d.cantidad) }
                        val usuario = db.usuarioDao().obtenerPorId(ventaEntity?.vendedorId ?: "")
                        ImprimirTicket58mmCompleto(device = impresoraBluetooth, context = context, logoDrawableId = R.drawable.logo, cliente = ventaEntity?.clienteNombre ?: ticketState?.cliente ?: "", productos = productosParaImprimir, ventaId = ventaEntity?.id, fechaVenta = ventaEntity?.fecha?.let { Date(it) } ?: ticketState?.fecha ?: Date(), totalVenta = ventaEntity?.total ?: ticketState?.total ?: 0.0, vendedorNombre = usuario?.nombre ?: "Vendedor", metodoPago = ventaEntity?.metodoPago ?: "", )
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Ticket impreso correctamente", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show() } }
                }
            } else { Toast.makeText(context, "No hay impresora", Toast.LENGTH_SHORT).show() }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleTicketContent(
    ticket: TicketVentaCompleto?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onImprimir: () -> Unit
) {
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val formatoFecha = SimpleDateFormat("EEEE d 'de' MMMM hh:mm a", Locale("es", "MX"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle Ticket #${ticket?.numeroTicket?.takeLast(6)?.uppercase() ?: ""}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.AltRoute, null, tint = Color.Red) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(Color.White).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isLoading) { CircularProgressIndicator(color = Color.Red) }
            else if (ticket == null) { Text("No se pudo cargar el ticket") }
            else {
                AsyncImage(model = ticket.fotoCliente, contentDescription = null, contentScale = ContentScale.Crop, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), modifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.height(16.dp)); Text(ticket.cliente, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Ticket #${ticket.numeroTicket.takeLast(6).uppercase()}", color = Color.Gray, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp)); Text("Fecha: ${formatoFecha.format(ticket.fecha)}", fontSize = 14.sp); Text("Total: ${formatoMoneda.format(ticket.total)}", fontSize = 20.sp, color = Color.Red, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp)); Text(if (ticket.sincronizado) "SINCRONIZADO" else "PENDIENTE", color = if (ticket.sincronizado) Color(0xFF388E3C) else Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp)); Text("Productos:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Column(Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp)).padding(12.dp)) {
                    ticket.productos.forEach { p -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("${p.nombre} x${p.cantidad}"); Text(formatoMoneda.format(p.precio * p.cantidad)) } }
                }
                Spacer(Modifier.height(30.dp)); Button(onClick = onImprimir, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp)) { Text("Imprimir Ticket", fontSize = 16.sp, color = Color.White) }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Detalle Ticket - Datos")
@Composable
fun DetalleTicketPreview() {
    val dummy = TicketVentaCompleto("4810-ABC", "Abarrotes Doña Mary", 345.50, Date(), true, "", listOf(ProductoTicketDetalle("Papas Delisa", 2, 15.0), ProductoTicketDetalle("Gomitas Mix", 1, 10.0)))
    DeliveryTheme { DetalleTicketContent(dummy, false, {}, {}) }
}

@Preview(showBackground = true, showSystemUi = true, name = "Detalle Ticket - Cargando")
@Composable
fun DetalleTicketLoadingPreview() {
    DeliveryTheme { DetalleTicketContent(null, true, {}, {}) }
}
