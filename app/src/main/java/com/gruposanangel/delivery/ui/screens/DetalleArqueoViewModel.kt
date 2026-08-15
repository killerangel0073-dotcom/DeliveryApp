package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ProductoArqueoDetalle(
    val id: String,
    val nombre: String,
    val precio: Double,
    val diferencia: Int,
    val fisico: Int?,
    val teorico: Int?,
    val imagenUrl: String,
    val categoria: String = "General" // 🔥 NUEVO: Para agrupación
)

data class DetalleArqueoUiState(
    val isLoading: Boolean = false,
    val productos: List<ProductoArqueoDetalle> = emptyList(),
    val fecha: Long? = null,
    val metodoAuditoria: String? = null,
    val error: String? = null
)

class DetalleArqueoViewModel(
    private val arqueoId: String,
    private val inventarioRepo: RepositoryInventario
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetalleArqueoUiState())
    val uiState: StateFlow<DetalleArqueoUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()

    init {
        cargarDetalle()
    }

    private fun cargarDetalle() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            // 1. Observar datos locales (Offline-First)
            inventarioRepo.obtenerMovimientosPorReferenciaLocal(arqueoId)
                .collect { movimientosLocales ->
                    if (movimientosLocales.isNotEmpty()) {
                        procesarMovimientos(movimientosLocales)
                    }
                    
                    // Si no hay locales o queremos asegurar frescura, disparamos sync de Firestore en background
                    dispararSyncFirestore()
                }
        }
    }

    private suspend fun procesarMovimientos(movimientos: List<com.gruposanangel.delivery.data.MovimientoInventarioEntity>) {
        try {
            val catalogo = inventarioRepo.obtenerProductosLocal().first()
            var fechaArqueo: Long? = null
            var metodo: String? = null

            val productos = movimientos.mapNotNull { mov ->
                if (fechaArqueo == null) {
                    fechaArqueo = mov.timestamp
                }
                if (metodo == null) {
                    metodo = mov.metodoAuditoria
                }
                
                val prodId = mov.productoId
                val info = catalogo.find { it.productoId == prodId }
                
                ProductoArqueoDetalle(
                    id = prodId,
                    nombre = info?.nombre ?: mov.nombreProducto,
                    precio = info?.precio ?: 0.0,
                    diferencia = if (mov.tipo == "AJUSTE_ARQUEO_FALTANTE") -mov.cantidad else if (mov.tipo == "AJUSTE_ARQUEO_SOBRANTE") mov.cantidad else 0,
                    fisico = mov.cantidadFisica,
                    teorico = mov.cantidadTeorica,
                    imagenUrl = info?.imagenUrl ?: "",
                    categoria = info?.categoria ?: "General"
                )
            }.sortedWith(compareBy<ProductoArqueoDetalle>({ it.categoria }, { it.nombre }))

            _uiState.update { it.copy(productos = productos, fecha = fechaArqueo, metodoAuditoria = metodo, isLoading = false) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    private fun dispararSyncFirestore() {
        viewModelScope.launch {
            try {
                val snap = db.collection("ajustes_inventario")
                    .whereEqualTo("referenciaId", arqueoId)
                    .get()
                    .await()

                if (!snap.isEmpty) {
                    // Aquí podrías actualizar Room si hay cambios, 
                    // pero por ahora dejamos que el listener local reaccione si otros procesos guardan en Room.
                    // O simplemente mapear y actualizar el estado si es necesario.
                }
            } catch (e: Exception) {
                // Silencioso si falla el background sync
            }
        }
    }
}

class DetalleArqueoViewModelFactory(
    private val arqueoId: String,
    private val repo: RepositoryInventario
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DetalleArqueoViewModel(arqueoId, repo) as T
    }
}
