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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.VentaRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import androidx.compose.ui.text.style.TextAlign
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryInventario

data class TicketVenta(
    val numeroTicket: String,
    val cliente: String,
    val total: Double,
    val fecha: Date,
    val sincronizado: Boolean,
    val fotoCliente: String = ""
)

@Composable
fun MedidorDeMetaPremium(
    metaDelDia: Double,
    totalClientes: Int,
    clientesVisitados: Int,
    avance: Double, // ahora lo pasamos desde afuera
    width: Int = 380,
    height: Int = 200
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }

    val clientesFaltantes = (totalClientes - clientesVisitados).coerceAtLeast(0)
    val falta = (metaDelDia - avance).coerceAtLeast(0.0)
    val ticketPromedio = if (clientesVisitados > 0) avance / clientesVisitados else 0.0
    val ticketNecesario = if (clientesFaltantes > 0) falta / clientesFaltantes else 0.0
    val metaCumplida = avance >= metaDelDia

    Column(
        modifier = Modifier
            .width(width.dp)
            .height(height.dp)
            .background(Color(0xFFFF0000), RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        // Slider solo si quieres que sea interactivo, sino puedes mostrar un ProgressBar
        LinearProgressIndicator(
            progress = (avance / metaDelDia).coerceIn(0.0, 1.0).toFloat(),
            color = Color.White,
            trackColor = Color(0xFF880E1F),
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InfoBlock("Avance", currencyFormat.format(avance))
            InfoBlock("Meta", currencyFormat.format(metaDelDia))
            InfoBlock("Falta", currencyFormat.format(falta))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x33000000), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoBlock("Total Clientes", "$totalClientes")
                InfoBlock("VENTAS", "$clientesVisitados")
                InfoBlock("Faltantes", "$clientesFaltantes")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoBlock("Ticket promedio", currencyFormat.format(ticketPromedio))
                if (metaCumplida) {
                    Text(
                        "META CUMPLIDA",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFF333333), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else {
                    InfoBlock("Ticket necesario", currencyFormat.format(ticketNecesario))
                }
            }
        }
    }
}


@Composable

fun InfoBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF666666), fontWeight = FontWeight.Normal, fontSize = 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}



// -----------------------------
// PANTALLA PRINCIPAL DE VENTAS
// -----------------------------
@Composable
fun PaginaVentaScreen(
    navController: NavController,
    ventaRepository: VentaRepository
) {


    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val inventarioRepo = RepositoryInventario(db.productoDao()) // ✅ inicializar inventarioRepo
    val ventaRepository = VentaRepository(db.VentaDao())


    // ViewModel para manejar la lógica de ventas
    val viewModel: VistaModeloVenta = viewModel(
        factory = VistaModeloVentaFactory(
            repositoryInventario = inventarioRepo,
            ventaRepository = ventaRepository
        )
    )


    // Estado observable de ventas de hoy
    val ventasHoy by viewModel.ventasPeriodo.collectAsState(initial = emptyList())







    // Cargar ventas de hoy al entrar
    LaunchedEffect(Unit) {
        viewModel.cargarVentasHoy()
    }

    val ticketsHoy = ventasHoy.map { venta ->
        TicketVenta(
            numeroTicket = venta.id.toString(),
            cliente = venta.clienteNombre,
            total = venta.total,
            fecha = Date(venta.fecha),
            sincronizado = venta.sincronizado,
            fotoCliente = venta.clienteImagenUrl ?: ""
        )
    }

    PaginaVentaContent(navController, ticketsHoy, viewModel)


}



@Composable
fun PaginaVentaContent(
    navController: NavController?,
    ticketsHoy: List<TicketVenta>,
    viewModel: VistaModeloVenta? = null // <-- opcional para preview✅ recibe el ViewModel
) {


    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val formatoFecha = SimpleDateFormat("EEEE d 'de' MMMM hh:mm a", Locale("es", "MX"))


    // ✅ Definir estas variables aquí
    val totalHoy = ticketsHoy.sumOf { it.total }
    val totalTickets = ticketsHoy.size
    val clientesVisitados = ticketsHoy.count { it.sincronizado }
    // Para el medidor
    val metaDelDia = 9821.0
    val toatlClientes = 29

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Medidor actualizado dinámicamente
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            MedidorDeMetaPremium(
                metaDelDia = metaDelDia, // <-- aquí va la meta del día
                totalClientes = toatlClientes, // <-- aquí va el total de clientes del día
                clientesVisitados = totalTickets, // <-- aquí va el total de tickets emitidos
                width = 380,
                height = 200,
                avance = totalHoy, // <-- aquí va el avance real
            )
        }









        if (ticketsHoy.isEmpty()) {
            // ✅ Mensaje cuando no hay ventas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sin ventas el día de hoy",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        } else {



        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(ticketsHoy) { ticket ->

                val scope = rememberCoroutineScope()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable {
                            val ticketIdLong = ticket.numeroTicket.toLongOrNull()
                            if (ticketIdLong != null) {
                                navController?.navigate("detalle_ticket_completo/$ticketIdLong")
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ticket.fotoCliente,
                            contentDescription = ticket.cliente,
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(R.drawable.repartidor),
                            error = painterResource(R.drawable.repartidor),
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text("Ticket #${ticket.numeroTicket}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp)
                            Text(ticket.cliente,
                                color = Color(0xFF555555),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp)
                            Text(formatoFecha.format(ticket.fecha), fontSize = 12.sp, color = Color.Gray)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                formatoMoneda.format(ticket.total),
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                if (ticket.sincronizado) "SINCRONIZADO" else "PENDIENTE",
                                color = if (ticket.sincronizado) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
        }





        // Resumen
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tickets: ${ticketsHoy.size}", fontWeight = FontWeight.Bold, color = Color.Black)
                Text("Total: ${formatoMoneda.format(totalHoy)}", fontWeight = FontWeight.Bold, color = Color.Red)
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun PaginaVentaPreview() {
    val ticketsPreview = listOf(
        TicketVenta("001", "Negocio 1", 250.0, Date(), true),
        TicketVenta("002", "Negocio 2", 380.5, Date(), false),
        TicketVenta("003", "Negocio 3", 120.0, Date(), true)
    )

    PaginaVentaContent(
        navController = null,
        ticketsHoy = ticketsPreview
    )
}
