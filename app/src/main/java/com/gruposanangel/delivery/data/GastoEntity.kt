package com.gruposanangel.delivery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gastos")
data class GastoEntity(
    @PrimaryKey val id: String,
    val monto: Double,
    val categoria: String,
    val descripcion: String,
    val fecha: Long,
    val vendedorId: String,
    val vendedorNombre: String,
    val rutaNombre: String,
    val sincronizado: Boolean = false
)
