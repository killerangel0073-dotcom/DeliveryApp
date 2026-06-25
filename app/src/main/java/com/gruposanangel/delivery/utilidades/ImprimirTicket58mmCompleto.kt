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
import kotlinx.coroutines.*

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
    espacioLogo: Int = 2
) {
    if (device == null) return

    fun limpiarTexto(texto: String): String {
        return texto.replace("ñ", "n").replace("Ñ", "N")
            .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
            .replace("Á", "A").replace("É", "E").replace("Í", "I").replace("Ó", "O").replace("Ú", "U")
    }

    // Comandos ESC/POS Estándar
    val ESC = 0x1B.toByte()
    val GS = 0x1D.toByte()
    val ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(ESC, 0x61, 0x01)
    val BOLD_ON = byteArrayOf(ESC, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(ESC, 0x45, 0x00)
    val DOUBLE_SIZE_ON = byteArrayOf(ESC, 0x21, 0x30) // Doble alto y ancho
    val NORMAL_SIZE = byteArrayOf(ESC, 0x21, 0x00)

    CoroutineScope(Dispatchers.IO).launch {
        var socket: BluetoothSocket? = null
        var outputStream: OutputStream? = null
        try {
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            socket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            outputStream = socket.outputStream

            // 1. LOGO Y TITULO GIGANTE
            outputStream.write(ALIGN_CENTER)
            val drawable = ContextCompat.getDrawable(context, logoDrawableId)
            drawable?.let {
                val bitmap = Bitmap.createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                it.setBounds(0, 0, canvas.width, canvas.height)
                it.draw(canvas)
                val resized = Bitmap.createScaledBitmap(bitmap, 300, bitmap.height * 300 / bitmap.width, false)
                outputStream.write(convertirBitmapABytesGSv0(resized))
                outputStream.write("\n".repeat(espacioLogo).toByteArray())
            }

            outputStream.write(DOUBLE_SIZE_ON)
            outputStream.write("DELISA\n".toByteArray())
            outputStream.write(NORMAL_SIZE)
            outputStream.write(BOLD_ON)
            outputStream.write("BOTANAS Y PRODUCTOS\n".toByteArray())
            outputStream.write(BOLD_OFF)
            outputStream.write("Grupo Corporativo San Angel\n".toByteArray())
            outputStream.write("\n".toByteArray())

            // 2. INFO DEL TICKET
            outputStream.write(ALIGN_LEFT)
            val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            val sufijo = if (!ventaId.isNullOrBlank()) (if (ventaId.length > 8) ventaId.takeLast(6) else ventaId).uppercase() else "PROV"
            val fechaHora = SimpleDateFormat("dd/MM/yyyy  hh:mm a", Locale.getDefault()).format(fechaVenta ?: Date())

            outputStream.write("================================\n".toByteArray())
            outputStream.write(BOLD_ON)
            outputStream.write("TICKET DE VENTA: #$sufijo\n".toByteArray())
            outputStream.write(BOLD_OFF)
            outputStream.write("Folio: DEL-$year-$sufijo\n".toByteArray())
            outputStream.write("Fecha: $fechaHora\n".toByteArray())
            outputStream.write("--------------------------------\n".toByteArray())
            
            outputStream.write(BOLD_ON)
            outputStream.write("CLIENTE: ${limpiarTexto(cliente).uppercase()}\n".toByteArray())
            outputStream.write(BOLD_OFF)
            
            vendedorNombre?.let { outputStream.write("Atendio: ${limpiarTexto(it)}\n".toByteArray()) }
            metodoPago?.let { outputStream.write("Metodo: ${limpiarTexto(it)}\n".toByteArray()) }
            outputStream.write("================================\n\n".toByteArray())

            // 3. TABLA DE PRODUCTOS
            outputStream.write(BOLD_ON)
            outputStream.write("CANT DESC.          PRECIO TOTAL\n".toByteArray())
            outputStream.write(BOLD_OFF)
            outputStream.write("--------------------------------\n".toByteArray())

            var totalCalc = 0.0
            for (p in productos.filter { it.cantidad > 0 }) {
                val subtotal = p.cantidad * p.precio
                totalCalc += subtotal
                
                // Formateo: 3(CANT) + 1 + 14(DESC) + 1 + 6(PRE) + 1 + 6(TOT) = 32
                val cant = p.cantidad.toString().padEnd(3)
                val desc = limpiarTexto(if (p.nombre.length > 14) p.nombre.take(14) else p.nombre.padEnd(14))
                val pre = String.format("%.2f", p.precio).padStart(6)
                val tot = String.format("%.2f", subtotal).padStart(6)
                
                outputStream.write("$cant $desc $pre $tot\n".toByteArray())
            }
            outputStream.write("--------------------------------\n".toByteArray())

            // 4. SECCION DE TOTAL GIGANTE
            outputStream.write("\n".toByteArray())
            outputStream.write(ALIGN_CENTER)
            outputStream.write(byteArrayOf(ESC, 0x21, 0x10)) // Solo Doble Altura
            outputStream.write(BOLD_ON)
            val totalFinal = totalVenta ?: totalCalc
            outputStream.write("TOTAL: $${String.format("%.2f", totalFinal)}\n".toByteArray())
            outputStream.write(BOLD_OFF)
            outputStream.write(NORMAL_SIZE)
            outputStream.write(ALIGN_LEFT)
            outputStream.write("\n".toByteArray())

            // 5. PIE DE PAGINA ESTILIZADO
            outputStream.write(ALIGN_CENTER)
            outputStream.write("********************************\n".toByteArray())
            outputStream.write(BOLD_ON)
            outputStream.write("¡GRACIAS POR SU COMPRA!\n".toByteArray())
            outputStream.write(BOLD_OFF)
            outputStream.write("Este no es un comprobante fiscal\n".toByteArray())
            outputStream.write("********************************\n\n".toByteArray())
            
            outputStream.write("Siguenos en Redes Sociales:\n".toByteArray())
            outputStream.write("FB: @DelisaBotanas\n".toByteArray())
            outputStream.write("IG: @DelisaBotanas\n".toByteArray())
            
            // Espacio final largo para que el usuario pueda cortar bien el ticket
            outputStream.write("\n\n\n\n\n".toByteArray())
            outputStream.flush()
            
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { delay(500); outputStream?.close(); socket?.close() } catch (_: Exception) {}
        }
    }
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
