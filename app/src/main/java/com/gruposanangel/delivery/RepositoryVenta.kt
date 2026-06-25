package com.gruposanangel.delivery

import ProductoTicketDetalle
import TicketVentaCompleto
import android.util.Log
import com.gruposanangel.delivery.data.VentaDao
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.data.VentaDetalleEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import java.util.UUID

class VentaRepository(
    private val ventaDao: VentaDao,
    private val productoDao: com.gruposanangel.delivery.data.ProductoDao
) {

    suspend fun obtenerVentasPorPeriodo(vendedorId: String, inicio: Long, fin: Long): List<VentaEntity> =
        withContext(Dispatchers.IO) {
            if (vendedorId.isEmpty()) {
                ventaDao.obtenerTodasVentasPorPeriodo(inicio, fin)
            } else {
                ventaDao.obtenerVentasPorPeriodo(vendedorId, inicio, fin)
            }
        }

    fun obtenerVentasPorPeriodoFlow(vendedorId: String, inicio: Long, fin: Long): kotlinx.coroutines.flow.Flow<List<VentaEntity>> {
        return if (vendedorId.isEmpty()) {
            ventaDao.obtenerTodasVentasPorPeriodoFlow(inicio, fin)
        } else {
            ventaDao.obtenerVentasPorPeriodoFlow(vendedorId, inicio, fin)
        }
    }

    suspend fun obtenerTicketCompleto(ticketId: String): TicketVentaCompleto? {
        val ventaLocal = ventaDao.obtenerVentaPorId(ticketId)
        val detalles = ventaDao.obtenerDetallesPorVenta(ticketId)

        if ((ventaLocal != null) && detalles.isNotEmpty()) {
            val productos = detalles.map {
                ProductoTicketDetalle(
                    nombre = it.nombre,
                    cantidad = it.cantidad,
                    precio = it.precio,
                )
            }

            var fotoFinal = ventaLocal.clienteImagenUrl ?: ""

            if (fotoFinal.isEmpty() || !fotoFinal.startsWith("/")) {
                try {
                    val firestore = FirebaseFirestore.getInstance()
                    val clienteDoc = firestore.collection("clientes").document(ventaLocal.clienteId).get().await()
                    if (clienteDoc.exists()) {
                        fotoFinal = clienteDoc.getString("FotografiaCliente") ?: ""
                    }
                } catch (e: Exception) {
                    Log.w("VentaRepo", "No se pudo recuperar foto remota para ticket local $ticketId: ${e.message}")
                }
            }

            var vendedorNombre = "Vendedor"
            try {
                val firestore = FirebaseFirestore.getInstance()
                val vendedorDoc = firestore.collection("users").document(ventaLocal.vendedorId).get().await()
                if (vendedorDoc.exists()) {
                    vendedorNombre = vendedorDoc.getString("nombre") ?: "Vendedor"
                }
            } catch (e: Exception) {
                Log.w("VentaRepo", "No se pudo recuperar nombre del vendedor para ticket local $ticketId: ${e.message}")
            }

            return TicketVentaCompleto(
                numeroTicket = ventaLocal.id,
                cliente = ventaLocal.clienteNombre,
                total = ventaLocal.total,
                fecha = Date(ventaLocal.fecha),
                sincronizado = ventaLocal.sincronizado,
                fotoCliente = fotoFinal,
                productos = productos,
                vendedorNombre = vendedorNombre,
                fueraDeRango = ventaLocal.fueraDeRango,
                fotoEvidenciaUrl = ventaLocal.fotoEvidenciaVisita
            )
        }

        return obtenerTicketCompletoFirestore(ticketId)
    }

    suspend fun obtenerTicketCompletoFirestore(ticketId: String): TicketVentaCompleto? = withContext(Dispatchers.IO) {
        val firestore = FirebaseFirestore.getInstance()
        try {
            val doc = firestore.collection("ventas").document(ticketId).get().await()
            if (!doc.exists()) {
                Log.w("VentaRepo", "El ticket $ticketId no existe en Firestore")
                return@withContext null
            }

            val clienteId = when (val clienteRaw = doc.get("clienteId") ?: doc.get("clienteRef") ?: doc.get("id_cliente")) {
                is com.google.firebase.firestore.DocumentReference -> clienteRaw.id
                else -> clienteRaw?.toString() ?: ""
            }

            val vendedorId = when (val vendRaw = doc.get("vendedorId") ?: doc.get("vendedorRef")) {
                is com.google.firebase.firestore.DocumentReference -> vendRaw.id
                else -> vendRaw?.toString() ?: ""
            }

            val clienteNombre = doc.getString("clienteNombre") ?: "Cliente"
            val total = (doc.get("total") as? Number)?.toDouble() ?: 0.0
            val fecha = doc.getTimestamp("fecha")?.toDate() ?: Date()

            var fotoUrl = doc.getString("clienteImagenUrl") ?: doc.getString("FotografiaCliente") ?: ""

            if (fotoUrl.isEmpty() && clienteId.isNotEmpty()) {
                try {
                    val clienteDoc = firestore.collection("clientes").document(clienteId).get().await()
                    if (clienteDoc.exists()) {
                        fotoUrl = clienteDoc.getString("FotografiaCliente") ?: ""
                    }
                } catch (e: Exception) { }
            }

            var vendedorNombre = doc.getString("vendedorNombre") ?: "Vendedor"
            if ((vendedorNombre == "Vendedor") && vendedorId.isNotEmpty()) {
                try {
                    val vendedorDoc = firestore.collection("users").document(vendedorId).get().await()
                    if (vendedorDoc.exists()) {
                        vendedorNombre = vendedorDoc.getString("nombre") ?: "Vendedor"
                    }
                } catch (e: Exception) { }
            }

            val productosSnap = firestore.collection("ventas").document(ticketId).collection("productos").get().await()
            val productos = productosSnap.documents.map { pDoc ->
                ProductoTicketDetalle(
                    nombre = pDoc.getString("nombre") ?: "Producto",
                    cantidad = (pDoc.get("cantidad") as? Number)?.toInt() ?: 0,
                    precio = (pDoc.get("precio") as? Number)?.toDouble() ?: 0.0
                )
            }

            TicketVentaCompleto(
                numeroTicket = ticketId,
                cliente = clienteNombre,
                total = total,
                fecha = fecha,
                sincronizado = true,
                fotoCliente = fotoUrl,
                productos = productos,
                vendedorNombre = vendedorNombre,
                fueraDeRango = doc.getBoolean("fueraDeRango") ?: false,
                fotoEvidenciaUrl = doc.getString("fotoEvidenciaVisita")
            )
        } catch (e: Exception) {
            Log.e("VentaRepo", "Error crítico buscando ticket en Firestore", e)
            null
        }
    }

    suspend fun descargarVentasDia(vendedorId: String): List<VentaEntity> =
        withContext(Dispatchers.IO) {
            val firestore = FirebaseFirestore.getInstance()
            
            // 🛡️ Rango Ampliado: Traemos 3 días (Ayer, Hoy, Mañana) para evitar desfases de zona horaria
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -1) // Empezamos desde ayer
            calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
            val inicio = calendar.time

            val calendarFin = Calendar.getInstance()
            calendarFin.add(Calendar.DAY_OF_MONTH, 1) // Hasta mañana
            calendarFin.set(Calendar.HOUR_OF_DAY, 23); calendarFin.set(Calendar.MINUTE, 59); calendarFin.set(Calendar.SECOND, 59); calendarFin.set(Calendar.MILLISECOND, 999)
            val fin = calendarFin.time

            Log.d("VentaRepo", "📡 VentaRepo: Sincronizando ventana de 3 días para evitar desfases.")

            try {
                var query: com.google.firebase.firestore.Query = firestore.collection("ventas")
                    .whereGreaterThanOrEqualTo("fecha", inicio)
                    .whereLessThanOrEqualTo("fecha", fin)

                if (vendedorId.isNotEmpty()) {
                    query = query.whereEqualTo("vendedorId", vendedorId)
                }

                val snapshot = query.get().await()
                Log.i("VentaRepo", "📡 VentaRepo: Se encontraron ${snapshot.size()} ventas en la ventana de tiempo.")

                snapshot.documents.forEach { doc ->
                    try {
                        val localId = doc.getString("localId") ?: doc.id
                        val fRaw = doc.get("fecha")
                        val fechaFinal = when(fRaw) {
                            is com.google.firebase.Timestamp -> fRaw.toDate().time
                            is Number -> fRaw.toLong()
                            else -> System.currentTimeMillis()
                        }

                        val cId = when (val cRaw = doc.get("clienteId") ?: doc.get("clienteRef")) {
                            is com.google.firebase.firestore.DocumentReference -> cRaw.id
                            else -> cRaw?.toString() ?: ""
                        }

                        val vId = when (val vRaw = doc.get("vendedorId") ?: doc.get("vendedorRef")) {
                            is com.google.firebase.firestore.DocumentReference -> vRaw.id
                            else -> vRaw?.toString() ?: ""
                        }

                        val venta = VentaEntity(
                            id = localId,
                            clienteId = cId,
                            clienteNombre = doc.getString("clienteNombre") ?: "Cliente",
                            clienteImagenUrl = doc.getString("clienteImagenUrl"),
                            total = (doc.get("total") as? Number)?.toDouble() ?: 0.0,
                            metodoPago = doc.getString("metodoPago") ?: "Efectivo",
                            vendedorId = vId,
                            vendedorNombre = doc.getString("vendedorNombre"),
                            almacenId = doc.getString("almacenId") ?: doc.getString("almacenVendedorId"),
                            fecha = fechaFinal,
                            horaDispositivo = doc.getLong("horaDispositivo") ?: fechaFinal,
                            horaVerificada = doc.getLong("horaVerificada") ?: fechaFinal,
                            alertaTiempo = doc.getBoolean("alertaTiempo") ?: false,
                            latitudVenta = doc.getDouble("latitudVenta") ?: 0.0,
                            longitudVenta = doc.getDouble("longitudVenta") ?: 0.0,
                            fueraDeRango = doc.getBoolean("fueraDeRango") ?: false,
                            fotoEvidenciaVisita = doc.getString("fotoEvidenciaVisita"),
                            sincronizado = true,
                            firestoreId = doc.id
                        )

                        val detalles = mutableListOf<VentaDetalleEntity>()
                        try {
                            val prodsSnap = firestore.collection("ventas").document(doc.id).collection("productos").get().await()
                            prodsSnap.documents.forEach { pDoc ->
                                detalles.add(VentaDetalleEntity(
                                    ventaId = localId,
                                    productoId = pDoc.id,
                                    stockId = null,
                                    nombre = pDoc.getString("nombre") ?: "Producto",
                                    precio = (pDoc.get("precio") as? Number)?.toDouble() ?: 0.0,
                                    cantidad = (pDoc.getLong("cantidad") ?: 0L).toInt()
                                ))
                            }
                        } catch (e: Exception) { }

                        ventaDao.refrescarVentaCompleta(venta, detalles)
                    } catch (e: Exception) {
                        Log.e("VentaRepo", "❌ Error parseando ${doc.id}")
                    }
                }
            } catch (e: Exception) {
                Log.e("VentaRepo", "❌ Error Firebase: ${e.message}")
            }

            // Devolvemos todo lo que hay en local para este rango ampliado
            return@withContext if (vendedorId.isEmpty()) {
                ventaDao.obtenerTodasVentasPorPeriodo(inicio.time, fin.time)
            } else {
                ventaDao.obtenerVentasPorPeriodo(vendedorId, inicio.time, fin.time)
            }
        }

    suspend fun sincronizarVentasPeriodo(vendedorId: String, inicio: Long? = null, fin: Long? = null) =
        withContext(Dispatchers.IO) {
            val firestore = FirebaseFirestore.getInstance()
            try {
                var query: com.google.firebase.firestore.Query = firestore.collection("ventas")
                
                // Si el vendedorId no está vacío, filtramos por él (Vendedores)
                // Si está vacío, bajamos todo el periodo (Admins)
                if (vendedorId.isNotEmpty()) {
                    query = query.whereEqualTo("vendedorId", vendedorId)
                }
                
                if (inicio != null) query = query.whereGreaterThanOrEqualTo("fecha", Date(inicio))
                if (fin != null) query = query.whereLessThanOrEqualTo("fecha", Date(fin))

                val snapshot = query.get().await()
                Log.d("VentaRepo", "Sincronizando periodo: ${snapshot.size()} ventas encontradas.")

                snapshot.documents.forEach { doc ->
                    val localId = doc.getString("localId") ?: doc.id
                    val fRaw = doc.get("fecha")
                    val fechaFinal = when(fRaw) {
                        is com.google.firebase.Timestamp -> fRaw.toDate().time
                        is Number -> fRaw.toLong()
                        else -> System.currentTimeMillis()
                    }

                    val cId = when (val cRaw = doc.get("clienteId") ?: doc.get("clienteRef")) {
                        is com.google.firebase.firestore.DocumentReference -> cRaw.id
                        else -> cRaw?.toString() ?: ""
                    }

                    val vId = when (val vRaw = doc.get("vendedorId") ?: doc.get("vendedorRef")) {
                        is com.google.firebase.firestore.DocumentReference -> vRaw.id
                        else -> vRaw?.toString() ?: ""
                    }

                    val venta = VentaEntity(
                        id = localId,
                        clienteId = cId,
                        clienteNombre = doc.getString("clienteNombre") ?: "Cliente",
                        clienteImagenUrl = doc.getString("clienteImagenUrl"),
                        total = (doc.get("total") as? Number)?.toDouble() ?: 0.0,
                        metodoPago = doc.getString("metodoPago") ?: "Efectivo",
                        vendedorId = vId,
                        vendedorNombre = doc.getString("vendedorNombre"),
                        almacenId = doc.getString("almacenId") ?: doc.getString("almacenVendedorId"),
                        fecha = fechaFinal,
                        horaDispositivo = doc.getLong("horaDispositivo") ?: fechaFinal,
                        horaVerificada = doc.getLong("horaVerificada") ?: fechaFinal,
                        alertaTiempo = doc.getBoolean("alertaTiempo") ?: false,
                        latitudVenta = doc.getDouble("latitudVenta") ?: 0.0,
                        longitudVenta = doc.getDouble("longitudVenta") ?: 0.0,
                        fueraDeRango = doc.getBoolean("fueraDeRango") ?: false,
                        fotoEvidenciaVisita = doc.getString("fotoEvidenciaVisita"),
                        sincronizado = true,
                        firestoreId = doc.id
                    )

                    try {
                        val prodsSnap = firestore.collection("ventas").document(doc.id).collection("productos").get().await()
                        val detalles = prodsSnap.documents.map { pDoc ->
                            VentaDetalleEntity(
                                ventaId = localId,
                                productoId = pDoc.id,
                                nombre = pDoc.getString("nombre") ?: "Producto",
                                precio = (pDoc.get("precio") as? Number)?.toDouble() ?: 0.0,
                                cantidad = (pDoc.getLong("cantidad") ?: 0L).toInt()
                            )
                        }
                        ventaDao.refrescarVentaCompleta(venta, detalles)
                    } catch (e: Exception) {
                        Log.e("VentaRepo", "Error bajando productos para periodo venta $localId", e)
                        ventaDao.insertarVenta(venta)
                    }
                }
            } catch (e: Exception) {
                Log.e("VentaRepo", "Error en sincronizarVentasPeriodo: ${e.message}")
            }
        }

    suspend fun guardarVentaLocal(
        clienteId: String,
        clienteNombre: String,
        clienteImagenUrl: String?,
        productos: List<Plantilla_Producto>,
        total: Double, // Se ignora para el cálculo, se recalcula por seguridad
        metodoPago: String,
        vendedorId: String,
        vendedorNombre: String? = null,
        almacenId: String? = null,
        latitud: Double = 0.0,
        longitud: Double = 0.0,
        fueraDeRango: Boolean = false,
        fotoEvidencia: String? = null
    ): String = withContext(Dispatchers.IO) {
        val idLocal = UUID.randomUUID().toString()
        
        // 🔥 Auditoría de Tiempo Blindada
        val horaDispositivo = System.currentTimeMillis()
        val horaRealVerificada = com.gruposanangel.delivery.utilidades.TimeManager.getHoraReal()
        val alertaTiempo = Math.abs(horaRealVerificada - horaDispositivo) > 300_000 // Discrepancia > 5 min

        // 🛡️ BLINDAJE FINANCIERO: Mapeo de detalles con recuperación de PRECIO MAESTRO desde DB Local
        val detalles = productos.map { p ->
            val productoDB = productoDao.getProductoById(p.id)
            val precioReal = productoDB?.precio ?: 0.0 // 🛡️ Si no existe, precio 0 (Error de seguridad)
            
            val idLimpio = p.id.split("_")[0] 
            
            VentaDetalleEntity(
                ventaId = idLocal,
                productoId = idLimpio,
                stockId = p.id,
                nombre = productoDB?.nombre ?: p.nombre,
                precio = precioReal, // 🛡️ Asignación exclusiva desde DB Local (Inmutable)
                cantidad = p.cantidad
            )
        }

        // Recalcular total real basado en precios de DB
        val totalReal = detalles.sumOf { it.precio * it.cantidad }

        val venta = VentaEntity(
            id = idLocal,
            clienteId = clienteId,
            clienteNombre = clienteNombre,
            clienteImagenUrl = clienteImagenUrl,
            total = totalReal, // 🛡️ Total blindado
            metodoPago = metodoPago,
            vendedorId = vendedorId,
            vendedorNombre = vendedorNombre,
            almacenId = almacenId,
            fecha = horaRealVerificada, 
            horaDispositivo = horaDispositivo,
            horaVerificada = horaRealVerificada,
            alertaTiempo = alertaTiempo,
            latitudVenta = latitud,
            longitudVenta = longitud,
            fueraDeRango = fueraDeRango,
            fotoEvidenciaVisita = fotoEvidencia,
            sincronizado = false
        )

        ventaDao.insertarVentaYActualizarStock(venta, detalles)
        idLocal
    }

    suspend fun marcarVentaConFirestoreId(ventaLocalId: String, firestoreId: String) = withContext(Dispatchers.IO) {
        val venta = ventaDao.obtenerVentaPorId(ventaLocalId)
        if (venta != null) {
            val ventaActualizada = venta.copy(firestoreId = firestoreId, sincronizado = true)
            ventaDao.actualizarVenta(ventaActualizada)
        }
    }

    suspend fun obtenerVentasPendientes(): List<VentaEntity> = withContext(Dispatchers.IO) {
        ventaDao.obtenerVentasPendientes()
    }

    suspend fun obtenerDetallesDeVenta(ventaId: String): List<VentaDetalleEntity> = withContext(Dispatchers.IO) {
        ventaDao.obtenerDetallesPorVenta(ventaId)
    }

    suspend fun obtenerVentaPorId(ventaId: String): VentaEntity? {
        return ventaDao.obtenerVentaPorId(ventaId)
    }

    suspend fun sincronizarConServidor(
        ventaLocalId: String,
        clienteId: String,
        clienteNombre: String,
        productos: List<Plantilla_Producto>,
        metodoPago: String,
        vendedorId: String,
        vendedorNombre: String,
        almacenVendedorId: String,
        fotoEvidenciaLocal: String? = null,
        fueraDeRango: Boolean = false, // 🔥
        latitudVenta: Double = 0.0,    // 🔥
        longitudVenta: Double = 0.0    // 🔥
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val url = "https://us-central1-appventas--san-angel.cloudfunctions.net/registrarVenta"
        val client = OkHttpClient()

        // 🛡️ SUBIDA DE IMAGEN A FIREBASE STORAGE
        var fotoUrlFinal = ""
        if (!fotoEvidenciaLocal.isNullOrEmpty()) {
            val file = java.io.File(fotoEvidenciaLocal)
            if (file.exists()) {
                try {
                    Log.d("VentaRepo", "📸 Iniciando subida de foto: ${file.absolutePath}")
                    val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
                        .child("evidencias_visita/${ventaLocalId}.jpg")
                    
                    val fileUri = android.net.Uri.fromFile(file)
                    storageRef.putFile(fileUri).await()
                    fotoUrlFinal = storageRef.downloadUrl.await().toString()
                    Log.d("VentaRepo", "✅ Foto de evidencia subida exitosamente: $fotoUrlFinal")
                } catch (e: Exception) {
                    Log.e("VentaRepo", "❌ Error subiendo foto a Storage: ${e.message}", e)
                }
            }
        }

        try {
            val json = JSONObject().apply {
                put("ventaLocalId", ventaLocalId)
                put("clienteId", clienteId)
                put("clienteNombre", clienteNombre)
                put("vendedorNombre", vendedorNombre)
                put("fotoEvidenciaVisita", fotoUrlFinal)
                put("fueraDeRango", fueraDeRango) // 🔥
                put("latitudVenta", latitudVenta) // 🔥
                put("longitudVenta", longitudVenta) // 🔥
                put("productos", JSONArray().apply {
                    productos.forEach { p ->
                        put(
                            JSONObject().apply {
                                put("id", p.id)
                                put("nombre", p.nombre)
                                put("precio", p.precio)
                                put("cantidad", p.cantidad)
                                put("imagenUrl", p.imagenUrl)
                            }
                        )
                    }
                })
                put("metodoPago", metodoPago)
                put("vendedorId", vendedorId)
                put("almacenVendedorId", almacenVendedorId)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            Pair(response.isSuccessful, respBody)
        } catch (e: Exception) {
            Log.e("VentaRepo", "Error sincronizando con servidor", e)
            Pair(false, e.message ?: "Error desconocido")
        }
    }
}
