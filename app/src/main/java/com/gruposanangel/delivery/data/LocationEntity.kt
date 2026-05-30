package com.gruposanangel.delivery.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete

@Entity(tableName = "ubicaciones_pendientes")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val battery: Int,
    val timestamp: Long,
    val ruta: String,
    val status: String
)

@Dao
interface LocationDao {
    @Insert
    suspend fun insertar(ubicacion: LocationEntity)

    @Query("SELECT * FROM ubicaciones_pendientes ORDER BY timestamp ASC LIMIT 100")
    suspend fun obtenerPendientes(): List<LocationEntity>

    @Query("SELECT COUNT(*) FROM ubicaciones_pendientes")
    suspend fun obtenerConteoPendientes(): Int

    @Delete
    suspend fun eliminar(ubicaciones: List<LocationEntity>)

    @Query("DELETE FROM ubicaciones_pendientes WHERE id = :id")
    suspend fun eliminarPorId(id: Int)
}
