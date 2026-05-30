package com.gruposanangel.delivery.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
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
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantila_carga
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PantallaDetalleCarga(
    navController: NavController,
    plantilacarga: Plantila_carga? = null
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val cargaBase = remember { plantilacarga ?: navController.previousBackStackEntry?.savedStateHandle?.get<Plantila_carga>("carga") }

    if (cargaBase == null && !isPreview) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF8F9FA)), contentAlignment = Alignment.Center) {
            Text("Error: No se recibió información", fontWeight = FontWeight.Bold, color = Color.Gray)
        }
        return
    }

    if (!isPreview && cargaBase != null) {
        val db = AppDatabase.getDatabase(context)
        val repo = RepositoryInventario(FirebaseDataSource(), db.productoDao(), db.VentaDao())
        val viewModel: DetalleCargaViewModel = viewModel(factory = DetalleCargaViewModelFactory(repo, db.productoDao()))
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(cargaBase.id) { viewModel.cargarDetalle(cargaBase.id) }
        if (uiState.aceptadaExito) {
            LaunchedEffect(Unit) {
                Toast.makeText(context, "Carga aceptada e inventario actualizado", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
        }

        PantallaDetalleCargaContent(uiState, onBack = { navController.popBackStack() }, onAceptar = { viewModel.aceptarCarga() })
    } else {
        PantallaDetalleCargaContent(DetalleCargaUiState(carga = cargaBase, productos = cargaBase?.plantillaProductos ?: emptyList()), {}, {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalleCargaContent(uiState: DetalleCargaUiState, onBack: () -> Unit, onAceptar: () -> Unit) {
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    var mostrarDialog by remember { mutableStateOf(false) }
    val totalCantidad = uiState.productos.sumOf { it.cantidad }
    val totalValor = uiState.productos.sumOf { it.cantidad * it.precio }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DETALLE DE CARGA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.DarkGray) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Red) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.Red) }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).background(Color(0xFFF8F9FA))) {
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.productos) { producto ->
                        val totalProducto = producto.cantidad * producto.precio
                        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(75.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFFF1F2F6)), contentAlignment = Alignment.Center) {
                                    AsyncImage(model = producto.imagenUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = producto.nombre, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(producto.nombre, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp)) {
                                        Text("${producto.cantidad} pzas autorizadas", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(formatoMoneda.format(totalProducto), fontWeight = FontWeight.Black, color = Color.Red, fontSize = 17.sp)
                                    Text("u: ${formatoMoneda.format(producto.precio)}", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(16.dp)) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text("TOTAL PIEZAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray); Text("$totalCantidad", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.Black) }
                            Column(horizontalAlignment = Alignment.End) { Text("VALOR CARGA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray); Text(formatoMoneda.format(totalValor), fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.Red) }
                        }
                        if (uiState.carga?.aceptada == false) {
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = { mostrarDialog = true }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                                Icon(Icons.Default.Inventory, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("ACEPTAR Y SUMAR AL STOCK", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            }
                        } else {
                            Spacer(Modifier.height(20.dp))
                            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFFE8F5E9), shape = RoundedCornerShape(16.dp)) {
                                Text("ESTA CARGA YA FUE ACEPTADA", modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarDialog) {
        DialogoConfirmacion(titulo = "Confirmación de Carga", mensaje = "¿Confirmas la recepción de $totalCantidad piezas?", onConfirmar = { mostrarDialog = false; onAceptar() }, onCancelar = { mostrarDialog = false })
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Detalle Carga - Pendiente")
@Composable
fun PantallaDetalleCargaPreview() {
    val productos = listOf(Plantilla_Producto("1", "Papas Delisa 45g", 15.0, 100), Plantilla_Producto("2", "Gomitas Pikabüum", 12.0, 50))
    val state = DetalleCargaUiState(carga = Plantila_carga(aceptada = false), productos = productos)
    DeliveryTheme { PantallaDetalleCargaContent(state, {}, {}) }
}

@Preview(showBackground = true, showSystemUi = true, name = "Detalle Carga - Aceptada")
@Composable
fun PantallaDetalleCargaAceptadaPreview() {
    val productos = listOf(Plantilla_Producto("1", "Papas Delisa 45g", 15.0, 100))
    val state = DetalleCargaUiState(carga = Plantila_carga(aceptada = true), productos = productos)
    DeliveryTheme { PantallaDetalleCargaContent(state, {}, {}) }
}

@Preview(showBackground = true, showSystemUi = true, name = "Detalle Carga - Cargando")
@Composable
fun PantallaDetalleCargaCargandoPreview() {
    DeliveryTheme { PantallaDetalleCargaContent(DetalleCargaUiState(isLoading = true), {}, {}) }
}
