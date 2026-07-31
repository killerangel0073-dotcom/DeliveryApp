package com.gruposanangel.delivery.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import coil.compose.AsyncImage
import androidx.compose.foundation.isSystemInDarkTheme

data class VentaPeriodo(
    val ticketNumero: String,
    val clienteNombre: String,
    val clienteImagenUrl: String? = null,
    val fecha: Date,
    val total: Double,
    val sincronizado: Boolean,
    val estado: String = "pagada"
)

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
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX"))
    val formatoFechaBtn = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-MX"))

    val totalPeriodo by remember(listaVentas) { 
        derivedStateOf { 
            listaVentas.filter { it.estado != "CANCELADA" }.sumOf { it.total } 
        } 
    }

    val fechaPickerInicio = remember { mutableStateOf(false) }
    val fechaPickerFin = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VENTAS POR PERIODO", fontWeight = FontWeight.Black, fontSize = 16.sp) },
                navigationIcon = {
                    if (navController != null) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = DelisaRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedCard(
                    onClick = { fechaPickerInicio.value = true },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DelisaRed.copy(alpha = 0.2f)),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = DelisaRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("DESDE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatoFechaBtn.format(fechaInicio),
                                fontWeight = FontWeight.ExtraBold,
                                color = DelisaRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                OutlinedCard(
                    onClick = { fechaPickerFin.value = true },
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DelisaRed.copy(alpha = 0.2f)),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = DelisaRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("HASTA", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatoFechaBtn.format(fechaFin),
                                fontWeight = FontWeight.ExtraBold,
                                color = DelisaRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

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

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(listaVentas) { venta ->
                    val esCancelada = venta.estado == "CANCELADA"
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = if (esCancelada) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        )
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = if (venta.sincronizado) DelisaGreen.copy(0.1f) else DelisaRed.copy(0.1f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (venta.sincronizado) "SINCRONIZADO" else "PENDIENTE",
                                            fontSize = 9.sp,
                                            color = if (venta.sincronizado) DelisaGreenDark else DelisaRed,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (esCancelada) {
                                        Spacer(Modifier.width(8.dp))
                                        Surface(color = DelisaRed, shape = RoundedCornerShape(6.dp)) {
                                            Text("ANULADA", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = venta.clienteNombre, 
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 15.sp, 
                                    color = if (esCancelada) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = if (esCancelada) MaterialTheme.typography.bodyMedium.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else MaterialTheme.typography.bodyMedium
                                )
                                Text("Folio: ${venta.ticketNumero.takeLast(6).uppercase()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Text(formatoFecha.format(venta.fecha), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = formatoMoneda.format(venta.total),
                                fontWeight = FontWeight.Black,
                                color = if (esCancelada) MaterialTheme.colorScheme.onSurfaceVariant else DelisaRed,
                                fontSize = 17.sp,
                                style = if (esCancelada) androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default
                            )
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val clientesTotales = listaVentas.filter { it.estado != "CANCELADA" }
                            .map { it.clienteNombre }.distinct().size
                        Text("CLIENTES", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$clientesTotales", fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)))

                    Column(
                        modifier = Modifier.weight(1.5f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TOTAL RECAUDADO", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatoMoneda.format(totalPeriodo), fontWeight = FontWeight.Black, fontSize = 24.sp, color = DelisaRed)
                    }
                }
            }
        }
    }
}

@Composable
fun PantallaVentaPeriodo(
    navController: NavController,
    vistaModelo: VentaViewModel
) {
    var fechaInicio by remember { mutableStateOf(Date()) }
    var fechaFin by remember { mutableStateOf(Date()) }

    val ventasPeriodo by vistaModelo.ventasPeriodo.collectAsState()

    LaunchedEffect(fechaInicio, fechaFin) {
        vistaModelo.cargarVentasPorPeriodo(fechaInicio, fechaFin)
    }

    val listaVentas = ventasPeriodo.map { venta ->
        VentaPeriodo(
            ticketNumero = venta.id,
            clienteNombre = venta.clienteNombre,
            clienteImagenUrl = venta.clienteImagenUrl,
            fecha = Date(venta.fecha),
            total = venta.total,
            sincronizado = venta.sincronizado,
            estado = venta.estado
        )
    }

    val isDark = ThemeConfig.isActuallyDark

    DeliveryTheme(darkTheme = isDark) {
        PantallaVentaPeriodoContent(
            navController = navController,
            listaVentas = listaVentas,
            fechaInicio = fechaInicio,
            fechaFin = fechaFin,
            onCambiarFechaInicio = { fechaInicio = it },
            onCambiarFechaFin = { fechaFin = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogMaterial3(
    initialDate: Date,
    onDateSelected: (Date) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate.time)
    val isDark = ThemeConfig.isActuallyDark

    DeliveryTheme(darkTheme = isDark) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateSelected(Date(it)) }
                }) { Text("ACEPTAR", fontWeight = FontWeight.Bold, color = DelisaRed) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        ) {
            DatePicker(
                state = pickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    headlineContentColor = DelisaRed,
                    selectedDayContainerColor = DelisaRed,
                    selectedDayContentColor = Color.White,
                    todayDateBorderColor = DelisaRed,
                    todayContentColor = DelisaRed
                )
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PantallaVentaPeriodoPreview() {
    val navController = rememberNavController()
    DeliveryTheme(darkTheme = false) {
        PantallaVentaPeriodoContent(
            navController = navController,
            listaVentas = listOf(
                VentaPeriodo("001", "Abarrotes La Lupita", null, Date(), 450.0, true),
                VentaPeriodo("002", "Mini Super El Sol", null, Date(), 320.0, false),
                VentaPeriodo("003", "Tienda Don Juan", null, Date(), 210.0, true, "CANCELADA")
            ),
            fechaInicio = Date(),
            fechaFin = Date(),
            onCambiarFechaInicio = {},
            onCambiarFechaFin = {}
        )
    }
}
