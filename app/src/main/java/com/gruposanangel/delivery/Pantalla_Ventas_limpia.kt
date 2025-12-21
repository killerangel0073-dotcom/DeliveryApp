package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
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
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion



@Composable
fun PantallaVentas2(
    navController: NavController,
    clienteId: String,
    repository: RepositoryCliente? = null,
    inventarioRepo: RepositoryInventario? = null,
    impresoraBluetooth: BluetoothDevice? = null,
    productosPreview: List<Plantilla_Producto>? = null
) {

    val mostrandoSnackbar = remember { mutableStateOf(false) }

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

        // 1️⃣ Header fijo arriba y clicable
        val alturaHeader = 120.dp
        InfoClienteCompacta(
            cliente = cliente.value,
            navController = navController, // Pasamos el NavController
            modifier = Modifier
                .fillMaxWidth()
                .height(alturaHeader)
                .align(Alignment.TopStart)
                .zIndex(1f) // ✅ asegura que esté encima de la LazyColumn
                .background(Color.White) // opcional: para tapar la LazyColumn debajo
        )








        // 2️⃣ Lista de productos
        LazyColumn(
            contentPadding = PaddingValues(
                top = alturaHeader + 8.dp, // dejar espacio para el header
                bottom = 140.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {


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
                                        if (!mostrandoSnackbar.value) {
                                            mostrandoSnackbar.value = true
                                            scope.launch {
                                                snackbarHostState.showSnackbar("No hay suficiente inventario")
                                                mostrandoSnackbar.value = false
                                            }
                                        }
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




        // 3️⃣ Total fijo abajo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            LineaResplandorRoja()
            CardTotal2(total = totalGeneral, formato = formatoMoneda, modifier = Modifier.fillMaxWidth())
        }


        // 4️⃣ Botón flotante
        var botonBloqueado by remember { mutableStateOf(false) }

        FloatingActionButton(
            onClick = {
                val productosParaVenta = productosEnCarrito.filter { it.cantidad > 0 }
                if (productosParaVenta.isEmpty()) {
                    if (!botonBloqueado) {
                        // Mostrar mensaje solo si no está bloqueado
                        scope.launch {
                            snackbarHostState.showSnackbar("Seleccione al menos un producto")
                        }
                        botonBloqueado = true
                        // Desbloquear después de 2 segundos
                        scope.launch {
                            kotlinx.coroutines.delay(2000)
                            botonBloqueado = false
                        }
                    }
                } else {
                    // Abrir diálogo solo si hay productos
                    mostrarDialog = true
                }
            },
            containerColor = Color(0xFFFF0000),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 6.dp)
        ) {
            Icon(Icons.Default.ReceiptLong, contentDescription = "Finalizar venta")
        }






        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)
        )

        // Dialog Confirmación
        if (mostrarDialog) {
            val productosParaVenta = productosEnCarrito.filter { it.cantidad > 0 }

            DialogoConfirmacion(
                titulo = "Confirmación",
                mensaje = "¿Deseas realizar la venta para \n\n${cliente.value?.nombreNegocio ?: "Cliente"} \npor  ${formatoMoneda.format(totalGeneral)}?",

                textoConfirmar = "CONFIRMAR",
                textoCancelar = "CANCELAR",
                colorConfirmar = Color.Red,
                onConfirmar = {
                    if (productosParaVenta.isEmpty()) {
                        scope.launch { snackbarHostState.showSnackbar("Seleccione al menos un producto") }
                        return@DialogoConfirmacion
                    }
                    mostrarDialog = false

                    // INICIO PROCESO DE VENTA (delegado al ViewModel)
                    ventaViewModel.procesarVenta(
                        clienteId = cliente.value?.id ?: "",
                        clienteNombre = cliente.value?.nombreNegocio ?: "Negocio",
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
                            generarYAbrirPDF2(
                                context,
                                cliente.value?.nombreNegocio ?: "Negocio",
                                productosParaVenta,
                                totalGeneral,
                                scope,
                                snackbarHostState
                            )

                            // 2. Imprimir Ticket
                            impresoraBluetooth?.let { device ->
                                imprimirTicket2(
                                    context,
                                    device,
                                    cliente.value?.nombreNegocio ?: "Negocio",
                                    productosParaVenta,
                                    scope,
                                    snackbarHostState
                                )
                            } ?: run {
                                scope.launch { snackbarHostState.showSnackbar("No hay impresora conectada") }
                            }
                        }
                    }
                },
                onCancelar = { mostrarDialog = false }
            )
        }

    }
}

// ---------------- Componentes de UI (Renombrados con '2' para evitar colisiones) ----------------


