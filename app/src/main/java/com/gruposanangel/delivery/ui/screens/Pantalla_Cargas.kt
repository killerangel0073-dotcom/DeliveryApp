@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.*

private val RojoDelisa = Color(0xFFE53935)
private val NegroPremium = Color(0xFF1E1E24)
private val GrisFondoPremium = Color(0xFFF6F8FA)
private val GrisTextoSecundario = Color(0xFF757575)

@Composable
fun MovimientosInventarioScreen(
    navController: NavController,
    impresoraBluetooth: BluetoothDevice? = null,
    onImpresoraSeleccionada: (BluetoothDevice) -> Unit = {},
    preSelectedOrigen: String? = null,
    preSelectedDestino: String? = null,
    isEmergency: Boolean = false,
    isTabMode: Boolean = false,
    isLiquidationMode: Boolean = false
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    val viewModel: MovimientosViewModel = viewModel(
        factory = remember {
            val db = AppDatabase.getDatabase(context.applicationContext)
            val firebaseDataSource = FirebaseDataSource()
            val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao())
            val usuarioRepo = RepositoryUsuario(firebaseDataSource, db.usuarioDao())
            MovimientosViewModelFactory(inventarioRepo, usuarioRepo, context.applicationContext)
        }
    )

    val state by viewModel.uiState.collectAsState()
    val catalogo by viewModel.catalogoProductos.collectAsState()

    var expandedOrigen by remember { mutableStateOf(false) }
    var expandedDestino by remember { mutableStateOf(false) }
    var retornarABodega by remember { mutableStateOf(false) }

    // 🔥 ESTADO DE DESPLAZAMIENTO PARA REINICIAR AL INICIO
    val listState = rememberLazyListState()

    // Reiniciar scroll al principio cuando se cambia de almacén o el stock termina de cargar
    LaunchedEffect(state.origen, state.stockOrigen, state.isLoading) {
        if (catalogo.isNotEmpty() && !state.isLoading) {
            listState.scrollToItem(0)
        }
    }

    // 🔥 Determinar si es Auditoría de Vendedor o de Almacén Central
    val esAuditoriaVendedor = isLiquidationMode && state.origen.startsWith("Vendedor")

    // 🔥 SISTEMA DE ALERTAS PREMIUM
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var ultimoAvisoStock by remember { mutableLongStateOf(0L) }
    
    val mostrarAvisoStock: (String, Int) -> Unit = { nombre, disponible ->
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoAvisoStock > 2500) {
            ultimoAvisoStock = ahora
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = "Stock insuficiente: $disponible disponibles",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val opcionesOrigen = remember(state.listaAlmacenes, isTabMode) {
        val base = state.listaAlmacenes.filter { !it.startsWith("Vendedor") && it != "Compra Producto" }
        if (isTabMode) base else listOf("Compra Producto") + base
    }
    
    val opcionesDestino = remember(state.listaAlmacenes, state.origen) {
        when {
            state.origen == "Compra Producto" -> state.listaAlmacenes.filter { !it.startsWith("Vendedor") && it != "Compra Producto" }
            state.origen != "Selecciona Origen" -> state.listaAlmacenes.filter { it.startsWith("Vendedor") && it != state.origen }
            else -> emptyList()
        }
    }

    var mostrarDialogConfirmacion by remember { mutableStateOf(false) }

    val productosOrdenados = remember(catalogo, state.stockOrigen, state.origen) {
        if (state.origen == "Selecciona Origen") {
            catalogo.sortedBy { it.nombre }
        } else {
            catalogo.sortedWith(
                compareByDescending<Plantilla_Producto> { (state.stockOrigen[it.id] ?: 0) * it.precio }.thenBy { it.nombre }
            )
        }
    }

    // 🔥 LÓGICA DE ARQUEO/LIQUIDACIÓN: Pre-llenar cantidades cuando el stock de origen esté listo
    // Nota: Solo hacemos esto si las cantidades están vacías (primera vez que entra en este modo)
    LaunchedEffect(state.stockOrigen, isLiquidationMode) {
        if (isLiquidationMode && state.stockOrigen.isNotEmpty() && state.cantidades.isEmpty()) {
            state.stockOrigen.forEach { (prodId, cant) ->
                if (cant > 0) {
                    viewModel.actualizarCantidad(prodId, cant)
                }
            }
        }
    }

    Scaffold(
        containerColor = GrisFondoPremium,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RojoDelisa)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = data.visuals.message.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // 🔹 CABECERA PREMIUM
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isTabMode) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RojoDelisa)
                            }
                        }
                        Text(
                            text = when {
                                isLiquidationMode -> "ARQUEO DE RUTA"
                                isTabMode -> "SURTIR VENDEDORES"
                                else -> "GESTIÓN DE CARGAS"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = if (isLiquidationMode) RojoDelisa else NegroPremium,
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    // 🔥 BOTÓN VACIAR / RESET
                    TextButton(
                        onClick = { viewModel.limpiarPantalla() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                    ) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("VACIAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- SELECTORES DE RUTA (TESLA STYLE) ---
            if (isLiquidationMode) {
                if (esAuditoriaVendedor) {
                    // 🔘 DISEÑO MODERNO: Segmented Switch para Liquidación
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SelectorCard(
                            titulo = "ALMACÉN AUDITADO",
                            seleccionado = state.origen,
                            placeholder = "Cargando...",
                            icon = Icons.Default.LocalShipping,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {}
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Nuevo Selector de Acción de Auditoría (Tesla Style)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(Color(0xFFF1F2F6), RoundedCornerShape(16.dp))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (!retornarABodega) Color.White else Color.Transparent)
                                    .clickable { retornarABodega = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("ARQUEO LOCAL", fontSize = 11.sp, fontWeight = if (!retornarABodega) FontWeight.Black else FontWeight.Bold, color = if (!retornarABodega) Color.Black else Color.Gray)
                                    Text("SE QUEDA EN RUTA", fontSize = 8.sp, color = if (!retornarABodega) Color.Gray else Color.LightGray)
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (retornarABodega) RojoDelisa else Color.Transparent)
                                    .clickable { retornarABodega = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("LIQUIDACIÓN", fontSize = 11.sp, fontWeight = if (retornarABodega) FontWeight.Black else FontWeight.Bold, color = if (retornarABodega) Color.White else Color.Gray)
                                    Text("RETORNAR A BODEGA", fontSize = 8.sp, color = if (retornarABodega) Color.White.copy(0.8f) else Color.LightGray)
                                }
                            }
                        }
                    }
                } else {
                    // 🔘 DISEÑO PARA ALMACÉN CENTRAL: Solo ajuste directo
                    SelectorCard(
                        titulo = "AUDITORÍA ALMACÉN CENTRAL",
                        seleccionado = state.origen,
                        placeholder = "Cargando...",
                        icon = Icons.Default.Warehouse,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {}
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Selector Origen
                    Box(modifier = Modifier.weight(1f).wrapContentSize(Alignment.TopStart)) {
                        SelectorCard(
                            titulo = "ORIGEN / VENDEDOR",
                            seleccionado = state.origen,
                            placeholder = "Seleccionar",
                            icon = Icons.Default.Person,
                            enabled = !isEmergency && !state.isAlmacenRole,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { expandedOrigen = true }
                        )
                        DropdownMenu(
                            expanded = expandedOrigen,
                            onDismissRequest = { expandedOrigen = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            opcionesOrigen.forEach { op ->
                                DropdownMenuItem(
                                    text = { Text(op, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        viewModel.actualizarOrigen(op)
                                        expandedOrigen = false
                                        if (op == "Compra Producto") {
                                            viewModel.actualizarDestino("Almacen Huasteca")
                                        } else {
                                            viewModel.actualizarDestino("Selecciona Destino")
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Selector Destino
                    Box(modifier = Modifier.weight(1f).wrapContentSize(Alignment.TopStart)) {
                        SelectorCard(
                            titulo = "DESTINO",
                            seleccionado = state.destino,
                            placeholder = "Seleccionar",
                            icon = Icons.Default.HomeWork,
                            enabled = !isEmergency && state.origen != "Selecciona Origen" && state.origen != "Compra Producto",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { expandedDestino = true }
                        )
                        DropdownMenu(
                            expanded = expandedDestino,
                            onDismissRequest = { expandedDestino = false },
                            modifier = Modifier.background(Color.White)
                        ) {
                            opcionesDestino.forEach { op ->
                                DropdownMenuItem(
                                    text = { Text(op, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        viewModel.actualizarDestino(op)
                                        expandedDestino = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- LISTADO DE PRODUCTOS ---
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.isLoading && catalogo.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RojoDelisa) }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(productosOrdenados, key = { it.id }) { producto ->
                            ItemProductoCargaModerno(
                                producto = producto,
                                cantidadActual = state.cantidades[producto.id] ?: 0,
                                stockDisponible = state.stockOrigen[producto.id] ?: 0,
                                esCompra = state.origen == "Compra Producto" || isEmergency || isLiquidationMode,
                                isAudit = isLiquidationMode,
                                onCantidadChange = { viewModel.actualizarCantidad(producto.id, it) },
                                onStockLimitReached = { mostrarAvisoStock(producto.nombre, state.stockOrigen[producto.id] ?: 0) }
                            )
                        }
                    }
                }
            }

            // --- RESUMEN Y ACCIÓN ---
            val totalMonto = catalogo.sumOf { (state.cantidades[it.id] ?: 0) * it.precio }
            val totalPiezas = state.cantidades.values.sum()
            val habilitarBoton = totalPiezas > 0 && (isLiquidationMode || (state.destino != "Selecciona Destino" && (isEmergency || state.origen != "Selecciona Origen")))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = if (isLiquidationMode && retornarABodega) RojoDelisa else if (isLiquidationMode) Color(0xFF1A1A1A) else NegroPremium),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text(
                                text = when {
                                    isLiquidationMode && retornarABodega -> "VALOR A RETORNAR"
                                    isLiquidationMode -> "VALOR AUDITADO"
                                    else -> "RESUMEN DE CARGA"
                                }, 
                                color = Color.White.copy(0.5f), 
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Bold, 
                                letterSpacing = 1.sp
                            )
                            Text(text = "$totalPiezas unidades", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }
                        Text(
                            text = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(totalMonto),
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { mostrarDialogConfirmacion = true },
                        enabled = habilitarBoton,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLiquidationMode && retornarABodega) Color.White else if (isLiquidationMode) Color.White else RojoDelisa,
                            contentColor = if (isLiquidationMode && retornarABodega) RojoDelisa else if (isLiquidationMode) Color.Black else Color.White,
                            disabledContainerColor = Color.White.copy(0.1f)
                        )
                    ) {
                        Icon(
                            imageVector = when {
                                isLiquidationMode && retornarABodega -> Icons.AutoMirrored.Filled.AssignmentReturn
                                isLiquidationMode -> Icons.AutoMirrored.Filled.FactCheck
                                isEmergency -> Icons.Default.FlashOn
                                else -> Icons.Default.CheckCircle
                            }, 
                            contentDescription = null
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = when {
                                isLiquidationMode && retornarABodega -> "VACIAR UNIDAD Y RETORNAR TODO"
                                isLiquidationMode -> "CORREGIR STOCK EN CAMIONETA"
                                isEmergency -> "CONFIRMAR CARGA DIRECTA"
                                else -> "ENVIAR CARGA"
                            }, 
                            fontWeight = FontWeight.Black, 
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    if (mostrarDialogConfirmacion) {
        val totalItems = state.cantidades.values.filter { it > 0 }.size
        DialogoConfirmacion(
            titulo = when {
                isLiquidationMode && retornarABodega -> "Confirmar Liquidación"
                isLiquidationMode -> "Confirmar Arqueo"
                isEmergency -> "Carga Directa"
                else -> "Confirmar Carga"
            },
            mensaje = when {
                isLiquidationMode && retornarABodega -> "¿Confirmas el retorno de $totalItems productos al almacén central? El vendedor quedará en cero."
                isLiquidationMode -> "¿Deseas ajustar el inventario de la ruta con estos $totalItems productos? Se registrarán posibles faltantes automáticamente."
                else -> "¿Estás seguro de transferir $totalItems productos a ${state.destino}?"
            },
            onConfirmar = {
                mostrarDialogConfirmacion = false
                val productosConCantidad = catalogo.filter { (state.cantidades[it.id] ?: 0) > 0 }
                if (!isPreview) {
                    if (isLiquidationMode) {
                        // En auditoría, el destino define si se queda en el mismo sitio o va a Huasteca
                        val destinoFinal = if (retornarABodega) "Almacen Huasteca" else state.origen
                        viewModel.confirmarCargaDirecta(state.origen, destinoFinal, productosConCantidad, state.cantidades, true, state.stockOrigen) {
                            Toast.makeText(context, if (retornarABodega) "Retorno procesado con éxito" else "Auditoría finalizada con éxito", Toast.LENGTH_LONG).show()
                            navController.popBackStack()
                        }
                    } else if (isEmergency) {
                        viewModel.confirmarCargaDirecta(state.origen, state.destino, productosConCantidad, state.cantidades) {
                            Toast.makeText(context, "Carga aplicada localmente", Toast.LENGTH_LONG).show()
                            navController.popBackStack()
                        }
                    } else {
                        viewModel.crearOrden(state.origen, state.destino, productosConCantidad, state.cantidades) { docId ->
                            val file = generarPdfMovimientoInventario(context, state.origen, state.destino, productosConCantidad, state.cantidades)
                            abrirPdfConFileProvider(context, file)
                            Toast.makeText(context, "Carga enviada con éxito", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onCancelar = { mostrarDialogConfirmacion = false }
        )
    }
}

@Composable
fun SelectorCard(
    titulo: String, seleccionado: String, placeholder: String, icon: androidx.compose.ui.graphics.vector.ImageVector, 
    enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1f, label = "")

    Card(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White else Color(0xFFEEEEEE)),
        border = if (seleccionado != placeholder && enabled) BorderStroke(2.dp, RojoDelisa.copy(0.3f)) else null,
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (seleccionado == placeholder) Color.Gray else RojoDelisa, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(titulo, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 0.5.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = seleccionado, 
                    fontSize = 12.sp, 
                    fontWeight = FontWeight.Black, 
                    color = if (seleccionado == placeholder) Color.LightGray else NegroPremium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (enabled) Icon(Icons.Outlined.ArrowDropDown, null, tint = Color.LightGray)
            }
        }
    }
}

@Composable
fun ItemProductoCargaModerno(
    producto: Plantilla_Producto, 
    cantidadActual: Int, 
    stockDisponible: Int, 
    esCompra: Boolean, 
    isAudit: Boolean = false,
    onCantidadChange: (Int) -> Unit,
    onStockLimitReached: () -> Unit
) {
    val seleccionado = cantidadActual > 0
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else if (seleccionado) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )
    val elevation by animateDpAsState(targetValue = if (seleccionado) 8.dp else 2.dp, label = "elevation")

    // 🔥 Lógica de color de texto para Auditoría
    val textColor = when {
        !isAudit -> NegroPremium
        cantidadActual == stockDisponible -> Color(0xFF2E7D32) // Verde Delisa / Bosque (Coincide)
        cantidadActual < stockDisponible -> Color.Red // Faltante
        else -> Color(0xFF2196F3) // Azul (Sobrante)
    }

    Card(
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = if (seleccionado) BorderStroke(1.5.dp, RojoDelisa.copy(alpha = 0.5f)) else null,
        elevation = CardDefaults.cardElevation(elevation)
    ) {
        Box {
            if (seleccionado) {
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

            Row(modifier = Modifier.padding(12.dp).padding(start = if (seleccionado) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(model = producto.imagenUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(18.dp)))
                    
                    // 🟢 MOSTRAR STOCK SIEMPRE (Como referencia en Compra, o como límite en Traspaso)
                    val colorBurbuja = if (esCompra) Color(0xFF2196F3) else if (stockDisponible > 0) Color(0xFF4CAF50) else Color.Red
                    
                    Surface(
                        color = colorBurbuja,
                        shape = CircleShape,
                        modifier = Modifier.size(32.dp).offset(x = 6.dp, y = 6.dp).border(2.5.dp, Color.White, CircleShape),
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = stockDisponible.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(producto.nombre, fontWeight = FontWeight.Black, fontSize = 15.sp, color = NegroPremium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(text = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(producto.precio), color = RojoDelisa, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (cantidadActual > 0) {
                        Text("Subtotal: " + NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(cantidadActual * producto.precio), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cantidadActual > 0) {
                        IconButton(onClick = { onCantidadChange(cantidadActual - 1) }, modifier = Modifier.size(32.dp).background(Color(0xFFF1F2F6), CircleShape)) { Icon(Icons.Default.Remove, null, tint = NegroPremium, modifier = Modifier.size(16.dp)) }
                        var textValue by remember(cantidadActual) { mutableStateOf(cantidadActual.toString()) }
                        BasicTextField(
                            value = if (textValue == "0") "" else textValue,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    textValue = newValue
                                    val newInt = newValue.toIntOrNull() ?: 0
                                    if (!esCompra && newInt > stockDisponible) {
                                        onStockLimitReached()
                                        onCantidadChange(stockDisponible)
                                    } else {
                                        onCantidadChange(newInt)
                                    }
                                }
                            },
                            modifier = Modifier.width(40.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Black, fontSize = 17.sp, color = textColor),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                    IconButton(onClick = { if (esCompra || cantidadActual < stockDisponible) onCantidadChange(cantidadActual + 1) else onStockLimitReached() }, modifier = Modifier.size(38.dp).background(RojoDelisa, CircleShape)) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
}

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
    canvas.drawText("ORDEN DE CARGA DELISA", 100f, y, paint)
    y += 30f
    canvas.drawText("Origen: $origen", 20f, y, paint)
    y += 20f
    canvas.drawText("Destino: $destino", 20f, y, paint)
    y += 30f
    productos.forEach { p ->
        val cant = cantidades[p.id] ?: 0
        canvas.drawText("$cant x ${p.nombre}", 20f, y, paint)
        y += 20f
    }
    pdfDocument.finishPage(page)
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "carga_${System.currentTimeMillis()}.pdf")
    pdfDocument.writeTo(FileOutputStream(file))
    pdfDocument.close()
    return file
}

fun abrirPdfConFileProvider(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir PDF"))
    } catch (e: Exception) { }
}

@Preview(showBackground = true)
@Composable
fun MovimientosInventarioPreview() {
    DeliveryTheme {
        MovimientosInventarioScreen(navController = rememberNavController())
    }
}
