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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.draw.blur
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.ui.theme.*
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion
import com.gruposanangel.delivery.utilidades.ImprimirTicket58mmCompleto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.*
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun PantallaVentas(
    navController: NavController,
    clienteId: String,
    repository: RepositoryCliente? = null,
    inventarioRepo: RepositoryInventario? = null,
    impresoraBluetooth: BluetoothDevice? = null,
    origen: String? = "Clientes",
    isAdminOverride: Boolean? = null
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
    
    // 🔥 SINCRONIZAR CON EL MODO (ADMIN/RUTA)
    LaunchedEffect(isAdminOverride) {
        if (isAdminOverride != null) {
            ventaViewModel.sobreescribirAdmin(isAdminOverride)
        }
    }

    val uiState by ventaViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var cliente by remember { mutableStateOf<ClienteEntity?>(null) }
    var nombreVendedor by remember { mutableStateOf("Cargando...") }
    var showSuccessScreen by remember { mutableStateOf(false) }
    var motivoPendiente by remember { mutableStateOf<String?>(null) }
    var showImageFull by remember { mutableStateOf(false) }

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
            rutaId = cliente?.rutaId,
            rutaNombre = cliente?.rutaId, // De momento usamos el ID como nombre si no tenemos el mapeo a la mano
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

    val isDark = ThemeConfig.isActuallyDark

    DeliveryTheme(darkTheme = isDark) {
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
            onLimpiarCarrito = { ventaViewModel.limpiarCarrito() },
            onFinalizar = { motivo ->
                if (uiState.requiereFotoEvidencia) {
                    motivoPendiente = motivo
                    launcherEvidencia.launch(null)
                } else {
                    finalizarVentaProceso(motivo = motivo)
                }
            },
            onSeleccionarPerfil = { ventaViewModel.seleccionarPerfil(it) },
            onVerImagenFull = { showImageFull = true },
            onVerHistorial = { 
                if (cliente != null) navController.navigate("historial_cliente/${cliente!!.id}?isAdminOverride=${isAdminOverride ?: true}")
            },
            snackbarHostState = snackbarHostState
        )

        if (showImageFull && cliente != null) {
            Dialog(
                onDismissRequest = { showImageFull = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
    onLimpiarCarrito: () -> Unit,
    onVerImagenFull: () -> Unit,
    onSeleccionarPerfil: (PerfilVenta) -> Unit,
    onFinalizar: (String?) -> Unit, 
    snackbarHostState: SnackbarHostState,
    onVerHistorial: () -> Unit
) {
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    var mostrarConfirm by remember { mutableStateOf(false) }
    var mostrarMotivos by remember { mutableStateOf(false) }
    val motivos = listOf("Tienda cerrada", "Tiene producto", "No tiene dinero", "No estaba el de compras")
    
    val listState = rememberLazyListState()

    // 🔥 FIX DEFINITIVO: Estado local puro para el buscador.
    var textFieldValue by remember { mutableStateOf(TextFieldValue(uiState.searchQuery)) }
    
    // 🔥 AUTO-SCROLL INTELIGENTE: Al precargar, cambiar de perfil o limpiar búsqueda
    var yaScrolledPrecarga by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.perfilSeleccionado, uiState.searchQuery) {
        if (uiState.productosEnCarrito.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(uiState.cantidades) {
        // Si detectamos que hay productos (por precarga) y aún no hemos hecho el scroll inicial
        if (uiState.cantidades.isNotEmpty() && !yaScrolledPrecarga) {
            listState.animateScrollToItem(0)
            yaScrolledPrecarga = true
        }
        // Si el carrito se vacía, reseteamos el flag por si vuelve a haber una precarga (raro, pero posible)
        if (uiState.cantidades.isEmpty()) {
            yaScrolledPrecarga = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
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
                    PerfilVentaSelector(
                        perfiles = uiState.perfilesDisponibles,
                        seleccionado = uiState.perfilSeleccionado,
                        onSeleccionar = onSeleccionarPerfil
                    )
                    SearchBarVentas(
                        value = textFieldValue, 
                        onValueChange = {
                            textFieldValue = it
                            onSearchQueryChanged(it.text)
                        }
                    )
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
                                    CircularProgressIndicator(color = DelisaRed) 
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
                            else -> item { 
                                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                    Text("Inventario no disponible", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.estaProcesando) { 
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.2f)).zIndex(100f), Alignment.Center) { 
                    CircularProgressIndicator(color = DelisaRed) 
                } 
            }
        }

        SuccessOverlay(showSuccess)
    }
    
    if (mostrarConfirm) { 
        DialogoConfirmacion(
            titulo = "CONFIRMAR COBRO", 
            mensaje = "${cliente?.nombreNegocio}\nVenta por ${formatoMoneda.format(uiState.totalVenta)}",
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
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val scale by animateFloatAsState(
                    targetValue = if (show) 1.2f else 0.8f,
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow),
                    label = "successScale"
                )
                Surface(
                    modifier = Modifier.size(120.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                    shape = CircleShape, color = DelisaGreen.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, null, tint = DelisaGreenDark, modifier = Modifier.size(80.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("¡VENTA EXITOSA!", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text("Redirigiendo a tu ruta...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun MotivoNoVentaDialog(motivos: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = DelisaRed, modifier = Modifier.size(32.dp)) },
        title = { Text("MOTIVO DE NO VENTA", fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Selecciona la razón por la que no se concretó la venta.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 20.dp))
                motivos.forEach { motivo ->
                    Surface(
                        onClick = { onSelect(motivo) },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), 
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowForward, null, tint = DelisaRed, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(motivo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) } },
        containerColor = MaterialTheme.colorScheme.surface, 
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun JornadaNoIniciadaView(onIrAInicio: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Block, null, tint = DelisaRed, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("JORNADA NO INICIADA", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Debes deslizar el botón de inicio en el Dashboard para poder realizar ventas.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onIrAInicio, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface), shape = RoundedCornerShape(12.dp)) { 
                Text("REGRESAR AL DASHBOARD", color = MaterialTheme.colorScheme.surface) 
            }
        }
    }
}

@Composable
fun ModernSalesHeader(cliente: ClienteEntity?, distanciaMetros: Float, estaEnRango: Boolean, onBack: () -> Unit, onImageClick: () -> Unit, onVerHistorial: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(), 
        color = MaterialTheme.colorScheme.surface, 
        shadowElevation = 2.dp, 
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                .fillMaxWidth(), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { 
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed) 
            }
            
            Column(Modifier.weight(1f)) {
                Text(
                    text = cliente?.nombreNegocio ?: "Cargando...", 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Black, 
                    color = MaterialTheme.colorScheme.onSurface, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = cliente?.nombreDueno ?: "Titular no registrado", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    fontWeight = FontWeight.Medium
                )

                if (cliente != null && distanciaMetros >= 0) {
                    Spacer(Modifier.height(6.dp))
                    
                    Surface(
                        color = if (estaEnRango) DelisaGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.errorContainer,
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
                                    .background(if (estaEnRango) DelisaGreenDark else DelisaRed, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            val distTexto = if (distanciaMetros < 1000) "${distanciaMetros.toInt()}m" 
                                           else String.format(Locale.US, "%.1f km", distanciaMetros / 1000f)
                            
                            Text(
                                text = if (estaEnRango) "DENTRO DEL RANGO" else "FUERA DE RANGO ($distTexto)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (estaEnRango) DelisaGreenDark else DelisaRed,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AsyncImage(
                    model = cliente?.fotografiaUrl, 
                    placeholder = painterResource(R.drawable.repartidor), 
                    error = painterResource(R.drawable.repartidor), 
                    contentDescription = null, 
                    modifier = Modifier
                        .size(64.dp) 
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .clickable { onImageClick() }, 
                    contentScale = ContentScale.Crop
                )

                Surface(
                    onClick = onVerHistorial,
                    color = DelisaRed.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null, 
                            tint = DelisaRed,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("HISTORIAL", fontSize = 8.sp, fontWeight = FontWeight.Black, color = DelisaRed)
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation, RoundedCornerShape(24.dp)), 
        shape = RoundedCornerShape(24.dp), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), 
        border = if (enCarrito) BorderStroke(1.5.dp, DelisaRed.copy(alpha = 0.5f)) else null
    ) {
        Box {
            if (enCarrito) {
                Box(modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(6.dp).background(brush = Brush.verticalGradient(listOf(DelisaRed, DelisaRedDark)), shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)))
            }
            Row(modifier = Modifier.padding(12.dp).padding(start = if (enCarrito) 8.dp else 0.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AsyncImage(model = producto.imagenUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = null, modifier = Modifier.size(90.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
                    if (enCarrito) {
                        Surface(color = DelisaRed, shape = CircleShape, modifier = Modifier.align(Alignment.TopStart).offset((-10).dp, (-10).dp).size(42.dp), shadowElevation = 8.dp) {
                            Box(contentAlignment = Alignment.Center) { Text(text = "${producto.cantidad}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black) }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(text = producto.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, lineHeight = 20.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(text = "${formato.format(producto.precio)} / pza", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Surface(color = if (producto.cantidadDisponible < 5) DelisaRed.copy(0.12f) else DelisaGreen.copy(0.1f), shape = RoundedCornerShape(10.dp)) {
                        Text(text = "${producto.cantidadDisponible} Disponibles", fontSize = 13.sp, fontWeight = FontWeight.Black, color = if (producto.cantidadDisponible < 5) DelisaRed else DelisaGreenDark, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = onSumar, modifier = Modifier.size(44.dp).background(DelisaRed, RoundedCornerShape(12.dp))) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
                    IconButton(onClick = onRestar, enabled = enCarrito, modifier = Modifier.size(44.dp).background(if (enCarrito) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, RoundedCornerShape(12.dp))) { Icon(Icons.Default.Remove, null, tint = if (enCarrito) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun CardTotalVentaPro(total: Double, formato: NumberFormat, onFinalizar: () -> Unit) {
    val esSoloVisita = total == 0.0
    val isDark = isSystemInDarkTheme()
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .shadow(20.dp, RoundedCornerShape(24.dp)), 
        color = MaterialTheme.colorScheme.surface, 
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onFinalizar, 
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (esSoloVisita) MaterialTheme.colorScheme.onSurface else DelisaRed, 
                    contentColor = if (esSoloVisita) MaterialTheme.colorScheme.surface else Color.White
                ), 
                shape = RoundedCornerShape(16.dp), 
                modifier = Modifier.height(54.dp).weight(1f), 
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = if (esSoloVisita) Icons.Default.Info else Icons.AutoMirrored.Filled.ReceiptLong, 
                    contentDescription = null, 
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (esSoloVisita) "REGISTRAR VISITA" else "FINALIZAR VENTA", 
                    fontWeight = FontWeight.ExtraBold, 
                    fontSize = 13.sp, 
                    letterSpacing = 0.5.sp
                )
            }
            if (!esSoloVisita) {
                Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.End) { 
                    Text(
                        text = "TOTAL COBRO", 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formato.format(total), 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.Black, 
                        color = if (isDark) Color.White else DelisaRed
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateProductos(isSearch: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Inventory2, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(Modifier.height(16.dp))
        Text(if (isSearch) "No se encontraron productos" else "Inventario vacío en esta ruta", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SeccionHeader(titulo: String, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp), 
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = titulo,
            fontSize = 11.sp, 
            fontWeight = FontWeight.Black,
            color = DelisaRed
        )
        
        if (onAction != null) {
            Surface(
                onClick = onAction,
                color = DelisaRed.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.DeleteSweep, null, tint = DelisaRed, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("VACIAR", fontSize = 9.sp, fontWeight = FontWeight.Black, color = DelisaRed)
                }
            }
        }
    }
}

@Composable
fun SearchBarVentas(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp), // 🔥 Pegado al componente superior
        placeholder = { Text("Buscar producto...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = DelisaRed) },
        trailingIcon = {
            if (value.text.isNotEmpty()) {
                IconButton(onClick = { onValueChange(TextFieldValue("")) }) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DelisaRed,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
