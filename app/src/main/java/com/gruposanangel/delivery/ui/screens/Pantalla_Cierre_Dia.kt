package com.gruposanangel.delivery.ui.screens

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.io.File
import java.io.FileOutputStream
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

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
    val ventaRepo = VentaRepository(db.VentaDao())
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    
    val efectivoAEntregar = uiState.ventaDia 

    // --- ESTADO DEL CONTADOR DE DINERO ---
    var showCashCounter by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    // Denominaciones México: Valor to Cantidad
    val cashState = remember { mutableStateMapOf(
        1000 to 0, 500 to 0, 200 to 0, 100 to 0, 50 to 0, 20 to 0, // Billetes
        10 to 0, 5 to 0, 2 to 0, 1 to 0 // Monedas principales
    )}
    val totalContado = cashState.entries.sumOf { it.key * it.value }.toDouble()
    val diferencia = totalContado - efectivoAEntregar

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resumen Operativo", fontWeight = FontWeight.Black) },
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
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("LIQUIDACIÓN TOTAL DEL DÍA", color = Color.White.copy(0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(formatoMoneda.format(efectivoAEntregar), color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
                    
                    if (totalContado > 0) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Contado: ${formatoMoneda.format(totalContado)}", color = Color.White.copy(0.8f), fontSize = 14.sp)
                            Spacer(Modifier.width(12.dp))
                            val colorDif = if (Math.abs(diferencia) < 1) Color(0xFF4CAF50) else if (diferencia > 0) Color.Yellow else Color.Red
                            Text(
                                text = if (Math.abs(diferencia) < 1) "CUADRADO" else "Dif: ${formatoMoneda.format(diferencia)}",
                                color = colorDif,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text("RUTA CONCILIADA", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CierreMiniCard("Atendidos", "${uiState.clientesDia}", Icons.Rounded.Groups, Color(0xFF2196F3), Modifier.weight(1f))
                CierreMiniCard("Promedio", formatoMoneda.format(uiState.ticketPromedioDia), Icons.Rounded.Analytics, Color(0xFFFF9800), Modifier.weight(1f))
            }

            Spacer(Modifier.height(24.dp))

            // 🔥 HERRAMIENTA DE CONTADOR
            OutlinedButton(
                onClick = { showCashCounter = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF4CAF50))
            ) {
                Icon(Icons.Rounded.Calculate, null, tint = Color(0xFF4CAF50))
                Spacer(Modifier.width(12.dp))
                Text("CONTADOR DE CAJA", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("EXPORTACIÓN EJECUTIVA", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp)
                
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
                                        estado = "VENTA"
                                    )
                                }.sortedWith(compareBy({ it.hora }, { it.minutos }))
                            }
                            val file = GenerarPDFCierreCarta(context, uiState, ventasItems, false)
                            abrirPdfCierre(context, file)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE30613))
                ) {
                    Icon(Icons.Rounded.Description, null)
                    Spacer(Modifier.width(12.dp))
                    Text("GENERAR REPORTE", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = {
                        val nombresTiendas = listOf(
                            "Abarrotes La Esperanza", "Miscelánea San Judas", "Mini Super La Bendición", 
                            "Tienda El Paso", "Abarrotes Los Primos", "Cervecería El Puerto", 
                            "Miscelánea Doña Mary", "Abarrotes El Güero", "Mini Super Sol", 
                            "Tienda La Pasadita", "Miscelánea El Recreo", "Abarrotes Santa Fe", 
                            "Mini Super Ámbar", "Tienda El Oasis", "Miscelánea Las Flores", 
                            "Abarrotes El Triunfo", "Mini Super Galaxia", "Tienda La Unión", 
                            "Miscelánea San José", "Abarrotes Mi Pueblito", "Abarrotes Don Pepe",
                            "Mini Super El Amigo", "Miscelánea El Sol", "Tienda La Guadalupana",
                            "Abarrotes El Milagro", "Mini Super La Esquina", "Cervecería La Terraza",
                            "Miscelánea Los Angeles", "Tienda El Porvenir", "Abarrotes La Union",
                            "Mini Super Express", "Miscelánea La Fe", "Tienda El Progreso",
                            "Abarrotes El Ahorro", "Mini Super San Angel"
                        )
                        val estados = listOf("VENTA", "VENTA", "VENTA", "SIN VENTA", "CERRADO", "NO LOCALIZADO", "VENTA")
                        
                        val demoData = nombresTiendas.take(35).shuffled().map { nombre ->
                            val estadoAleatorio = estados.random()
                            val esVenta = estadoAleatorio == "VENTA"
                            VentaReporteItem(
                                cliente = nombre,
                                piezas = if (esVenta) (15..60).random() else 0,
                                monto = if (esVenta) (450..1800).random().toDouble() else 0.0,
                                hora = (7..17).random(),
                                minutos = (0..59).random(),
                                estado = estadoAleatorio
                            )
                        }.sortedWith(compareBy({ it.hora }, { it.minutos }))
                        
                        val file = GenerarPDFCierreCarta(context, uiState, demoData, true)
                        abrirPdfCierre(context, file)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1A1A1A))
                ) {
                    Icon(Icons.Rounded.AutoMode, null, tint = Color(0xFF1A1A1A))
                    Spacer(Modifier.width(12.dp))
                    Text("VISTA PREVIA DESEMPEÑO", color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(40.dp))
        }

        // --- HOJA DESLIZABLE: CONTADOR DE CAJA ---
        if (showCashCounter) {
            ModalBottomSheet(
                onDismissRequest = { showCashCounter = false },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("ARQUEO DE EFECTIVO", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("Desglose de billetes y monedas", color = Color.Gray, fontSize = 12.sp)
                    
                    Spacer(Modifier.height(24.dp))
                    
                    // Lista de denominaciones
                    cashState.keys.sortedByDescending { it }.forEach { den ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Valor de la denominación (Billetes vs Monedas)
                            Column(modifier = Modifier.width(65.dp)) {
                                Text(
                                    text = "$$den",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = if (den >= 20) Color(0xFF1A1A1A) else Color(0xFF546E7A)
                                )
                                Text(
                                    text = if (den >= 20) "BILLETE" else "MONEDA",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }
                            
                            // Selector de Cantidad (Botones + Input Manual)
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { if(cashState[den]!! > 0) cashState[den] = cashState[den]!! - 1 },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.RemoveCircleOutline, null, tint = Color.LightGray)
                                }
                                
                                // Input Manual Premium
                                OutlinedTextField(
                                    value = if (cashState[den]!! == 0) "" else "${cashState[den]}",
                                    onValueChange = { newValue ->
                                        val cleanValue = newValue.filter { it.isDigit() }
                                        cashState[den] = if (cleanValue.isEmpty()) 0 else cleanValue.toInt()
                                    },
                                    modifier = Modifier
                                        .width(75.dp)
                                        .padding(horizontal = 4.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF4CAF50),
                                        unfocusedBorderColor = Color(0xFFE0E0E0),
                                        cursorColor = Color(0xFF4CAF50)
                                    )
                                )

                                IconButton(
                                    onClick = { cashState[den] = cashState[den]!! + 1 },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.AddCircle, null, tint = Color(0xFF4CAF50))
                                }
                            }
                            
                            // Subtotal por denominación
                            Column(modifier = Modifier.width(100.dp), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatoMoneda.format(den * cashState[den]!!), 
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = if (cashState[den]!! > 0) Color(0xFF1A1A1A) else Color.LightGray
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0xFFF8F9FA))
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    
                    // Resumen del arqueo
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Contado:", fontWeight = FontWeight.Bold)
                                Text(formatoMoneda.format(totalContado), fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF4CAF50))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Venta del Día:", color = Color.Gray)
                                Text(formatoMoneda.format(efectivoAEntregar), color = Color.Gray)
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Diferencia:", fontWeight = FontWeight.Bold)
                                Text(
                                    text = formatoMoneda.format(diferencia), 
                                    fontWeight = FontWeight.Black, 
                                    color = if (Math.abs(diferencia) < 1) Color(0xFF4CAF50) else Color.Red
                                )
                            }
                        }
                    }
                    
                    Button(
                        onClick = { showCashCounter = false },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                    ) {
                        Text("LISTO, VOLVER AL CIERRE")
                    }
                }
            }
        }
    }
}

