package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.launch

class DetalleCargaViewModel(
    private val inventarioRepo: RepositoryInventario
) : ViewModel() {

    fun aceptarCargaLocal(productos: List<Plantilla_Producto>, onComplete: () -> Unit) {
        viewModelScope.launch {
            inventarioRepo.aplicarCargaLocal(productos)
            onComplete()
        }
    }
}

class DetalleCargaViewModelFactory(
    private val inventarioRepo: RepositoryInventario
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DetalleCargaViewModel(inventarioRepo) as T
    }
}
