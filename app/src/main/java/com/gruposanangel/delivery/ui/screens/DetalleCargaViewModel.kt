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
                // 1. Intentar buscar en Firebase (Carga Normal)
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
                    val fechaCarga = doc.getTimestamp("timestamp")?.toDate()
                    _uiState.update { it.copy(
                        isLoading = false, 
                        productos = productosCompletos, 
                        carga = Plantila_carga(
                            id = doc.id, 
                            nombreCarga = "Carga desde " + (doc.getString("origen") ?: "Almacén"), 
                            aceptada = doc.getString("estado") == "COMPLETADA" || doc.getString("estado") == "ACEPTADA",
                            fecha = fechaCarga
                        )
                    ) }
                } else {
                    // 2. Si no existe en Firebase, buscar en Room (Carga de Emergencia)
                    val movsLocales = inventarioRepo.obtenerMovimientosDesde(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "", 0L)
                        .filter { it.referenciaId == cargaId || it.id == cargaId }
                    
                    if (movsLocales.isNotEmpty()) {
                        val primerMov = movsLocales.first()
                        val productosCompletos = movsLocales.map { mov ->
                            val pLocal = productoDao.getProductoById(mov.productoId)
                            Plantilla_Producto(
                                id = mov.productoId,
                                nombre = mov.nombreProducto,
                                precio = pLocal?.precio ?: 0.0,
                                cantidad = mov.cantidad,
                                imagenUrl = pLocal?.imagenUrl ?: ""
                            )
                        }
                        
                        _uiState.update { it.copy(
                            isLoading = false,
                            productos = productosCompletos,
                            carga = Plantila_carga(
                                id = cargaId,
                                nombreCarga = "CARGA MANUAL (EMERGENCIA)",
                                aceptada = true,
                                fecha = java.util.Date(primerMov.timestamp)
                            )
                        ) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "No se encontró el detalle de la carga") }
                    }
                }
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun aceptarCarga() {
        val state = _uiState.value
        // 🛡️ BLINDAJE: Evitar múltiples ejecuciones si ya está cargando o ya se aceptó
        if (state.isLoading || state.aceptadaExito || state.carga == null || state.productos.isEmpty()) return

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val nombreAlmacen = inventarioRepo.getAlmacenVendedor(uid) ?: "Almacen Principal"

                // 🔥 ESTRATEGIA DE PREVENCIÓN DE DUPLICIDAD (Idempotencia)
                state.productos.forEach { p ->
                    // Limpiamos el ID por si trae sufijos de almacén
                    val baseId = if (p.id.contains("_")) p.id.split("_")[0] else p.id
                    
                    val movimiento = com.gruposanangel.delivery.data.MovimientoInventarioEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        productoId = baseId,
                        nombreProducto = p.nombre,
                        cantidad = p.cantidad,
                        tipo = "CARGA_INVENTARIO",
                        vendedorId = uid,
                        almacenNombre = nombreAlmacen,
                        clienteId = null,
                        referenciaId = state.carga.id, 
                        // 🛡️ MARCADO COMO SINCRONIZADO:
                        // Como esta carga viene de una 'ordenesTransferencia', el backend ya se encarga
                        // de sumar el stock al detectar el cambio de estado. 
                        // Marcarlo como sincronizado aquí evita que la lógica local lo sume doble.
                        sincronizado = true, 
                        timestamp = System.currentTimeMillis()
                    )
                    // Solo actualizamos el stock localmente para feedback inmediato, 
                    // sin intentar subir un ajuste redundante a Firebase.
                    inventarioRepo.registrarMovimientoCargaLocal(movimiento, p.copy(id = baseId))
                }

                // 2. Intentamos marcar en la nube como aceptada (Idempotencia por ID de carga)
                try {
                    db.collection("ordenesTransferencia").document(state.carga.id)
                        .update("estado", "ACEPTADA").await()
                } catch (e: Exception) {
                    // Si falla el internet, no importa. El Worker de Inventario 
                    // intentará subir los MovimientoInventarioEntity después.
                }

                _uiState.update { it.copy(isLoading = false, aceptadaExito = true) }
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}

class DetalleCargaViewModelFactory(private val inventarioRepo: RepositoryInventario, private val productoDao: ProductoDao) : ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = DetalleCargaViewModel(inventarioRepo, productoDao) as T }
