package com.gruposanangel.delivery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "productos")
data class ProductoEntity(
    @PrimaryKey val id: String,           // ID de inventarioStock
    val productoId: String,               // ID del producto
    val nombre: String,
    val precio: Double,
    val cantidadDisponible: Int,          // stock real en inventario
    val imagenUrl: String?,
    val syncStatus: Boolean = false
)
