package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.utilidades.enviarNotificacion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class MovimientosUiState(
    val isLoading: Boolean = false,
    val stockOrigen: Map<String, Int> = emptyMap(),
    val error: String? = null,
    val ordenCreadaExito: Boolean = false,
    val listaAlmacenes: List<String> = emptyList(),
    val origen: String = "Selecciona Origen",
    val destino: String = "Selecciona Destino",
    val cantidades: Map<String, Int> = emptyMap(),
    val isAlmacenRole: Boolean = false,
    val isAdmin: Boolean = false,
    val editOrderId: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MovimientosViewModel(
    private val inventarioRepo: RepositoryInventario,
    private val usuarioRepo: RepositoryUsuario,
    private val prefs: android.content.SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovimientosUiState())
    val uiState: StateFlow<MovimientosUiState> = _uiState.asStateFlow()

    private val _almacenSeleccionado = MutableStateFlow<String?>(null)

    val catalogoProductos: StateFlow<List<Plantilla_Producto>> = inventarioRepo.obtenerProductosLocal()
        .map { entities ->
            entities.filter { !it.id.contains("_") }
                .map {
                    Plantilla_Producto(
                        id = it.id,
                        nombre = it.nombre,
                        precio = it.precio,
                        cantidad = 0,
                        imagenUrl = it.imagenUrl ?: "",
                        marca = it.marca,
                        categoria = it.categoria
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        _almacenSeleccionado
            .filterNotNull()
            .filter { it != "Selecciona Origen" }
            .flatMapLatest { almacen ->
                val almacenConsultar = if (almacen == "Compra Producto") "Almacen Huasteca" else almacen
                inventarioRepo.obtenerStockAlmacenFlow(almacenConsultar)
                    .onStart { _uiState.update { it.copy(isLoading = true) } }
                    .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
            }
            .onEach { stock ->
                _uiState.update { it.copy(stockOrigen = stock, isLoading = false) }
            }
            .launchIn(viewModelScope)

        recuperarEstado()
        
        viewModelScope.launch {
            val user = usuarioRepo.obtenerUsuarioActual()
            val puesto = user?.puestoTrabajo?.trim() ?: ""
            val esAlmacen = puesto.contains("Almacen", ignoreCase = true) || puesto.contains("Bodega", ignoreCase = true)
            val esAdmin = puesto == "CEO" || puesto == "Gerente General"
            
            _uiState.update { it.copy(
                isAlmacenRole = esAlmacen,
                isAdmin = esAdmin
            ) }
            
            // 🔥 Si es de almacén (y no admin), forzar Huasteca y no permitir cambiar
            if (esAlmacen && !esAdmin) {
                actualizarOrigen("Almacen Huasteca")
            }
        }

        viewModelScope.launch {
            inventarioRepo.descargarCatalogoProductos()
            cargarAlmacenes()
        }
    }

    private fun recuperarEstado() {
        val origen = prefs.getString("origen", "Selecciona Origen") ?: "Selecciona Origen"
        val destino = prefs.getString("destino", "Selecciona Destino") ?: "Selecciona Destino"
        val cantidadesJson = prefs.getString("cantidades", "{}") ?: "{}"
        val cantidades = mutableMapOf<String, Int>()
        try {
            val clean = cantidadesJson.removeSurrounding("{", "}")
            if (clean.isNotEmpty()) {
                clean.split(",").forEach { pair ->
                    val parts = pair.split(":")
                    if (parts.size == 2) {
                        cantidades[parts[0].trim()] = parts[1].trim().toInt()
                    }
                }
            }
        } catch (e: Exception) { Log.e("MOV_VM", "Error", e) }

        _uiState.update { it.copy(origen = origen, destino = destino, cantidades = cantidades) }
        _almacenSeleccionado.value = origen
    }

    private fun guardarEstado() {
        val state = _uiState.value
        val cantidadesJson = state.cantidades.entries.joinToString(",", prefix = "{", postfix = "}") { 
            "${it.key}:${it.value}" 
        }
        prefs.edit().apply {
            putString("origen", state.origen)
            putString("destino", state.destino)
            putString("cantidades", cantidadesJson)
            apply()
        }
    }

    fun actualizarOrigen(nuevo: String) {
        _uiState.update { it.copy(origen = nuevo) }
        _almacenSeleccionado.value = nuevo
        guardarEstado()
    }

    fun actualizarDestino(nuevo: String) {
        _uiState.update { it.copy(destino = nuevo) }
        guardarEstado()
    }

    fun actualizarCantidad(productoId: String, cantidad: Int) {
        val nuevasCantidades = _uiState.value.cantidades.toMutableMap()
        if (cantidad <= 0) nuevasCantidades.remove(productoId)
        else nuevasCantidades[productoId] = cantidad
        _uiState.update { it.copy(cantidades = nuevasCantidades) }
        guardarEstado()
    }

    fun limpiarPantalla() {
        val currentState = _uiState.value
        val esAlmacen = currentState.isAlmacenRole
        val isAdmin = currentState.isAdmin
        
        // 🔥 Si es Admin, reseteamos a "Selecciona Origen"
        // 🔥 Si es Almacenista (no admin), forzamos "Almacen Huasteca"
        val nuevoOrigen = if (isAdmin) {
            "Selecciona Origen"
        } else if (esAlmacen) {
            "Almacen Huasteca"
        } else {
            "Selecciona Origen"
        }

        _uiState.update { 
            it.copy(
                origen = nuevoOrigen,
                destino = "Selecciona Destino",
                cantidades = emptyMap()
            ) 
        }
        
        // Actualizar el listener de stock
        _almacenSeleccionado.value = nuevoOrigen

        guardarEstado()
    }

    fun limpiarCantidades() {
        _uiState.update { it.copy(cantidades = emptyMap(), editOrderId = null) }
        guardarEstado()
    }

    /**
     * 🔥 CARGAR ORDEN EXISTENTE PARA EDICIÓN
     */
    fun cargarOrdenParaEditar(orderId: String) {
        _uiState.update { it.copy(isLoading = true, editOrderId = orderId) }
        viewModelScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val doc = db.collection("ordenesTransferencia").document(orderId).get().await()
                
                if (doc.exists()) {
                    val data = doc.data ?: return@launch
                    val estado = data["estado"] as? String ?: "PENDIENTE"
                    
                    if (estado != "PENDIENTE") {
                        _uiState.update { it.copy(isLoading = false, error = "Esta carga ya no se puede editar (Estado: $estado)") }
                        return@launch
                    }

                    val origen = data["origen"] as? String ?: ""
                    val destino = data["destino"] as? String ?: ""
                    val productosRaw = data["productos"] as? List<Map<String, Any>> ?: emptyList()
                    
                    val nuevasCantidades = mutableMapOf<String, Int>()
                    productosRaw.forEach { p ->
                        val id = p["productoId"] as? String ?: ""
                        val cant = (p["cantidad"] as? Number)?.toInt() ?: 0
                        if (id.isNotEmpty()) nuevasCantidades[id] = cant
                    }

                    _uiState.update { it.copy(
                        origen = origen,
                        destino = destino,
                        cantidades = nuevasCantidades,
                        isLoading = false
                    ) }
                    
                    _almacenSeleccionado.value = origen
                    guardarEstado()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun cargarAlmacenes() {
        viewModelScope.launch {
            val lista = inventarioRepo.obtenerListaAlmacenes()
            _uiState.update { it.copy(listaAlmacenes = lista) }
        }
    }

    fun crearOrden(origen: String, destino: String, productosSeleccionados: List<Plantilla_Producto>, cantidades: Map<String, Int>, onSuccess: (String) -> Unit) {
        if (_uiState.value.isLoading) return // 🔥 CANDADO: Evitar múltiples clics
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                
                // 🛡️ VALIDACIÓN DE SEGURIDAD: Si estamos editando, verificar que siga PENDIENTE
                if (state.editOrderId != null) {
                    val doc = db.collection("ordenesTransferencia").document(state.editOrderId).get().await()
                    val estadoActual = doc.getString("estado") ?: "PENDIENTE"
                    if (estadoActual != "PENDIENTE") {
                        _uiState.update { it.copy(isLoading = false, error = "No se puede guardar: El vendedor ya aceptó esta carga.") }
                        return@launch
                    }
                }

                val uid = usuarioRepo.obtenerUsuarioActual()?.uid ?: ""
                val listaProductos = productosSeleccionados.map { p ->
                    mapOf("productoId" to p.id, "nombre" to p.nombre, "precio" to p.precio, "imagenUrl" to p.imagenUrl, "cantidad" to (cantidades[p.id] ?: 0))
                }
                
                val tipoOrden = when {
                    origen == "Compra Producto" -> "COMPRA_PRODUCTO"
                    destino.startsWith("Vendedor") -> "TRANSFERENCIA_VENDEDOR"
                    else -> "TRANSFERENCIA_INTERNA"
                }

                val ordenData = mutableMapOf(
                    "tipo" to tipoOrden,
                    "origen" to origen,
                    "destino" to destino,
                    "productos" to listaProductos,
                    "vendedorId" to uid,
                    "estado" to "PENDIENTE"
                )

                val finalOrderId: String
                if (state.editOrderId != null) {
                    // Mantenemos el timestamp original o añadimos uno de edición
                    ordenData["ultimaModificacion"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                    db.collection("ordenesTransferencia").document(state.editOrderId).update(ordenData as Map<String, Any>).await()
                    finalOrderId = state.editOrderId
                } else {
                    ordenData["timestamp"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                    finalOrderId = inventarioRepo.crearOrdenTransferencia(ordenData as Map<String, Any>)
                }

                if (tipoOrden != "COMPRA_PRODUCTO") {
                    val tokens = usuarioRepo.obtenerTokensPorDestino(destino)
                    tokens.forEach { enviarNotificacion(it, "Carga Modificada", "Se ha actualizado tu carga desde $origen.", "CARGA_NUEVA", finalOrderId) }
                }

                limpiarCantidades()
                _uiState.update { it.copy(isLoading = false, ordenCreadaExito = true, editOrderId = null) }
                onSuccess(finalOrderId)
            } catch (e: Exception) { 
                _uiState.update { it.copy(isLoading = false, error = e.message) } 
            }
        }
    }

    fun confirmarCargaDirecta(origen: String, destino: String, productosSeleccionados: List<Plantilla_Producto>, cantidades: Map<String, Int>, onSuccess: () -> Unit) {
        if (_uiState.value.isLoading) return // 🔥 CANDADO: Evitar múltiples clics
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val user = usuarioRepo.obtenerUsuarioActual()
                val uid = user?.uid ?: ""
                val folio = "DIRECT_LOAD_${System.currentTimeMillis()}"
                productosSeleccionados.forEach { p ->
                    val cant = cantidades[p.id] ?: 0
                    val movimiento = com.gruposanangel.delivery.data.MovimientoInventarioEntity(
                        id = java.util.UUID.randomUUID().toString(),
                        productoId = p.id,
                        nombreProducto = p.nombre,
                        cantidad = cant,
                        tipo = "CARGA_INVENTARIO",
                        vendedorId = uid,
                        almacenNombre = destino,
                        clienteId = null,
                        timestamp = System.currentTimeMillis(),
                        referenciaId = folio,
                        sincronizado = false
                    )
                    inventarioRepo.registrarMovimientoCarga(movimiento, p.copy(cantidad = cant))
                }
                limpiarCantidades()
                _uiState.update { it.copy(isLoading = false, ordenCreadaExito = true) }
                onSuccess()
            } catch (e: Exception) { _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}

class MovimientosViewModelFactory(private val inventarioRepo: RepositoryInventario, private val usuarioRepo: RepositoryUsuario, context: android.content.Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val prefs = appContext.getSharedPreferences("movimientos_prefs", android.content.Context.MODE_PRIVATE)
        return MovimientosViewModel(inventarioRepo, usuarioRepo, prefs) as T
    }
}
