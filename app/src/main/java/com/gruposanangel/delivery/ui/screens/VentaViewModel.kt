package com.gruposanangel.delivery.ui.screens

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.SegundoPlano.LocationState
import TicketVentaCompleto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.*

data class VentaUiState(
    val productosEnCarrito: List<Plantilla_Producto> = emptyList(),
    val catalogoCompleto: List<Plantilla_Producto> = emptyList(), 
    val estaProcesando: Boolean = false,
    val totalVenta: Double = 0.0,
    val estadoRuta: EstadoRuta = EstadoRuta.Cargando,
    val isLoadingInventario: Boolean = true,
    
    // 🔥 BÚSQUEDA Y CARRITO
    val searchQuery: String = "",
    val cantidades: Map<String, Int> = emptyMap(),

    // 🔥 AUDITORÍA GEOGRÁFICA
    val distanciaAlClienteMetros: Float = -1f,
    val estaEnRango: Boolean = true,
    val requiereFotoEvidencia: Boolean = false,
    val enRuta: Boolean = true // Por defecto true para no bloquear mientras carga
)

sealed class EstadoRuta {
    object Cargando : EstadoRuta()
    object SinRuta : EstadoRuta()
    data class Error(val mensaje: String) : EstadoRuta()
    data class ConRuta(val nombreAlmacen: String, val almacenId: String) : EstadoRuta()
}

/**
 * VentaViewModel - Maneja la lógica de ventas y stock
 */
