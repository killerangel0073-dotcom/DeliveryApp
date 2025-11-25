package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.utilidades.GeneraPDFdeVenta
import com.gruposanangel.delivery.utilidades.ImprimirTicket58mm
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.data.ClienteEntity
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaRepository
import java.text.NumberFormat
import java.util.Locale
import com.gruposanangel.delivery.utilidades.hayInternet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException


// ----------------------------
// Pantalla Venta
// ----------------------------
@Composable
fun PantallaVenta(
    navController: NavController,
    clienteId: String,
    repository: RepositoryCliente? = null,
    inventarioRepo: RepositoryInventario? = null,
    impresoraBluetooth: BluetoothDevice? = null,
    productosPreview: List<Plantilla_Producto>? = null
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    // Define un estado para el Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope() // ✅ necesario para lanzar Snackbar desde un callback



    val db = AppDatabase.getDatabase(context)
    val ventaRepo = VentaRepository(db.VentaDao())
    val vistaModeloVenta: VistaModeloVenta? =
        if (!isPreview) {
            val db = AppDatabase.getDatabase(context)
            val inventarioRepoNonNull = inventarioRepo ?: RepositoryInventario(db.productoDao())
            val ventaRepo = VentaRepository(db.VentaDao())

            viewModel(factory = VistaModeloVentaFactory(
                repositoryInventario = inventarioRepoNonNull,
                ventaRepository = ventaRepo
            ))
        } else {
            null
        }






    // -------- Estado de ruta para mensajes en área de productos --------
    var rutaAsignada by remember { mutableStateOf<String?>(null) }
    var rutaCargada by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            rutaAsignada = null
            rutaCargada = true
            return@LaunchedEffect
        }
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("users")
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val userDoc = querySnapshot.documents.firstOrNull()
                if (userDoc == null) {
                    rutaAsignada = null
                    rutaCargada = true
                    return@addOnSuccessListener
                }

                // extraemos el campo sin asumir que sea DocumentReference
                val rutaField = userDoc.get("rutaAsignada")
                val rutaRef = rutaField as? com.google.firebase.firestore.DocumentReference

                if (rutaRef == null) {
                    rutaAsignada = null
                    rutaCargada = true
                    return@addOnSuccessListener
                }

                rutaRef.get().addOnSuccessListener { rutaSnap ->
                    val almacenField = rutaSnap.get("almacenAsignado")
                    val almacenRef = almacenField as? com.google.firebase.firestore.DocumentReference
                    if (almacenRef == null) {
                        rutaAsignada = null
                        rutaCargada = true
                        return@addOnSuccessListener
                    }

                    almacenRef.get().addOnSuccessListener { almacenSnap ->
                        val nombreAlmacen = almacenSnap.getString("nombre")
                        rutaAsignada = if (!nombreAlmacen.isNullOrEmpty()) nombreAlmacen else null
                        rutaCargada = true
                    }.addOnFailureListener {
                        rutaAsignada = null
                        rutaCargada = true
                    }
                }.addOnFailureListener {
                    rutaAsignada = null
                    rutaCargada = true
                }
            }
            .addOnFailureListener {
                rutaAsignada = null
                rutaCargada = true
            }
    }






















    // -------- Datos del cliente --------
    val cliente = remember { mutableStateOf<ClienteEntity?>(null) }
    LaunchedEffect(clienteId, repository, isPreview) {
        cliente.value = if (isPreview) {
            ClienteEntity(
                id = clienteId,
                nombreNegocio = "Negocio Preview",
                nombreDueno = "Juan Pérez",
                telefono = "",
                correo = "",
                tipoExhibidor = "",
                ubicacionLat = 0.0,
                ubicacionLon = 0.0,
                fotografiaUrl = null,
                activo = true,
                medio = "",
                fechaDeCreacion = System.currentTimeMillis(),
                syncStatus = true
            )
        } else {
            repository?.obtenerClientesLocalPorId(clienteId)
        }
    }

    // -------- Productos --------
    val productosList: List<Plantilla_Producto> = if (isPreview) {
        productosPreview ?: listOf(
            Plantilla_Producto("001", "Leche NutriLeche", 22.0, 16, cantidadDisponible = 16),
            Plantilla_Producto("002", "Papas Fritas", 25.0, 2, cantidadDisponible = 2)
        )
    } else {
        inventarioRepo?.obtenerProductosLocal()?.collectAsState(initial = emptyList())?.value
            ?.map { entidad ->
                Plantilla_Producto(
                    id = entidad.id,
                    nombre = entidad.nombre,
                    precio = entidad.precio,
                    cantidad = 0,
                    cantidadDisponible = entidad.cantidadDisponible,  // 🔹 ESTO
                    imagenUrl = entidad.imagenUrl ?: ""
                )
            } ?: emptyList()
    }


    val listaProductos = remember { mutableStateListOf<Plantilla_Producto>() }
    LaunchedEffect(productosList) {
        listaProductos.clear()
        listaProductos.addAll(productosList.map { it.copy(cantidad = 0, cantidadDisponible = it.cantidadDisponible) })

    }

    val totalGeneral by remember { derivedStateOf { listaProductos.sumOf { it.precio * it.cantidad } } }
    val formatoMoneda = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("es", "MX"))
    var mostrarDialog by remember { mutableStateOf(false) }





    // -------- UI --------
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {

        // LazyColumn con cliente y productos
        LazyColumn(
            contentPadding = PaddingValues(top = 16.dp, bottom = 140.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Cliente
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Foto del cliente (si existe)
                        cliente.value?.fotografiaUrl?.let { fotoPath ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(fotoPath)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Foto del cliente",
                                modifier = Modifier
                                    .size(64.dp) // más pequeño porque va en fila
                                    .clip(CircleShape)
                                    .border(2.dp, Color.Gray, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } ?: run {
                            // Si no hay foto, mostrar un ícono por defecto
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Cliente sin foto",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.LightGray)
                                    .padding(8.dp),
                                tint = Color.DarkGray
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = cliente.value?.nombreDueno ?: "Cargando...",
                                fontSize = 22.sp,  // 👈 aquí defines el tamaño
                                fontWeight = FontWeight.Bold // opcional para hacerlo más notorio
                            )
                            Text(
                                text = cliente.value?.nombreNegocio ?: "",
                                fontSize = 16.sp, // un poco más pequeño
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // --- Mensajes / estado centrados correctamente ---
            if (!rutaCargada) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxHeight()   // <--- ocupa todo el alto visible dentro de LazyColumn
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFF0000))
                    }
                }
            } else if (rutaAsignada == null) {
                // 2) Usuario sin ruta asignada
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxHeight()   // <--- ocupa todo el alto visible dentro de LazyColumn
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Usuario sin ruta asignada",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else if (listaProductos.isEmpty()) {
                // 3) Tiene ruta pero no hay productos locales
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxHeight()   // <--- ocupa todo el alto visible dentro de LazyColumn
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aún no tienes productos en tu almacén",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                // 4) Estado normal: iterar productos (tu bloque existente por producto)

                // Productos
                items(listaProductos.size) { index ->
                    val producto = listaProductos[index]

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            if (isPreview) {
                                Image(
                                    painter = painterResource(R.drawable.repartidor),
                                    contentDescription = producto.nombre,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                AsyncImage(
                                    model = producto.imagenUrl,
                                    contentDescription = producto.nombre,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = producto.nombre,
                                    fontSize = 18.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Precio: $${producto.precio}",
                                    fontSize = 14.sp,
                                    color = Color(0xFF555555)
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Subtotal: ${formatoMoneda.format(producto.precio * producto.cantidad)}",
                                    fontSize = 14.sp,
                                    color = Color(0xFFFF0000)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                                Spacer(modifier = Modifier.height(10.dp))

                                ContadorCantidad(

                                    cantidad = producto.cantidad,
                                    onSumar = {
                                        if (producto.cantidad < producto.cantidadDisponible) {
                                            listaProductos[index] = producto.copy(cantidad = producto.cantidad + 1)

                                        } else {

                                            // Lanzar Snackbar desde un callback usando scope.launch
                                            scope.launch {
                                                snackbarHostState.showSnackbar("No hay suficiente inventario")
                                            }
                                        }
                                    },

                                    onRestar = { if (producto.cantidad > 0) listaProductos[index] = producto.copy(cantidad = producto.cantidad - 1) },

                                    onCantidadCambiada = { nuevaCantidad ->
                                        val cantidadSegura = nuevaCantidad.coerceIn(0, producto.cantidadDisponible)
                                        listaProductos[index] = producto.copy(cantidad = cantidadSegura)
                                    }

                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "${producto.cantidadDisponible}",
                                    fontSize = 14.sp,
                                    color = Color(0xFFFF0000),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )

                                Text(
                                    text = "Disponible",
                                    fontSize = 12.sp,
                                    color = Color(0xFF555555)
                                )
                            }
                        }
                    }
                }
            }
        }




        // Total fijo
        Card(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total General:",
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatoMoneda.format(totalGeneral),
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color(0xFFFF0000)
                )
            }
        }

        // Botón venta (Preview no ejecuta lógica real)
        FloatingActionButton(
            onClick = { if (!isPreview) mostrarDialog = true },
            containerColor = Color(0xFFFF0000),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 6.dp)
        ) {
            Icon(Icons.Default.ReceiptLong, contentDescription = "Finalizar venta")
        }





        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp), // ajusta altura
            snackbar = { data ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f) // hace el Snackbar 70% del ancho de la pantalla
                ) {
                    Snackbar(
                        containerColor = Color.Red,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        action = {}, // opcional
                        modifier = Modifier.fillMaxWidth() // ocupa todo el ancho del Box
                    ) {
                        Text(
                            text = data.visuals.message,
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,  // tamaño grande
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Cerrar automáticamente después de 1.5 segundos
                LaunchedEffect(data) {
                    kotlinx.coroutines.delay(1000)
                    data.dismiss()
                }
            }
        )









        // ---------- Confirmación venta ----------
        if (mostrarDialog) {
            AlertDialog(
                onDismissRequest = { mostrarDialog = false },
                containerColor = Color.White,
                title = {
                    Text(
                        text = "Confirmación",
                        color = Color.Red,
                        fontSize = 30.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Text(
                        text = "¿Deseas realizar la venta?",
                        fontSize = 18.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },





                confirmButton = {
                    Button(
                        onClick = {
                            if (isPreview) {
                                mostrarDialog = false
                                return@Button
                            }

                            val productosActualizados = listaProductos.filter { it.cantidad > 0 }


                            if (productosActualizados.isEmpty()) {

                                mostrarSnackbar(scope, snackbarHostState, "No hay productos seleccionados")
                                Toast.makeText(context, "No hay productos seleccionados", Toast.LENGTH_SHORT).show()


                                return@Button
                            }

                            mostrarDialog = false

                            val uidVendedor = FirebaseAuth.getInstance().currentUser?.uid ?: return@Button
                            val clienteNombre = cliente.value?.nombreDueno ?: "Cliente"
                            val clienteId = cliente.value?.id ?: ""
                            val metodoPago = "Efectivo"

                            if (vistaModeloVenta != null) {
                                scope.launch {

                                    // Guardar venta local
                                    vistaModeloVenta.guardarVentaLocal(
                                        clienteId = clienteId,
                                        clienteNombre = clienteNombre,
                                        productos = productosActualizados,
                                        metodoPago = metodoPago,
                                        vendedorId = uidVendedor,
                                        clienteImagenUrl = cliente.value?.fotografiaUrl
                                    ) { exitoLocal, ventaLocalId ->

                                        if (!exitoLocal) {


                                            mostrarSnackbar(scope, snackbarHostState, "Error guardando venta local")
                                            Toast.makeText(context, "Error guardando venta local", Toast.LENGTH_LONG).show()

                                            return@guardarVentaLocal
                                        }

                                        mostrarSnackbar(scope, snackbarHostState, "Venta guardada localmente")
                                        Toast.makeText(context, "Venta guardada localmente (ID: $ventaLocalId)", Toast.LENGTH_SHORT).show()




                                        // ---------- Actualizar inventario ----------
                                        scope.launch(Dispatchers.IO) {
                                            inventarioRepo?.let {
                                                productosActualizados.forEach { producto ->
                                                    try {
                                                        inventarioRepo.actualizarCantidadProducto(producto.id, producto.cantidad)
                                                    } catch (e: Exception) { e.printStackTrace() }
                                                }
                                            }
                                        }



                                        // ---------- Sincronizar con servidor ----------
                                        if (hayInternet(context)) {
                                            scope.launch(Dispatchers.IO) {
                                                val almacenId = vistaModeloVenta.obtenerAlmacenVendedor(uidVendedor) ?: return@launch

                                                val (exitoServidor, respuestaServidor) = guardarVentaEnServidorSuspend(
                                                    ventaLocalId, clienteId, clienteNombre, productosActualizados,
                                                    metodoPago, uidVendedor, almacenId
                                                )

                                                if (exitoServidor) {
                                                    val firestoreId = try { JSONObject(respuestaServidor).optString("ventaId", null) } catch (e: Exception) { null }

                                                    firestoreId?.let {
                                                        vistaModeloVenta.marcarVentaSincronizada(ventaLocalId, it)
                                                        scope.launch(Dispatchers.Main) {
                                                            Toast.makeText(context, "Venta sincronizada", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } ?: run {
                                                        scope.launch(Dispatchers.Main) {
                                                            mostrarSnackbar(scope, snackbarHostState, "No se recibió ID de Firestore, venta pendiente")
                                                            Toast.makeText(context, "No se recibió ID de Firestore, venta pendiente", Toast.LENGTH_LONG).show()

                                                        }
                                                    }
                                                } else {
                                                    scope.launch(Dispatchers.Main) {
                                                        mostrarSnackbar(scope, snackbarHostState, "Error sincronizando: $respuestaServidor")
                                                        Toast.makeText(context, "Error sincronizando: $respuestaServidor", Toast.LENGTH_LONG).show()

                                                    }
                                                }
                                            }
                                        } else {
                                            mostrarSnackbar(scope, snackbarHostState, "Sin conexión: venta pendiente de sincronización")
                                            Toast.makeText(context, "Sin conexión: venta pendiente de sincronización", Toast.LENGTH_SHORT).show()

                                        }




                                        // ---------- Generar PDF ----------
                                        scope.launch(Dispatchers.IO) {
                                            try {
                                                val pdfFile = GeneraPDFdeVenta(context, clienteNombre, productosActualizados, totalGeneral)
                                                val pdfUri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.provider",
                                                    pdfFile
                                                )
                                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                    setDataAndType(pdfUri, "application/pdf")
                                                    flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                scope.launch(Dispatchers.Main) {

                                                    mostrarSnackbar(scope, snackbarHostState, "No se pudo abrir el PDF")
                                                    Toast.makeText(context, "No se pudo abrir el PDF", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }



                                        // ---------- Imprimir ticket ----------
                                        if (impresoraBluetooth != null) {
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    ImprimirTicket58mm(
                                                        device = impresoraBluetooth,
                                                        context = context,
                                                        logoDrawableId = R.drawable.logo,
                                                        cliente = clienteNombre,
                                                        productos = productosActualizados
                                                    )

                                                    // Toast de éxito
                                                    withContext(Dispatchers.Main) {

                                                        mostrarSnackbar(scope, snackbarHostState, "Ticket impreso correctamente")
                                                        Toast.makeText(context, "Ticket impreso correctamente", Toast.LENGTH_SHORT).show()

                                                    }

                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    // Toast de error
                                                    withContext(Dispatchers.Main) {
                                                        mostrarSnackbar(scope, snackbarHostState, "Error al imprimir ticket: ${e.message}")
                                                        Toast.makeText(context, "Error al imprimir ticket: ${e.message}", Toast.LENGTH_LONG).show()

                                                    }
                                                }
                                            }
                                        } else {
                                            // Toast si no hay impresora seleccionada
                                            mostrarSnackbar(scope, snackbarHostState, "No hay impresora seleccionada")
                                            Toast.makeText(context, "No hay impresora seleccionada", Toast.LENGTH_SHORT).show()

                                        }






                                        // ---------- Limpiar cantidades en UI ----------
                                        listaProductos.replaceAll { it.copy(cantidad = 0) }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "CONFIRMAR", fontWeight = FontWeight.Bold)
                    }
                }









                ,
                dismissButton = {
                    Button(
                        onClick = { mostrarDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0), contentColor = Color.Gray),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(text = "CANCELAR", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            )
        }




    }
}

fun mostrarSnackbar(scope: CoroutineScope, snackbarHostState: SnackbarHostState, mensaje: String) {
    scope.launch {
        snackbarHostState.showSnackbar(
            message = mensaje,
            duration = SnackbarDuration.Short
        )
    }
}


suspend fun guardarVentaEnServidorSuspend(
    ventaLocalId: Long,
    clienteId: String,
    clienteNombre: String,
    productos: List<Plantilla_Producto>,
    metodoPago: String,
    vendedorId: String,
    almacenVendedorId: String
): Pair<Boolean, String> {
    val url = "https://us-central1-appventas--san-angel.cloudfunctions.net/registrarVenta"
    val client = OkHttpClient()

    val json = JSONObject().apply {
        put("ventaLocalId", ventaLocalId)
        put("clienteId", clienteId)
        put("clienteNombre", clienteNombre)
        put("productos", JSONArray().apply {
            productos.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("nombre", p.nombre)
                    put("precio", p.precio)
                    put("cantidad", p.cantidad)
                    put("imagenUrl", p.imagenUrl ?: "")
                })
            }
        })
        put("metodoPago", metodoPago)
        put("vendedorId", vendedorId)
        put("almacenVendedorId", almacenVendedorId)
    }

    val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder().url(url).post(body).build()

    return try {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val respBody = response.body?.string() ?: ""
            Pair(true, respBody)
        } else {
            Pair(false, "Error ${response.code}: ${response.message}")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(false, e.message ?: "Error desconocido")
    }
}











// ----------------------------
// Contadores de cantidad
// ----------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContadorCantidad(cantidad: Int, onSumar: () -> Unit, onRestar: () -> Unit, onCantidadCambiada: (Int) -> Unit = {}) {
    var textoCantidad by remember { mutableStateOf(cantidad.toString()) }
    LaunchedEffect(cantidad) { if (textoCantidad != cantidad.toString()) textoCantidad = cantidad.toString() }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { if (textoCantidad.toInt() > 0) onRestar() }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, contentDescription = "Restar", tint = Color(0xFFD32F2F), modifier = Modifier.size(24.dp)) }
        BasicTextField(
            value = textoCantidad,
            onValueChange = { nuevo -> val num = nuevo.filter { it.isDigit() }; textoCantidad = if (num.isEmpty()) "0" else num; onCantidadCambiada(textoCantidad.toInt()) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.width(65.dp).height(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF0000)).padding(vertical = 4.dp)
        )
        IconButton(onClick = onSumar, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, contentDescription = "Sumar", tint = Color(0xFF388E3C), modifier = Modifier.size(24.dp)) }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContadorDummy(cantidad: Int, onSumar: () -> Unit, onRestar: () -> Unit, onCantidadCambiada: (Int) -> Unit = {}) {
    var textoCantidad by remember { mutableStateOf(cantidad.toString()) }
    LaunchedEffect(cantidad) { if (textoCantidad != cantidad.toString()) textoCantidad = cantidad.toString() }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        IconButton(onClick = { if (textoCantidad.toInt() > 0) onRestar() }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Remove, contentDescription = "Restar", tint = Color(0xFFD32F2F), modifier = Modifier.size(14.dp)) }
        BasicTextField(
            value = textoCantidad,
            onValueChange = { nuevo -> val num = nuevo.filter { it.isDigit() }; textoCantidad = if (num.isEmpty()) "0" else num; onCantidadCambiada(textoCantidad.toInt()) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.width(50.dp).height(25.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Color.Red, RoundedCornerShape(8.dp)).padding(vertical = 4.dp)
        )
        IconButton(onClick = onSumar, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, contentDescription = "Sumar", tint = Color(0xFF388E3C), modifier = Modifier.size(14.dp)) }
    }
}






// ----------------------------
// Preview de PantallaVenta
// ----------------------------
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PantallaVentaPreview() {
    val productosPreview = listOf(
        Plantilla_Producto("001", "Leche NutriLeche", 22.0, 16),
        Plantilla_Producto("002", "Papas Fritas", 25.0, 2)
    )

    val fakeNavController = androidx.navigation.compose.rememberNavController()

    PantallaVenta(
        navController = fakeNavController,
        clienteId = "preview-1",
        repository = null,
        inventarioRepo = null,
        productosPreview = productosPreview
    )
}


