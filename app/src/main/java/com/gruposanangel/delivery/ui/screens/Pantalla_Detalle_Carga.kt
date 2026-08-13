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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
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
import com.gruposanangel.delivery.ui.theme.*
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

@Composable
fun PantallaDetalleCarga(
    navController: NavController,
    plantilacarga: Plantila_carga? = null
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val cargaBase = remember { plantilacarga ?: navController.previousBackStackEntry?.savedStateHandle?.get<Plantila_carga>("carga") }

    val isDark = ThemeConfig.isActuallyDark

    if (cargaBase == null && !isPreview) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Text("Error: No se recibió información", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    if (!isPreview && cargaBase != null) {
        val db = AppDatabase.getDatabase(context)
        val firebaseDataSource = FirebaseDataSource()
        val repoUsuario = com.gruposanangel.delivery.RepositoryUsuario(firebaseDataSource, db.usuarioDao())
        val repo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao())
        val viewModel: DetalleCargaViewModel = viewModel(factory = DetalleCargaViewModelFactory(repo, db.productoDao(), repoUsuario))
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
    val isDark = ThemeConfig.isActuallyDark
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    val formatoFecha = SimpleDateFormat("EEEE, dd 'de' MMMM, hh:mm a", Locale("es", "MX"))

    var mostrarDialog by remember { mutableStateOf(false) }
    val totalCantidad = uiState.productos.sumOf { it.cantidad }
    val totalValor = uiState.productos.sumOf { it.cantidad * it.precio }
    val esEmergencia = uiState.carga?.nombreCarga?.contains("EMERGENCIA") == true
    val esCancelada = uiState.carga?.estado == "CANCELADA"
    
    // 🔥 CONTROL DE SEGURIDAD: Vendedores y Administradores pueden aceptar cargas
    val puesto = uiState.puestoTrabajo?.trim()?.uppercase() ?: ""
    val esVendedor = puesto == "VENDEDOR DE RUTA" || puesto == "SUPLENTE DE RUTA"
    val esAdmin = puesto.contains("CEO") || puesto.contains("GERENTE") || puesto.contains("SUPERVISOR") || puesto.contains("ADMIN")
    
    val autorizadaParaAceptar = esVendedor || esAdmin
    val mensajeErrorPermiso = "ESTA CUENTA NO TIENE PERMISOS PARA ACEPTAR CARGAS"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("DETALLE DE CARGA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = if (isDark) Color.White else Color.DarkGray)
                        if (esEmergencia) {
                            Text("PROCESADA LOCALMENTE", style = MaterialTheme.typography.labelSmall, color = DelisaRed, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DelisaRed) }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
                // Header Informativo
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.Start // Asegura alineación a la izquierda
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (esEmergencia) Icons.Default.FlashOn else Icons.Default.Inventory,
                                null,
                                tint = if (esEmergencia) DelisaRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = uiState.carga?.nombreCarga ?: "CARGA DESCONOCIDA",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        uiState.carga?.fecha?.let {
                            Text(
                                text = formatoFecha.format(it).uppercase(),
                                fontSize = 13.sp,
                                color = if (esEmergencia) DelisaRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .fillMaxWidth(), // Toma todo el ancho para que el TextAlign funcione
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Start
                            )
                        }

                        if (uiState.carga?.id?.isNotEmpty() == true) {
                            Text(
                                text = "FOLIO: ${uiState.carga.id}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .fillMaxWidth(),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }

                val productosPorCategoria = remember(uiState.productos) {
                    uiState.productos.groupBy { it.categoria }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f), 
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    productosPorCategoria.forEach { (categoria, productos) ->
                        item(key = "header_$categoria") {
                            CategoryHeader(categoria)
                        }
                        items(productos, key = { it.id }) { producto ->
                            val totalProducto = producto.cantidad * producto.precio
                            Card(
                                shape = RoundedCornerShape(24.dp), 
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), 
                                elevation = CardDefaults.cardElevation(2.dp), 
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(70.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                        AsyncImage(model = producto.imagenUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = producto.nombre, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(producto.nombre, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (producto.cantidadUnitario != null && producto.cantidadUnitario > 0) {
                                            Text("Presentación: ${producto.cantidadUnitario}g", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Surface(color = if (esEmergencia) DelisaRed.copy(alpha = 0.1f) else DelisaGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                            Text("${producto.cantidad} pzas", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, color = if (esEmergencia) DelisaRed else DelisaGreenDark, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(formatoMoneda.format(totalProducto), fontWeight = FontWeight.Black, color = DelisaRed, fontSize = 16.sp)
                                        Text("${formatoMoneda.format(producto.precio)} c/u", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(), 
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), 
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), 
                    elevation = CardDefaults.cardElevation(16.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text("TOTAL PIEZAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("$totalCantidad", fontSize = 22.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) }
                            Column(horizontalAlignment = Alignment.End) { Text("VALOR CARGA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(formatoMoneda.format(totalValor), fontSize = 26.sp, fontWeight = FontWeight.Black, color = DelisaRed) }
                        }
                        if (uiState.carga?.aceptada == false && !esCancelada) {
                            Spacer(Modifier.height(20.dp))
                            if (autorizadaParaAceptar) {
                                Button(
                                    onClick = { mostrarDialog = true }, 
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.fillMaxWidth().height(56.dp), 
                                    shape = RoundedCornerShape(16.dp), 
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (uiState.isLoading) Color.Gray else DelisaRed,
                                        disabledContainerColor = Color.Gray
                                    )
                                ) {
                                    if (uiState.isLoading) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Text("PROCESANDO...", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                        }
                                    } else {
                                        Icon(Icons.Default.Inventory, null, tint = Color.White)
                                        Spacer(Modifier.width(8.dp))
                                        Text("ACEPTAR Y SUMAR AL STOCK", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                    }
                                }
                            } else {
                                // Muestra mensaje informativo para Admins/Almacenistas
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = mensajeErrorPermiso,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(20.dp))
                            val surfaceColor = if (esCancelada) Color.Black else if (esEmergencia) DelisaRed.copy(alpha = 0.1f) else DelisaGreen.copy(alpha = 0.1f)
                            val textColor = if (esCancelada) Color.White else if (esEmergencia) DelisaRed else DelisaGreenDark
                            Surface(
                                modifier = Modifier.fillMaxWidth(), 
                                color = surfaceColor, 
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = if (esCancelada) "ESTA CARGA FUE ANULADA" else if (esEmergencia) "ESTA CARGA FUE PROCESADA MANUALMENTE" else "ESTA CARGA YA FUE ACEPTADA", 
                                    modifier = Modifier.padding(16.dp), 
                                    textAlign = TextAlign.Center, 
                                    color = textColor,
                                    fontWeight = FontWeight.Bold, 
                                    fontSize = 13.sp
                                )
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
