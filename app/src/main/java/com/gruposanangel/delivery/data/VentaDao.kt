package com.gruposanangel.delivery.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface VentaDao {

    @Insert
    suspend fun insertarVenta(venta: VentaEntity): Long

    @Insert
    suspend fun insertarDetalle(detalle: VentaDetalleEntity): Long

    @Query("SELECT * FROM ventas WHERE sincronizado = 0")
    suspend fun obtenerVentasPendientes(): List<VentaEntity>

    @Query("SELECT * FROM detalle_ventas WHERE ventaId = :ventaId")
    suspend fun obtenerDetallesPorVenta(ventaId: Long): List<VentaDetalleEntity>

    @Query("UPDATE ventas SET sincronizado = 1 WHERE id = :ventaId")
    suspend fun marcarComoSincronizada(ventaId: Long)


    @Update
    suspend fun actualizarVenta(venta: VentaEntity)

    @Query("SELECT * FROM ventas WHERE id = :ventaId")
    suspend fun obtenerVentaPorId(ventaId: Long): VentaEntity?

    @Query("SELECT * FROM ventas WHERE fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    suspend fun obtenerVentasPorPeriodo(inicio: Long, fin: Long): List<VentaEntity>

    @Query("UPDATE ventas SET sincronizado = :sincronizado, firestoreId = :firestoreId WHERE id = :id")
    suspend fun updateSincronizacion(id: Long, firestoreId: String?, sincronizado: Boolean)

    @Query("SELECT firestoreId FROM ventas WHERE id = :ventaLocalId LIMIT 1")
    fun obtenerFirestoreIdDeVenta(ventaLocalId: Long): String?







}
