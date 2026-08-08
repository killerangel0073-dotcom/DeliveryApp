package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.data.MovimientoInventarioEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
    private val prefs: android.content.SharedPreferences
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
        // 🔥 MOTOR REACTIVO PARA EL STOCK TEÓRICO
        _almacenAuditado
            .filterNotNull()
            .flatMapLatest { almacen ->
                inventarioRepo.obtenerStockAlmacenFlow(almacen)
                    .onStart { _uiState.update { it.copy(isLoading = true) } }
            }
            .onEach { stock ->
                _uiState.update { current ->
                    // Solo inicializamos si no hay nada guardado o si el almacén cambió
                    val yaTieneDatos = current.cantidadesAuditadas.isNotEmpty() && current.origen == _almacenAuditado.value
                    
                    current.copy(
                        stockTeorico = stock, 
                        cantidadesAuditadas = if (yaTieneDatos) current.cantidadesAuditadas else stock,
                        isLoading = false
                    ) 
                }
            }
            .launchIn(viewModelScope)

        cargarListaAlmacenes()
        recuperarEstado()
    }

    private fun cargarListaAlmacenes() {
        viewModelScope.launch {
            val lista = inventarioRepo.obtenerListaAlmacenes()
            _uiState.update { it.copy(listaAlmacenes = lista.filter { it != "Compra Producto" }) }
        }
    }

    private fun recuperarEstado() {
        val origen = prefs.getString("audit_origen", "") ?: ""
        val destino = prefs.getString("audit_destino", "") ?: ""
        val cantidadesJson = prefs.getString("audit_cantidades", "{}") ?: "{}"
        
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
        } catch (e: Exception) { Log.e("AuditVM", "Error parsing", e) }

        _uiState.update { it.copy(origen = origen, destino = destino, cantidadesAuditadas = cantidades) }
        if (origen.isNotEmpty()) _almacenAuditado.value = origen
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
        // 🔥 CORRECCIÓN: Siempre actualizar si los valores son distintos para evitar basura de SharedPreferences
        if (_uiState.value.origen != origen || _uiState.value.destino != destino) {
            _uiState.update { it.copy(
                origen = origen, 
                destino = if (destino.isNotEmpty()) destino else it.destino 
            ) }
            
            if (_uiState.value.origen != origen) {
                _almacenAuditado.value = origen
                // Solo limpiamos si el origen realmente cambió de unidad
                _uiState.update { it.copy(cantidadesAuditadas = emptyMap()) }
            }
            guardarEstado()
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
                val user = usuarioRepo.obtenerUsuarioActual()
                val uid = user?.uid ?: ""
                val destinoFinal = state.destino.trim()
                // 🔥 USAR EL PREFIJO QUE DISPARA EL PROCESAMIENTO DIRECTO EN LA CLOUD FUNCTION
                val folio = "DIRECT_LOAD_${System.currentTimeMillis()}"

                val productosParaAjuste = catalogoProductos.value.filter { 
                    (state.cantidadesAuditadas[it.id] ?: 0) > 0 || (state.stockTeorico[it.id] ?: 0) > 0 
                }

                productosParaAjuste.forEach { p ->
                    val fisico = state.cantidadesAuditadas[p.id] ?: 0
                    val teorico = state.stockTeorico[p.id] ?: 0
                    val diferencial = fisico - teorico

                    // 1. AJUSTE DE ARQUEO (Para cuadrar la camioneta actual)
                    if (diferencial != 0) {
                        val tipoAjuste = if (diferencial > 0) "AJUSTE_ARQUEO_SOBRANTE" else "AJUSTE_ARQUEO_FALTANTE"
                        val movAjuste = MovimientoInventarioEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            productoId = p.id,
                            nombreProducto = p.nombre,
                            cantidad = Math.abs(diferencial),
                            tipo = tipoAjuste,
                            vendedorId = uid,
                            almacenNombre = state.origen,
                            timestamp = System.currentTimeMillis(),
                            referenciaId = "AUDIT_${System.currentTimeMillis()}",
                            clienteId = null,
                            sincronizado = false
                        )
                        inventarioRepo.registrarMovimientoCarga(movAjuste, p.copy(cantidad = diferencial))
                    }

                    // 2. RETORNO (Carga inversa hacia el almacén central)
                    if (retornarABodega && fisico > 0) {
                        // SALIDA DE LA CAMIONETA (Negativa para que la función reste: stock + (-cantidad))
                        val movSalida = MovimientoInventarioEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            productoId = p.id,
                            nombreProducto = p.nombre,
                            cantidad = -fisico, // 🔥 CRITICO: Valor negativo para descontar
                            tipo = "CARGA_INVENTARIO", 
                            vendedorId = uid,
                            almacenNombre = state.origen,
                            timestamp = System.currentTimeMillis(),
                            referenciaId = folio,
                            clienteId = null,
                            sincronizado = false
                        )
                        
                        // ENTRADA AL ALMACÉN CENTRAL (Positiva para que sume)
                        val movEntrada = MovimientoInventarioEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            productoId = p.id,
                            nombreProducto = p.nombre,
                            cantidad = fisico, // 🔥 CRITICO: Valor positivo para sumar
                            tipo = "CARGA_INVENTARIO",
                            vendedorId = uid,
                            almacenNombre = destinoFinal,
                            timestamp = System.currentTimeMillis() + 1,
                            referenciaId = folio,
                            clienteId = null,
                            sincronizado = false
                        )

                        // Ejecutamos ambos movimientos
                        inventarioRepo.registrarMovimientoCarga(movSalida, p.copy(cantidad = -fisico))
                        inventarioRepo.registrarMovimientoCarga(movEntrada, p.copy(cantidad = fisico))
                    }
                }

                // Limpiar persistencia tras éxito
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
    private val context: android.content.Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val prefs = context.getSharedPreferences("audit_prefs", android.content.Context.MODE_PRIVATE)
        return LiquidacionViewModel(inventarioRepo, usuarioRepo, prefs) as T
    }
}
