package com.gruposanangel.delivery.ui.screens

import android.net.Uri
import android.util.Log
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaRepository
// 👇 IMPORTANTE: Asegúrate que estos nombres coincidan con tu paquete de utilidades
import com.gruposanangel.delivery.utilidades.MedidorDeMetaPremium
import com.gruposanangel.delivery.utilidades.PreferenciasMetas
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class TicketVenta(
    val id: Long,
    val cliente: String,
    val total: Double,
    val fecha: Date,
    val sincronizado: Boolean,
    val fotoCliente: String = ""
)

@Composable
fun PaginaVentaScreen(
    navController: NavController,
    ventaRepository: VentaRepository
) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val inventarioRepo = RepositoryInventario(db.productoDao())
    val clienteDao = db.clienteDao()

    val viewModel: VistaModeloVenta = viewModel(
        factory = VistaModeloVentaFactory(
            repositoryInventario = inventarioRepo,
            ventaRepository = ventaRepository
        )
    )

    val ventasHoy by viewModel.ventasPeriodo.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        viewModel.cargarVentasHoy()
        viewModel.descargarVentasDiaYRefrescar(uid)
    }

    // Mapeo de datos (Room -> UI)
    val ticketsHoy = ventasHoy.map { venta ->
        // Nota: El runBlocking aquí puede ralentizar la UI.
        // Idealmente el clienteId debería traer el objeto Cliente mediante un JOIN en el DAO.
        val cliente = runBlocking { clienteDao.getClientePorId(venta.clienteId) }
        TicketVenta(
            id = venta.id,
            cliente = venta.clienteNombre,
            total = venta.total,
            fecha = Date(venta.fecha),
            sincronizado = venta.sincronizado,
            fotoCliente = cliente?.fotografiaUrl ?: ""
        )
    }

    PaginaVentaContent(navController, ticketsHoy, viewModel)
}

@Composable
fun PaginaVentaContent(
    navController: NavController?,
    ticketsHoy: List<TicketVenta>,
    viewModel: VistaModeloVenta? = null
) {
    val context = LocalContext.current
    val formatoMoneda = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val formatoFecha = remember { SimpleDateFormat("EEEE d 'de' MMMM hh:mm a", Locale("es", "MX")) }

    // 1. Usar la clase que moviste a Utilidades
    val prefsMeta = remember { PreferenciasMetas(context) }
    val valoresIniciales = remember { prefsMeta.obtenerValores(11666.0, 35) }

    var meta by remember { mutableStateOf(valoresIniciales.first) }
    var totalClientes by remember { mutableStateOf(valoresIniciales.second) }
    val totalHoy = ticketsHoy.sumOf { it.total }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Spacer(modifier = Modifier.height(8.dp))

        // 2. Llamada al componente que ahora es externo
        MedidorDeMetaPremium(
            metaDelDia = meta,
            totalClientes = totalClientes,
            clientesVisitados = ticketsHoy.size,
            avance = totalHoy,
            onUpdateMeta = { nuevaMeta ->
                meta = nuevaMeta
                prefsMeta.guardarValores(meta, totalClientes)
            },
            onUpdateClientes = { nuevosClientes ->
                totalClientes = nuevosClientes
                prefsMeta.guardarValores(meta, totalClientes)
            }
        )

        if (ticketsHoy.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Sin ventas el día de hoy", color = Color.Gray, fontSize = 18.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(ticketsHoy) { ticket ->
                    CardTicketVenta(ticket, formatoFecha, formatoMoneda, navController)
                }
            }
        }

        // Resumen Inferior
        ResumenVentasCard(ticketsHoy.size, totalHoy, formatoMoneda)
    }
}

@Composable
fun CardTicketVenta(
    ticket: TicketVenta,
    formatoFecha: SimpleDateFormat,
    formatoMoneda: NumberFormat,
    navController: NavController?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { navController?.navigate("detalle_ticket_completo/${ticket.id}") },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val imageModel = remember(ticket.fotoCliente) {
                if (ticket.fotoCliente.isNotBlank()) {
                    val file = File(ticket.fotoCliente)
                    if (file.exists()) Uri.fromFile(file) else ticket.fotoCliente
                } else null
            }

            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.repartidor),
                error = painterResource(R.drawable.repartidor),
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp))
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text("Ticket #${ticket.id}", fontSize = 11.sp, color = Color.Gray)
                Text(ticket.cliente, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF333333))
                Spacer(modifier = Modifier.height(4.dp)) // <- espacio vertical
                Text(formatoFecha.format(ticket.fecha), fontSize = 11.sp, color = Color.Gray)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(formatoMoneda.format(ticket.total), color = Color.Red, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp)) // <- espacio vertical
                Text(
                    if (ticket.sincronizado) "SINCRONIZADO" else "PENDIENTE",
                    color = if (ticket.sincronizado) Color(0xFF388E3C) else Color(0xFFD32F2F),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ResumenVentasCard(totalTickets: Int, totalDinero: Double, formatoMoneda: NumberFormat) {
    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Ventas: $totalTickets", fontWeight = FontWeight.Bold)
            Text("Total: ${formatoMoneda.format(totalDinero)}", fontWeight = FontWeight.Black, color = Color.Red, fontSize = 18.sp)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PaginaVentaPreview() {
    // Datos de ejemplo para la vista previa
    val ticketsPreview = listOf(
        TicketVenta(
            id = 101L,
            cliente = "Abarrotes La Pasadita",
            total = 1250.50,
            fecha = Date(),
            sincronizado = true,
            fotoCliente = ""
        ),
        TicketVenta(
            id = 102L,
            cliente = "Miscelánea Doña Mary",
            total = 450.00,
            fecha = Date(),
            sincronizado = false,
            fotoCliente = ""
        ),
        TicketVenta(
            id = 103L,
            cliente = "Carnicería El Torito",
            total = 2800.00,
            fecha = Date(),
            sincronizado = true,
            fotoCliente = ""
        )
    )

    // En el preview pasamos null al navController y al viewModel
    // ya que PaginaVentaContent está preparada para manejar nulos
    MaterialTheme {
        PaginaVentaContent(
            navController = null,
            ticketsHoy = ticketsPreview,
            viewModel = null
        )
    }
}