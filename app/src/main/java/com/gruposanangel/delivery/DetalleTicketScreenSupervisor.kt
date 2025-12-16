package com.gruposanangel.delivery.ui.screens

import ProductoTicketDetalle
import TicketVentaCompleto
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
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
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.utilidades.ImprimirTicket58mmCompleto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/* -----------------------------------------------------
   🔥 PANTALLA PRINCIPAL (SUPERVISOR - SOLO LECTURA)
------------------------------------------------------ */

@Composable
fun DetalleTicketSupervisorScreen(
    firestoreId: String,
    navController: NavController? = null,
    impresoraBluetooth: BluetoothDevice? = null
) {
    var ticket by remember { mutableStateOf<TicketVentaCompleto?>(null) }
    var cargando by remember { mutableStateOf(true) }

    // 🔥 DESCARGA DE FIRESTORE
    LaunchedEffect(firestoreId) {
        try {
            val db = FirebaseFirestore.getInstance()
            val doc = db.collection("ventas").document(firestoreId).get().await()

            if (doc.exists()) {
                val productosRemotos =
                    doc.get("productos") as? List<Map<String, Any>> ?: emptyList()

                val productos = productosRemotos.map {
                    ProductoTicketDetalle(
                        nombre = it["nombre"].toString(),
                        cantidad = (it["cantidad"] as Long).toInt(),
                        precio = it["precio"] as Double
                    )
                }

                ticket = TicketVentaCompleto(
                    numeroTicket = firestoreId,
                    cliente = doc.getString("clienteNombre") ?: "",
                    total = doc.getDouble("total") ?: 0.0,
                    fecha = doc.getDate("fecha") ?: Date(),
                    sincronizado = true,
                    fotoCliente = doc.getString("clienteImagenUrl") ?: "",
                    productos = productos
                )
            }

        } catch (_: Exception) {
        }
        cargando = false
    }

    if (cargando) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    ticket?.let {
        PantallaDetalleTicketFirebase(
            ticket = it,
            navController = navController,
            impresoraBluetooth = impresoraBluetooth
        )
    } ?: Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text("Error al cargar ticket desde Firebase")
    }
}

/* -----------------------------------------------------
   🔥 UI DE DETALLE
------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalleTicketFirebase(
    ticket: TicketVentaCompleto,
    navController: NavController?,
    impresoraBluetooth: BluetoothDevice?
) {
    val contexto = LocalContext.current
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val formatoFecha = SimpleDateFormat("EEEE d 'de' MMMM hh:mm a", Locale("es", "MX"))
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ticket #${ticket.numeroTicket}") },
                navigationIcon = {
                    if (navController != null) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.AltRoute, "Atrás")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // FOTO CLIENTE
            AsyncImage(
                model = ticket.fotoCliente,
                contentDescription = ticket.cliente,
                placeholder = painterResource(R.drawable.repartidor),
                error = painterResource(R.drawable.repartidor),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp))
            )

            Spacer(Modifier.height(16.dp))

            // DATOS PRINCIPALES
            Text(ticket.cliente, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Ticket #${ticket.numeroTicket}", color = Color.DarkGray)
            Text("Fecha: ${formatoFecha.format(ticket.fecha)}", color = Color.Gray)

            Text(
                "Total: ${formatoMoneda.format(ticket.total)}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Red
            )

            Spacer(Modifier.height(24.dp))

            // LISTA DE PRODUCTOS
            Text("Productos", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF3F3F3), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                ticket.productos.forEach { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${p.nombre} x${p.cantidad}")
                        Text(formatoMoneda.format(p.precio * p.cantidad))
                    }
                }
            }

            Spacer(Modifier.height(30.dp))

            // BOTÓN IMPRIMIR
            Button(
                onClick = {
                    if (impresoraBluetooth == null) {
                        Toast.makeText(contexto, "Seleccione una impresora", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    scope.launch(Dispatchers.IO) {
                        try {
                            val productos = ticket.productos.map {
                                Plantilla_Producto(
                                    id = "",
                                    nombre = it.nombre,
                                    precio = it.precio,
                                    cantidad = it.cantidad
                                )
                            }

                            ImprimirTicket58mmCompleto(
                                device = impresoraBluetooth,
                                context = contexto,
                                logoDrawableId = R.drawable.logo,
                                cliente = ticket.cliente,
                                productos = productos,
                                ventaId = null,
                                fechaVenta = ticket.fecha,
                                totalVenta = ticket.total,
                                vendedorNombre = "SUPERVISOR",
                                metodoPago = "",
                                sincronizado = true,
                                firestoreId = ticket.numeroTicket,
                                clienteId = ""
                            )

                            withContext(Dispatchers.Main) {
                                Toast.makeText(contexto, "Ticket impreso", Toast.LENGTH_SHORT).show()
                            }

                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(contexto, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(Color.Red)
            ) {
                Text("Imprimir Ticket", color = Color.White)
            }
        }
    }
}

/* -----------------------------------------------------
   🔥 PREVIEW FUNCIONAL
------------------------------------------------------ */

@Preview(showBackground = true)
@Composable
fun PreviewPantallaSupervisor() {
    val ticketEjemplo = TicketVentaCompleto(
        numeroTicket = "FB-12345",
        cliente = "Tienda La Esquina",
        total = 234.50,
        fecha = Date(),
        sincronizado = true,
        fotoCliente = "",
        productos = listOf(
            ProductoTicketDetalle("Coca Cola 600ml", 2, 18.5),
            ProductoTicketDetalle("Galletas", 1, 12.0),
            ProductoTicketDetalle("Sabritas", 3, 15.0)
        )
    )

    PantallaDetalleTicketFirebase(
        ticket = ticketEjemplo,
        navController = null,
        impresoraBluetooth = null
    )
}
