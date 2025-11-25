package com.gruposanangel.delivery.utilidades

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.model.Plantilla_Producto
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Genera un PDF con el ticket y devuelve el File.
 * NOTA: No llamar desde @Preview (usa isPreview antes).
 */
fun GeneraPDFdeVenta(

    context: Context,
    nombreCliente: String,
    productos: List<Plantilla_Producto>,
    total: Double
): File {
    val pdfDocument = PdfDocument()
    val pageWidth = 384
    val lineHeight = 20

    val productosValidos = productos.filter { it.cantidad > 0 && it.precio > 0 }
    val logo = context.getDrawable(R.drawable.logo)
    val logoWidth = 180
    val logoHeight = 120
    val espacioEncabezado = 10 + logoHeight + 35 + 3 * lineHeight + 15
    val espacioProductos = productosValidos.size * (lineHeight + 5)
    val extraMarginFinal = 160
    val espacioFooter = 15 + 30 + extraMarginFinal
    val pageHeight = espacioEncabezado + espacioProductos + espacioFooter

    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    val paintCenterBold = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 22f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }
    val paintCenter = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 14f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }
    val paintLeft = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 14f
        textAlign = android.graphics.Paint.Align.LEFT
        typeface = android.graphics.Typeface.MONOSPACE
    }
    val paintRight = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 16f
        textAlign = android.graphics.Paint.Align.RIGHT
        typeface = android.graphics.Typeface.MONOSPACE
    }
    val paintLine = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 1f
    }

    var y = 10
    logo?.let {
        val left = (pageWidth - logoWidth) / 2
        val top = y
        val right = left + logoWidth
        val bottom = top + logoHeight
        it.setBounds(left, top, right, bottom)
        it.draw(canvas)
        y += logoHeight + 50
    }

    canvas.drawText("DELISA BOTANAS", (pageWidth / 2).toFloat(), y.toFloat(), paintCenterBold)
    y += 20
    canvas.drawText("Grupo Corporativo San Angel", (pageWidth / 2).toFloat(), y.toFloat(), paintCenter)
    y += 35

    canvas.drawText("Vendedor: Lizeth Vanessa Flores Corona", 0f, y.toFloat(), paintLeft)
    y += lineHeight
    canvas.drawText("Cliente: $nombreCliente", 0f, y.toFloat(), paintLeft)
    y += lineHeight
    val fechaHora = SimpleDateFormat("dd/MM/yy - hh:mm:ss a").format(Date())
    canvas.drawText("Fecha: $fechaHora", 0f, y.toFloat(), paintLeft)
    y += lineHeight

    canvas.drawLine(0f, y.toFloat(), pageWidth.toFloat(), y.toFloat(), paintLine)
    y += 15

    val cantX = 0f
    val descX = 40f
    val precioX = pageWidth * 0.65f
    val totalX = pageWidth.toFloat() - 5f

    canvas.drawText("CANT", cantX, y.toFloat(), paintLeft)
    canvas.drawText("DESCRIPCIÓN", descX, y.toFloat(), paintLeft)
    canvas.drawText("PRECIO", precioX, y.toFloat(), paintRight)
    canvas.drawText("TOTAL", totalX, y.toFloat(), paintRight)
    y += lineHeight / 2
    canvas.drawLine(0f, y.toFloat(), pageWidth.toFloat(), y.toFloat(), paintLine)
    y += 20

    for (producto in productosValidos) {
        val nombreProd = if (producto.nombre.length > 20) producto.nombre.take(20) + "..." else producto.nombre
        canvas.drawText("${producto.cantidad}", cantX, y.toFloat(), paintLeft)
        canvas.drawText(nombreProd, descX, y.toFloat(), paintLeft)
        canvas.drawText("$${"%.2f".format(producto.precio)}", precioX, y.toFloat(), paintRight)
        val subtotal = producto.precio * producto.cantidad
        canvas.drawText("$${"%.2f".format(subtotal)}", totalX, y.toFloat(), paintRight)
        y += lineHeight
    }

    canvas.drawLine(0f, y.toFloat(), pageWidth.toFloat(), y.toFloat(), paintLine)
    y += 20

    val impuesto = total * 0.08
    val totalConImpuesto = total + impuesto
    canvas.drawText("Subtotal = $${"%.2f".format(total)}", totalX, y.toFloat(), paintRight)
    y += lineHeight
    canvas.drawText("IEPS 8% = $${"%.2f".format(impuesto)}", totalX, y.toFloat(), paintRight)
    y += lineHeight
    canvas.drawText("TOTAL = $${"%.2f".format(totalConImpuesto)}", totalX, y.toFloat(), paintRight)
    y += 30

    paintCenterBold.textSize = 14f
    canvas.drawText("¡Gracias por su compra!", (pageWidth / 2).toFloat(), y.toFloat(), paintCenterBold)

    pdfDocument.finishPage(page)

    // Guardar archivo PDF
    val timestamp = System.currentTimeMillis()
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "ticket_venta_$timestamp.pdf")
    pdfDocument.writeTo(FileOutputStream(file))
    pdfDocument.close()

    return file
}
