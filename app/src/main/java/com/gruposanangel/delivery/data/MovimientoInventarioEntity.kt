package com.gruposanangel.delivery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movimientos_inventario")
data class MovimientoInventarioEntity(
    @PrimaryKey val id: String, // UUID
    val productoId: String,
    val nombreProducto: String,
    val cantidad: Int,
    val tipo: String, // "CAMBIO_BUENO", "DEVOLUCION_DANIADO", "AJUSTE_FISICO"
    val motivo: String? = null, // Motivo de la devolución o ajuste
    val vendedorId: String,
    val almacenNombre: String? = null, // Almacén afectado
    val clienteId: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val referenciaId: String?, // ID del ticket de venta original
    val sincronizado: Boolean = false,
    val cantidadFisica: Int? = null,   // Lo que se contó manualmente
    val cantidadTeorica: Int? = null, // Lo que decía el sistema
    val metodoAuditoria: String? = null // 🔥 Nuevo: "ARQUEO" o "LIQUIDACION"
)
