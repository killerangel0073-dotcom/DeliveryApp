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
    val licenciaConducir: String? = null // licencia
)
