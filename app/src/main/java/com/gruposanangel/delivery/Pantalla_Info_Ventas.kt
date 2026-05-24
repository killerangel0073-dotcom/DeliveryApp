package com.gruposanangel.delivery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ===================== PALETA ROJO PREMIUM ===================== */
private val RojoPrincipal = Color(0xFFE30613)
private val Blanco = Color(0xFFFFFFFF)
private val NegroBancario = Color(0xFF1A1A1A)
private val GrisFondo = Color(0xFFF8F9FA)
private val GrisTexto = Color(0xFF636E72)
private val GrisBorde = Color(0xFFE9ECEF)

data class VentaDia(
    val nombre: String,
    val letra: String,
    val venta: Double,
    val clientes: Int,
    val metaClientes: Int = 0,
    val esFuturo: Boolean = false
)

@Composable
fun VendedorInfoVentasScreen() {
    val scrollState = rememberScrollState()

    // --- DATOS SIMULADOS ---
    val metaVentaDia = 5000.0
    val diaHoyIndice = 2 // Miércoles

    val semanaData = listOf(
        VentaDia("Lunes", "L", 4200.0, 18, 30),
        VentaDia("Martes", "M", 3850.0, 15, 30),
        VentaDia("Miércoles", "M", 3120.0, 16, 32), // HOY: 16 de 32
        VentaDia("Jueves", "J", 0.0, 0, 30, true),
        VentaDia("Viernes", "V", 0.0, 0, 30, true),
        VentaDia("Sábado", "S", 0.0, 0, 25, true)
    )

    // Cálculos Acumulados de la Semana
    val ventaAcumuladaSemana = semanaData.sumOf { it.venta }
    val clientesAcumuladosSemana = semanaData.sumOf { it.clientes }
    val ticketPromedioSemana = if (clientesAcumuladosSemana > 0) ventaAcumuladaSemana / clientesAcumuladosSemana else 0.0

    var diaSeleccionado by remember { mutableStateOf(semanaData[diaHoyIndice]) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GrisFondo)
            .verticalScroll(scrollState)
    ) {
        /* ================= 1. HEADER: EL DÍA EN CURSO (16/32 Clientes) ================= */
        val hoy = semanaData[diaHoyIndice]
        val progresoVenta = (hoy.venta / metaVentaDia).toFloat().coerceIn(0f, 1f)
        val ticketPromedioHoy = if (hoy.clientes > 0) hoy.venta / hoy.clientes else 0.0

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(RojoPrincipal)
                .statusBarsPadding()
                .padding(top = 24.dp, bottom = 32.dp, start = 24.dp, end = 24.dp)
        ) {
            Column {
                Text("VENTA DE HOY", color = Blanco.copy(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$${"%,.2f".format(hoy.venta)}", color = Blanco, fontSize = 42.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text("${(progresoVenta * 100).toInt()}%", color = Blanco, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))
                // Barra de progreso de la meta de dinero
                Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Blanco.copy(0.2f))) {
                    Box(Modifier.fillMaxWidth(progresoVenta).fillMaxHeight().clip(CircleShape).background(Blanco))
                }

                Spacer(Modifier.height(24.dp))

                // KPIs: Clientes format 16/32 y Ticket Promedio
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    KpiHeaderItem("Clientes", "${hoy.clientes} / ${hoy.metaClientes}")
                    KpiHeaderItem("Ticket Prom.", "$${"%,.0f".format(ticketPromedioHoy)}")
                    KpiHeaderItem("Meta Día", "$${"%,.0f".format(metaVentaDia)}")
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))

            /* ================= 2. ACUMULADO SEMANAL (Con Ticket Promedio) ================= */
            SectionTitle("Acumulado Semanal")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NegroBancario),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("VENTA TOTAL", color = RojoPrincipal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("$${"%,.2f".format(ventaAcumuladaSemana)}", color = Blanco, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Blanco.copy(0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Semana Activa", color = Blanco, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Divider(Modifier.padding(vertical = 16.dp), thickness = 0.5.dp, color = Blanco.copy(0.1f))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        KpiAcumuladoItem("Clientes", "$clientesAcumuladosSemana")
                        KpiAcumuladoItem("Ticket Prom.", "$${"%,.0f".format(ticketPromedioSemana)}")
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            /* ================= 3. HISTORIAL INTERACTIVO ================= */
            SectionTitle("Detalle por Día")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Blanco),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        semanaData.forEachIndexed { index, dia ->
                            val esHoy = index == diaHoyIndice
                            val isSelected = diaSeleccionado.nombre == dia.nombre

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) RojoPrincipal else if (esHoy) RojoPrincipal.copy(0.1f) else GrisFondo)
                                        .clickable { diaSeleccionado = dia },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dia.letra,
                                        color = if (isSelected) Blanco else if (esHoy) RojoPrincipal else NegroBancario,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (esHoy) {
                                    Box(Modifier.padding(top = 4.dp).size(4.dp).background(RojoPrincipal, CircleShape))
                                }
                            }
                        }
                    }

                    Divider(Modifier.padding(vertical = 18.dp), thickness = 0.5.dp, color = GrisBorde)

                    if (diaSeleccionado.esFuturo) {
                        Text("DÍA SIN ACTIVIDAD", color = GrisTexto, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Column {
                            Text(diaSeleccionado.nombre.uppercase(), color = RojoPrincipal, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                KpiDetalleDia("Venta", "$${"%,.0f".format(diaSeleccionado.venta)}")
                                KpiDetalleDia("Clientes", "${diaSeleccionado.clientes}")
                                KpiDetalleDia("Ticket P.", "$${if(diaSeleccionado.clientes > 0) "%,.0f".format(diaSeleccionado.venta/diaSeleccionado.clientes) else "0"}")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            /* ================= 4. FOOTER BLOQUE ================= */
            SectionTitle("Meta de Bloque")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Blanco),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GrisBorde)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(progress = 0.5f, color = RojoPrincipal, strokeWidth = 5.dp, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Día 12 de 24 (50%)", color = NegroBancario, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/* ===================== COMPONENTES DE APOYO ===================== */

@Composable
fun KpiHeaderItem(label: String, value: String) {
    Column {
        Text(label, color = Blanco.copy(0.6f), fontSize = 11.sp)
        Text(value, color = Blanco, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun KpiAcumuladoItem(label: String, value: String) {
    Column {
        Text(label, color = Blanco.copy(0.4f), fontSize = 11.sp)
        Text(value, color = Blanco, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun KpiDetalleDia(label: String, value: String) {
    Column {
        Text(label, color = GrisTexto, fontSize = 11.sp)
        Text(value, color = NegroBancario, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = NegroBancario,
        modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
        letterSpacing = 1.sp
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewFinal() {
    VendedorInfoVentasScreen()
}