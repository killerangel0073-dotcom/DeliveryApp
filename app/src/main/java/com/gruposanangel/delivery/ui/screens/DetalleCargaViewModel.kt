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
import kotlinx.coroutines.delay

data class DetalleCargaUiState(
    val isLoading: Boolean = false,
    val carga: Plantila_carga? = null,
    val productos: List<Plantilla_Producto> = emptyList(),
    val error: String? = null,
    val aceptadaExito: Boolean = false,
    val puestoTrabajo: String? = null, // 🔥 NUEVO: Para control de permisos
    val destinoAlmacen: String? = null // 🔥 NUEVO: Para lógica de Huasteca
)

class DetalleCargaViewModel(
    private val inventarioRepo: RepositoryInventario,
    private val productoDao: ProductoDao,
    private val usuarioRepo: com.gruposanangel.delivery.RepositoryUsuario
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetalleCargaUiState())
    val uiState: StateFlow<DetalleCargaUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()

    init {
        // 🔥 Obtener el puesto de trabajo del usuario actual
        viewModelScope.launch {
            val user = usuarioRepo.obtenerUsuarioActual()
            _uiState.update { it.copy(puestoTrabajo = user?.puestoTrabajo) }
        }
    }

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
                        Plantilla_Producto(
                            id = id, 
                            nombre = pLocal?.nombre ?: nombreCarga ?: "ID: $id", 
                            precio = if (pLocal != null && pLocal.precio > 0) pLocal.precio else precioCarga, 
                            imagenUrl = pLocal?.imagenUrl ?: "", 
                            cantidad = cantidad,
                            marca = pLocal?.marca ?: "Delisa",
                            categoria = pLocal?.categoria ?: "General"
                        )
                    }.sortedWith(compareBy<Plantilla_Producto>({ it.categoria }, { it.nombre }))

                    val fechaCarga = doc.getTimestamp("timestamp")?.toDate()
                    val destino = doc.getString("destino") ?: ""
                    val estadoDoc = doc.getString("estado") ?: "PENDIENTE"
                    
                    _uiState.update { it.copy(
                        isLoading = false, 
                        productos = productosCompletos, 
                        destinoAlmacen = destino,
                        carga = Plantila_carga(
                            id = doc.id, 
                            nombreCarga = "Carga desde " + (doc.getString("origen") ?: "Almacén"), 
                            aceptada = estadoDoc == "COMPLETADA" || estadoDoc == "ACEPTADA",
                            fecha = fechaCarga,
                            estado = estadoDoc
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
                                imagenUrl = pLocal?.imagenUrl ?: "",
                                marca = pLocal?.marca ?: "Delisa",
                                categoria = pLocal?.categoria ?: "General"
                            )
                        }.sortedWith(compareBy<Plantilla_Producto>({ it.categoria }, { it.nombre }))
                        
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
        // 🛡️ BLINDAJE: Evitar múltiples ejecuciones concurrentes
        if (state.isLoading || state.aceptadaExito || state.carga == null || state.productos.isEmpty()) return

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val cargaId = state.carga.id
                
                // 1. 🛡️ VERIFICAR SI YA SE PROCESÓ LOCALMENTE (Evita doble suma de stock)
                val movsExistentes = inventarioRepo.obtenerMovimientosDesde(uid, 0L)
                    .filter { it.referenciaId == cargaId && it.tipo == "CARGA_INVENTARIO" }
                
                val yaProcesadoLocal = movsExistentes.isNotEmpty()

                if (!yaProcesadoLocal) {
                    val nombreAlmacen = inventarioRepo.getAlmacenVendedor(uid) ?: "Almacen Principal"

                    state.productos.forEach { p ->
                        val baseId = if (p.id.contains("_")) p.id.split("_")[0] else p.id
                        val movimiento = com.gruposanangel.delivery.data.MovimientoInventarioEntity(
                            id = "AUTO_${cargaId}_$baseId", // 🆔 ID DETERMINÍSTICO: Blindaje total contra duplicados
                            productoId = baseId,
                            nombreProducto = p.nombre,
                            cantidad = p.cantidad,
                            tipo = "CARGA_INVENTARIO",
                            vendedorId = uid,
                            almacenNombre = nombreAlmacen,
                            clienteId = null,
                            referenciaId = cargaId, 
                            sincronizado = true, 
                            timestamp = System.currentTimeMillis()
                        )
                        inventarioRepo.registrarMovimientoCargaLocal(movimiento, p.copy(id = baseId))
                    }
                }

                // 2. Intentar marcar en la nube como aceptada
                try {
                    db.collection("ordenesTransferencia").document(cargaId)
                        .update("estado", "ACEPTADA").await()
                    _uiState.update { it.copy(isLoading = false, aceptadaExito = true) }
                } catch (e: Exception) {
                    // Si falla el internet, notificamos pero permitimos salir porque localmente ya se sumó
                    _uiState.update { it.copy(isLoading = false, error = "Carga guardada localmente. Sincronización en curso.") }
                    delay(1500)
                    _uiState.update { it.copy(aceptadaExito = true) }
                }

            } catch (e: Exception) { 
                _uiState.update { it.copy(isLoading = false, error = e.message) } 
            }
        }
    }
}

class DetalleCargaViewModelFactory(
    private val inventarioRepo: RepositoryInventario, 
    private val productoDao: ProductoDao,
    private val usuarioRepo: com.gruposanangel.delivery.RepositoryUsuario
) : ViewModelProvider.Factory { 
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = DetalleCargaViewModel(inventarioRepo, productoDao, usuarioRepo) as T 
}
