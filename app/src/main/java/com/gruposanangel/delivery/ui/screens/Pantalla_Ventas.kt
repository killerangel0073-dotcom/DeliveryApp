package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

    // Control de tiempo para evitar múltiples avisos seguidos de stock
    var ultimoAvisoStock by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val usuario = repoUsuario.obtenerUsuarioActual()
        nombreVendedor = usuario?.nombre ?: FirebaseAuth.getInstance().currentUser?.displayName ?: "Vendedor"
    }

    LaunchedEffect(clienteId) {
        if (!isPreview) {
            cliente = repository?.obtenerClientesLocalPorId(clienteId)
            ventaViewModel.verificarRutaAsignadaLocal(FirebaseAuth.getInstance().currentUser?.uid ?: "")
        }
    }

    val handleBack: () -> Unit = {
        if (!navController.popBackStack()) {
            val dest = if (origen == "Mapa") "delivery?screen=    Mapa    " else "delivery?screen=Clientes"
            navController.navigate(dest) { launchSingleTop = true }
        }
    }

    PantallaVentasContent(
        uiState = uiState,
        cliente = cliente,
        onBack = handleBack,
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
            ventaViewModel.procesarVenta(cliente?.id ?: "", cliente?.nombreNegocio ?: "Negocio", cliente?.fotografiaUrl, "Efectivo") { exito, msg, idDeVentaGenerado ->
                scope.launch {
                    if (exito) {
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
                        handleBack()
                    } else {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            }
        },
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun PantallaVentasContent(
    uiState: VentaUiState, 
    cliente: ClienteEntity?, 
    onBack: () -> Unit, 
    onSumar: (Plantilla_Producto) -> Unit, 
    onRestar: (Plantilla_Producto) -> Unit, 
    onCantidadCambiada: (Plantilla_Producto, Int) -> Unit, 
    onFinalizar: () -> Unit, 
    snackbarHostState: SnackbarHostState
) {
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    var mostrarConfirm by remember { mutableStateOf(false) }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.White,
        bottomBar = { 
            AnimatedVisibility(
                visible = uiState.totalVenta > 0,
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
            // 🔝 Header Moderno con Foto del Cliente Gigante
            ModernSalesHeader(cliente, onBack)

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

        if (uiState.estaProcesando) { 
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.2f)).zIndex(100f), Alignment.Center) { 
                CircularProgressIndicator(color = RojoDelisa) 
            } 
        }
    }
    
    if (mostrarConfirm) { 
        DialogoConfirmacion(
            titulo = "Cobrar Venta", 
            mensaje = "¿Realizar venta por ${formatoMoneda.format(uiState.totalVenta)}?", 
            onConfirmar = { mostrarConfirm = false; onFinalizar() }, 
            onCancelar = { mostrarConfirm = false }
        ) 
    }
}

@Composable
fun ModernSalesHeader(
    cliente: ClienteEntity?, 
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
    ) {
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

            // Imagen del cliente a la derecha y más grande
            AsyncImage(
                model = cliente?.fotografiaUrl, 
                placeholder = painterResource(R.drawable.repartidor), 
                error = painterResource(R.drawable.repartidor), 
                contentDescription = null, 
                modifier = Modifier
                    .size(100.dp) 
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, RojoDelisa.copy(alpha = 0.1f), RoundedCornerShape(24.dp)), 
                contentScale = ContentScale.Crop
            )
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
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enCarrito) RojoDelisa.copy(0.04f) else Color.White
        ),
        elevation = CardDefaults.cardElevation(if (enCarrito) 2.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
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
