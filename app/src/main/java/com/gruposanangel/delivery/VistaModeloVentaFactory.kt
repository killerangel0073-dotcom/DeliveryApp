package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaRepository

class VistaModeloVentaFactory(
    private val repositoryInventario: RepositoryInventario,
    private val ventaRepository: VentaRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VistaModeloVenta::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VistaModeloVenta(repositoryInventario, ventaRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

