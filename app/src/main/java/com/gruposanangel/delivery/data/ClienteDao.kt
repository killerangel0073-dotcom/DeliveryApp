package com.gruposanangel.delivery.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClienteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cliente: ClienteEntity)

    @Update
    suspend fun update(cliente: ClienteEntity)

    @Query("SELECT * FROM clientes")
    suspend fun getAllClientes(): List<ClienteEntity>

    @Query("SELECT * FROM clientes WHERE syncStatus = 0")
    suspend fun getClientesPendientes(): List<ClienteEntity>

    @Query("DELETE FROM clientes WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Devuelve todos los clientes como Flow para observar cambios en Compose.
     */
    @Query("SELECT * FROM clientes ORDER BY fechaDeCreacion DESC")
    fun getAllClientesFlow(): Flow<List<ClienteEntity>>
}
