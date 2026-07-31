package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ProductoFormUiState(
    val marcas: List<String> = emptyList(),
    val categoriasPorMarca: Map<String, List<String>> = emptyMap(),
    val isLoading: Boolean = false
)

class ProductoFormViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProductoFormUiState())
    val uiState: StateFlow<ProductoFormUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()

    init {
        cargarConfiguracion()
    }

    private fun cargarConfiguracion() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 1. Cargar Marcas
                val marcasSnap = db.collection("marca").get().await()
                val listaMarcas = marcasSnap.documents.mapNotNull { it.getString("id") ?: it.getString("nombre") }.distinct()

                // 2. Cargar Categorías
                val catsSnap = db.collection("Categorias").get().await()
                val mapaCategorias = mutableMapOf<String, MutableList<String>>()
                
                catsSnap.documents.forEach { doc ->
                    val marcaId = doc.getString("marca_id") ?: "Delisa"
                    val catNombre = doc.getString("nombre") ?: doc.getString("id") ?: ""
                    if (catNombre.isNotEmpty()) {
                        mapaCategorias.getOrPut(marcaId) { mutableListOf() }.add(catNombre)
                    }
                }

                _uiState.update { it.copy(
                    marcas = listaMarcas,
                    categoriasPorMarca = mapaCategorias,
                    isLoading = false
                ) }
            } catch (e: Exception) {
                Log.e("ProductoFormVM", "Error cargando config", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
