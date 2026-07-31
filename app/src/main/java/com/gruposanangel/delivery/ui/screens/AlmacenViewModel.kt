package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AlmacenUiState(
    val almacenes: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class AlmacenViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AlmacenUiState())
    val uiState: StateFlow<AlmacenUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()

    init {
        cargarAlmacenes()
    }

    fun cargarAlmacenes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val snapshot = db.collection("almacenes").get().await()
                val lista = snapshot.documents.map { it.id }.sorted()
                _uiState.update { it.copy(almacenes = lista, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun crearAlmacen(nombre: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // En este sistema, el ID del documento es el nombre del almacén
                db.collection("almacenes").document(nombre).set(mapOf("nombre" to nombre)).await()
                cargarAlmacenes()
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun eliminarAlmacen(nombre: String) {
        viewModelScope.launch {
            try {
                db.collection("almacenes").document(nombre).delete().await()
                cargarAlmacenes()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun editarAlmacen(nombreAnterior: String, nuevoNombre: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // Firestore no permite renombrar documentos. Debemos crear uno nuevo y borrar el anterior.
                // OJO: Esto es peligroso si hay muchas referencias, pero para esta app parece que se usa el nombre como ID.
                db.runTransaction { transaction ->
                    val oldRef = db.collection("almacenes").document(nombreAnterior)
                    val newRef = db.collection("almacenes").document(nuevoNombre)
                    
                    transaction.set(newRef, mapOf("nombre" to nuevoNombre))
                    transaction.delete(oldRef)
                }.await()
                
                cargarAlmacenes()
                onSuccess()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
