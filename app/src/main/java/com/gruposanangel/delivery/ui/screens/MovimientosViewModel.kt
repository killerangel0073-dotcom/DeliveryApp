package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.utilidades.enviarNotificacion
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MovimientosUiState(
    val isLoading: Boolean = false,
    val stockOrigen: Map<String, Int> = emptyMap(),
    val error: String? = null,
    val ordenCreadaExito: Boolean = false,
    val listaAlmacenes: List<String> = emptyList()
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
            // 🛡️ FILTRO: Solo tomar productos base (ID simple) para evitar duplicados en el catálogo
            entities.filter { !it.id.contains("_") }
                .map {
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
            cargarAlmacenes()
        }
    }

    private fun cargarAlmacenes() {
        viewModelScope.launch {
            val lista = inventarioRepo.obtenerListaAlmacenes()
            _uiState.update { it.copy(listaAlmacenes = lista) }
        }
    }

    fun cargarStockOrigen(almacen: String) {
        if (almacen == "Selecciona Origen") return
        
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                // 🛒 Si es compra, mostramos el stock del Almacen Huasteca como referencia visual
                val almacenConsultar = if (almacen == "Compra Producto") "Almacen Huasteca" else almacen
                val stock = inventarioRepo.obtenerStockAlmacen(almacenConsultar)
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
                val uid = usuarioRepo.obtenerUsuarioActual()?.uid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val listaProductos = productosSeleccionados.map { p ->
                    mapOf(
                        "productoId" to p.id,
                        "nombre" to p.nombre,
                        "precio" to p.precio,
                        "imagenUrl" to p.imagenUrl,
                        "cantidad" to (cantidades[p.id] ?: 0)
                    )
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
                
                // 🔔 NOTIFICAR AL DESTINO (VENDEDOR O ALMACÉN)
                if (tipoOrden == "TRANSFERENCIA_VENDEDOR" || tipoOrden == "TRANSFERENCIA_INTERNA") {
                    val tokens = usuarioRepo.obtenerTokensPorDestino(destino)
                    tokens.forEach { token ->
                        enviarNotificacion(
                            token = token,
                            titulo = "Nueva Carga Asignada",
                            mensaje = "Tienes una nueva carga desde $origen. Toca para aceptar.",
                            tipo = "CARGA_NUEVA",
                            idExtra = docId
                        )
                    }
                }

                _uiState.update { it.copy(isLoading = false, ordenCreadaExito = true) }
                onSuccess(docId)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun confirmarCargaDirecta(
        origen: String,
        destino: String,
        productosSeleccionados: List<Plantilla_Producto>,
        cantidadesContadas: Map<String, Int>, // Cambiamos nombre para claridad: conteo físico
        isLiquidation: Boolean = false,      // Nuevo flag
        stockTeorico: Map<String, Int> = emptyMap(), // Lo que el sistema cree que hay
        onSuccess: () -> Unit
    ) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val usuarioRoom = usuarioRepo.obtenerUsuarioActual()
                val uidActual = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val uid = if (usuarioRoom?.uid?.isNotEmpty() == true) usuarioRoom.uid else uidActual
                
                val nombreAlmacen = if (isLiquidation) origen else (usuarioRoom?.ultimoAlmacenNombre ?: destino)
                val folioOperacion = if (isLiquidation) "AUDIT_${System.currentTimeMillis()}" else "DIRECT_LOAD_${System.currentTimeMillis()}"

                productosSeleccionados.forEach { p ->
                    val cantidadFisica = cantidadesContadas[p.id] ?: 0
                    
                    // --- LÓGICA DE DIFERENCIAL (PARA ARQUEO) ---
                    var cantidadAMover = cantidadFisica
                    var tipoAjuste = "CARGA_INVENTARIO"
                    
                    if (isLiquidation) {
                        val teorico = stockTeorico[p.id] ?: 0
                        // El diferencial es lo que falta o sobra para llegar al físico
                        cantidadAMover = cantidadFisica - teorico
                        
                        tipoAjuste = when {
                            cantidadAMover > 0 -> "AJUSTE_ARQUEO_SOBRANTE"
                            cantidadAMover < 0 -> "AJUSTE_ARQUEO_FALTANTE"
                            else -> "AJUSTE_ARQUEO_OK"
                        }
                    }

                    val baseId = if (p.id.contains("_")) p.id.split("_")[0] else p.id
                    val movimiento = com.gruposanangel.delivery.data.MovimientoInventarioEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        productoId = baseId,
                        nombreProducto = p.nombre,
                        cantidad = Math.abs(cantidadAMover), // Diferencia absoluta
                        tipo = if (isLiquidation) tipoAjuste else "CARGA_INVENTARIO",
                        vendedorId = uid,
                        almacenNombre = nombreAlmacen,
                        clienteId = null,
                        referenciaId = folioOperacion,
                        timestamp = System.currentTimeMillis(),
                        sincronizado = false,
                        cantidadFisica = if (isLiquidation) cantidadFisica else null,
                        cantidadTeorica = if (isLiquidation) (stockTeorico[p.id] ?: 0) else null
                    )
                    
                    // Solo aplicar ajuste de stock si realmente hubo diferencia
                    val cantidadLocal = if (tipoAjuste == "AJUSTE_ARQUEO_FALTANTE") -Math.abs(cantidadAMover) else Math.abs(cantidadAMover)
                    inventarioRepo.registrarMovimientoCarga(movimiento, p.copy(cantidad = cantidadLocal))
                }

                // 2. SI ES LIQUIDACIÓN (RETORNO A BODEGA), HACEMOS EL TRASPASO DEL STOCK FÍSICO CONTADO
                // (Esta parte solo corre si el switch de retornarABodega estaba ON en la UI, pero 
                // para esta lógica simple de arqueo, ya ajustamos el stock del vendedor arriba)

                _uiState.update { it.copy(isLoading = false, ordenCreadaExito = true) }
                onSuccess()
            } catch (e: Exception) {
                Log.e("MOV_VM", "Error en confirmarCargaDirecta", e)
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
