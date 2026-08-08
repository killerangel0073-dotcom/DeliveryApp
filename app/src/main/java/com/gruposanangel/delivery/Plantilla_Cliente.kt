package com.gruposanangel.delivery

class Plantilla_Cliente (
    val id: String,
    val nombreNegocio: String,
    val nombreDueno: String,
    val fotografiaCliente: String,
    val activo: Boolean,
    val distanciaMetros: Float = -1f,
    val distanciaTexto: String = "",
    val rutaId: String? = null,
    val visitadoAnteriormente: Boolean = false, // 🔥 Nuevo: Guía de ciclo pasado
    val montoCompraPasada: Double = 0.0          // 🔥 Nuevo: Para mostrar cuánto compró
)
