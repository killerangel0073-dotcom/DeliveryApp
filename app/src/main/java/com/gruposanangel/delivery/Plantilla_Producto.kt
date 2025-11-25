package com.gruposanangel.delivery.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Plantilla_Producto(
    val id: String = "",
    val nombre: String = "",
    val precio: Double = 0.0,
    val cantidad: Int = 0,          // lo que quiere vender
    val cantidadDisponible: Int = 0, // lo que hay en inventario
    val imagenUrl: String = ""
) : Parcelable
