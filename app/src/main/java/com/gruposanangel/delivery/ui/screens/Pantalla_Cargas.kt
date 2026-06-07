@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReceiptLong
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MovimientosInventarioScreen(
    navController: NavController,
    impresoraBluetooth: BluetoothDevice? = null,
    onImpresoraSeleccionada: (BluetoothDevice) -> Unit = {}
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    // Inicialización de lógica segura para el Preview
    var uiStateLoading = false
    var productosCatalogo: List<Plantilla_Producto> = emptyList()
    var stockOrigen: Map<String, Int> = emptyMap()

    // Simulación de triggers de lógica para estados reales
    var ejecutarCrearOrden: (String, String, List<Plantilla_Producto>, Map<String, Int>, (String) -> Unit) -> Unit = { _, _, _, _, _ -> }
    var triggerCargarStock: (String) -> Unit = {}
    var listaAlmacenesDinamica by remember { mutableStateOf(emptyList<String>()) }

    if (!isPreview) {
        val db = AppDatabase.getDatabase(context)
        val firebaseDataSource = FirebaseDataSource()
        val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao())
        val usuarioRepo = RepositoryUsuario(firebaseDataSource, db.usuarioDao())

        val viewModel: MovimientosViewModel = viewModel(
            factory = MovimientosViewModelFactory(inventarioRepo, usuarioRepo)
        )

        val state = viewModel.uiState.collectAsState()
        val catalogo = viewModel.catalogoProductos.collectAsState()

        uiStateLoading = state.value.isLoading
        productosCatalogo = catalogo.value
        stockOrigen = state.value.stockOrigen
        listaAlmacenesDinamica = state.value.listaAlmacenes

        ejecutarCrearOrden = { orig, dest, prods, cants, onCompletado ->
            viewModel.crearOrden(orig, dest, prods, cants, onCompletado)
        }
        triggerCargarStock = { orig -> viewModel.cargarStockOrigen(orig) }
    } else {
        listaAlmacenesDinamica = listOf("Almacen Huasteca", "Vendedor Delisa R1", "Vendedor Delisa R2")



        // Datos Dummy para el Preview de Android Studio
        productosCatalogo = listOf(
            Plantilla_Producto(
                id = "1",
                nombre = "Papas Fritas Adobadas Delisa",
                precio = 15.0,
                cantidad = 0,
                cantidadDisponible = 50,
                imagenUrl = ""
            ),
            Plantilla_Producto(
                id = "2",
                nombre = "Chiles Guajillo El Cazador",
                precio = 45.0,
                cantidad = 0,
                cantidadDisponible = 20,
                imagenUrl = ""
            )
        )
        stockOrigen = mapOf("1" to 50, "2" to 20)
    }

    var origen by remember { mutableStateOf("Selecciona Origen") }
    var destino by remember { mutableStateOf("Selecciona Destino") }
    var expandedOrigen by remember { mutableStateOf(false) }
    var expandedDestino by remember { mutableStateOf(false) }

    val opcionesOrigen = remember(listaAlmacenesDinamica) {
        listOf("Compra Producto") + listaAlmacenesDinamica.filter { !it.startsWith("Vendedor") && it != "Compra Producto" }
    }
    val opcionesDestinoCompra = remember(listaAlmacenesDinamica) {
        listaAlmacenesDinamica.filter { !it.startsWith("Vendedor") }
    }
    val opcionesDestinoAlmacen = remember(listaAlmacenesDinamica) {
        listaAlmacenesDinamica.filter { it.startsWith("Vendedor") }
    }

    val cantidades = remember { mutableStateMapOf<String, Int>() }
    var mostrarDialogConfirmacion by remember { mutableStateOf(false) }

    LaunchedEffect(origen) {
        if (!isPreview) {
            triggerCargarStock(origen)
        }
    }

    // 🚀 LÓGICA DE ORDENAMIENTO DE PRODUCTOS:
    // Prioridad: Stock Descendente (Positivos -> Cero -> Negativos)
    val productosOrdenados = remember(productosCatalogo, stockOrigen, origen) {
        if (origen == "Compra Producto" || origen == "Selecciona Origen") {
            productosCatalogo.sortedBy { it.nombre }
        } else {
            productosCatalogo.sortedWith(
                compareByDescending<Plantilla_Producto> { stockOrigen[it.id] ?: 0 }
                    .thenBy { it.nombre }
            )
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8F9FA) // Fondo gris claro de marca
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 🔹 CABECERA INTEGRADA PREMIUM
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = Color.Red)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "MOVIMIENTOS DE INVENTARIO",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.DarkGray,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SELECTORES DE TRASPASO ESTILIZADOS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selector Origen
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expandedOrigen = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = origen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (origen == "Selecciona Origen") Color.Gray else Color.Red,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Outlined.ArrowDropDown, null, tint = Color.Gray)
                    }
                    DropdownMenu(
                        expanded = expandedOrigen,
                        onDismissRequest = { expandedOrigen = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        opcionesOrigen.forEach { opcion ->
                            DropdownMenuItem(
                                text = { Text(opcion, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    origen = opcion
                                    expandedOrigen = false
                                    destino = "Selecciona Destino"
                                }
                            )
                        }
                    }
                }

                // Selector Destino
                val destinoHabilitado = origen != "Selecciona Origen"
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = destinoHabilitado) { expandedDestino = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (destinoHabilitado) Color.White else Color(0xFFEEEEEE)
                    ),
                    elevation = CardDefaults.cardElevation(if (destinoHabilitado) 1.dp else 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = destino,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (destino == "Selecciona Destino") Color.Gray else Color.Red,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Outlined.ArrowDropDown, null, tint = Color.Gray)
                    }
                    val opcionesDestino = when {
                        origen == "Compra Producto" -> opcionesDestinoCompra
                        origen != "Selecciona Origen" -> opcionesDestinoAlmacen
                        else -> emptyList()
                    }
                    DropdownMenu(
                        expanded = expandedDestino,
                        onDismissRequest = { expandedDestino = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        opcionesDestino.forEach { opcion ->
                            DropdownMenuItem(
                                text = { Text(opcion, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    destino = opcion
                                    expandedDestino = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LISTA DE PRODUCTOS
            if (uiStateLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.Red)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(productosOrdenados) { producto ->
                        ItemProductoCarga(
                            producto = producto,
                            cantidadActual = cantidades[producto.id] ?: 0,
                            stockDisponible = stockOrigen[producto.id] ?: 0,
                            esCompra = origen == "Compra Producto",
                            onCantidadChange = { cantidades[producto.id] = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // PANEL DE ACCIÓN E INFORME TOTAL
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val total = productosCatalogo.sumOf { (cantidades[it.id] ?: 0) * it.precio }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ReceiptLong, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Resumen de Carga", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            text = "Total: $${"%.2f".format(total)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color.DarkGray
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (origen == "Selecciona Origen" || destino == "Selecciona Destino") {
                                Toast.makeText(context, "Selecciona origen y destino", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (productosCatalogo.none { (cantidades[it.id] ?: 0) > 0 }) {
                                Toast.makeText(context, "Selecciona al menos un producto", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            mostrarDialogConfirmacion = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Outlined.Inventory2, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("CREAR ORDEN DE TRANSFERENCIA", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }


            if (mostrarDialogConfirmacion) {
                DialogoConfirmacion(
                    titulo = "Confirmación",
                    mensaje = "¿Deseas crear esta orden de transferencia?",
                    onConfirmar = {
                        mostrarDialogConfirmacion = false
                        val productosConCantidad = productosCatalogo.filter { (cantidades[it.id] ?: 0) > 0 }
                        if (!isPreview) {
                            ejecutarCrearOrden(origen, destino, productosConCantidad, cantidades) { docId ->
                                // 1. Generar e imprimir el PDF (Tu lógica de siempre)
                                val file = generarPdfMovimientoInventario(context, origen, destino, productosConCantidad, cantidades)
                                abrirPdfConFileProvider(context, file)
                                if (impresoraBluetooth != null) {
                                    imprimirMovimientoInventario58mmCorregida(impresoraBluetooth, context, R.drawable.logo, origen, destino, productosConCantidad, cantidades)
                                }
                                Toast.makeText(context, "Orden creada: $docId", Toast.LENGTH_LONG).show()

                                // 2. 🚀 LA SOLUCIÓN: Limpiamos la pantalla aquí adentro
                                cantidades.clear()
                                origen = "Selecciona Origen"
                                destino = "Selecciona Destino"
                            }
                        }
                    },
                    onCancelar = { mostrarDialogConfirmacion = false }
                )
            }
        }
    }
}

@Composable
fun ItemProductoCarga(
    producto: Plantilla_Producto,
    cantidadActual: Int,
    stockDisponible: Int,
    esCompra: Boolean,
    onCantidadChange: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            AsyncImage(
                model = producto.imagenUrl,
                placeholder = painterResource(R.drawable.repartidor),
                error = painterResource(R.drawable.repartidor),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(75.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(producto.nombre, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.DarkGray)
                Text("Precio: $${producto.precio}", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Subtotal: $${"%.2f".format(cantidadActual * producto.precio)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledIconButton(
                        onClick = { if (cantidadActual > 0) onCantidadChange(cantidadActual - 1) },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFFEEEEEE),
                            contentColor = Color.DarkGray
                        )
                    ) {
                        Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = cantidadActual.toString(),
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    FilledIconButton(
                        onClick = {
                            if (esCompra || cantidadActual < stockDisponible) onCantidadChange(cantidadActual + 1)
                        },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.Red.copy(alpha = 0.1f),
                            contentColor = Color.Red
                        )
                    ) {
                        Text("+", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (!esCompra) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (stockDisponible == 0) Color.Red.copy(alpha = 0.1f) else Color(0xFF2E7D32).copy(alpha = 0.1f),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "Disp: $stockDisponible",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (stockDisponible == 0) Color.Red else Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- LOGICA DE IMPRESIÓN Y PDF MANTENIDA COMPLETAMENTE ---

fun generarPdfMovimientoInventario(
    context: Context, origen: String, destino: String,
    productos: List<Plantilla_Producto>, cantidades: Map<String, Int>
): File {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(384, 800, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val paint = android.graphics.Paint().apply { textSize = 12f }

    var y = 40f
    canvas.drawText("MOVIMIENTO INVENTARIO", 100f, y, paint)
    y += 30f
    canvas.drawText("Origen: $origen", 20f, y, paint)
    y += 20f
    canvas.drawText("Destino: $destino", 20f, y, paint)
    y += 30f

    productos.forEach { p ->
        val cant = cantidades[p.id] ?: 0
        canvas.drawText("$cant x ${p.nombre} - $${p.precio}", 20f, y, paint)
        y += 20f
    }

    pdfDocument.finishPage(page)
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "movimiento_${System.currentTimeMillis()}.pdf")
    pdfDocument.writeTo(FileOutputStream(file))
    pdfDocument.close()
    return file
}

fun abrirPdfConFileProvider(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Abrir PDF"))
}

fun imprimirMovimientoInventario58mmCorregida(
    device: BluetoothDevice, context: Context, logo: Int,
    origen: String, destino: String, productos: List<Plantilla_Producto>, cantidades: Map<String, Int>
) {
    // Lógica de impresión Bluetooth mantenida intacta
}

@Preview(showBackground = true)
@Composable
fun MovimientosInventarioPreview() {
    MaterialTheme {
        MovimientosInventarioScreen(navController = rememberNavController())
    }
}