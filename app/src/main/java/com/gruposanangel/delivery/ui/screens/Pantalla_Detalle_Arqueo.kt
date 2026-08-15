package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.gruposanangel.delivery.ui.theme.*
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
    val formatoFechaCompleta = remember { SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy", Locale("es", "MX")) }
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
    val totalValorFisico = uiState.productos.sumOf { (it.fisico ?: 0) * it.precio }

    val isDark = ThemeConfig.isActuallyDark

    DeliveryTheme(darkTheme = isDark) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DelisaRed)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // 🔥 CABECERA MANUAL SLIM (IGUAL A DETALLE CARGA)
                    Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.size(40.dp)) { 
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed, modifier = Modifier.size(22.dp)) 
                            }
                            
                            Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                                val titulo = if (uiState.metodoAuditoria == "LIQUIDACION") "RESUMEN DE LIQUIDACIÓN" else "RESUMEN DE AUDITORÍA"
                                Text(
                                    text = titulo, 
                                    style = MaterialTheme.typography.titleSmall, 
                                    fontWeight = FontWeight.Black, 
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                )
                                Text("FOLIO: ${cargaBase.id.takeLast(8).uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // 🔹 CABECERA INFORMATIVA DELISA
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Analytics, null, tint = DelisaRed, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "UNIDAD AUDITADA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = DelisaRed
                                )
                            }
                            Text(
                                text = cargaBase.nombreCarga.uppercase(),
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            if (uiState.metodoAuditoria == "LIQUIDACION") {
                                // 🏠 ALMACÉN DE DESTINO (Solo para Liquidación)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warehouse, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "REGRESÓ A:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "ALMACÉN HUASTECA",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Event, null, tint = DelisaRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                uiState.fecha?.let {
                                    Text(
                                        text = formatoFechaCompleta.format(Date(it)).uppercase(),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        if (uiState.metodoAuditoria == "LIQUIDACION") "RESUMEN DE MERCANCÍA DEVUELTA" else "CONTEO FÍSICO VS SISTEMA",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )

                    val productosPorCategoria = remember(uiState.productos) {
                        uiState.productos.groupBy { it.categoria }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        productosPorCategoria.forEach { (categoria, productos) ->
                            item(key = "header_$categoria") {
                                CategoryHeader(categoria)
                            }
                            items(productos) { producto ->
                                ItemDetalleArqueoCompleto(producto, formatoMoneda, uiState.metodoAuditoria)
                            }
                        }
                    }

                    // 🔹 FOOTER DE TOTALES PREMIUM CON DEGRADADO
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
                                        Text(
                                            if (uiState.metodoAuditoria == "LIQUIDACION") "VALOR DEVUELTO" else "VALOR TOTAL INVENTARIO", 
                                            fontSize = 10.sp, 
                                            fontWeight = FontWeight.Bold, 
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = formatoMoneda.format(totalValorFisico), 
                                            fontSize = 22.sp, 
                                            fontWeight = FontWeight.Black, 
                                            color = Color.White
                                        ) 
                                    }
                                    Column(horizontalAlignment = Alignment.End) { 
                                        Text(
                                            "DIFERENCIA NETA", // 🔥 SIEMPRE MOSTRAR DIFERENCIA
                                            fontSize = 10.sp, 
                                            fontWeight = FontWeight.Bold, 
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = formatoMoneda.format(totalDiferenciaDinero), 
                                            fontSize = 26.sp, 
                                            fontWeight = FontWeight.Black, 
                                            color = if (totalDiferenciaDinero < 0) Color.Yellow else Color.White
                                        ) 
                                    }
                                }
                                
                                if (totalDiferenciaDinero != 0.0) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = if (totalDiferenciaDinero < 0) "FALTANTE DETECTADO: ${formatoMoneda.format(totalDiferenciaDinero)}" 
                                               else "SOBRANTE DETECTADO: ${formatoMoneda.format(totalDiferenciaDinero)}",
                                        color = if (totalDiferenciaDinero < 0) Color.Yellow else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                                
                                Spacer(Modifier.height(16.dp))
                                
                                Surface(
                                    modifier = Modifier.fillMaxWidth(), 
                                    color = Color.White.copy(alpha = 0.15f), 
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = when {
                                            uiState.metodoAuditoria == "LIQUIDACION" -> "UNIDAD LIQUIDADA CORRECTAMENTE"
                                            totalDiferenciaDinero < 0 -> "SE DETECTÓ UN FALTANTE DE INVENTARIO"
                                            totalDiferenciaDinero > 0 -> "SE DETECTÓ UN SOBRANTE DE INVENTARIO"
                                            else -> "EL INVENTARIO COINCIDE CON EL SISTEMA"
                                        }, 
                                        modifier = Modifier.padding(16.dp), 
                                        textAlign = TextAlign.Center, 
                                        color = Color.White,
                                        fontWeight = FontWeight.Black, 
                                        fontSize = 12.sp
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

@Composable
fun ItemDetalleArqueoCompleto(producto: ProductoArqueoDetalle, formato: NumberFormat, metodo: String? = null) {
    val colorEstado = when {
        producto.diferencia < 0 -> DelisaRed
        producto.diferencia > 0 -> DelisaBlue
        else -> DelisaGreenDark
    }
    
    val valorContado = (producto.fisico ?: 0) * producto.precio

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (producto.diferencia != 0) BorderStroke(1.dp, colorEstado.copy(0.2f)) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    AsyncImage(
                        model = producto.imagenUrl,
                        placeholder = painterResource(R.drawable.repartidor),
                        error = painterResource(R.drawable.repartidor),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        producto.nombre, 
                        fontWeight = FontWeight.Black, 
                        fontSize = 14.sp, 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (producto.diferencia != 0) {
                        Text(
                            text = if (producto.diferencia > 0) "Sobrante: +${producto.diferencia}" else "Faltante: ${producto.diferencia}",
                            fontSize = 12.sp,
                            color = colorEstado,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            if (metodo == "LIQUIDACION") "Devolución completa" else "Inventario correcto", 
                            fontSize = 11.sp, 
                            color = DelisaGreenDark, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (metodo == "LIQUIDACION") "VALOR DEVUELTO" else "VALOR CONTADO", 
                        fontSize = 8.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formato.format(valorContado),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Valor Teórico (Sistema)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(if (metodo == "LIQUIDACION") "ESPERADO" else "SISTEMA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${producto.teorico ?: "-"}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                
                // Icono de flecha
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))

                // Valor Físico (Conteo)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(if (metodo == "LIQUIDACION") "REGRESÓ" else "CONTEO REAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colorEstado)
                    Text("${producto.fisico ?: "-"}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = colorEstado)
                }
            }
        }
    }
}
