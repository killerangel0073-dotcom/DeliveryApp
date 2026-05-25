package com.gruposanangel.delivery.ui.screens

import ProductoTicketDetalle
import TicketVentaCompleto
import android.bluetooth.BluetoothDevice
import android.net.Uri
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
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.data.VentaRepository
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.utilidades.ImprimirTicket58mmCompleto
import com.gruposanangel.delivery.data.FirebaseDataSource
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
    val db = AppDatabase.getDatabase(context)
    val firebaseDataSource = FirebaseDataSource()
    val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao())
    val repoUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())

    val viewModel: VentaViewModel = viewModel(
        factory = VentaViewModelFactory(
            repositoryInventario = inventarioRepo,
            ventaRepository = ventaRepository,
            repositoryUsuario = repoUsuario
        )
    )

    val ticketCompletoState = remember { mutableStateOf<TicketVentaCompleto?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ticketId) {
        ticketCompletoState.value = viewModel.obtenerTicketCompleto(ticketId)
    }

    ticketCompletoState.value?.let { ticket ->
        PantallaDetalleTicketCompleto(
            navController = navController,
            ticket = ticket,
            impresoraBluetooth = impresoraBluetooth,
            coroutineScope = scope,
            viewModel = viewModel,
            ticketId = ticketId
        )
    } ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Cargando ticket...")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalleTicketCompleto(
    navController: NavController?,
    ticket: TicketVentaCompleto,
    impresoraBluetooth: BluetoothDevice? = null,
    coroutineScope: CoroutineScope,
    viewModel: VentaViewModel? = null,
    ticketId: String? = null
) {
    val context = LocalContext.current
    val clienteDao = AppDatabase.getDatabase(context).clienteDao()

    val fotoClienteReal by produceState<String?>(initialValue = null, ticketId) {
        if (ticketId == null || viewModel == null) {
            value = null
            return@produceState
        }
        val ventaEntity = viewModel.obtenerVentaPorId(ticketId)
        val clienteId = ventaEntity?.clienteId
        value = clienteId?.let {
            clienteDao.getClientePorId(it)?.fotografiaUrl
        }
    }

    val localeMX = Locale("es", "MX")
    val formatoMoneda = NumberFormat.getCurrencyInstance(localeMX)
    val formatoFecha = SimpleDateFormat("EEEE d 'de' MMMM hh:mm a", localeMX)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle Ticket #${ticket.numeroTicket}") },
                navigationIcon = {
                    if (navController != null) {
                        IconButton(onClick = {
                            navController.navigate("delivery?screen=  Ruta  ") {
                                launchSingleTop = true
                                popUpTo(0) { inclusive = true }
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "Ir a Ruta")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val imageModel = remember(fotoClienteReal) {
                fotoClienteReal?.let { path ->
                    val file = File(path)
                    if (file.exists()) Uri.fromFile(file) else path
                }
            }

            AsyncImage(
                model = imageModel,
                contentDescription = ticket.cliente,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.repartidor),
                error = painterResource(R.drawable.repartidor),
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(Modifier.height(16.dp))
            Text(ticket.cliente, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Ticket #${ticket.numeroTicket}", color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Text("Fecha: ${formatoFecha.format(ticket.fecha)}", fontSize = 14.sp)
            Text(
                "Total: ${formatoMoneda.format(ticket.total)}",
                fontSize = 20.sp,
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))
            Text(
                if (ticket.sincronizado) "SINCRONIZADO" else "PENDIENTE",
                color = if (ticket.sincronizado) Color(0xFF388E3C) else Color(0xFFD32F2F),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(20.dp))
            Text("Productos:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                ticket.productos.forEach { producto ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${producto.nombre} x${producto.cantidad}")
                        Text(formatoMoneda.format(producto.precio * producto.cantidad))
                    }
                }
            }

            Spacer(Modifier.height(30.dp))

            Button(
                onClick = {
                    if (impresoraBluetooth != null) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val ventaEntity: VentaEntity? = ticketId?.let { viewModel?.obtenerVentaPorId(it) }
                                val detalles = ticketId?.let { viewModel?.obtenerDetallesDeVenta(it) } ?: emptyList()
                                val productosParaImprimir = detalles.map { d ->
                                    Plantilla_Producto(d.productoId, d.nombre, d.precio, d.cantidad)
                                }

                                val vendedorUid = ventaEntity?.vendedorId ?: ""
                                val usuarioDao = AppDatabase.getDatabase(context).usuarioDao()
                                val usuario = usuarioDao.obtenerPorId(vendedorUid)
                                val nombreVendedor = usuario?.nombre ?: vendedorUid

                                ImprimirTicket58mmCompleto(
                                    device = impresoraBluetooth,
                                    context = context,
                                    logoDrawableId = R.drawable.logo,
                                    cliente = ventaEntity?.clienteNombre ?: ticket.cliente,
                                    productos = productosParaImprimir,
                                    ventaId = ventaEntity?.id,
                                    fechaVenta = ventaEntity?.fecha?.let { Date(it) } ?: ticket.fecha,
                                    totalVenta = ventaEntity?.total ?: ticket.total,
                                    vendedorNombre = nombreVendedor,
                                    metodoPago = ventaEntity?.metodoPago ?: "",
                                    sincronizado = ventaEntity?.sincronizado,
                                    firestoreId = ventaEntity?.firestoreId ?: "",
                                    clienteId = ventaEntity?.clienteId ?: ""
                                )

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Ticket impreso correctamente", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Error imprimiendo: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "No hay impresora seleccionada", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(15.dp)
            ) {
                Text("Imprimir Ticket", fontSize = 16.sp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewPantallaDetalleTicketCompleto() {
    val ticketEjemplo = TicketVentaCompleto(
        numeroTicket = "001",
        cliente = "Tienda Don Pepe",
        total = 459.99,
        fecha = Date(),
        sincronizado = false,
        fotoCliente = "",
        productos = listOf(
            ProductoTicketDetalle("Producto A", 2, 100.0),
            ProductoTicketDetalle("Producto B", 1, 259.99),
        )
    )

    PantallaDetalleTicketCompleto(
        navController = null,
        ticket = ticketEjemplo,
        impresoraBluetooth = null,
        coroutineScope = rememberCoroutineScope()
    )
}
