package com.gruposanangel.delivery.utilidades

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.experimental.or
import com.gruposanangel.delivery.model.Plantilla_Producto

// Añade esto en el archivo utilidades (mismo lugar donde está ImprimirTicket58mm)
fun ImprimirTicket58mmCompleto(
    device: android.bluetooth.BluetoothDevice?,  // acepta null de forma segura
    context: android.content.Context,
    logoDrawableId: Int,
    cliente: String,
    productos: List<com.gruposanangel.delivery.model.Plantilla_Producto>,
    // parámetros opcionales extra (para imprimir exactamente lo guardado en DB)
    ventaId: Long? = null,
    fechaVenta: java.util.Date? = null,
    totalVenta: Double? = null,
    vendedorNombre: String? = null,
    metodoPago: String? = null,
    sincronizado: Boolean? = null,
    firestoreId: String? = null,
    clienteId: String? = null,
    // ajustes de formato
    lineWidth: Int = 32,
    espacioLogo: Int = 3,
    espacioQR: Int = 2,
    qrSize: Byte = 6
) {
    // validaciones iniciales (mismo comportamiento que la original)
    if (device == null) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, "No se imprimió: no hay impresora seleccionada", android.widget.Toast.LENGTH_SHORT).show()
        }
        return
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Permiso BLUETOOTH_CONNECT requerido para imprimir", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
    }

    Thread {
        var socket: android.bluetooth.BluetoothSocket? = null
        var outputStream: java.io.OutputStream? = null
        try {
            val uuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            socket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            outputStream = socket.outputStream

            // --- Logo ---
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, logoDrawableId)
            drawable?.let {
                val bitmap = android.graphics.Bitmap.createBitmap(it.intrinsicWidth, it.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                it.setBounds(0, 0, canvas.width, canvas.height)
                it.draw(canvas)

                val resized = android.graphics.Bitmap.createScaledBitmap(bitmap, 384, bitmap.height * 384 / bitmap.width, false)
                val bytesLogo = convertirBitmapABytesGSv0(resized)
                outputStream.write(bytesLogo)
                outputStream.write("\n".repeat(espacioLogo).toByteArray(Charsets.UTF_8))
            }

            // --- Datos (prefiriendo valores pasados desde DB) ---
            val fechaParaImprimir = fechaVenta ?: java.util.Date()
            val fechaHora = java.text.SimpleDateFormat("dd/MM/yy HH:mm:ss", java.util.Locale.getDefault()).format(fechaParaImprimir)
            val folioStr = ventaId?.toString() ?: (1000..9999).random().toString()

            val sb = StringBuilder()



            // --- Encabezado ---
            sb.append("\n         DELISA BOTANAS\n")
            sb.append("  Grupo Corporativo San Angel\n\n")
            sb.append("Folio: #$folioStr\n")
            sb.append("Cliente: $cliente\n")
            vendedorNombre?.let { sb.append("Vendedor: $it\n") }
            metodoPago?.let { sb.append("Pago: $it\n") }
            sb.append("Fecha: $fechaHora\n")
            sincronizado?.let { sb.append("Sincronizado: ${if (it) "SI" else "NO"}\n") }

            sb.append("-------------------------------\n")
            sb.append("CANT DESCRIPCION   PRECIO  TOTAL\n")
            sb.append("-------------------------------\n")

              // --- Productos ---
            var totalCalc = 0.0
            for (p in productos.filter { it.cantidad > 0 }) {
                val subtotal = p.cantidad * p.precio
                totalCalc += subtotal

                val nombreAjustado = if (p.nombre.length > 16) p.nombre.take(16) else p.nombre.padEnd(16)
                val cantidadStr = p.cantidad.toString().padEnd(4)
                val precioStr = String.format("%.2f", p.precio).padStart(6)
                val subtotalStr = String.format("%.2f", subtotal).padStart(7)

                sb.append("$cantidadStr$nombreAjustado$precioStr$subtotalStr\n")
            }


            sb.append("-------------------------------\n")

            val totalUsado = totalVenta ?: totalCalc
            val impuesto = (totalUsado * 0.08)
            val totalConImpuesto = totalUsado + impuesto

            fun rightAlign(text: String, width: Int): String {
                val espacios = width - text.length
                return " ".repeat(if (espacios > 0) espacios else 0) + text
            }

            // negritas encendido
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x01))

            val subtotalStr = "Subtotal = ${"%.2f".format(totalUsado)}"
            val iepsStr = "IEPS 8%   = ${"%.2f".format(impuesto)}"
            val totalStr = "TOTAL     = ${"%.2f".format(totalConImpuesto)}"

            sb.append(rightAlign(subtotalStr, lineWidth) + "\n")
            sb.append(rightAlign(iepsStr, lineWidth) + "\n")
            sb.append(rightAlign(totalStr, lineWidth) + "\n\n\n")

            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            sb.clear()

            // negritas apagado
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x00))

            val mensajeGracias = "¡Gracias por su compra!"
            val espaciosAntes = (lineWidth - mensajeGracias.length) / 2
            sb.append(" ".repeat(if (espaciosAntes > 0) espaciosAntes else 0) + mensajeGracias + "\n\n")
            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            sb.clear()

            sb.append("\n".repeat(espacioQR))
            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            sb.clear()

            val qrData = if (ventaId != null) "https://delisabotanas.com/seguimiento/venta/$ventaId" else "https://delisabotanas.com/seguimiento/$folioStr"
            outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, 3, 0, 49, 67, qrSize))
            outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, qrData.length.toByte(), 0, 49, 80, 48))
            outputStream.write(qrData.toByteArray(Charsets.UTF_8))
            outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, 3, 0, 49, 81, 48))

            sb.append("\n  Síguenos en redes sociales\n")
            sb.append("  Facebook: @DelisaBotanas\n")
            sb.append("  Instagram: @DelisaBotanas\n\n\n")
            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))

            outputStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Error al imprimir: ${e.message ?: "desconocido"}", android.widget.Toast.LENGTH_LONG).show()
            }
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }.start()
}
