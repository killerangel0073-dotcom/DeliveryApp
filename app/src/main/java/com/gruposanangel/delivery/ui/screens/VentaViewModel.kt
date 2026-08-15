package com.gruposanangel.delivery.ui.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.SegundoPlano.LocationState
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.data.PerfilVenta
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.utilidades.TimeManager
import TicketVentaCompleto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.*

sealed class EstadoRuta {
    object Cargando : EstadoRuta()
    object SinRuta : EstadoRuta()
    data class Error(val mensaje: String) : EstadoRuta()
    data class ConRuta(val nombreAlmacen: String, val almacenId: String) : EstadoRuta()
}

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
    val enRuta: Boolean = true,
    
    // 🔥 VISITA SIN VENTA
    val mostrarDialogoSinVenta: Boolean = false,
    val motivoSinVenta: String? = null,

    // 🔥 FILTROS DE RUTA PARA ADMINISTRADOR
    val filtroRutaAdmin: String? = null,
    val rutasDisponibles: List<String> = emptyList(),
    val mostrarFiltrosAdmin: Boolean = false,

    // 🔥 FILTROS DE FECHA
    val fechaInicio: Long = 0L,
    val fechaFin: Long = 0L,
    val numDiasFiltro: Int = 1,
    val totalVentaPeriodo: Double = 0.0,
    val visitasCount: Int = 0,
    val cargandoDashboard: Boolean = false,

    // 🔥 MODO CONSOLIDADO
    val modoConsolidado: Boolean = false,
    val subtotalesPorPerfil: Map<String, Double> = emptyMap()
)

