package com.gruposanangel.delivery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantila_carga
import com.gruposanangel.delivery.model.Plantilla_Producto
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalleArqueo(
    navController: NavController
) {
    val context = LocalContext.current
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val formatoFechaCompleta = remember { SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy, hh:mm a", Locale("es", "MX")) }
    val cargaBase = remember { navController.previousBackStackEntry?.savedStateHandle?.get<Plantila_carga>("carga") }

    if (cargaBase == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontró información del arqueo")
        }
        return
    }

    val db = AppDatabase.getDatabase(context)
    val repo = RepositoryInventario(FirebaseDataSource(), db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
    
    val vm: DetalleArqueoViewModel = viewModel(
        factory = DetalleArqueoViewModelFactory(cargaBase.id, repo)
    )
    val uiState by vm.uiState.collectAsState()

    val totalDiferenciaPiezas = uiState.productos.sumOf { it.diferencia }
    val totalDiferenciaDinero = uiState.productos.sumOf { it.diferencia * it.precio }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("RESUMEN DE AUDITORÍA", fontWeight = FontWeight.Black, fontSize = 16.sp)
                        val fechaLong = uiState.fecha
                        if (fechaLong != null) {
                            Text(
                                text = formatoFechaCompleta.format(Date(fechaLong)).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                fontSize = 9.sp
                            )
                        } else {
                            Text("PROCESANDO DATOS...", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Red)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // 🔹 CARD DE ESTADO GLOBAL
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            totalDiferenciaDinero < 0 -> Color(0xFFFFEBEE)
                            totalDiferenciaDinero > 0 -> Color(0xFFE3F2FD)
                            else -> Color(0xFFE8F5E9)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(
                                when {
                                    totalDiferenciaDinero < 0 -> Color.Red
                                    totalDiferenciaDinero > 0 -> Color(0xFF2196F3)
                                    else -> Color(0xFF2E7D32)
                                }
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    totalDiferenciaDinero < 0 -> Icons.AutoMirrored.Filled.TrendingDown
                                    totalDiferenciaDinero > 0 -> Icons.AutoMirrored.Filled.TrendingUp
                                    else -> Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = when {
                                    totalDiferenciaDinero < 0 -> "DISCREPANCIA (FALTANTE)"
                                    totalDiferenciaDinero > 0 -> "DISCREPANCIA (SOBRANTE)"
                                    else -> "INVENTARIO CONCILIADO"
                                },
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                            Text(
                                text = if (totalDiferenciaDinero == 0.0) "Inventario Cuadrado" 
                                       else "${formatoMoneda.format(totalDiferenciaDinero)} ($totalDiferenciaPiezas piezas)",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    totalDiferenciaDinero < 0 -> Color.Red
                                    totalDiferenciaDinero > 0 -> Color(0xFF2196F3)
                                    else -> Color(0xFF2E7D32)
                                }
                            )
                        }
                    }
                }

                Text(
                    "CONTEO FÍSICO VS SISTEMA",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.Gray
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.productos) { producto ->
                        ItemDetalleArqueoCompleto(producto, formatoMoneda)
                    }
                }

                // 🔹 FOOTER INFORMATIVO
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            "Esta auditoría muestra el estado completo de la unidad al momento del arqueo, comparando el stock esperado contra el conteo real.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Analytics, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Folio Auditoría: ${cargaBase.id}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemDetalleArqueoCompleto(producto: ProductoArqueoDetalle, formato: NumberFormat) {
    val colorEstado = when {
        producto.diferencia < 0 -> Color.Red
        producto.diferencia > 0 -> Color(0xFF2196F3)
        else -> Color(0xFF2E7D32)
    }
    
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        border = if (producto.diferencia != 0) androidx.compose.foundation.BorderStroke(1.dp, colorEstado.copy(0.3f)) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(45.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F2F6))) {
                    AsyncImage(
                        model = producto.imagenUrl,
                        placeholder = painterResource(R.drawable.repartidor),
                        error = painterResource(R.drawable.repartidor),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        producto.nombre, 
                        fontWeight = FontWeight.ExtraBold, 
                        fontSize = 14.sp, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis
                    )
                    if (producto.diferencia != 0) {
                        Text(
                            text = if (producto.diferencia > 0) "Sobrante: +${producto.diferencia}" else "Faltante: ${producto.diferencia}",
                            fontSize = 12.sp,
                            color = colorEstado,
                            fontWeight = FontWeight.Black
                        )
                    } else {
                        Text("Inventario correcto", fontSize = 12.sp, color = Color(0xFF2E7D32))
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text("DIFERENCIA", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text(
                        text = if (producto.diferencia != 0) formato.format(producto.diferencia * producto.precio) else "$0.00",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = colorEstado
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF1F2F6))
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Valor Teórico (Sistema)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("SISTEMA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("${producto.teorico ?: "-"}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.DarkGray)
                }
                
                // Icono de flecha
                Box(Modifier.align(Alignment.CenterVertically)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }

                // Valor Físico (Conteo)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("CONTEO FÍSICO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colorEstado)
                    Text("${producto.fisico ?: "-"}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = colorEstado)
                }
            }
        }
    }
}
