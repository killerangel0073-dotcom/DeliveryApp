package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.gruposanangel.delivery.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pantalla_Analiticas_Admin(
    navController: NavController,
    startTime: Long,
    endTime: Long
) {
    val viewModel: AnalyticsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val sdf = SimpleDateFormat("d MMM yyyy", Locale("es", "MX"))

    // 🏗️ Obtener Perfiles desde el ViewModel
    val perfilesFiltro = remember(uiState.perfilesDisponibles) {
        val list = mutableListOf(com.gruposanangel.delivery.data.PerfilVenta("ALL", "CONSOLIDADO", emptyList()))
        list.addAll(uiState.perfilesDisponibles)
        list
    }
    
    val perfilSeleccionado = uiState.perfilSeleccionado ?: perfilesFiltro.first()

    LaunchedEffect(startTime, endTime) {
        viewModel.cargarAnaliticas(Date(startTime), Date(endTime))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DESEMPEÑO GLOBAL", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = "${sdf.format(Date(startTime))} - ${sdf.format(Date(endTime))}".uppercase(),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        try {
                            val file = com.gruposanangel.delivery.utilidades.ReporteAnaliticasPdf.generarPDF(
                                context = context,
                                uiState = uiState,
                                fechaInicio = Date(startTime),
                                fechaFin = Date(endTime),
                                perfilNombre = perfilSeleccionado?.nombre ?: "CONSOLIDADO"
                            )
                            val authority = "${context.packageName}.provider"
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Error al generar reporte: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            Log.e("PDF_ERROR", "Error generando PDF", e)
                        }
                    }) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = DelisaRed)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DelisaRed)
            }
        } else if (uiState.error != null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, tint = DelisaRed, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.cargarAnaliticas(Date(startTime), Date(endTime)) },
                        colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
                    ) {
                        Text("REINTENTAR", color = Color.White)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                // 🔹 SELECTOR DE LÍNEA DE NEGOCIO
                PerfilVentaSelector(
                    perfiles = perfilesFiltro,
                    seleccionado = perfilSeleccionado,
                    onSeleccionar = { perfil ->
                        if (perfil.id == "ALL") viewModel.seleccionarPerfil(null)
                        else viewModel.seleccionarPerfil(perfil)
                    }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. BALANCE FINANCIERO
                    item {
                        BalanceCard(
                            bruta = uiState.totalVentaBruta,
                            gastos = uiState.totalGastos,
                            neto = uiState.utilidadOperativa,
                            formato = formatoMoneda
                        )
                    }

                    // 1.1 GRÁFICA DE TENDENCIA (Si hay más de 1 día)
                    if (uiState.ventasPorDia.size > 1) {
                        item { SectionHeader("Tendencia de Ventas", Icons.AutoMirrored.Filled.ShowChart) }
                        item {
                            TrendChart(uiState.ventasPorDia)
                        }
                    }

                    // 2. DESGLOSE DE GASTOS
                    if (uiState.desgloseGastos.isNotEmpty() && uiState.perfilSeleccionado == null) {
                        item { SectionHeader("Distribución de Gastos", Icons.Default.PieChart) }
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(24.dp)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    uiState.desgloseGastos.forEach { gasto ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(8.dp).background(DelisaRed, CircleShape))
                                            Spacer(Modifier.width(12.dp))
                                            Text(gasto.categoria, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                            Text(formatoMoneda.format(gasto.total), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. TOP PRODUCTOS
                    if (uiState.topProductos.isNotEmpty()) {
                        item { SectionHeader("Top 5 Productos", Icons.Default.Star) }
                        items(uiState.topProductos) { prod ->
                            ProductStatItem(prod, formatoMoneda)
                        }
                    }

                    // 4. RANKING VENDEDORES
                    if (uiState.rankingVendedores.isNotEmpty()) {
                        item { SectionHeader("Ranking de Vendedores", Icons.Default.Leaderboard) }
                        items(uiState.rankingVendedores) { seller ->
                            SellerStatItem(seller, formatoMoneda)
                        }
                    }
                    
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

@Composable
fun TrendChart(days: List<DayStat>) {
    val maxMonto = (days.maxOfOrNull { it.monto } ?: 1.0).coerceAtLeast(1.0)
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { day ->
                    val hFactor = (day.monto / maxMonto).toFloat().coerceIn(0.01f, 1f)
                    Column(
                        modifier = Modifier
                            .width(60.dp) 
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom 
                    ) {
                        if (day.monto > 0) {
                            val label = if (day.monto >= 1000) {
                                "%.1fk".format(day.monto / 1000.0)
                            } else {
                                day.monto.toInt().toString()
                            }
                            Text(
                                text = label,
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Black,
                                color = DelisaRed,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp * hFactor) 
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(DelisaRed, DelisaRed.copy(alpha = 0.7f))
                                    )
                                )
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Text(
                            text = day.fecha, 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BalanceCard(bruta: Double, gastos: Double, neto: Double, formato: NumberFormat) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Box(modifier = Modifier.background(Brush.verticalGradient(listOf(DelisaRed, DelisaRedDark)))) {
            Column(Modifier.padding(24.dp)) {
                Text("UTILIDAD NETA OPERATIVA", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(formato.format(neto), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
                
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(0.2f))
                Spacer(Modifier.height(20.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("VENTA BRUTA", color = Color.White.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(formato.format(bruta), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("GASTOS TOTALES", color = Color.White.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("-${formato.format(gastos)}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProductStatItem(prod: ProductStat, formato: NumberFormat) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = prod.imagenUrl,
                contentDescription = null,
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(prod.nombre, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                Text("${prod.cantidad} unidades vendidas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formato.format(prod.monto), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun SellerStatItem(seller: SellerStat, formato: NumberFormat) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!seller.fotoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = seller.fotoUrl,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(Modifier.size(40.dp), shape = CircleShape, color = DelisaRed.copy(0.1f)) {
                        Icon(Icons.Default.Person, null, tint = DelisaRed, modifier = Modifier.padding(10.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(seller.nombre, fontWeight = FontWeight.Black, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("${seller.numTickets} tickets | ${seller.cancelaciones} anuladas", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formato.format(seller.totalVenta), fontWeight = FontWeight.Black, color = DelisaGreen)
                    if (seller.totalGastos > 0) {
                        Text("-${formato.format(seller.totalGastos)} gastos", fontSize = 10.sp, color = DelisaRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Icon(icon, null, tint = DelisaRed, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
    }
}
