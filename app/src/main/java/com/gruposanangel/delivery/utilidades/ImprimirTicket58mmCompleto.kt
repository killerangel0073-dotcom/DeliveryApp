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

fun ImprimirTicket58mmCompleto(
    device: BluetoothDevice?,
    context: Context,
    logoDrawableId: Int,
    cliente: String,
    productos: List<Plantilla_Producto>,
    ventaId: String? = null,
    fechaVenta: Date? = null,
    totalVenta: Double? = null,
    vendedorNombre: String? = null,
    metodoPago: String? = null,
    lineWidth: Int = 32,
    espacioLogo: Int = 3
) {
    if (device == null) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, "No se imprimió: no hay impresora seleccionada", android.widget.Toast.LENGTH_SHORT).show()
        }
        return
    }

    // Función para limpiar caracteres problemáticos (ñ, acentos)
    fun limpiarTexto(texto: String): String {
        return texto.replace("ñ", "n").replace("Ñ", "N")
            .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
            .replace("Á", "A").replace("É", "E").replace("Í", "I").replace("Ó", "O").replace("Ú", "U")
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
    }

    Thread {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null
        try {
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            socket = try {
                device.createRfcommSocketToServiceRecord(uuid)
            } catch (_: Exception) {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                m.invoke(device, 1) as BluetoothSocket
            }

            socket?.connect()
            outputStream = socket.outputStream

            // Logo
            val drawable = ContextCompat.getDrawable(context, logoDrawableId)
            drawable?.let {
                val bitmap = Bitmap.createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                it.setBounds(0, 0, canvas.width, canvas.height)
                it.draw(canvas)
                val resized = Bitmap.createScaledBitmap(bitmap, 384, bitmap.height * 384 / bitmap.width, false)
                outputStream.write(convertirBitmapABytesGSv0(resized))
                outputStream.write("\n".repeat(espacioLogo).toByteArray(Charsets.UTF_8))
            }

            // Datos
            val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            val sufijo = if (!ventaId.isNullOrBlank()) ventaId.takeLast(6).uppercase() else "SIN FOLIO"
            val fechaHora = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(fechaVenta ?: Date())

            val sb = StringBuilder()
            sb.append("\n         DELISA BOTANAS\n")
            sb.append("  Grupo Corporativo San Angel\n\n")
            sb.append("TICKET #$sufijo\n")
            sb.append("Folio: DEL-$year-$sufijo\n")
            sb.append("Cliente: ${limpiarTexto(cliente)}\n")

            // Vendedor en un solo renglón
            vendedorNombre?.let {
                val nombreV = limpiarTexto(it)
                sb.append("Vendedor: ${if (nombreV.length > 20) nombreV.take(20) else nombreV}\n")
            }
            sb.append("\n") // Espacio tras vendedor

            metodoPago?.let { sb.append("Pago: ${limpiarTexto(it)}\n") }
            sb.append("Fecha: $fechaHora\n\n") // Espacio tras fecha

            sb.append("-------------------------------\n")
            sb.append("CAN DESCRIPCION   PRECIO  TOTAL\n")
            sb.append("-------------------------------\n")

            // Productos
            var totalCalc = 0.0
            for (p in productos.filter { it.cantidad > 0 }) {
                val subtotal = p.cantidad * p.precio
                totalCalc += subtotal
                val cantidadStr = p.cantidad.toString().padEnd(3)
                val nombreAjustado = limpiarTexto(if (p.nombre.length > 13) p.nombre.take(13) else p.nombre.padEnd(13))
                val precioStr = String.format("%.2f", p.precio).padStart(6)
                val subtotalStr = String.format("%.2f", subtotal).padStart(7)
                sb.append("$cantidadStr $nombreAjustado $precioStr $subtotalStr\n")
            }

            sb.append("-------------------------------\n")

            val totalUsado = totalVenta ?: totalCalc
            val impuesto = (totalUsado * 0.08)
            val totalConImpuesto = totalUsado + impuesto

            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            sb.clear()

            // Negritas ON
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x01))

            fun rightAlign(text: String, width: Int): String {
                val espacios = width - text.length
                return " ".repeat(if (espacios > 0) espacios else 0) + text
            }

            sb.append(rightAlign("Subtotal = ${"%.2f".format(totalUsado)}", lineWidth) + "\n")
            sb.append(rightAlign("IEPS 8%   = ${"%.2f".format(impuesto)}", lineWidth) + "\n")
            sb.append(rightAlign("TOTAL     = ${"%.2f".format(totalConImpuesto)}", lineWidth) + "\n\n\n") // Espacio tras total

            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            sb.clear()

            // Negritas OFF
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x00))

            val gracias = "Gracias por su compra!"
            sb.append(" ".repeat((lineWidth - gracias.length) / 2) + gracias + "\n\n")

            sb.append("  Siguenos en redes sociales\n")
            sb.append("  Facebook: @DelisaBotanas\n")
            sb.append("  Instagram: @DelisaBotanas\n\n\n\n\n") // 5 espacios finales

            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { socket?.close() } catch (_: Exception) {}
        }
    }.start()
}

fun convertirBitmapABytesGSv0(bitmap: android.graphics.Bitmap): ByteArray {
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
                    val luminance = (0.299 * ((pixel shr 16) and 0xFF) + 0.587 * ((pixel shr 8) and 0xFF) + 0.114 * (pixel and 0xFF))
                    if (luminance < 128) b = b or (1 shl (7 - bit)).toByte()
                }
            }
            bytes.add(b)
        }
    }
    return bytes.toByteArray()
}