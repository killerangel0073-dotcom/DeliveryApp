package com.gruposanangel.delivery.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GastoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(gasto: GastoEntity)

    @Query("SELECT * FROM gastos WHERE vendedorId = :vendedorId AND fecha >= :inicioDia ORDER BY fecha DESC")
    fun obtenerGastosHoyFlow(vendedorId: String, inicioDia: Long): Flow<List<GastoEntity>>

    @Query("SELECT * FROM gastos WHERE vendedorId = :vendedorId AND fecha BETWEEN :inicio AND :fin ORDER BY fecha ASC")
    suspend fun obtenerGastosPorPeriodo(vendedorId: String, inicio: Long, fin: Long): List<GastoEntity>

    @Query("SELECT * FROM gastos WHERE fecha BETWEEN :inicio AND :fin ORDER BY fecha ASC")
    suspend fun obtenerTodasGastosPorPeriodo(inicio: Long, fin: Long): List<GastoEntity>

    @Query("SELECT * FROM gastos WHERE vendedorId = :vendedorId AND fecha BETWEEN :inicio AND :fin ORDER BY fecha ASC")
    fun obtenerGastosPorPeriodoFlow(vendedorId: String, inicio: Long, fin: Long): Flow<List<GastoEntity>>

    @Query("SELECT * FROM gastos WHERE fecha BETWEEN :inicio AND :fin ORDER BY fecha ASC")
    fun obtenerTodasGastosPorPeriodoFlow(inicio: Long, fin: Long): Flow<List<GastoEntity>>

    @Query("SELECT * FROM gastos WHERE rutaNombre = :rutaNombre AND fecha BETWEEN :inicio AND :fin ORDER BY fecha ASC")
    fun obtenerGastosPorRutaPeriodoFlow(rutaNombre: String, inicio: Long, fin: Long): Flow<List<GastoEntity>>

    @Query("SELECT * FROM gastos WHERE rutaNombre = :rutaNombre AND fecha BETWEEN :inicio AND :fin ORDER BY fecha ASC")
    suspend fun obtenerGastosPorRutaPeriodo(rutaNombre: String, inicio: Long, fin: Long): List<GastoEntity>

    @Query("SELECT * FROM gastos WHERE sincronizado = 0")
    suspend fun obtenerGastosPendientes(): List<GastoEntity>

    @Query("UPDATE gastos SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarComoSincronizado(id: String)

    @Query("DELETE FROM gastos WHERE fecha < :limite")
    suspend fun limpiarGastosAntiguos(limite: Long)

    @Query("DELETE FROM gastos WHERE id = :id")
    suspend fun eliminarGastoPorId(id: String)

    @Query("SELECT * FROM gastos WHERE esFijo = 1 AND activo = 1")
    fun obtenerGastosFijosActivos(): Flow<List<GastoEntity>>
}
