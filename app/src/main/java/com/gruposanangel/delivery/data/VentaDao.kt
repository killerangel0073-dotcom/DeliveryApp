package com.gruposanangel.delivery.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VentaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarVenta(venta: VentaEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllVentas(ventas: List<VentaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarDetalle(detalle: VentaDetalleEntity)

    @Transaction
    suspend fun insertarVentaConDetalles(venta: VentaEntity, detalles: List<VentaDetalleEntity>) {
        insertarVenta(venta)
        detalles.forEach { insertarDetalle(it) }
    }

    @Query("SELECT * FROM ventas WHERE sincronizado = 0")
    suspend fun obtenerVentasPendientes(): List<VentaEntity>

    @Query("SELECT * FROM detalle_ventas WHERE ventaId = :ventaId")
    suspend fun obtenerDetallesPorVenta(ventaId: String): List<VentaDetalleEntity>

    @Query("UPDATE ventas SET sincronizado = 1 WHERE id = :ventaId")
    suspend fun marcarComoSincronizada(ventaId: String)

    @Update
    suspend fun actualizarVenta(venta: VentaEntity)

    @Query("SELECT * FROM ventas WHERE id = :ventaId")
    suspend fun obtenerVentaPorId(ventaId: String): VentaEntity?

    @Query("SELECT * FROM ventas WHERE vendedorId = :vendedorId AND fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    suspend fun obtenerVentasPorPeriodo(vendedorId: String, inicio: Long, fin: Long): List<VentaEntity>

    @Query("SELECT * FROM ventas WHERE almacenId = :almacenId AND fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    suspend fun obtenerVentasPorAlmacenPeriodo(almacenId: String, inicio: Long, fin: Long): List<VentaEntity>

    @Query("SELECT * FROM ventas WHERE fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    suspend fun obtenerTodasVentasPorPeriodo(inicio: Long, fin: Long): List<VentaEntity>

    @Query("SELECT * FROM ventas WHERE (vendedorId = :vendedorId OR :vendedorId = '') AND fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    fun obtenerVentasPorPeriodoFlow(vendedorId: String, inicio: Long, fin: Long): Flow<List<VentaEntity>>

    @Query("SELECT * FROM ventas WHERE (almacenId = :almacenId OR rutaId = :rutaId OR rutaNombre = :rutaId OR rutaId = :rutaIdReal OR rutaNombre = :rutaIdReal OR almacenId = :rutaIdReal) AND fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    fun obtenerVentasPorUnidadPeriodoFlow(almacenId: String, rutaId: String, rutaIdReal: String, inicio: Long, fin: Long): Flow<List<VentaEntity>>
    
    @Query("SELECT * FROM ventas WHERE (almacenId = :almacenId OR rutaId = :rutaId OR rutaNombre = :rutaId OR rutaId = :rutaIdReal OR rutaNombre = :rutaIdReal OR almacenId = :rutaIdReal) AND fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    suspend fun obtenerVentasPorUnidadPeriodo(almacenId: String, rutaId: String, rutaIdReal: String, inicio: Long, fin: Long): List<VentaEntity>

    @Query("SELECT * FROM ventas WHERE fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    fun obtenerTodasVentasPorPeriodoFlow(inicio: Long, fin: Long): Flow<List<VentaEntity>>

    @Query("UPDATE ventas SET sincronizado = :sincronizado, firestoreId = :firestoreId WHERE id = :id")
    suspend fun updateSincronizacion(id: String, firestoreId: String?, sincronizado: Boolean)

    @Query("UPDATE ventas SET intentosSync = intentosSync + 1, ultimoError = :error WHERE id = :id")
    suspend fun registrarIntentoFallido(id: String, error: String)

    @Query("UPDATE ventas SET fotoSincronizada = 1 WHERE id = :id")
    suspend fun marcarFotoSincronizada(id: String)

    @Query("UPDATE productos SET cantidadDisponible = cantidadDisponible - :cantidad WHERE id = :productoId")
    suspend fun descontarStockLocal(productoId: String, cantidad: Int)

    @Query("UPDATE productos SET cantidadDisponible = cantidadDisponible + :cantidad WHERE id = :productoId")
    suspend fun reponerStockLocal(productoId: String, cantidad: Int)

    @Transaction
    suspend fun insertarVentaYActualizarStock(venta: VentaEntity, detalles: List<VentaDetalleEntity>) {
        insertarVenta(venta)
        detalles.forEach {
            insertarDetalle(it)
            // 🛡️ Blindaje Financiero: Descontar stock usando el stockId (PK real de la tabla productos)
            val idParaStock = it.stockId ?: it.productoId
            descontarStockLocal(idParaStock, it.cantidad)
        }
    }

    @Query("SELECT firestoreId FROM ventas WHERE id = :ventaLocalId LIMIT 1")
    fun obtenerFirestoreIdDeVenta(ventaLocalId: String): String?

    @Query("DELETE FROM detalle_ventas WHERE ventaId = :ventaId")
    suspend fun eliminarDetallesPorVenta(ventaId: String)

    @Transaction
    suspend fun refrescarVentaCompleta(venta: VentaEntity, detalles: List<VentaDetalleEntity>) {
        insertarVenta(venta)
        eliminarDetallesPorVenta(venta.id)
        detalles.forEach { insertarDetalle(it) }
    }

    @Query("SELECT * FROM ventas WHERE clienteId = :clienteId AND total > 0 AND UPPER(estado) NOT IN ('CANCELADA', 'ANULADA') ORDER BY fecha DESC LIMIT 1")
    suspend fun obtenerUltimaVentaConProductosPorCliente(clienteId: String): VentaEntity?

    @Query("SELECT * FROM ventas WHERE clienteId = :clienteId AND fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    suspend fun obtenerVentasPorClientePeriodo(clienteId: String, inicio: Long, fin: Long): List<VentaEntity>

    @Query("SELECT * FROM ventas WHERE clienteId = :clienteId ORDER BY fecha DESC")
    suspend fun obtenerTodoHistorialCliente(clienteId: String): List<VentaEntity>

    @Query("SELECT MAX(fecha) FROM ventas WHERE almacenId = :almacenId")
    suspend fun obtenerFechaUltimaVentaLocal(almacenId: String): Long?

    @Query("SELECT * FROM detalle_ventas WHERE ventaId IN (SELECT id FROM ventas WHERE (vendedorId = :vendedorId OR :vendedorId = '') AND fecha BETWEEN :inicio AND :fin AND estado != 'CANCELADA')")
    fun obtenerDetallesPorPeriodoFlow(vendedorId: String, inicio: Long, fin: Long): Flow<List<VentaDetalleEntity>>

    @Query("SELECT * FROM detalle_ventas WHERE ventaId IN (SELECT id FROM ventas WHERE (almacenId = :almacenId OR rutaId = :rutaId OR rutaNombre = :rutaId OR rutaId = :rutaIdReal OR rutaNombre = :rutaIdReal OR almacenId = :rutaIdReal) AND fecha BETWEEN :inicio AND :fin AND estado != 'CANCELADA')")
    fun obtenerDetallesPorUnidadPeriodoFlow(almacenId: String, rutaId: String, rutaIdReal: String, inicio: Long, fin: Long): Flow<List<VentaDetalleEntity>>
}
