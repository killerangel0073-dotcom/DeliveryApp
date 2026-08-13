package com.gruposanangel.delivery.ui.screens

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.RepositoryInventario
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.io.FileOutputStream
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.gruposanangel.delivery.ui.theme.*
import androidx.compose.foundation.isSystemInDarkTheme

data class VentaReporteItem(
    val cliente: String, 
    val piezas: Int, 
    val monto: Double, 
    val hora: Int, 
    val minutos: Int = 0,
    val estado: String = "VENTA"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaCierreDia(
    navController: NavController,
    uiState: DashboardVendedorUiState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDatabase.getDatabase(context)
    val ventaRepo = VentaRepository(db.VentaDao(), db.productoDao())
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    
    val totalVenta = uiState.ventaDia
    val totalGastos = uiState.totalGastosHoy
    val efectivoNetoAEntregar = totalVenta - totalGastos

    var showCashCounter by remember { mutableStateOf(false) }
    var showExpensesDetail by remember { mutableStateOf(false) } 
    val sheetState = rememberModalBottomSheetState()
    
    val cashState = remember { mutableStateMapOf(
        1000 to 0, 500 to 0, 200 to 0, 100 to 0, 50 to 0, 20 to 0,
        10 to 0, 5 to 0, 2 to 0, 1 to 0
    )}
    val totalContado = cashState.entries.sumOf { it.key * it.value }.toDouble()
    val diferencia = totalContado - efectivoNetoAEntregar

    var showConfirmLiquidation by remember { mutableStateOf(false) }
    val isDark = ThemeConfig.isActuallyDark

    DeliveryTheme(darkTheme = isDark) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("RESUMEN OPERATIVO", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Atrás", tint = DelisaRed)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .shadow(12.dp, RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(DelisaRed, DelisaRedDark)))
                            .padding(vertical = 28.dp, horizontal = 20.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "LIQUIDACIÓN TOTAL DEL DÍA", 
                                color = Color.White.copy(0.8f), 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Black, 
                                letterSpacing = 1.5.sp
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            
                            Text(
                                text = formatoMoneda.format(efectivoNetoAEntregar), 
                                color = Color.White, 
                                fontSize = 44.sp, 
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Venta: ${formatoMoneda.format(totalVenta)}", color = Color.White.copy(0.9f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("  |  ", color = Color.White.copy(0.3f))
                                Text("Gastos: -${formatoMoneda.format(totalGastos)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }

                            if (totalContado > 0) {
                                Spacer(Modifier.height(20.dp))
                                Surface(
                                    color = Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), 
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("EFECTIVO CONTADO", color = Color.White.copy(0.7f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            Text(formatoMoneda.format(totalContado), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                        }
                                        
                                        Spacer(Modifier.width(16.dp))
                                        Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(0.2f)))
                                        Spacer(Modifier.width(16.dp))

                                        val colorDif = when {
                                            Math.abs(diferencia) < 1 -> Color(0xFF81C784) // Verde pastel
                                            diferencia > 0 -> Color(0xFFFFF176) // Amarillo pastel
                                            else -> Color(0xFFFFAB91) // Rojo pastel
                                        }
                                        
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("DIFERENCIA", color = Color.White.copy(0.7f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (Math.abs(diferencia) < 1) "OK" else formatoMoneda.format(diferencia),
                                                color = colorDif,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(20.dp))
                            Surface(
                                color = Color.Black.copy(alpha = 0.2f),
                                shape = CircleShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("INFORMACIÓN LISTA PARA ENVÍO", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                }
                            }
                        }
                    }
                }

                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CierreMiniCard("Clientes", "${uiState.clientesDia}", Icons.Rounded.Groups, DelisaBlue, Modifier.weight(1f))
                    CierreMiniCard("Promedio", formatoMoneda.format(uiState.ticketPromedioDia), Icons.Rounded.Analytics, WarningOrange, Modifier.weight(1f))
                }
                
                Spacer(Modifier.height(12.dp))

                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CierreMiniCard("Venta Bruta", formatoMoneda.format(uiState.ventaDia), Icons.Rounded.Payments, DelisaGreenDark, Modifier.weight(1f))
                    CierreMiniCard(
                        titulo = "Gastos Hoy", 
                        valor = formatoMoneda.format(uiState.totalGastosHoy), 
                        icono = Icons.AutoMirrored.Rounded.ReceiptLong,
                        color = DelisaRed, 
                        modifier = Modifier.weight(1f),
                        onClick = { showExpensesDetail = true }
                    )
                }

                Spacer(Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { showCashCounter = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, DelisaGreenDark),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DelisaGreenDark)
                ) {
                    Icon(Icons.Rounded.Calculate, null)
                    Spacer(Modifier.width(12.dp))
                    Text("CONTADOR DE CAJA", fontWeight = FontWeight.Black)
                }

                Spacer(Modifier.height(24.dp))

                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("ACCIONES DE FINALIZACIÓN", fontWeight = FontWeight.Black, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                    
                    Button(
                        onClick = {
                            scope.launch {
                                val ventasItems = withContext(Dispatchers.IO) {
                                    uiState.ventasHoy.map { v ->
                                        val piezas = ventaRepo.obtenerDetallesDeVenta(v.id).sumOf { it.cantidad }
                                        val cal = Calendar.getInstance().apply { timeInMillis = v.fecha }
                                        VentaReporteItem(
                                            cliente = v.clienteNombre, 
                                            piezas = piezas, 
                                            monto = v.total, 
                                            hora = cal.get(Calendar.HOUR_OF_DAY), 
                                            minutos = cal.get(Calendar.MINUTE),
                                            estado = if (v.estado == "CANCELADA") "ANULADA" else "VENTA"
                                        )
                                    }.sortedWith(compareBy({ it.hora }, { it.minutos }))
                                }
                                val file = GenerarPDFCierreCarta(context, uiState, ventasItems, false)
                                abrirPdfCierre(context, file)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp).shadow(4.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
                    ) {
                        Icon(Icons.Rounded.Description, null, tint = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Text("GENERAR REPORTE PDF", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                    }

                    if (totalContado > 0) {
                        Button(
                            onClick = { showConfirmLiquidation = true },
                            modifier = Modifier.fillMaxWidth().height(64.dp).shadow(4.dp, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DelisaGreenDark)
                        ) {
                            Icon(Icons.Rounded.CloudUpload, null, tint = Color.White)
                            Spacer(Modifier.width(12.dp))
                            Text("LIQUIDAR Y CERRAR RUTA", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            // Demo data logic remains the same
                            val nombresTiendas = listOf("Abarrotes La Esperanza", "Tienda El Paso", "Miscelánea Doña Mary", "Abarrotes Don Pepe")
                            val estados = listOf("VENTA", "VENTA", "SIN VENTA")
                            val demoData = nombresTiendas.map { nombre ->
                                VentaReporteItem(nombre, (15..60).random(), (450..1800).random().toDouble(), (7..17).random(), (0..59).random(), estados.random())
                            }.sortedWith(compareBy({ it.hora }, { it.minutos }))
                            val file = GenerarPDFCierreCarta(context, uiState, demoData, true)
                            abrirPdfCierre(context, file)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Icon(Icons.Rounded.AutoMode, null)
                        Spacer(Modifier.width(12.dp))
                        Text("VISTA PREVIA DESEMPEÑO", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }

        // --- HOJA DESLIZABLE: DETALLE DE GASTOS ---
        if (showExpensesDetail) {
            ModalBottomSheet(
                onDismissRequest = { showExpensesDetail = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("DETALLE DE GASTOS", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Registros realizados hoy", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    
                    Spacer(Modifier.height(24.dp))
                    
                    if (uiState.gastosHoy.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text("No hay gastos registrados hoy.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            uiState.gastosHoy.forEach { gasto ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(gasto.categoria, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                        if (gasto.descripcion.isNotEmpty()) {
                                            Text(gasto.descripcion, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(formatoMoneda.format(gasto.monto), fontWeight = FontWeight.Black, color = DelisaRed)
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { showExpensesDetail = false },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("LISTO, VOLVER AL CIERRE", color = MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }

        // --- HOJA DESLIZABLE: CONTADOR DE CAJA ---
        if (showCashCounter) {
            ModalBottomSheet(
                onDismissRequest = { showCashCounter = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("ARQUEO DE EFECTIVO", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Desglose de billetes y monedas", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    
                    Spacer(Modifier.height(24.dp))
                    
                    cashState.keys.sortedByDescending { it }.forEach { den ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.width(65.dp)) {
                                Text(text = "$$den", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                                Text(text = if (den >= 20) "BILLETE" else "MONEDA", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { if(cashState[den]!! > 0) cashState[den] = cashState[den]!! - 1 }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                                }
                                OutlinedTextField(
                                    value = if (cashState[den]!! == 0) "" else "${cashState[den]}",
                                    onValueChange = { newValue ->
                                        val cleanValue = newValue.filter { it.isDigit() }
                                        cashState[den] = if (cleanValue.isEmpty()) 0 else if (cleanValue.length <= 4) cleanValue.toInt() else cashState[den]!!
                                    },
                                    modifier = Modifier.width(75.dp).padding(horizontal = 4.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DelisaGreenDark, unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                )
                                IconButton(onClick = { if (cashState[den]!! < 9999) cashState[den] = cashState[den]!! + 1 }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.AddCircle, null, tint = DelisaGreenDark)
                                }
                            }
                            Column(modifier = Modifier.width(100.dp), horizontalAlignment = Alignment.End) {
                                Text(text = formatoMoneda.format(den * cashState[den]!!), fontWeight = FontWeight.Black, fontSize = 14.sp, color = if (cashState[den]!! > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Contado:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(formatoMoneda.format(totalContado), fontWeight = FontWeight.Black, fontSize = 18.sp, color = DelisaGreenDark)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("A Entregar:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatoMoneda.format(efectivoNetoAEntregar), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Diferencia:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(formatoMoneda.format(diferencia), fontWeight = FontWeight.Black, color = if (Math.abs(diferencia) < 1) DelisaGreenDark else DelisaRed)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showCashCounter = false }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)) {
                        Text("LISTO, VOLVER AL CIERRE", color = MaterialTheme.colorScheme.surface)
                    }
                }
            }
        }

        if (showConfirmLiquidation) {
            val dbV = AppDatabase.getDatabase(context)
            val firebaseDataSource = FirebaseDataSource()
            val viewModelDashboard: DashboardVendedorViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = 
                        DashboardVendedorViewModel(
                            VentaRepository(dbV.VentaDao(), dbV.productoDao()), 
                            RepositoryUsuario(firebaseDataSource, dbV.usuarioDao()), 
                            RepositoryInventario(firebaseDataSource, dbV.productoDao(), dbV.VentaDao(), dbV.movimientoInventarioDao()),
                            com.gruposanangel.delivery.data.RepositoryGasto(dbV.gastoDao()),
                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        ) as T
                }
            )

            AlertDialog(
                onDismissRequest = { showConfirmLiquidation = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Confirmar Liquidación", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Se enviará el reporte de caja a la administración y se cerrará tu jornada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Efectivo Contado:", color = MaterialTheme.colorScheme.onSurface)
                            Text(formatoMoneda.format(totalContado), fontWeight = FontWeight.Bold, color = DelisaGreenDark)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Diferencia:", color = MaterialTheme.colorScheme.onSurface)
                            Text(formatoMoneda.format(diferencia), fontWeight = FontWeight.Bold, color = if (Math.abs(diferencia) < 1) DelisaGreenDark else DelisaRed)
                        }
                        if (uiState.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DelisaRed)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModelDashboard.finalizarJornadaYLiquidar(
                                ventaTotal = efectivoNetoAEntregar,
                                efectivoContado = totalContado,
                                diferencia = diferencia,
                                desgloseEfectivo = cashState.toMap()
                            ) {
                                showConfirmLiquidation = false
                                Toast.makeText(context, "Liquidación enviada con éxito", Toast.LENGTH_LONG).show()
                                navController.popBackStack()
                            }
                        },
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = DelisaGreenDark),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("CONFIRMAR Y ENVIAR", color = Color.White, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmLiquidation = false }, enabled = !uiState.isLoading) { 
                        Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant) 
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

@Composable
fun CierreMiniCard(
    titulo: String, 
    valor: String, 
    icono: androidx.compose.ui.graphics.vector.ImageVector, 
    color: Color, 
    modifier: Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp)) {
            Box(Modifier.background(color.copy(0.1f), CircleShape).padding(8.dp)) {
                Icon(icono, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(titulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(valor, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

fun GenerarPDFCierreCarta(
    context: Context, 
    state: DashboardVendedorUiState, 
    ventas: List<VentaReporteItem>, 
    esDemo: Boolean,
    fechaManual: Long? = null 
): File {
    val pdfDocument = PdfDocument()
    val pageWidth = 612; val pageHeight = 792 
    val nf = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    
    val fechaReferencia = if (fechaManual != null) Date(fechaManual) else Date()
    
    val pDelisaRed = Paint().apply { color = android.graphics.Color.rgb(227, 6, 19); style = Paint.Style.FILL; isAntiAlias = true }
    val pTitle = Paint().apply { textSize = 18f; color = android.graphics.Color.WHITE; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pSub = Paint().apply { textSize = 9f; color = android.graphics.Color.LTGRAY; typeface = Typeface.SANS_SERIF; isAntiAlias = true }
    val pText = Paint().apply { textSize = 10.5f; color = android.graphics.Color.BLACK; typeface = Typeface.SANS_SERIF; isAntiAlias = true }
    val pBold = Paint().apply { textSize = 11f; color = android.graphics.Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pRedText = Paint().apply { textSize = 11f; color = android.graphics.Color.rgb(227, 6, 19); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }

    val totalVentaBruta = if(esDemo) 12500.0 else state.ventaDia
    val totalGastos = if(esDemo) 654.50 else state.totalGastosHoy
    val totalVentaNeta = totalVentaBruta - totalGastos
    val clientesProgramados = ventas.size
    val clientesConVenta = ventas.count { it.estado == "VENTA" }
    val clientesSinVenta = clientesProgramados - clientesConVenta
    val efectividad = if (clientesProgramados > 0) (clientesConVenta.toFloat() / clientesProgramados) * 100 else 0f
    val ticketPromedio = if (clientesConVenta > 0) totalVentaNeta / clientesConVenta else 0.0
    val productividad = if (clientesProgramados > 0) totalVentaNeta / clientesProgramados else 0.0
    val totalPiezas = ventas.sumOf { it.piezas }

    val itemsFirstPage = 14
    val itemsOtherPages = 32
    val totalPages = if (ventas.size <= itemsFirstPage) 1 else 1 + Math.ceil((ventas.size - itemsFirstPage).toDouble() / itemsOtherPages).toInt()
    
    var currentPageNumber = 1
    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas

    fun drawHeader(canv: Canvas, pNum: Int) {
        if (pNum == 1) {
            val headerH = 100f
            val headerGradient = LinearGradient(0f, 0f, 0f, headerH, android.graphics.Color.rgb(227, 6, 19), android.graphics.Color.rgb(160, 0, 10), Shader.TileMode.CLAMP)
            canv.drawRect(0f, 0f, pageWidth.toFloat(), headerH, Paint().apply { shader = headerGradient; isAntiAlias = true })
            
            val logo = context.getDrawable(R.drawable.logo)
            logo?.let { it.setBounds(40, 15, 140, 75); it.draw(canv) }
            
            canv.drawText("REPORTE DE DESEMPEÑO", 160f, 35f, pTitle.apply { textSize = 22f })
            pBold.color = android.graphics.Color.WHITE; pBold.textSize = 9f
            canv.drawText("VENDEDOR: ${state.nombreVendedor.uppercase()}", 160f, 65f, pBold)
            canv.drawText("HOJA $pNum DE $totalPages", 572f, 35f, pBold.apply { textAlign = Paint.Align.RIGHT })
            
            val df = SimpleDateFormat("EEEE dd 'DE' MMMM 'DEL' yyyy", Locale("es", "MX")).apply {
                if (fechaManual != null) timeZone = TimeZone.getTimeZone("UTC")
            }
            canv.drawText("RUTA: ${state.rutaNombre.uppercase()}", 160f, 85f, pBold.apply { textAlign = Paint.Align.LEFT })
            canv.drawText(df.format(fechaReferencia).uppercase(), 572f, 85f, pBold.apply { textAlign = Paint.Align.RIGHT })
            pBold.color = android.graphics.Color.BLACK; pBold.textAlign = Paint.Align.LEFT
        } else {
            canv.drawText("HOJA $pNum DE $totalPages", 572f, 30f, pSub.apply { textAlign = Paint.Align.RIGHT })
            pSub.textAlign = Paint.Align.LEFT
        }
    }

    drawHeader(canvas, currentPageNumber)
    var y = 130f

    canvDrawTextCentered(canvas, "RESUMEN OPERATIVO DE RUTA", pageWidth / 2f, y, pBold); y += 20f
    
    val colorEfectividad = when {
        efectividad >= 85f -> android.graphics.Color.rgb(200, 230, 201)
        efectividad >= 75f -> android.graphics.Color.rgb(255, 245, 157)
        else -> android.graphics.Color.rgb(255, 205, 210)
    }

    val segW = 532f; val segH = 45f; val segX = (pageWidth - segW) / 2f
    canvas.drawRoundRect(segX, y, segX + segW, y + segH, 8f, 8f, Paint().apply { color = colorEfectividad })
    
    val colW = segW / 4f
    fun drawSubKpiLocal(l: String, v: String, i: Int) {
        val cx = segX + (i * colW) + (colW / 2f)
        canvas.drawText(l.uppercase(), cx, y + 16f, pSub.apply { textAlign = Paint.Align.CENTER; color = android.graphics.Color.rgb(60,60,60) })
        canvas.drawText(v, cx, y + 34f, pBold.apply { textAlign = Paint.Align.CENTER; textSize = 12f })
    }
    drawSubKpiLocal("Programados", "$clientesProgramados", 0)
    drawSubKpiLocal("Con Venta", "$clientesConVenta", 1)
    drawSubKpiLocal("Sin Venta", "$clientesSinVenta", 2)
    drawSubKpiLocal("Efectividad", "${"%.1f".format(efectividad)}%", 3)
    y += segH + 15f

    val finKpiW = 172f; val finKpiH = 40f; val finKpiGap = 8f
    val startXFin = (pageWidth - (finKpiW * 3 + finKpiGap * 2)) / 2f
    fun drawFinKpiLocal(l: String, v: String, x: Float, bg: Int, tc: Int = android.graphics.Color.BLACK) {
        canvas.drawRoundRect(x, y, x + finKpiW, y + finKpiH, 6f, 6f, Paint().apply { color = bg })
        canvas.drawText(l, x + finKpiW/2, y + 15f, pSub.apply { textAlign = Paint.Align.CENTER; color = android.graphics.Color.GRAY })
        canvas.drawText(v, x + finKpiW/2, y + 32f, pBold.apply { textAlign = Paint.Align.CENTER; color = tc })
    }
    drawFinKpiLocal("Venta Bruta", nf.format(totalVentaBruta), startXFin, android.graphics.Color.rgb(248,248,248))
    drawFinKpiLocal("Gastos Hoy", "-${nf.format(totalGastos)}", startXFin + finKpiW + finKpiGap, android.graphics.Color.rgb(255, 235, 238), android.graphics.Color.rgb(198, 40, 40))
    drawFinKpiLocal("Efectivo Neto", nf.format(totalVentaNeta), startXFin + 2*(finKpiW + finKpiGap), android.graphics.Color.rgb(232, 245, 233), android.graphics.Color.rgb(46, 125, 50))
    y += finKpiH + 12f
    
    drawFinKpiLocal("Ticket Prom.", nf.format(ticketPromedio), startXFin, android.graphics.Color.rgb(248,248,248))
    drawFinKpiLocal("Productividad", nf.format(productividad), startXFin + finKpiW + finKpiGap, android.graphics.Color.rgb(248,248,248))
    drawFinKpiLocal("Piezas Totales", "$totalPiezas pzas", startXFin + 2*(finKpiW + finKpiGap), android.graphics.Color.rgb(248,248,248))
    y += finKpiH + 25f

    pBold.textAlign = Paint.Align.CENTER
    canvas.drawText("DETALLE CRONOLÓGICO DE VISITAS", pageWidth / 2f, y, pBold); y += 22f
    
    fun drawTableHeaderLocal(canv: Canvas, curY: Float) {
        canv.drawRect(40f, curY, 572f, curY + 1.5f, pDelisaRed)
        pBold.textAlign = Paint.Align.LEFT
        canv.drawText("#", 45f, curY + 14f, pBold)
        canv.drawText("HORA", 65f, curY + 14f, pBold)
        canv.drawText("CLIENTE / ESTABLECIMIENTO", 115f, curY + 14f, pBold)
        canv.drawText("ESTADO", 335f, curY + 14f, pBold)
        canv.drawText("PZAS", 455f, curY + 14f, pBold)
        canv.drawText("TOTAL", 510f, curY + 14f, pBold)
    }
    drawTableHeaderLocal(canvas, y); y += 22f

    ventas.forEachIndexed { index, v ->
        if (y > 730f) {
            pdfDocument.finishPage(page); currentPageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()); canvas = page.canvas
            drawHeader(canvas, currentPageNumber); y = 62f; drawTableHeaderLocal(canvas, y); y += 22f
        }
        val bgC = when(v.estado) { "VENTA" -> android.graphics.Color.rgb(232, 245, 233); "ANULADA" -> android.graphics.Color.rgb(245, 245, 245); "SIN VENTA" -> android.graphics.Color.rgb(255, 235, 238); else -> android.graphics.Color.rgb(255, 253, 231) }
        canvas.drawRect(40f, y, 572f, y + 20f, Paint().apply { color = bgC })
        canvas.drawText("${index + 1}", 45f, y + 15f, pText)
        canvas.drawText(String.format(Locale.US, "%02d:%02d", v.hora, v.minutos), 65f, y + 15f, pText)
        canvas.drawText(v.cliente.take(35), 115f, y + 15f, pText)
        canvas.drawText(v.estado, 335f, y + 15f, pBold.apply { textSize = 10f })
        canvas.drawText("${v.piezas}", 455f, y + 15f, pText)
        canvas.drawText(nf.format(v.monto), 510f, y + 15f, pText)
        y += 20f
    }

    // --- NUEVA SECCIÓN: DETALLE DE GASTOS ---
    if (state.gastosHoy.isNotEmpty()) {
        y += 25f
        if (y > 700f) {
            pdfDocument.finishPage(page); currentPageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()); canvas = page.canvas
            drawHeader(canvas, currentPageNumber); y = 62f
        }
        
        pBold.textAlign = Paint.Align.CENTER
        canvas.drawText("DETALLE DE GASTOS DEL DÍA", pageWidth / 2f, y, pBold.apply { textSize = 11f }); y += 22f
        
        fun drawExpensesHeaderLocal(canv: Canvas, curY: Float) {
            canv.drawRect(40f, curY, 572f, curY + 1.2f, pDelisaRed)
            pBold.textAlign = Paint.Align.LEFT
            canv.drawText("CATEGORÍA", 50f, curY + 14f, pBold)
            canv.drawText("DESCRIPCIÓN", 200f, curY + 14f, pBold)
            canv.drawText("MONTO", 510f, curY + 14f, pBold)
        }
        drawExpensesHeaderLocal(canvas, y); y += 22f
        
        state.gastosHoy.forEach { gasto ->
            if (y > 730f) {
                pdfDocument.finishPage(page); currentPageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()); canvas = page.canvas
                drawHeader(canvas, currentPageNumber); y = 62f; drawExpensesHeaderLocal(canvas, y); y += 22f
            }
            canvas.drawRect(40f, y, 572f, y + 18f, Paint().apply { color = android.graphics.Color.rgb(250, 250, 250) })
            canvas.drawText(gasto.categoria.uppercase(), 50f, y + 13f, pText.apply { textSize = 9f })
            canvas.drawText(gasto.descripcion.take(45), 200f, y + 13f, pText)
            canvas.drawText(nf.format(gasto.monto), 510f, y + 13f, pBold.apply { textAlign = Paint.Align.LEFT; textSize = 10f; color = android.graphics.Color.rgb(198, 40, 40) })
            y += 18f
        }
    }

    if (y > 710f) { pdfDocument.finishPage(page); currentPageNumber++; page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()); canvas = page.canvas; y = 40f }
    val footerY = 710f
    canvas.drawRect(40f, footerY, 572f, footerY + 1.2f, pDelisaRed)
    try {
        val qrContent = "DELISA_AUDIT|${state.nombreVendedor}|EFECT:${totalVentaNeta}|EFEC:${efectividad}%"
        val bitMatrix = QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 200, 200)
        val qrBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.RGB_565)
        for (i in 0 until 200) for (j in 0 until 200) qrBitmap.setPixel(i, j, if (bitMatrix.get(i, j)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        canvas.drawBitmap(qrBitmap, null, RectF(40f, footerY + 15f, 90f, footerY + 65f), null)
    } catch (e: Exception) { }
    canvas.drawText("REPORTE OPERATIVO DELISA / SEGURIDAD EN RUTA", 306f, 780f, pSub.apply { textAlign = Paint.Align.CENTER })

    pdfDocument.finishPage(page)
    val name = "Cierre_${state.rutaNombre.replace(" ", "")}_${SimpleDateFormat("ddMMyy", Locale.US).format(fechaReferencia)}.pdf"
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), name)
    pdfDocument.writeTo(FileOutputStream(file)); pdfDocument.close()
    return file
}

private fun canvDrawTextCentered(c: Canvas, t: String, x: Float, y: Float, p: Paint) {
    val oldAlign = p.textAlign
    p.textAlign = Paint.Align.CENTER
    c.drawText(t, x, y, p)
    p.textAlign = oldAlign
}

fun abrirPdfCierre(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/pdf"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(intent, "Abrir Reporte"))
    } catch (e: Exception) { }
}
