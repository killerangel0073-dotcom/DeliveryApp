package com.gruposanangel.delivery.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gruposanangel.delivery.model.Plantilla_Producto

class VentaRepository(private val ventaDao: VentaDao) {

    suspend fun obtenerVentasPorPeriodo(inicio: Long, fin: Long): List<VentaEntity> =
        withContext(Dispatchers.IO) {
            ventaDao.obtenerVentasPorPeriodo(inicio, fin)
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