class VentaViewModel(
    private val repositoryInventario: RepositoryInventario,
    private val ventaRepository: VentaRepository,
    private val repositoryUsuario: RepositoryUsuario,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VentaUiState())
    val uiState: StateFlow<VentaUiState> = _uiState.asStateFlow()

    private val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    private var salesListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val _ventasPeriodo = MutableStateFlow<List<VentaEntity>>(emptyList())
    val ventasPeriodo: StateFlow<List<VentaEntity>> = _ventasPeriodo.asStateFlow()

    // 🔥 OFFLINE-FIRST: Observamos las ventas de hoy de forma reactiva
    val ventasHoyFlow: StateFlow<List<VentaEntity>> = flow {
        val usuario = repositoryUsuario.obtenerUsuarioActual()
        val uid = usuario?.uid ?: ""
        val puesto = usuario?.puestoTrabajo?.trim() ?: ""
        // 🛡️ Si es admin, idParaQuery es "" para ver todo
        val idParaQuery = if (puesto == "Vendedor de Ruta" || puesto == "Suplente de Ruta") uid else ""
        
        // 🔥 AUDITORÍA DE TIEMPO: Límites basados en Hora Real
        val ahoraReal = com.gruposanangel.delivery.utilidades.TimeManager.getHoraReal()
        val cal = Calendar.getInstance()
        cal.timeInMillis = ahoraReal
        
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val inicio = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val fin = cal.timeInMillis
        
        emitAll(ventaRepository.obtenerVentasPorPeriodoFlow(idParaQuery, inicio, fin))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        observarInventario()
        escucharVentasNube()
        observarEstadoJornada()
    }

    private fun observarEstadoJornada() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("jornadas").document(uid)
            .addSnapshotListener { snapshot, _ ->
                val activo = snapshot?.getBoolean("activo") ?: false
                _uiState.update { it.copy(enRuta = activo) }
            }
    }

    /**
     * Monitorea la distancia en tiempo real entre el vendedor y el cliente.
     */
    fun monitorearGeocerca(clienteLat: Double, clienteLon: Double) {
        viewModelScope.launch {
            LocationState.ultimaUbicacion.collect { miUbicacion ->
                if (miUbicacion != null && clienteLat != 0.0) {
                    val locCliente = Location("").apply {
                        latitude = clienteLat
                        longitude = clienteLon
                    }
                    val distancia = miUbicacion.distanceTo(locCliente)
                    val precisión = miUbicacion.accuracy
                    
                    // 📏 Regla de Negocio: Rango de 200m
                    // Si el GPS está muy impreciso (> 100m), activamos modo foto preventivo
                    val enRango = distancia < 200f
                    val requiereFoto = !enRango || precisión > 100f
                    
                    _uiState.update { it.copy(
                        distanciaAlClienteMetros = distancia,
                        estaEnRango = enRango,
                        requiereFotoEvidencia = requiereFoto
                    ) }
                }
            }
        }
    }

    private fun escucharVentasNube() {
        viewModelScope.launch {
            val usuario = repositoryUsuario.obtenerUsuarioActual() ?: return@launch
            val uid = usuario.uid
            val puesto = usuario.puestoTrabajo?.trim() ?: ""
            val esVendedor = puesto == "Vendedor de Ruta" || puesto == "Suplente de Ruta"
            val idParaSync = if (esVendedor) uid else ""

            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            val inicio = cal.time

            salesListener?.remove()
            
            var query: com.google.firebase.firestore.Query = db.collection("ventas")
                .whereGreaterThanOrEqualTo("fecha", inicio)
            
            if (esVendedor) {
                query = query.whereEqualTo("vendedorId", uid)
            }

            salesListener = query.addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    viewModelScope.launch {
                        ventaRepository.descargarVentasDia(idParaSync)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        salesListener?.remove()
    }

    private fun observarInventario() {
        combine(
            repositoryInventario.obtenerProductosLocal(),
            _uiState.map { it.searchQuery }.distinctUntilChanged(),
            _uiState.map { it.cantidades }.distinctUntilChanged()
        ) { entidades, query, mapaCantidades ->
            // 1. Catálogo completo (Contiene todos los productos para persistir cantidades fuera de búsqueda)
            val catalogo = entidades.map { e ->
                Plantilla_Producto(
                    id = e.id,
                    nombre = e.nombre,
                    precio = e.precio,
                    cantidad = mapaCantidades[e.id] ?: 0,
                    cantidadDisponible = e.cantidadDisponible,
                    imagenUrl = e.imagenUrl ?: ""
                )
            }

            // 2. Productos visibles (Filtrados por búsqueda y con stock)
            val productosMapeados = entidades.asSequence()
                .filter { it.cantidadDisponible > 0 }
                .filter { query.isBlank() || it.nombre.contains(query, ignoreCase = true) }
                .map { entidad ->
                    Plantilla_Producto(
                        id = entidad.id,
                        nombre = entidad.nombre,
                        precio = entidad.precio,
                        cantidad = mapaCantidades[entidad.id] ?: 0,
                        cantidadDisponible = entidad.cantidadDisponible,
                        imagenUrl = entidad.imagenUrl ?: ""
                    )
                }
                .sortedWith(
                    if (query.isBlank()) {
                        // Lista General: Seleccionados primero (A-Z), luego resto (A-Z)
                        compareByDescending<Plantilla_Producto> { it.cantidad > 0 }
                            .thenBy { it.nombre }
                    } else {
                        // En Búsqueda: Siempre alfabético para evitar que el producto se mueva mientras escribes/agregas
                        compareBy { it.nombre }
                    }
                )
                .toList()
            
            Pair(catalogo, productosMapeados)
        }.onEach { (catalogo, productos) ->
            _uiState.update { state ->
                // Recalcular total sumando todos los productos que tienen cantidad > 0 en el catálogo
                val total = catalogo.sumOf { it.precio * it.cantidad }
                
                state.copy(
                    productosEnCarrito = productos,
                    catalogoCompleto = catalogo,
                    isLoadingInventario = false,
                    totalVenta = total
                )
            }
        }.launchIn(viewModelScope)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun actualizarCantidad(productoId: String, nuevaCantidad: Int) {
        _uiState.update { state ->
            // Buscamos en el catálogo completo para obtener la disponibilidad
            val producto = state.catalogoCompleto.find { it.id == productoId }
            if (producto != null) {
                val valorFinal = nuevaCantidad.coerceIn(0, producto.cantidadDisponible)
                val nuevasCantidades = state.cantidades.toMutableMap()
                
                if (valorFinal > 0) nuevasCantidades[productoId] = valorFinal
                else nuevasCantidades.remove(productoId)

                // Si estamos incrementando la cantidad, NO limpiamos la búsqueda para que el vendedor
                // pueda seguir agregando piezas del mismo producto sin que se mueva.
                state.copy(
                    cantidades = nuevasCantidades,
                    searchQuery = state.searchQuery
                )
            } else {
                Log.w("VentaViewModel", "Producto no encontrado en catálogo: $productoId")
                state
            }
        }
    }

    fun cargarVentasHoy() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val inicio = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val fin = calendar.timeInMillis

        cargarVentasPorPeriodo(Date(inicio), Date(fin))
    }

    fun cargarVentasPorPeriodo(fechaInicio: Date, fechaFin: Date) {
        viewModelScope.launch {
            try {
                val usuario = repositoryUsuario.obtenerUsuarioActual()
                val uid = usuario?.uid ?: ""
                val puesto = usuario?.puestoTrabajo?.trim() ?: ""
                val idParaQuery = if (puesto == "Vendedor de Ruta" || puesto == "Suplente de Ruta") uid else ""
                
                val ventas = ventaRepository.obtenerVentasPorPeriodo(idParaQuery, fechaInicio.time, fechaFin.time)
                _ventasPeriodo.value = ventas
            } catch (e: Exception) {
                Log.e("VentaViewModel", "Error cargando ventas por periodo", e)
            }
        }
    }

    fun sincronizarHistorialVendedor() {
        viewModelScope.launch {
            try {
                val usuario = repositoryUsuario.obtenerUsuarioActual()
                val uid = usuario?.uid ?: ""
                val puesto = usuario?.puestoTrabajo?.trim() ?: ""
                val idParaSync = if (puesto == "Vendedor de Ruta" || puesto == "Suplente de Ruta") uid else ""
                
                // Sincroniza todas las ventas (Maestro o individuales)
                ventaRepository.sincronizarVentasPeriodo(idParaSync)
                cargarVentasHoy()
            } catch (e: Exception) {
                Log.e("VentaViewModel", "Error sincronizando historial completo", e)
            }
        }
    }

    fun sincronizarVentasDia(vendedorId: String) {
        viewModelScope.launch {
            try {
                ventaRepository.descargarVentasDia(vendedorId)
                cargarVentasHoy()
            } catch (e: Exception) {
                Log.e("VentaViewModel", "Error sincronizando ventas", e)
            }
        }
    }

    fun verificarRutaAsignadaLocal(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val usuario = repositoryUsuario.obtenerUsuarioLocal(uid)
                val nuevoEstado = if ((usuario?.ultimaRutaId != null) && (usuario.ultimoAlmacenId != null)) {
                    EstadoRuta.ConRuta(
                        nombreAlmacen = usuario.ultimoAlmacenNombre ?: "Almacén",
                        almacenId = usuario.ultimoAlmacenId
                    )
                } else {
                    EstadoRuta.SinRuta
                }
                _uiState.update { it.copy(estadoRuta = nuevoEstado) }
            } catch (e: Exception) {
                _uiState.update { it.copy(estadoRuta = EstadoRuta.Error(e.message ?: "Error desconocido")) }
            }
        }
    }


    fun procesarVenta(
        clienteId: String,
        clienteNombre: String,
        clienteFotoUrl: String?,
        metodoPago: String,
        fotoEvidenciaUrl: String? = null,
        onResultado: (Boolean, String, String) -> Unit
    ) {
        if (_uiState.value.estaProcesando) return
        if (!_uiState.value.enRuta) {
            onResultado(false, "Debes iniciar jornada para realizar ventas", "")
            return
        }
        _uiState.update { it.copy(estaProcesando = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val usuarioActual = repositoryUsuario.obtenerUsuarioActual()
                val uidVendedor = usuarioActual?.uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val nombreVendedor = usuarioActual?.nombre ?: FirebaseAuth.getInstance().currentUser?.displayName ?: "Vendedor"
                
                val almacenId = (_uiState.value.estadoRuta as? EstadoRuta.ConRuta)?.almacenId
                val miUbicacion = LocationState.ultimaUbicacion.value
                
                val productosVenta = _uiState.value.catalogoCompleto.filter { it.cantidad > 0 }
                val totalVenta = productosVenta.sumOf { it.precio * it.cantidad }

                val ventaLocalId = ventaRepository.guardarVentaLocal(
                    clienteId = clienteId,
                    clienteNombre = clienteNombre,
                    clienteImagenUrl = clienteFotoUrl,
                    productos = productosVenta,
                    total = totalVenta,
                    metodoPago = metodoPago,
                    vendedorId = uidVendedor,
                    vendedorNombre = nombreVendedor,
                    almacenId = almacenId,
                    latitud = miUbicacion?.latitude ?: 0.0,
                    longitud = miUbicacion?.longitude ?: 0.0,
                    fueraDeRango = !_uiState.value.estaEnRango,
                    fotoEvidencia = fotoEvidenciaUrl
                )

                withContext(Dispatchers.Main) {
                    // 🔔 NOTIFICACIÓN: Se ha eliminado la llamada manual aquí para evitar duplicados.
                    // Ahora la notificación la gestiona automáticamente la Cloud Function 'notificarNuevaVenta'
                    // al detectar el registro en Firestore, asegurando que llegue una sola vez y con todos los datos.
                    _uiState.update { it.copy(estaProcesando = false) }
                    onResultado(true, "Venta registrada con éxito", ventaLocalId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(estaProcesando = false) }
                    onResultado(false, e.message ?: "Error", "")
                }
            }
        }
    }

    // --- Consultas de Tickets (Utilizadas por Detalle Ticket) ---

    suspend fun obtenerTicketCompleto(ticketId: String): TicketVentaCompleto? {
        return withContext(Dispatchers.IO) {
            ventaRepository.obtenerTicketCompleto(ticketId)
        }
    }

    suspend fun obtenerVentaPorId(ventaId: String): VentaEntity? {
        return withContext(Dispatchers.IO) {
            ventaRepository.obtenerVentaPorId(ventaId)
        }
    }

    suspend fun obtenerDetallesDeVenta(ventaId: String): List<VentaDetalleEntity> {
        return withContext(Dispatchers.IO) {
            ventaRepository.obtenerDetallesDeVenta(ventaId)
        }
    }

    // --- NUEVO: REGISTRO DE CAMBIOS Y DEVOLUCIONES (DOBLE FLUJO) ---

    fun registrarAjusteDoble(
        ticketId: String,
        clienteId: String?,
        productoEntra: Plantilla_Producto,
        productoSale: Plantilla_Producto,
        cantidad: Int,
        tipoOperacion: String,
        motivo: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val usuario = repositoryUsuario.obtenerUsuarioActual()
                val uid = usuario?.uid ?: ""
                val almacenNombre = usuario?.ultimoAlmacenNombre ?: ""

                repositoryInventario.registrarDobleMovimiento(
                    tipoOperacion = tipoOperacion,
                    productoEntra = productoEntra,
                    productoSale = productoSale,
                    cantidad = cantidad,
                    vendedorId = uid,
                    almacenNombre = almacenNombre,
                    clienteId = clienteId,
                    ticketId = ticketId,
                    motivo = motivo
                )
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                Log.e("VentaViewModel", "Error al registrar ajuste", e)
            }
        }
    }

    fun anularVenta(
        ventaId: String,
        motivo: String,
        onResultado: (Boolean, String) -> Unit
    ) {
        _uiState.update { it.copy(estaProcesando = true) }
        viewModelScope.launch {
            try {
                val admin = repositoryUsuario.obtenerUsuarioActual()
                val adminNombre = admin?.nombre ?: "Admin"
                val adminUid = admin?.uid ?: ""

                val (exito, mensaje) = ventaRepository.anularVenta(
                    ventaId = ventaId,
                    motivo = motivo,
                    adminNombre = adminNombre,
                    adminUid = adminUid
                )
                
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(estaProcesando = false) }
                    onResultado(exito, mensaje)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(estaProcesando = false) }
                    onResultado(false, e.message ?: "Error desconocido")
                }
            }
        }
    }
}

class VentaViewModelFactory(
    private val repositoryInventario: RepositoryInventario,
    private val ventaRepository: VentaRepository,
    private val repositoryUsuario: RepositoryUsuario
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VentaViewModel(repositoryInventario, ventaRepository, repositoryUsuario) as T
    }
}
