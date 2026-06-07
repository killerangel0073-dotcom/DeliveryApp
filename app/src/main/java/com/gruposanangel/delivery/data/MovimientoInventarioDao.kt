package com.gruposanangel.delivery.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MovimientoInventarioDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarMovimiento(movimiento: MovimientoInventarioEntity)

    @Query("SELECT * FROM movimientos_inventario WHERE sincronizado = 0")
    suspend fun obtenerMovimientosPendientes(): List<MovimientoInventarioEntity>

    @Query("UPDATE movimientos_inventario SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarComoSincronizado(id: String)

    @Query("SELECT * FROM movimientos_inventario WHERE vendedorId = :vendedorId AND timestamp >= :inicio")
    suspend fun obtenerMovimientosDesde(vendedorId: String, inicio: Long): List<MovimientoInventarioEntity>

    @Query("SELECT * FROM movimientos_inventario WHERE referenciaId = :referenciaId")
    fun obtenerMovimientosPorReferencia(referenciaId: String): Flow<List<MovimientoInventarioEntity>>
}
