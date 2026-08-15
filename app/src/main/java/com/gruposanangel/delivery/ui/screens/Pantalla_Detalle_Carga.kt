package com.gruposanangel.delivery.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.model.Plantila_carga
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.ui.theme.*
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalleCarga(
    navController: NavController
) {
    val context = LocalContext.current
    val cargaBase = remember { navController.previousBackStackEntry?.savedStateHandle?.get<Plantila_carga>("carga") }

    if (cargaBase == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontró información de la carga")
        }
        return
    }

    val db = AppDatabase.getDatabase(context)
    val firebaseDataSource = FirebaseDataSource()
    val repo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
    val usuarioRepo = RepositoryUsuario(firebaseDataSource, db.usuarioDao())
    
    val vm: DetalleCargaViewModel = viewModel(
        factory = DetalleCargaViewModelFactory(repo, db.productoDao(), usuarioRepo)
    )
    val uiState by vm.uiState.collectAsState()

    // Cargar datos al iniciar
    LaunchedEffect(cargaBase.id) {
        vm.cargarDetalle(cargaBase.id)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(uiState.aceptadaExito) {
        if (uiState.aceptadaExito) {
            Toast.makeText(context, "Carga aceptada correctamente", Toast.LENGTH_SHORT).show()
            navController.popBackStack()
        }
    }

    PantallaDetalleCargaContent(
        uiState = uiState,
        onBack = { navController.popBackStack() },
        onAceptar = { vm.aceptarCarga() }
    )
}

