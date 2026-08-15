package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class CargaResumen(
    val id: String,
    val origen: String,
    val destino: String,
    val estado: String,
    val fechaFormateada: String,
    val timestamp: Long,
    val totalPiezas: Int,
    val montoTotal: Double,
    val diferenciaDinero: Double = 0.0, // 🔥 Nueva: Para Arqueos/Liquidaciones
    val diferenciaPiezas: Int = 0,      // 🔥 Nueva: Para Arqueos/Liquidaciones
    val productos: List<Plantilla_Producto>,
    val esEmergencia: Boolean = false,
    val metodoAuditoria: String? = null
)

data class HistorialCargasUiState(
    val isLoading: Boolean = false,
    val isQueryingCloud: Boolean = false, // 🔥 NUEVO: Para saber si estamos trayendo datos viejos de la nube
    val tabIndex: Int = 0, // 🔥 PERSISTENCIA DE PESTAÑA: 0=Cargas, 1=Arqueos
    val cargas: List<CargaResumen> = emptyList(),
    val arqueos: List<CargaResumen> = emptyList(), // 🔥 Nueva lista separada
    val listaVendedores: List<String> = emptyList(),
    val filtroVendedor: String = "Todos",
    val fechaInicio: Long = System.currentTimeMillis(),
    val fechaFin: Long = System.currentTimeMillis(),
    val error: String? = null
)

