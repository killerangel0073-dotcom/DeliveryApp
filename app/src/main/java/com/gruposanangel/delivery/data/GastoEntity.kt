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
    val sincronizado: Boolean = false,
    
    // 🔥 NUEVOS CAMPOS FINANCIEROS
    val esFijo: Boolean = false,          // true para Renta, Sueldos, etc.
    val periodicidad: String = "UNICO",    // "UNICO", "QUINCENAL", "MENSUAL"
    val activo: Boolean = true             // Para dar de baja gastos fijos sin borrarlos
)
