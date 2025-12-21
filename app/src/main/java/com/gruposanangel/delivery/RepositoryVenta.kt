package com.gruposanangel.delivery.data

import ProductoTicketDetalle
import TicketVentaCompleto
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gruposanangel.delivery.model.Plantilla_Producto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

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


    suspend fun guardarVentaLocal(
        clienteId: String,
        clienteNombre: String,
        clienteImagenUrl: String?,
        productos: List<Plantilla_Producto>,
        total: Double,
        metodoPago: String,
        vendedorId: String
    ): Long = withContext(Dispatchers.IO) {
        val venta = VentaEntity(
            clienteId = clienteId,
            clienteNombre = clienteNombre,
            clienteImagenUrl = clienteImagenUrl,
            total = total,
            metodoPago = metodoPago,
            vendedorId = vendedorId,
            fecha = System.currentTimeMillis(),
            sincronizado = false
        )
        val ventaId = ventaDao.insertarVenta(venta)
        // Guardar detalles
        productos.forEach { producto ->
            val detalle = VentaDetalleEntity(
                ventaId = ventaId,
                productoId = producto.id,
                nombre = producto.nombre,
                precio = producto.precio,
                cantidad = producto.cantidad
            )
            ventaDao.insertarDetalle(detalle)
        }
        ventaId
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
