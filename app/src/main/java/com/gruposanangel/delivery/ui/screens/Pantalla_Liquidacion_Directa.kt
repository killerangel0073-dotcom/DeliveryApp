@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion
import com.gruposanangel.delivery.ui.theme.*
import java.text.NumberFormat
import java.util.*

private val RojoDelisa = Color(0xFFE53935)
private val NegroPremium = Color(0xFF1E1E24)

@Composable
fun PantallaLiquidacionDirecta(
    navController: NavController,
    origen: String,
    destino: String
) {
    val context = LocalContext.current
    val viewModel: LiquidacionViewModel = viewModel(
        factory = remember(origen, destino) {
            val db = AppDatabase.getDatabase(context.applicationContext)
            val firebaseDataSource = FirebaseDataSource()
            val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
            val usuarioRepo = RepositoryUsuario(firebaseDataSource, db.usuarioDao())
            LiquidacionViewModelFactory(inventarioRepo, usuarioRepo, context.applicationContext, origen, destino)
        }
    )

    val state by viewModel.uiState.collectAsState()
    val catalogo by viewModel.catalogoProductos.collectAsState()
    var retornarABodega by remember { mutableStateOf(false) }
    var mostrarConfirmacion by remember { mutableStateOf(false) }
    var mostrarExito by remember { mutableStateOf(false) }

    val interactionSourceArqueo = remember { MutableInteractionSource() }
    val isPressedArqueo by interactionSourceArqueo.collectIsPressedAsState()
    val scaleArqueo by animateFloatAsState(targetValue = if (isPressedArqueo) 0.96f else 1f, label = "scaleArqueo")

    val interactionSourceLiquidar = remember { MutableInteractionSource() }
    val isPressedLiquidar by interactionSourceLiquidar.collectIsPressedAsState()
    val scaleLiquidar by animateFloatAsState(targetValue = if (isPressedLiquidar) 0.96f else 1f, label = "scaleLiquidar")

    val listState = rememberLazyListState()
    val isDark = ThemeConfig.isActuallyDark

    // 🔥 Reiniciar scroll al principio cuando cambia el modo o se cargan los datos
    LaunchedEffect(retornarABodega, state.isLoading, state.stockTeorico) {
        if (!state.isLoading && catalogo.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(origen, destino) {
        viewModel.inicializar(origen, destino)
    }

    DeliveryTheme(darkTheme = isDark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("ARQUEO / LIQUIDACIÓN", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RojoDelisa)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refrescarDatos() }) {
                            Icon(Icons.Default.Sync, null, tint = RojoDelisa)
                        }
                        TextButton(
                            onClick = { viewModel.restablecerStockTeorico() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("REINICIAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                
                Spacer(Modifier.height(16.dp))

                Text(
                    "MODALIDAD DE AUDITORÍA", 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Black, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))

                // Selector de Modo con protagonismo para Arqueo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // MODO ARQUEO (90% de uso)
                    Card(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(85.dp)
                            .graphicsLayer {
                                scaleX = scaleArqueo
                                scaleY = scaleArqueo
                            }
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = interactionSourceArqueo,
                                indication = androidx.compose.material.ripple.rememberRipple(color = RojoDelisa.copy(alpha = 0.1f)),
                                onClick = { retornarABodega = false }
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!retornarABodega) RojoDelisa else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(if (!retornarABodega) 4.dp else 0.dp)
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            if (!retornarABodega) {
                                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                            Column(Modifier.padding(12.dp).align(Alignment.CenterStart)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.FactCheck, 
                                        null, 
                                        tint = if (!retornarABodega) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "ARQUEO", 
                                        fontWeight = FontWeight.Black, 
                                        fontSize = 14.sp,
                                        color = if (!retornarABodega) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Corregir diferencias físicamente", 
                                    fontSize = 11.sp, 
                                    lineHeight = 13.sp,
                                    color = if (!retornarABodega) Color.White.copy(0.9f) else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "PRODUCTO SE QUEDA EN RUTA", 
                                    fontSize = 10.sp, 
                                    color = if (!retornarABodega) Color.White.copy(0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    // MODO LIQUIDACIÓN (10% de uso)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(85.dp)
                            .graphicsLayer {
                                scaleX = scaleLiquidar
                                scaleY = scaleLiquidar
                            }
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(
                                interactionSource = interactionSourceLiquidar,
                                indication = androidx.compose.material.ripple.rememberRipple(color = NegroPremium.copy(alpha = 0.1f)),
                                onClick = { retornarABodega = true }
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (retornarABodega) NegroPremium else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(if (retornarABodega) 4.dp else 0.dp)
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.AssignmentReturn, 
                                    null, 
                                    tint = if (retornarABodega) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "LIQUIDAR", 
                                    fontWeight = FontWeight.Black, 
                                    fontSize = 12.sp,
                                    color = if (retornarABodega) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Vaciar Camioneta", 
                                fontSize = 11.sp, 
                                lineHeight = 13.sp,
                                color = if (retornarABodega) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "PRODUCTO SE VA A ALMACÉN", 
                                fontSize = 10.sp, 
                                color = if (retornarABodega) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Información del Almacén Auditado (Compacto)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalShipping, null, tint = RojoDelisa, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("AUDITANDO:", fontSize = 9.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(state.origen, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                        }

                        if (retornarABodega) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Spacer(Modifier.height(8.dp))
                            
                            var expandedDestino by remember { mutableStateOf(false) }
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(enabled = false) { expandedDestino = true }
                            ) {
                                Icon(Icons.Default.Warehouse, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("RETORNA A:", fontSize = 9.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Almacen Huasteca", 
                                    fontSize = 12.sp, 
                                    fontWeight = FontWeight.ExtraBold, 
                                    color = RojoDelisa,
                                    modifier = Modifier.weight(1f)
                                )
                                // Icon(Icons.Default.ArrowDropDown, null, tint = RojoDelisa) // 🔥 Oculto porque ya no es seleccionable
                            }

                            DropdownMenu(
                                expanded = expandedDestino,
                                onDismissRequest = { expandedDestino = false },
                                modifier = Modifier.fillMaxWidth(0.8f).background(MaterialTheme.colorScheme.surface)
                            ) {
                                state.listaAlmacenes.forEach { alm ->
                                    DropdownMenuItem(
                                        text = { Text(alm, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            viewModel.seleccionarDestino(alm)
                                            expandedDestino = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Listado de Productos
                val productosOrdenados = remember(catalogo, state.stockTeorico, state.cantidadesAuditadas) {
                    catalogo.sortedWith(
                        compareBy<Plantilla_Producto> { it.categoria }
                            .thenBy { it.nombre }
                    )
                }

                val productosPorCategoria = remember(productosOrdenados) {
                    productosOrdenados.groupBy { it.categoria }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (state.isLoading && catalogo.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = RojoDelisa) }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            // verticalArrangement = Arrangement.spacedBy(10.dp), // 🔥 Eliminado para manejar headers
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            productosPorCategoria.forEach { (categoria, productos) ->
                                item(key = "header_$categoria") {
                                    CategoryHeader(categoria)
                                }
                                
                                items(productos, key = { it.id }) { producto ->
                                    val fisico = state.cantidadesAuditadas[producto.id] ?: 0
                                    val teorico = state.stockTeorico[producto.id] ?: 0
                                    
                                    ItemProductoCargaModerno(
                                        producto = producto,
                                        cantidadActual = fisico,
                                        stockDisponible = teorico,
                                        esCompra = true, // Permite libre edición en auditoría
                                        isAudit = true,
                                        onCantidadChange = { viewModel.actualizarCantidadAuditada(producto.id, it) },
                                        onStockLimitReached = {}
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }

                // Pie con Totales
                val totalFisico = catalogo.sumOf { (state.cantidadesAuditadas[it.id] ?: 0) * it.precio }
                val totalTeorico = catalogo.sumOf { (state.stockTeorico[it.id] ?: 0) * it.precio }
                val diferencia = totalFisico - totalTeorico
                val colorModoAudit = if (retornarABodega) NegroPremium else RojoDelisa

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = colorModoAudit),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Column {
                                Text("VALOR EN FÍSICO", color = Color.White.copy(0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")).format(totalFisico), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("DIFERENCIA", color = Color.White.copy(0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                val colorTextoDiferencia = when {
                                    diferencia < 0 -> Color(0xFFFFB300) // Amarillo (Menor)
                                    diferencia > 0 -> Color.Red         // Rojo (Mayor)
                                    else -> Color(0xFF2E7D32)           // Verde (Igual)
                                }
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = (if (diferencia > 0) "+" else "") + NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")).format(diferencia),
                                        color = colorTextoDiferencia,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        
                        val puedeConfirmar = !retornarABodega || state.destino.isNotBlank()
                        
                        Button(
                            onClick = { mostrarConfirmacion = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = puedeConfirmar,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White, 
                                contentColor = colorModoAudit,
                                disabledContainerColor = Color.White.copy(0.5f),
                                disabledContentColor = colorModoAudit.copy(0.5f)
                            )
                        ) {
                            Icon(if (retornarABodega) Icons.AutoMirrored.Filled.AssignmentReturn else Icons.AutoMirrored.Filled.FactCheck, null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (retornarABodega) "CONFIRMAR LIQUIDACIÓN Y RETORNO" else "CONFIRMAR AJUSTE DE ARQUEO", 
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            // 🔥 OVERLAY DE CARGA (Para que no se quede la pantalla "en blanco" o sin respuesta)
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                        .clickable(enabled = false) {}, // Bloquear toques
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = RojoDelisa, strokeWidth = 5.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (retornarABodega) "Procesando Liquidación..." else "Aplicando Ajustes...",
                            fontWeight = FontWeight.Bold,
                            color = RojoDelisa
                        )
                    }
                }
            }
        }
    }

    if (mostrarConfirmacion) {
        DialogoConfirmacion(
            titulo = if (retornarABodega) "Confirmar Liquidación" else "Confirmar Arqueo",
            mensaje = if (retornarABodega) 
                "¿Estás seguro de vaciar la unidad ${state.origen} y retornar todo el stock al Almacen Huasteca?" 
                else "¿Confirmas los ajustes realizados? Se generarán movimientos de sobrantes/faltantes según el conteo.",
            onConfirmar = {
                mostrarConfirmacion = false
                viewModel.confirmarAuditoria(retornarABodega) {
                    mostrarExito = true
                }
            },
            onCancelar = { mostrarConfirmacion = false }
        )
    }

    if (mostrarExito) {
        AlertDialog(
            onDismissRequest = { 
                mostrarExito = false
                navController.popBackStack() 
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle, 
                    contentDescription = null, 
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("¡Operación Exitosa!", fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface) },
            text = { 
                Text(
                    text = if (retornarABodega) 
                        "La unidad ha sido liquidada. La mercancía se ha descontado del vendedor y se ha retornado correctamente al Almacen Huasteca."
                        else "El arqueo ha sido finalizado. Los ajustes de inventario (sobrantes y faltantes) han sido aplicados con éxito.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            confirmButton = {
                Button(
                    onClick = { 
                        mostrarExito = false
                        navController.popBackStack() 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RojoDelisa),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ACEPTAR Y SALIR", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
