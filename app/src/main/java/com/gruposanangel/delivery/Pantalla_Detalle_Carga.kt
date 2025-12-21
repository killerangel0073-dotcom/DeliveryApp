package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.model.Plantila_carga
import com.gruposanangel.delivery.model.Plantilla_Producto
import java.util.Locale
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion


@Composable
fun PantallaDetalleCarga(
    navController: NavController,
    plantilacarga: Plantila_carga? = null
) {
    val isPreview = LocalInspectionMode.current

    // 🔹 CORRECCIÓN AQUÍ: Buscamos en 'previousBackStackEntry' porque ahí lo guardó Notificaciones
    val cargaBase = remember {
        plantilacarga ?: navController.previousBackStackEntry?.savedStateHandle?.get<Plantila_carga>("carga")
    }

    // Si cargaBase es nulo, no dibujamos nada para evitar errores
    if (cargaBase == null && !isPreview) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error: No se recibió información de la carga")
        }
        return
    }

    var listaCompletaProductos by remember { mutableStateOf<List<Plantilla_Producto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(!isPreview) }
    var mostrarDialog by remember { mutableStateOf(false) }
    var mensajeDialog by remember { mutableStateOf("") }

    // 2. Solo aquí adentro llamamos a Firebase usando el ID que ya comprobamos
    LaunchedEffect(cargaBase?.id) {
        val idDocumento = cargaBase?.id ?: ""

        if (!isPreview && idDocumento.isNotEmpty()) {
            Log.d("DEBUG_CARGA", "Buscando documento: $idDocumento")
            try {
                val db = FirebaseFirestore.getInstance()

                db.collection("ordenesTransferencia").document(idDocumento).get()
                    .addOnSuccessListener { document ->
                        // 🔹 REVISA ESTO: El campo en Firestore debe llamarse exactamente "productos"
                        val productosArray = document.get("productos") as? List<Map<String, Any>> ?: emptyList()
                        val timestamp = document.getTimestamp("timestamp")?.toDate()

                        // Guardamos la fecha en cargaBase (si tu modelo la tiene)
                        cargaBase?.fecha = timestamp

                        if (productosArray.isEmpty()) {
                            Log.d("DEBUG_CARGA", "El array 'productos' está vacío o no existe")
                            isLoading = false
                            return@addOnSuccessListener
                        }

                        val listaTemporal = mutableListOf<Plantilla_Producto>()
                        var contadorProcesados = 0

                        productosArray.forEach { item ->
                            val pId = item["productoId"] as? String ?: ""
                            val pCantidad = (item["cantidad"] as? Long)?.toInt() ?: 0

                            // Buscamos el detalle de cada producto
                            db.collection("producto").document(pId).get()
                                .addOnSuccessListener { pDoc ->
                                    if (pDoc.exists()) {
                                        listaTemporal.add(Plantilla_Producto(
                                            id = pId,
                                            nombre = pDoc.getString("nombre") ?: "Producto",
                                            precio = pDoc.getDouble("precio") ?: 0.0,
                                            cantidad = pCantidad,
                                            imagenUrl = pDoc.getString("imagenUrl") ?: ""
                                        ))
                                    }

                                    contadorProcesados++
                                    // Cuando terminamos con todos, actualizamos la lista
                                    if (contadorProcesados == productosArray.size) {
                                        listaCompletaProductos = listaTemporal
                                        isLoading = false
                                        Log.d("DEBUG_CARGA", "Carga finalizada con ${listaTemporal.size} productos")
                                    }
                                }
                                .addOnFailureListener {
                                    contadorProcesados++
                                    if (contadorProcesados == productosArray.size) isLoading = false
                                }
                        }
                    }
                    .addOnFailureListener {
                        Log.e("DEBUG_CARGA", "Error al obtener orden: ${it.message}")
                        isLoading = false
                    }
            } catch (e: Exception) {
                isLoading = false
            }
        } else if (isPreview) {
            listaCompletaProductos = cargaBase?.plantillaProductos ?: emptyList()
        }
    }

    // --- CÁLCULOS ---
    val totalCantidad = listaCompletaProductos.sumOf { it.cantidad }
    val totalValor = listaCompletaProductos.sumOf { it.cantidad * it.precio }

    // --- INTERFAZ (UI) ---
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Cabecera
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Icono de regresar a la izquierda
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = Color(0xFFFF0000))
            }

            // Column central para título y fecha
            Column(
                modifier = Modifier.align(Alignment.Center)
            ) {
                // Título centrado
                Text(
                    text = "Detalle de transferencia",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth() // esto permite que el texto se centre en la pantalla
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Fecha alineada a la izquierda dentro del Column
                cargaBase?.fecha?.let { fecha ->
                    val formatoFecha = java.text.SimpleDateFormat(
                        "EEEE, dd 'de' MMMM",
                        Locale("es", "MX")
                    )
                    Text(
                        text = formatoFecha.format(fecha).replaceFirstChar { it.uppercase() },
                        fontSize = 17.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.End, // Alineada a la izquierda
                        modifier = Modifier.fillMaxWidth() // asegura que la alineación funcione
                    )
                }
            }
        }







        Divider(color = Color.LightGray)

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFF0000))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(listaCompletaProductos) { producto ->
                    val totalProducto = producto.cantidad * producto.precio
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = producto.imagenUrl,
                                placeholder = painterResource(R.drawable.repartidor),
                                error = painterResource(R.drawable.repartidor),
                                contentDescription = producto.nombre,
                                modifier = Modifier.size(80.dp).padding(end = 12.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(producto.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Precio: $${producto.precio}", fontSize = 14.sp)
                                Text("Cantidad: ${producto.cantidad}", fontSize = 14.sp)
                            }
                            Text("$${"%.2f".format(totalProducto)}", fontWeight = FontWeight.Bold, color = Color(0xFFFF0000))
                        }
                    }
                }
            }
        }

        // Totales y Botones
        if (!isLoading) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Divider(color = Color.LightGray)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Total Piezas:", fontWeight = FontWeight.Bold)
                        Text("Valor Total:", fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text("$totalCantidad", fontWeight = FontWeight.Bold, color = Color(0xFFFF0000))
                        Text("$${"%.2f".format(totalValor)}", fontWeight = FontWeight.Bold, color = Color(0xFFFF0000))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (cargaBase?.aceptada == false) {
                    Button(
                        onClick = { mensajeDialog = "¿Aceptar carga?"; mostrarDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp), // 👈 menos redondeado
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))
                    ) { Text("Aceptar Carga", color = Color.White) }
                }
            }
        }
    }

    // Diálogo de Confirmación
    if (mostrarDialog) {
        DialogoConfirmacion(
            titulo = "Confirmación",
            mensaje = mensajeDialog,
            textoConfirmar = "Aceptar",
            textoCancelar = "Cancelar",
            colorConfirmar = Color.Red,
            onConfirmar = {
                mostrarDialog = false
                if (!isPreview) {
                    FirebaseFirestore.getInstance().collection("ordenesTransferencia")
                        .document(cargaBase?.id ?: "")
                        .update("estado", "ACEPTADA")
                        .addOnSuccessListener { navController.popBackStack() }
                }
            },
            onCancelar = { mostrarDialog = false }
        )
    }

}

@Preview(showBackground = true)
@Composable
fun PantallaDetalleCargaPreview() {
    val productosEjemplos = listOf(
        Plantilla_Producto(id = "1", nombre = "Pepita Frita", cantidad = 7, precio = 13.0, imagenUrl = ""),
        Plantilla_Producto(id = "2", nombre = "Botana Mix", cantidad = 5, precio = 12.5, imagenUrl = "")
    )
    val cargaEjemplo = Plantila_carga(id = "123", plantillaProductos = productosEjemplos, nombreCarga = "Carga de Almacén", aceptada = false)
    val navController = rememberNavController()
    PantallaDetalleCarga(navController, cargaEjemplo)
}