@Composable
fun CierreMiniCard(titulo: String, valor: String, icono: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Box(Modifier.background(color.copy(0.1f), CircleShape).padding(8.dp)) {
                Icon(icono, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(titulo, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(valor, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

fun GenerarPDFCierreCarta(
    context: Context, 
    state: DashboardVendedorUiState, 
    ventas: List<VentaReporteItem>, 
    esDemo: Boolean
): File {
    val pdfDocument = PdfDocument()
    val pageWidth = 612; val pageHeight = 792 
    val nf = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    
    // --- PINCELES ---
    val pHeaderBg = Paint().apply { color = android.graphics.Color.rgb(227, 6, 19); style = Paint.Style.FILL } // Rojo Delisa
    val pDelisaRed = Paint().apply { color = android.graphics.Color.rgb(227, 6, 19); style = Paint.Style.FILL; isAntiAlias = true }
    val pAreaFill = Paint().apply { color = android.graphics.Color.argb(40, 227, 6, 19); style = Paint.Style.FILL; isAntiAlias = true }
    val pZebra = Paint().apply { color = android.graphics.Color.rgb(242, 242, 242); style = Paint.Style.FILL }
    val pTitle = Paint().apply { textSize = 18f; color = android.graphics.Color.WHITE; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pSub = Paint().apply { textSize = 9f; color = android.graphics.Color.LTGRAY; typeface = Typeface.SANS_SERIF; isAntiAlias = true }
    val pText = Paint().apply { textSize = 10.5f; color = android.graphics.Color.BLACK; typeface = Typeface.SANS_SERIF; isAntiAlias = true }
    val pBold = Paint().apply { textSize = 11f; color = android.graphics.Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pRedText = Paint().apply { textSize = 11f; color = android.graphics.Color.rgb(227, 6, 19); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pLine = Paint().apply { strokeWidth = 0.5f; color = android.graphics.Color.LTGRAY; style = Paint.Style.STROKE; isAntiAlias = true }

    // --- CÁLCULO DE KPIs ---
    val totalVentaNeta = if(esDemo) 11845.50 else state.ventaDia
    val clientesProgramados = ventas.size
    val clientesConVenta = ventas.count { it.estado == "VENTA" }
    
    // Ahora "Sin Venta" es el total de fallos (Rojos + Amarillos)
    val clientesSinVenta = clientesProgramados - clientesConVenta
    
    val clientesNoContactados = ventas.count { it.estado != "VENTA" && it.estado != "SIN VENTA" }
    val efectividad = if (clientesProgramados > 0) (clientesConVenta.toFloat() / clientesProgramados) * 100 else 0f
    val ticketPromedio = if (clientesConVenta > 0) totalVentaNeta / clientesConVenta else 0.0
    val productividad = if (clientesProgramados > 0) totalVentaNeta / clientesProgramados else 0.0
    val totalPiezas = ventas.sumOf { it.piezas }

    // --- CÁLCULO DE PÁGINAS ---
    val itemsFirstPage = 14
    val itemsOtherPages = 32
    var totalPages = if (ventas.size <= itemsFirstPage) 1 else 1 + Math.ceil((ventas.size - itemsFirstPage).toDouble() / itemsOtherPages).toInt()
    
    var currentPageNumber = 1
    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas

    fun drawHeader(canv: Canvas, pNum: Int) {
        if (pNum == 1) {
            // --- ENCABEZADO TESLA RED HUD (VIBRANTE Y MODERNO) ---
            val headerH = 100f
            val headerGradient = LinearGradient(
                0f, 0f, 0f, headerH,
                android.graphics.Color.rgb(227, 6, 19), // Rojo Delisa Superior
                android.graphics.Color.rgb(160, 0, 10),  // Rojo Profundo Inferior
                Shader.TileMode.CLAMP
            )
            val pHeaderHUD = Paint().apply { shader = headerGradient; style = Paint.Style.FILL; isAntiAlias = true }
            canv.drawRect(0f, 0f, pageWidth.toFloat(), headerH, pHeaderHUD)
            
            // Línea de definición inferior (Contraste técnico)
            val pAccentLine = Paint().apply { color = android.graphics.Color.rgb(26, 26, 26); strokeWidth = 1.2f; style = Paint.Style.STROKE }
            canv.drawLine(0f, headerH, pageWidth.toFloat(), headerH, pAccentLine)

            val logo = context.getDrawable(R.drawable.logo)
            logo?.let { it.setBounds(40, 15, 140, 75); it.draw(canv) }
            
            // Título Principal (White Premium)
            val pMainTitle = Paint(pTitle).apply { 
                textSize = 22f 
                letterSpacing = 0.05f 
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            canv.drawText("REPORTE DE DESEMPEÑO", 160f, 35f, pMainTitle)
            
            // Metadatos en Blanco Bold (Alineación Técnica por niveles)
            pBold.color = android.graphics.Color.WHITE
            pBold.textSize = 9f
            
            // Nivel 1: Vendedor e Hoja
            pBold.textAlign = Paint.Align.LEFT
            canv.drawText("VENDEDOR: ${state.nombreVendedor.uppercase()}", 160f, 65f, pBold)
            
            pBold.textAlign = Paint.Align.RIGHT
            canv.drawText("HOJA $pNum DE $totalPages", 572f, 35f, pBold)
            
            // Nivel 2: Ruta y Fecha (Sincronizados horizontalmente)
            val df = SimpleDateFormat("EEEE dd 'DE' MMMM 'DEL' yyyy", Locale("es", "MX"))
            val fechaFormateada = df.format(Date()).uppercase()
            
            pBold.textAlign = Paint.Align.LEFT
            canv.drawText("RUTA: ${state.rutaNombre.uppercase()}", 160f, 85f, pBold)
            
            pBold.textAlign = Paint.Align.RIGHT
            canv.drawText(fechaFormateada, 572f, 85f, pBold)
            
            // Restaurar pinceles
            pBold.textAlign = Paint.Align.LEFT
            pBold.color = android.graphics.Color.BLACK
            pBold.textSize = 9f
        } else {
            // Cabecera minimalista para hojas 2, 3...
            pSub.color = android.graphics.Color.DKGRAY; pSub.textAlign = Paint.Align.RIGHT
            canv.drawText("HOJA $pNum DE $totalPages", 572f, 30f, pSub); pSub.textAlign = Paint.Align.LEFT
        }
    }

    drawHeader(canvas, currentPageNumber)
    var y = 130f

    // --- RESUMEN OPERATIVO (KPIs) ---
    pBold.textAlign = Paint.Align.CENTER
    canvas.drawText("RESUMEN OPERATIVO DE RUTA", pageWidth / 2f, y, pBold); y += 20f
    pBold.textAlign = Paint.Align.LEFT
    
    val kpiW = 135f; val kpiH = 40f; val kpiGap = 8f
    val totalKpiWidth = (kpiW * 4) + (kpiGap * 3)
    val startX = (pageWidth - totalKpiWidth) / 2f

    fun drawKpi(l: String, v: String, x: Float, c: Int = android.graphics.Color.rgb(245, 245, 245)) {
        canvas.drawRoundRect(x, y, x + kpiW, y + kpiH, 6f, 6f, Paint().apply { color = c; style = Paint.Style.FILL })
        val centerX = x + kpiW / 2f
        pSub.textAlign = Paint.Align.CENTER; pSub.color = android.graphics.Color.GRAY
        canvas.drawText(l, centerX, y + 15f, pSub)
        pBold.textAlign = Paint.Align.CENTER; pBold.textSize = 11f
        canvas.drawText(v, centerX, y + 32f, pBold)
        pSub.textAlign = Paint.Align.LEFT; pBold.textAlign = Paint.Align.LEFT; pBold.textSize = 9f
    }
    
    // Determinamos el color de Efectividad basado en estándares industriales (Bimbo/Barcel)
    // Intensificados para mayor visibilidad en el reporte impreso
    val colorEfectividad = when {
        efectividad >= 85f -> android.graphics.Color.rgb(200, 230, 201) // Verde (Más presente)
        efectividad >= 75f -> android.graphics.Color.rgb(255, 245, 157) // Amarillo (Más presente)
        else -> android.graphics.Color.rgb(255, 205, 210) // Rojo Alerta (Más presente)
    }

    drawKpi("Programados", "$clientesProgramados", startX)
    drawKpi("Con Venta", "$clientesConVenta", startX + kpiW + kpiGap)
    drawKpi("Sin Venta", "$clientesSinVenta", startX + 2 * (kpiW + kpiGap))
    drawKpi("Efectividad", "${"%.1f".format(efectividad)}%", startX + 3 * (kpiW + kpiGap), colorEfectividad)
    y += 55f
    drawKpi("Venta Neta", nf.format(totalVentaNeta), startX)
    drawKpi("Ticket Prom.", nf.format(ticketPromedio), startX + kpiW + kpiGap)
    drawKpi("Productividad", nf.format(productividad), startX + 2 * (kpiW + kpiGap))
    drawKpi("Piezas", "$totalPiezas pzas", startX + 3 * (kpiW + kpiGap))
    y += 65f

    // --- GRÁFICA DE AUDITORÍA CRONOLÓGICA (ULTRA-WIDE EDGE-TO-EDGE) ---
    pBold.textAlign = Paint.Align.CENTER
    canvas.drawText("ANÁLISIS DE RITMO Y PRODUCTIVIDAD", pageWidth / 2f, y, pBold); y += 25f
    pBold.textAlign = Paint.Align.LEFT
    
    val chartW = 612f; val chartH = 75f
    val chartX = 0f
    
    // Rango dinámico basado en las ventas reales
    val firstVentaMin = (ventas.minOfOrNull { it.hora * 60 + it.minutos } ?: (7 * 60)) - 30 // 30 min de margen antes
    val lastVentaMin = (ventas.maxOfOrNull { it.hora * 60 + it.minutos } ?: (18 * 60)) + 30 // 30 min de margen después
    val startMin = firstVentaMin.coerceAtMost(7 * 60) // Al menos desde las 7 si no hay ventas
    val endMin = lastVentaMin.coerceAtLeast(18 * 60) // Al menos hasta las 18 si no hay ventas
    
    val totalDuration = endMin - startMin
    val maxAmt = (ventas.maxOfOrNull { it.monto } ?: 1.0).coerceAtLeast(1.0)
    
    // 1. Dibujar Indicadores de Inactividad (Líneas de trazo discontinuo)
    val pInactivityLine = Paint().apply { 
        color = android.graphics.Color.rgb(220, 220, 220)
        strokeWidth = 1f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
    }
    val pInactivityText = Paint().apply { 
        textSize = 6f
        color = android.graphics.Color.GRAY
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        textAlign = Paint.Align.CENTER
    }

    for (i in 0 until ventas.size - 1) {
        val t1 = ventas[i].hora * 60 + ventas[i].minutos
        val t2 = ventas[i+1].hora * 60 + ventas[i+1].minutos
        val diff = t2 - t1
        if (diff > 60) {
            val x1 = chartX + ((t1 - startMin).toFloat() / totalDuration * chartW).coerceIn(0f, chartW)
            val x2 = chartX + ((t2 - startMin).toFloat() / totalDuration * chartW).coerceIn(0f, chartW)
            
            // Dibujamos una zona sombreada muy sutil con líneas laterales discontinuas
            canvas.drawRect(x1, y, x2, y + chartH, Paint().apply { color = android.graphics.Color.argb(15, 0, 0, 0); style = Paint.Style.FILL })
            canvas.drawLine(x1, y, x1, y + chartH, pInactivityLine)
            canvas.drawLine(x2, y, x2, y + chartH, pInactivityLine)
            
            // Calculamos texto de duración
            val h = diff / 60
            val m = diff % 60
            val durStr = "${h}h ${m}m"
            
            // Texto posicionado en la parte superior en 2 niveles
            val pInactivityBold = Paint(pInactivityText).apply { 
                textSize = 7f 
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) 
            }
            
            canvas.drawText("LAPSO DE INACTIVIDAD", (x1 + x2) / 2f, y + 10f, pInactivityText)
            canvas.drawText(durStr, (x1 + x2) / 2f, y + 18f, pInactivityBold)
        }
    }

    // 2. Líneas guía horizontales
    val pGrid = Paint().apply { color = android.graphics.Color.rgb(245, 245, 245); strokeWidth = 0.5f; style = Paint.Style.STROKE }
    for (i in 0..4) {
        val gridY = y + chartH - (i * chartH / 4)
        canvas.drawLine(chartX, gridY, chartX + chartW, gridY, pGrid)
    }

    // 3. Generar Puntos basados en TIEMPO REAL
    val points = mutableListOf<android.graphics.PointF>()
    ventas.forEach { v ->
        val currentMin = v.hora * 60 + v.minutos
        val px = chartX + ((currentMin - startMin).toFloat() / totalDuration * chartW).coerceIn(0f, chartW)
        val py = y + chartH - (v.monto.toFloat() / maxAmt.toFloat() * chartH)
        points.add(android.graphics.PointF(px, py))
    }

    if (points.isNotEmpty()) {
        // 4. Dibujar Área Spline
        val areaPath = Path().apply { moveTo(points[0].x, y + chartH) }
        for (i in 0 until points.size - 1) {
            val p1 = points[i]; val p2 = points[i+1]
            areaPath.cubicTo(p1.x + (p2.x - p1.x) * 0.35f, p1.y, p2.x - (p2.x - p1.x) * 0.35f, p2.y, p2.x, p2.y)
        }
        areaPath.lineTo(points.last().x, y + chartH); areaPath.close()
        val grad = LinearGradient(0f, y, 0f, y + chartH, android.graphics.Color.argb(90, 227, 6, 19), android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawPath(areaPath, Paint().apply { shader = grad; style = Paint.Style.FILL; isAntiAlias = true })

        // 5. Línea de Tendencia
        val linePath = Path().apply { moveTo(points[0].x, points[0].y) }
        for (i in 0 until points.size - 1) {
            val p1 = points[i]; val p2 = points[i+1]
            linePath.cubicTo(p1.x + (p2.x - p1.x) * 0.35f, p1.y, p2.x - (p2.x - p1.x) * 0.35f, p2.y, p2.x, p2.y)
        }
        canvas.drawPath(linePath, Paint(pDelisaRed).apply { style = Paint.Style.STROKE; strokeWidth = 1.8f; isAntiAlias = true; setShadowLayer(3f, 0f, 1f, android.graphics.Color.argb(50, 227, 6, 19)) })

        // 6. Marcadores y Peak
        val peakAmt = ventas.maxOfOrNull { it.monto } ?: -1.0
        points.forEachIndexed { i, p ->
            val v = ventas[i]
            val isPeak = v.monto == peakAmt && v.monto > 0
            canvas.drawCircle(p.x, p.y, if(isPeak) 3f else 1.5f, Paint().apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL })
            canvas.drawCircle(p.x, p.y, if(isPeak) 3f else 1.5f, Paint().apply { color = android.graphics.Color.rgb(227, 6, 19); style = Paint.Style.STROKE; strokeWidth = 0.8f })
            if(isPeak) {
                val pPeak = Paint().apply { textSize = 7f; color = android.graphics.Color.rgb(227, 6, 19); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER }
                canvas.drawText(nf.format(v.monto), p.x, p.y - 8f, pPeak)
            }
        }
    }

    // 7. Eje del tiempo dinámico
    if (ventas.isNotEmpty()) {
        pSub.textAlign = Paint.Align.CENTER
        // Hora de inicio exacta
        val startXPos = chartX + ((ventas.first().hora * 60 + ventas.first().minutos - startMin).toFloat() / totalDuration * chartW).coerceIn(0f, chartW)
        canvas.drawText(String.format(Locale.US, "%02d:%02d", ventas.first().hora, ventas.first().minutos), startXPos, y + chartH + 12f, pSub.apply { color = android.graphics.Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) })
        
        // Marcas intermedias cada 2 horas (solo si no chocan con inicio/fin)
        val firstH = (startMin / 60) + 1
        val lastH = (endMin / 60) - 1
        for (h in firstH..lastH step 2) {
            val px = chartX + ((h * 60 - startMin).toFloat() / totalDuration * chartW).coerceIn(0f, chartW)
            // Evitamos encimar con la primera o última hora
            if (Math.abs(px - startXPos) > 40f) {
                canvas.drawText("${h}:00", px, y + chartH + 12f, pSub.apply { color = android.graphics.Color.GRAY; typeface = Typeface.SANS_SERIF })
            }
        }

        // Hora de fin exacta
        val endXPos = chartX + ((ventas.last().hora * 60 + ventas.last().minutos - startMin).toFloat() / totalDuration * chartW).coerceIn(0f, chartW)
        if (Math.abs(endXPos - startXPos) > 40f) {
            canvas.drawText(String.format(Locale.US, "%02d:%02d", ventas.last().hora, ventas.last().minutos), endXPos, y + chartH + 12f, pSub.apply { color = android.graphics.Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) })
        }
    } else {
        // Si no hay ventas, mostramos un eje genérico
        pSub.textAlign = Paint.Align.CENTER
        canvas.drawText("07:00", chartX, y + chartH + 12f, pSub)
        canvas.drawText("18:00", chartX + chartW, y + chartH + 12f, pSub)
    }

    pSub.textAlign = Paint.Align.LEFT
    y += chartH + 45f

    // --- TABLA DE DESEMPEÑO ---
    fun drawTableHeader(canv: android.graphics.Canvas, curY: Float) {
        canv.drawRect(40f, curY, 572f, curY + 1.5f, pDelisaRed)
        val hY = curY + 14f
        canv.drawText("#", 45f, hY, pBold)
        canv.drawText("HORA", 65f, hY, pBold)
        canv.drawText("CLIENTE / ESTABLECIMIENTO", 115f, hY, pBold)
        canv.drawText("ESTADO", 335f, hY, pBold)
        canv.drawText("PZAS", 455f, hY, pBold)
        canv.drawText("TOTAL", 510f, hY, pBold)
    }
    drawTableHeader(canvas, y); y += 22f

    ventas.forEachIndexed { index, v ->
        if (y > 730f) {
            pdfDocument.finishPage(page)
            currentPageNumber++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            page = pdfDocument.startPage(pageInfo); canvas = page.canvas
            drawHeader(canvas, currentPageNumber); y = 62f
            drawTableHeader(canvas, y); y += 22f
        }

        // Colores por Estado (Nueva Lógica Solicitada)
        val (bgC, txtC) = when(v.estado) {
            "VENTA" -> android.graphics.Color.rgb(232, 245, 233) to android.graphics.Color.rgb(46, 125, 50) // Verde
            "SIN VENTA" -> android.graphics.Color.rgb(255, 235, 238) to android.graphics.Color.rgb(198, 40, 40) // Rojo (No quiso comprar)
            else -> android.graphics.Color.rgb(255, 253, 231) to android.graphics.Color.rgb(245, 127, 23) // Amarillo (Cerrado, No localizado, etc.)
        }

        // Pintamos el fondo de la fila con el color del estado
        canvas.drawRect(40f, y, 572f, y + 20f, Paint().apply { color = bgC; style = Paint.Style.FILL })
        
        // Aplicamos el color de texto del estado a todas las columnas para consistencia total
        val pStatusText = Paint(pText).apply { color = txtC }
        val pStatusBold = Paint(pBold).apply { color = txtC; textSize = 10f }

        canvas.drawText("${index + 1}", 45f, y + 15f, pStatusText)
        canvas.drawText(String.format(Locale.US, "%02d:%02d", v.hora, v.minutos), 65f, y + 15f, pStatusText)
        canvas.drawText(v.cliente.take(40), 115f, y + 15f, pStatusText)
        canvas.drawText(v.estado, 335f, y + 15f, pStatusBold)
        canvas.drawText("${v.piezas}", 455f, y + 15f, pStatusText)
        canvas.drawText(nf.format(v.monto), 510f, y + 15f, if(v.monto > 0) pStatusBold else pStatusText)
        y += 20f
    }

    // --- PIE DE PÁGINA Y QR ---
    if (y > 700f) { pdfDocument.finishPage(page); currentPageNumber++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; y = 40f }
    y = 710f
    canvas.drawRect(40f, y, 572f, y + 1.2f, pDelisaRed); y += 15f
    try {
        val qrContent = "DELISA_AUDIT|${state.nombreVendedor}|EFECT:${totalVentaNeta}|EFEC:${efectividad}%"
        val bitMatrix = QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 200, 200)
        val qrBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.RGB_565)
        for (i in 0 until 200) for (j in 0 until 200) qrBitmap.setPixel(i, j, if (bitMatrix.get(i, j)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        canvas.drawBitmap(qrBitmap, null, RectF(40f, y, 90f, y + 50f), null)
    } catch (e: Exception) { canvas.drawRect(40f, y, 80f, y + 40f, pDelisaRed) }
    canvas.drawText("REPORTE OPERATIVO DELISA / SEGURIDAD EN RUTA", 306f, 780f, pSub.apply { textAlign = Paint.Align.CENTER })

    pdfDocument.finishPage(page)
    val name = "REPORTE_${state.rutaNombre.replace(" ", "_").uppercase()}_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.pdf"
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), name)
    pdfDocument.writeTo(FileOutputStream(file)); pdfDocument.close()
    return file
}

fun abrirPdfCierre(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Abrir Reporte de Cierre"))
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir el PDF", Toast.LENGTH_SHORT).show()
    }
}
