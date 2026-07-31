package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.ProductoEntity
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class InventarioUiState(
    val isLoading: Boolean = true,
    val rutaAsignada: String? = null,
    val productos: List<Plantilla_Producto> = emptyList(),
    val productosDanados: List<Plantilla_Producto> = emptyList(),
    val notificaciones: List<Notificacion> = emptyList(),
    val error: String? = null,
    val puestoTrabajo: String? = null,
    val isAdmin: Boolean = false,
    val listaAlmacenes: List<String> = emptyList(),
    val almacenSeleccionado: String? = null,
    val esVistaGlobal: Boolean = false,
    
    // 🔥 PERFILES DE INVENTARIO (ESPEJO DE VENTAS)
    val perfilesDisponibles: List<com.gruposanangel.delivery.data.PerfilVenta> = emptyList(),
    val perfilSeleccionado: com.gruposanangel.delivery.data.PerfilVenta? = null
)

class InventarioViewModel(
    private val inventarioRepo: RepositoryInventario,
    private val usuarioRepo: RepositoryUsuario
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventarioUiState())
    val uiState: StateFlow<InventarioUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private var notificationsListener: ListenerRegistration? = null
    private var selectedWarehouseListener: ListenerRegistration? = null // 🔥 Nuevo listener dinámico
    
    private val formatoFecha = SimpleDateFormat(
        "EEEE, dd 'de' MMMM 'de' yyyy, hh:mm a", 
        Locale("es", "MX")
    )

    init {
        // 🔥 OFFLINE-FIRST: Observar datos del usuario desde Room de forma reactiva
        usuarioRepo.getUsuarioActual()
            .onEach { usuario ->
                if (usuario != null) {
                    val p = usuario.puestoTrabajo?.trim() ?: ""
                    val adminRoles = listOf("CEO", "Gerente General", "Encargado Almacen", "Auxiliar de almacen")
                    val esAdmin = p in adminRoles
                    val esAlmacenRol = p == "Encargado Almacen" || p == "Auxiliar de almacen"
                    val esDirectivo = p == "CEO" || p == "Gerente General"
                    
                    val nombreAlmacen = usuario.ultimoAlmacenNombre
                    
                    // --- PARSEAR PERFILES DE VENTA PARA FILTRADO ---
                    val perfiles = mutableListOf<com.gruposanangel.delivery.data.PerfilVenta>()
                    // 1. Agregar opción "TODO"
                    perfiles.add(com.gruposanangel.delivery.data.PerfilVenta("ALL", "TODO", emptyList()))
                    
                    // 2. Cargar perfiles configurados
                    try {
                        val json = usuario.perfilesVentaJson
                        if (!json.isNullOrBlank()) {
                            val array = org.json.JSONArray(json)
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                val filtrosArr = obj.getJSONArray("filtros")
                                val filtros = mutableListOf<com.gruposanangel.delivery.data.FiltroPerfil>()
                                for (j in 0 until filtrosArr.length()) {
                                    val fObj = filtrosArr.getJSONObject(j)
                                    val catsArr = fObj.optJSONArray("categorias")
                                    val cats = if (catsArr != null) {
                                        (0 until catsArr.length()).map { catsArr.getString(it) }
                                    } else emptyList<String>()
                                    filtros.add(com.gruposanangel.delivery.data.FiltroPerfil(fObj.getString("marca"), cats))
                                }
                                perfiles.add(com.gruposanangel.delivery.data.PerfilVenta(obj.getString("id"), obj.getString("nombre"), filtros))
                            }
                        }
                    } catch (e: Exception) { Log.e("InventarioVM", "Error parseando perfiles", e) }

                    // --- Sincronizar el perfil seleccionado ---
                    val perfilActualizado = if (_uiState.value.perfilSeleccionado != null) {
                        perfiles.find { it.id == _uiState.value.perfilSeleccionado?.id } ?: perfiles.first() // Fallback al primero (TODO)
                    } else {
                        perfiles.first()
                    }

                    _uiState.update { state ->
                        state.copy(
                            puestoTrabajo = usuario.puestoTrabajo,
                            rutaAsignada = nombreAlmacen,
                            isAdmin = esAdmin,
                            isLoading = false,
                            perfilesDisponibles = perfiles,
                            perfilSeleccionado = perfilActualizado
                        )
                    }
                    
                    if (esAdmin) {
                        cargarListaAlmacenes()
                    }
                    
                    // 🔥 PRESELECCIÓN POR ROL
                    if (esDirectivo) {
                        // CEO/Gerente: Vista Global (El mundo) por defecto
                        activarVistaGlobal()
                    } else if (esAlmacenRol) {
                        // Almacenistas: Su almacén asignado por defecto (Fallback a Huasteca)
                        seleccionarAlmacen(nombreAlmacen ?: "Almacen Huasteca")
                    }
 else if (!nombreAlmacen.isNullOrEmpty()) {
                        // Vendedores: Su propio almacén por defecto
                        seleccionarAlmacen(nombreAlmacen)
                        escucharNotificaciones(nombreAlmacen)
                        cargarStockDanado(nombreAlmacen)
                        observarMovimientosLocales(nombreAlmacen, usuario.uid)
                    }
                }
            }
            .launchIn(viewModelScope)

        // Sincronización inicial: Intentar descargar desde Firebase usando el UID de Auth
        viewModelScope.launch {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                inventarioRepo.descargarProductosFirebase(uid)
            }
        }

        // 🔥 OFFLINE-FIRST: Observar productos desde Room de forma reactiva
        combine(
            inventarioRepo.obtenerProductosLocal(),
            _uiState.map { it.perfilSeleccionado }.distinctUntilChanged()
        ) { entities, perfilActivo ->
                val state = _uiState.value
                val almacenActual = state.almacenSeleccionado
                
                // 🛡️ REGLA DE VISIBILIDAD: Para Vendedores usamos Room (Offline-First)
                // Para Admins, el filtrado reactivo de Room también ayuda si ya tienen la data.
                if (!state.esVistaGlobal && !almacenActual.isNullOrEmpty()) {
                    val predicate: (ProductoEntity) -> Boolean = { entidad ->
                        if (perfilActivo == null || perfilActivo.id == "ALL") true
                        else {
                            perfilActivo.filtros.any { filtro ->
                                val marcaMatch = entidad.marca.trim().equals(filtro.marca.trim(), ignoreCase = true)
                                val categoriaMatch = if (filtro.categorias.isNotEmpty()) {
                                    filtro.categorias.any { it.trim().equals(entidad.categoria.trim(), ignoreCase = true) }
                                } else true
                                marcaMatch && categoriaMatch
                            }
                        }
                    }

                    // 1. Filtrar Productos Buenos
                    val modelos = entities
                        .filter { it.cantidadDisponible > 0 && it.id.contains(almacenActual) }
                        .filter(predicate)
                        .map { entity ->
                            Plantilla_Producto(
                                id = entity.id,
                                nombre = entity.nombre,
                                precio = entity.precio,
                                cantidad = entity.cantidadDisponible,
                                imagenUrl = entity.imagenUrl ?: "",
                                marca = entity.marca,
                                categoria = entity.categoria
                            )
                        }
                        .sortedByDescending { it.cantidad * it.precio }
                    
                    // Solo actualizamos si no somos Admin o si el listener de Firebase no ha mandado algo más fresco
                    if (!state.isAdmin) {
                        _uiState.update { it.copy(productos = modelos) }
                    }
                    
                    // Disparar recarga de Dañados (se filtrarán en cargarStockDanado)
                    cargarStockDanado(almacenActual)
                }
            }
            .launchIn(viewModelScope)
    }

    fun seleccionarPerfil(perfil: com.gruposanangel.delivery.data.PerfilVenta) {
        _uiState.update { it.copy(perfilSeleccionado = perfil) }
    }

    private fun observarMovimientosLocales(almacen: String, vendedorId: String) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val inicioHoy = cal.timeInMillis

        // Observamos cualquier cambio en los movimientos de hoy para actualizar la pestaña de devoluciones
        inventarioRepo.obtenerMovimientosDesdeFlow(vendedorId, inicioHoy)
            .onEach { movimientos ->
                val tieneDevoluciones = movimientos.any { it.almacenNombre == almacen && (it.tipo == "ENTRADA_MALO_DEVOLUCION" || it.tipo == "DEVOLUCION_DANIADO") }
                if (tieneDevoluciones) {
                    cargarStockDanado(almacen)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun cargarListaAlmacenes() {
        viewModelScope.launch {
            var lista = inventarioRepo.obtenerListaAlmacenes()
            // 🛡️ Filtro global: "Compra Producto" no es un almacén físico real para conteo, se elimina para todos
            lista = lista.filter { it != "Compra Producto" }
            _uiState.update { it.copy(listaAlmacenes = lista) }
        }
    }

    fun seleccionarAlmacen(almacen: String) {
        selectedWarehouseListener?.remove() // Limpiar listener previo
        _uiState.update { it.copy(isLoading = true, almacenSeleccionado = almacen, esVistaGlobal = false) }

        // 🔥 ESCUCHA EN TIEMPO REAL DEL ALMACÉN SELECCIONADO
        selectedWarehouseListener = db.collection("inventarioStock")
            .whereEqualTo("almacenNombre", almacen)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                    return@addSnapshotListener
                }

                viewModelScope.launch {
                    try {
                        val catalogo = inventarioRepo.obtenerProductosLocal().first()
                        val stockMap = snapshot?.documents?.associate { 
                            (it.getString("productoId") ?: it.id.split("_")[0]) to (it.getLong("cantidad")?.toInt() ?: 0)
                        } ?: emptyMap()

                        val productosStock = stockMap.mapNotNull { (prodId, cant) ->
                            if (cant <= 0) return@mapNotNull null
                            val info = catalogo.find { it.productoId == prodId } ?: return@mapNotNull null
                            
                            // 🔥 FILTRADO POR PERFIL TAMBIÉN PARA ADMIN (FIREBASE DATA)
                            val perfil = _uiState.value.perfilSeleccionado
                            val cumplePerfil = if (perfil == null || perfil.id == "ALL") true
                            else {
                                perfil.filtros.any { filtro ->
                                    val marcaMatch = info.marca.trim().equals(filtro.marca.trim(), ignoreCase = true)
                                    val categoriaMatch = if (filtro.categorias.isNotEmpty()) {
                                        filtro.categorias.any { it.trim().equals(info.categoria.trim(), ignoreCase = true) }
                                    } else true
                                    marcaMatch && categoriaMatch
                                }
                            }

                            if (!cumplePerfil) return@mapNotNull null

                            Plantilla_Producto(
                                id = prodId, 
                                nombre = info.nombre, 
                                precio = info.precio, 
                                cantidad = cant, 
                                cantidadDisponible = 0, 
                                imagenUrl = info.imagenUrl ?: "",
                                marca = info.marca,
                                categoria = info.categoria
                            )
                        }.sortedByDescending { it.cantidad * it.precio }

                        // Cargar también dañados (aunque no sean en tiempo real por ahora para no saturar)
                        val danado = inventarioRepo.obtenerStockDanado(almacen)
                        val productosDanados = danado.mapNotNull { (prodId, cant) ->
                            if (cant <= 0) return@mapNotNull null
                            val info = catalogo.find { it.productoId == prodId } ?: return@mapNotNull null
                            Plantilla_Producto(prodId, info.nombre, info.precio, cant, 0, info.imagenUrl ?: "")
                        }.sortedByDescending { it.cantidad * it.precio }

                        _uiState.update { it.copy(
                            productos = productosStock,
                            productosDanados = productosDanados,
                            isLoading = false
                        ) }
                    } catch (e: Exception) {
                        Log.e("InventarioVM", "Error procesando snapshot", e)
                    }
                }
            }
        
        // 🔥 TAMBIÉN ESCUCHAR NOTIFICACIONES PARA ESTE ALMACÉN (Para que admins vean cargas pendientes de otros)
        escucharNotificaciones(almacen)
    }

    fun activarVistaGlobal() {
        _uiState.update { it.copy(isLoading = true, esVistaGlobal = true, almacenSeleccionado = "VISTA GLOBAL") }
        viewModelScope.launch {
            try {
                val globalStock = inventarioRepo.obtenerStockGlobal()
                val catalogo = inventarioRepo.obtenerProductosLocal().first()
                
                val productos = globalStock.mapNotNull { (prodId, cant) ->
                    if (cant <= 0) return@mapNotNull null
                    val info = catalogo.find { it.productoId == prodId } ?: return@mapNotNull null
                    
                    Plantilla_Producto(
                        id = prodId, 
                        nombre = info.nombre, 
                        precio = info.precio, 
                        cantidad = cant, 
                        cantidadDisponible = 0, 
                        imagenUrl = info.imagenUrl ?: "",
                        marca = info.marca,
                        categoria = info.categoria
                    )
                }.sortedByDescending { it.cantidad * it.precio }

                _uiState.update { it.copy(productos = productos, productosDanados = emptyList(), isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun cargarStockDanado(almacen: String) {
        viewModelScope.launch {
            try {
                val danado = inventarioRepo.obtenerStockDanado(almacen)
                val catalogo = inventarioRepo.obtenerProductosLocal().first()
                val perfil = _uiState.value.perfilSeleccionado

                val lista = danado.mapNotNull { (prodId, cant) ->
                    if (cant <= 0) return@mapNotNull null
                    val info = catalogo.find { it.productoId == prodId } ?: return@mapNotNull null
                    
                    // 🔥 FILTRO DE PERFIL TAMBIÉN PARA DAÑADOS
                    val cumplePerfil = if (perfil == null || perfil.id == "ALL" || _uiState.value.isAdmin && !_uiState.value.almacenSeleccionado?.startsWith("Vendedor").let { it == true }) true
                    else {
                        perfil.filtros.any { filtro ->
                            val marcaMatch = info.marca.trim().equals(filtro.marca.trim(), ignoreCase = true)
                            val categoriaMatch = if (filtro.categorias.isNotEmpty()) {
                                filtro.categorias.any { it.trim().equals(info.categoria.trim(), ignoreCase = true) }
                            } else true
                            marcaMatch && categoriaMatch
                        }
                    }

                    if (!cumplePerfil) return@mapNotNull null

                    Plantilla_Producto(
                        id = prodId, 
                        nombre = info.nombre, 
                        precio = info.precio, 
                        cantidad = cant, 
                        cantidadDisponible = 0, 
                        imagenUrl = info.imagenUrl ?: "",
                        marca = info.marca,
                        categoria = info.categoria
                    )
                }.sortedByDescending { it.cantidad * it.precio }
                _uiState.update { it.copy(productosDanados = lista) }
            } catch (e: Exception) { }
        }
    }

    private fun escucharNotificaciones(nombreAlmacen: String) {
        notificationsListener?.remove()
        notificationsListener = db.collection("ordenesTransferencia")
            .whereEqualTo("destino", nombreAlmacen)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                
                val nuevas = snapshots?.documents?.mapNotNull { doc ->
                    if (doc.getString("estado") == "PENDIENTE") {
                        val fecha = doc.getTimestamp("timestamp")?.toDate()
                        Notificacion(
                            id = doc.id,
                            titulo = "Carga de Almacén",
                            mensaje = "Nueva carga pendiente",
                            fecha = fecha?.let { formatoFecha.format(it) } ?: "",
                            esCarga = true,
                            aceptada = false
                        )
                    } else null
                } ?: emptyList()
                
                _uiState.update { it.copy(notificaciones = nuevas) }
            }
    }

    override fun onCleared() {
        super.onCleared()
        notificationsListener?.remove()
    }
}

class InventarioViewModelFactory(
    private val inventarioRepo: RepositoryInventario,
    private val usuarioRepo: RepositoryUsuario
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return InventarioViewModel(inventarioRepo, usuarioRepo) as T
    }
}
