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
import kotlinx.coroutines.delay
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
    
    // 🔥 PERFILES DE VENTA DINÁMICOS
    val perfilesDisponibles: List<PerfilVenta> = emptyList(),
    val perfilSeleccionado: PerfilVenta? = null,

    // 🔥 BÚSQUEDA Y CARRITO
    val searchQuery: String = "",
    val cantidades: Map<String, Int> = emptyMap(),

    // 🔥 AUDITORÍA GEOGRÁFICA
    val distanciaAlClienteMetros: Float = -1f,
    val estaEnRango: Boolean = true,
    val requiereFotoEvidencia: Boolean = false,
    val enRuta: Boolean = true, // Por defecto true para no bloquear mientras carga
    
    // 🔥 VISITA SIN VENTA
    val mostrarDialogoSinVenta: Boolean = false,
    val motivoSinVenta: String? = null
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
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ventasHoyFlow: StateFlow<List<VentaEntity>> = repositoryUsuario.getUsuarioActual()
        .flatMapLatest { usuario ->
            val uid = usuario?.uid ?: ""
            val puesto = usuario?.puestoTrabajo?.trim() ?: ""
            // 🛡️ Búsqueda más flexible del puesto para evitar fallos por mayúsculas o espacios
            val esVendedor = puesto.contains("Vendedor", ignoreCase = true) || puesto.contains("Suplente", ignoreCase = true)
            val idParaQuery = if (esVendedor) uid else ""
            
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
            
            ventaRepository.obtenerVentasPorPeriodoFlow(idParaQuery, inicio, fin)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        observarInventario()
        escucharVentasNube()
        observarEstadoRutaReactivo()
        observarEstadoJornada()
    }

    private fun observarEstadoRutaReactivo() {
        repositoryUsuario.getUsuarioActual()
            .onEach { usuario ->
                val nuevoEstado = if (usuario?.ultimaRutaId != null && usuario.ultimoAlmacenId != null) {
                    EstadoRuta.ConRuta(
                        nombreAlmacen = usuario.ultimoAlmacenNombre ?: "Almacén",
                        almacenId = usuario.ultimoAlmacenId
                    )
                } else {
                    EstadoRuta.SinRuta
                }
                _uiState.update { it.copy(estadoRuta = nuevoEstado) }
            }
            .launchIn(viewModelScope)
    }

    fun precargarUltimaVenta(clienteId: String) {
        viewModelScope.launch {
            try {
                Log.d("VentaDebug", "🚀 INICIANDO PRECARGA - Cliente: $clienteId")

                // 🔄 1. ESPERAR A QUE LA RUTA ESTÉ LISTA
                var retryRoute = 0
                while (uiState.value.estadoRuta !is EstadoRuta.ConRuta && retryRoute < 40) {
                    delay(150)
                    retryRoute++
                }
                
                val rutaEstado = uiState.value.estadoRuta
                if (rutaEstado !is EstadoRuta.ConRuta) {
                    Log.e("VentaDebug", "❌ ABORTO: No se pudo determinar la ruta del vendedor tras 6 segundos.")
                    return@launch
                }
                val nombreAlmacenActual = rutaEstado.nombreAlmacen
                Log.d("VentaDebug", "📍 RUTA DETECTADA: $nombreAlmacenActual")

                // 🔄 2. OBTENER LA ÚLTIMA VENTA
                val ultimaVenta = ventaRepository.obtenerUltimaVentaConProductosPorCliente(clienteId)
                if (ultimaVenta == null) {
                    Log.w("VentaDebug", "ℹ️ SIN HISTORIAL: El cliente no tiene ventas previas registradas en Room.")
                    return@launch
                }
                Log.d("VentaDebug", "📄 VENTA ENCONTRADA: ID=${ultimaVenta.id} | Fecha=${ultimaVenta.fecha}")

                // 🔄 3. OBTENER TODO EL STOCK DISPONIBLE DEL ALMACÉN (Sin filtrar por perfil aún para cruzar todo el historial)
                val stockLocal = repositoryInventario.obtenerProductosLocal().first().filter { entidad ->
                    entidad.id.endsWith("_$nombreAlmacenActual") || (rutaEstado.almacenId.isNotEmpty() && entidad.id.endsWith("_" + rutaEstado.almacenId))
                }
                
                if (stockLocal.isEmpty()) {
                    Log.e("VentaDebug", "❌ ABORTO: El inventario local del almacén está vacío.")
                    return@launch
                }
                Log.d("VentaDebug", "📦 STOCK TOTAL ALMACÉN: ${stockLocal.size} productos.")

                // 🔄 4. PROCESAR DETALLES Y CRUZAR CON STOCK
                val detalles = ventaRepository.obtenerDetallesDeVenta(ultimaVenta.id)
                Log.d("VentaDebug", "🛒 DETALLES VENTA ANTERIOR: ${detalles.size} items.")

                val nuevasCantidades = mutableMapOf<String, Int>()

                detalles.forEach { detalle ->
                    val pIdLimpio = detalle.productoId.trim()
                    val idConstruido = "${pIdLimpio}_$nombreAlmacenActual"
                    
                    Log.d("VentaDebug", "🔍 BUSCANDO: ${detalle.nombre} | ID Sugerido: $idConstruido")

                    // Búsqueda profunda
                    val productoMatch = stockLocal.find { 
                        it.id.trim() == idConstruido || 
                        it.id.trim() == detalle.stockId?.trim() || 
                        it.id.trim() == pIdLimpio ||
                        it.nombre.lowercase().trim() == detalle.nombre.lowercase().trim()
                    }

                    if (productoMatch != null) {
                        // 🔥 TOPE ESTRICTO AL STOCK ACTUAL:
                        val stockDisponible = productoMatch.cantidadDisponible
                        val cantidadSugerida = Math.min(detalle.cantidad, stockDisponible)
                        
                        if (cantidadSugerida > 0) {
                            nuevasCantidades[productoMatch.id] = cantidadSugerida
                            Log.d("VentaDebug", "   ✅ MATCH: ${productoMatch.nombre} | Sugerido (Topado): $cantidadSugerida")
                        } else {
                            Log.w("VentaDebug", "   ⚠️ MATCH SIN STOCK: ${productoMatch.nombre} (No se añade)")
                        }
                    } else {
                        Log.w("VentaDebug", "   ❓ NO ENCONTRADO: ${detalle.nombre}")
                    }
                }
                
                if (nuevasCantidades.isNotEmpty()) {
                    _uiState.update { it.copy(cantidades = nuevasCantidades) }
                    Log.d("VentaDebug", "🎉 PRECARGA COMPLETADA: ${nuevasCantidades.size} productos añadidos al carrito.")
                } else {
                    Log.w("VentaDebug", "⚠️ FIN: No hubo coincidencias válidas entre el historial y el stock actual.")
                }
            } catch (e: Exception) {
                Log.e("VentaDebug", "🔥 ERROR CRÍTICO EN PRECARGA", e)
            }
        }
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
        repositoryUsuario.getUsuarioActual()
            .onEach { usuario ->
                if (usuario == null) return@onEach
                
                val uid = usuario.uid
                val puesto = usuario.puestoTrabajo?.trim() ?: ""
                val esVendedor = puesto.contains("Vendedor", ignoreCase = true) || puesto.contains("Suplente", ignoreCase = true)
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
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        salesListener?.remove()
    }

    private fun observarInventario() {
        combine(
            repositoryInventario.obtenerProductosLocal(),
            _uiState.map { it.searchQuery }.distinctUntilChanged(),
            _uiState.map { it.cantidades }.distinctUntilChanged(),
            _uiState.map { it.perfilSeleccionado }.distinctUntilChanged(), // 🔥 Perfil activo
            repositoryUsuario.getUsuarioActual()
        ) { entidades, query, mapaCantidades, perfilActivo, usuario ->
            
            // 1. Parsear Perfiles desde el JSON del usuario
            val perfiles = try {
                val json = usuario?.perfilesVentaJson
                if (!json.isNullOrBlank()) {
                    val array = org.json.JSONArray(json)
                    (0 until array.length()).map { i ->
                        val obj = array.getJSONObject(i)
                        val filtrosArr = obj.getJSONArray("filtros")
                        val filtros = (0 until filtrosArr.length()).map { j ->
                            val fObj = filtrosArr.getJSONObject(j)
                            val catsArr = fObj.optJSONArray("categorias")
                            val cats = if (catsArr != null) {
                                (0 until catsArr.length()).map { k -> catsArr.getString(k) }
                            } else emptyList<String>()
                            FiltroPerfil(fObj.getString("marca"), cats)
                        }
                        PerfilVenta(obj.getString("id"), obj.getString("nombre"), filtros)
                    }
                } else emptyList()
            } catch (e: Exception) {
                Log.e("VentaViewModel", "Error parseando perfiles", e)
                emptyList()
            }

            // 2. Determinar Almacenes Permitidos
            val almacenesPermitidos = usuario?.almacenesConfig?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

            // 3. Filtrado por Almacén + Perfil Operativo
            val entidadesFiltradas = entidades.filter { entidad ->
                // Filtro de Almacén (Físico)
                val perteneceAlmacen = if (almacenesPermitidos.isNotEmpty()) {
                    almacenesPermitidos.any { almacen -> entidad.id.endsWith("_$almacen") }
                } else {
                    val principal = usuario?.ultimoAlmacenNombre
                    principal != null && entidad.id.endsWith("_$principal")
                }

                // Filtro de Perfil (Marca/Categoría)
                val cumplePerfil = if (perfilActivo != null) {
                    perfilActivo.filtros.any { filtro ->
                        // 1. Comparación de Marca robusta
                        val marcaMatch = entidad.marca.trim().equals(filtro.marca.trim(), ignoreCase = true)
                        
                        // 2. Comparación de Categoría (Si el perfil no tiene categorías, pasan TODAS las de la marca)
                        val categoriaMatch = if (filtro.categorias.isNotEmpty()) {
                            filtro.categorias.any { it.trim().equals(entidad.categoria.trim(), ignoreCase = true) }
                        } else {
                            true // 🛡️ LIBERACIÓN: Si no hay categorías en el filtro, entra toda la marca
                        }
                        
                        marcaMatch && categoriaMatch
                    }
                } else {
                    true // 🛡️ MODO LIBRE: Si no hay perfiles configurados, muestra todo lo del almacén
                }

                perteneceAlmacen && cumplePerfil
            }

            val catalogo = entidadesFiltradas.map { e ->
                Plantilla_Producto(
                    id = e.id,
                    nombre = e.nombre,
                    precio = e.precio,
                    cantidad = mapaCantidades[e.id] ?: 0,
                    cantidadDisponible = e.cantidadDisponible,
                    imagenUrl = e.imagenUrl ?: "",
                    marca = e.marca,
                    categoria = e.categoria
                )
            }

            // 4. Productos visibles (Filtrados por búsqueda y con stock/selección)
            val productosMapeados = catalogo.asSequence()
                .filter { it.cantidadDisponible > 0 || it.cantidad > 0 }
                .filter { query.isBlank() || it.nombre.contains(query, ignoreCase = true) }
                .sortedWith(
                    if (query.isBlank()) {
                        compareByDescending<Plantilla_Producto> { it.cantidad > 0 }
                            .thenBy { it.nombre }
                    } else {
                        compareBy { it.nombre }
                    }
                )
                .toList()
            
            Triple(catalogo, productosMapeados, perfiles)
        }.onEach { (catalogo, productos, perfiles) ->
            _uiState.update { state ->
                val total = catalogo.sumOf { it.precio * it.cantidad }
                
                // 🔥 Sincronizar el perfil seleccionado con la nueva lista de perfiles
                val perfilActualizado = if (state.perfilSeleccionado != null) {
                    perfiles.find { it.id == state.perfilSeleccionado.id } ?: perfiles.firstOrNull()
                } else {
                    perfiles.firstOrNull()
                }

                state.copy(
                    productosEnCarrito = productos,
                    catalogoCompleto = catalogo,
                    isLoadingInventario = false,
                    totalVenta = total,
                    perfilesDisponibles = perfiles,
                    perfilSeleccionado = perfilActualizado
                )
            }
        }.launchIn(viewModelScope)
    }

    fun seleccionarPerfil(perfil: PerfilVenta) {
        _uiState.update { it.copy(perfilSeleccionado = perfil) }
        // Se elimina limpiarCarrito() para preservar la precarga e items seleccionados al navegar entre perfiles
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setMostrarDialogoSinVenta(mostrar: Boolean) {
        _uiState.update { it.copy(mostrarDialogoSinVenta = mostrar) }
    }

    fun seleccionarMotivoSinVenta(motivo: String) {
        _uiState.update { it.copy(motivoSinVenta = motivo) }
    }

    fun limpiarCarrito() {
        _uiState.update { it.copy(cantidades = emptyMap()) }
        Log.d("VentaViewModel", "🛒 Carrito vaciado por el usuario.")
    }

    fun actualizarCantidad(productoId: String, nuevaCantidad: Int) {
        _uiState.update { state ->
            val producto = state.catalogoCompleto.find { it.id == productoId }
            if (producto != null) {
                // 🔥 RESTAURADO: Bloqueo estricto al stock disponible
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
        motivoVisita: String? = null,
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

                val ventaId = ventaRepository.guardarVentaLocal(
                    clienteId = clienteId,
                    clienteNombre = clienteNombre,
                    clienteImagenUrl = clienteFotoUrl,
                    productos = productosVenta,
                    total = 0.0, // El total se recalcula internamente
                    metodoPago = metodoPago,
                    vendedorId = uidVendedor,
                    vendedorNombre = nombreVendedor,
                    almacenId = almacenId,
                    latitud = miUbicacion?.latitude ?: 0.0,
                    longitud = miUbicacion?.longitude ?: 0.0,
                    fueraDeRango = !_uiState.value.estaEnRango,
                    fotoEvidencia = fotoEvidenciaUrl,
                    motivoVisita = motivoVisita
                )

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(estaProcesando = false) }
                    onResultado(true, "Venta registrada con éxito", ventaId)
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