@Composable
fun PantallaDetalleCargaContent(
    uiState: DetalleCargaUiState, 
    onBack: () -> Unit, 
    onAceptar: () -> Unit
) {
    val formatoMoneda = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val formatoFecha = remember { SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("es", "MX")) }
    var mostrarDialog by remember { mutableStateOf(false) }

    val totalCantidad = uiState.productos.sumOf { it.cantidad }
    val totalValor = uiState.productos.sumOf { it.cantidad * it.precio }
    
    val isDark = ThemeConfig.isActuallyDark
    val esCancelada = uiState.carga?.estado == "CANCELADA"
    val esEmergencia = uiState.carga?.id?.startsWith("EMERGENCY_") == true

    val autorizadaParaAceptar = remember(uiState.puestoTrabajo, uiState.origen) {
        val puesto = uiState.puestoTrabajo?.trim()?.uppercase() ?: ""
        val esAdmin = puesto == "CEO" || puesto == "GERENTE GENERAL" || puesto == "SUPERVISOR" || puesto == "ADMINISTRADOR"
        val esCompra = uiState.origen == "Compra Producto"

        if (esCompra) {
            esAdmin // Solo admins aceptan compras
        } else {
            // Cargas normales: Solo vendedores/repartidores
            puesto.contains("VENDEDOR") || puesto.contains("REPARTIDOR") || puesto.isEmpty()
        }
    }
    
    val mensajeErrorPermiso = when {
        uiState.origen == "Compra Producto" -> "SOLO UN ADMINISTRADOR PUEDE ACEPTAR COMPRAS DE PRODUCTO"
        uiState.puestoTrabajo?.contains("CEO", ignoreCase = true) == true || 
        uiState.puestoTrabajo?.contains("Gerente", ignoreCase = true) == true -> "LOS DIRECTIVOS NO PUEDEN SUMAR STOCK A VENDEDORES"
        uiState.puestoTrabajo?.contains("Almacenista", ignoreCase = true) == true -> "EL ALMACENISTA NO PUEDE ACEPTAR SU PROPIA CARGA"
        else -> "SOLO EL VENDEDOR ASIGNADO PUEDE ACEPTAR ESTA CARGA"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            // 🔥 CABECERA MANUAL COMPACTA (SIEMPRE VISIBLE)
            Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed, modifier = Modifier.size(22.dp)) 
                    }
                    
                    Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                        Text(
                            text = "DETALLE DE CARGA", 
                            style = MaterialTheme.typography.titleSmall, 
                            fontWeight = FontWeight.Black, 
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                        if (esEmergencia) {
                            Text("PROCESADA LOCALMENTE", style = MaterialTheme.typography.labelSmall, color = DelisaRed, fontWeight = FontWeight.Bold, fontSize = 7.sp)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(2.dp))

            // 🔥 TRANSICIÓN FLUIDA ENTRE SKELETON Y CONTENIDO
            Crossfade(targetState = uiState.isLoading, label = "mainContentTransition") { isLoading ->
                if (isLoading) {
                    DetalleCargaSkeleton()
                } else {
                    Column(Modifier.fillMaxSize()) {
                        // Header Informativo Premium
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
                                .shadow(3.dp, RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                // 🚩 DESTINO / RUTA (PROMINENTE - ESTILO DELISA)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocalShipping, null, tint = DelisaRed, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "DESTINO / VENDEDOR",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = DelisaRed
                                    )
                                }
                                Text(
                                    text = uiState.destinoAlmacen?.uppercase() ?: "CARGA SIN DESTINO",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                                // ORIGEN Y FOLIO (ESTILO RECIBO)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("ORIGEN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = uiState.carga?.nombreCarga?.replace("Carga desde ", "") ?: "ALMACÉN CENTRAL",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("FOLIO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = "#${uiState.carga?.id?.takeLast(8)?.uppercase() ?: "---"}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                if (uiState.carga?.fecha != null) {
                                    val fechaSegura = uiState.carga.fecha
                                    Spacer(Modifier.height(16.dp))
                                    Surface(
                                        color = DelisaRed.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(0.5.dp, DelisaRed.copy(alpha = 0.2f))
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Event, null, tint = DelisaRed, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = formatoFecha.format(fechaSegura).uppercase(),
                                                fontSize = 10.sp,
                                                color = DelisaRed,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
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
                                        shape = RoundedCornerShape(20.dp), 
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .shadow(2.dp, RoundedCornerShape(20.dp))
                                    ) {
                                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(65.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                                AsyncImage(model = producto.imagenUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = producto.nombre, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                            }
                                            Spacer(Modifier.width(16.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(producto.nombre, fontWeight = FontWeight.Black, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                if (producto.cantidadUnitario != null && producto.cantidadUnitario > 0) {
                                                    Text("Presentación: ${producto.cantidadUnitario}g", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                Text(text = "${producto.cantidad} PZAS", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = DelisaRed)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(formatoMoneda.format(totalProducto), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                                                Text("${formatoMoneda.format(producto.precio)} c/u", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                        }

                        // 🔹 FOOTER DE TOTALES CON BOTÓN DE ACCIÓN
                        Card(
                            modifier = Modifier.fillMaxWidth(), 
                            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), 
                            elevation = CardDefaults.cardElevation(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Brush.verticalGradient(listOf(DelisaRed, DelisaRedDark)))
                                    .padding(24.dp)
                            ) {
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column { 
                                            Text("TOTAL PIEZAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                                            Text("$totalCantidad", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White) 
                                        }
                                        Column(horizontalAlignment = Alignment.End) { 
                                            Text("VALOR TOTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                                            Text(formatoMoneda.format(totalValor), fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White) 
                                        }
                                    }
                                    
                                    if (uiState.carga?.aceptada == false && !esCancelada) {
                                        Spacer(Modifier.height(20.dp))
                                        if (autorizadaParaAceptar) {
                                            CompositionLocalProvider(LocalAbsoluteTonalElevation provides 0.dp) {
                                                Button(
                                                    onClick = { mostrarDialog = true },
                                                    enabled = !uiState.isLoading,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(56.dp)
                                                        .shadow(if (uiState.isLoading) 0.dp else 4.dp, RoundedCornerShape(16.dp)),
                                                    shape = RoundedCornerShape(16.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isDark) Color(0xFF111111) else Color.White,
                                                        contentColor = DelisaRed,
                                                        disabledContainerColor = (if (isDark) Color.Black else Color.White).copy(alpha = 0.3f),
                                                        disabledContentColor = DelisaRed.copy(alpha = 0.5f)
                                                    ),
                                                    elevation = ButtonDefaults.buttonElevation(
                                                        defaultElevation = 0.dp,
                                                        pressedElevation = 0.dp,
                                                        hoveredElevation = 0.dp,
                                                        focusedElevation = 0.dp
                                                    )
                                                ) {
                                                    if (uiState.isLoading) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            CircularProgressIndicator(color = DelisaRed, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                                            Spacer(Modifier.width(12.dp))
                                                            Text("PROCESANDO...", color = DelisaRed, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                                        }
                                                    } else {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.CheckCircle, null, tint = DelisaRed)
                                                            Spacer(Modifier.width(12.dp))
                                                            Text("ACEPTAR Y SUMAR AL STOCK", color = DelisaRed, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = Color.White.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                            ) {
                                                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(Icons.Default.Info, null, tint = Color.White)
                                                    Spacer(Modifier.height(8.dp))
                                                    Text(
                                                        text = mensajeErrorPermiso,
                                                        textAlign = TextAlign.Center,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(Modifier.height(20.dp))
                                        val surfaceColor = if (esCancelada) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.15f)
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(), 
                                            color = surfaceColor, 
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Text(
                                                text = if (esCancelada) "ESTA CARGA FUE ANULADA" else if (esEmergencia) "ESTA CARGA FUE PROCESADA MANUALMENTE" else "ESTA CARGA YA FUE ACEPTADA", 
                                                modifier = Modifier.padding(16.dp), 
                                                textAlign = TextAlign.Center, 
                                                color = Color.White,
                                                fontWeight = FontWeight.Black, 
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarDialog) {
        DialogoConfirmacion(
            titulo = "Confirmación de Carga", 
            mensaje = "¿Confirmas la recepción de $totalCantidad piezas?", 
            onConfirmar = { mostrarDialog = false; onAceptar() }, 
            onCancelar = { mostrarDialog = false }
        )
    }
}

@Composable
fun DetalleCargaSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val skeletonColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(skeletonColor)
        )
        Spacer(Modifier.height(16.dp))
        repeat(5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(skeletonColor)
            )
        }
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
