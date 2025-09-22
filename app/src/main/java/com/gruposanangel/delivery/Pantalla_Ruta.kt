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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.ClienteRepository
import com.gruposanangel.delivery.data.ClienteEntity
import java.text.NumberFormat
import java.util.*

data class ClienteVenta(
    val nombreNegocio: String = "",
    val nombreDueno: String = "",
    val fotografiaCliente: String = "",
    val activo: Boolean = true
)

@Composable
fun MedidorDeMetaPremium(
    metaDelDia: Double,
    totalClientes: Int,
    clientesVisitados: Int,
    width: Int = 380,
    height: Int = 200
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    var avance by remember { mutableStateOf(0.0) }

    val clientesFaltantes = (totalClientes - clientesVisitados).coerceAtLeast(0)
    val falta = (metaDelDia - avance).coerceAtLeast(0.0)
    val ticketPromedio = if (clientesVisitados > 0) avance / clientesVisitados else 0.0
    val ticketNecesario = if (clientesFaltantes > 0) falta / clientesFaltantes else 0.0
    val progreso = (avance / metaDelDia).coerceIn(0.0, 1.0)
    val metaCumplida = avance >= metaDelDia

    Column(
        modifier = Modifier
            .width(width.dp)
            .height(height.dp)
            .background(Color(0xFFFF0000), RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Slider(
            value = avance.toFloat(),
            onValueChange = { avance = it.toDouble() },
            valueRange = 0f..metaDelDia.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0xFF880E1F)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
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
                InfoBlock("Total clientes", "$totalClientes")
                InfoBlock("Visitados", "$clientesVisitados")
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
        Text(label, color = Color(0xFF666666), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaginaVentaScreen(repository: ClienteRepository) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var buscando by remember { mutableStateOf(false) }

    // Observa la base de datos local
    val clientesLocal by repository.obtenerClientesLocal().collectAsState(initial = emptyList())

    // Filtrado por búsqueda
    val listaFiltrada = if (buscando) {
        clientesLocal.filter { it.nombreNegocio.contains(textFieldValue.text, ignoreCase = true) }
            .map { ClienteVenta(it.nombreNegocio, it.nombreDueno, it.fotografiaUrl ?: "", it.activo) }
    } else {
        clientesLocal.map { ClienteVenta(it.nombreNegocio, it.nombreDueno, it.fotografiaUrl ?: "", it.activo) }
    }

    val totalClientes = clientesLocal.size
    val clientesVisitados = clientesLocal.count { it.activo }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            MedidorDeMetaPremium(
                metaDelDia = 9821.0,
                totalClientes = totalClientes,
                clientesVisitados = clientesVisitados
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    buscando = it.text.isNotEmpty()
                },
                placeholder = { Text("Buscar Cliente", color = Color(0xFF888888)) },
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = Color(0xFFF5F5F5),
                    cursorColor = Color(0xFFB71C1C),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(listaFiltrada) { cliente ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { /* Acción al seleccionar cliente */ },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = cliente.fotografiaCliente,
                            contentDescription = cliente.nombreNegocio,
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(R.drawable.repartidor),
                            error = painterResource(R.drawable.repartidor),
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(cliente.nombreNegocio, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(cliente.nombreDueno, color = Color(0xFF555555), fontSize = 14.sp)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (cliente.activo) "Activo" else "Inactivo",
                                color = if (cliente.activo) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PaginaVentaPreview() {
    val clientesPreview = listOf(
        ClienteVenta("Negocio 1", "Dueño 1", "", true),
        ClienteVenta("Negocio 2", "Dueño 2", "", false),
        ClienteVenta("Negocio 3", "Dueño 3", "", true)
    )

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            MedidorDeMetaPremium(
                metaDelDia = 9821.0,
                totalClientes = 3,
                clientesVisitados = 2
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(clientesPreview) { cliente ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = cliente.fotografiaCliente,
                            contentDescription = cliente.nombreNegocio,
                            placeholder = painterResource(R.drawable.repartidor),
                            error = painterResource(R.drawable.repartidor),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(cliente.nombreNegocio, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(cliente.nombreDueno, color = Color(0xFF555555), fontSize = 14.sp)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (cliente.activo) "Activo" else "Inactivo",
                                color = if (cliente.activo) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
