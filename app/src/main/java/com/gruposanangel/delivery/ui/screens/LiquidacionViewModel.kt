package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.data.MovimientoInventarioEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class LiquidacionUiState(
    val isLoading: Boolean = false,
    val stockTeorico: Map<String, Int> = emptyMap(),
    val cantidadesAuditadas: Map<String, Int> = emptyMap(),
    val error: String? = null,
    val exito: Boolean = false,
    val origen: String = "",
    val destino: String = "",
    val listaAlmacenes: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class LiquidacionViewModel(
    private val inventarioRepo: RepositoryInventario,
    private val usuarioRepo: RepositoryUsuario,
    private val prefs: android.content.SharedPreferences,
    initialOrigen: String = "",
    initialDestino: String = ""
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiquidacionUiState())
    val uiState: StateFlow<LiquidacionUiState> = _uiState.asStateFlow()

    private val _almacenAuditado = MutableStateFlow<String?>(null)

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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        iniciarMotorObservacion()
        cargarListaAlmacenes()
        
        // 🔥 Inicialización inmediata desde el constructor para máxima agilidad
        if (initialOrigen.isNotEmpty()) {
            inicializar(initialOrigen, initialDestino)
        }
    }

    private fun iniciarMotorObservacion() {
        _almacenAuditado
            .filterNotNull()
            .flatMapLatest { almacen ->
                // Inmediatamente indicamos carga
                flow {
                    emit(emptyMap<String, Int>() to true)
                    inventarioRepo.obtenerStockAlmacenFlow(almacen).collect { stock ->
                        emit(stock to false)
                    }
                }
            }
            .onEach { data ->
                val stock = data.first
                val loading = data.second
                _uiState.update { current ->
                    // Solo inicializamos cantidades auditadas si están vacías (primera carga de la sesión)
                    val debeInicializarCantidades = current.cantidadesAuditadas.isEmpty()
                    
                    current.copy(
                        stockTeorico = stock, 
                        cantidadesAuditadas = if (debeInicializarCantidades) stock else current.cantidadesAuditadas,
                        isLoading = loading
                    ) 
                }
            }
            .catch { e -> 
                Log.e("LiquidacionVM", "Error en motor", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
            .launchIn(viewModelScope)
    }

    private fun cargarListaAlmacenes() {
        viewModelScope.launch {
            try {
                val lista = inventarioRepo.obtenerListaAlmacenes()
                _uiState.update { it.copy(listaAlmacenes = lista.filter { it != "Compra Producto" }) }
            } catch (e: Exception) {
                Log.e("LiquidacionVM", "Error cargando almacenes", e)
            }
        }
    }

    private fun guardarEstado() {
        val state = _uiState.value
        val cantidadesJson = state.cantidadesAuditadas.entries.joinToString(",", prefix = "{", postfix = "}") { 
            "${it.key}:${it.value}" 
        }
        prefs.edit().apply {
            putString("audit_origen", state.origen)
            putString("audit_destino", state.destino)
            putString("audit_cantidades", cantidadesJson)
            apply()
        }
    }

    fun inicializar(origen: String, destino: String) {
        val current = _uiState.value
        
        // Si el almacén es diferente al que tenemos en memoria o en Prefs, 
        // debemos limpiar y cargar lo nuevo de inmediato.
        if (current.origen != origen || current.destino != destino) {
            
            // 1. Intentar recuperar estado de SharedPreferences
            val savedOrigen = prefs.getString("audit_origen", "") ?: ""
            val savedDestino = prefs.getString("audit_destino", "") ?: ""
            
            val esMismaSesion = savedOrigen == origen
            
            if (esMismaSesion) {
                // Si es la misma sesión, recuperamos cantidades
                val cantidadesJson = prefs.getString("audit_cantidades", "{}") ?: "{}"
                val cantidades = parseCantidades(cantidadesJson)
                
                _uiState.update { it.copy(
                    origen = origen,
                    destino = if (destino.isNotEmpty()) destino else savedDestino,
                    cantidadesAuditadas = cantidades,
                    isLoading = true
                ) }
            } else {
                // Si es un almacén nuevo, LIMPIEZA TOTAL inmediata
                _uiState.update { it.copy(
                    origen = origen,
                    destino = destino,
                    stockTeorico = emptyMap(),
                    cantidadesAuditadas = emptyMap(),
                    isLoading = true,
                    exito = false,
                    error = null
                ) }
            }

            // 2. Disparar motor de búsqueda
            _almacenAuditado.value = origen
            
            // 3. Persistir el nuevo origen/destino
            guardarEstado()
        }
    }

    private fun parseCantidades(json: String): Map<String, Int> {
        val cantidades = mutableMapOf<String, Int>()
        try {
            val clean = json.removeSurrounding("{", "}")
            if (clean.isNotEmpty()) {
                clean.split(",").forEach { pair ->
                    val parts = pair.split(":")
                    if (parts.size == 2) {
                        cantidades[parts[0].trim()] = parts[1].trim().toInt()
                    }
                }
            }
        } catch (e: Exception) { Log.e("AuditVM", "Error parsing", e) }
        return cantidades
    }

    fun refrescarDatos() {
        val actual = _almacenAuditado.value
        if (actual != null) {
            _almacenAuditado.value = null
            _almacenAuditado.value = actual
        }
    }

    fun seleccionarDestino(destino: String) {
        _uiState.update { it.copy(destino = destino) }
        guardarEstado()
    }

    fun actualizarCantidadAuditada(productoId: String, cantidad: Int) {
        val nuevas = _uiState.value.cantidadesAuditadas.toMutableMap()
        if (cantidad < 0) nuevas.remove(productoId)
        else nuevas[productoId] = cantidad
        _uiState.update { it.copy(cantidadesAuditadas = nuevas) }
        guardarEstado()
    }

    fun restablecerStockTeorico() {
        _uiState.update { it.copy(cantidadesAuditadas = it.stockTeorico) }
        guardarEstado()
    }

    fun confirmarAuditoria(retornarABodega: Boolean, onSuccess: () -> Unit) {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val user = usuarioRepo.obtenerUsuarioActual()
                val uid = user?.uid ?: ""
                // 🔥 MODIFICACIÓN: Siempre liquidar a Almacén Huasteca (Central) si es Liquidación
                val destinoFinal = if (retornarABodega) "Almacen Huasteca" else state.destino.trim()
                val folio = "DIRECT_LOAD_${System.currentTimeMillis()}"

                // 🔥 Blindaje: Incluimos cualquier producto que tenga stock físico contado O stock teórico (aunque sea negativo)
                val productosParaAjuste = catalogoProductos.value.filter { 
                    (state.cantidadesAuditadas[it.id] ?: 0) != 0 || (state.stockTeorico[it.id] ?: 0) != 0 
                }

                val batch = db.batch()
                val arqueoId = "AUDIT_${java.util.UUID.randomUUID()}"
                val ts = System.currentTimeMillis()
                val metodo = if (retornarABodega) "LIQUIDACION" else "ARQUEO"

                productosParaAjuste.forEach { p ->
                    val fisico = state.cantidadesAuditadas[p.id] ?: 0
                    val teorico = state.stockTeorico[p.id] ?: 0
                    val diferencial = fisico - teorico

                    // 1. REGISTRO DE ARQUEO EN FIREBASE (Siempre registramos para tener el historial completo)
                    val tipoAjuste = when {
                        diferencial > 0 -> "AJUSTE_ARQUEO_SOBRANTE"
                        diferencial < 0 -> "AJUSTE_ARQUEO_FALTANTE"
                        else -> "AJUSTE_ARQUEO_OK"
                    }
                    
                    val movId = java.util.UUID.randomUUID().toString()
                    val dataMov = mapOf(
                        "productoId" to p.id,
                        "nombreProducto" to p.nombre,
                        "cantidad" to Math.abs(diferencial),
                        "cantidadFisica" to fisico,
                        "cantidadTeorica" to teorico,
                        "tipo" to tipoAjuste,
                        "vendedorId" to uid,
                        "almacenNombre" to state.origen,
                        "timestamp" to ts,
                        "referenciaId" to arqueoId,
                        "metodoAuditoria" to metodo
                    )
                    batch.set(db.collection("ajustes_inventario").document(movId), dataMov)
                    
                    // Solo actualizamos el stock si hubo cambio físico
                    if (diferencial != 0) {
                        val stockRef = db.collection("inventarioStock").document("${p.id}_${state.origen}")
                        batch.update(stockRef, "cantidad", fisico)
                    }

                    // 2. RETORNO (Carga inversa hacia el almacén central)
                    if (retornarABodega && fisico > 0) {
                        // En Liquidación Directa, el supervisor manda a bodega.
                        // Impactamos la nube para ambos almacenes.
                        val stockOrigenRef = db.collection("inventarioStock").document("${p.id}_${state.origen}")
                        val stockDestinoRef = db.collection("inventarioStock").document("${p.id}_$destinoFinal")
                        
                        // Restar de origen (queda en 0 si es liquidación completa)
                        batch.update(stockOrigenRef, "cantidad", 0)
                        
                        // Sumar en destino
                        // 🔥 MEJORA: Usar set con merge para asegurar que el documento exista antes de incrementar
                        val dataIncrement = mapOf(
                            "productoId" to p.id,
                            "productoNombre" to p.nombre,
                            "almacenNombre" to destinoFinal,
                            "cantidad" to com.google.firebase.firestore.FieldValue.increment(fisico.toLong()),
                            "precioUnitario" to p.precio,
                            "categoria" to (p.categoria ?: "General")
                        )
                        batch.set(stockDestinoRef, dataIncrement, com.google.firebase.firestore.SetOptions.merge())
                    }
                }

                batch.commit().await()
                
                prefs.edit().clear().apply()
                _uiState.update { it.copy(isLoading = false, exito = true) }
                onSuccess()
            } catch (e: Exception) {
                Log.e("LiquidacionVM", "Error", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}

class LiquidacionViewModelFactory(
    private val inventarioRepo: RepositoryInventario,
    private val usuarioRepo: RepositoryUsuario,
    private val context: android.content.Context,
    private val initialOrigen: String = "",
    private val initialDestino: String = ""
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val prefs = context.getSharedPreferences("audit_prefs", android.content.Context.MODE_PRIVATE)
        return LiquidacionViewModel(inventarioRepo, usuarioRepo, prefs, initialOrigen, initialDestino) as T
    }
}
