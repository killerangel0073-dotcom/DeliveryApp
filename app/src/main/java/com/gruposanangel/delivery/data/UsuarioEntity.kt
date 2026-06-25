package com.gruposanangel.delivery.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class UsuarioEntity(
    @PrimaryKey val uid: String,         // UID de Firebase
    val nombre: String,                  // nombre completo
    val email: String? = null,           // correo
    val photoUrl: String? = null,        // foto
    val puestoTrabajo: String? = null,   // puesto
    val licenciaConducir: String? = null, // licencia

    // NUEVOS CAMPOS que traemos de Firestore
    val activo: Boolean? = null,
    val status: String = "ACTIVO",           // "ACTIVO", "SUSPENDIDO", "BAJA"
    val fechaBaja: Long? = null,             // Timestamp de la baja
    val motivoBaja: String? = null,          // Razón de la baja

    val createdTime: Long? = null,           // epoch millis
    val credencialElector: String? = null,
    val jefeDirectoNombre: String? = null, // <-- NUEVO
    val jefeDirectoId: String? = null,

    // --- NUEVOS CAMPOS PARA RUTA Y ALMACÉN (ya tenías estos) ---
    val ultimaRutaId: String? = null,
    val ultimaRutaNombre: String? = null,
    val ultimoAlmacenId: String? = null,
    val ultimoAlmacenNombre: String? = null,

    // --- AUDITORÍA DE LICENCIA (IA VALIDATION) ---
    val licenciaFotoUrl: String? = null,
    val licenciaVencimiento: Long? = null,
    val licenciaEstado: String? = "PENDIENTE", // "VIGENTE", "VENCIDA", "RECHAZADA"
    val licenciaUltimaRevision: Long? = null,

    // --- AUDITORÍA DE INE (IA VALIDATION) ---
    val ineFotoUrl: String? = null,
    val ineVencimiento: Long? = null,
    val ineEstado: String? = "PENDIENTE", // "VIGENTE", "VENCIDA", "RECHAZADA"
    val ineUltimaRevision: Long? = null
)

