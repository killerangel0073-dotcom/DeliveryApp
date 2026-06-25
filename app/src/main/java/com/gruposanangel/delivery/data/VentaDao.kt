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

    @Query("SELECT * FROM ventas WHERE fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    suspend fun obtenerTodasVentasPorPeriodo(inicio: Long, fin: Long): List<VentaEntity>

    @Query("SELECT * FROM ventas WHERE vendedorId = :vendedorId AND fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    fun obtenerVentasPorPeriodoFlow(vendedorId: String, inicio: Long, fin: Long): Flow<List<VentaEntity>>

    @Query("SELECT * FROM ventas WHERE fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    fun obtenerTodasVentasPorPeriodoFlow(inicio: Long, fin: Long): Flow<List<VentaEntity>>

    @Query("UPDATE ventas SET sincronizado = :sincronizado, firestoreId = :firestoreId WHERE id = :id")
    suspend fun updateSincronizacion(id: String, firestoreId: String?, sincronizado: Boolean)

    @Query("UPDATE productos SET cantidadDisponible = cantidadDisponible - :cantidad WHERE id = :productoId")
    suspend fun descontarStockLocal(productoId: String, cantidad: Int)

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
}
