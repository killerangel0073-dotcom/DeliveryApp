package com.gruposanangel.delivery.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OrdenTransferenciaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarOrden(orden: OrdenTransferenciaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarDetalles(detalles: List<OrdenTransferenciaDetalleEntity>)

    @Transaction
    suspend fun insertarOrdenConDetalles(orden: OrdenTransferenciaEntity, detalles: List<OrdenTransferenciaDetalleEntity>) {
        insertarOrden(orden)
        eliminarDetallesPorOrden(orden.id)
        insertarDetalles(detalles)
    }

    @Query("DELETE FROM orden_transferencia_detalles WHERE ordenId = :ordenId")
    suspend fun eliminarDetallesPorOrden(ordenId: String)

    @Query("UPDATE ordenes_transferencia SET estado = :estado WHERE id = :id")
    suspend fun actualizarEstado(id: String, estado: String)

    @Query("SELECT * FROM ordenes_transferencia WHERE (destino = :vendedorId OR :vendedorId = 'Todos') AND timestamp BETWEEN :inicio AND :fin ORDER BY timestamp DESC")
    fun obtenerOrdenesPorPeriodoFlow(vendedorId: String, inicio: Long, fin: Long): Flow<List<OrdenTransferenciaEntity>>

    @Query("SELECT * FROM orden_transferencia_detalles WHERE ordenId = :ordenId")
    suspend fun obtenerDetallesPorOrden(ordenId: String): List<OrdenTransferenciaDetalleEntity>
}
