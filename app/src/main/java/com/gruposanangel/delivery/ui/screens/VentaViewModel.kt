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
    val motivoSinVenta: String? = null,

    // 🔥 FILTROS DE RUTA PARA ADMINISTRADOR
    val filtroRutaAdmin: String? = null,
    val rutasDisponibles: List<String> = emptyList(),
    val mostrarFiltrosAdmin: Boolean = false
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
    private val clienteDao: com.gruposanangel.delivery.data.ClienteDao? = null // 🔥 Agregado para mapeo UI
) : ViewModel() {

    private val _uiState = MutableStateFlow(VentaUiState())
    val uiState: StateFlow<VentaUiState> = _uiState.asStateFlow()

    private val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    private var salesListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val _ventasPeriodo = MutableStateFlow<List<VentaEntity>>(emptyList())
    val ventasPeriodo: StateFlow<List<VentaEntity>> = _ventasPeriodo.asStateFlow()

    // 🔥 CAPACIDAD DE SOBREESCRITURA DE ROL (Para Admins en modo Ruta)
    private val _isAdminOverride = MutableStateFlow<Boolean?>(null)

    private val _filtroRutaAdmin = MutableStateFlow<String?>(null)
    private val _rutasDisponibles = MutableStateFlow<List<String>>(emptyList())
    private val _rutaToAlmacenMap = MutableStateFlow<Map<String, String>>(emptyMap())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ventasHoyFlow: StateFlow<List<VentaEntity>> = combine(
        repositoryUsuario.getUsuarioActual(),
        _isAdminOverride,
        _filtroRutaAdmin,
        _rutaToAlmacenMap
    ) { args ->
        val usuario = args[0] as? com.gruposanangel.delivery.data.UsuarioEntity
        val override = args[1] as? Boolean
        val filtro = args[2] as? String
        @Suppress("UNCHECKED_CAST")
        val mapaRutas = args[3] as? Map<String, String> ?: emptyMap()

        val uid = usuario?.uid ?: ""
        val puesto = usuario?.puestoTrabajo?.trim() ?: ""
        val almid = usuario?.ultimoAlmacenNombre ?: ""
        val rNom = usuario?.ultimaRutaNombre ?: ""
        val rId = usuario?.ultimaRutaId ?: ""
        
        val puestoLimpio = puesto.uppercase().trim()
        val esAdminReal = puestoLimpio == "CEO" || puestoLimpio.contains("GERENTE") || 
                         puestoLimpio.contains("SUPERVISOR") || puestoLimpio.contains("ADMIN") ||
                         puestoLimpio.contains("DIRECCION")
        
        val esVendedorReal = puestoLimpio.contains("VENDEDOR") || puestoLimpio.contains("SUPLENTE")
        
        val modoVendedorActivo = if (esAdminReal) {
            override == false
        } else if (esVendedorReal) {
            override != true
        } else {
            false
        }
        
        val idParaQuery = if (modoVendedorActivo) uid else ""
            
        val ahoraRelativo = com.gruposanangel.delivery.utilidades.TimeManager.getHoraReal()
        val ahoraReal = if (ahoraRelativo > 1000000000000L) ahoraRelativo else System.currentTimeMillis()
        
        val cal = Calendar.getInstance()
        cal.timeInMillis = ahoraReal
        
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val inicio = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val fin = cal.timeInMillis
        
        val resultFlow = if (modoVendedorActivo && (almid.isNotEmpty() || rNom.isNotEmpty() || rId.isNotEmpty())) {
            ventaRepository.obtenerVentasPorUnidadPeriodoFlow(almid, rNom, rId, inicio, fin)
        } else if (filtro != null && !modoVendedorActivo) {
            val almacenAsociado = mapaRutas[filtro] ?: filtro
            ventaRepository.obtenerVentasPorUnidadPeriodoFlow(almacenAsociado, filtro, filtro, inicio, fin)
        } else {
            ventaRepository.obtenerVentasPorPeriodoFlow(idParaQuery, inicio, fin)
        }
        resultFlow
    }.flatMapLatest { it }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ticketsHoyFlow: StateFlow<List<TicketVenta>> = ventasHoyFlow.flatMapLatest { ventas ->
        flow {
            val lista = if (clienteDao != null) {
                val clientes = clienteDao.getAllClientes()
                val mapFotos = clientes.associate { it.id to (it.fotografiaUrl ?: "") }
                ventas.map { v ->
                    TicketVenta(
                        id = v.id,
                        cliente = v.clienteNombre,
                        total = v.total,
                        fecha = Date(v.fecha),
                        sincronizado = v.sincronizado,
                        fotoCliente = v.clienteImagenUrl?.takeIf { it.isNotEmpty() } ?: mapFotos[v.clienteId] ?: "",
                        estado = v.estado,
                        intentosSync = v.intentosSync,
                        ultimoError = v.ultimoError
                    )
                }
            } else {
                ventas.map { v ->
                    TicketVenta(
                        id = v.id, cliente = v.clienteNombre, total = v.total,
                        fecha = Date(v.fecha), sincronizado = v.sincronizado,
                        fotoCliente = v.clienteImagenUrl ?: "", estado = v.estado,
                        intentosSync = v.intentosSync, ultimoError = v.ultimoError
                    )
                }
            }
            emit(lista)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        observarUsuarioYPerfiles()
        observarBaseInventario()
        observarInventarioOptimizado()
        escucharVentasNube()
        observarEstadoRutaReactivo()
        observarEstadoJornada()
        escucharRutas()
    }

    private fun escucharRutas() {
        db.collection("rutas").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val names = mutableListOf<String>()
                val map = mutableMapOf<String, String>()
                snapshot.documents.forEach { doc ->
                    val name = doc.getString("nombre") ?: doc.id
                    names.add(name)
                    
                    // Intentamos obtener el ID del almacén asociado (Referencia o String)
                    val almRef = doc.get("almacenAsignado") as? com.google.firebase.firestore.DocumentReference
                    if (almRef != null) {
                        map[name] = almRef.id
                    } else {
                        val almId = doc.getString("almacenAsignado")
                        if (almId != null) map[name] = almId
                    }
                }
                _rutasDisponibles.value = listOf("TODAS") + names.distinct().sorted()
                _rutaToAlmacenMap.value = map
            }
        }
    }

    fun cambiarFiltroRuta(ruta: String) {
        _filtroRutaAdmin.value = if (ruta == "TODAS") null else ruta
    }

    fun sobreescribirAdmin(esAdmin: Boolean) {
        _isAdminOverride.value = esAdmin
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("VentaDebug", "🚀 PRECARGA VELOZ - Cliente: $clienteId")

                // 1. Obtener última venta e inventario en paralelo (VÍA ROOM - INSTANTÁNEO)
                val ultimaVenta = ventaRepository.obtenerUltimaVentaConProductosPorCliente(clienteId)
                    ?: ventaRepository.obtenerTicketCompletoFirestorePorCliente(clienteId)?.let { ticketCloud ->
                        VentaEntity(
                            id = ticketCloud.numeroTicket, clienteId = clienteId, clienteNombre = ticketCloud.cliente,
                            total = ticketCloud.total, metodoPago = "Efectivo", vendedorId = "",
                            fecha = ticketCloud.fecha.time, horaDispositivo = ticketCloud.fecha.time,
                            horaVerificada = ticketCloud.fecha.time, alertaTiempo = false, sincronizado = true
                        )
                    }

                if (ultimaVenta == null) return@launch

                val stockTotal = repositoryInventario.obtenerProductosLocal().first()
                val usuario = repositoryUsuario.getUsuarioActual().first()

                // 3. Determinar almacén objetivo
                val filtroAdmin = _filtroRutaAdmin.value
                val almacenIdPerfil = usuario?.ultimoAlmacenId ?: ""
                val almacenNombrePerfil = usuario?.ultimoAlmacenNombre ?: ""

                val stockFiltrado = stockTotal.filter { entidad ->
                    if (filtroAdmin != null) {
                        entidad.id.endsWith("_$filtroAdmin")
                    } else {
                        entidad.id.endsWith("_$almacenNombrePerfil") || (almacenIdPerfil.isNotEmpty() && entidad.id.endsWith("_$almacenIdPerfil"))
                    }
                }

                if (stockFiltrado.isEmpty()) return@launch

                // 4. Obtener detalles y cruzar
                var detalles = ventaRepository.obtenerDetallesDeVenta(ultimaVenta.id)
                if (detalles.isEmpty()) {
                    val ticketCompleto = ventaRepository.obtenerTicketCompletoFirestore(ultimaVenta.id)
                    detalles = ticketCompleto?.productos?.map { p ->
                        VentaDetalleEntity(
                            ventaId = ultimaVenta.id, productoId = p.nombre.split("_")[0],
                            nombre = p.nombre, precio = p.precio, cantidad = p.cantidad
                        )
                    } ?: emptyList()
                }

                val nuevasCantidades = mutableMapOf<String, Int>()
                detalles.forEach { detalle ->
                    val pIdLimpio = detalle.productoId.trim()
                    val productoMatch = stockFiltrado.find { 
                        it.productoId == pIdLimpio || it.nombre.lowercase().trim() == detalle.nombre.lowercase().trim()
                    }

                    if (productoMatch != null) {
                        val cantidadSugerida = Math.min(detalle.cantidad, productoMatch.cantidadDisponible)
                        if (cantidadSugerida > 0) nuevasCantidades[productoMatch.id] = cantidadSugerida
                    }
                }
                
                if (nuevasCantidades.isNotEmpty()) {
                    _cantidades.value = nuevasCantidades
                }
            } catch (e: Exception) {
                Log.e("VentaDebug", "Error en precarga veloz", e)
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
                val almacenId = usuario.ultimoAlmacenId ?: ""
                val esVendedor = puesto.contains("Vendedor", ignoreCase = true) || puesto.contains("Suplente", ignoreCase = true)
                val idParaSync = if (esVendedor) uid else ""

                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                val inicio = cal.time

                salesListener?.remove()
                
                var query: com.google.firebase.firestore.Query = db.collection("ventas")
                    .whereGreaterThanOrEqualTo("fecha", inicio)
                
                if (esVendedor) {
                    // Escuchamos prioritariamente por Almacén si está disponible
                    if (almacenId.isNotEmpty()) {
                        query = query.whereEqualTo("almacenId", almacenId)
                    } else {
                        query = query.whereEqualTo("vendedorId", uid)
                    }
                }

                salesListener = query.addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        viewModelScope.launch {
                            ventaRepository.descargarVentasDia(idParaSync, almacenId)
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

    private val _searchQuery = MutableStateFlow("")
    private val _cantidades = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _perfilSeleccionado = MutableStateFlow<PerfilVenta?>(null)

    // 🔥 CACHÉ DE PROCESAMIENTO
    private val _perfilesCache = MutableStateFlow<List<PerfilVenta>>(emptyList())
    private val _productosAlmacenCache = MutableStateFlow<List<com.gruposanangel.delivery.data.ProductoEntity>>(emptyList())

    init {
        // Inicializar flujos de datos
        observarUsuarioYPerfiles()
        observarBaseInventario()
        observarInventarioOptimizado()
        
        // Servicios secundarios
        escucharVentasNube()
        observarEstadoRutaReactivo()
        observarEstadoJornada()
        escucharRutas()
    }

    private fun observarUsuarioYPerfiles() {
        repositoryUsuario.getUsuarioActual()
            .map { usuario ->
                try {
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
                } catch (e: Exception) { emptyList() }
            }
            .onEach { perfiles -> _perfilesCache.value = perfiles }
            .launchIn(viewModelScope)
    }

    private fun observarBaseInventario() {
        combine(
            repositoryInventario.obtenerProductosLocal(),
            repositoryUsuario.getUsuarioActual()
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val productos = args[0] as? List<com.gruposanangel.delivery.data.ProductoEntity> ?: emptyList()
            val usuario = args[1] as? com.gruposanangel.delivery.data.UsuarioEntity

            val almacenesPermitidos = usuario?.almacenesConfig?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
            productos.filter { entidad ->
                if (almacenesPermitidos.isNotEmpty()) {
                    almacenesPermitidos.any { almacen -> entidad.id.endsWith("_$almacen") }
                } else {
                    val principal = usuario?.ultimoAlmacenNombre
                    principal != null && entidad.id.endsWith("_$principal")
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .catch { e -> Log.e("VentaVM", "Error en base inventario", e) }
        .onEach { filtrados -> _productosAlmacenCache.value = filtrados }
        .launchIn(viewModelScope)
    }

    private fun observarInventarioOptimizado() {
        combine(
            _productosAlmacenCache,
            _searchQuery,
            _cantidades,
            _perfilSeleccionado,
            _perfilesCache,
            _filtroRutaAdmin,
            _rutasDisponibles,
            _isAdminOverride
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            val entidades = args[0] as? List<com.gruposanangel.delivery.data.ProductoEntity> ?: emptyList()
            val query = args[1] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val mapaCantidades = args[2] as? Map<String, Int> ?: emptyMap()
            val perfilActivo = args[3] as? PerfilVenta
            @Suppress("UNCHECKED_CAST")
            val perfiles = args[4] as? List<PerfilVenta> ?: emptyList()
            val filtroRuta = args[5] as? String
            @Suppress("UNCHECKED_CAST")
            val rutas = args[6] as? List<String> ?: emptyList()
            val override = args[7] as? Boolean

            val mostrarFiltros = (override != false) 

            // 1. Filtrado por Perfil Operativo
            val cumplePerfil = if (perfilActivo != null) {
                entidades.filter { entidad ->
                    perfilActivo.filtros.any { filtro ->
                        val marcaMatch = entidad.marca.trim().equals(filtro.marca.trim(), ignoreCase = true)
                        val categoriaMatch = if (filtro.categorias.isNotEmpty()) {
                            filtro.categorias.any { it.trim().equals(entidad.categoria.trim(), ignoreCase = true) }
                        } else true
                        marcaMatch && categoriaMatch
                    }
                }
            } else entidades

            // 2. Mapeo a Plantilla
            val catalogo = cumplePerfil.map { e ->
                Plantilla_Producto(
                    id = e.id, nombre = e.nombre, precio = e.precio,
                    cantidad = mapaCantidades[e.id] ?: 0,
                    cantidadDisponible = e.cantidadDisponible,
                    imagenUrl = e.imagenUrl ?: "",
                    marca = e.marca, categoria = e.categoria
                )
            }.sortedWith(compareBy<Plantilla_Producto>({ it.categoria }, { it.nombre }))

            // 3. Resultados de búsqueda
            val productosMapeados = if (query.isBlank()) {
                catalogo.filter { it.cantidadDisponible > 0 || it.cantidad > 0 }
                    .sortedByDescending { it.cantidad > 0 }
            } else {
                catalogo.filter { it.nombre.contains(query, ignoreCase = true) }
            }

            val total = catalogo.sumOf { it.precio * it.cantidad }
            val perfilActualizado = perfiles.find { it.id == perfilActivo?.id } ?: perfiles.firstOrNull()

            _uiState.update { state ->
                state.copy(
                    productosEnCarrito = productosMapeados,
                    catalogoCompleto = catalogo,
                    isLoadingInventario = false,
                    totalVenta = total,
                    perfilesDisponibles = perfiles,
                    perfilSeleccionado = perfilActualizado,
                    filtroRutaAdmin = filtroRuta,
                    rutasDisponibles = rutas,
                    searchQuery = query,
                    cantidades = mapaCantidades,
                    mostrarFiltrosAdmin = mostrarFiltros
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .catch { e -> Log.e("VentaVM", "Error en flujo optimizado", e) }
        .launchIn(viewModelScope)
    }

    fun seleccionarPerfil(perfil: PerfilVenta) {
        _perfilSeleccionado.value = perfil
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun setMostrarDialogoSinVenta(mostrar: Boolean) {
        _uiState.update { it.copy(mostrarDialogoSinVenta = mostrar) }
    }

    fun seleccionarMotivoSinVenta(motivo: String) {
        _uiState.update { it.copy(motivoSinVenta = motivo) }
    }

    fun limpiarCarrito() {
        _cantidades.value = emptyMap<String, Int>()
        Log.d("VentaViewModel", "🛒 Carrito vaciado por el usuario.")
    }

    fun actualizarCantidad(productoId: String, nuevaCantidad: Int) {
        val cantidadesActuales = _cantidades.value.toMutableMap()
        // Buscamos el producto en el catálogo actual del UIState para verificar stock
        val producto = _uiState.value.catalogoCompleto.find { it.id == productoId }
        
        if (producto != null) {
            val valorFinal = nuevaCantidad.coerceIn(0, producto.cantidadDisponible)
            if (valorFinal > 0) cantidadesActuales[productoId] = valorFinal
            else cantidadesActuales.remove(productoId)
            
            _cantidades.value = cantidadesActuales
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
                // 🔥 PROTECCIÓN ADMIN: Si estamos en modo Admin, sincronizamos globalmente (vendedorId = "")
                val isAdmin = _isAdminOverride.value == true
                val idSync = if (isAdmin) "" else vendedorId
                
                ventaRepository.descargarVentasDia(idSync)
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
        rutaId: String? = null,
        rutaNombre: String? = null,
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
                    rutaId = rutaId,
                    rutaNombre = rutaNombre,
                    latitud = miUbicacion?.latitude ?: 0.0,
                    longitud = miUbicacion?.longitude ?: 0.0,
                    fueraDeRango = !_uiState.value.estaEnRango,
                    fotoEvidencia = fotoEvidenciaUrl,
                    motivoVisita = motivoVisita
                )

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(estaProcesando = false) }
                    
                    // 🔥 DISPARO INMEDIATO DE SINCRONIZACIÓN
                    com.gruposanangel.delivery.SegundoPlano.scheduleSyncWorkers(com.google.firebase.FirebaseApp.getInstance().applicationContext)

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
            this@VentaViewModel.ventaRepository.obtenerVentaPorId(ventaId)
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
    private val repositoryUsuario: RepositoryUsuario,
    private val clienteDao: com.gruposanangel.delivery.data.ClienteDao? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return VentaViewModel(repositoryInventario, ventaRepository, repositoryUsuario, clienteDao) as T
    }
}
