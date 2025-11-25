package com.gruposanangel.delivery

import com.gruposanangel.delivery.model.Plantilla_Producto

data class Plantilla_Venta(
    val id: String = "",                  // ID único de la venta
    val cliente: String = "",             // Nombre del cliente
    val clienteId: String = "",           // ID del cliente
    val fecha: Long = System.currentTimeMillis(),  // Fecha de creación
    val total: Double = 0.0,              // Total de la venta
    val productos: List<Plantilla_Producto> = emptyList(), // Lista de productos
    val sincronizado: Boolean = true      // Para sincronizar con Room
)
