package com.gruposanangel.delivery.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ordenes_transferencia")
data class OrdenTransferenciaEntity(
    @PrimaryKey val id: String,
    val origen: String,
    val destino: String,
    val estado: String,
    val timestamp: Long,
    val esEmergencia: Boolean = false,
    val metodoAuditoria: String? = null,
    val montoTotal: Double = 0.0,
    val totalPiezas: Int = 0
)

@Entity(
    tableName = "orden_transferencia_detalles",
    foreignKeys = [
        ForeignKey(
            entity = OrdenTransferenciaEntity::class,
            parentColumns = ["id"],
            childColumns = ["ordenId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["ordenId"])]
)
data class OrdenTransferenciaDetalleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ordenId: String,
    val productoId: String,
    val nombre: String,
    val precio: Double,
    val cantidad: Int
)
