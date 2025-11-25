package com.gruposanangel.delivery.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "clientes")
data class ClienteEntity(
    @PrimaryKey val id: String,
    val nombreNegocio: String,
    val nombreDueno: String,
    val telefono: String,
    val correo: String,
    val tipoExhibidor: String,
    val ubicacionLat: Double,
    val ubicacionLon: Double,
    val fotografiaUrl: String?,      // local o URL remota

    val activo: Boolean = true,
    val medio: String = "medio",

    val fechaDeCreacion: Long = System.currentTimeMillis(),

    val syncStatus: Boolean = false, // false = pendiente, true = sincronizado

    // 🔥 Nuevos campos
    val ownerUid: String = "",
    val lastModified: Long = System.currentTimeMillis()
)
