package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val estado: String = "pagada"
)

@Composable
fun PaginaVentaScreen(navController: NavController, ventaRepository: VentaRepository) {
    val context = LocalContext.current; val isPreview = LocalInspectionMode.current
    val db = AppDatabase.getDatabase(context); val repoUsuario = RepositoryUsuario(FirebaseDataSource(), db.usuarioDao())
    val viewModel: VentaViewModel = viewModel(factory = VentaViewModelFactory(RepositoryInventario(FirebaseDataSource(), db.productoDao(), db.VentaDao()), ventaRepository, repoUsuario))
    val ventasHoy by viewModel.ventasHoyFlow.collectAsState()
    var ticketsHoy by remember { mutableStateOf<List<TicketVenta>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }

    val isDark = ThemeConfig.isDarkTheme.value ?: isSystemInDarkTheme()

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
                TicketVenta(
                    id = v.id, 
                    cliente = v.clienteNombre, 
                    total = v.total, 
                    fecha = Date(v.fecha), 
                    sincronizado = v.sincronizado, 
                    fotoCliente = c?.fotografiaUrl ?: "",
                    estado = v.estado
                )
            } 
        }
        ticketsHoy = lista
    }

    DeliveryTheme(darkTheme = isDark) {
        PaginaVentaContent(
            ticketsHoy = ticketsHoy, 
            isLoading = isRefreshing && ticketsHoy.isEmpty(),
            onTicketClick = { navController.navigate("detalle_venta_admin/${it.id}") }
        )
    }
}

@Composable
fun PaginaVentaContent(ticketsHoy: List<TicketVenta>, isLoading: Boolean = false, onTicketClick: (TicketVenta) -> Unit) {
    val context = LocalContext.current; val fmtMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    val fmtFecha = SimpleDateFormat("EEEE d 'de' MMMM, hh:mm a", Locale.forLanguageTag("es-MX"))
    val prefs = remember { PreferenciasMetas(context) }; val metaVals = remember { prefs.obtenerValores(6500.0, 30) }
    var meta by remember { mutableStateOf(metaVals.first) }; var cliTarget by remember { mutableStateOf(metaVals.second) }
    val totalHoy = ticketsHoy.filter { it.estado != "CANCELADA" }.sumOf { it.total }
    val visitasHoy = ticketsHoy.count { it.estado != "CANCELADA" }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(10.dp))
            MedidorDeMetaPremium(
                metaDelDia = meta, 
                totalClientes = cliTarget, 
                clientesVisitados = visitasHoy, 
                avance = totalHoy, 
                onUpdateMeta = { meta = it; prefs.guardarValores(meta, cliTarget) }, 
                onUpdateClientes = { cliTarget = it; prefs.guardarValores(meta, cliTarget) }
            )
            Spacer(Modifier.height(16.dp))
            
            if (isLoading && ticketsHoy.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = DelisaRed) }
            } else if (ticketsHoy.isEmpty()) { 
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Sin ventas hoy", color = MaterialTheme.colorScheme.onSurfaceVariant) } 
            } else { 
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) { items(ticketsHoy, key = { it.id }) { t -> CardTicketRuta(t, fmtFecha, fmtMoneda, onTicketClick) } } 
            }
        }
        ResumenVentasCard(visitasHoy, totalHoy, fmtMoneda)
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
                Surface(
                    shape = RoundedCornerShape(8.dp), 
                    color = if (ticket.sincronizado) DelisaGreen.copy(alpha = 0.1f) else DelisaRed.copy(alpha = 0.1f),
                    modifier = Modifier.width(90.dp)
                ) { 
                    Text(
                        text = if (ticket.sincronizado) "SINCRONIZADO" else "PENDIENTE", 
                        modifier = Modifier.padding(vertical = 2.dp).fillMaxWidth(), 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Black, 
                        color = if (ticket.sincronizado) DelisaGreenDark else DelisaRed,
                        textAlign = TextAlign.Center
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
                            fontSize = 12.sp, // Más grande
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
    DeliveryTheme { PaginaVentaContent(tickets, isLoading = false, onTicketClick = {}) }
}
