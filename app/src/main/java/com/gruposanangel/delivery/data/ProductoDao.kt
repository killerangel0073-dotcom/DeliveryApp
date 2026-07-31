package com.gruposanangel.delivery.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(productos: List<ProductoEntity>)

    @Update
    suspend fun update(producto: ProductoEntity)

    @Query("SELECT * FROM productos ORDER BY nombre ASC")
    fun getAllProductosFlow(): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM productos WHERE id = :id")
    suspend fun getProductoById(id: String): ProductoEntity?

    @Query("DELETE FROM productos")
    suspend fun clearProductos()

    // 🔹 Eliminar un producto por su ID
    @Query("DELETE FROM productos WHERE id = :id")
    suspend fun deleteById(id: String)

    // 🔹 Método para eliminar productos por ID
    @Query("DELETE FROM productos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM productos")
    suspend fun clearAll()

    // 🔹 Actualizar stock (cantidad disponible)
    @Query("UPDATE productos SET cantidadDisponible = :cantidad WHERE id = :id")
    suspend fun updateCantidadDisponible(id: String, cantidad: Int)


    @Query("UPDATE productos SET imagenUrl = :url WHERE id = :id")
    suspend fun actualizarFotoLocal(id: String, url: String)

    @Query("UPDATE productos SET imagenUrl = :url WHERE productoId = :productoId")
    suspend fun actualizarFotoPorProductoId(productoId: String, url: String)

}
