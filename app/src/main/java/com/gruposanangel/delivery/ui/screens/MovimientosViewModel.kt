package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MovimientosUiState(
    val isLoading: Boolean = false,
    val stockOrigen: Map<String, Int> = emptyMap(),
    val error: String? = null,
    val ordenCreadaExito: Boolean = false
)

class MovimientosViewModel(
    private val inventarioRepo: RepositoryInventario,
    private val usuarioRepo: RepositoryUsuario
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovimientosUiState())
    val uiState: StateFlow<MovimientosUiState> = _uiState.asStateFlow()

    // 🔥 OFFLINE-FIRST: Catálogo desde Room usando stateIn como se solicitó
    val catalogoProductos: StateFlow<List<Plantilla_Producto>> = inventarioRepo.obtenerProductosLocal()
        .map { entities ->
            entities.map {
                Plantilla_Producto(
                    id = it.id,
                    nombre = it.nombre,
                    precio = it.precio,
                    cantidad = 0,
                    imagenUrl = it.imagenUrl ?: ""
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Aseguramos que el catálogo esté actualizado al entrar
        viewModelScope.launch {
            inventarioRepo.descargarCatalogoProductos()
        }
    }

    fun cargarStockOrigen(almacen: String) {
        if (almacen == "Selecciona Origen") return
        
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val stock = inventarioRepo.obtenerStockAlmacen(almacen)
                _uiState.update { it.copy(stockOrigen = stock, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun crearOrden(
        origen: String,
        destino: String,
        productosSeleccionados: List<Plantilla_Producto>,
        cantidades: Map<String, Int>,
        onSuccess: (String) -> Unit
    ) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val uid = usuarioRepo.obtenerUsuarioActual()?.uid ?: ""
                val listaProductos = productosSeleccionados.map { p ->
                    mapOf("productoId" to p.id, "cantidad" to (cantidades[p.id] ?: 0))
                }

                val tipoOrden = when {
                    origen == "Compra Producto" -> "COMPRA_PRODUCTO"
                    destino.startsWith("Vendedor") -> "TRANSFERENCIA_VENDEDOR"
                    else -> "TRANSFERENCIA_INTERNA"
                }

                val ordenData = mapOf(
                    "tipo" to tipoOrden,
                    "origen" to origen,
                    "destino" to destino,
                    "productos" to listaProductos,
                    "vendedorId" to uid,
                    "estado" to "PENDIENTE",
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )

                val docId = inventarioRepo.crearOrdenTransferencia(ordenData)
                _uiState.update { it.copy(isLoading = false, ordenCreadaExito = true) }
                onSuccess(docId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun resetEstado() {
        _uiState.update { it.copy(ordenCreadaExito = false, error = null) }
    }
}

class MovimientosViewModelFactory(
    private val inventarioRepo: RepositoryInventario,
    private val usuarioRepo: RepositoryUsuario
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MovimientosViewModel(inventarioRepo, usuarioRepo) as T
    }
}
