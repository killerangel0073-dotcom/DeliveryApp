package com.gruposanangel.delivery.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ventas")
data class VentaEntity(
    @PrimaryKey val id: String, // Cambiado a String para UUID
    val clienteId: String,
    val clienteNombre: String,
    val clienteImagenUrl: String? = null,
    val total: Double,
    val metodoPago: String,
    val vendedorId: String,
    val fecha: Long,
    val sincronizado: Boolean,
    val firestoreId: String? = null
)

@Entity(
    tableName = "detalle_ventas",
    foreignKeys = [
        ForeignKey(
            entity = VentaEntity::class,
            parentColumns = ["id"],
            childColumns = ["ventaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["ventaId"])]
)
data class VentaDetalleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ventaId: String, // Cambiado a String para coincidir con el ID de VentaEntity
    val productoId: String,
    val nombre: String,
    val precio: Double,
    val cantidad: Int
)
