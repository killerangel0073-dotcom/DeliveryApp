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




// ----------------------------
// Funciones de impresión
// ----------------------------
fun ImprimirTicket58mm(
    device: BluetoothDevice?,  // acepta null de forma segura
    context: Context,
    logoDrawableId: Int,
    cliente: String,
    productos: List<Plantilla_Producto>,
    lineWidth: Int = 32,
    espacioLogo: Int = 3,
    espacioQR: Int = 2,
    qrSize: Byte = 6
) {
    // 1) Validación inmediata
    if (device == null) {
        // Toast en hilo principal
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, "No se imprimió: no hay impresora seleccionada", android.widget.Toast.LENGTH_SHORT).show()
        }
        return
    }

    // 2) Verificar permiso en Android S+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Permiso BLUETOOTH_CONNECT requerido para imprimir", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
    }

    // 3) Ejecutar la impresión en un hilo de fondo (no bloquear UI)
    Thread {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null
        try {
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            socket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            outputStream = socket.outputStream

            // --- LOGO ---
            val drawable = ContextCompat.getDrawable(context, logoDrawableId)
            drawable?.let {
                val bitmap = Bitmap.createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                it.setBounds(0, 0, canvas.width, canvas.height)
                it.draw(canvas)

                val resized = Bitmap.createScaledBitmap(bitmap, 384, bitmap.height * 384 / bitmap.width, false)
                val bytesLogo = convertirBitmapABytesGSv0(resized)
                outputStream.write(bytesLogo)
                outputStream.write("\n".repeat(espacioLogo).toByteArray(Charsets.UTF_8))
            }

            val fechaHora = SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault()).format(Date())
            val folio = (1000..9999).random()
            val sb = StringBuilder()

            // --- ENCABEZADO ---
            sb.append("\n         DELISA BOTANAS\n")
            sb.append("  Grupo Corporativo San Angel\n\n\n")
            sb.append("Folio: #$folio\n")
            sb.append("Cliente: $cliente\n")
            sb.append("Vendedor: Lizeth Vanessa Flores Corona\n")
            sb.append("Fecha: $fechaHora\n\n")
            sb.append("-------------------------------\n")
            sb.append("CANT DESCRIPCION   PRECIO  TOTAL\n")
            sb.append("-------------------------------\n")

            // --- PRODUCTOS ---
            var totalCalc = 0.0
            for (p in productos.filter { it.cantidad > 0 }) {
                val subtotal = p.cantidad * p.precio
                totalCalc += subtotal
                val nombreAjustado = if (p.nombre.length > 16) p.nombre.take(16) else p.nombre.padEnd(16)
                val cantidadStr = p.cantidad.toString().padEnd(4)
                val precioStr = String.format("%.2f", p.precio).padStart(6)
                val subtotalStr = String.format("%.2f", subtotal).padStart(6)
                sb.append("$cantidadStr$nombreAjustado$precioStr$subtotalStr\n\n")
            }

            sb.append("-------------------------------\n")
            sb.append("-------------------------------\n")

            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            sb.clear()

            // --- Totales ---
            val impuesto = totalCalc * 0.08
            val totalConImpuesto = totalCalc + impuesto

            val subtotalStr = "Subtotal = ${"%.2f".format(totalCalc)}"
            val iepsStr = "IEPS 8%   = ${"%.2f".format(impuesto)}"
            val totalStr = "TOTAL     = ${"%.2f".format(totalConImpuesto)}"

            fun rightAlign(text: String, width: Int): String {
                val espacios = width - text.length
                return " ".repeat(if (espacios > 0) espacios else 0) + text
            }

            // Activar negritas
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x01))

            sb.append(rightAlign(subtotalStr, lineWidth) + "\n")
            sb.append(rightAlign(iepsStr, lineWidth) + "\n")
            sb.append(rightAlign(totalStr, lineWidth) + "\n\n\n")

            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            sb.clear()

            // Desactivar negritas
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x00))

            // Mensaje de gracias
            val mensajeGracias = "¡Gracias por su compra!"
            val espaciosAntes = (lineWidth - mensajeGracias.length) / 2
            sb.append(" ".repeat(if (espaciosAntes > 0) espaciosAntes else 0) + mensajeGracias + "\n\n")
            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            sb.clear()

            // Espacios antes del QR
            sb.append("\n".repeat(espacioQR))
            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            sb.clear()

            // QR
            val qrData = "https://delisabotanas.com/seguimiento/$folio"
            outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, 3, 0, 49, 67, qrSize))
            outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, qrData.length.toByte(), 0, 49, 80, 48))
            outputStream.write(qrData.toByteArray(Charsets.UTF_8))
            outputStream.write(byteArrayOf(0x1D, 0x28, 0x6B, 3, 0, 49, 81, 48))

            // Redes sociales y final
            sb.append("\n  Síguenos en redes sociales\n")
            sb.append("  Facebook: @DelisaBotanas\n")
            sb.append("  Instagram: @DelisaBotanas\n\n\n")
            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))

            // flush
            outputStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
            // Mostrar error en hilo principal
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Error al imprimir: ${e.message ?: "desconocido"}", android.widget.Toast.LENGTH_LONG).show()
            }
        } finally {
            // Cerrar recursos con seguridad
            try { outputStream?.close() } catch (_: Exception) { }
            try { socket?.close() } catch (_: Exception) { }
        }
    }.start()
}




// ----------------------------
// Conversión Bitmap a Bytes ESC/POS
// ----------------------------
fun convertirBitmapABytesGSv0(bitmap: Bitmap): ByteArray {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val bytes = mutableListOf<Byte>()
    val widthBytes = (width + 7) / 8

    bytes.addAll(byteArrayOf(0x1D, 0x76, 0x30, 0x00, (widthBytes % 256).toByte(), (widthBytes / 256).toByte(), (height % 256).toByte(), (height / 256).toByte()).toList())

    for (y in 0 until height) {
        for (x in 0 until width step 8) {
            var b: Byte = 0
            for (bit in 0..7) {
                if (x + bit < width) {
                    val pixel = pixels[y * width + x + bit]
                    val luminance = (0.299 * ((pixel shr 16) and 0xFF) +
                            0.587 * ((pixel shr 8) and 0xFF) +
                            0.114 * (pixel and 0xFF))
                    if (luminance < 128) b = b or (1 shl (7 - bit)).toByte()
                }
            }
            bytes.add(b)
        }
    }
    return bytes.toByteArray()
}