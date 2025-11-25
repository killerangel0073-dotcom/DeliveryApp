package com.gruposanangel.delivery.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.VentaEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import coil.compose.AsyncImage

// -----------------------------
// Modelo UI para la pantalla
// -----------------------------
data class VentaPeriodo(
    val ticketNumero: String,
    val clienteNombre: String,
    val clienteImagenUrl: String? = null,
    val fecha: Date,
    val total: Double,
    val sincronizado: Boolean // 🔹 nuevo campo
)

// -----------------------------
// Pantalla UI pura
// -----------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaVentaPeriodoContent(
    navController: NavController?,
    listaVentas: List<VentaPeriodo>,
    fechaInicio: Date,
    fechaFin: Date,
    onCambiarFechaInicio: (Date) -> Unit,
    onCambiarFechaFin: (Date) -> Unit
) {
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX"))
    val formatoFechaBtn = SimpleDateFormat("dd/MM/yyyy", Locale("es", "MX"))

    val totalPeriodo by remember(listaVentas) { derivedStateOf { listaVentas.sumOf { it.total } } }

    // Estados para DatePicker
    val fechaPickerInicio = remember { mutableStateOf(false) }
    val fechaPickerFin = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ventas por periodo") },
                navigationIcon = {
                    if (navController != null) {


                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Color.Black)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black
                ),modifier = Modifier.height(48.dp) // 🔹 ajusta la altura aquí
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Botones de fecha
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { fechaPickerInicio.value = true },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.Red)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.Red)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Desde: ${formatoFechaBtn.format(fechaInicio)}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF0000)
                    )

                }

                OutlinedButton(
                    onClick = { fechaPickerFin.value = true },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.Red)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.Red)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Hasta: ${formatoFechaBtn.format(fechaFin)}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF0000)
                    )


                }
            }

            // DatePickers
            if (fechaPickerInicio.value) {
                DatePickerDialogMaterial3(
                    initialDate = fechaInicio,
                    onDateSelected = {
                        onCambiarFechaInicio(it)
                        fechaPickerInicio.value = false
                    },
                    onDismiss = { fechaPickerInicio.value = false }
                )
            }
            if (fechaPickerFin.value) {
                DatePickerDialogMaterial3(
                    initialDate = fechaFin,
                    onDateSelected = {
                        onCambiarFechaFin(it)
                        fechaPickerFin.value = false
                    },
                    onDismiss = { fechaPickerFin.value = false }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Lista de ventas
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(listaVentas) { venta ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {





                            AsyncImage(
                                model = venta.clienteImagenUrl,
                                contentDescription = venta.clienteNombre,
                                placeholder = painterResource(R.drawable.repartidor),
                                error = painterResource(R.drawable.repartidor),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )








                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (venta.sincronizado) "Sincronizado" else "Por sincronizar",
                                    fontSize = 12.sp,
                                    color = if (venta.sincronizado) Color(0xFF388E3C) else Color(0xFFFF0000),
                                    fontWeight = FontWeight.Medium
                                )

                                Text("Ticket: ${venta.ticketNumero}", fontWeight = FontWeight.Bold)
                                Text("Cliente: ${venta.clienteNombre}", fontSize = 14.sp, color = Color(0xFF555555))
                                Text("Fecha: ${formatoFecha.format(venta.fecha)}", fontSize = 12.sp, color = Color.Gray)
                            }
                            Text(
                                formatoMoneda.format(venta.total),
                                fontWeight = FontWeight.Bold,
                                color = Color.Red,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // 🔹 Resumen
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val clientesTotales = listaVentas.map { it.clienteNombre }.distinct().size
                        Text("$clientesTotales", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Black)
                        Text("Clientes totales", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.width(50.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(formatoMoneda.format(totalPeriodo), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFFFF0000))
                        Text("Total del periodo", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}






// -----------------------------
// Con ViewModel real
// -----------------------------
@Composable
fun PantallaVentaPeriodo(
    navController: NavController,
    vistaModelo: VistaModeloVenta
) {
    var fechaInicio by remember { mutableStateOf(Date()) }
    var fechaFin by remember { mutableStateOf(Date()) }

    // Obtenemos la lista de ventas como StateFlow y la convertimos a State
    val ventasPeriodo by vistaModelo.ventasPeriodo.collectAsState()

    // Cada vez que cambian las fechas, solicitamos cargar las ventas desde el ViewModel
    LaunchedEffect(fechaInicio, fechaFin) {
        vistaModelo.cargarVentasPorPeriodo(fechaInicio, fechaFin)
    }

    // Mapeamos ventasPeriodo a nuestro modelo de UI; se recompondrá automáticamente al cambiar
    val listaVentas = ventasPeriodo.map { venta ->
        VentaPeriodo(
            ticketNumero = venta.id.toString(),
            clienteNombre = venta.clienteNombre,
            clienteImagenUrl = venta.clienteImagenUrl,
            fecha = Date(venta.fecha),
            total = venta.total,
            sincronizado = venta.sincronizado
        )
    }

    PantallaVentaPeriodoContent(
        navController = navController,
        listaVentas = listaVentas,
        fechaInicio = fechaInicio,
        fechaFin = fechaFin,
        onCambiarFechaInicio = { fechaInicio = it },
        onCambiarFechaFin = { fechaFin = it }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogMaterial3(
    initialDate: Date,
    onDateSelected: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate.time)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            // Aplica un tema rojo solo para el DatePicker
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFFFF0000), // rojo
                    onPrimary = Color.White,
                    surface = Color.White,
                    onSurface = Color.Black
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DatePicker(
                        state = pickerState,
                        title = { Text("Selecciona una fecha") }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                pickerState.selectedDateMillis?.let { onDateSelected(Date(it)) }
                            }
                        ) { Text("Aceptar") }
                    }
                }
            }
        }
    }
}
// -----------------------------
// Preview sin ViewModel
// -----------------------------
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PantallaVentaPeriodoPreview() {
    val navController = rememberNavController()
    PantallaVentaPeriodoContent(
        navController = navController,
        listaVentas = listOf(
            VentaPeriodo("001", "Juan Pérez", null, Date(), 450.0, true),
            VentaPeriodo("002", "María López", null, Date(), 320.0, false),
            VentaPeriodo("003", "Carlos Sánchez", null, Date(), 210.0, true)
        ),
        fechaInicio = Date(),
        fechaFin = Date(),
        onCambiarFechaInicio = {},
        onCambiarFechaFin = {}
    )
}
