package com.gruposanangel.delivery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ventas")
data class VentaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: String,
    val clienteNombre: String,
    val clienteImagenUrl: String? = null, // <--- nuevo
    val total: Double,
    val metodoPago: String,
    val vendedorId: String,
    val fecha: Long,
    val sincronizado: Boolean,
    val firestoreId: String? = null  // <--- Nuevo campo
)

@Entity(tableName = "detalle_ventas")
data class VentaDetalleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ventaId: Long,
    val productoId: String,
    val nombre: String,
    val precio: Double,
    val cantidad: Int
)
