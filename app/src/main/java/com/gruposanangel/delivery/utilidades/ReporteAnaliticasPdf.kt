package com.gruposanangel.delivery.utilidades

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.appcompat.content.res.AppCompatResources
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.ui.screens.AnalyticsUiState
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object ReporteAnaliticasPdf {

    fun generarPDF(
        context: Context,
        uiState: AnalyticsUiState,
        fechaInicio: Date,
        fechaFin: Date,
        perfilNombre: String = "CONSOLIDADO"
    ): File {
        val pdfDocument = PdfDocument()
        val pageWidth = 612 // Letter size width
        val pageHeight = 792 // Letter size height
        val margin = 40f
        
        val nf = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("es", "MX"))
        val dfConHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX"))
        val fechaReporteStr = dfConHora.format(Date())
        
        // --- PINCELES ---
        val pDelisaRed = Color.rgb(227, 6, 19)
        val pDelisaGreen = Color.rgb(46, 125, 50)
        val pZebra = Paint().apply { color = Color.rgb(248, 249, 250); style = Paint.Style.FILL }
        val pTitle = Paint().apply { textSize = 22f; color = Color.WHITE; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
        val pSubTitle = Paint().apply { textSize = 9f; color = Color.rgb(220, 220, 220); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); isAntiAlias = true }
        val pSectionHeader = Paint().apply { textSize = 12f; color = Color.rgb(50, 50, 50); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
        val pBold = Paint().apply { textSize = 10f; color = Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
        val pText = Paint().apply { textSize = 10f; color = Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); isAntiAlias = true }
        val pLine = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f; style = Paint.Style.STROKE; isAntiAlias = true }

        var currentPageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // --- FUNCIONES DE DIBUJO ---
        
        fun drawHeader(canv: Canvas) {
            val headerH = 100f
            val gradient = LinearGradient(0f, 0f, 0f, headerH, Color.rgb(227, 6, 19), Color.rgb(150, 0, 10), Shader.TileMode.CLAMP)
            canv.drawRect(0f, 0f, pageWidth.toFloat(), headerH, Paint().apply { shader = gradient; isAntiAlias = true })
            canv.drawLine(0f, headerH, pageWidth.toFloat(), headerH, Paint().apply { color = Color.BLACK; strokeWidth = 1.2f })
            
            canv.drawText("REPORTE DE DESEMPEÑO", margin, 35f, pTitle)
            canv.drawText("LÍNEA: ${perfilNombre.uppercase()} | PERIODO: ${sdf.format(fechaInicio)} - ${sdf.format(fechaFin)}", margin, 55f, pSubTitle)
            canv.drawText("SISTEMA DE GESTIÓN DELISA BOTANAS | GENERADO: $fechaReporteStr", margin, 75f, pSubTitle)
            
            // Logo
            try {
                val logo = AppCompatResources.getDrawable(context, R.drawable.logo)
                logo?.let {
                    it.setBounds(pageWidth - 140, 20, pageWidth - 40, 80)
                    it.draw(canv)
                }
            } catch (e: Exception) {}
        }

        fun drawKPICard(canv: Canvas, y: Float, label: String, value: String, x: Float, w: Float, colorInt: Int = Color.BLACK) {
            val rect = RectF(x, y, x + w, y + 50f)
            canv.drawRoundRect(rect, 12f, 12f, Paint().apply { color = Color.rgb(245, 245, 245); style = Paint.Style.FILL })
            canv.drawRoundRect(rect, 12f, 12f, Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f })
            
            val pLabel = Paint().apply { textSize = 8f; color = Color.GRAY; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            val pVal = Paint().apply { textSize = 14f; color = colorInt; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            
            canv.drawText(label.uppercase(), x + w/2, y + 18f, pLabel)
            canv.drawText(value, x + w/2, y + 40f, pVal)
        }

        fun drawTableHeader(canv: Canvas, curY: Float, color: Int, columns: List<Pair<String, Float>>) {
            canv.drawRect(margin, curY, pageWidth - margin, curY + 22f, Paint().apply { this.color = color; style = Paint.Style.FILL; isAntiAlias = true })
            pBold.color = Color.WHITE
            columns.forEach { (text, x) ->
                canv.drawText(text, x, curY + 15f, pBold)
            }
            pBold.color = Color.BLACK
        }

        // --- INICIO DEL CONTENIDO ---
        drawHeader(canvas)
        var y = 130f

        // 1. KPI CARDS
        val kpiW = (pageWidth - 2 * margin - 20f) / 3f
        drawKPICard(canvas, y, "Venta Bruta", nf.format(uiState.totalVentaBruta), margin, kpiW, pDelisaGreen)
        drawKPICard(canvas, y, "Gastos Totales", nf.format(uiState.totalGastos), margin + kpiW + 10f, kpiW, pDelisaRed)
        drawKPICard(canvas, y, "Utilidad Neta", nf.format(uiState.utilidadOperativa), margin + 2 * (kpiW + 10f), kpiW, Color.BLACK)
        y += 85f

        // 2. TOP PRODUCTOS
        canvas.drawText("TOP 5 PRODUCTOS MÁS VENDIDOS", margin, y, pSectionHeader)
        y += 15f
        val prodCols = listOf("PRODUCTO" to margin + 5f, "CANTIDAD" to margin + 300f, "MONTO TOTAL" to margin + 420f)
        drawTableHeader(canvas, y, Color.rgb(50, 50, 50), prodCols)
        y += 22f

        uiState.topProductos.forEachIndexed { index, prod ->
            if (index % 2 == 0) canvas.drawRect(margin, y, pageWidth - margin, y + 20f, pZebra)
            canvas.drawText(prod.nombre.take(45), margin + 5f, y + 14f, pText)
            canvas.drawText("${prod.cantidad} uds", margin + 300f, y + 14f, pText)
            canvas.drawText(nf.format(prod.monto), margin + 420f, y + 14f, pBold)
            y += 20f
            canvas.drawLine(margin, y, pageWidth - margin, y, pLine)
        }
        y += 35f

        // 3. RANKING VENDEDORES
        if (y > pageHeight - 150f) {
            pdfDocument.finishPage(page); currentPageNumber++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); page = pdfDocument.startPage(pageInfo); canvas = page.canvas
            drawHeader(canvas); y = 120f
        }
        
        canvas.drawText("DESEMPEÑO POR VENDEDOR", margin, y, pSectionHeader)
        y += 15f
        val sellerCols = listOf("VENDEDOR" to margin + 5f, "TICKETS" to margin + 220f, "ANULADAS" to margin + 300f, "VENTA TOTAL" to margin + 400f)
        drawTableHeader(canvas, y, pDelisaRed, sellerCols)
        y += 22f

        uiState.rankingVendedores.forEachIndexed { index, seller ->
            if (y > pageHeight - 100f) {
                pdfDocument.finishPage(page); currentPageNumber++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); page = pdfDocument.startPage(pageInfo); canvas = page.canvas
                drawHeader(canvas); y = 120f; drawTableHeader(canvas, y, pDelisaRed, sellerCols); y += 22f
            }
            if (index % 2 == 0) canvas.drawRect(margin, y, pageWidth - margin, y + 20f, pZebra)
            canvas.drawText(seller.nombre.take(30), margin + 5f, y + 14f, pText)
            canvas.drawText("${seller.numTickets}", margin + 220f, y + 14f, pText)
            canvas.drawText("${seller.cancelaciones}", margin + 300f, y + 14f, pText)
            canvas.drawText(nf.format(seller.totalVenta), margin + 400f, y + 14f, pBold)
            y += 20f
            canvas.drawLine(margin, y, pageWidth - margin, y, pLine)
        }
        y += 35f

        // 4. DESGLOSE DE GASTOS
        if (uiState.desgloseGastos.isNotEmpty() && perfilNombre == "CONSOLIDADO") {
            if (y > pageHeight - 150f) {
                pdfDocument.finishPage(page); currentPageNumber++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); page = pdfDocument.startPage(pageInfo); canvas = page.canvas
                drawHeader(canvas); y = 120f
            }
            
            canvas.drawText("DISTRIBUCIÓN DE GASTOS OPERATIVOS", margin, y, pSectionHeader)
            y += 15f
            val expenseCols = listOf("CATEGORÍA DE GASTO" to margin + 5f, "TOTAL ACUMULADO" to margin + 400f)
            drawTableHeader(canvas, y, Color.rgb(100, 100, 100), expenseCols)
            y += 22f

            uiState.desgloseGastos.forEachIndexed { index, gasto ->
                if (index % 2 == 0) canvas.drawRect(margin, y, pageWidth - margin, y + 20f, pZebra)
                canvas.drawText(gasto.categoria.uppercase(), margin + 5f, y + 14f, pText)
                canvas.drawText(nf.format(gasto.total), margin + 400f, y + 14f, pBold)
                y += 20f
                canvas.drawLine(margin, y, pageWidth - margin, y, pLine)
            }
        }

        // --- FOOTER CON QR ---
        val footerY = pageHeight - 70f
        canvas.drawLine(margin, footerY, pageWidth - margin, footerY, Paint().apply { color = Color.BLACK; strokeWidth = 1f })
        
        try {
            val qrContent = "DELISA_REPORT|LINEA:${perfilNombre}|TOTAL:${uiState.totalVentaBruta}|DATE:${fechaReporteStr}"
            val bitMatrix = QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 100, 100)
            val qrBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
            for (i in 0 until 100) for (j in 0 until 100) qrBitmap.setPixel(i, j, if (bitMatrix.get(i, j)) Color.BLACK else Color.WHITE)
            canvas.drawBitmap(qrBitmap, null, RectF(margin, footerY + 10f, margin + 50f, footerY + 60f), null)
        } catch (e: Exception) {}

        val pFooterText = Paint().apply { textSize = 8f; color = Color.GRAY; textAlign = Paint.Align.RIGHT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
        canvas.drawText("DOCUMENTO OFICIAL DE INTELIGENCIA DE NEGOCIO", pageWidth - margin, footerY + 20f, pFooterText)
        canvas.drawText("PROPIEDAD DE GRUPO SAN ANGEL - CONFIDENCIAL", pageWidth - margin, footerY + 35f, pFooterText)
        canvas.drawText("PÁGINA $currentPageNumber", pageWidth - margin, footerY + 50f, pFooterText)

        pdfDocument.finishPage(page)
        
        val timestamp = System.currentTimeMillis()
        val folder = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (folder?.exists() == false) folder.mkdirs()
        
        val file = File(folder, "Reporte_${perfilNombre.replace(" ", "_")}_$timestamp.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        
        return file
    }
}
