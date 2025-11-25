package com.gruposanangel.delivery.ui.screens

import ProductoTicketDetalle
import TicketVentaCompleto
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.data.VentaRepository
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.TimeZone


// ViewModel para manejar la lógica de ventas, incluyendo carga de productos y sincronización con Firestore.
class VistaModeloVenta(

    private val repositoryInventario: RepositoryInventario,
    private val ventaRepository: VentaRepository
) : ViewModel() {




    private val firestore = FirebaseFirestore.getInstance()

    private val _productos = MutableStateFlow<List<Plantilla_Producto>>(emptyList())
    val productos: StateFlow<List<Plantilla_Producto>> get() = _productos

    init {
        cargarProductos()
    }



    private val _ventasPeriodo = MutableStateFlow<List<VentaEntity>>(emptyList())
    val ventasPeriodo: StateFlow<List<VentaEntity>> = _ventasPeriodo

    fun cargarVentasPorPeriodo(fechaInicio: Date, fechaFin: Date) {
        viewModelScope.launch {
            val ventas = ventaRepository.obtenerVentasPorPeriodo(fechaInicio.time, fechaFin.time)
            _ventasPeriodo.value = ventas
        }
    }







    // Carga productos desde Firestore y los expone a través de un StateFlow.
    private fun cargarProductos() {
        firestore.collection("producto")
            .get()
            .addOnSuccessListener { result ->
                val lista = result.map { doc ->
                    Plantilla_Producto(
                        id = doc.id,
                        nombre = doc.getString("nombre") ?: "",
                        precio = doc.getDouble("precio") ?: 0.0,
                        cantidad = 0,
                        imagenUrl = doc.getString("imagenUrl") ?: ""
                    )
                }
                _productos.value = lista
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }


    // Guarda una venta localmente en Room y retorna el ID generado.
    fun guardarVentaLocal(
        clienteId: String,
        clienteNombre: String,
        clienteImagenUrl: String?,
        productos: List<Plantilla_Producto>,
        metodoPago: String,
        vendedorId: String,
        onResult: (Boolean, Long) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val total = productos.sumOf { it.precio * it.cantidad }
                val ventaId = ventaRepository.guardarVentaLocal(clienteId, clienteNombre, clienteImagenUrl,productos, total, metodoPago, vendedorId)
                onResult(true, ventaId)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, -1)
            }
        }
    }






    suspend fun obtenerTicketDirecto(ticketId: Long): TicketVentaCompleto? {
        // Obtenemos la venta usando el método existente en el repositorio
        val venta = ventaRepository.obtenerVentaPorId(ticketId) ?: return null

        // Obtenemos los detalles de la venta
        val detalles = ventaRepository.obtenerDetallesDeVenta(ticketId)

        // Mapear los detalles a productos para el TicketVentaCompleto
        val productos = detalles.map { detalle ->
            ProductoTicketDetalle(
                nombre = detalle.nombre,
                cantidad = detalle.cantidad,
                precio = detalle.precio
            )
        }

        return TicketVentaCompleto(
            numeroTicket = venta.id.toString(),
            cliente = venta.clienteNombre,
            total = venta.total,
            fecha = Date(venta.fecha),
            sincronizado = venta.sincronizado,
            fotoCliente = venta.clienteImagenUrl ?: "",
            productos = productos
        )
    }










    // Obtiene los detalles de una venta específica por su ID.
    suspend fun obtenerDetallesDeVentaSuspend(ticketId: Long) =
        ventaRepository.obtenerDetallesDeVenta(ticketId)




    // Carga ventas del día actual.
    fun cargarVentasHoy() {
        val tz = TimeZone.getDefault() // usa la misma zona horaria del dispositivo
        val calendar = Calendar.getInstance(tz)

        // Inicio del día
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val inicio = calendar.timeInMillis

        // Fin del día
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val fin = calendar.timeInMillis

        // Llama al repositorio usando los timestamps correctos
        cargarVentasPorPeriodo(Date(inicio), Date(fin))
    }




    //
    suspend fun marcarVentaSincronizada(ventaId: Long, firestoreId: String) {
        ventaRepository.marcarVentaConFirestoreId(ventaId, firestoreId)
    }





    suspend fun obtenerAlmacenVendedor(uid: String): String? {
        return repositoryInventario.getAlmacenVendedor(uid)
    }





    // Guarda una venta directamente en Firestore, actualiza inventario y crea movimientos.
    fun nuevaguardarVentaFirestoreCompletalecambie(
        ventaLocalId: Long,
        clienteId: String,
        clienteNombre: String,
        productos: List<Plantilla_Producto>,
        metodoPago: String,
        vendedorId: String,
        almacenVendedorId: String, // <-- ID del almacén del vendedor
        onResult: (Boolean, String) -> Unit
    ) {
        val ventaRef = firestore.collection("ventas").document() // ID único global

        val total = productos.sumOf { it.precio * it.cantidad }
        val totalPiezas = productos.sumOf { it.cantidad }

        val ventaData = mapOf(
            "clienteId" to clienteId,
            "clienteNombre" to clienteNombre,
            "localId" to ventaLocalId,
            "total" to total,
            "totalPiezas" to totalPiezas,
            "fecha" to Timestamp.now(),
            "metodoPago" to metodoPago,
            "vendedorId" to vendedorId,
            "sincronizado" to true,
            "estado" to "pagada",
            "comentarios" to ""
        )

        firestore.runTransaction { transaction ->

            // 1️⃣ Crear venta
            transaction.set(ventaRef, ventaData)

            // 2️⃣ Crear productos de la venta
            productos.forEach { producto ->
                val productoDocRef = ventaRef.collection("productos").document(producto.id)
                transaction.set(productoDocRef, mapOf(
                    "nombre" to producto.nombre,
                    "precio" to producto.precio,
                    "cantidad" to producto.cantidad,
                    "imagenUrl" to producto.imagenUrl
                ))

                // 3️⃣ Restar inventario del vendedor
                val stockRef = firestore.collection("inventarioStock")
                    .document("${producto.id}_$almacenVendedorId")
                val stockSnap = transaction.get(stockRef)
                if (!stockSnap.exists()) {
                    throw Exception("Stock no existe para ${producto.nombre} en el almacén del vendedor")
                }
                val stockActual = stockSnap.getLong("cantidad") ?: 0
                if (stockActual < producto.cantidad) {
                    throw Exception("Stock insuficiente para ${producto.nombre}")
                }

                transaction.update(stockRef, "cantidad", stockActual - producto.cantidad)
                transaction.update(stockRef, "ultimaActualizacion", FieldValue.serverTimestamp())

                // 4️⃣ Crear movimiento
                val movimientoRef = firestore.collection("movimientosStock").document()
                transaction.set(movimientoRef, mapOf(
                    "tipoMovimiento" to "VENTA",
                    "productoRef" to firestore.collection("producto").document(producto.id),
                    "productoNombre" to producto.nombre,
                    "precioUnitario" to producto.precio,
                    "cantidad" to producto.cantidad,
                    "almacenRef" to firestore.collection("almacenes").document(almacenVendedorId),
                    "almacenNombre" to almacenVendedorId,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "vendedorId" to vendedorId,
                    "clienteId" to clienteId,
                    "ventaId" to ventaRef.id
                ))
            }

            // 5️⃣ Retornar el ID de la venta
            ventaRef.id
        }.addOnSuccessListener { ventaId ->
            // Marcar la venta local con el ID de Firestore
            viewModelScope.launch {
                ventaRepository.marcarVentaConFirestoreId(ventaLocalId, ventaId)
            }
            onResult(true, ventaId)
        }.addOnFailureListener { e ->
            e.printStackTrace()
            onResult(false, e.message ?: "Error subiendo a Firestore")
        }
    }






    fun nuevaguardarVentaFirestoreCompleta(
        ventaLocalId: Long,
        clienteId: String,
        clienteNombre: String,
        productos: List<Plantilla_Producto>,
        metodoPago: String,
        vendedorId: String,
        almacenVendedorId: String, // ID del almacén del vendedor
        onResult: (Boolean, String) -> Unit
    ) {
        val ventaRef = firestore.collection("ventas").document()
        val almacenIdLimpio = almacenVendedorId.trim()

        val total = productos.sumOf { it.precio * it.cantidad }
        val totalPiezas = productos.sumOf { it.cantidad }

        val ventaData = mapOf(
            "clienteId" to clienteId,
            "clienteNombre" to clienteNombre,
            "localId" to ventaLocalId,
            "total" to total,
            "totalPiezas" to totalPiezas,
            "fecha" to Timestamp.now(),
            "metodoPago" to metodoPago,
            "vendedorId" to vendedorId,
            "sincronizado" to true,
            "estado" to "pagada",
            "comentarios" to ""
        )

        firestore.runTransaction { transaction ->

            Log.d("FirestoreVenta", "🚀 Iniciando transacción ventaId=${ventaRef.id}")

            // 🔹 1️⃣ Primero, leer todos los stocks
            val stockMap = mutableMapOf<String, Long>() // docId -> cantidad actual
            for (producto in productos) {
                val productIdLimpio = producto.id.split("_")[0] // eliminar posible concatenación previa
                val stockDocId = "${productIdLimpio}_$almacenIdLimpio"
                val stockRef = firestore.collection("inventarioStock").document(stockDocId)

                Log.e("FirestoreVenta", "🔍 Leyendo stock: $stockDocId (producto=${producto.nombre})")
                val stockSnap = transaction.get(stockRef)

                if (!stockSnap.exists()) {
                    Log.e("FirestoreVenta", "❌ Stock no existe para ${producto.nombre} en [$almacenIdLimpio]")
                    throw Exception("Stock no existe para ${producto.nombre} en el almacén del vendedor")
                }

                val stockActual = stockSnap.getLong("cantidad") ?: 0
                if (stockActual < producto.cantidad) {
                    Log.e("FirestoreVenta", "⚠️ Stock insuficiente para ${producto.nombre}: actual=$stockActual, requerido=${producto.cantidad}")
                    throw Exception("Stock insuficiente para ${producto.nombre}")
                }

                stockMap[stockDocId] = stockActual
            }

            // 🔹 2️⃣ Crear venta
            transaction.set(ventaRef, ventaData)
            Log.d("FirestoreVenta", "✅ Venta creada en ${ventaRef.id}")

            // 🔹 3️⃣ Crear productos y actualizar stocks
            for (producto in productos) {
                val productIdLimpio = producto.id.split("_")[0]
                val stockDocId = "${productIdLimpio}_$almacenIdLimpio"
                val stockRef = firestore.collection("inventarioStock").document(stockDocId)

                // Productos dentro de la venta
                val productoDocRef = ventaRef.collection("productos").document(productIdLimpio)
                transaction.set(productoDocRef, mapOf(
                    "nombre" to producto.nombre,
                    "precio" to producto.precio,
                    "cantidad" to producto.cantidad,
                    "imagenUrl" to producto.imagenUrl
                ))
                Log.d("FirestoreVenta", "📦 Producto agregado a venta: ${producto.nombre} x${producto.cantidad}")

                // Actualizar stock
                val stockActual = stockMap[stockDocId]!!
                transaction.update(stockRef, "cantidad", stockActual - producto.cantidad)
                transaction.update(stockRef, "ultimaActualizacion", FieldValue.serverTimestamp())
                Log.d("FirestoreVenta", "✅ Stock actualizado: ${producto.nombre} $stockActual → ${stockActual - producto.cantidad}")

                // Crear movimiento
                val movimientoRef = firestore.collection("movimientosStock").document()
                transaction.set(movimientoRef, mapOf(
                    "tipoMovimiento" to "VENTA",
                    "productoRef" to firestore.collection("producto").document(productIdLimpio),
                    "productoNombre" to producto.nombre,
                    "precioUnitario" to producto.precio,
                    "cantidad" to producto.cantidad,
                    "almacenRef" to firestore.collection("almacenes").document(almacenIdLimpio),
                    "almacenNombre" to almacenIdLimpio,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "vendedorId" to vendedorId,
                    "clienteId" to clienteId,
                    "ventaId" to ventaRef.id
                ))
                Log.d("FirestoreVenta", "📝 Movimiento creado para ${producto.nombre}")
            }

            ventaRef.id
        }.addOnSuccessListener { ventaId ->
            Log.d("FirestoreVenta", "🎉 Transacción completada con éxito, ventaId=$ventaId")
            viewModelScope.launch {
                ventaRepository.marcarVentaConFirestoreId(ventaLocalId, ventaId)
            }
            onResult(true, ventaId)
        }.addOnFailureListener { e ->
            Log.e("FirestoreVenta", "❌ Error en transacción Firestore", e)
            onResult(false, e.message ?: "Error subiendo a Firestore")
        }
    }




    fun guardarVentaEnServidor(
        ventaLocalId: Long,
        clienteId: String,
        clienteNombre: String,
        productos: List<Plantilla_Producto>,
        metodoPago: String,
        vendedorId: String,
        almacenVendedorId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        // Construir JSON
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

        // URL de la Cloud Function desplegada
        val url = "https://us-central1-appventas--san-angel.cloudfunctions.net/registrarVenta"

        // Preparar OkHttp
        val client = OkHttpClient()
        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            // .addHeader("x-api-key", "TU_API_KEY") // descomenta si usas API key
            .build()

        // Ejecutar llamada asíncrona
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(false, e.message ?: "Error de red")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val respJson = JSONObject(response.body?.string() ?: "{}")
                    val ventaId = respJson.optString("ventaId")
                    onResult(true, ventaId)
                } else {
                    onResult(false, response.message)
                }
            }
        })
    }









    // Sincroniza todas las ventas pendientes en Room con Firestore.
    fun sincronizarVentasPendientes() {
        viewModelScope.launch {
            val pendientes = ventaRepository.obtenerVentasPendientes()
            val uidVendedor = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val almacenId = repositoryInventario.getAlmacenVendedor(uidVendedor) ?: return@launch

            pendientes.forEach { venta ->
                val detalles = ventaRepository.obtenerDetallesDeVenta(venta.id)
                val productos = detalles.map {
                    Plantilla_Producto(it.productoId, it.nombre, it.precio, it.cantidad)
                }

                nuevaguardarVentaFirestoreCompleta(
                    ventaLocalId = venta.id,
                    clienteId = venta.clienteId,
                    clienteNombre = venta.clienteNombre,
                    productos = productos,
                    metodoPago = venta.metodoPago,
                    vendedorId = venta.vendedorId,
                    almacenVendedorId = almacenId
                ) { exito, firestoreId ->
                    if (exito) Log.d("VentasPendientes", "Venta ${venta.id} sincronizada con $firestoreId")
                    else Log.e("VentasPendientes", "Error sincronizando venta ${venta.id}")
                }
            }
        }
    }




    // Sube una venta y sus productos a Firestore, luego marca la venta local como sincronizada.
    private suspend fun subirVentaYMarcarComoSincronizada(
        venta: VentaEntity,
        productos: List<Plantilla_Producto>
    ): Boolean = suspendCancellableCoroutine { cont ->
        val ventaRef = FirebaseFirestore.getInstance().collection("ventas").document()

        val total = productos.sumOf { it.precio * it.cantidad }
        val totalPiezas = productos.sumOf { it.cantidad }

        val ventaData = mapOf(
            "clienteId" to venta.clienteId,
            "clienteNombre" to venta.clienteNombre,
            "localId" to venta.id,
            "total" to total,
            "totalPiezas" to totalPiezas,
            "fecha" to Timestamp.now(),
            "metodoPago" to venta.metodoPago,
            "vendedorId" to venta.vendedorId,
            "sincronizado" to true,  // Firestore siempre marca como sincronizada
            "estado" to "pagada",
            "comentarios" to ""
        )

        ventaRef.set(ventaData)
            .addOnSuccessListener {
                // Subir productos
                val batch = FirebaseFirestore.getInstance().batch()
                productos.forEach { producto ->
                    val prodRef = ventaRef.collection("productos").document(producto.id)
                    batch.set(prodRef, mapOf(
                        "nombre" to producto.nombre,
                        "precio" to producto.precio,
                        "cantidad" to producto.cantidad,
                        "imagenUrl" to producto.imagenUrl
                    ))
                }

                // Ejecutar batch
                batch.commit()
                    .addOnSuccessListener {
                        // Marcar la venta local como sincronizada UNA VEZ
                        viewModelScope.launch {
                            ventaRepository.marcarVentaConFirestoreId(venta.id, ventaRef.id)
                            cont.resume(true) {}
                        }
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        cont.resume(false) {}
                    }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                cont.resume(false) {}
            }
    }





    // Función suspend para llamar a la Cloud Function y obtener resultado
    suspend fun guardarVentaEnServidorSuspend(
        ventaLocalId: Long,
        clienteId: String,
        clienteNombre: String,
        productos: List<Plantilla_Producto>,
        metodoPago: String,
        vendedorId: String,
        almacenVendedorId: String
    ): Pair<Boolean, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val url = "https://us-central1-appventas--san-angel.cloudfunctions.net/registrarVenta"
            val client = okhttp3.OkHttpClient()

            val json = org.json.JSONObject().apply {
                put("ventaLocalId", ventaLocalId)
                put("clienteId", clienteId)
                put("clienteNombre", clienteNombre)
                put("productos", org.json.JSONArray().apply {
                    productos.forEach { p ->
                        put(org.json.JSONObject().apply {
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

            val body = json.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = okhttp3.Request.Builder()
                .url(url)
                .post(body)
                .build()

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







    suspend fun obtenerVentaEntityPorId(ticketId: Long): VentaEntity? {
        return ventaRepository.obtenerVentaPorId(ticketId)
    }










}

