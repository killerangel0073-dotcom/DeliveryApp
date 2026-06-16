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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
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
private val GrisTextoSecundario = Color(0xFF757575)

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
            inventarioRepo ?: RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao()), 
            VentaRepository(db.VentaDao()), 
            repoUsuario
        )
    )
    val uiState by ventaViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var cliente by remember { mutableStateOf<ClienteEntity?>(null) }
    var nombreVendedor by remember { mutableStateOf("Cargando...") }
    var showSuccessScreen by remember { mutableStateOf(false) }

    // Control de tiempo para evitar múltiples avisos seguidos de stock
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

    fun finalizarVentaProceso(fotoPath: String? = null) {
        ventaViewModel.procesarVenta(
            clienteId = cliente?.id ?: "", 
            clienteNombre = cliente?.nombreNegocio ?: "Negocio", 
            clienteFotoUrl = cliente?.fotografiaUrl, 
            metodoPago = "Efectivo",
            fotoEvidenciaUrl = fotoPath
        ) { exito, msg, idDeVentaGenerado ->
            scope.launch {
                if (exito) {
                    // 🔥 MOSTRAR PANTALLA DE ÉXITO
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
                    
                    // Esperar un poco para que se vea la animación y luego navegar
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
            // Guardar foto de evidencia temporalmente
            scope.launch(Dispatchers.IO) {
                val file = File(context.cacheDir, "evidencia_${System.currentTimeMillis()}.jpg")
                try {
                    java.io.FileOutputStream(file).use { out -> 
                        it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out) 
                    }
                    withContext(Dispatchers.Main) {
                        // Proceder con la venta usando la foto como prueba
                        finalizarVentaProceso(file.absolutePath)
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
            
            // 🔥 Monitorear cercanía al cliente
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
        onSumar = { p -> 
            val index = uiState.productosEnCarrito.indexOfFirst { it.id == p.id }
            if (index != -1) {
                if (p.cantidad < p.cantidadDisponible) {
                    ventaViewModel.actualizarCantidad(index, p.cantidad + 1) 
                } else {
                    val ahora = System.currentTimeMillis()
                    if (ahora - ultimoAvisoStock > 2500) { // Limita la frecuencia de la alerta visual
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss() // Quita el anterior si existe
                            snackbarHostState.showSnackbar(
                                message = "Stock insuficiente: ${p.cantidadDisponible} disponibles",
                                duration = SnackbarDuration.Short
                            )
                        }
                        ultimoAvisoStock = ahora
                    }
                }
            }
        },
        onRestar = { p -> 
            val index = uiState.productosEnCarrito.indexOfFirst { it.id == p.id }
            if (index != -1 && p.cantidad > 0) ventaViewModel.actualizarCantidad(index, p.cantidad - 1) 
        },
        onCantidadCambiada = { p, n -> 
            val index = uiState.productosEnCarrito.indexOfFirst { it.id == p.id }
            if (index != -1) ventaViewModel.actualizarCantidad(index, n)
        },
        onFinalizar = {
            if (uiState.requiereFotoEvidencia) {
                // Si el GPS dice que estamos lejos o no es preciso, pedimos foto forzosa
                launcherEvidencia.launch(null)
            } else {
                finalizarVentaProceso()
            }
        },
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun PantallaVentasContent(
    uiState: VentaUiState, 
    cliente: ClienteEntity?, 
    showSuccess: Boolean = false,
    onBack: () -> Unit, 
    onIrAInicio: () -> Unit = {},
    onSumar: (Plantilla_Producto) -> Unit, 
    onRestar: (Plantilla_Producto) -> Unit, 
    onCantidadCambiada: (Plantilla_Producto, Int) -> Unit, 
    onFinalizar: () -> Unit, 
    snackbarHostState: SnackbarHostState
) {
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    var mostrarConfirm by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.White,
            bottomBar = { 
                AnimatedVisibility(
                    visible = uiState.totalVenta > 0 && uiState.enRuta,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    CardTotalVentaPro(
                        total = uiState.totalVenta, 
                        formato = formatoMoneda, 
                        onFinalizar = { mostrarConfirm = true }
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                // 🔝 Header Moderno con Foto del Cliente Gigante y Geocerca
                ModernSalesHeader(
                    cliente = cliente, 
                    distanciaMetros = uiState.distanciaAlClienteMetros,
                    estaEnRango = uiState.estaEnRango,
                    onBack = onBack
                )

                if (!uiState.enRuta) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = null,
                                tint = RojoDelisa,
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "JORNADA NO INICIADA",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = NegroPremium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Debes deslizar el botón de inicio en el Dashboard para poder realizar ventas.",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = onIrAInicio,
                                colors = ButtonDefaults.buttonColors(containerColor = NegroPremium),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("REGRESAR AL DASHBOARD")
                            }
                        }
                    }
                } else {
                    LazyColumn(
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
                                    item { EmptyStateProductos(false) }
                                } else {
                                    itemsIndexed(uiState.productosEnCarrito, key = { _, p -> p.id }) { _, producto ->
                                        ItemVentaProductoModerno(
                                            producto = producto, 
                                            formato = formatoMoneda, 
                                            onSumar = { onSumar(producto) }, 
                                            onRestar = { onRestar(producto) }
                                        )
                                    }
                                }
                            }
                            else -> item { 
                                Text("Inventario no disponible", Modifier.padding(20.dp), color = Color.Gray) 
                            }
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

        // 🔥 PANTALLA DE ÉXITO (OPCIÓN 2)
        AnimatedVisibility(
            visible = showSuccess,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.zIndex(200f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Animación de escala para el check
                    val scale by animateFloatAsState(
                        targetValue = if (showSuccess) 1.2f else 0.8f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow),
                        label = "successScale"
                    )

                    Surface(
                        modifier = Modifier
                            .size(120.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        shape = CircleShape,
                        color = Color(0xFFE8F5E9)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CheckCircle, 
                                contentDescription = null, 
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Text(
                        "¡VENTA EXITOSA!", 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.Black, 
                        color = NegroPremium
                    )
                    
                    Text(
                        "Redirigiendo a tu ruta...", 
                        fontSize = 14.sp, 
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
    
    if (mostrarConfirm) { 
        DialogoConfirmacion(
            titulo = "CONFIRMAR COBRO", 
            mensaje = "${cliente?.nombreNegocio}\nVenta por ${formatoMoneda.format(uiState.totalVenta)} pesos",
            onConfirmar = { mostrarConfirm = false; onFinalizar() }, 
            onCancelar = { mostrarConfirm = false }
        ) 
    }
}

@Composable
fun ModernSalesHeader(
    cliente: ClienteEntity?, 
    distanciaMetros: Float,
    estaEnRango: Boolean,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { 
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RojoDelisa) 
                }
                
                Spacer(Modifier.width(8.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = cliente?.nombreNegocio ?: "Cargando...", 
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black, 
                        color = NegroPremium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = cliente?.nombreDueno ?: "Titular no registrado", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.width(16.dp))

                AsyncImage(
                    model = cliente?.fotografiaUrl, 
                    placeholder = painterResource(R.drawable.repartidor), 
                    error = painterResource(R.drawable.repartidor), 
                    contentDescription = null, 
                    modifier = Modifier
                        .size(80.dp) 
                        .clip(RoundedCornerShape(20.dp))
                        .border(2.dp, RojoDelisa.copy(alpha = 0.1f), RoundedCornerShape(20.dp)), 
                    contentScale = ContentScale.Crop
                )
            }
            
            // 🔥 INDICADOR DE GEOCERCA
            if (cliente != null && distanciaMetros >= 0) {
                Surface(
                    color = if (estaEnRango) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (estaEnRango) Icons.Default.LocationOn else Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = if (estaEnRango) Color(0xFF2E7D32) else RojoDelisa,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        val distTexto = if (distanciaMetros < 1000) "${distanciaMetros.toInt()}m" 
                                       else String.format(Locale.US, "%.1f km", distanciaMetros / 1000f)
                        
                        Text(
                            text = if (estaEnRango) "ESTÁS EN LA UBICACIÓN DEL CLIENTE" 
                                   else "FUERA DE RANGO: $distTexto (SE REQUERIRÁ FOTO)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (estaEnRango) Color(0xFF2E7D32) else RojoDelisa,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ItemVentaProductoModerno(
    producto: Plantilla_Producto, 
    formato: NumberFormat, 
    onSumar: () -> Unit, 
    onRestar: () -> Unit
) {
    val enCarrito = producto.cantidad > 0
    
    // 🎭 Animación de escala y elevación
    val scale by animateFloatAsState(
        targetValue = if (enCarrito) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (enCarrito) 6.dp else 1.dp,
        label = "elevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enCarrito) Color.White else Color.White
        ),
        border = if (enCarrito) BorderStroke(1.5.dp, RojoDelisa.copy(alpha = 0.5f)) else null,
        elevation = CardDefaults.cardElevation(elevation)
    ) {
        Box {
            // Barra de acento lateral moderna
            if (enCarrito) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(6.dp)
                        .background(
                            brush = Brush.verticalGradient(listOf(RojoDelisa, RojoDelisa.copy(0.6f))),
                            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                        )
                )
            }

                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .padding(start = if (enCarrito) 8.dp else 0.dp) // Espacio para la barra
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Imagen con Badge de cantidad GIGANTE
                    Box {
                        AsyncImage(
                            model = producto.imagenUrl, 
                            placeholder = painterResource(R.drawable.repartidor), 
                            error = painterResource(R.drawable.repartidor), 
                            contentDescription = null, 
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFF5F5F5)), 
                            contentScale = ContentScale.Crop
                        )
                        if (enCarrito) {
                            Surface(
                                color = RojoDelisa,
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset((-10).dp, (-10).dp)
                                    .size(42.dp), // Círculo aún más grande
                                shadowElevation = 8.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${producto.cantidad}", 
                                        color = Color.White, 
                                        fontSize = 18.sp, // Número más grande
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(16.dp))
                    
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = producto.nombre, 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, 
                            color = NegroPremium,
                            maxLines = 2,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(Modifier.height(4.dp))
                        
                        Text(
                            text = "${formato.format(producto.precio)} / pza", 
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        // Indicador de Stock Pill
                        Surface(
                            color = if (producto.cantidadDisponible < 5) RojoDelisa.copy(0.12f) else Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${producto.cantidadDisponible} Disponibles", 
                                fontSize = 13.sp, // Aumentado de 10.sp a 13.sp
                                fontWeight = FontWeight.Black, 
                                color = if (producto.cantidadDisponible < 5) RojoDelisa else Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Contador Vertical Premium Minimalista (Sin número central)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = onSumar,
                            modifier = Modifier
                                .size(44.dp)
                                .background(RojoDelisa, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        
                        IconButton(
                            onClick = onRestar,
                            enabled = enCarrito,
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (enCarrito) Color(0xFFF1F2F6) else Color.Transparent, 
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                Icons.Default.Remove, 
                                null, 
                                tint = if (enCarrito) NegroPremium else Color.LightGray,
                                modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CardTotalVentaPro(total: Double, formato: NumberFormat, onFinalizar: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .shadow(20.dp, RoundedCornerShape(24.dp)),
        color = NegroPremium,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp), 
            Arrangement.SpaceBetween, 
            Alignment.CenterVertically
        ) {
            Button(
                onClick = onFinalizar, 
                colors = ButtonDefaults.buttonColors(containerColor = RojoDelisa), 
                shape = RoundedCornerShape(16.dp), 
                modifier = Modifier.height(54.dp).width(180.dp)
            ) { 
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("FINALIZAR VENTA", fontWeight = FontWeight.Black, fontSize = 12.sp) 
            }

            Column(horizontalAlignment = Alignment.End) { 
                Text("TOTAL VENTA", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White.copy(0.5f))
                Text(formato.format(total), fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }
    }
}

@Composable
fun EmptyStateProductos(isSearch: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Inventory2, null, Modifier.size(64.dp), tint = Color(0xFFE0E0E0))
        Spacer(Modifier.height(16.dp))
        Text(
            if (isSearch) "No se encontraron productos" else "Inventario vacío en esta ruta",
            color = Color.LightGray,
            fontWeight = FontWeight.Medium
        )
    }
}
