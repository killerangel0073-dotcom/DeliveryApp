package com.gruposanangel.delivery.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "clientes")
data class ClienteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nombreNegocio: String,
    val nombreDueno: String,
    val telefono: String,
    val correo: String,
    val tipoExhibidor: String,
    val ubicacionLat: Double,
    val ubicacionLon: Double,
    val fotografiaUrl: String?,      // ruta local si offline, URL de Firebase si ya subido
    val activo: Boolean = true,
    val medio: String = "medio",
    val fechaDeCreacion: Long = System.currentTimeMillis(),
    val syncStatus: Boolean = false  // false = pendiente de subir, true = ya sincronizado
)