class VentaViewModel(
    private val repositoryInventario: RepositoryInventario,
    private val ventaRepository: VentaRepository,
    private val repositoryUsuario: RepositoryUsuario,
    private val clienteDao: com.gruposanangel.delivery.data.ClienteDao? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(VentaUiState())
    val uiState: StateFlow<VentaUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private var salesListener: ListenerRegistration? = null
    
    // 🔥 FILTROS DE FECHA REACTIVOS
    private val _fechaInicioFiltro = MutableStateFlow<Long?>(null)
    private val _fechaFinFiltro = MutableStateFlow<Long?>(null)

    // 🔥 CAPACIDAD DE SOBREESCRITURA DE ROL
    private val _isAdminOverride = MutableStateFlow<Boolean?>(null)
    private val _filtroRutaAdmin = MutableStateFlow<String?>(null)
    private val _rutaToAlmacenMap = MutableStateFlow<Map<String, String>>(emptyMap())

    private var jobGeocerca: Job? = null

    // 🔥 Soporte para Pantalla_Ventas_Periodo
    private val _ventasPeriodo = MutableStateFlow<List<VentaEntity>>(emptyList())
    val ventasPeriodo: StateFlow<List<VentaEntity>> = _ventasPeriodo.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ventasHoyFlow: StateFlow<List<VentaEntity>> = combine(
        repositoryUsuario.getUsuarioActual(),
        _isAdminOverride,
        _filtroRutaAdmin,
        _rutaToAlmacenMap,
        _fechaInicioFiltro,
        _fechaFinFiltro
    ) { args ->
        val usuario = args[0] as? UsuarioEntity
        val override = args[1] as? Boolean
        val filtro = args[2] as? String
        @Suppress("UNCHECKED_CAST")
        val mapaRutas = args[3] as? Map<String, String> ?: emptyMap()
        val startFiltro = args[4] as? Long
        val endFiltro = args[5] as? Long

        val uid = usuario?.uid ?: ""
        val puesto = usuario?.puestoTrabajo?.trim() ?: ""
        val almid = usuario?.ultimoAlmacenNombre ?: ""
        val rNom = usuario?.ultimaRutaNombre ?: ""
        val rId = usuario?.ultimaRutaId ?: ""
        
        val puestoLimpio = puesto.uppercase()
        val esAdminReal = puestoLimpio == "CEO" || puestoLimpio.contains("GERENTE") || 
                         puestoLimpio.contains("SUPERVISOR") || puestoLimpio.contains("ADMIN")
        
        val esVendedorReal = puestoLimpio.contains("VENDEDOR") || puestoLimpio.contains("SUPLENTE")
        
        val modoVendedorActivo = if (esAdminReal) override == false else if (esVendedorReal) override != true else false
        val idParaQuery = if (modoVendedorActivo) uid else ""
            
        val ahoraReal = TimeManager.getHoraReal().takeIf { it > 1000000L } ?: System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = ahoraReal }
        
        val inicio: Long
        val fin: Long
        
        if (startFiltro != null && endFiltro != null) {
            inicio = startFiltro
            fin = endFiltro
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            inicio = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            fin = cal.timeInMillis
        }
        
        if (modoVendedorActivo && (almid.isNotEmpty() || rNom.isNotEmpty() || rId.isNotEmpty())) {
            ventaRepository.obtenerVentasPorUnidadPeriodoFlow(almid, rNom, rId, inicio, fin)
        } else if (filtro != null && !modoVendedorActivo) {
            val almacenAsociado = mapaRutas[filtro] ?: filtro
            ventaRepository.obtenerVentasPorUnidadPeriodoFlow(almacenAsociado, filtro, filtro, inicio, fin)
        } else {
            ventaRepository.obtenerVentasPorPeriodoFlow(idParaQuery, inicio, fin)
        }
    }.flatMapLatest { it }
    .onEach { lista -> 
        _uiState.update { state -> 
            state.copy(
                estaProcesando = false,
                cargandoDashboard = false,
                totalVentaPeriodo = lista.filter { it.estado != "CANCELADA" }.sumOf { it.total },
                visitasCount = lista.count { it.estado != "CANCELADA" }
            ) 
        } 
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ticketsHoyFlow: StateFlow<List<TicketVenta>> = ventasHoyFlow.flatMapLatest { ventas ->
        flow {
            val lista = if (clienteDao != null) {
                val clientes = clienteDao.getAllClientes()
                val mapFotos = clientes.associate { it.id to (it.fotografiaUrl ?: "") }
                ventas.map { v ->
                    TicketVenta(
                        id = v.id, cliente = v.clienteNombre, total = v.total,
                        fecha = Date(v.fecha), sincronizado = v.sincronizado,
                        fotoCliente = v.clienteImagenUrl?.takeIf { it.isNotEmpty() } ?: mapFotos[v.clienteId] ?: "",
                        estado = v.estado, intentosSync = v.intentosSync, ultimoError = v.ultimoError
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun escucharRutas() {
        db.collection("rutas").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val names = mutableListOf<String>()
                val map = mutableMapOf<String, String>()
                snapshot.documents.forEach { doc ->
                    val name = doc.getString("nombre") ?: doc.id
                    names.add(name)
                    val almRef = doc.get("almacenAsignado") as? com.google.firebase.firestore.DocumentReference
                    if (almRef != null) map[name] = almRef.id
                    else doc.getString("almacenAsignado")?.let { map[name] = it }
                }
                val finalRutas = listOf("TODAS") + names.distinct().sorted()
                _uiState.update { it.copy(rutasDisponibles = finalRutas) }
                _rutaToAlmacenMap.value = map
            }
        }
    }

    fun cambiarFiltroRuta(ruta: String) {
        val filtro = if (ruta == "TODAS") null else ruta
        _filtroRutaAdmin.value = filtro
        _uiState.update { it.copy(filtroRutaAdmin = filtro) }
    }

    fun actualizarRangoFechas(inicio: Long, fin: Long) {
        if (_fechaInicioFiltro.value == inicio && _fechaFinFiltro.value == fin) return
        
        _uiState.update { it.copy(cargandoDashboard = true) }
        _fechaInicioFiltro.value = inicio
        _fechaFinFiltro.value = fin
        
        val calStart = Calendar.getInstance().apply { timeInMillis = inicio; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val calEnd = Calendar.getInstance().apply { timeInMillis = fin; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        var dias = 0
        while (!calStart.after(calEnd)) { dias++; calStart.add(Calendar.DAY_OF_YEAR, 1) }
        _uiState.update { it.copy(fechaInicio = inicio, fechaFin = fin, numDiasFiltro = if (dias <= 0) 1 else dias) }
    }

    fun resetFiltro() {
        val ahoraReal = TimeManager.getHoraReal().takeIf { it > 1000000L } ?: System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = ahoraReal }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val inicio = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val fin = cal.timeInMillis

        val yaEsHoy = _fechaInicioFiltro.value == null && _fechaFinFiltro.value == null
        _uiState.update { it.copy(
            cargandoDashboard = !yaEsHoy, numDiasFiltro = 1,
            fechaInicio = inicio, fechaFin = fin,
            totalVentaPeriodo = if (yaEsHoy) it.totalVentaPeriodo else 0.0,
            visitasCount = if (yaEsHoy) it.visitasCount else 0
        ) }
        if (!yaEsHoy) {
            _fechaInicioFiltro.value = null
            _fechaFinFiltro.value = null
        }
    }

    fun cargarVentasPorPeriodo(inicio: Date, fin: Date) {
        viewModelScope.launch(Dispatchers.IO) {
            val u = repositoryUsuario.obtenerUsuarioActual()
            val uid = u?.uid ?: ""
            
            val cal = Calendar.getInstance()
            cal.time = inicio
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val tInicio = cal.timeInMillis

            cal.time = fin
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            val tFin = cal.timeInMillis

            val ventas = ventaRepository.obtenerVentasPorPeriodo(uid, tInicio, tFin)
            _ventasPeriodo.value = ventas
        }
    }

    fun sobreescribirAdmin(esAdmin: Boolean) {
        _isAdminOverride.value = esAdmin
        _uiState.update { it.copy(mostrarFiltrosAdmin = esAdmin) }
    }

    fun verificarRutaAsignadaLocal(uid: String) {
        viewModelScope.launch {
            val usuario = repositoryUsuario.obtenerUsuarioActual()
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
    }

    private fun observarEstadoRutaReactivo() {
        repositoryUsuario.getUsuarioActual().onEach { usuario ->
            val nuevoEstado = if (usuario?.ultimaRutaId != null && usuario.ultimoAlmacenId != null) {
                EstadoRuta.ConRuta(usuario.ultimoAlmacenNombre ?: "Almacén", usuario.ultimoAlmacenId)
            } else EstadoRuta.SinRuta
            _uiState.update { it.copy(estadoRuta = nuevoEstado) }
        }.launchIn(viewModelScope)
    }

    private fun observarEstadoJornada() {
        repositoryUsuario.getUsuarioActual().onEach { _ ->
            _uiState.update { it.copy(enRuta = true) }
        }.launchIn(viewModelScope)
    }

    fun escucharVentasNube() {
        repositoryUsuario.getUsuarioActual()
            .onEach { user ->
                if (user == null) return@onEach
                
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val puesto = user.puestoTrabajo?.trim() ?: ""
                val esVendedor = puesto.contains("VENDEDOR", ignoreCase = true) || puesto.contains("SUPLENTE", ignoreCase = true)
                val idParaSync = if (esVendedor) uid else ""
                val almid = user.ultimoAlmacenNombre ?: ""

                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val inicio = cal.time

                salesListener?.remove()
                
                var query: Query = db.collection("ventas")
                    .whereGreaterThanOrEqualTo("fecha", inicio)
                
                if (esVendedor) {
                    query = query.whereEqualTo("almacenId", almid)
                }

                salesListener = query.addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        viewModelScope.launch {
                            ventaRepository.descargarVentasDia(idParaSync, almid)
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun sincronizarVentasDia(uid: String) {
        viewModelScope.launch {
            try {
                val u = repositoryUsuario.obtenerUsuarioActual()
                val almid = u?.ultimoAlmacenNombre ?: ""
                val puesto = u?.puestoTrabajo?.trim() ?: ""
                val esVendedor = puesto.contains("VENDEDOR", ignoreCase = true) || puesto.contains("SUPLENTE", ignoreCase = true)
                val idParaSync = if (esVendedor) uid else ""
                
                ventaRepository.descargarVentasDia(idParaSync, almid)
            } catch (e: Exception) {
                Log.e("VentaViewModel", "Error sync manual", e)
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    private val _cantidades = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _perfilSeleccionado = MutableStateFlow<PerfilVenta?>(null)
    private val _modoConsolidado = MutableStateFlow(false)
    private val _perfilesCache = MutableStateFlow<List<PerfilVenta>>(emptyList())
    private val _productosAlmacenCache = MutableStateFlow<List<ProductoEntity>>(emptyList())

    private fun observarUsuarioYPerfiles() {
        repositoryUsuario.getUsuarioActual().onEach { u ->
            val perfiles = try {
                val json = u?.perfilesVentaJson
                if (!json.isNullOrBlank()) {
                    val array = JSONArray(json)
                    (0 until array.length()).map { i ->
                        val obj = array.getJSONObject(i)
                        val filtrosArr = obj.getJSONArray("filtros")
                        val filtros = (0 until filtrosArr.length()).map { j ->
                            val fObj = filtrosArr.getJSONObject(j)
                            val catsArr = fObj.optJSONArray("categorias")
                            val cats = if (catsArr != null) (0 until catsArr.length()).map { catsArr.getString(it) } else emptyList()
                            FiltroPerfil(fObj.getString("marca"), cats)
                        }
                        PerfilVenta(obj.getString("id"), obj.getString("nombre"), filtros)
                    }
                } else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            
            // 🔥 Actualizar cache y estado de forma atómica para evitar saltos
            val selected = _perfilSeleccionado.value ?: perfiles.firstOrNull()
            
            _perfilesCache.value = perfiles
            _perfilSeleccionado.value = selected
            
            val puesto = u?.puestoTrabajo?.trim()?.uppercase() ?: ""
            val esAdminReal = puesto == "CEO" || puesto.contains("GERENTE") || 
                             puesto.contains("SUPERVISOR") || puesto.contains("ADMIN")

            _uiState.update { it.copy(
                perfilesDisponibles = perfiles, 
                perfilSeleccionado = selected,
                mostrarFiltrosAdmin = _isAdminOverride.value ?: esAdminReal
            ) }
        }.launchIn(viewModelScope)
    }

    private fun observarBaseInventario() {
        // 🔥 Refuerzo de carga: Observamos el cambio de almacén y cargamos solo lo que pertenece a ese almacén
        _uiState.map { it.estadoRuta }.distinctUntilChanged().flatMapLatest { estado ->
            val almacenId = (estado as? EstadoRuta.ConRuta)?.almacenId
            if (!almacenId.isNullOrEmpty()) {
                repositoryInventario.obtenerProductosLocal().map { lista ->
                    // Filtro estricto por almacén ID para evitar ver inventario de otros
                    lista.filter { it.id.contains(almacenId, ignoreCase = true) }
                }
            } else {
                flowOf(emptyList())
            }
        }.onEach { filtrados ->
            Log.d("VentaViewModel", "Inventario filtrado por almacén cargado: ${filtrados.size} productos")
            _productosAlmacenCache.value = filtrados 
        }.launchIn(viewModelScope)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observarInventarioOptimizado() {
        combine(
            _productosAlmacenCache, 
            _perfilSeleccionado, 
            _searchQuery, 
            _cantidades, 
            _modoConsolidado
        ) { productos: List<ProductoEntity>, perfil: PerfilVenta?, query: String, cants: Map<String, Int>, consolidado: Boolean ->
            
            val perfiles = _perfilesCache.value
            val subtotales = mutableMapOf<String, Double>()
            
            // 1. Calcular subtotales por perfil (Analizando todo el catálogo local)
            perfiles.forEach { pVenta ->
                val suma = productos.filter { p ->
                    val c = cants[p.productoId] ?: cants[p.id] ?: 0
                    if (c > 0) {
                        pVenta.filtros.any { f ->
                            val mMatch = p.marca.trim().equals(f.marca.trim(), ignoreCase = true)
                            val cMatch = if (f.categorias.isNotEmpty()) f.categorias.any { it.trim().equals(p.categoria?.trim() ?: "", ignoreCase = true) } else true
                            mMatch && cMatch
                        }
                    } else false
                }.sumOf { (cants[it.productoId] ?: cants[it.id] ?: 0) * it.precio }
                if (suma > 0) subtotales[pVenta.nombre] = suma
            }

            val filtros = perfil?.filtros ?: emptyList()
            
            // 2. Filtrar por perfil y búsqueda
            val filtrados = productos.filter { p ->
                val matchesProfile = if (filtros.isEmpty()) true 
                else filtros.any { f -> 
                    (f.marca.isEmpty() || p.marca.equals(f.marca, ignoreCase = true)) &&
                    (f.categorias.isEmpty() || f.categorias.any { it.equals(p.categoria, ignoreCase = true) })
                }
                matchesProfile && p.nombre.contains(query, ignoreCase = true)
            }

            // 3. DE-DUPLICACIÓN
            val unicos = filtrados.groupBy { it.productoId.ifEmpty { it.nombre } }
                .map { entry -> 
                    entry.value.maxByOrNull { it.cantidadDisponible } ?: entry.value.first() 
                }

            // 4. Mapear a Plantilla_Producto
            val listaMapeada = unicos.map { p ->
                val cantidad = cants[p.productoId] ?: cants[p.id] ?: 0
                
                Plantilla_Producto(
                    id = p.id,
                    nombre = p.nombre,
                    precio = p.precio,
                    cantidad = cantidad,
                    cantidadDisponible = p.cantidadDisponible,
                    imagenUrl = p.imagenUrl ?: "",
                    cantidadUnitario = p.cantidadUnitario,
                    unidadesPorDisplay = p.unidadesPorDisplay,
                    gramosVenta = p.gramosVenta,
                    precioCompra = p.precioCompra,
                    marca = p.marca,
                    categoria = p.categoria ?: "General"
                )
            }.sortedWith(compareBy({ it.categoria }, { it.nombre }))

            DataSnapshot(listaMapeada, subtotales, consolidado)
        }.onEach { result ->
            val totalVenta = if (result.consolidado) result.subtotales.values.sum() else result.lista.sumOf { it.cantidad * it.precio }
            
            _uiState.update { it.copy(
                catalogoCompleto = result.lista, 
                productosEnCarrito = result.lista, 
                totalVenta = totalVenta,
                subtotalesPorPerfil = result.subtotales,
                modoConsolidado = result.consolidado,
                isLoadingInventario = false
            ) }
        }.launchIn(viewModelScope)
    }

    private data class DataSnapshot(
        val lista: List<Plantilla_Producto>,
        val subtotales: Map<String, Double>,
        val consolidado: Boolean
    )

    fun toggleModoConsolidado() {
        _modoConsolidado.value = !_modoConsolidado.value
    }

    fun seleccionarPerfil(perfil: PerfilVenta) {
        _perfilSeleccionado.value = perfil
        _uiState.update { it.copy(perfilSeleccionado = perfil) }
    }

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
        _uiState.update { it.copy(searchQuery = q) }
    }

    fun actualizarCantidad(id: String, nueva: Int) {
        // Encontrar el producto en el cache para obtener su productoId base (sin warehouse)
        val producto = _productosAlmacenCache.value.find { it.id == id }
        val idMapa = producto?.productoId ?: id.split("_")[0]

        val current = _cantidades.value.toMutableMap()
        if (nueva <= 0) {
            current.remove(idMapa)
            current.remove(id) // Por seguridad borrar ambos
        } else {
            current[idMapa] = nueva
        }
        _cantidades.value = current
        _uiState.update { it.copy(cantidades = current) }
    }

    fun limpiarCarrito() {
        _cantidades.value = emptyMap()
        _uiState.update { it.copy(
            cantidades = emptyMap(),
            totalVenta = 0.0,
            productosEnCarrito = _uiState.value.catalogoCompleto.map { it.copy(cantidad = 0) }
        ) }
    }

    fun precargarUltimaVenta(clienteId: String) {
        viewModelScope.launch {
            try {
                Log.d("VentaViewModel", "🔍 Iniciando precarga inteligente Offline-First para: $clienteId")
                
                // 1. INTENTO INSTANTÁNEO (ROOM)
                ejecutarAnalisisPrecargaLocal(clienteId)

                // 2. REFRESCO EN SEGUNDO PLANO (FIRESTORE) - No bloquea la UI
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        ventaRepository.descargarHistorialCliente90Dias(clienteId)
                        // Si la descarga trajo algo nuevo y el carrito sigue vacío, volvemos a analizar
                        if (_cantidades.value.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                ejecutarAnalisisPrecargaLocal(clienteId)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VentaViewModel", "Error sync background", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("VentaViewModel", "Error en flujo precarga", e)
            }
        }
    }

    private suspend fun ejecutarAnalisisPrecargaLocal(clienteId: String) {
        try {
            // Obtener todo el historial local
            val historial = ventaRepository.obtenerTodoHistorialCliente(clienteId)
            if (historial.isEmpty()) return

            // Agrupar tickets por DÍA
            val ticketsPorDia = historial.groupBy { v ->
                val cal = Calendar.getInstance().apply { timeInMillis = v.fecha }
                "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.DAY_OF_YEAR)}"
            }.toList().sortedByDescending { it.second.maxOf { v -> v.fecha } }

            // Buscar hacia atrás el primer día con ventas reales
            for (diaEntry in ticketsPorDia) {
                val ticketsDelDia = diaEntry.second
                val mapaConsolidado = mutableMapOf<String, Int>()
                var algunaVentaConDetalles = false

                for (ticket in ticketsDelDia) {
                    if (ticket.estado == "CANCELADA") continue
                    val detalles = ventaRepository.obtenerDetallesDeVenta(ticket.id)
                    
                    if (detalles.isEmpty() && ticket.total > 0) {
                        // Si no hay detalles locales, intentar descarga rápida (solo si hay internet)
                        val ticketNube = withContext(Dispatchers.IO) { 
                            try { ventaRepository.obtenerTicketCompletoFirestore(ticket.id) } catch(e: Exception) { null }
                        }
                        ticketNube?.productos?.forEach { p ->
                            val prodLocal = _uiState.value.catalogoCompleto.find { it.nombre == p.nombre }
                            val idFinal = prodLocal?.id?.split("_")?.get(0) ?: p.nombre 
                            mapaConsolidado[idFinal] = (mapaConsolidado[idFinal] ?: 0) + p.cantidad
                            algunaVentaConDetalles = true
                        }
                    } else {
                        detalles.forEach { d ->
                            val idProd = d.productoId 
                            mapaConsolidado[idProd] = (mapaConsolidado[idProd] ?: 0) + d.cantidad
                            algunaVentaConDetalles = true
                        }
                    }
                }

                if (algunaVentaConDetalles && mapaConsolidado.values.any { it > 0 }) {
                    _cantidades.value = mapaConsolidado
                    val catalogo = _uiState.value.catalogoCompleto
                    if (catalogo.isNotEmpty()) {
                        val total = catalogo.sumOf { p -> 
                            val idBase = p.id.split("_")[0]
                            (mapaConsolidado[idBase] ?: 0) * p.precio 
                        }
                        _uiState.update { it.copy(cantidades = mapaConsolidado, totalVenta = total) }
                    } else {
                        _uiState.update { it.copy(cantidades = mapaConsolidado) }
                    }
                    Log.i("VentaViewModel", "✅ Precarga Local exitosa: ${Date(ticketsDelDia.first().fecha)}")
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("VentaViewModel", "Error en analisis local", e)
        }
    }

    fun monitorearGeocerca(lat: Double, lng: Double) {
        jobGeocerca?.cancel()
        jobGeocerca = viewModelScope.launch {
            LocationState.ultimaUbicacion.collect { location ->
                if (location != null) {
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        location.latitude, location.longitude,
                        lat, lng,
                        results
                    )
                    val distancia = results[0]
                    val enRango = distancia <= 100 
                    _uiState.update { it.copy(
                        distanciaAlClienteMetros = distancia,
                        estaEnRango = enRango,
                        requiereFotoEvidencia = !enRango
                    ) }
                }
            }
        }
    }

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
        Log.i("VentaViewModel", "Registrando ajuste doble: $tipoOperacion")
        viewModelScope.launch {
            try {
                val u = repositoryUsuario.obtenerUsuarioActual()
                val uid = u?.uid ?: ""
                val almacen = u?.ultimoAlmacenNombre ?: ""
                
                repositoryInventario.registrarDobleMovimiento(
                    tipoOperacion = tipoOperacion,
                    productoEntra = productoEntra,
                    productoSale = productoSale,
                    cantidad = cantidad,
                    vendedorId = uid,
                    almacenNombre = almacen,
                    clienteId = clienteId,
                    ticketId = ticketId,
                    motivo = motivo
                )
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("VentaViewModel", "Error en ajuste doble", e)
            }
        }
    }

    fun procesarVenta(
        clienteId: String, clienteNombre: String, clienteFotoUrl: String?,
        metodoPago: String, rutaId: String? = null, rutaNombre: String? = null,
        fotoEvidenciaUrl: String? = null, motivoVisita: String? = null,
        onResultado: (Boolean, String, String) -> Unit
    ) {
        if (_uiState.value.estaProcesando) return
        if (!_uiState.value.enRuta) {
            onResultado(false, "Debes iniciar jornada", "")
            return
        }
        _uiState.update { it.copy(estaProcesando = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val u = repositoryUsuario.obtenerUsuarioActual()
                val uid = u?.uid ?: ""
                val nom = u?.nombre ?: "Vendedor"
                val almId = (_uiState.value.estadoRuta as? EstadoRuta.ConRuta)?.almacenId
                
                // 🔥 SI ESTAMOS EN MODO CONSOLIDADO, TOMAMOS TODOS LOS PRODUCTOS CON CANTIDAD > 0 DE TODO EL CATÁLOGO LOCAL
                val prods = if (_uiState.value.modoConsolidado) {
                    val allCants = _cantidades.value
                    _productosAlmacenCache.value.filter { p -> 
                        (allCants[p.productoId] ?: allCants[p.id] ?: 0) > 0 
                    }.map { p ->
                        Plantilla_Producto(
                            id = p.id,
                            nombre = p.nombre,
                            precio = p.precio,
                            cantidad = allCants[p.productoId] ?: allCants[p.id] ?: 0
                        )
                    }
                } else {
                    _uiState.value.catalogoCompleto.filter { it.cantidad > 0 }
                }

                val vId = ventaRepository.guardarVentaLocal(
                    clienteId, clienteNombre, clienteFotoUrl, prods, 0.0, metodoPago,
                    uid, nom, almId, rutaId, rutaNombre, 0.0, 0.0,
                    !_uiState.value.estaEnRango, fotoEvidenciaUrl, motivoVisita
                )
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(estaProcesando = false) }
                    onResultado(true, "Venta guardada", vId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(estaProcesando = false) }
                    onResultado(false, "Error: ${e.message}", "")
                }
            }
        }
    }

    fun anularVenta(vId: String, motivo: String, onRes: (Boolean, String) -> Unit) {
        _uiState.update { it.copy(estaProcesando = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val u = repositoryUsuario.obtenerUsuarioActual()
                val adminUid = u?.uid ?: ""
                val adminNombre = u?.nombre ?: "Admin"
                val result = ventaRepository.anularVenta(vId, motivo, adminUid, adminNombre)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(estaProcesando = false) }
                    onRes(result.first, result.second)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(estaProcesando = false) }
                    onRes(false, e.message ?: "Error al anular venta")
                }
            }
        }
    }

    // 🔥 MÉTODOS DE CONSULTA PARA DETALLES DE VENTA
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

    init {
        resetFiltro()
        observarUsuarioYPerfiles()
        observarBaseInventario()
        observarInventarioOptimizado()
        escucharVentasNube()
        observarEstadoRutaReactivo()
        observarEstadoJornada()
        escucharRutas()
    }

    override fun onCleared() {
        super.onCleared()
        salesListener?.remove()
        jobGeocerca?.cancel()
    }
}

class VentaViewModelFactory(
    private val repositoryInventario: RepositoryInventario,
    private val ventaRepository: VentaRepository,
    private val repositoryUsuario: RepositoryUsuario,
    private val clienteDao: com.gruposanangel.delivery.data.ClienteDao? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return VentaViewModel(repositoryInventario, ventaRepository, repositoryUsuario, clienteDao) as T
    }
}
