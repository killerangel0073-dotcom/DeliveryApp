package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.experimental.or
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion







@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovimientosInventarioScreen(
    navController: NavController,


    //////impresoras
    impresoraBluetooth: BluetoothDevice? = null,
    onImpresoraSeleccionada: (BluetoothDevice) -> Unit = {}
) {




// 🔹 Stock disponible del almacén origen
    val stockOrigen = remember { mutableStateMapOf<String, Int>() }
    var cargandoStock by remember { mutableStateOf(false) }



    var mostrarDialogConfirmacion by remember { mutableStateOf(false) }
    var origen by remember { mutableStateOf("Selecciona Origen") }
    var destino by remember { mutableStateOf("Selecciona Destino") }
    var expandedOrigen by remember { mutableStateOf(false) }
    var expandedDestino by remember { mutableStateOf(false) }

    val opcionesOrigen = listOf("Compra Producto", "Almacen Huasteca")
    val opcionesDestinoCompra = listOf("Almacen Huasteca")
    val opcionesDestinoAlmacen = listOf(
        "Vendedor Delisa R1",
        "Vendedor Delisa R2",
        "Vendedor Cazador R1",
        "Vendedor Cazador R2"
    )


    var plantillaProductos by remember { mutableStateOf(listOf<Plantilla_Producto>()) }
    var cargando by remember { mutableStateOf(true) }
    val cantidades = remember { mutableStateMapOf<String, Int>() }


    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance()
            .collection("producto")
            .get()
            .addOnSuccessListener { result ->


                val lista = result.documents.mapNotNull { doc ->
                    val id = doc.id  // el ID del documento
                    val nombre = doc.getString("nombre") ?: return@mapNotNull null
                    val precio = doc.getDouble("precio") ?: 0.0
                    val imagenUrl = doc.getString("imagenUrl") ?: ""
                    Plantilla_Producto(id = id, nombre = nombre, precio = precio, cantidad = 0, imagenUrl = imagenUrl)
                }

                plantillaProductos = lista
                lista.forEach { cantidades[it.id] = 0 }

                cargando = false
            }
            .addOnFailureListener { cargando = false }
    }


    LaunchedEffect(origen) {
        if (origen == "Selecciona Origen") return@LaunchedEffect

        cargandoStock = true
        stockOrigen.clear()

        FirebaseFirestore.getInstance()
            .collection("inventarioStock")
            .whereEqualTo("almacenNombre", origen)
            .get()
            .addOnSuccessListener { result ->
                for (doc in result.documents) {
                    val productoRef = doc.getDocumentReference("productoRef")
                    val cantidad = doc.getLong("cantidad")?.toInt() ?: 0

                    productoRef?.id?.let { productoId ->
                        stockOrigen[productoId] = cantidad
                    }
                }
                cargandoStock = false
            }
            .addOnFailureListener {
                cargandoStock = false
            }
    }






    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text(
            text = "Movimientos Inventario",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = Color.Black,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                Button(
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    onClick = { expandedOrigen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(origen, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }

                DropdownMenu(expanded = expandedOrigen, onDismissRequest = { expandedOrigen = false }) {
                    opcionesOrigen.forEach { opcion ->
                        DropdownMenuItem(text = { Text(opcion) }, onClick = {
                            origen = opcion
                            expandedOrigen = false
                            destino = "Selecciona Destino"
                        })
                    }
                }
            }

            val opcionesDestino = when (origen) {
                "Compra Producto" -> opcionesDestinoCompra
                "Almacen Huasteca" -> opcionesDestinoAlmacen
                else -> emptyList()
            }

            Box(modifier = Modifier.weight(1f)) {
                Button(
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    onClick = { expandedDestino = true },
                    enabled = origen != "Selecciona Origen",
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(destino,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }

                DropdownMenu(expanded = expandedDestino, onDismissRequest = { expandedDestino = false }) {
                    opcionesDestino.forEach { opcion ->
                        DropdownMenuItem(text = { Text(opcion) }, onClick = {
                            destino = opcion
                            expandedDestino = false
                        })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (cargando) {
            Box(
                modifier = Modifier
                    .weight(1f) // ocupa espacio restante
                    .fillMaxSize(),
                contentAlignment = Alignment
                    .Center
            ) { CircularProgressIndicator(color = Color.Red) }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(plantillaProductos) { producto ->
                    val cantidadActual = cantidades[producto.id] ?: 0
                    var textCantidad by remember(producto.id) { mutableStateOf(cantidadActual.toString()) }
                    val focusRequester = remember { FocusRequester() }
                    val numberFormatter = remember {
                        NumberFormat.getIntegerInstance(Locale.US)
                    }





                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && textCantidad == "0") {
                                    textCantidad = ""
                                }
                            }

                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            AsyncImage(
                                model = producto.imagenUrl,
                                placeholder = painterResource(R.drawable.repartidor),
                                error = painterResource(R.drawable.repartidor),
                                contentDescription = producto.nombre,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(producto.nombre, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Precio: $${producto.precio}", fontSize = 14.sp, color = Color(0xFF555555))


                                Spacer(modifier = Modifier.height(4.dp))
                                val subtotal = (cantidades[producto.id] ?: 0) * producto.precio

                                val subtotalFormateado = NumberFormat.getCurrencyInstance(Locale.US).format(subtotal)

                                Text("Subtotal: $$subtotalFormateado", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.width(8.dp))




                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Botón Restar
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val current = textCantidad.toIntOrNull() ?: 0
                                                if (current > 0) {
                                                    val nuevo = current - 1
                                                    textCantidad = nuevo.toString()
                                                    cantidades[producto.id] = nuevo
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("-", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Campo cantidad
                                    Box(
                                        modifier = Modifier
                                            .width(60.dp)
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {



                                        BasicTextField(
                                            value = numberFormatter.format(textCantidad.toIntOrNull() ?: 0),

                                            onValueChange = { newValue ->

                                                // 🔹 Quitar comas antes de procesar
                                                val limpio = newValue
                                                    .replace(",", "")
                                                    .filter { it.isDigit() }
                                                    .trimStart('0')

                                                val numeroIngresado = if (limpio.isEmpty()) 0 else limpio.toInt()

                                                val valorFinal = if (origen == "Compra Producto") {
                                                    numeroIngresado.coerceAtLeast(0)
                                                } else {
                                                    val disponible = stockOrigen[producto.id] ?: 0
                                                    numeroIngresado.coerceIn(0, disponible)
                                                }

                                                textCantidad = valorFinal.toString()
                                                cantidades[producto.id] = valorFinal
                                            },

                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            textStyle = LocalTextStyle.current.copy(
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Red,
                                                textAlign = TextAlign.Center
                                            ),
                                            modifier = Modifier.fillMaxSize()
                                        )

                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Botón Sumar
                                    val disponible = stockOrigen[producto.id] ?: 0

                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val current = textCantidad.toIntOrNull() ?: 0
                                                val nuevo = current + 1

                                                if (origen != "Compra Producto") {
                                                    val disponible = stockOrigen[producto.id] ?: 0
                                                    if (nuevo > disponible) return@clickable
                                                }

                                                textCantidad = nuevo.toString()
                                                cantidades[producto.id] = nuevo
                                            },


                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // 👇 STOCK DEBAJO DEL CONTADOR
                                if (origen != "Compra Producto") {
                                    Text(
                                        text = "Disponible: ${stockOrigen[producto.id] ?: 0}",
                                        fontSize = 14.sp,
                                        color = if ((stockOrigen[producto.id] ?: 0) == 0)
                                            Color.Red
                                        else
                                            Color(0xFF2E7D32),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                            }





                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))



        val total = plantillaProductos.sumOf { (cantidades[it.id] ?: 0) * it.precio }
        val nf = NumberFormat.getNumberInstance(Locale.US)
        nf.minimumFractionDigits = 2
        nf.maximumFractionDigits = 2
        val totalFormateado = nf.format(total)

        Text(
            text = "Total: $$totalFormateado",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )


        Spacer(modifier = Modifier.height(8.dp))






        val context = LocalContext.current
        // Estados para origen y destino (ejemplo)


        // Estado para mostrar el diálogo
        var mostrarDialogConfirmacion by remember { mutableStateOf(false) }

// Botón que dispara el diálogo
        Button(
            onClick = {
                // Validaciones antes de mostrar el diálogo
                if (origen == "Selecciona Origen") {
                    Toast.makeText(context, "Selecciona un almacén de origen", Toast.LENGTH_LONG).show()
                    return@Button
                }

                if (destino == "Selecciona Destino") {
                    Toast.makeText(context, "Selecciona un almacén de destino", Toast.LENGTH_LONG).show()
                    return@Button
                }

                val productosConCantidad = plantillaProductos.filter { (cantidades[it.id] ?: 0) > 0 }
                if (productosConCantidad.isEmpty()) {
                    Toast.makeText(context, "Selecciona al menos un producto", Toast.LENGTH_LONG).show()
                    return@Button
                }

                if (origen != "Compra Producto") {
                    for (producto in plantillaProductos) {
                        val solicitada = cantidades[producto.id] ?: 0
                        if (solicitada > 0) {
                            val disponible = stockOrigen[producto.id] ?: 0
                            if (solicitada > disponible) {
                                Toast.makeText(
                                    context,
                                    "Stock insuficiente de ${producto.nombre}. Disponible: $disponible",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                        }
                    }
                }

                // Mostrar diálogo de confirmación
                mostrarDialogConfirmacion = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Text("Crear Orden de Transferencia", fontSize = 16.sp)
        }

// Composable reutilizable para el diálogo
        if (mostrarDialogConfirmacion) {
            DialogoConfirmacion(
                titulo = "Confirmación",
                mensaje = "¿Deseas crear esta orden de transferencia?",
                textoConfirmar = "CONFIRMAR",
                textoCancelar = "CANCELAR",
                colorConfirmar = Color.Red,
                onConfirmar = {
                    mostrarDialogConfirmacion = false
                    crearOrdenDeTransferencia(
                        navController = navController,
                        plantillaProductos = plantillaProductos,
                        cantidades = cantidades,
                        origen = origen,
                        destino = destino,
                        impresoraBluetooth = impresoraBluetooth
                    )
                },
                onCancelar = { mostrarDialogConfirmacion = false }
            )
        }






    }
}



private fun crearOrdenDeTransferencia(
    navController: NavController,
    plantillaProductos: List<Plantilla_Producto>,
    cantidades: Map<String, Int>,
    origen: String,
    destino: String,
    impresoraBluetooth: BluetoothDevice?
) {
    val context = navController.context

    try {
        // Filtrar solo los productos con cantidad > 0
        val productosConCantidad = plantillaProductos.filter { (cantidades[it.id] ?: 0) > 0 }

        if (productosConCantidad.isEmpty()) {
            Toast.makeText(context, "Selecciona al menos un producto", Toast.LENGTH_LONG).show()
            return
        }

        // Construir lista de productos para Firestore
        val listaProductos = productosConCantidad.map { p ->
            mapOf(
                "productoId" to p.id,
                "cantidad" to (cantidades[p.id] ?: 0)
            )
        }

        val db = FirebaseFirestore.getInstance()

        // Crear un documento nuevo en ordenesTransferencia con ID automático
        val ordenRef = db.collection("ordenesTransferencia").document()

        val tipoOrden = when {
            origen == "Compra Producto" -> "COMPRA_PRODUCTO"
            destino.startsWith("Vendedor") -> "TRANSFERENCIA_VENDEDOR"
            else -> "TRANSFERENCIA_INTERNA"
        }

        // Datos de la orden
        val estadoOrden = "PENDIENTE"
        val ordenData = mapOf(
            "tipo" to tipoOrden,
            "origen" to origen,
            "destino" to destino,
            "productos" to listaProductos,
            "vendedorId" to FirebaseAuth.getInstance().currentUser?.uid,
            "estado" to estadoOrden,
            "timestamp" to FieldValue.serverTimestamp()
        )

        // Guardar orden en Firestore
        Toast.makeText(
            context,
            "Orden guardada localmente, se enviará cuando haya conexión",
            Toast.LENGTH_LONG
        ).show()

        ordenRef.set(ordenData)
            .addOnSuccessListener {
                Toast.makeText(context, "Orden sincronizada con la nube", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Error creando la orden: ${e.message}", Toast.LENGTH_LONG).show()
            }

        // 2️⃣ Generar PDF y visualizarlo
        val file = generarPdfMovimientoInventario(
            context = context,
            origen = origen,
            destino = destino,
            plantillaProductos = plantillaProductos,
            cantidades = cantidades
        )
        abrirPdfConFileProvider(context, file)

        // 3️⃣ Imprimir Ticket en Bluetooth (si hay impresora seleccionada)
        if (impresoraBluetooth != null) {
            imprimirMovimientoInventario58mmCorregida(
                device = impresoraBluetooth,
                context = context,
                logoDrawableId = R.drawable.logo, // tu logo aquí
                origen = origen,
                destino = destino,
                plantillaProductos = plantillaProductos,
                cantidades = cantidades
            )
            Toast.makeText(context, "Enviando impresión...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No hay impresora Bluetooth seleccionada", Toast.LENGTH_LONG).show()
        }

    } catch (e: Exception) {
        Toast.makeText(context, "Error generando PDF o imprimiendo: ${e.message}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
}






// Conversión segura de bitmap a bytes para impresora 58mm con ESC/POS GS v0
fun convertirBitmapABytesGSv02(bitmap: Bitmap): ByteArray {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    val bytes = mutableListOf<Byte>()
    val widthBytes = (width + 7) / 8

    // Comando ESC/POS para imprimir imagen
    bytes.addAll(
        byteArrayOf(
            0x1D, 0x76, 0x30, 0x00,
            (widthBytes % 256).toByte(), (widthBytes / 256).toByte(),
            (height % 256).toByte(), (height / 256).toByte()
        ).toList()
    )

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

// Imprimir movimiento de inventario en impresora Bluetooth 58mm
fun imprimirMovimientoInventario58mmCorregida(
    device: BluetoothDevice,
    context: Context,
    logoDrawableId: Int,
    origen: String,
    destino: String,
    plantillaProductos: List<Plantilla_Producto>,
    cantidades: Map<String, Int>,
    lineWidth: Int = 32,
    espacioLogo: Int = 1
) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            val socket = device.createRfcommSocketToServiceRecord(uuid)
            socket.connect()
            val outputStream = socket.outputStream

            // --- LOGO ---
            val drawable = ContextCompat.getDrawable(context, logoDrawableId)
            drawable?.let {
                val bitmap = Bitmap.createBitmap(it.intrinsicWidth, it.intrinsicHeight, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                it.setBounds(0, 0, canvas.width, canvas.height)
                it.draw(canvas)

                val resized = Bitmap.createScaledBitmap(bitmap, 384, bitmap.height * 384 / bitmap.width, false)
                val bytesLogo = convertirBitmapABytesGSv02(resized)
                outputStream.write(bytesLogo)
                outputStream.write("\n".repeat(espacioLogo).toByteArray(Charsets.UTF_8))
            }

            val fechaHora = SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.getDefault()).format(Date())
            val sb = StringBuilder()

            // --- ENCABEZADO ---
            sb.append("\n    MOVIMIENTO INVENTARIO\n\n\n")
            sb.append("Fecha: $fechaHora\n\n")
            sb.append("Origen: $origen\n")
            sb.append("Encargada: Itzel Hernandez\n\n")

            sb.append("Destino: $destino\n")
            sb.append("Vendedor: Rey Gerardo Perez\n\n")
            sb.append("-------------------------------\n")
            sb.append("CANT PRODUCTO    PRECIO  SUBT\n")
            sb.append("-------------------------------\n")

            // --- PRODUCTOS CON CANTIDAD ---
            val productosConCantidad = plantillaProductos.filter { cantidades[it.id]?.let { cant -> cant > 0 } ?: false }

            var totalGeneral = 0.0

            for (p in productosConCantidad) {
                val cantidad = cantidades[p.id] ?: 0

                val subtotal = cantidad * p.precio
                totalGeneral += subtotal

                val nombreAjustado = if (p.nombre.length > 16) p.nombre.take(16) else p.nombre.padEnd(16)
                val cantidadStr = cantidad.toString().padEnd(4)
                val precioStr = String.format("%.2f", p.precio).padStart(6)
                val subtotalStr = String.format("%.2f", subtotal).padStart(6)
                sb.append("$cantidadStr$nombreAjustado$precioStr$subtotalStr\n")
            }

            sb.append("-------------------------------\n")

            // --- TOTAL EN NEGRITAS ---
            val totalStr = "TOTAL = ${"%.2f".format(totalGeneral)}"
            val espacios = lineWidth - totalStr.length

            outputStream.write(byteArrayOf(0x1B, 0x45, 0x01)) // activar negritas
            sb.append(" ".repeat(if (espacios > 0) espacios else 0) + totalStr + "\n\n\n")
            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
            sb.clear()
            outputStream.write(byteArrayOf(0x1B, 0x45, 0x00)) // desactivar negritas

            // --- MENSAJE FINAL ---
            sb.append("¡Movimiento registrado!\n\n")
            sb.append("Firma del Vendedor:\n\n\n")
            sb.append("________________________\n\n\n\n")
            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))

            // --- CORTE DE PAPEL ---
            outputStream.write(byteArrayOf(0x1D, 0x56, 0x00))

            outputStream.flush()
            outputStream.close()
            socket.close()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}



// Abrir un PDF usando FileProvider
fun abrirPdfConFileProvider(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider", // <- este authority debe estar configurado en el Manifest
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Abrir PDF con"))
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir el PDF: ${e.message}", Toast.LENGTH_LONG).show()
        e.printStackTrace()
    }
}

// Generar PDF para el movimiento de inventario
fun generarPdfMovimientoInventario(
    context: Context,
    origen: String,
    destino: String,
    plantillaProductos: List<Plantilla_Producto>,
    cantidades: Map<String, Int>
): File {
    val pdfDocument = PdfDocument()
    val pageWidth = 384
    val lineHeight = 22
    val margin = 10
    val productosConCantidad = plantillaProductos.filter { (cantidades[it.id] ?: 0) > 0 }

    val pageHeight = 450 + productosConCantidad.size * lineHeight + 150
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    val paintLeft = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 14f
        textAlign = android.graphics.Paint.Align.LEFT
        isFakeBoldText = true
        typeface = android.graphics.Typeface.MONOSPACE
    }
    val paintRight = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 14f
        textAlign = android.graphics.Paint.Align.RIGHT
        isFakeBoldText = true
        typeface = android.graphics.Typeface.MONOSPACE
    }
    val paintCenterBold = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 18f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
    }
    val paintLine = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 1f
    }

    var y = margin
    val logo = ContextCompat.getDrawable(context, R.drawable.logo)
    val logoWidth = 140
    val logoHeight = 100
    logo?.let {
        val left = (pageWidth - logoWidth) / 2
        val top = y
        it.setBounds(left, top, left + logoWidth, top + logoHeight)
        it.draw(canvas)
        y += logoHeight + 30
    }

    canvas.drawText("MOVIMIENTO INVENTARIO", (pageWidth / 2).toFloat(), y.toFloat(), paintCenterBold)
    y += 40

    val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
    canvas.drawText("Fecha: $fecha", margin.toFloat(), y.toFloat(), paintLeft)
    y += 30
    canvas.drawText("Origen: $origen", margin.toFloat(), y.toFloat(), paintLeft)
    y += lineHeight
    canvas.drawText("Encargada: Itzel Hernandez Hernandez", margin.toFloat(), y.toFloat(), paintLeft)
    y += 30
    canvas.drawText("Destino: $destino", margin.toFloat(), y.toFloat(), paintLeft)
    y += lineHeight
    canvas.drawText("Vendedor: Rey Gerardo Perez Aquino", margin.toFloat(), y.toFloat(), paintLeft)
    y += 40

    canvas.drawLine(margin.toFloat(), y.toFloat(), (pageWidth - margin).toFloat(), y.toFloat(), paintLine)
    y += 15

    val cantX = margin.toFloat()
    val descX = 60f
    val precioX = pageWidth * 0.65f
    val totalX = (pageWidth - margin).toFloat()
    canvas.drawText("CANT", cantX, y.toFloat(), paintLeft)
    canvas.drawText("PRODUCTO", descX, y.toFloat(), paintLeft)
    canvas.drawText("PRECIO", precioX, y.toFloat(), paintRight)
    canvas.drawText("SUBTOTAL", totalX, y.toFloat(), paintRight)
    y += lineHeight
    canvas.drawLine(margin.toFloat(), y.toFloat(), (pageWidth - margin).toFloat(), y.toFloat(), paintLine)
    y += 15

    var totalGeneral = 0.0
    val nf = NumberFormat.getNumberInstance(Locale.US)
    for (p in productosConCantidad) {
        val cantidad = cantidades[p.id] ?: 0

        val subtotal = cantidad * p.precio
        totalGeneral += subtotal
        canvas.drawText("$cantidad", cantX, y.toFloat(), paintLeft)
        val nombreProd = if (p.nombre.length > 20) p.nombre.take(20) + "..." else p.nombre
        canvas.drawText(nombreProd, descX, y.toFloat(), paintLeft)
        canvas.drawText("$${nf.format(p.precio)}", precioX, y.toFloat(), paintRight)
        canvas.drawText("$${nf.format(subtotal)}", totalX, y.toFloat(), paintRight)
        y += lineHeight
    }

    y += 10
    canvas.drawLine(margin.toFloat(), y.toFloat(), (pageWidth - margin).toFloat(), y.toFloat(), paintLine)
    y += lineHeight
    val paintTotal = android.graphics.Paint(paintRight).apply {
        textSize = 16f
        isFakeBoldText = true
    }
    canvas.drawText("TOTAL: $${nf.format(totalGeneral)}", totalX, y.toFloat(), paintTotal)

    y += 60
    canvas.drawText("¡Movimiento registrado!", (pageWidth / 2).toFloat(), y.toFloat(), paintCenterBold)

    y += 80
    canvas.drawText("Firma del Vendedor: ____________________", margin.toFloat(), y.toFloat(), paintLeft)

    pdfDocument.finishPage(page)
    val timestamp = System.currentTimeMillis()
    val file = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
        "movimiento_$timestamp.pdf"
    )
    pdfDocument.writeTo(FileOutputStream(file))
    pdfDocument.close()

    return file
}







@Preview(showBackground = true)
@Composable
fun MovimientosInventarioPreview() {
    val navController = rememberNavController()
    MovimientosInventarioScreen(navController)
}
