package com.gruposanangel.delivery.ui.screens

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

@Composable
fun PantallaDetalleCarga(
    navController: NavController,
    plantilacarga: Plantila_carga? = null // se puede recibir null si se usa SavedStateHandle
) {



    // Si se pasa null, intentar obtener desde SavedStateHandle
    val cargaReal = plantilacarga ?: navController.currentBackStackEntry
        ?.savedStateHandle
        ?.get<Plantila_carga>("carga") ?: return // si no hay carga, no mostrar nada

    var mostrarDialog by remember { mutableStateOf(false) }
    var mensajeDialog by remember { mutableStateOf("") }

    val totalCantidad = cargaReal.plantillaProductos.sumOf { it.cantidad }
    val totalValor = cargaReal.plantillaProductos.sumOf { it.cantidad * it.precio }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {

        // Encabezado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Regresar",
                    tint = Color(0xFFFF0000)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = cargaReal.nombreCarga,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
        }
        Divider(color = Color.LightGray)

        // Lista de productos
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cargaReal.plantillaProductos) { producto ->
                val totalProducto = producto.cantidad * producto.precio
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = producto.imagenUrl,
                            placeholder = painterResource(R.drawable.repartidor),
                            error = painterResource(R.drawable.repartidor),
                            contentDescription = producto.nombre,
                            modifier = Modifier
                                .size(80.dp)
                                .padding(end = 12.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = producto.nombre,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Precio: $${producto.precio}",
                                fontSize = 14.sp,
                                color = Color(0xFF555555)
                            )
                            Text(
                                text = "Cantidad: ${producto.cantidad}",
                                fontSize = 14.sp,
                                color = Color(0xFF555555)
                            )
                        }
                        Text(
                            text = "$${"%.2f".format(totalProducto)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFFFF0000)
                        )
                    }
                }
            }
        }

        // Totales
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Divider(color = Color.LightGray)
            Spacer(modifier = Modifier.height(8.dp))

            // Una sola fila con dos columnas: títulos (a la izquierda) y valores (a la derecha),
            // pero los títulos están alineados hacia la derecha para quedar pegados a los valores.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Columna de títulos: ocupa el espacio disponible y alinea su contenido a la derecha
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp), // separa un poco del valor
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Total Piezas:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Valor Total:", fontWeight = FontWeight.Bold)
                }

                // Columna de valores: ancho ajustado al contenido y los textos alineados a la derecha
                Column(
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(start = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("$totalCantidad", fontWeight = FontWeight.Bold, color = Color(0xFFFF0000), textAlign = TextAlign.End)

                    Spacer(modifier = Modifier.height(8.dp))

                    val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).apply {
                        minimumFractionDigits = 2
                        maximumFractionDigits = 2
                    }
                    Text("$${nf.format(totalValor)}", fontWeight = FontWeight.Bold, color = Color(0xFFFF0000), textAlign = TextAlign.End)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))




            if (!cargaReal.aceptada) { // <-- solo mostrar si no ha sido aceptada

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Botón Aceptar
                    Button(
                        onClick = {
                            mensajeDialog = "¿Deseas aceptar la carga?"
                            mostrarDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Aceptar", color = Color.White)
                    }



                    // Botón Rechazar
                    Button(
                        onClick = { navController.popBackStack() }, // Navega hacia atrás directamente,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE0E0E0),
                            contentColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Rechazar")
                    }
                }

            }


            // Dialogo de confirmación
            if (mostrarDialog) {
                AlertDialog(
                    onDismissRequest = { mostrarDialog = false },
                    title = { Text("Confirmación") },
                    text = { Text(mensajeDialog) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                mostrarDialog = false
                                // ✅ Actualiza Firestore y navega solo después de confirmar
                                val db = FirebaseFirestore.getInstance()
                                db.collection("ordenesTransferencia")
                                    .document(cargaReal.id)
                                    .update("estado", "ACEPTADA")
                                    .addOnSuccessListener {
                                        navController.popBackStack()
                                    }
                                    .addOnFailureListener { e ->
                                        // Opcional: mostrar error en otro diálogo o Snackbar
                                    }
                            }
                        ) {
                            Text("Aceptar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { mostrarDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
fun PantallaDetalleCargaPreview() {
    val productosEjemplos = listOf(
        Plantilla_Producto(id = "1", nombre = "Botana Mix", cantidad = 5, precio = 12.5, imagenUrl = ""),
        Plantilla_Producto(id = "2", nombre = "Refresco Cola", cantidad = 3, precio = 18.0, imagenUrl = ""),
        Plantilla_Producto(id = "3", nombre = "Pan de Caja", cantidad = 2, precio = 25.0, imagenUrl = "")
    )

    val cargaEjemplo = Plantila_carga(
        id = "123",
        plantillaProductos = productosEjemplos,
        nombreCarga = "Carga del Día"
    )

    val navController = rememberNavController()
    PantallaDetalleCarga(navController, cargaEjemplo)
}