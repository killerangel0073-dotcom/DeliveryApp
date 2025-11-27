package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.ClienteEntity
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaRepository
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.utilidades.GeneraPDFdeVenta
import com.gruposanangel.delivery.utilidades.ImprimirTicket58mm
import com.gruposanangel.delivery.utilidades.hayInternet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PantallaVentas2(
    navController: NavController,
    clienteId: String,
    repository: RepositoryCliente? = null,
    inventarioRepo: RepositoryInventario? = null,
    impresoraBluetooth: BluetoothDevice? = null,
    productosPreview: List<Plantilla_Producto>? = null
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 1. Inicialización del ViewModel (VentaViewModel)
    val db = AppDatabase.getDatabase(context)
    val ventaViewModel: VentaViewModel = if (!isPreview) {
        val repoInventarioReal = inventarioRepo ?: RepositoryInventario(db.productoDao())
        val repoVentaReal = VentaRepository(db.VentaDao())
        viewModel(factory = VentaViewModelFactory(repoInventarioReal, repoVentaReal))
    } else {
        // Mock simple para preview
        viewModel(factory = VentaViewModelFactory(RepositoryInventario(db.productoDao()), VentaRepository(db.VentaDao())))
    }

    // 2. Estados observados desde el ViewModel
    val productosEnCarrito = ventaViewModel.productosEnCarrito
    val estadoRuta by ventaViewModel.estadoRuta.collectAsState()
    val procesandoVenta by ventaViewModel.estaProcesando.collectAsState()

    // 3. Carga inicial de datos
    val cliente = remember { mutableStateOf<ClienteEntity?>(null) }

    // Cargar Cliente
    LaunchedEffect(clienteId) {
        if (isPreview) {
            cliente.value = ClienteEntity(
                id = clienteId, nombreNegocio = "Negocio Preview", nombreDueno = "Juan Pérez",
                telefono = "", correo = "", tipoExhibidor = "", ubicacionLat = 0.0,
                ubicacionLon = 0.0, fotografiaUrl = null, activo = true, medio = "",
                fechaDeCreacion = System.currentTimeMillis(), syncStatus = true
            )
        } else {
            cliente.value = repository?.obtenerClientesLocalPorId(clienteId)
        }
    }

    // Cargar Ruta
    LaunchedEffect(Unit) {
        if (!isPreview) {
            ventaViewModel.verificarRutaAsignada()
        }
    }

    // -------------------------------------------------------------------------
    // CORRECCIÓN AQUÍ: Manejo explícito de tipos para evitar el error "Unresolved reference"
    // -------------------------------------------------------------------------

    // Paso A: Obtener los datos crudos de la base de datos (Si no es preview)
    val entidadesBD = if (!isPreview) {
        inventarioRepo?.obtenerProductosLocal()?.collectAsState(initial = emptyList())?.value ?: emptyList()
    } else {
        emptyList()
    }

    // Paso B: Unificar todo en una lista de 'Plantilla_Producto'
    val productosListosParaCargar = remember(entidadesBD, productosPreview, isPreview) {
        if (isPreview) {
            productosPreview ?: emptyList()
        } else {
            // Aquí transformamos 'ProductoEntity' a 'Plantilla_Producto'
            // Ahora Kotlin sabe exactamente qué campos tiene 'entidad'
            entidadesBD.map { entidad ->
                Plantilla_Producto(
                    id = entidad.id,
                    nombre = entidad.nombre,
                    precio = entidad.precio,
                    cantidad = 0,
                    cantidadDisponible = entidad.cantidadDisponible,
                    imagenUrl = entidad.imagenUrl ?: ""
                )
            }
        }
    }

    // Paso C: Enviar al ViewModel
    LaunchedEffect(productosListosParaCargar) {
        if (productosListosParaCargar.isNotEmpty()) {
            ventaViewModel.cargarProductosIniciales(productosListosParaCargar)
        }
    }
    // -------------------------------------------------------------------------


    val totalGeneral by remember { derivedStateOf { productosEnCarrito.sumOf { it.precio * it.cantidad } } }
    val formatoMoneda = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("es", "MX"))
    var mostrarDialog by remember { mutableStateOf(false) }

    // -------- UI Principal --------
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {

        LazyColumn(
            contentPadding = PaddingValues(top = 16.dp, bottom = 140.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Cliente
            item {
                CardInfoCliente2(cliente.value)
            }

            // Estados de Carga / Error / Lista
            when (val estado = estadoRuta) {
                is EstadoRuta.Cargando -> {
                    item { LoadingView2() }
                }
                is EstadoRuta.SinRuta -> {
                    item { MessageView2("Usuario sin ruta asignada o sin permisos") }
                }
                is EstadoRuta.Error -> {
                    item { MessageView2("Error: ${estado.mensaje}") }
                }
                is EstadoRuta.ConRuta -> {
                    if (productosEnCarrito.isEmpty()) {
                        item { MessageView2("Cargando inventario...") }
                    } else {
                        // Lista de Productos Optimizada
                        itemsIndexed(
                            items = productosEnCarrito,
                            key = { _, item -> item.id }
                        ) { index, producto ->
                            ItemProductoVenta2(
                                producto = producto,
                                isPreview = isPreview,
                                formatoMoneda = formatoMoneda,
                                onSumar = {
                                    if (producto.cantidad < producto.cantidadDisponible) {
                                        ventaViewModel.actualizarCantidad(index, producto.cantidad + 1)
                                    } else {
                                        scope.launch { snackbarHostState.showSnackbar("No hay suficiente inventario") }
                                    }
                                },
                                onRestar = {
                                    if (producto.cantidad > 0) {
                                        ventaViewModel.actualizarCantidad(index, producto.cantidad - 1)
                                    }
                                },
                                onCantidadCambiada = { nueva ->
                                    ventaViewModel.actualizarCantidad(index, nueva)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Footer Total
        CardTotal2(
            total = totalGeneral,
            formato = formatoMoneda,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Botón Flotante Venta
        if (!procesandoVenta) {
            FloatingActionButton(
                onClick = { if (!isPreview) mostrarDialog = true },
                containerColor = Color(0xFFFF0000),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 6.dp)
            ) {
                Icon(Icons.Default.ReceiptLong, contentDescription = "Finalizar venta")
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)
        )

        // Dialog Confirmación
        if (mostrarDialog) {
            val productosParaVenta = productosEnCarrito.filter { it.cantidad > 0 }

            AlertDialog(
                onDismissRequest = { mostrarDialog = false },
                containerColor = Color.White,
                title = { Text("Confirmación", color = Color.Red, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
                text = { Text("¿Deseas realizar la venta de ${productosParaVenta.size} productos?", fontSize = 18.sp, textAlign = TextAlign.Center) },
                confirmButton = {
                    Button(
                        onClick = {
                            if (productosParaVenta.isEmpty()) {
                                scope.launch { snackbarHostState.showSnackbar("Seleccione al menos un producto") }
                                return@Button
                            }
                            mostrarDialog = false

                            // INICIO PROCESO DE VENTA (Delegado al ViewModel)
                            ventaViewModel.procesarVenta(
                                clienteId = cliente.value?.id ?: "",
                                clienteNombre = cliente.value?.nombreDueno ?: "Cliente",
                                clienteFotoUrl = cliente.value?.fotografiaUrl,
                                metodoPago = "Efectivo",
                                hayInternet = hayInternet(context)
                            ) { exito, mensaje, ventaLocalId ->

                                scope.launch {
                                    snackbarHostState.showSnackbar(mensaje)
                                    Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show()
                                }

                                if (exito) {
                                    // 1. Generar PDF
                                    generarYAbrirPDF2(context, cliente.value?.nombreDueno ?: "", productosParaVenta, totalGeneral, scope, snackbarHostState)

                                    // 2. Imprimir Ticket
                                    impresoraBluetooth?.let { device ->
                                        imprimirTicket2(context, device, cliente.value?.nombreDueno ?: "", productosParaVenta, scope, snackbarHostState)
                                    } ?: run {
                                        scope.launch { snackbarHostState.showSnackbar("No hay impresora conectada") }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("CONFIRMAR", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { mostrarDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0), contentColor = Color.Gray),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("CANCELAR", fontWeight = FontWeight.Bold) }
                }
            )
        }
    }
}

// ---------------- Componentes de UI (Renombrados con '2' para evitar colisiones) ----------------

@Composable
fun CardInfoCliente2(cliente: ClienteEntity?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (cliente?.fotografiaUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(cliente.fotografiaUrl).crossfade(true).build(),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(CircleShape).border(2.dp, Color.Gray, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.LightGray).padding(8.dp), tint = Color.DarkGray)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(cliente?.nombreDueno ?: "Cargando...", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(cliente?.nombreNegocio ?: "", fontSize = 16.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun ItemProductoVenta2(
    producto: Plantilla_Producto,
    isPreview: Boolean,
    formatoMoneda: java.text.NumberFormat,
    onSumar: () -> Unit,
    onRestar: () -> Unit,
    onCantidadCambiada: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            if (isPreview) {
                Image(painter = painterResource(R.drawable.repartidor), contentDescription = null, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)))
            } else {
                AsyncImage(model = producto.imagenUrl, contentDescription = null, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(producto.nombre, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Precio: $${producto.precio}", fontSize = 14.sp, color = Color(0xFF555555))
                Spacer(modifier = Modifier.height(14.dp))
                Text("Subtotal: ${formatoMoneda.format(producto.precio * producto.cantidad)}", fontSize = 14.sp, color = Color(0xFFFF0000))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ContadorCantidad2(producto.cantidad, onSumar, onRestar, onCantidadCambiada)
                Spacer(modifier = Modifier.height(10.dp))
                Text("${producto.cantidadDisponible}", fontSize = 14.sp, color = Color(0xFFFF0000), fontWeight = FontWeight.Bold)
                Text("Disponible", fontSize = 12.sp, color = Color(0xFF555555))
            }
        }
    }
}

@Composable
fun CardTotal2(total: Double, formato: java.text.NumberFormat, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier.fillMaxWidth().height(60.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total General:", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
            Spacer(modifier = Modifier.width(8.dp))
            Text(formato.format(total), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0000FF))
        }
    }
}




@Composable
fun LoadingView2() {
    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.Red)
    }
}

@Composable
fun MessageView2(msg: String) {
    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Text(msg, fontSize = 18.sp, color = Color.Gray, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContadorCantidad2(cantidad: Int, onSumar: () -> Unit, onRestar: () -> Unit, onCantidadCambiada: (Int) -> Unit = {}) {
    var textoCantidad by remember(cantidad) { mutableStateOf(cantidad.toString()) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { onRestar() }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Remove, "Restar", tint = Color(0xFFD32F2F), modifier = Modifier.size(24.dp))
        }
        BasicTextField(
            value = textoCantidad,
            onValueChange = { nuevo ->
                val num = nuevo.filter { it.isDigit() }
                textoCantidad = if (num.isEmpty()) "0" else num
                onCantidadCambiada(textoCantidad.toInt())
            },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.width(65.dp).height(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF0000)).padding(vertical = 4.dp)
        )
        IconButton(onClick = onSumar, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, "Sumar", tint = Color(0xFF388E3C), modifier = Modifier.size(24.dp))
        }
    }
}

// ---------------- Utilidades ----------------

fun generarYAbrirPDF2(
    context: android.content.Context,
    clienteNombre: String,
    productos: List<Plantilla_Producto>,
    total: Double,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    scope.launch(Dispatchers.IO) {
        try {
            val pdfFile = GeneraPDFdeVenta(context, clienteNombre, productos, total)
            val pdfUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(pdfUri, "application/pdf")
                flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                snackbarHostState.showSnackbar("No se pudo abrir el PDF")
            }
        }
    }
}

fun imprimirTicket2(
    context: android.content.Context,
    device: BluetoothDevice,
    clienteNombre: String,
    productos: List<Plantilla_Producto>,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    scope.launch(Dispatchers.IO) {
        try {
            ImprimirTicket58mm(device = device, context = context, logoDrawableId = R.drawable.logo, cliente = clienteNombre, productos = productos)
            withContext(Dispatchers.Main) {
                snackbarHostState.showSnackbar("Ticket impreso correctamente")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                snackbarHostState.showSnackbar("Error impresión: ${e.message}")
            }
        }
    }
}


@Composable
fun PantallaVentas2PreviewContent(productosPreview: List<Plantilla_Producto>) {

    val productos = remember { mutableStateListOf(*productosPreview.toTypedArray()) }

    // Total
    val totalGeneral = productos.sumOf { it.cantidad * it.precio }
    val formatoMoneda = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("es", "MX"))

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                CardInfoCliente2(
                    ClienteEntity(
                        id = "preview",
                        nombreNegocio = "Negocio Preview",
                        nombreDueno = "Juan Pérez",
                        telefono = "", correo = "", tipoExhibidor = "",
                        ubicacionLat = 0.0, ubicacionLon = 0.0,
                        fotografiaUrl = null, activo = true,
                        medio = "", fechaDeCreacion = 0L, syncStatus = true
                    )
                )
            }

            itemsIndexed(productos) { index, producto ->
                ItemProductoVenta2(
                    producto = producto,
                    isPreview = true,
                    formatoMoneda = formatoMoneda,
                    onSumar = { productos[index] = productos[index].copy(cantidad = productos[index].cantidad + 1) },
                    onRestar = {
                        if (productos[index].cantidad > 0)
                            productos[index] = productos[index].copy(cantidad = productos[index].cantidad - 1)
                    },
                    onCantidadCambiada = { nueva ->
                        productos[index] = productos[index].copy(cantidad = nueva)
                    }
                )
            }
        }

        CardTotal2(
            total = totalGeneral,
            formato = formatoMoneda,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}



@Preview(showBackground = true)
@Composable
fun PreviewPantallaVentas2() {
    PantallaVentas2PreviewContent(
        productosPreview = listOf(
            Plantilla_Producto("1", "Coca Cola", 15.0, 0, 10, ""),
            Plantilla_Producto("2", "Galletas", 12.0, 0, 8, ""),
            Plantilla_Producto("3", "Agua 600ml", 8.0, 0, 20, "")
        )
    )
}


