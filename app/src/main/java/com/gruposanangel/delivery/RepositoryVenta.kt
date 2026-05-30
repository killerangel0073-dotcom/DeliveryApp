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

class VentaRepository(private val ventaDao: VentaDao) {

    suspend fun obtenerVentasPorPeriodo(vendedorId: String, inicio: Long, fin: Long): List<VentaEntity> =
        withContext(Dispatchers.IO) {
            ventaDao.obtenerVentasPorPeriodo(vendedorId, inicio, fin)
        }

    fun obtenerVentasPorPeriodoFlow(vendedorId: String, inicio: Long, fin: Long): kotlinx.coroutines.flow.Flow<List<VentaEntity>> {
        return ventaDao.obtenerVentasPorPeriodoFlow(vendedorId, inicio, fin)
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
                vendedorNombre = vendedorNombre
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
                vendedorNombre = vendedorNombre
            )
        } catch (e: Exception) {
            Log.e("VentaRepo", "Error crítico buscando ticket en Firestore", e)
            null
        }
    }

    suspend fun descargarVentasDia(vendedorId: String): List<VentaEntity> =
        withContext(Dispatchers.IO) {
            val firestore = FirebaseFirestore.getInstance()
            Log.d("VentaRepo", "Iniciando sincronización de historial para: $vendedorId")

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val inicio = calendar.time

            val calendarFin = Calendar.getInstance()
            calendarFin.set(Calendar.HOUR_OF_DAY, 23); calendarFin.set(Calendar.MINUTE, 59)
            calendarFin.set(Calendar.SECOND, 59); calendarFin.set(Calendar.MILLISECOND, 999)
            val fin = calendarFin.time

            try {
                val snapshot = firestore.collection("ventas")
                    .whereGreaterThanOrEqualTo("fecha", inicio)
                    .whereLessThanOrEqualTo("fecha", fin)
                    .get()
                    .await()

                snapshot.documents.forEach { doc ->
                    val vId = when(val vIdRaw = doc.get("vendedorId")) {
                        is com.google.firebase.firestore.DocumentReference -> vIdRaw.id
                        else -> vIdRaw?.toString() ?: ""
                    }

                    if (vId == vendedorId) {
                        val localId = doc.getString("localId") ?: doc.id
                        val fRaw = doc.get("fecha")
                        val fechaFinal = when(fRaw) {
                            is com.google.firebase.Timestamp -> fRaw.toDate().time
                            is Number -> fRaw.toLong()
                            else -> System.currentTimeMillis()
                        }

                        val venta = VentaEntity(
                            id = localId,
                            clienteId = doc.getString("clienteId") ?: "",
                            clienteNombre = doc.getString("clienteNombre") ?: "Cliente",
                            clienteImagenUrl = doc.getString("clienteImagenUrl"),
                            total = (doc.get("total") as? Number)?.toDouble() ?: 0.0,
                            metodoPago = doc.getString("metodoPago") ?: "Efectivo",
                            vendedorId = vendedorId,
                            fecha = fechaFinal,
                            sincronizado = true,
                            firestoreId = doc.id
                        )

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
                        Log.d("VentaRepo", "✅ Sincronizada venta: ${venta.clienteNombre} [$localId]")
                    }
                }
            } catch (e: Exception) {
                Log.e("VentaRepo", "❌ Error sincronizando historial: ${e.message}")
            }

            return@withContext ventaDao.obtenerVentasPorPeriodo(vendedorId, inicio.time, fin.time)
        }

    suspend fun guardarVentaLocal(
        clienteId: String,
        clienteNombre: String,
        clienteImagenUrl: String?,
        productos: List<Plantilla_Producto>,
        total: Double,
        metodoPago: String,
        vendedorId: String
    ): String = withContext(Dispatchers.IO) {
        val idLocal = UUID.randomUUID().toString()
        val venta = VentaEntity(
            id = idLocal,
            clienteId = clienteId,
            clienteNombre = clienteNombre,
            clienteImagenUrl = clienteImagenUrl,
            total = total,
            metodoPago = metodoPago,
            vendedorId = vendedorId,
            fecha = System.currentTimeMillis(),
            sincronizado = false
        )

        val detalles = productos.map { producto ->
            VentaDetalleEntity(
                ventaId = idLocal,
                productoId = producto.id,
                nombre = producto.nombre,
                precio = producto.precio,
                cantidad = producto.cantidad
            )
        }

        ventaDao.insertarVentaConDetalles(venta, detalles)
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
        almacenVendedorId: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val url = "https://us-central1-appventas--san-angel.cloudfunctions.net/registrarVenta"
        val client = OkHttpClient()
        try {
            val json = JSONObject().apply {
                put("ventaLocalId", ventaLocalId)
                put("clienteId", clienteId)
                put("clienteNombre", clienteNombre)
                put("vendedorNombre", vendedorNombre)
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
