package com.gruposanangel.delivery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pantalla_Mi_Rendimiento(
    navController: NavController,
    nombreVendedor: String,
    ventaDia: Double,
    clientesDia: Int
) {
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val comision = ventaDia * 0.03
    val efectivoCaja = ventaDia - comision

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Rendimiento", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8F9FA))
                .verticalScroll(rememberScrollState())
        ) {
            // Header con Degradado
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFE53935), Color(0xFFB71C1C))
                        ),
                        RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column {
                    Text(
                        text = "VENDEDOR",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = nombreVendedor,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Grid de 4 Bloques (2x2)
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RendimientoCard(
                        titulo = "Sueldo Base Hoy",
                        valor = "$300.00",
                        icono = Icons.Rounded.WorkOutline,
                        color = Color(0xFF607D8B),
                        modifier = Modifier.weight(1f)
                    )
                    RendimientoCard(
                        titulo = "Comisión hoy",
                        valor = formatoMoneda.format(comision),
                        icono = Icons.Rounded.Payments,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RendimientoCard(
                        titulo = "Venta del día",
                        valor = formatoMoneda.format(ventaDia),
                        icono = Icons.Rounded.TrendingUp,
                        color = Color(0xFFE53935),
                        modifier = Modifier.weight(1f)
                    )
                    RendimientoCard(
                        titulo = "Ingreso Total Hoy",
                        valor = formatoMoneda.format(300.0 + comision),
                        icono = Icons.Rounded.Savings,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Sección Adicional: Resumen de Clientes
            Spacer(Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Storefront, null, tint = Color.Blue)
                    Spacer(Modifier.width(12.dp))
                    Text("Clientes atendidos hoy:", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("$clientesDia", fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun RendimientoCard(
    titulo: String,
    valor: String,
    icono: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = icono,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Column {
                Text(
                    text = titulo,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = valor,
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
