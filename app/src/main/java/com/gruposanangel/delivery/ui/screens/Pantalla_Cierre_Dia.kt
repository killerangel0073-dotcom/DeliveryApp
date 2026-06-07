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
import java.io.File
import java.io.FileOutputStream
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class VentaReporteItem(val cliente: String, val piezas: Int, val monto: Double, val hora: Int)

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

            Spacer(Modifier.height(32.dp))

            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("EXPORTACIÓN EJECUTIVA", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp)
                
                Button(
                    onClick = {
                        scope.launch {
                            val ventasItems = withContext(Dispatchers.IO) {
                                uiState.ventasHoy.map { v ->
                                    val piezas = ventaRepo.obtenerDetallesDeVenta(v.id).sumOf { it.cantidad }
                                    val cal = Calendar.getInstance().apply { timeInMillis = v.fecha }
                                    VentaReporteItem(v.clienteNombre, piezas, v.total, cal.get(Calendar.HOUR_OF_DAY))
                                }
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
                    Text("GENERAR REPORTE CARTA", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }

                OutlinedButton(
                    onClick = {
                        val demoData = List(25) { i ->
                            VentaReporteItem("Establecimiento Demo #$i", (10..100).random(), (300..2500).random().toDouble(), (8..18).random())
                        }
                        val file = GenerarPDFCierreCarta(context, uiState, demoData, true)
                        abrirPdfCierre(context, file)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF1A1A1A))
                ) {
                    Icon(Icons.Rounded.AutoMode, null, tint = Color(0xFF1A1A1A))
                    Spacer(Modifier.width(12.dp))
                    Text("VISTA PREVIA DISEÑO (MULTICARTA)", color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(40.dp))
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
    val pText = Paint().apply { textSize = 9f; color = android.graphics.Color.BLACK; typeface = Typeface.SANS_SERIF; isAntiAlias = true }
    val pBold = Paint().apply { textSize = 9f; color = android.graphics.Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pRedText = Paint().apply { textSize = 9f; color = android.graphics.Color.rgb(227, 6, 19); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pLine = Paint().apply { strokeWidth = 0.5f; color = android.graphics.Color.LTGRAY; style = Paint.Style.STROKE; isAntiAlias = true }

    // --- CÁLCULO DE PÁGINAS ---
    val itemsFirstPage = 16
    val itemsOtherPages = 29
    var totalPages = if (ventas.size <= itemsFirstPage) 1 else 1 + Math.ceil((ventas.size - itemsFirstPage).toDouble() / itemsOtherPages).toInt()
    
    // Verificamos si el pie de página necesita una hoja extra
    var lastPageItems = if (ventas.size <= itemsFirstPage) ventas.size else (ventas.size - itemsFirstPage) % itemsOtherPages
    if (lastPageItems == 0 && ventas.size > itemsFirstPage) lastPageItems = itemsOtherPages
    val lastYEstimate = if (totalPages == 1) 350 + lastPageItems * 22 else 62 + lastPageItems * 22
    if (lastYEstimate > 680) totalPages++

    var currentPageNumber = 1
    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas

    // Función para dibujar encabezado de cada hoja
    fun drawHeader(canv: Canvas, pNum: Int) {
        if (pNum == 1) {
            // --- ENCABEZADO PREMIUM CON DEGRADADO (Estilo HUD) ---
            val headerH = 100f
            val headerGradient = LinearGradient(
                0f, 0f, 0f, headerH,
                android.graphics.Color.rgb(155, 0, 0), // Rojo profundo superior
                android.graphics.Color.rgb(227, 6, 19), // Rojo Delisa base
                Shader.TileMode.CLAMP
            )
            val pHeaderHUD = Paint().apply { shader = headerGradient; style = Paint.Style.FILL; isAntiAlias = true }
            canv.drawRect(0f, 0f, pageWidth.toFloat(), headerH, pHeaderHUD)
            
            // Línea de acento técnica inferior para definición
            val pAccentLine = Paint().apply { color = android.graphics.Color.rgb(26, 26, 26); strokeWidth = 1f; style = Paint.Style.STROKE }
            canv.drawLine(0f, headerH, pageWidth.toFloat(), headerH, pAccentLine)

            val logo = context.getDrawable(R.drawable.logo)
            logo?.let { it.setBounds(40, 15, 140, 75); it.draw(canv) }
            
            canv.drawText("REPORTE OPERATIVO", 160f, 35f, pTitle)
            
            pBold.color = android.graphics.Color.WHITE
            pBold.textSize = 9f
            
            // Metadatos alineados con precisión
            pBold.textAlign = Paint.Align.RIGHT
            canv.drawText("HOJA $pNum DE $totalPages", 572f, 35f, pBold)
            
            pBold.textAlign = Paint.Align.LEFT
            canv.drawText("VENDEDOR: ${state.nombreVendedor.uppercase()}", 160f, 65f, pBold)
            
            // Línea base del HUD (Ruta y Fecha)
            canv.drawText("RUTA: ${state.rutaNombre.uppercase()}", 160f, 80f, pBold)
            
            val dateFormat = SimpleDateFormat("EEEE dd 'DE' MMMM 'DEL' yyyy", Locale("es", "MX"))
            val fechaFormateada = dateFormat.format(Date()).uppercase()
            pBold.textAlign = Paint.Align.RIGHT
            canv.drawText(fechaFormateada, 572f, 80f, pBold)
            
            // Restaurar pincel para el cuerpo
            pBold.textAlign = Paint.Align.LEFT
            pBold.color = android.graphics.Color.BLACK
        } else {
            // Solo numeración minimalista para hojas subsiguientes
            pSub.color = android.graphics.Color.DKGRAY
            pSub.textAlign = Paint.Align.RIGHT
            canv.drawText("HOJA $pNum DE $totalPages", 572f, 30f, pSub)
        }
        
        pSub.textAlign = Paint.Align.LEFT
        pSub.color = android.graphics.Color.LTGRAY
    }

    drawHeader(canvas, currentPageNumber)

    var y = 130f
    
    // Tarjetas KPI (Solo en Hoja 1)
    val vHoy = if(esDemo) 11845.50 else state.ventaDia
    val cHoy = if(esDemo) 25 else state.clientesDia
    val tProm = if (cHoy > 0) vHoy / cHoy else 0.0

    val cardW = 175f; val cardH = 45f; val gap = 12f
    fun drawKpiCard(label: String, value: String, x: Float) {
        canvas.drawRoundRect(x, y, x + cardW, y + cardH, 8f, 8f, pZebra)
        val centerX = x + (cardW / 2)
        pSub.textAlign = Paint.Align.CENTER; pSub.color = android.graphics.Color.GRAY
        canvas.drawText(label, centerX, y + 15f, pSub)
        pBold.textAlign = Paint.Align.CENTER; pBold.textSize = 12f
        canvas.drawText(value, centerX, y + 35f, pBold)
        pSub.textAlign = Paint.Align.LEFT; pBold.textAlign = Paint.Align.LEFT; pBold.textSize = 9f
    }

    drawKpiCard("VENTA NETA", nf.format(vHoy), 40f)
    drawKpiCard("CLIENTES", "$cHoy atendidos", 40f + cardW + gap)
    drawKpiCard("TICKET PROM.", nf.format(tProm), 40f + 2*(cardW + gap))
    y += 70f

    // --- NUEVA GRÁFICA SPLINE PREMIUM (Tesla/Apple Style) ---
    canvas.drawText("RENDIMIENTO HORARIO (VENTAS $)", 40f, y, pBold); y += 25f
    val hourlyAmount = DoubleArray(24) { 0.0 }
    ventas.forEach { 
        if (it.hora in 0..23) {
            hourlyAmount[it.hora] += it.monto
        }
    }
    
    val startH = 6; val endH = 20 // Rango operativo ampliado
    val chartW = 532f; val chartH = 80f
    val maxAmount = (hourlyAmount.slice(startH..endH).maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    
    // 1. Líneas guía horizontales (Gris muy tenue)
    val pGrid = Paint().apply { color = android.graphics.Color.rgb(242, 242, 242); strokeWidth = 0.5f; style = Paint.Style.STROKE }
    for (i in 0..4) {
        val gridY = y + chartH - (i * chartH / 4)
        canvas.drawLine(40f, gridY, 40f + chartW, gridY, pGrid)
    }

    val points = mutableListOf<android.graphics.PointF>()
    val stepX = chartW / (endH - startH)
    for (h in startH..endH) {
        val px = 40f + (h - startH) * stepX
        val py = y + chartH - (hourlyAmount[h].toFloat() / maxAmount.toFloat() * chartH)
        points.add(android.graphics.PointF(px, py))
    }

    // 2. Dibujar Área con Degradado (Basado en Dinero $)
    val areaPath = Path()
    areaPath.moveTo(points[0].x, y + chartH)
    areaPath.lineTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val p1 = points[i]; val p2 = points[i + 1]
        areaPath.cubicTo(
            p1.x + (p2.x - p1.x) * 0.35f, p1.y,
            p2.x - (p2.x - p1.x) * 0.35f, p2.y,
            p2.x, p2.y
        )
    }
    areaPath.lineTo(points.last().x, y + chartH)
    areaPath.close()
    
    val gradient = LinearGradient(0f, y, 0f, y + chartH, android.graphics.Color.argb(95, 227, 6, 19), android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP)
    val pArea = Paint().apply { shader = gradient; style = Paint.Style.FILL; isAntiAlias = true }
    canvas.drawPath(areaPath, pArea)

    // 3. Línea Principal con Efecto Glow
    val linePath = Path()
    linePath.moveTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val p1 = points[i]; val p2 = points[i + 1]
        linePath.cubicTo(
            p1.x + (p2.x - p1.x) * 0.35f, p1.y,
            p2.x - (p2.x - p1.x) * 0.35f, p2.y,
            p2.x, p2.y
        )
    }
    val pLineChart = Paint().apply { 
        color = android.graphics.Color.rgb(227, 6, 19)
        strokeWidth = 2.2f; style = Paint.Style.STROKE; isAntiAlias = true
        setShadowLayer(4f, 0f, 1.5f, android.graphics.Color.argb(80, 227, 6, 19))
    }
    canvas.drawPath(linePath, pLineChart)

    // 4. Marcadores y Resalte del Pico de Venta $
    val pMarkerWhite = Paint().apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
    val pMarkerRed = Paint().apply { color = android.graphics.Color.rgb(227, 6, 19); style = Paint.Style.STROKE; strokeWidth = 1.2f; isAntiAlias = true }
    val pMaxLabel = Paint().apply { textSize = 7.5f; color = android.graphics.Color.rgb(227, 6, 19); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER }

    val peakAmount = hourlyAmount.slice(startH..endH).maxOrNull() ?: -1.0

    points.forEachIndexed { i, p ->
        val hour = startH + i
        val amount = hourlyAmount[hour]
        val isMax = amount == peakAmount && amount > 0
        
        val radius = if (isMax) 3.5f else 2f
        canvas.drawCircle(p.x, p.y, radius, pMarkerWhite)
        canvas.drawCircle(p.x, p.y, radius, pMarkerRed)

        if (isMax) {
            canvas.drawText(nf.format(amount), p.x, p.y - 12f, pMaxLabel)
        }

        if (hour % 2 == 0 || hour == startH || hour == endH) {
            pSub.textAlign = Paint.Align.CENTER
            canvas.drawText("${hour}:00", p.x, y + chartH + 15f, pSub)
            pSub.textAlign = Paint.Align.LEFT
        }
    }
    y += chartH + 50f

    // --- TABLA DE VENTAS ---
    fun drawTableHeader(canv: Canvas, currentY: Float) {
        canv.drawRect(40f, currentY, 572f, currentY + 1.5f, pDelisaRed)
        val headerY = currentY + 14f
        canv.drawText("#", 45f, headerY, pBold)
        canv.drawText("HORA", 75f, headerY, pBold)
        canv.drawText("CLIENTE / ESTABLECIMIENTO", 125f, headerY, pBold)
        canv.drawText("PZAS", 420f, headerY, pBold)
        canv.drawText("TOTAL VENTA", 500f, headerY, pBold)
    }

    drawTableHeader(canvas, y); y += 22f

    val rowH = 22f
    ventas.forEachIndexed { index, v ->
        if (y > 720f) {
            pdfDocument.finishPage(page)
            currentPageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            
            drawHeader(canvas, currentPageNumber)
            y = 62f // Empezamos más arriba en hojas sin encabezado rojo
            drawTableHeader(canvas, y); y += 22f
        }

        if (index % 2 != 0) canvas.drawRect(40f, y, 572f, y + rowH, pZebra)
        canvas.drawText("${index + 1}", 45f, y + 15f, pText)
        canvas.drawText(String.format(Locale.US, "%02d:00", v.hora), 75f, y + 15f, pText)
        canvas.drawText(v.cliente.take(45), 125f, y + 15f, pText)
        canvas.drawText("${v.piezas}", 420f, y + 15f, pText)
        canvas.drawText(nf.format(v.monto), 500f, y + 15f, pRedText)
        y += rowH
    }

    // --- PIE DE PÁGINA (Solo en la última hoja) ---
    if (y > 680f) {
        pdfDocument.finishPage(page)
        currentPageNumber++
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
        page = pdfDocument.startPage(pageInfo)
        canvas = page.canvas
        y = 40f
    }
    
    y = 710f
    canvas.drawRect(40f, y, 572f, y + 1.5f, pDelisaRed); y += 15f

    // --- QR REAL INTEGRADO ---
    try {
        val qrContent = "DELISA_AUDIT|${state.nombreVendedor}|${vHoy}|${SimpleDateFormat("yyyyMMdd_HHmm").format(Date())}"
        val qrWriter = QRCodeWriter()
        val bitMatrix = qrWriter.encode(qrContent, BarcodeFormat.QR_CODE, 200, 200)
        val qrBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.RGB_565)
        for (i in 0 until 200) {
            for (j in 0 until 200) {
                qrBitmap.setPixel(i, j, if (bitMatrix.get(i, j)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        canvas.drawBitmap(qrBitmap, null, RectF(40f, y, 90f, y + 50f), null)
    } catch (e: Exception) {
        // Fallback sutil si falla el QR
        canvas.drawRect(40f, y, 80f, y + 40f, pHeaderBg)
    }

    canvas.drawText("REPORTE OPERATIVO DELISA / SEGURIDAD EN RUTA", 306f, 780f, pSub.apply { textAlign = Paint.Align.CENTER })

    pdfDocument.finishPage(page)
    
    // Nombre de archivo profesional: REPORTE_RUTA_FECHA.pdf
    val nombreRutaLimpio = state.rutaNombre.replace(" ", "_").uppercase()
    val fechaArchivo = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    val fileName = "REPORTE_${nombreRutaLimpio}_${fechaArchivo}.pdf"
    
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
    pdfDocument.writeTo(FileOutputStream(file))
    pdfDocument.close()
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
