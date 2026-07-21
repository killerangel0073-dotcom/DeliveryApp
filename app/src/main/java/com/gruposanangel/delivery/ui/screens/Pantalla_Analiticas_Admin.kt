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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val RojoDelisa = Color(0xFFE53935)
private val NegroPremium = Color(0xFF1E1E24)
private val GrisFondo = Color(0xFFF8F9FA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pantalla_Analiticas_Admin(
    navController: NavController,
    startTime: Long,
    endTime: Long
) {
    val viewModel: AnalyticsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val sdf = SimpleDateFormat("d MMM yyyy", Locale("es", "MX"))

    LaunchedEffect(startTime, endTime) {
        viewModel.cargarAnaliticas(Date(startTime), Date(endTime))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DESEMPEÑO GLOBAL", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        Text(
                            text = "${sdf.format(Date(startTime))} - ${sdf.format(Date(endTime))}".uppercase(),
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RojoDelisa)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = GrisFondo
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RojoDelisa)
            }
        } else if (uiState.error != null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, tint = RojoDelisa, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = uiState.error!!,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.cargarAnaliticas(Date(startTime), Date(endTime)) },
                        colors = ButtonDefaults.buttonColors(containerColor = RojoDelisa)
                    ) {
                        Text("REINTENTAR")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                if (uiState.desgloseGastos.isNotEmpty()) {
                    item { SectionHeader("Distribución de Gastos", Icons.Default.PieChart) }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                uiState.desgloseGastos.forEach { gasto ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(8.dp).background(RojoDelisa, CircleShape))
                                        Spacer(Modifier.width(12.dp))
                                        Text(gasto.categoria, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                        Text(formatoMoneda.format(gasto.total), fontWeight = FontWeight.Black)
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

@Composable
fun TrendChart(days: List<DayStat>) {
    val maxMonto = (days.maxOfOrNull { it.monto } ?: 1.0).coerceAtLeast(1.0)
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
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
                            .width(60.dp) // Un poco más de aire
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom // Todo nace desde abajo
                    ) {
                        if (day.monto > 0) {
                            val label = if (day.monto >= 1000) {
                                "%.1fk".format(day.monto / 1000.0)
                            } else {
                                day.monto.toInt().toString()
                            }
                            Text(
                                text = label,
                                fontSize = 10.sp, // Un poco más grande para legibilidad
                                fontWeight = FontWeight.Black,
                                color = RojoDelisa,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        
                        // ÁREA DE BARRA (Altura calculada sobre un máximo de 110dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp * hFactor) 
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(RojoDelisa, RojoDelisa.copy(alpha = 0.7f))
                                    )
                                )
                        )
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Text(
                            text = day.fecha, 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = Color.Gray,
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = NegroPremium),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("UTILIDAD NETA OPERATIVA", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(formato.format(neto), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
            
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(0.1f))
            Spacer(Modifier.height(20.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("VENTA BRUTA", color = Color.White.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(formato.format(bruta), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("GASTOS TOTALES", color = Color.White.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("-${formato.format(gastos)}", color = RojoDelisa, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ProductStatItem(prod: ProductStat, formato: NumberFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = prod.imagenUrl,
                contentDescription = null,
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)).background(GrisFondo),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(prod.nombre, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text("${prod.cantidad} unidades vendidas", fontSize = 12.sp, color = Color.Gray)
            }
            Text(formato.format(prod.monto), fontWeight = FontWeight.Black, color = NegroPremium)
        }
    }
}

@Composable
fun SellerStatItem(seller: SellerStat, formato: NumberFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
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
                    Surface(Modifier.size(40.dp), shape = CircleShape, color = RojoDelisa.copy(0.1f)) {
                        Icon(Icons.Default.Person, null, tint = RojoDelisa, modifier = Modifier.padding(10.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(seller.nombre, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("${seller.numTickets} tickets | ${seller.cancelaciones} anuladas", fontSize = 11.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formato.format(seller.totalVenta), fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                    if (seller.totalGastos > 0) {
                        Text("-${formato.format(seller.totalGastos)} gastos", fontSize = 10.sp, color = RojoDelisa, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Icon(icon, null, tint = RojoDelisa, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
    }
}
