package com.gruposanangel.delivery.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ventas")
data class VentaEntity(
    @PrimaryKey val id: String,
    val clienteId: String,
    val clienteNombre: String,
    val clienteImagenUrl: String? = null,
    val total: Double,
    val metodoPago: String,
    val vendedorId: String,
    val vendedorNombre: String? = null,
    val almacenId: String? = null,
    
    // --- AUDITORÍA DE TIEMPO (Blindaje de Fraude) ---
    val fecha: Long,                  // Hora real verificada (para UI y reportes)
    val horaDispositivo: Long,        // Hora que marcaba el teléfono en ese momento
    val horaVerificada: Long,         // Hora calculada por TimeManager
    val alertaTiempo: Boolean,        // True si hay discrepancia > 5 min

    // --- AUDITORÍA GEOGRÁFICA (Visita Real) ---
    val latitudVenta: Double = 0.0,
    val longitudVenta: Double = 0.0,
    val fueraDeRango: Boolean = false,
    val fotoEvidenciaVisita: String? = null,

    val sincronizado: Boolean,
    val firestoreId: String? = null,

    // --- ESTADO DE LA VENTA ---
    val estado: String = "pagada", // "pagada", "CANCELADA"
    val motivoCancelacion: String? = null,
    val canceladoPorNombre: String? = null,
    val fechaCancelacion: Long? = null,
    val motivoVisita: String? = null // 🔥 Nuevo: Motivo de visita sin venta (Tienda cerrada, etc.)
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
    val ventaId: String, 
    val productoId: String, // ID Limpio para Firestore (Base)
    val stockId: String? = null, // ID compuesto (IdProducto_IdAlmacen) para Room
    val nombre: String,
    val precio: Double, // 🛡️ SNAPSHOT INMUTABLE: Precio unitario cobrado (No Nulo)
    val cantidad: Int,

    // --- CLASIFICACIÓN PARA REPORTE DINÁMICO ---
    val marca: String = "Delisa",
    val categoria: String = "General"
)
