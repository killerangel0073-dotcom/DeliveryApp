@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ArrowDropDown
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
import com.gruposanangel.delivery.ui.theme.*
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.*

@Composable
fun MovimientosInventarioScreen(
    navController: NavController,
    impresoraBluetooth: BluetoothDevice? = null,
    onImpresoraSeleccionada: (BluetoothDevice) -> Unit = {},
    preSelectedOrigen: String? = null,
    preSelectedDestino: String? = null,
    isEmergency: Boolean = false,
    isTabMode: Boolean = false,
    editOrderId: String? = null
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    val viewModel: MovimientosViewModel = viewModel(
        factory = remember {
            val db = AppDatabase.getDatabase(context.applicationContext)
            val firebaseDataSource = FirebaseDataSource()
            val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
            val usuarioRepo = RepositoryUsuario(firebaseDataSource, db.usuarioDao())
            MovimientosViewModelFactory(inventarioRepo, usuarioRepo, context.applicationContext)
        }
    )

    val state by viewModel.uiState.collectAsState()
    val catalogo by viewModel.catalogoProductos.collectAsState()

    var expandedOrigen by remember { mutableStateOf(false) }
    var expandedDestino by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // 🔥 ACTUALIZACIÓN: Sincronizar parámetros de navegación si se proporcionan
    LaunchedEffect(preSelectedOrigen, preSelectedDestino, editOrderId) {
        if (editOrderId != null) {
            viewModel.cargarOrdenParaEditar(editOrderId)
        } else {
            if (preSelectedOrigen != null && preSelectedOrigen != state.origen) {
                viewModel.actualizarOrigen(preSelectedOrigen)
            }
            if (preSelectedDestino != null && preSelectedDestino != state.destino) {
                viewModel.actualizarDestino(preSelectedDestino)
            }
        }
    }

    LaunchedEffect(state.origen, state.stockOrigen, state.isLoading) {
        if (catalogo.isNotEmpty() && !state.isLoading) {
            listState.scrollToItem(0)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var ultimoAvisoStock by remember { mutableLongStateOf(0L) }
    
    val mostrarAvisoStock: (Int) -> Unit = { disponible ->
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

    val opcionesOrigen = remember(state.listaAlmacenes, isTabMode, state.isAdmin, state.isAlmacenRole) {
        if (state.isAdmin) {
            listOf("Almacen Huasteca", "Compra Producto")
        } else if (state.isAlmacenRole) {
            listOf("Almacen Huasteca")
        } else {
            val base = state.listaAlmacenes.filter { !it.startsWith("Vendedor") && it != "Compra Producto" }
            if (isTabMode) base else listOf("Compra Producto") + base
        }
    }
    
    val opcionesDestino = remember(state.listaAlmacenes, state.origen) {
        when {
            state.origen == "Compra Producto" -> state.listaAlmacenes.filter { !it.startsWith("Vendedor") && it != "Compra Producto" }
            state.origen != "Selecciona Origen" -> state.listaAlmacenes.filter { it.startsWith("Vendedor") && it != state.origen }
            else -> emptyList()
        }
    }

    var mostrarDialogConfirmacion by remember { mutableStateOf(false) }

    val productosOrdenados = remember(catalogo, state.stockOrigen, state.origen, state.cantidades) {
        catalogo.sortedWith(
            compareBy<Plantilla_Producto> { it.categoria }
                .thenBy { it.nombre }
        )
    }

    val productosPorCategoria = remember(productosOrdenados) {
        productosOrdenados.groupBy { it.categoria }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
            // 🔹 CABECERA PREMIUM
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .shadow(2.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isTabMode) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed)
                            }
                        }
                        Text(
                            text = if (isTabMode) "SURTIR VENDEDORES" else "GESTIÓN DE CARGAS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    TextButton(onClick = { viewModel.limpiarPantalla() }) {
                        Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text("VACIAR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f).wrapContentSize(Alignment.TopStart)) {
                        SelectorCard(
                            titulo = "ORIGEN / VENDEDOR",
                            seleccionado = state.origen,
                            placeholder = "Seleccionar",
                            icon = Icons.Default.Person,
                            enabled = !isEmergency && state.isAdmin, // 🔥 Solo habilitado para Administradores
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { expandedOrigen = true }
                        )
                    DropdownMenu(expanded = expandedOrigen, onDismissRequest = { expandedOrigen = false }) {
                        opcionesOrigen.forEach { op ->
                            DropdownMenuItem(text = { Text(op) }, onClick = { viewModel.actualizarOrigen(op); expandedOrigen = false })
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).wrapContentSize(Alignment.TopStart)) {
                    SelectorCard(
                        titulo = "DESTINO",
                        seleccionado = state.destino,
                        placeholder = "Seleccionar",
                        icon = Icons.Default.HomeWork,
                        enabled = !isEmergency && state.origen != "Selecciona Origen",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { expandedDestino = true }
                    )
                    DropdownMenu(expanded = expandedDestino, onDismissRequest = { expandedDestino = false }) {
                        opcionesDestino.forEach { op ->
                            DropdownMenuItem(text = { Text(op) }, onClick = { viewModel.actualizarDestino(op); expandedDestino = false })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (state.isLoading && catalogo.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DelisaRed) }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // verticalArrangement = Arrangement.spacedBy(12.dp), // Eliminado para manejar espaciado manual con headers
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        productosPorCategoria.forEach { (categoria, productos) ->
                            item(key = "header_$categoria") {
                                CategoryHeader(categoria)
                            }
                            
                            items(productos, key = { it.id }) { producto ->
                                ItemProductoCargaModerno(
                                    producto = producto,
                                    cantidadActual = state.cantidades[producto.id] ?: 0,
                                    stockDisponible = state.stockOrigen[producto.id] ?: 0,
                                    esCompra = state.origen == "Compra Producto" || isEmergency,
                                    isAudit = false,
                                    onCantidadChange = { viewModel.actualizarCantidad(producto.id, it) },
                                    onStockLimitReached = { mostrarAvisoStock(state.stockOrigen[producto.id] ?: 0) }
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

            val totalMonto = catalogo.sumOf { (state.cantidades[it.id] ?: 0) * it.precio }
            val totalPiezas = state.cantidades.values.sum()
            val habilitarBoton = totalPiezas > 0 && 
                                (state.destino != "Selecciona Destino" && (isEmergency || state.origen != "Selecciona Origen")) &&
                                !state.isLoading // 🔥 Bloquear si ya está cargando

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .shadow(12.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = DelisaRed)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(DelisaRed, DelisaRedDark)))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Column {
                                Text("RESUMEN DE CARGA", color = Color.White.copy(0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(text = "$totalPiezas unidades", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                            Text(text = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(totalMonto), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { mostrarDialogConfirmacion = true },
                            enabled = habilitarBoton,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White, 
                                contentColor = DelisaRed,
                                disabledContainerColor = Color.White.copy(alpha = 0.3f),
                                disabledContentColor = Color.White.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(imageVector = if (isEmergency) Icons.Default.FlashOn else Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(text = if (isEmergency) "CONFIRMAR CARGA DIRECTA" else "ENVIAR CARGA", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }

    if (mostrarDialogConfirmacion) {
        DialogoConfirmacion(
            titulo = if (isEmergency) "Carga Directa" else "Confirmar Carga",
            mensaje = "¿Estás seguro de transferir los productos a ${state.destino}?",
            onConfirmar = {
                mostrarDialogConfirmacion = false
                val productosConCantidad = catalogo.filter { (state.cantidades[it.id] ?: 0) > 0 }
                if (isEmergency) {
                    viewModel.confirmarCargaDirecta(state.origen, state.destino, productosConCantidad, state.cantidades) {
                        Toast.makeText(context, "Carga aplicada localmente", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                } else {
                    viewModel.crearOrden(state.origen, state.destino, productosConCantidad, state.cantidades) { 
                        Toast.makeText(context, "Carga enviada con éxito", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                }
            },
            onCancelar = { mostrarDialogConfirmacion = false }
        )
    }

    // 🔥 OVERLAY DE CARGA (Bloqueo de pantalla y feedback visual)
    if (state.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                .clickable(enabled = false) {}, // Bloquear toques
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = DelisaRed, strokeWidth = 5.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (isEmergency) "Procesando Carga Directa..." else "Enviando Carga a la Nube...",
                    fontWeight = FontWeight.Bold,
                    color = DelisaRed
                )
                Text(
                    text = "Por favor, espera un momento.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SelectorCard(
    titulo: String, 
    seleccionado: String, 
    placeholder: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    enabled: Boolean, 
    modifier: Modifier = Modifier, 
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "selectorScale"
    )

    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (isPressed) 1.dp else 2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(color = DelisaRed.copy(alpha = 0.1f)),
                enabled = enabled,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = if (seleccionado == placeholder) MaterialTheme.colorScheme.onSurfaceVariant else DelisaRed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(titulo, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(text = seleccionado, fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ItemProductoCargaModerno(producto: Plantilla_Producto, cantidadActual: Int, stockDisponible: Int, esCompra: Boolean, isAudit: Boolean = false, onCantidadChange: (Int) -> Unit, onStockLimitReached: () -> Unit) {
    val seleccionado = cantidadActual > 0
    val textColor = when {
        !isAudit -> MaterialTheme.colorScheme.onSurface
        cantidadActual < stockDisponible -> WarningOrange
        cantidadActual > stockDisponible -> DelisaRed
        else -> DelisaGreenDark
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (seleccionado) 8.dp else 2.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (seleccionado) BorderStroke(1.5.dp, DelisaRed.copy(0.5f)) else null
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.BottomEnd) {
                AsyncImage(model = producto.imagenUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(18.dp)))
                Surface(color = if (stockDisponible > 0) DelisaGreen else DelisaRed, shape = CircleShape, modifier = Modifier.size(30.dp).offset(x = 5.dp, y = 5.dp).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)) {
                    Box(contentAlignment = Alignment.Center) { Text(text = stockDisponible.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black) }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(producto.nombre, fontWeight = FontWeight.Black, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(text = NumberFormat.getCurrencyInstance(Locale("es", "MX")).format(producto.precio), color = DelisaRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cantidadActual > 0) {
                    IconButton(onClick = { onCantidadChange(cantidadActual - 1) }, modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface) }
                    BasicTextField(
                        value = if (cantidadActual == 0) "" else cantidadActual.toString(),
                        onValueChange = { val n = it.toIntOrNull() ?: 0; if (esCompra || n <= stockDisponible) onCantidadChange(n) else onStockLimitReached() },
                        modifier = Modifier.width(35.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Black, fontSize = 16.sp, color = textColor),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                IconButton(onClick = { if (esCompra || cantidadActual < stockDisponible) onCantidadChange(cantidadActual + 1) else onStockLimitReached() }, modifier = Modifier.size(38.dp).background(DelisaRed, CircleShape)) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}
