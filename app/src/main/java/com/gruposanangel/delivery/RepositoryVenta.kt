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

class VentaRepository(private val ventaDao: VentaDao) {

    suspend fun obtenerVentasPorPeriodo(inicio: Long, fin: Long): List<VentaEntity> =
        withContext(Dispatchers.IO) {
            ventaDao.obtenerVentasPorPeriodo(inicio, fin)
        }


    suspend fun sincronizarVentasPendientes(
        inventarioRepo: RepositoryInventario
    ) = withContext(Dispatchers.IO) {

        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return@withContext

        val pendientes = ventaDao.obtenerVentasPendientes()
        if (pendientes.isEmpty()) return@withContext

        val almacenId = inventarioRepo.getAlmacenVendedor(user.uid)
        if (almacenId.isNullOrEmpty()) return@withContext

        for (venta in pendientes) {
            try {
                val detalles = ventaDao.obtenerDetallesPorVenta(venta.id)

                val productos = detalles.map {
                    hashMapOf(
                        "id" to it.productoId,
                        "nombre" to it.nombre,
                        "precio" to it.precio,
                        "cantidad" to it.cantidad
                    )
                }

                val json = JSONObject().apply {
                    put("ventaLocalId", venta.id)
                    put("clienteId", venta.clienteId)
                    put("clienteNombre", venta.clienteNombre)
                    put("productos", JSONArray(productos))
                    put("metodoPago", venta.metodoPago)
                    put("vendedorId", venta.vendedorId)
                    put("almacenVendedorId", almacenId)
                }

                val body = json.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("https://us-central1-appventas--san-angel.cloudfunctions.net/registrarVenta")
                    .post(body)
                    .build()

                val response = OkHttpClient().newCall(request).execute()

                if (response.isSuccessful) {
                    val resp = JSONObject(response.body?.string() ?: "{}")
                    val firestoreId = resp.optString("ventaId")

                    if (firestoreId.isNotEmpty()) {
                        ventaDao.updateSincronizacion(
                            id = venta.id,
                            firestoreId = firestoreId,
                            sincronizado = true
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e("VentaRepo", "Error sincronizando venta ${venta.id}", e)
            }
        }
    }


    suspend fun obtenerTicketCompleto(ticketId: Long): TicketVentaCompleto? {
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
            numeroTicket = venta.id.toString(),
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

            // Inicio y fin del día en UTC
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

                    val localId = doc.getLong("localId") ?: return@forEach

                    // 1️⃣ Guardar / actualizar venta
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

                    ventaDao.insertarVenta(venta)

                    // 2️⃣ LIMPIAR detalles locales previos
                    ventaDao.eliminarDetallesPorVenta(localId)

                    // 3️⃣ LEER SUBCOLECCIÓN productos (ESTA ES LA CLAVE)
                    val productosSnapshot = firestore
                        .collection("ventas")
                        .document(doc.id)
                        .collection("productos")
                        .get()
                        .await()

                    // 4️⃣ Insertar productos reales en Room
                    productosSnapshot.documents.forEach { pDoc ->
                        val detalle = VentaDetalleEntity(
                            ventaId = localId,
                            productoId = pDoc.id,
                            nombre = pDoc.getString("nombre") ?: "Producto",
                            precio = pDoc.getDouble("precio") ?: 0.0,
                            cantidad = (pDoc.getLong("cantidad") ?: 0L).toInt()
                        )
                        ventaDao.insertarDetalle(detalle)
                    }
                }

            } catch (e: Exception) {
                Log.e("VentaRepo", "Error sincronizando Firebase", e)
            }

            // 5️⃣ Retornar ventas del día desde Room
            return@withContext ventaDao.obtenerVentasPorPeriodo(
                inicio.time,
                fin.time
            )
        }







    suspend fun guardarVentaLocal(
        clienteId: String,
        clienteNombre: String,
        clienteImagenUrl: String?,
        productos: List<Plantilla_Producto>,
        total: Double,
        metodoPago: String,
        vendedorId: String
    ): Long = withContext(Dispatchers.IO) {
        val idLocal = System.currentTimeMillis() // <-- guardamos este ID
        val venta = VentaEntity(
            id = idLocal, // usar el mismo para detalles
            clienteId = clienteId,
            clienteNombre = clienteNombre,
            clienteImagenUrl = clienteImagenUrl,
            total = total,
            metodoPago = metodoPago,
            vendedorId = vendedorId,
            fecha = System.currentTimeMillis(),
            sincronizado = false
        )

        ventaDao.insertarVenta(venta) // no necesitamos guardar el Long retornado

        // Guardar detalles usando el mismo ID
        productos.forEach { producto ->
            val detalle = VentaDetalleEntity(
                ventaId = idLocal, // <-- CORRECCIÓN
                productoId = producto.id,
                nombre = producto.nombre,
                precio = producto.precio,
                cantidad = producto.cantidad
            )
            ventaDao.insertarDetalle(detalle)
        }
        idLocal
    }




    suspend fun marcarVentaConFirestoreId(ventaLocalId: Long, firestoreId: String) = withContext(Dispatchers.IO) {
        val venta = ventaDao.obtenerVentaPorId(ventaLocalId)
        if (venta != null) {
            val ventaActualizada = venta.copy(firestoreId = firestoreId, sincronizado = true)
            ventaDao.actualizarVenta(ventaActualizada)
        }
    }




    suspend fun obtenerVentasPendientes(): List<VentaEntity> = withContext(Dispatchers.IO) {
        ventaDao.obtenerVentasPendientes()
    }

    suspend fun obtenerDetallesDeVenta(ventaId: Long): List<VentaDetalleEntity> = withContext(Dispatchers.IO) {
        ventaDao.obtenerDetallesPorVenta(ventaId)
    }

    suspend fun marcarVentaComoSincronizada(ventaId: Long) = withContext(Dispatchers.IO) {
        ventaDao.marcarComoSincronizada(ventaId)
    }

    // Obtener una venta por ID
    suspend fun obtenerVentaPorId(ventaId: Long): VentaEntity? {
        return ventaDao.obtenerVentaPorId(ventaId)
    }


    suspend fun obtenerFirestoreIdDeVenta(ventaLocalId: Long): String? =
        withContext(Dispatchers.IO) {
            ventaDao.obtenerFirestoreIdDeVenta(ventaLocalId)
        }



}
