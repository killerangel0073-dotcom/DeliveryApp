package com.gruposanangel.delivery.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


@Dao
interface   UsuarioDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(vendedor: UsuarioEntity)

    @Query("SELECT * FROM usuarios WHERE uid = :uid LIMIT 1")
    suspend fun obtenerPorId(uid: String): UsuarioEntity?

    @Query("DELETE FROM usuarios")
    suspend fun limpiarTabla()

    @Query("SELECT * FROM usuarios WHERE uid = :uid")
    fun obtenerPorIdFlow(uid: String): Flow<UsuarioEntity?>

}

