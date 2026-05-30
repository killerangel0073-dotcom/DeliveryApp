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
import androidx.compose.material.icons.filled.Notifications
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
        onNotificacionClick = { navController.navigate("NOTIFICACIONES") }
    )
}

@Composable
fun PantallaInventarioContent(
    uiState: InventarioUiState,
    onNotificacionClick: () -> Unit
) {
    val totalProductos = uiState.productos.sumOf { it.cantidad }
    val valorTotal = uiState.productos.sumOf { it.cantidad * it.precio }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            ResumenInventario(
                totalProductos = totalProductos,
                valorTotal = valorTotal,
                notificacionesCount = uiState.notificaciones.size,
                onNotificacionClick = onNotificacionClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (uiState.isLoading) {
                    InventarioSkeleton()
                } else if (uiState.rutaAsignada == null) {
                    Text("Usuario sin ruta asignada", color = Color.Gray, fontWeight = FontWeight.Bold)
                } else if (uiState.productos.isEmpty()) {
                    Text("Aún no tienes productos en tu almacén", color = Color.Gray, fontWeight = FontWeight.Bold)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                        items(uiState.productos, key = { it.id }) { producto ->
                            ItemProductoInventario(producto)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResumenInventario(totalProductos: Int, valorTotal: Double, notificacionesCount: Int, onNotificacionClick: () -> Unit) {
    val nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.Red), elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Red, Color(0xFFB71C1C)))).padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("TOTAL PIEZAS", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("$totalProductos", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
                }
                Box {
                    FloatingActionButton(onClick = onNotificacionClick, containerColor = Color.White, contentColor = Color.Red, shape = CircleShape, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(24.dp))
                    }
                    if (notificacionesCount > 0) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp)) {
                            Box(modifier = Modifier.size(18.dp).background(Color(0xFF00AAFF), CircleShape), contentAlignment = Alignment.Center) {
                                Text(notificacionesCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("VALOR TOTAL", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(nf.format(valorTotal), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun ItemProductoInventario(producto: Plantilla_Producto) {
    val totalProducto = producto.cantidad * producto.precio
    val nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = producto.imagenUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = producto.nombre, contentScale = ContentScale.Crop, modifier = Modifier.size(75.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = producto.nombre, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(4.dp))
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
