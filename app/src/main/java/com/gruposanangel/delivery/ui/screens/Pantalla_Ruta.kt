package com.gruposanangel.delivery.ui.screens

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
import androidx.compose.ui.platform.LocalInspectionMode
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
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import com.gruposanangel.delivery.utilidades.MedidorDeMetaPremium
import com.gruposanangel.delivery.utilidades.PreferenciasMetas
import kotlinx.coroutines.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class TicketVenta(val id: String, val cliente: String, val total: Double, val fecha: Date, val sincronizado: Boolean, val fotoCliente: String = "")

@Composable
fun PaginaVentaScreen(navController: NavController, ventaRepository: VentaRepository) {
    val context = LocalContext.current; val isPreview = LocalInspectionMode.current
    val db = AppDatabase.getDatabase(context); val repoUsuario = RepositoryUsuario(FirebaseDataSource(), db.usuarioDao())
    val viewModel: VentaViewModel = viewModel(factory = VentaViewModelFactory(RepositoryInventario(FirebaseDataSource(), db.productoDao(), db.VentaDao()), ventaRepository, repoUsuario))
    val ventasHoy by viewModel.ventasHoyFlow.collectAsState()
    var ticketsHoy by remember { mutableStateOf<List<TicketVenta>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { 
        if (!isPreview) { 
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
            isRefreshing = true
            viewModel.sincronizarVentasDia(uid)
            isRefreshing = false
        } 
    }

    LaunchedEffect(ventasHoy) { 
        val lista = withContext(Dispatchers.IO) { 
            ventasHoy.map { v -> 
                val c = db.clienteDao().getClientePorId(v.clienteId)
                TicketVenta(v.id, v.clienteNombre, v.total, Date(v.fecha), v.sincronizado, c?.fotografiaUrl ?: "") 
            } 
        }
        ticketsHoy = lista
    }

    PaginaVentaContent(
        ticketsHoy = ticketsHoy, 
        isLoading = isRefreshing && ticketsHoy.isEmpty(),
        onTicketClick = { navController.navigate("detalle_venta_admin/${it.id}") }
    )
}

@Composable
fun PaginaVentaContent(ticketsHoy: List<TicketVenta>, isLoading: Boolean = false, onTicketClick: (TicketVenta) -> Unit) {
    val context = LocalContext.current; val fmtMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    val fmtFecha = SimpleDateFormat("EEEE d 'de' MMMM, hh:mm a", Locale.forLanguageTag("es-MX"))
    val prefs = remember { PreferenciasMetas(context) }; val metaVals = remember { prefs.obtenerValores(11666.0, 35) }
    var meta by remember { mutableStateOf(metaVals.first) }; var cliTarget by remember { mutableStateOf(metaVals.second) }
    val totalHoy = ticketsHoy.sumOf { it.total }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(10.dp))
            MedidorDeMetaPremium(metaDelDia = meta, totalClientes = cliTarget, clientesVisitados = ticketsHoy.size, avance = totalHoy, onUpdateMeta = { meta = it; prefs.guardarValores(meta, cliTarget) }, onUpdateClientes = { cliTarget = it; prefs.guardarValores(meta, cliTarget) })
            Spacer(Modifier.height(16.dp))
            
            if (isLoading && ticketsHoy.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.Red) }
            } else if (ticketsHoy.isEmpty()) { 
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Sin ventas hoy", color = Color.Gray) } 
            } else { 
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) { items(ticketsHoy, key = { it.id }) { t -> CardTicketRuta(t, fmtFecha, fmtMoneda, onTicketClick) } } 
            }
        }
        ResumenVentasCard(ticketsHoy.size, totalHoy, fmtMoneda)
    }
}

@Composable
fun CardTicketRuta(ticket: TicketVenta, fmtFecha: SimpleDateFormat, fmtMoneda: NumberFormat, onClick: (TicketVenta) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick(ticket) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = ticket.fotoCliente, contentDescription = null, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentScale = ContentScale.Crop, modifier = Modifier.size(65.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("FOLIO #${ticket.id.takeLast(6).uppercase()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Text(ticket.cliente, fontWeight = FontWeight.Black, fontSize = 17.sp, color = Color.Black, maxLines = 1)
                Text(fmtFecha.format(ticket.fecha), fontSize = 11.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtMoneda.format(ticket.total), color = Color.Red, fontWeight = FontWeight.Black, fontSize = 18.sp)
                Surface(shape = RoundedCornerShape(8.dp), color = if (ticket.sincronizado) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)) { Text(if (ticket.sincronizado) "SINCRONIZADO" else "PENDIENTE", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (ticket.sincronizado) Color(0xFF2E7D32) else Color(0xFFC62828)) }
            }
        }
    }
}

@Composable
fun ResumenVentasCard(count: Int, total: Double, fmt: NumberFormat) {
    Card(shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(20.dp)) {
        Row(Modifier.padding(24.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column { Text("PRODUCTIVIDAD", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold); Text("$count Visitas", fontWeight = FontWeight.Black, fontSize = 20.sp) }
            Column(horizontalAlignment = Alignment.End) { Text("RECAUDADO", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold); Text(fmt.format(total), fontWeight = FontWeight.Black, color = Color.Red, fontSize = 24.sp) }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PaginaRutaPreview() {
    val tickets = listOf(TicketVenta("1", "Abarrotes Don Pepe", 1250.0, Date(), true), TicketVenta("2", "Mini Super El Sol", 450.0, Date(), false))
    DeliveryTheme { PaginaVentaContent(tickets, isLoading = false, onTicketClick = {}) }
}
