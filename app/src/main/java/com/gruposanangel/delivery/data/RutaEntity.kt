package com.gruposanangel.delivery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rutas")
data class RutaEntity(
    @PrimaryKey val id: String, // ID de Firestore
    val nombre: String,
    val diasVisita: String,    // Ejemplo: "Lunes,Jueves"
    val frecuencia: String,    // "Semanal" o "Quincenal"
    val activo: Boolean = true,
    val lastModified: Long = System.currentTimeMillis()
)

@androidx.room.Dao
interface RutaDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertarRuta(ruta: RutaEntity)

    @androidx.room.Query("SELECT * FROM rutas ORDER BY nombre ASC")
    fun obtenerRutasFlow(): kotlinx.coroutines.flow.Flow<List<RutaEntity>>

    @androidx.room.Query("SELECT * FROM rutas WHERE id = :id")
    suspend fun obtenerRutaPorId(id: String): RutaEntity?
    
    @androidx.room.Query("DELETE FROM rutas WHERE id = :id")
    suspend fun eliminarRuta(id: String)
}
