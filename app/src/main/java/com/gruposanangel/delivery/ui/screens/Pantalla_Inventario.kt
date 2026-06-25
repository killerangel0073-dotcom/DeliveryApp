@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
        uiState = uiState,
        onNotificacionClick = { navController.navigate("NOTIFICACIONES") },
        onAlmacenSelect = { viewModel.seleccionarAlmacen(it) },
        onVistaGlobalClick = { viewModel.activarVistaGlobal() }
    )
}

@Composable
fun PantallaInventarioContent(
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
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Selector de Almacén
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { expanded = true },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ALMACÉN / VENDEDOR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text(seleccionado ?: "Seleccionar...", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.Red, maxLines = 1)
                }
                Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray)
            }
            
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                almacenes.forEach { alm ->
                    DropdownMenuItem(
                        text = { Text(alm, fontWeight = FontWeight.Medium) },
                        onClick = { onSelect(alm); expanded = false }
                    )
                }
            }
        }

        // Botón Vista Global
        IconButton(
            onClick = onGlobal,
            modifier = Modifier
                .size(48.dp)
                .background(if (esVistaGlobal) Color.Red else Color.White, RoundedCornerShape(12.dp))
        ) {
            Icon(
                Icons.Default.Public, 
                contentDescription = "Global", 
                tint = if (esVistaGlobal) Color.White else Color.Gray
            )
        }
    }
}

@Composable
fun TabSelectorInventario(seleccionado: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color(0xFFEEEEEE), RoundedCornerShape(10.dp))
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (!seleccionado) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable { onToggle(false) },
            contentAlignment = Alignment.Center
        ) {
            Text("EN CAMIONETA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (!seleccionado) Color.Red else Color.Gray)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (seleccionado) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                .clickable { onToggle(true) },
            contentAlignment = Alignment.Center
        ) {
            Text("DEVOLUCIONES", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (seleccionado) Color.Red else Color.Gray)
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
            uiState = InventarioUiState(isLoading = false, productos = productos, rutaAsignada = "Ruta 1"),
            onNotificacionClick = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Inventario - Cargando")
@Composable
fun PantallaInventarioLoadingPreview() {
    DeliveryTheme {
        PantallaInventarioContent(uiState = InventarioUiState(isLoading = true), onNotificacionClick = {})
    }
}
