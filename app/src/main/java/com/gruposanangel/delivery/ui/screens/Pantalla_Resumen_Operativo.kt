package com.gruposanangel.delivery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaResumenOperativo(navController: NavController) {
    var productos by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("producto")
            .whereEqualTo("activo", true)
            .get()
            .addOnSuccessListener { result ->
                productos = result.documents.mapNotNull { it.data }
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RESUMEN OPERATIVO", fontWeight = FontWeight.Black, fontSize = 16.sp) },
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
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.Red)
            }
        } else {
            val margins = productos.map { calcularMargen(it) }
            val maxMargen = if (margins.isNotEmpty()) margins.maxOrNull() ?: 0.0 else 0.0
            val minMargen = if (margins.isNotEmpty()) margins.minOrNull() ?: 0.0 else 0.0

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ResumenGeneralFinanzas(productos)
                }

                item {
                    Text(
                        "Análisis de Rentabilidad",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Ordenamos por los que dejan más margen
                items(productos.sortedByDescending { calcularMargen(it) }) { prod ->
                    CardProductoFinanzas(prod, formatoMoneda, minMargen, maxMargen)
                }
            }
        }
    }
}

@Composable
fun ResumenGeneralFinanzas(productos: List<Map<String, Any>>) {
    val margenPromedio = if (productos.isNotEmpty()) {
        productos.map { calcularMargen(it) }.filter { it > 0 }.average()
    } else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Red, Color(0xFFB71C1C))))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Color.White, modifier = Modifier.size(32.dp))
                Text("MARGEN DE UTILIDAD PROM.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${String.format(Locale.US, "%.1f", margenPromedio)}%",
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black
                )
                Text("Cálculo basado en configuración de compra/venta", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun CardProductoFinanzas(prod: Map<String, Any>, formato: NumberFormat, minMargen: Double, maxMargen: Double) {
    val nombre = prod["nombre"] as? String ?: "Producto"
    
    // Extracción robusta de números (soporta String, Int, Double, Long)
    val precioVenta = prod["precio"]?.toString()?.toDoubleOrNull() ?: 0.0
    val precioCompra = prod["precioCompra"]?.toString()?.toDoubleOrNull() ?: 0.0
    val gramosBolsa = prod["cantidadUnitario"]?.toString()?.toDoubleOrNull() ?: 0.0
    val display = prod["unidadesPorDisplay"]?.toString()?.toDoubleOrNull() ?: 0.0
    val gramosVenta = prod["gramosVenta"]?.toString()?.toDoubleOrNull() ?: 0.0

    // Cálculo de Costo por Pieza (Bolsita)
    // Fórmula: (Precio Compra Bolsa / Gramos Bolsa) * Gramos Bolsita Venta
    val costoPieza = if (gramosBolsa > 0) {
        (precioCompra / gramosBolsa) * gramosVenta
    } else 0.0

    val utilidad = precioVenta - costoPieza
    val margen = if (precioVenta > 0) (utilidad / precioVenta) * 100 else 0.0

    // Escala de colores dinámica (Relativa al catálogo)
    val colorMargen = if (maxMargen == minMargen) {
        Color(0xFF4CAF50) 
    } else {
        val ratio = (margen - minMargen) / (maxMargen - minMargen)
        when {
            ratio >= 0.75 -> Color(0xFF2E7D32) // El mejor 25% (Verde Oscuro)
            ratio >= 0.50 -> Color(0xFF4CAF50) // Superior al promedio (Verde)
            ratio >= 0.25 -> Color(0xFFFF9800) // Inferior al promedio (Naranja)
            else -> Color(0xFFD32F2F)          // El peor 25% (Rojo)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colorMargen.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Inventory2, null, tint = colorMargen, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(nombre, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Costo Display: ${formato.format(precioCompra)} ($display pzas)", fontSize = 10.sp, color = Color.Gray)
                }
                Surface(
                    color = colorMargen,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${String.format(Locale.US, "%.0f", margen)}%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF1F2F6), thickness = 1.dp)
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InfoFinanzasItem("COSTO UNIT.", formato.format(costoPieza), Color.DarkGray)
                InfoFinanzasItem("PRECIO VENTA", formato.format(precioVenta), Color.Black)
                InfoFinanzasItem("UTILIDAD PZ", formato.format(utilidad), colorMargen)
            }
            
            if (display > 0 && gramosBolsa > 0 && gramosVenta > 0) {
                val pzasPorDisplay = (display * gramosBolsa) / gramosVenta
                Text(
                    "* Rendimiento: aprox. ${String.format(Locale.US, "%.0f", pzasPorDisplay)} bolsitas por display",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 12.dp),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun InfoFinanzasItem(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = valueColor)
    }
}

fun calcularMargen(prod: Map<String, Any>): Double {
    val precioVenta = prod["precio"]?.toString()?.toDoubleOrNull() ?: 0.0
    val precioCompra = prod["precioCompra"]?.toString()?.toDoubleOrNull() ?: 0.0
    val gramosBolsa = prod["cantidadUnitario"]?.toString()?.toDoubleOrNull() ?: 0.0
    val gramosVenta = prod["gramosVenta"]?.toString()?.toDoubleOrNull() ?: 0.0

    val costoPieza = if (gramosBolsa > 0) {
        (precioCompra / gramosBolsa) * gramosVenta
    } else 0.0

    return if (precioVenta > 0) ((precioVenta - costoPieza) / precioVenta) * 100 else 0.0
}