class HistorialCargasViewModel(
    private val usuarioRepo: com.gruposanangel.delivery.RepositoryUsuario,
    private val inventarioRepo: RepositoryInventario
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistorialCargasUiState())
    val uiState: StateFlow<HistorialCargasUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private var localCargasJob: kotlinx.coroutines.Job? = null
    private var localArqueosJob: kotlinx.coroutines.Job? = null
    private var remoteSyncJob: kotlinx.coroutines.Job? = null // 🔥 NUEVO: Para controlar el proceso de la nube
    private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX"))
    
    // 🔥 NUEVO: Para control de permisos en la UI
    var userRole by mutableStateOf("")
    private var userUid = ""
    private var userName = ""

    init {
        // Cargar datos del usuario
        viewModelScope.launch {
            val user = usuarioRepo.obtenerUsuarioActual()
            userRole = user?.puestoTrabajo ?: ""
            userUid = user?.uid ?: ""
            userName = user?.nombre ?: "Admin"
        }
        
        // Establecer rango de la semana actual (Lunes a Domingo) por defecto
        val cal = Calendar.getInstance(Locale("es", "MX"))
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        
        if (cal.timeInMillis > System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, -7)
        }
        val inicio = cal.timeInMillis
        
        val calEnd = cal.clone() as Calendar
        calEnd.add(Calendar.DAY_OF_YEAR, 6)
        calEnd.set(Calendar.HOUR_OF_DAY, 23)
        calEnd.set(Calendar.MINUTE, 59)
        calEnd.set(Calendar.SECOND, 59)
        calEnd.set(Calendar.MILLISECOND, 999)
        val fin = calEnd.timeInMillis
        
        _uiState.update { it.copy(fechaInicio = inicio, fechaFin = fin) }
        
        cargarVendedores()
        observarDatosReactivos()
    }

    private fun cargarVendedores() {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("almacenes").get().await()
                val vendedores = snapshot.documents
                    .map { it.id }
                    .filter { it.startsWith("Vendedor") }
                    .sorted()
                _uiState.update { it.copy(listaVendedores = listOf("Todos") + vendedores) }
            } catch (e: Exception) {
                Log.e("HistorialCargasVM", "Error cargando vendedores", e)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observarDatosReactivos() {
        viewModelScope.launch {
            // Combinamos los cambios de filtros y reaccionamos de forma reactiva
            _uiState.asStateFlow()
                .map { state -> Triple(state.filtroVendedor, state.fechaInicio, state.fechaFin) }
                .distinctUntilChanged()
                .onEach {
                    // 🔥 SOLO MOSTRAR LOADING SI NO HAY DATOS LOCALES PARA ESE FILTRO
                    // (Evitamos el círculo de carga intrusivo si ya tenemos historial)
                    val tieneDatos = _uiState.value.cargas.isNotEmpty() || _uiState.value.arqueos.isNotEmpty()
                    if (!tieneDatos) {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
                .flatMapLatest { (vendedor, inicio, fin) ->
                    // Disparar sync en segundo plano cuando cambian filtros
                    dispararSincronizacionRemota(vendedor, inicio, fin)
                    
                    combine(
                        inventarioRepo.obtenerOrdenesLocal(vendedor, inicio, fin),
                        inventarioRepo.obtenerArqueosLocal(vendedor, inicio, fin)
                    ) { ordenes, arqueos -> ordenes to arqueos }
                }
                .collect { (ordenes, arqueos) ->
                    procesarDatosLocales(ordenes, arqueos)
                }
        }
    }

    private suspend fun procesarDatosLocales(ordenes: List<com.gruposanangel.delivery.data.OrdenTransferenciaEntity>, arqueos: List<com.gruposanangel.delivery.data.MovimientoInventarioEntity>) {
        // 1. Transformar Órdenes (Cargas)
        val cargasResumen = ordenes.map { entity ->
            val detalles = inventarioRepo.obtenerDetallesOrdenLocal(entity.id)
            CargaResumen(
                id = entity.id,
                origen = entity.origen,
                destino = entity.destino,
                estado = entity.estado,
                fechaFormateada = sdf.format(Date(entity.timestamp)),
                timestamp = entity.timestamp,
                totalPiezas = entity.totalPiezas,
                montoTotal = entity.montoTotal,
                diferenciaDinero = 0.0, // Cargas no tienen "diferencia" en esta vista
                diferenciaPiezas = 0,
                productos = detalles.map { d ->
                    Plantilla_Producto(id = d.productoId, nombre = d.nombre, precio = d.precio, cantidad = d.cantidad)
                },
                esEmergencia = entity.esEmergencia,
                metodoAuditoria = entity.metodoAuditoria
            )
        }

        // 2. Transformar Arqueos / Liquidaciones
        // 🔥 MEJORA: Obtener catálogo de forma segura
        val catalogo = try {
            inventarioRepo.obtenerProductosLocal().first()
        } catch (e: Exception) {
            emptyList<com.gruposanangel.delivery.data.ProductoEntity>()
        }

        val listaArqueos = arqueos.groupBy { it.referenciaId ?: it.id } 
            .map { (referenciaId, docs) ->
                val primerDoc = docs.first()
                
                val productos = docs.map { d ->
                    val precio = catalogo.find { it.productoId == d.productoId }?.precio ?: 0.0
                    Plantilla_Producto(
                        id = d.productoId,
                        nombre = d.nombreProducto,
                        precio = precio,
                        cantidad = d.cantidad // Diferencia
                    )
                }

                // Cálculo de Totales Reales (Físicos)
                val valorRealTotal = docs.sumOf { (it.cantidadFisica ?: 0) * (catalogo.find { p -> p.productoId == it.productoId }?.precio ?: 0.0) }
                val piezasRealesTotal = docs.sumOf { it.cantidadFisica ?: 0 }
                val diferenciaDinero = productos.sumOf { it.cantidad * it.precio }
                val diferenciaPiezas = productos.sumOf { it.cantidad }

                CargaResumen(
                    id = referenciaId,
                    origen = if (primerDoc.metodoAuditoria == "LIQUIDACION") "LIQUIDACIÓN" else "AUDITORÍA FÍSICA",
                    destino = primerDoc.almacenNombre ?: "",
                    estado = if (primerDoc.metodoAuditoria == "LIQUIDACION") "LIQUIDADO" else "ARQUEADO",
                    fechaFormateada = sdf.format(Date(primerDoc.timestamp)),
                    timestamp = primerDoc.timestamp,
                    totalPiezas = piezasRealesTotal, // Mostramos lo que hay/regresó
                    montoTotal = valorRealTotal,     // Mostramos el valor de lo que hay/regresó
                    diferenciaDinero = diferenciaDinero,
                    diferenciaPiezas = diferenciaPiezas,
                    productos = productos,
                    metodoAuditoria = primerDoc.metodoAuditoria
                )
            }.sortedByDescending { it.timestamp }

        _uiState.update { it.copy(cargas = cargasResumen, arqueos = listaArqueos, isLoading = false) }
    }

    private fun dispararSincronizacionRemota(vendedor: String, inicio: Long, fin: Long) {
        // 🔥 CANCELAR BÚSQUEDA ANTERIOR: Evita que el mensaje se quede trabado
        remoteSyncJob?.cancel()
        
        remoteSyncJob = viewModelScope.launch(Dispatchers.IO) {
            val quinceDiasAtras = System.currentTimeMillis() - (15L * 24 * 60 * 60 * 1000)
            val esHistorico = inicio < quinceDiasAtras
            val estaVacio = _uiState.value.cargas.isEmpty() && _uiState.value.arqueos.isEmpty()
            
            if (esHistorico || estaVacio) {
                _uiState.update { it.copy(isQueryingCloud = true) }
            }
            
            // 🔥 MONITOREO DE DATOS: Si detectamos que ya llegaron datos, apagamos el indicador rápido
            val monitorJob = launch {
                _uiState.asStateFlow()
                    .filter { it.cargas.isNotEmpty() || it.arqueos.isNotEmpty() }
                    .first() // Esperar al primer dato
                _uiState.update { it.copy(isQueryingCloud = false) }
            }
            
            try {
                // Ejecutamos ambas sincronizaciones
                val syncCargas = launch { inventarioRepo.sincronizarOrdenesPeriodo(inicio, fin) }
                val syncArqueos = launch { inventarioRepo.sincronizarMovimientosPeriodo(inicio, fin, vendedor) }
                
                // Esperamos a que ambas terminen o fallen
                syncCargas.join()
                syncArqueos.join()
            } catch (e: Exception) {
                Log.e("HistorialCargasVM", "Error en sync remoto: ${e.message}")
            } finally {
                // 🛡️ SEGURIDAD: Siempre apagar el mensaje al finalizar o ser cancelado
                monitorJob.cancel()
                _uiState.update { it.copy(isQueryingCloud = false) }
            }
        }
    }

    fun actualizarFiltroVendedor(vendedor: String) {
        _uiState.update { it.copy(filtroVendedor = vendedor) }
    }

    fun actualizarFechas(inicio: Long, fin: Long) {
        _uiState.update { it.copy(fechaInicio = inicio, fechaFin = fin) }
    }

    fun cambiarPestaña(index: Int) {
        _uiState.update { it.copy(tabIndex = index) }
    }

    fun cargarHistorial() {
        val state = _uiState.value
        dispararSincronizacionRemota(state.filtroVendedor, state.fechaInicio, state.fechaFin)
    }

    /**
     * 🔥 CANCELAR CARGA (Solo CEO/Gerente y si está PENDIENTE)
     */
    fun cancelarCarga(ordenId: String, motivo: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                db.collection("ordenesTransferencia").document(ordenId).update(
                    mapOf(
                        "estado" to "CANCELADA",
                        "motivoCancelacion" to motivo,
                        "canceladoPorUid" to userUid,
                        "canceladoPorNombre" to userName,
                        "fechaCancelacion" to FieldValue.serverTimestamp()
                    )
                ).await()
                
                _uiState.update { it.copy(isLoading = false) }
                onComplete(true, "Carga cancelada exitosamente")
                
            } catch (e: Exception) {
                Log.e("HistorialCargasVM", "Error cancelando carga: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
                onComplete(false, e.message ?: "Error desconocido")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        localCargasJob?.cancel()
        localArqueosJob?.cancel()
        remoteSyncJob?.cancel()
    }
}

class HistorialCargasViewModelFactory(
    private val usuarioRepo: com.gruposanangel.delivery.RepositoryUsuario,
    private val inventarioRepo: RepositoryInventario
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = 
        HistorialCargasViewModel(usuarioRepo, inventarioRepo) as T
}
