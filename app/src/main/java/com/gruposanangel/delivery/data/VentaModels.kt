package com.gruposanangel.delivery.data

data class PerfilVenta(
    val id: String = "",
    val nombre: String = "",
    val filtros: List<FiltroPerfil> = emptyList()
)

data class FiltroPerfil(
    val marca: String = "",
    val categorias: List<String> = emptyList()
)
