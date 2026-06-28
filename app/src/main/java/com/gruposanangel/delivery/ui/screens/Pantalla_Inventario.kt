@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.text.NumberFormat
import java.util.*

@Composable
fun PantallaInventario(
    navController: NavController,
    inventarioRepo: RepositoryInventario
) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val firebaseDataSource = FirebaseDataSource()
    val repoUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())

    val viewModel: InventarioViewModel = viewModel(
        factory = InventarioViewModelFactory(inventarioRepo, repoUsuario)
    )

    val uiState by viewModel.uiState.collectAsState()

    PantallaInventarioContent(
        navController = navController,
        uiState = uiState,
        onNotificacionClick = { navController.navigate("NOTIFICACIONES") },
        onAlmacenSelect = { viewModel.seleccionarAlmacen(it) },
        onVistaGlobalClick = { viewModel.activarVistaGlobal() }
    )
}

@Composable
fun PantallaInventarioContent(
    navController: NavController, // 🔥 Añadido
    uiState: InventarioUiState,
    onNotificacionClick: () -> Unit,
    onAlmacenSelect: (String) -> Unit = {},
    onVistaGlobalClick: () -> Unit = {}
) {
    var verDanado by remember { mutableStateOf(false) }
    val productosAMostrar = if (verDanado) uiState.productosDanados else uiState.productos
    
    val totalProductos = productosAMostrar.sumOf { it.cantidad }
    val valorTotal = productosAMostrar.sumOf { it.cantidad * it.precio }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 🔹 CABECERA ADMINISTRATIVA (Solo si es Admin)
            if (uiState.isAdmin) {
                AdminInventoryHeader(
                    almacenes = uiState.listaAlmacenes,
                    seleccionado = uiState.almacenSeleccionado,
                    esVistaGlobal = uiState.esVistaGlobal,
                    onSelect = onAlmacenSelect,
                    onGlobal = onVistaGlobalClick
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            ResumenInventario(
                totalProductos = totalProductos,
                valorTotal = valorTotal,
                notificacionesCount = uiState.notificaciones.size,
                onNotificacionClick = onNotificacionClick,
                titulo = if (verDanado) "DEVOLUCIONES/DAÑADOS" else (uiState.almacenSeleccionado ?: "MI INVENTARIO")
            )

            // 🔹 BOTÓN DE ARQUEO / LIQUIDACIÓN (Solo para Admins viendo a un Vendedor)
            if (uiState.isAdmin && uiState.almacenSeleccionado?.startsWith("Vendedor") == true && !uiState.esVistaGlobal) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val origen = uiState.almacenSeleccionado
                        navController.navigate("LISTA PRODUCTOS?origen=$origen&destino=Almacen Huasteca&isLiquidation=true")
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)), // Negro Elegante
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Analytics, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("REALIZAR ARQUEO / LIQUIDACIÓN", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }

            // 🔹 BOTÓN DE AJUSTE DE ALMACÉN (Solo para CEO/Gerente en Almacen Huasteca)
            val puestoAdmin = uiState.puestoTrabajo?.trim() ?: ""
            val esDirectivo = puestoAdmin == "CEO" || puestoAdmin == "Gerente General"
            if (esDirectivo && uiState.almacenSeleccionado == "Almacen Huasteca" && !uiState.esVistaGlobal) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        navController.navigate("LISTA PRODUCTOS?origen=Almacen Huasteca&destino=Almacen Huasteca&isLiquidation=true")
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)), // Negro Elegante
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Analytics, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("REALIZAR AJUSTE DE ALMACÉN", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 🔹 SELECTOR DE TIPO (Bueno vs Dañado)
            if (!uiState.esVistaGlobal) {
                TabSelectorInventario(
                    seleccionado = verDanado,
                    onToggle = { verDanado = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (uiState.isLoading) {
                    InventarioSkeleton()
                } else if (uiState.rutaAsignada == null && !uiState.isAdmin) {
                    Text("Usuario sin ruta asignada", color = Color.Gray, fontWeight = FontWeight.Bold)
                } else if (productosAMostrar.isEmpty()) {
                    val msg = if (verDanado) "Sin devoluciones registradas" else "Sin stock disponible"
                    Text(msg, color = Color.Gray, fontWeight = FontWeight.Bold)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(), 
                        verticalArrangement = Arrangement.spacedBy(10.dp), 
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(productosAMostrar, key = { it.id + (if (verDanado) "_bad" else "_good") }) { producto ->
                            ItemProductoInventario(producto, esDanado = verDanado)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminInventoryHeader(
    almacenes: List<String>,
    seleccionado: String?,
    esVistaGlobal: Boolean,
    onSelect: (String) -> Unit,
    onGlobal: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label = "headerScale"
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Selector de Almacén Premium
        Card(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(if (isPressed) 2.dp else 4.dp, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material.ripple.rememberRipple(color = Color.Red.copy(alpha = 0.1f)),
                    onClick = { expanded = true }
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = if (expanded) BorderStroke(1.5.dp, Color.Red) else null
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Store, 
                        null, 
                        tint = if (expanded) Color.Red else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "FILTRAR POR UNIDAD", 
                            fontSize = 8.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = Color.Gray,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = seleccionado ?: "Seleccionar...", 
                            fontSize = 14.sp, 
                            fontWeight = FontWeight.ExtraBold, 
                            color = if (esVistaGlobal) Color.DarkGray else Color.Red, 
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, 
                    null, 
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(surface = Color.White),
                shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp))
            ) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .background(Color.White)
                ) {
                    almacenes.forEach { alm ->
                        val isSel = alm == seleccionado && !esVistaGlobal
                        DropdownMenuItem(
                            text = { 
                                Text(
                                    alm, 
                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSel) Color.Red else Color.DarkGray
                                ) 
                            },
                            leadingIcon = {
                                Icon(
                                    if (alm.startsWith("Vendedor")) Icons.Default.LocalShipping else Icons.Default.Warehouse,
                                    null,
                                    tint = if (isSel) Color.Red else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = { 
                                onSelect(alm)
                                expanded = false 
                            },
                            modifier = Modifier.background(if (isSel) Color.Red.copy(alpha = 0.05f) else Color.Transparent)
                        )
                    }
                }
            }
        }

        // Botón Vista Global Premium
        Surface(
            onClick = onGlobal,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (esVistaGlobal) Color.Red else Color.White,
            shadowElevation = 4.dp,
            border = if (!esVistaGlobal) BorderStroke(1.dp, Color(0xFFEEEEEE)) else null
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Public, 
                    contentDescription = "Global", 
                    tint = if (esVistaGlobal) Color.White else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun TabSelectorInventario(seleccionado: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFFF1F2F6), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        val interactionSourceLeft = remember { MutableInteractionSource() }
        val interactionSourceRight = remember { MutableInteractionSource() }
        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(if (!seleccionado) Color.White else Color.Transparent)
                .clickable(
                    interactionSource = interactionSourceLeft,
                    indication = androidx.compose.material.ripple.rememberRipple(color = Color.Red.copy(alpha = 0.1f)),
                    onClick = { onToggle(false) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "EN CAMIONETA", 
                fontSize = 12.sp, 
                fontWeight = if (!seleccionado) FontWeight.ExtraBold else FontWeight.Bold, 
                color = if (!seleccionado) Color.Red else Color.Gray
            )
        }
        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(if (seleccionado) Color.White else Color.Transparent)
                .clickable(
                    interactionSource = interactionSourceRight,
                    indication = androidx.compose.material.ripple.rememberRipple(color = Color.Red.copy(alpha = 0.1f)),
                    onClick = { onToggle(true) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "DEVOLUCIONES", 
                fontSize = 12.sp, 
                fontWeight = if (seleccionado) FontWeight.ExtraBold else FontWeight.Bold, 
                color = if (seleccionado) Color.Red else Color.Gray
            )
        }
    }
}

@Composable
fun ResumenInventario(
    totalProductos: Int, 
    valorTotal: Double, 
    notificacionesCount: Int, 
    onNotificacionClick: () -> Unit,
    titulo: String = "MI INVENTARIO"
) {
    val nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    Card(
        shape = RoundedCornerShape(24.dp), 
        colors = CardDefaults.cardColors(containerColor = Color.Red), 
        elevation = CardDefaults.cardElevation(4.dp), 
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Red, Color(0xFFB71C1C)))).padding(16.dp)) {
            Column {
                Text(titulo.uppercase(), color = Color.White.copy(0.8f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. TOTAL PIEZAS (Izquierda - Ocupa el 50% del espacio sobrante)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TOTAL PIEZAS", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("$totalProductos", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    }

                    // 2. BOTÓN NOTIFICACIONES (Centro Absoluto)
                    Box(
                        modifier = Modifier.wrapContentWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        FloatingActionButton(
                            onClick = onNotificacionClick, 
                            containerColor = Color.White, 
                            contentColor = Color.Red, 
                            shape = CircleShape, 
                            modifier = Modifier.size(46.dp),
                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                        ) {
                            Icon(Icons.Default.Notifications, null, modifier = Modifier.size(24.dp))
                        }
                        if (notificacionesCount > 0) {
                            Surface(
                                color = Color(0xFF00AAFF),
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .size(20.dp),
                                border = BorderStroke(2.dp, Color.Red)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = notificacionesCount.toString(), 
                                        color = Color.White, 
                                        fontSize = 10.sp, 
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }

                    // 3. VALOR TOTAL (Derecha - Ocupa el 50% del espacio sobrante)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("VALOR TOTAL", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = nf.format(valorTotal), 
                            color = Color.White, 
                            fontSize = 18.sp, 
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ItemProductoInventario(producto: Plantilla_Producto, esDanado: Boolean = false) {
    val totalProducto = producto.cantidad * producto.precio
    val nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    Card(
        shape = RoundedCornerShape(20.dp), 
        colors = CardDefaults.cardColors(containerColor = Color.White), 
        elevation = CardDefaults.cardElevation(if (esDanado) 1.dp else 2.dp), 
        modifier = Modifier.fillMaxWidth(),
        border = if (esDanado) BorderStroke(1.dp, Color.Red.copy(0.2f)) else null
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.BottomEnd) {
                AsyncImage(model = producto.imagenUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = producto.nombre, contentScale = ContentScale.Crop, modifier = Modifier.size(75.dp).clip(RoundedCornerShape(16.dp)))
                if (esDanado) {
                    Box(Modifier.background(Color.Red, CircleShape).padding(4.dp)) {
                        Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = producto.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (esDanado) Color.Red else Color.DarkGray)
                Spacer(Modifier.height(4.dp))
                Text(text = "Unitario: ${nf.format(producto.precio)}", fontSize = 13.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "${producto.cantidad} pzas", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.Red)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = nf.format(totalProducto), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.DarkGray)
            }
        }
    }
}

@Composable
fun InventarioSkeleton() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(24.dp)).background(Color.LightGray.copy(0.3f)))
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(24.dp)).background(Color.LightGray.copy(0.3f)))
        repeat(4) { Box(Modifier.fillMaxWidth().height(85.dp).clip(RoundedCornerShape(20.dp)).background(Color.LightGray.copy(0.3f))) }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Inventario - Lista")
@Composable
fun PantallaInventarioPreview() {
    val productos = listOf(
        Plantilla_Producto("1", "Papas Delisa Adobadas", 15.0, 120),
        Plantilla_Producto("2", "Gomitas de Tamarindo", 12.0, 85)
    )
    DeliveryTheme {
        PantallaInventarioContent(
            navController = rememberNavController(),
            uiState = InventarioUiState(isLoading = false, productos = productos, rutaAsignada = "Ruta 1"),
            onNotificacionClick = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Inventario - Cargando")
@Composable
fun PantallaInventarioLoadingPreview() {
    DeliveryTheme {
        PantallaInventarioContent(
            navController = rememberNavController(),
            uiState = InventarioUiState(isLoading = true), 
            onNotificacionClick = {}
        )
    }
}
