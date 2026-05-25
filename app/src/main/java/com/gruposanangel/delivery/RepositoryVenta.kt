package com.gruposanangel.delivery.data

import ProductoTicketDetalle
import TicketVentaCompleto
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
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
import java.util.TimeZone
import java.util.UUID

class VentaRepository(private val ventaDao: VentaDao) {

    suspend fun obtenerVentasPorPeriodo(inicio: Long, fin: Long): List<VentaEntity> =
        withContext(Dispatchers.IO) {
            ventaDao.obtenerVentasPorPeriodo(inicio, fin)
        }

    suspend fun obtenerTicketCompleto(ticketId: String): TicketVentaCompleto? {
        val venta = ventaDao.obtenerVentaPorId(ticketId) ?: return null
        val detalles = ventaDao.obtenerDetallesPorVenta(ticketId)

        val productos = detalles.map {
            ProductoTicketDetalle(
                nombre = it.nombre,
                cantidad = it.cantidad,
                precio = it.precio
            )
        }

        return TicketVentaCompleto(
            numeroTicket = venta.id,
            cliente = venta.clienteNombre,
            total = venta.total,
            fecha = Date(venta.fecha),
            sincronizado = venta.sincronizado,
            fotoCliente = venta.clienteImagenUrl ?: "",
            productos = productos
        )
    }

    suspend fun descargarVentasDia(vendedorId: String): List<VentaEntity> =
        withContext(Dispatchers.IO) {
            val firestore = FirebaseFirestore.getInstance()

            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val inicio = calendar.time

            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val fin = calendar.time

            try {
                val snapshot = firestore.collection("ventas")
                    .whereEqualTo("vendedorId", vendedorId)
                    .whereGreaterThanOrEqualTo("fecha", inicio)
                    .whereLessThanOrEqualTo("fecha", fin)
                    .get()
                    .await()

                snapshot.documents.forEach { doc ->
                    val localId = doc.getString("localId") ?: doc.id // Fallback al ID de firestore si no hay localId

                    val venta = VentaEntity(
                        id = localId,
                        clienteId = doc.getString("clienteId") ?: "",
                        clienteNombre = doc.getString("clienteNombre") ?: "",
                        clienteImagenUrl = doc.getString("clienteImagenUrl"),
                        total = doc.getDouble("total") ?: 0.0,
                        metodoPago = doc.getString("metodoPago") ?: "",
                        vendedorId = doc.getString("vendedorId") ?: "",
                        fecha = doc.getTimestamp("fecha")?.toDate()?.time ?: System.currentTimeMillis(),
                        sincronizado = true,
                        firestoreId = doc.id
                    )

                    val productosSnapshot = firestore
                        .collection("ventas")
                        .document(doc.id)
                        .collection("productos")
                        .get()
                        .await()

                    val detalles = productosSnapshot.documents.map { pDoc ->
                        VentaDetalleEntity(
                            ventaId = localId,
                            productoId = pDoc.id,
                            nombre = pDoc.getString("nombre") ?: "Producto",
                            precio = pDoc.getDouble("precio") ?: 0.0,
                            cantidad = (pDoc.getLong("cantidad") ?: 0L).toInt()
                        )
                    }

                    ventaDao.refrescarVentaCompleta(venta, detalles)
                }

            } catch (e: Exception) {
                Log.e("VentaRepo", "Error sincronizando Firebase", e)
            }

            return@withContext ventaDao.obtenerVentasPorPeriodo(inicio.time, fin.time)
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
        val idLocal = UUID.randomUUID().toString() // 🔥 Cambio a UUID para idempotencia
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

    suspend fun marcarVentaComoSincronizada(ventaId: String) = withContext(Dispatchers.IO) {
        ventaDao.marcarComoSincronizada(ventaId)
    }

    suspend fun obtenerVentaPorId(ventaId: String): VentaEntity? {
        return ventaDao.obtenerVentaPorId(ventaId)
    }

    suspend fun obtenerFirestoreIdDeVenta(ventaLocalId: String): String? =
        withContext(Dispatchers.IO) {
            ventaDao.obtenerFirestoreIdDeVenta(ventaLocalId)
        }

    /**
     * Sincroniza una venta con el servidor a través de Cloud Functions.
     * Centralizado aquí para que tanto el ViewModel como el Worker lo usen.
     */
    suspend fun sincronizarConServidor(
        ventaLocalId: String,
        clienteId: String,
        clienteNombre: String,
        productos: List<Plantilla_Producto>,
        metodoPago: String,
        vendedorId: String,
        almacenVendedorId: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val url = "https://us-central1-appventas--san-angel.cloudfunctions.net/registrarVenta"
        val client = OkHttpClient()
        try {
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
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            Pair(response.isSuccessful, respBody)
        } catch (e: Exception) {
            Log.e("VentaRepo", "Error sincronizando con servidor", e)
            Pair(false, e.message ?: "Error desconocido")
        }
    }
}
