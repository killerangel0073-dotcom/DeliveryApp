package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion
import com.gruposanangel.delivery.utilidades.ImprimirTicket58mmCompleto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.*

private val RojoDelisa = Color(0xFFE53935)
private val NegroPremium = Color(0xFF1E1E24)
private val GrisFondoPremium = Color(0xFFF6F8FA)

@Composable
fun PantallaVentas(
    navController: NavController,
    clienteId: String,
    repository: RepositoryCliente? = null,
    inventarioRepo: RepositoryInventario? = null,
    impresoraBluetooth: BluetoothDevice? = null,
    origen: String? = "Clientes"
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val firebaseDataSource = FirebaseDataSource()
    val repoUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())
    val ventaViewModel: VentaViewModel = viewModel(
        factory = VentaViewModelFactory(
            inventarioRepo ?: RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao()), 
            VentaRepository(db.VentaDao(), db.productoDao()),
            repoUsuario
        )
    )
    val uiState by ventaViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var cliente by remember { mutableStateOf<ClienteEntity?>(null) }
    var nombreVendedor by remember { mutableStateOf("Cargando...") }
    var showSuccessScreen by remember { mutableStateOf(false) }
    var motivoPendiente by remember { mutableStateOf<String?>(null) }
    var showImageFull by remember { mutableStateOf(false) } // 🔥 Nuevo: Ver imagen en grande

    var ultimoAvisoStock by remember { mutableLongStateOf(0L) }

    val handleBack: () -> Unit = {
        if (!navController.popBackStack()) {
            val dest = if (origen == "Mapa") "delivery?screen=    Mapa    " else "delivery?screen=Clientes"
            navController.navigate(dest) { launchSingleTop = true }
        }
    }

    val navegarARuta: () -> Unit = {
        navController.navigate("delivery?screen=  Ruta  ") {
            popUpTo(navController.graph.startDestinationId) { inclusive = false }
            launchSingleTop = true
        }
    }

    val irAInicio: () -> Unit = {
        navController.navigate("delivery?screen=Inicio") {
            popUpTo(navController.graph.startDestinationId) { inclusive = false }
            launchSingleTop = true
        }
    }

    fun finalizarVentaProceso(fotoPath: String? = null, motivo: String? = null) {
        ventaViewModel.procesarVenta(
            clienteId = cliente?.id ?: "", 
            clienteNombre = cliente?.nombreNegocio ?: "Negocio", 
            clienteFotoUrl = cliente?.fotografiaUrl, 
            metodoPago = "Efectivo",
            fotoEvidenciaUrl = fotoPath,
            motivoVisita = motivo
        ) { exito, msg, idDeVentaGenerado ->
            scope.launch {
                if (exito) {
                    showSuccessScreen = true
                    if (impresoraBluetooth != null) {
                        val productosAImprimir = uiState.productosEnCarrito.filter { it.cantidad > 0 }
                        scope.launch(Dispatchers.IO) {
                            try {
                                ImprimirTicket58mmCompleto(
                                    device = impresoraBluetooth,
                                    context = context,
                                    logoDrawableId = R.drawable.logo,
                                    cliente = cliente?.nombreNegocio ?: "Negocio",
                                    productos = productosAImprimir,
                                    ventaId = idDeVentaGenerado,
                                    fechaVenta = Date(),
                                    totalVenta = uiState.totalVenta,
                                    vendedorNombre = nombreVendedor,
                                    metodoPago = "Efectivo"
                                )
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Error al imprimir: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    kotlinx.coroutines.delay(1800)
                    navegarARuta()
                } else {
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    val launcherEvidencia = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        bmp?.let {
            scope.launch(Dispatchers.IO) {
                val file = File(context.cacheDir, "evidencia_${System.currentTimeMillis()}.jpg")
                try {
                    java.io.FileOutputStream(file).use { out -> 
                        it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out) 
                    }
                    withContext(Dispatchers.Main) {
                        finalizarVentaProceso(file.absolutePath, motivoPendiente)
                        motivoPendiente = null
                    }
                } catch (e: Exception) {
                    Log.e("Ventas", "Error guardando evidencia", e)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val usuario = repoUsuario.obtenerUsuarioActual()
        nombreVendedor = usuario?.nombre ?: FirebaseAuth.getInstance().currentUser?.displayName ?: "Vendedor"
    }

    LaunchedEffect(clienteId) {
        if (!isPreview) {
            cliente = repository?.obtenerClientesLocalPorId(clienteId)
            ventaViewModel.verificarRutaAsignadaLocal(FirebaseAuth.getInstance().currentUser?.uid ?: "")
            ventaViewModel.precargarUltimaVenta(clienteId)
            cliente?.let { 
                ventaViewModel.monitorearGeocerca(it.ubicacionLat, it.ubicacionLon)
            }
        }
    }

    PantallaVentasContent(
        uiState = uiState,
        cliente = cliente,
        showSuccess = showSuccessScreen,
        onBack = handleBack,
        onIrAInicio = irAInicio,
        onSearchQueryChanged = { ventaViewModel.onSearchQueryChanged(it) },
        onSumar = { p -> 
            if (p.cantidad < p.cantidadDisponible) {
                ventaViewModel.actualizarCantidad(p.id, p.cantidad + 1) 
            } else {
                val ahora = System.currentTimeMillis()
                if (ahora - ultimoAvisoStock > 2500) { 
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(
                            message = "Stock insuficiente: ${p.cantidadDisponible} disponibles",
                            duration = SnackbarDuration.Short
                        )
                    }
                    ultimoAvisoStock = ahora
                }
            }
        },
        onRestar = { p -> 
            if (p.cantidad > 0) ventaViewModel.actualizarCantidad(p.id, p.cantidad - 1) 
        },
        onCantidadCambiada = { p, n -> 
            ventaViewModel.actualizarCantidad(p.id, n)
        },
        onLimpiarCarrito = { ventaViewModel.limpiarCarrito() },
        onFinalizar = { motivo ->
            if (uiState.requiereFotoEvidencia) {
                motivoPendiente = motivo
                launcherEvidencia.launch(null)
            } else {
                finalizarVentaProceso(motivo = motivo)
            }
        },
        onVerImagenFull = { showImageFull = true },
        onVerHistorial = { 
            if (cliente != null) navController.navigate("historial_cliente/${cliente!!.id}") 
        },
        snackbarHostState = snackbarHostState
    )

    if (showImageFull && cliente != null) {
        Dialog(
            onDismissRequest = { showImageFull = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                // Fondo con blur para estilo premium
                AsyncImage(
                    model = cliente!!.fotografiaUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(40.dp).graphicsLayer { alpha = 0.5f }
                )

                var scale by remember { mutableStateOf(1f) }
                var offsetX by remember { mutableStateOf(0f) }
                var offsetY by remember { mutableStateOf(0f) }

                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 5f)
                    offsetX += panChange.x
                    offsetY += panChange.y
                }

                AsyncImage(
                    model = cliente!!.fotografiaUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                        .transformable(state = transformState)
                )

                IconButton(
                    onClick = { showImageFull = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .size(48.dp)
                        .background(Color.Black.copy(0.4f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
                
                // Indicador visual de que se puede hacer zoom
                if (scale == 1f) {
                    Text(
                        "Pizca para acercar",
                        color = Color.White.copy(0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PantallaVentasContent(
    uiState: VentaUiState, 
    cliente: ClienteEntity?, 
    showSuccess: Boolean = false,
    onBack: () -> Unit, 
    onIrAInicio: () -> Unit = {},
    onSearchQueryChanged: (String) -> Unit,
    onSumar: (Plantilla_Producto) -> Unit, 
    onRestar: (Plantilla_Producto) -> Unit, 
    onCantidadCambiada: (Plantilla_Producto, Int) -> Unit, 
    onLimpiarCarrito: () -> Unit,
    onVerImagenFull: () -> Unit,
    onFinalizar: (String?) -> Unit, 
    snackbarHostState: SnackbarHostState,
    onVerHistorial: () -> Unit
) {
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    var mostrarConfirm by remember { mutableStateOf(false) }
    var mostrarMotivos by remember { mutableStateOf(false) }
    val motivos = listOf("Tienda cerrada", "Tiene producto", "No tiene dinero", "No estaba el de compras")
    
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.cantidades) {
        if (uiState.cantidades.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.White,
            bottomBar = { 
                AnimatedVisibility(
                    visible = uiState.enRuta,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    CardTotalVentaPro(
                        total = uiState.totalVenta, 
                        formato = formatoMoneda, 
                        onFinalizar = { 
                            if (uiState.totalVenta > 0) mostrarConfirm = true 
                            else mostrarMotivos = true
                        }
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                ModernSalesHeader(
                    cliente = cliente, 
                    distanciaMetros = uiState.distanciaAlClienteMetros,
                    estaEnRango = uiState.estaEnRango,
                    onBack = onBack,
                    onImageClick = onVerImagenFull,
                    onVerHistorial = onVerHistorial
                )

                if (uiState.enRuta) {
                    SearchBar(query = uiState.searchQuery, onQueryChange = onSearchQueryChanged)
                }

                if (!uiState.enRuta) {
                    JornadaNoIniciadaView(onIrAInicio)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        when (uiState.estadoRuta) {
                            is EstadoRuta.Cargando -> item { 
                                Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) { 
                                    CircularProgressIndicator(color = RojoDelisa) 
                                } 
                            }
                            is EstadoRuta.ConRuta -> {
                                if (uiState.productosEnCarrito.isEmpty()) {
                                    item { EmptyStateProductos(uiState.searchQuery.isNotEmpty()) }
                                } else {
                                    val seleccionados = uiState.productosEnCarrito.filter { it.cantidad > 0 }
                                    val resto = uiState.productosEnCarrito.filter { it.cantidad == 0 }
                                    val mostrarHeaders = uiState.searchQuery.isBlank()

                                    if (mostrarHeaders && seleccionados.isNotEmpty()) {
                                        item { 
                                            SeccionHeader(
                                                titulo = "PRODUCTOS EN CARRITO",
                                                onAction = onLimpiarCarrito
                                            ) 
                                        }
                                        itemsIndexed(seleccionados, key = { _, p -> "sel_${p.id}" }) { _, producto ->
                                            ItemVentaProductoModerno(producto, formatoMoneda, { onSumar(producto) }, { onRestar(producto) })
                                        }
                                        if (resto.isNotEmpty()) item { SeccionHeader("CATÁLOGO DISPONIBLE") }
                                    }

                                    itemsIndexed(if (mostrarHeaders) resto else uiState.productosEnCarrito, key = { _, p -> p.id }) { _, producto ->
                                        ItemVentaProductoModerno(producto, formatoMoneda, { onSumar(producto) }, { onRestar(producto) })
                                    }
                                }
                            }
                            else -> item { Text("Inventario no disponible", Modifier.padding(20.dp), color = Color.Gray) }
                        }
                    }
                }
            }

            if (uiState.estaProcesando) { 
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.2f)).zIndex(100f), Alignment.Center) { 
                    CircularProgressIndicator(color = RojoDelisa) 
                } 
            }
        }

        SuccessOverlay(showSuccess)
    }
    
    if (mostrarConfirm) { 
        DialogoConfirmacion(
            titulo = "CONFIRMAR COBRO", 
            mensaje = "${cliente?.nombreNegocio}\nVenta por ${formatoMoneda.format(uiState.totalVenta)} pesos",
            onConfirmar = { mostrarConfirm = false; onFinalizar(null) }, 
            onCancelar = { mostrarConfirm = false }
        ) 
    }

    if (mostrarMotivos) {
        MotivoNoVentaDialog(
            motivos = motivos, 
            onDismiss = { mostrarMotivos = false }, 
            onSelect = { onFinalizar(it) }
        )
    }
}

@Composable
fun SuccessOverlay(show: Boolean) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = Modifier.zIndex(200f)
    ) {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val scale by animateFloatAsState(
                    targetValue = if (show) 1.2f else 0.8f,
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow),
                    label = "successScale"
                )
                Surface(
                    modifier = Modifier.size(120.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                    shape = CircleShape, color = Color(0xFFE8F5E9)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(80.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("¡VENTA EXITOSA!", fontSize = 24.sp, fontWeight = FontWeight.Black, color = NegroPremium)
                Text("Redirigiendo a tu ruta...", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun MotivoNoVentaDialog(motivos: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = RojoDelisa, modifier = Modifier.size(32.dp)) },
        title = { Text("MOTIVO DE NO VENTA", fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Selecciona la razón por la que no se concretó la venta.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 20.dp))
                motivos.forEach { motivo ->
                    Surface(
                        onClick = { onSelect(motivo) },
                        color = GrisFondoPremium, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowForward, null, tint = RojoDelisa, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(motivo, fontWeight = FontWeight.Bold, color = NegroPremium, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold) } },
        containerColor = Color.White, shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun JornadaNoIniciadaView(onIrAInicio: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Block, null, tint = RojoDelisa, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("JORNADA NO INICIADA", fontSize = 20.sp, fontWeight = FontWeight.Black, color = NegroPremium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Debes deslizar el botón de inicio en el Dashboard para poder realizar ventas.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onIrAInicio, colors = ButtonDefaults.buttonColors(containerColor = NegroPremium), shape = RoundedCornerShape(12.dp)) { Text("REGRESAR AL DASHBOARD") }
        }
    }
}

@Composable
fun ModernSalesHeader(cliente: ClienteEntity?, distanciaMetros: Float, estaEnRango: Boolean, onBack: () -> Unit, onImageClick: () -> Unit, onVerHistorial: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(), 
        color = Color.White, 
        shadowElevation = 2.dp, 
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 4.dp) // 🔥 Espaciado inferior reducido
                .fillMaxWidth(), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { 
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RojoDelisa) 
            }
            
            Column(Modifier.weight(1f)) {
                Text(
                    text = cliente?.nombreNegocio ?: "Cargando...", 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Black, 
                    color = NegroPremium, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = cliente?.nombreDueno ?: "Titular no registrado", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.Gray, 
                    fontWeight = FontWeight.Medium
                )

                if (cliente != null && distanciaMetros >= 0) {
                    Spacer(Modifier.height(6.dp))
                    
                    // 🔥 Badge de Geocerca Moderno
                    Surface(
                        color = if (estaEnRango) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (estaEnRango) Color(0xFF2E7D32) else RojoDelisa, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            val distTexto = if (distanciaMetros < 1000) "${distanciaMetros.toInt()}m" 
                                           else String.format(Locale.US, "%.1f km", distanciaMetros / 1000f)
                            
                            Text(
                                text = if (estaEnRango) "DENTRO DEL RANGO" else "FUERA DE RANGO ($distTexto)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (estaEnRango) Color(0xFF2E7D32) else RojoDelisa,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // 📸 BLOQUE DE FOTO E HISTORIAL
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp) // 🔥 Imagen y botón más cerca
            ) {
                AsyncImage(
                    model = cliente?.fotografiaUrl, 
                    placeholder = painterResource(R.drawable.repartidor), 
                    error = painterResource(R.drawable.repartidor), 
                    contentDescription = null, 
                    modifier = Modifier
                        .size(64.dp) 
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .clickable { onImageClick() }, 
                    contentScale = ContentScale.Crop
                )

                // 📜 Botón de Historial (Debajo de la foto)
                Surface(
                    onClick = onVerHistorial,
                    color = RojoDelisa.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null, 
                            tint = RojoDelisa,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("HISTORIAL", fontSize = 8.sp, fontWeight = FontWeight.Black, color = RojoDelisa)
                    }
                }
            }
        }
    }
}

@Composable
fun ItemVentaProductoModerno(producto: Plantilla_Producto, formato: NumberFormat, onSumar: () -> Unit, onRestar: () -> Unit) {
    val enCarrito = producto.cantidad > 0
    val scale by animateFloatAsState(targetValue = if (enCarrito) 1.02f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "scale")
    val elevation by animateDpAsState(targetValue = if (enCarrito) 6.dp else 1.dp, label = "elevation")

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).graphicsLayer { scaleX = scale; scaleY = scale }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = if (enCarrito) BorderStroke(1.5.dp, RojoDelisa.copy(alpha = 0.5f)) else null, elevation = CardDefaults.cardElevation(elevation)) {
        Box {
            if (enCarrito) {
                Box(modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(6.dp).background(brush = Brush.verticalGradient(listOf(RojoDelisa, RojoDelisa.copy(0.6f))), shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)))
            }
            Row(modifier = Modifier.padding(12.dp).padding(start = if (enCarrito) 8.dp else 0.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AsyncImage(model = producto.imagenUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = null, modifier = Modifier.size(90.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFF5F5F5)), contentScale = ContentScale.Crop)
                    if (enCarrito) {
                        Surface(color = RojoDelisa, shape = CircleShape, modifier = Modifier.align(Alignment.TopStart).offset((-10).dp, (-10).dp).size(42.dp), shadowElevation = 8.dp) {
                            Box(contentAlignment = Alignment.Center) { Text(text = "${producto.cantidad}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = producto.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = NegroPremium, maxLines = 2, lineHeight = 20.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(text = "${formato.format(producto.precio)} / pza", style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Surface(color = if (producto.cantidadDisponible < 5) RojoDelisa.copy(0.12f) else Color(0xFFE8F5E9), shape = RoundedCornerShape(10.dp)) {
                        Text(text = "${producto.cantidadDisponible} Disponibles", fontSize = 13.sp, fontWeight = FontWeight.Black, color = if (producto.cantidadDisponible < 5) RojoDelisa else Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = onSumar, modifier = Modifier.size(44.dp).background(RojoDelisa, RoundedCornerShape(12.dp))) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    IconButton(onClick = onRestar, enabled = enCarrito, modifier = Modifier.size(44.dp).background(if (enCarrito) Color(0xFFF1F2F6) else Color.Transparent, RoundedCornerShape(12.dp))) { Icon(Icons.Default.Remove, null, tint = if (enCarrito) NegroPremium else Color.LightGray, modifier = Modifier.size(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun CardTotalVentaPro(total: Double, formato: NumberFormat, onFinalizar: () -> Unit) {
    val esSoloVisita = total == 0.0
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp).shadow(20.dp, RoundedCornerShape(24.dp)), color = if (esSoloVisita) Color(0xFF37474F) else NegroPremium, shape = RoundedCornerShape(24.dp)) {
        Row(modifier = Modifier.padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Button(onClick = onFinalizar, colors = ButtonDefaults.buttonColors(containerColor = if (esSoloVisita) Color.White else RojoDelisa, contentColor = if (esSoloVisita) Color.Black else Color.White), shape = RoundedCornerShape(16.dp), modifier = Modifier.height(54.dp).weight(1f), elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)) {
                Icon(imageVector = if (esSoloVisita) Icons.Default.Info else Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text = if (esSoloVisita) "REGISTRAR VISITA" else "FINALIZAR VENTA", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 0.5.sp)
            }
            if (!esSoloVisita) {
                Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.End) { 
                    Text("TOTAL COBRO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White.copy(0.5f))
                    Text(formato.format(total), fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun EmptyStateProductos(isSearch: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Inventory2, null, Modifier.size(64.dp), tint = Color(0xFFE0E0E0))
        Spacer(Modifier.height(16.dp))
        Text(if (isSearch) "No se encontraron productos" else "Inventario vacío en esta ruta", color = Color.LightGray, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SeccionHeader(titulo: String, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp), // 🔥 Espaciado vertical reducido
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = titulo,
            fontSize = 11.sp, // 🔥 Un poco más pequeño para elegancia
            fontWeight = FontWeight.Black,
            color = RojoDelisa
        )
        
        if (onAction != null) {
            Surface(
                onClick = onAction,
                color = RojoDelisa.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), // 🔥 Botón más compacto
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.DeleteSweep, null, tint = RojoDelisa, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("VACIAR", fontSize = 9.sp, fontWeight = FontWeight.Black, color = RojoDelisa)
                }
            }
        }
    }
}

@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp), // 🔥 Espacio inferior mínimo
        placeholder = { Text("Buscar producto...", color = Color.Gray) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = RojoDelisa) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, null, tint = Color.Gray)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RojoDelisa,
            unfocusedBorderColor = Color.LightGray.copy(alpha = 0.5f),
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}
