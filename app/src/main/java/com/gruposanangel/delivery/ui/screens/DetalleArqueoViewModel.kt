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
    val imagenUrl: String
)

data class DetalleArqueoUiState(
    val isLoading: Boolean = false,
    val productos: List<ProductoArqueoDetalle> = emptyList(),
    val fecha: Long? = null,
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
            try {
                // Buscamos todos los ajustes que tengan este folio de auditoría
                val snap = db.collection("ajustes_inventario")
                    .whereEqualTo("referenciaId", arqueoId)
                    .get()
                    .await()

                val catalogo = inventarioRepo.obtenerProductosLocal().first()
                var fechaArqueo: Long? = null

                val productos = snap.documents.mapNotNull { doc ->
                    if (fechaArqueo == null) {
                        val tsRaw = doc.get("timestamp")
                        fechaArqueo = when (tsRaw) {
                            is com.google.firebase.Timestamp -> tsRaw.toDate().time
                            is Number -> tsRaw.toLong()
                            else -> null
                        }
                    }
                    
                    val prodId = doc.getString("productoId") ?: return@mapNotNull null
                    val tipo = doc.getString("tipo")
                    val cantDiff = doc.getLong("cantidad")?.toInt() ?: 0
                    val info = catalogo.find { it.productoId == prodId }
                    
                    ProductoArqueoDetalle(
                        id = prodId,
                        nombre = info?.nombre ?: doc.getString("nombreProducto") ?: "Producto",
                        precio = info?.precio ?: 0.0,
                        diferencia = if (tipo == "AJUSTE_ARQUEO_FALTANTE") -cantDiff else if (tipo == "AJUSTE_ARQUEO_SOBRANTE") cantDiff else 0,
                        fisico = doc.getLong("cantidadFisica")?.toInt(),
                        teorico = doc.getLong("cantidadTeorica")?.toInt(),
                        imagenUrl = info?.imagenUrl ?: ""
                    )
                }.sortedByDescending { Math.abs(it.diferencia) } // Primero los errores

                _uiState.update { it.copy(productos = productos, fecha = fechaArqueo, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
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