@Composable
fun LineaResplandorRoja() {
    val infiniteTransition = rememberInfiniteTransition()
    // Animación de desplazamiento horizontal
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -300f, // inicia fuera de la pantalla
        targetValue = 300f,   // termina al otro lado
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.Red) // línea base
    ) {
        // Capa de resplandor animada
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(100.dp) // ancho del resplandor
                .offset(x = offsetX.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )
    }
}


@Composable
fun InfoClienteCompacta(
    cliente: ClienteEntity?,
    modifier: Modifier = Modifier,
    navController: NavController? = null // Pasamos el NavController
) {
    var mostrarImagenGrande by remember { mutableStateOf(false) }

    Column(modifier = modifier) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // Foto del cliente
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { mostrarImagenGrande = true }
            ) {
                if (!cliente?.fotografiaUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = cliente!!.fotografiaUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = Color.DarkGray)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Textos del cliente con peso y overflow
            Column(
                modifier = Modifier
                    .weight(1f) // ocupa el espacio disponible sin empujar al icono
            ) {
                Text(
                    cliente?.nombreNegocio ?: "",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    cliente?.nombreDueno ?: "Cargando...",
                    fontSize = 18.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Icono de clientes con tamaño fijo
            Box(
                modifier = Modifier.size(40.dp), // tamaño fijo para que no se comprima
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        navController?.navigate("delivery?screen=Clientes") {
                            launchSingleTop = true
                            popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        }
                    },
                    modifier = Modifier.fillMaxSize() // llena el box para que sea clicable
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "Ir a Clientes",
                        tint = Color.Red,
                        modifier = Modifier.fillMaxSize() // el ícono ocupa todo el botón
                    )
                }
            }
        }


        Spacer(modifier = Modifier.height(8.dp))

        LineaResplandorRoja()

        if (mostrarImagenGrande && !cliente?.fotografiaUrl.isNullOrEmpty()) {
            FullscreenClienteImage(cliente!!.fotografiaUrl!!) {
                mostrarImagenGrande = false
            }
        }
    }
}



@Composable
fun FullscreenClienteImage(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Fondo blur
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp)
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

            // Imagen con zoom
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 4f)
                offsetX += panChange.x
                offsetY += panChange.y
            }

            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 12.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(22.dp))
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .transformable(state = transformState)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}






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
                ContadorCantidad2(producto.cantidad,cantidadDisponible = producto.cantidadDisponible, onSumar, onRestar, onCantidadCambiada)
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
            Text("Total Venta:", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
            Spacer(modifier = Modifier.width(8.dp))
            Text(formato.format(total), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF0000))
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
fun ContadorCantidad2(
    cantidad: Int,
    cantidadDisponible: Int,
    onSumar: () -> Unit,
    onRestar: () -> Unit,
    onCantidadCambiada: (Int) -> Unit
) {
    var textCantidad by remember(cantidad) { mutableStateOf(cantidad.toString()) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Botón Restar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val current = textCantidad.toIntOrNull() ?: 0
                    if (current > 0) {
                        val nuevo = current - 1
                        textCantidad = nuevo.toString()
                        onCantidadCambiada(nuevo)
                        onRestar()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("-", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
        }

        // Campo de texto cantidad
        Box(
            modifier = Modifier
                .width(65.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFF0000)),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = textCantidad,
                onValueChange = { newValue ->
                    val limpio = newValue.filter { it.isDigit() }.trimStart('0')
                    val numeroIngresado = if (limpio.isEmpty()) 0 else limpio.toInt()
                    val valorFinal = numeroIngresado.coerceIn(0, cantidadDisponible)
                    textCantidad = valorFinal.toString()
                    onCantidadCambiada(valorFinal)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp  // <- Esto asegura que el texto quede centrado verticalmente
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && textCantidad == "0") {
                            textCantidad = ""
                        }
                        if (!focusState.isFocused && textCantidad.isEmpty()) {
                            textCantidad = "0"
                        }
                    }
            )

        }

        // Botón Sumar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    val current = textCantidad.toIntOrNull() ?: 0
                    val nuevo = (current + 1).coerceAtMost(cantidadDisponible)
                    textCantidad = nuevo.toString()
                    onCantidadCambiada(nuevo)
                    onSumar()
                },
            contentAlignment = Alignment.Center
        ) {
            Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
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
                InfoClienteCompacta(
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            LineaResplandorRoja()
            CardTotal2(
                total = totalGeneral,
                formato = formatoMoneda,
                modifier = Modifier.fillMaxWidth()
            )
        }

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


