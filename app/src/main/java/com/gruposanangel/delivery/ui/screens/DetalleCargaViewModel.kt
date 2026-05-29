package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.data.ProductoDao
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantila_carga
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class DetalleCargaUiState(
    val isLoading: Boolean = false,
    val carga: Plantila_carga? = null,
    val productos: List<Plantilla_Producto> = emptyList(),
    val error: String? = null,
    val aceptadaExito: Boolean = false
)

class DetalleCargaViewModel(
    private val inventarioRepo: RepositoryInventario,
    private val productoDao: ProductoDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetalleCargaUiState())
    val uiState: StateFlow<DetalleCargaUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()

    fun cargarDetalle(cargaId: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val doc = db.collection("ordenesTransferencia").document(cargaId).get().await()
                if (doc.exists()) {
                    val productosRaw = doc.get("productos") as? List<Map<String, Any>> ?: emptyList()
                    val productosCompletos = productosRaw.map { item ->
                        val id = item["productoId"] as? String ?: ""
                        val cantidad = (item["cantidad"] as? Long)?.toInt() ?: 0
                        val nombreCarga = item["nombre"] as? String
                        val precioCarga = (item["precio"] as? Number)?.toDouble() ?: 0.0
                        val pLocal = productoDao.getProductoById(id)
                        Plantilla_Producto(id = id, nombre = pLocal?.nombre ?: nombreCarga ?: "ID: $id", precio = if (pLocal != null && pLocal.precio > 0) pLocal.precio else precioCarga, imagenUrl = pLocal?.imagenUrl ?: "", cantidad = cantidad)
                    }
                    _uiState.update { it.copy(isLoading = false, productos = productosCompletos, carga = Plantila_carga(id = doc.id, nombreCarga = "Carga desde " + (doc.getString("origen") ?: "Almacén"), aceptada = doc.getString("estado") == "COMPLETADA" || doc.getString("estado") == "ACEPTADA")) }
                }
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun aceptarCarga() {
        val state = _uiState.value; if (state.carga == null || state.productos.isEmpty()) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val nombreAlmacen = inventarioRepo.getAlmacenVendedor(uid)

                // 🔥 Enriquecer los datos en la nube antes de disparar la Cloud Function
                val productosData = state.productos.map { p ->
                    mapOf(
                        "productoId" to p.id,
                        "nombre" to p.nombre,
                        "precio" to p.precio,
                        "imagenUrl" to p.imagenUrl,
                        "cantidad" to p.cantidad
                    )
                }

                val docRef = db.collection("ordenesTransferencia").document(state.carga.id)
                db.runTransaction { transaction ->
                    transaction.update(docRef, "productos", productosData)
                    transaction.update(docRef, "estado", "ACEPTADA")
                    null
                }.await()

                inventarioRepo.aplicarCargaLocal(state.productos, nombreAlmacen)
                _uiState.update { it.copy(isLoading = false, aceptadaExito = true) }
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}

class DetalleCargaViewModelFactory(private val inventarioRepo: RepositoryInventario, private val productoDao: ProductoDao) : ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = DetalleCargaViewModel(inventarioRepo, productoDao) as T }
