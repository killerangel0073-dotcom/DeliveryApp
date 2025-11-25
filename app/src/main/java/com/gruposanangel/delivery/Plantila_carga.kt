package com.gruposanangel.delivery.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Plantila_carga(
    val id: String = "",
    val plantillaProductos: List<Plantilla_Producto> = emptyList(),
    val nombreCarga: String = "Carga de Almacén",
    val aceptada: Boolean = false // <-- nueva propiedad
) : Parcelable
