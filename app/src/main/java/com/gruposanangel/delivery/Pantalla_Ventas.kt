package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/* ---------------- HELPERS DE FECHA ---------------- */

fun Date.startOfDayLocal(): Date {
    val cal = Calendar.getInstance()
    cal.time = this
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.time
}

fun Date.nextDayStartLocal(): Date {
    val cal = Calendar.getInstance()
    cal.time = this
    cal.add(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.time
}

/* ---------------- MODELOS DE DATOS ---------------- */

data class VentaFirebase(
    val id: String,
    val clienteNombre: String,
    val fecha: Date,
    val total: Double,
    val sincronizado: Boolean,
    val productos: List<ProductoVenta>,
    val fotoClienteUrl: String // <--- Agregamos este campo
)

data class ProductoVenta(
    val nombre: String,
    val cantidad: Int,
    val precio: Double,
    val imagenUrl: String
)

/* ---------------- PANTALLA PRINCIPAL ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentasFirebaseScreen(navController: NavController) {
    val firestore = FirebaseFirestore.getInstance()

    val vendedores = remember { mutableStateListOf<Pair<String, String>>() }
    var vendedorSeleccionado by remember { mutableStateOf<String?>(null) }
    var nombreVendedorSeleccionado by remember { mutableStateOf("Seleccionar Vendedor") }
    var expandedVendedor by remember { mutableStateOf(false) }

    val fechaInicio = remember { mutableStateOf(Date().startOfDayLocal()) }
    val fechaFin = remember { mutableStateOf(Date()) }

    val ventas = remember { mutableStateListOf<VentaFirebase>() }
    var cargando by remember { mutableStateOf(false) }

    val formatoMoneda = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val formatoFecha = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX")) }

    LaunchedEffect(Unit) {
        firestore.collection("users")
            .whereEqualTo("activo", true)
            .get()
            .addOnSuccessListener { snap ->
                vendedores.clear()
                snap.forEach { doc ->
                    vendedores.add(doc.id to (doc.getString("nombre") ?: "Sin nombre"))
                }
            }
    }

    Scaffold(
        bottomBar = {
            if (ventas.isNotEmpty()) {
                ResumenVentasInferiorFirebase(ventas.size, ventas.sumOf { it.total }, formatoMoneda)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8F9FA))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("FILTROS DE CONSULTA", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray)

                    Box {
                        SelectorFiltroVisual(
                            label = "Vendedor",
                            valor = nombreVendedorSeleccionado,
                            icon = { Icon(Icons.Default.Person, null, tint = Color.Red, modifier = Modifier.size(20.dp)) },
                            onClick = { expandedVendedor = true }
                        )

                        DropdownMenu(
                            expanded = expandedVendedor,
                            onDismissRequest = { expandedVendedor = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            vendedores.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.second) },
                                    onClick = {
                                        vendedorSeleccionado = item.first
                                        nombreVendedorSeleccionado = item.second
                                        expandedVendedor = false
                                    }
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            DatePickerButtonFirebase("Desde", fechaInicio.value) { fechaInicio.value = it.startOfDayLocal() }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            DatePickerButtonFirebase("Hasta", fechaFin.value) { fechaFin.value = it }
                        }
                    }

                    Button(
                        onClick = {
                            val vId = vendedorSeleccionado ?: return@Button
                            cargando = true
                            ventas.clear()

                            val tsInicio = Timestamp(fechaInicio.value)
                            val tsFin = Timestamp(fechaFin.value.nextDayStartLocal())

                            firestore.collection("ventas")
                                .whereEqualTo("vendedorId", vId)
                                .whereGreaterThanOrEqualTo("fecha", tsInicio)
                                .whereLessThan("fecha", tsFin)
                                .orderBy("fecha")
                                .get()
                                .addOnSuccessListener { snap ->
                                    if (snap.isEmpty) { cargando = false; return@addOnSuccessListener }
                                    var pendientesCount = snap.size()

                                    snap.forEach { doc ->
                                        val clienteId = doc.getString("clienteId") ?: ""

                                        // 1. BUSCAMOS LA FOTO DEL CLIENTE PRIMERO
                                        firestore.collection("clientes").document(clienteId).get()
                                            .addOnSuccessListener { clienteDoc ->
                                                val fotoUrl = clienteDoc.getString("FotografiaCliente") ?: ""

                                                // 2. BUSCAMOS LOS PRODUCTOS DE ESA VENTA
                                                firestore.collection("ventas").document(doc.id).collection("productos").get()
                                                    .addOnSuccessListener { pSnap ->
                                                        val prods = pSnap.map { pDoc ->
                                                            ProductoVenta(
                                                                pDoc.getString("nombre") ?: "",
                                                                (pDoc.get("cantidad") as? Number)?.toInt() ?: 0,
                                                                (pDoc.get("precio") as? Number)?.toDouble() ?: 0.0,
                                                                pDoc.getString("imagenUrl") ?: ""
                                                            )
                                                        }

                                                        ventas.add(VentaFirebase(
                                                            id = doc.id,
                                                            clienteNombre = doc.getString("clienteNombre") ?: "Desconocido",
                                                            fecha = (doc["fecha"] as? Timestamp)?.toDate() ?: Date(),
                                                            total = (doc["total"] as? Number)?.toDouble() ?: 0.0,
                                                            sincronizado = doc.getBoolean("sincronizado") ?: false,
                                                            productos = prods,
                                                            fotoClienteUrl = fotoUrl // <--- AHORA SÍ ES LA FOTO DEL CLIENTE
                                                        ))

                                                        pendientesCount--
                                                        if (pendientesCount <= 0) cargando = false
                                                    }
                                                    .addOnFailureListener { cargando = false }
                                            }
                                            .addOnFailureListener { cargando = false }
                                    }
                                }
                                .addOnFailureListener { cargando = false }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("CONSULTAR VENTAS", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (cargando) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.Red) }
            } else if (ventas.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin resultados", color = Color.Gray) }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ventas) { item ->
                        VentaFirebaseCardPremium(item, formatoMoneda, formatoFecha)
                    }
                }
            }
        }
    }
}

/* ---------------- COMPONENTES DE DISEÑO ---------------- */

@Composable
fun VentaFirebaseCardPremium(venta: VentaFirebase, formatoMoneda: NumberFormat, formatoFecha: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                // --- AQUÍ SE MUESTRA LA FOTO DEL CLIENTE ---
                AsyncImage(
                    model = venta.fotoClienteUrl,
                    contentDescription = null,
                    modifier = Modifier.size(65.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF1F2F6)),
                    placeholder = painterResource(R.drawable.repartidor),
                    error = painterResource(R.drawable.repartidor),
                    contentScale = ContentScale.Crop
                )

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text("TICKET #${venta.id.takeLast(6).uppercase()}", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(venta.clienteNombre, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF2D3436), maxLines = 1)
                    Text(formatoFecha.format(venta.fecha), fontSize = 11.sp, color = Color.Gray)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(formatoMoneda.format(venta.total), color = Color.Red, fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (venta.sincronizado) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = if (venta.sincronizado) "OK" else "PEND",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = if (venta.sincronizado) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            }

            if (venta.productos.isNotEmpty()) {
                Divider(Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = Color(0xFFEEEEEE))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(venta.productos) { prod ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.background(Color(0xFFF8F9FA), RoundedCornerShape(8.dp)).padding(6.dp)
                        ) {
                            AsyncImage(
                                model = prod.imagenUrl,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(prod.nombre, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${prod.cantidad} pz • ${formatoMoneda.format(prod.precio)}", fontSize = 8.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectorFiltroVisual(label: String, valor: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable { onClick() }) {
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).background(Color(0xFFF1F2F6), RoundedCornerShape(8.dp)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(valor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerButtonFirebase(label: String, date: Date, onDateSelected: (Date) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val fmt = SimpleDateFormat("dd/MM/yy", Locale("es", "MX"))

    SelectorFiltroVisual(
        label = label,
        valor = fmt.format(date),
        icon = { Icon(Icons.Default.DateRange, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) }
    ) {
        showDialog = true
    }

    if (showDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.time)

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateSelected(Date(it)) }
                    showDialog = false
                }) {
                    Text("ACEPTAR", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        ) {
            DatePicker(
                state = state,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.White,
                    titleContentColor = Color.Gray,
                    headlineContentColor = Color.Red,
                    selectedDayContainerColor = Color.Red,
                    selectedDayContentColor = Color.White,
                    todayContentColor = Color.Red,
                    todayDateBorderColor = Color.Red,
                    dayContentColor = Color(0xFF2D3436),
                    weekdayContentColor = Color.Gray,
                    navigationContentColor = Color.DarkGray
                )
            )
        }
    }
}

@Composable
fun ResumenVentasInferiorFirebase(totalTickets: Int, totalDinero: Double, formatoMoneda: NumberFormat) {
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