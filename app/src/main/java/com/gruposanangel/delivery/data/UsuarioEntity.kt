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
    val createdTime: Long? = null,           // epoch millis
    val credencialElector: String? = null,
    val jefeDirectoNombre: String? = null, // <-- NUEVO
    val jefeDirectoId: String? = null,

    // --- NUEVOS CAMPOS PARA RUTA Y ALMACÉN (ya tenías estos) ---
    val ultimaRutaId: String? = null,
    val ultimaRutaNombre: String? = null,
    val ultimoAlmacenId: String? = null,
    val ultimoAlmacenNombre: String? = null
)

