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
import kotlinx.coroutines.tasks.await
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
    private var selectedWarehouseListener: ListenerRegistration? = null 
    
    private val formatoFecha = SimpleDateFormat(
        "EEEE, dd 'de' MMMM 'de' yyyy, hh:mm a", 
        Locale("es", "MX")
    )

    private var lastSnapshot: com.google.firebase.firestore.QuerySnapshot? = null

    init {
        usuarioRepo.getUsuarioActual()
            .onEach { usuario ->
                if (usuario != null) {
                    val p = usuario.puestoTrabajo?.trim() ?: ""
                    val adminRoles = listOf("CEO", "GERENTE GENERAL", "ENCARGADO ALMACEN", "AUXILIAR DE ALMACEN", "SUPERVISOR")
                    val esAdmin = p.uppercase() in adminRoles
                    val esAlmacenRol = p.uppercase().contains("ALMACEN") || p.uppercase().contains("BODEGA")
                    val esDirectivo = p.uppercase() == "CEO" || p.uppercase() == "GERENTE GENERAL"
                    
                    val nombreAlmacen = usuario.ultimoAlmacenNombre
                    
                    val perfiles = mutableListOf<com.gruposanangel.delivery.data.PerfilVenta>()
                    perfiles.add(com.gruposanangel.delivery.data.PerfilVenta("ALL", "TODO", emptyList()))
                    
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
                                    val cats = if (catsArr != null) (0 until catsArr.length()).map { catsArr.getString(it) } else emptyList()
                                    filtros.add(com.gruposanangel.delivery.data.FiltroPerfil(fObj.getString("marca"), cats))
                                }
                                perfiles.add(com.gruposanangel.delivery.data.PerfilVenta(obj.getString("id"), obj.getString("nombre"), filtros))
                            }
                        }
                    } catch (e: Exception) { Log.e("InventarioVM", "Error parseando perfiles", e) }

                    val perfilActualizado = if (_uiState.value.perfilSeleccionado != null) {
                        perfiles.find { it.id == _uiState.value.perfilSeleccionado?.id } ?: perfiles.first()
                    } else {
                        perfiles.first()
                    }

                    _uiState.update { state ->
                        // 🛡️ REGLA DE ORO: BLOQUEO DE PERFILES PARA AUDITORÍA ADMIN
                        // Si el Admin está viendo una unidad externa, CONGELAMOS la interfaz para que no borre los perfiles del vendedor
                        val esAuditoriaExterna = esAdmin && !state.almacenSeleccionado.isNullOrBlank() && 
                                               state.almacenSeleccionado != "VISTA GLOBAL" && 
                                               state.almacenSeleccionado != nombreAlmacen

                        state.copy(
                            puestoTrabajo = usuario.puestoTrabajo,
                            rutaAsignada = nombreAlmacen,
                            isAdmin = esAdmin,
                            isLoading = if (state.almacenSeleccionado == null) true else state.isLoading,
                            perfilesDisponibles = if (esAuditoriaExterna) state.perfilesDisponibles else perfiles,
                            perfilSeleccionado = if (esAuditoriaExterna) state.perfilSeleccionado else perfilActualizado
                        )
                    }
                    
                    if (esAdmin) {
                        cargarListaAlmacenes()
                    }
                    
                    // 🔥 MEJORA DE PERSISTENCIA: Solo auto-seleccionar si no hay nada en memoria
                    if (_uiState.value.almacenSeleccionado == null) {
                        if (esDirectivo) {
                            activarVistaGlobal()
                        } else if (esAlmacenRol) {
                            seleccionarAlmacen(nombreAlmacen ?: "Almacen Huasteca")
                        } else if (!nombreAlmacen.isNullOrEmpty()) {
                            seleccionarAlmacen(nombreAlmacen)
                            escucharNotificaciones(nombreAlmacen)
                            cargarStockDanado(nombreAlmacen)
                            observarMovimientosLocales(nombreAlmacen, usuario.uid)
                        }
                    }
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                inventarioRepo.descargarProductosFirebase(uid)
            }
        }

        combine(
            inventarioRepo.obtenerProductosLocal(),
            _uiState.map { it.perfilSeleccionado }.distinctUntilChanged()
        ) { entities, perfilActivo ->
                val state = _uiState.value
                val almacenActual = state.almacenSeleccionado
                
                // 🛡️ Filtro local solo para NO admins o si es su propio almacen
                if (!state.esVistaGlobal && !almacenActual.isNullOrEmpty() && !state.isAdmin) {
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

                    val modelos = entities
                        .filter { it.id.contains(almacenActual) }
                        .filter(predicate)
                        .filter { 
                            // 🔥 MODIFICACIÓN: Ocultar ceros para vendedores (excepto Huasteca)
                            val esHuasteca = almacenActual == "Almacen Huasteca"
                            if (esHuasteca) true else it.cantidadDisponible > 0
                        }
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
                        .sortedWith(compareBy<Plantilla_Producto>({ it.categoria }, { it.nombre }))
                    
                    _uiState.update { it.copy(productos = modelos) }
                    cargarStockDanado(almacenActual)
                }
            }
            .launchIn(viewModelScope)
    }

    fun seleccionarPerfil(perfil: com.gruposanangel.delivery.data.PerfilVenta) {
        _uiState.update { it.copy(perfilSeleccionado = perfil) }
        
        // 🔄 Si es admin o auditoría, refrescar stock inmediatamente con el nuevo perfil
        if (_uiState.value.isAdmin) {
            val almacen = _uiState.value.almacenSeleccionado
            if (!almacen.isNullOrBlank() && almacen != "VISTA GLOBAL") {
                lastSnapshot?.let { procesarStockSnapshot(it, almacen) }
                cargarStockDanado(almacen)
            }
        }
    }

    private fun observarMovimientosLocales(almacen: String, vendedorId: String) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val inicioHoy = cal.timeInMillis

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
            lista = lista.filter { it != "Compra Producto" }
            _uiState.update { it.copy(listaAlmacenes = lista) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun sobreescribirAdmin(esAdmin: Boolean) {
        _uiState.update { it.copy(isAdmin = esAdmin) }
        if (esAdmin) {
            cargarListaAlmacenes()
        } else {
            // Si deja de ser admin (Modo Ruta), forzar su almacén actual
            viewModelScope.launch {
                val user = usuarioRepo.obtenerUsuarioActual()
                val alm = user?.ultimoAlmacenNombre ?: ""
                if (alm.isNotEmpty()) {
                    seleccionarAlmacen(alm)
                }
            }
        }
    }

    fun seleccionarAlmacen(almacen: String) {
        selectedWarehouseListener?.remove() 
        _uiState.update { it.copy(isLoading = true, almacenSeleccionado = almacen, esVistaGlobal = false) }

        if (_uiState.value.isAdmin) {
            viewModelScope.launch {
                try {
                    Log.d("InventarioVM", "Iniciando búsqueda de perfiles para auditoría en: $almacen")
                    val perfiles = mutableListOf<com.gruposanangel.delivery.data.PerfilVenta>()
                    perfiles.add(com.gruposanangel.delivery.data.PerfilVenta("ALL", "TODO", emptyList()))

                    val destinoLimpio = almacen.replace("Vendedor ", "").trim()
                    var userDoc: com.google.firebase.firestore.DocumentSnapshot? = null

                    // 1. Intentar por Rutas (como en FirebaseDataSource)
                    val rutasSnap = db.collection("rutas").get().await()
                    for (doc in rutasSnap.documents) {
                        val nom = doc.getString("nombre") ?: ""
                        val almRef = doc.getDocumentReference("almacenAsignado")
                        if (nom.contains(destinoLimpio, true) || (almRef != null && almRef.id.contains(destinoLimpio, true))) {
                            val vendRef = doc.getDocumentReference("vendedorAsignado")
                            if (vendRef != null) {
                                userDoc = vendRef.get().await()
                                break
                            }
                        }
                    }

                    // 2. Fallback: Buscar por nombre en usuarios
                    if (userDoc == null || !userDoc.exists()) {
                        val usersSnap = db.collection("users").whereEqualTo("activo", true).get().await()
                        userDoc = usersSnap.documents.find { 
                            (it.getString("nombre") ?: "").contains(destinoLimpio, true) ||
                            (it.getString("ultimoAlmacenNombre") ?: "").contains(almacen, true)
                        }
                    }

                    if (userDoc != null && userDoc.exists()) {
                        Log.d("InventarioVM", "Dueño encontrado para perfiles: ${userDoc.getString("nombre")}")
                        
                        val arrayRaw = userDoc.get("perfilesVenta") as? List<Map<String, Any>>
                        val json = userDoc.getString("perfilesVentaJson") 

                        if (!arrayRaw.isNullOrEmpty()) {
                            arrayRaw.forEach { pMap ->
                                val id = pMap["id"] as? String ?: UUID.randomUUID().toString()
                                val nom = pMap["nombre"] as? String ?: ""
                                val fRaw = pMap["filtros"] as? List<Map<String, Any>>
                                val filts = fRaw?.map { f ->
                                    val m = f["marca"] as? String ?: ""
                                    val c = (f["categorias"] as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
                                    com.gruposanangel.delivery.data.FiltroPerfil(m, c)
                                } ?: emptyList()
                                if (nom.isNotEmpty()) {
                                    if (perfiles.none { it.id == id }) {
                                        perfiles.add(com.gruposanangel.delivery.data.PerfilVenta(id, nom, filts))
                                    }
                                }
                            }
                        } else if (!json.isNullOrBlank()) {
                            try {
                                val array = org.json.JSONArray(json)
                                for (i in 0 until array.length()) {
                                    val obj = array.getJSONObject(i)
                                    val filtrosArr = obj.getJSONArray("filtros")
                                    val filtros = mutableListOf<com.gruposanangel.delivery.data.FiltroPerfil>()
                                    for (j in 0 until filtrosArr.length()) {
                                        val fObj = filtrosArr.getJSONObject(j)
                                        val catsArr = fObj.optJSONArray("categorias")
                                        val cats = if (catsArr != null) (0 until catsArr.length()).map { catsArr.getString(it) } else emptyList()
                                        filtros.add(com.gruposanangel.delivery.data.FiltroPerfil(fObj.getString("marca"), cats))
                                    }
                                    val id = obj.getString("id")
                                    if (perfiles.none { it.id == id }) {
                                        perfiles.add(com.gruposanangel.delivery.data.PerfilVenta(id, obj.getString("nombre"), filtros))
                                    }
                                }
                            } catch (e: Exception) { Log.e("InventarioVM", "Error JSON perfiles", e) }
                        }
                    }
                    
                    Log.d("InventarioVM", "Perfiles finales cargados para auditoría: ${perfiles.size}")
                    _uiState.update { it.copy(perfilesDisponibles = perfiles, perfilSeleccionado = perfiles.first()) }
                } catch (e: Exception) { Log.e("InventarioVM", "Error auditoría perfiles", e) }
            }
        }

        selectedWarehouseListener = db.collection("inventarioStock")
            .whereEqualTo("almacenNombre", almacen)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                    return@addSnapshotListener
                }
                
                lastSnapshot = snapshot
                procesarStockSnapshot(snapshot, almacen)
            }
        escucharNotificaciones(almacen)
    }

    private fun procesarStockSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot?, almacen: String) {
        viewModelScope.launch {
            try {
                val catalogSnap = db.collection("producto").get().await()
                val catalogo = catalogSnap.documents.associateBy { it.id }

                val stockMap = snapshot?.documents?.associate { 
                    (it.getString("productoId") ?: it.id.substringBeforeLast("_Vendedor").substringBeforeLast("_Almacen").substringBeforeLast("_")) to (it.getLong("cantidad")?.toInt() ?: 0)
                } ?: emptyMap()

                val productosStock = stockMap.mapNotNull { (prodId, cant) ->
                    val info = catalogo[prodId]
                    
                    val pMarca = info?.getString("marca") ?: "Delisa"
                    val pCat = info?.getString("categoria") ?: "General"
                    val pNom = info?.getString("nombre") ?: "Producto Desconocido ($prodId)"
                    val pPrec = (info?.get("precio") as? Number)?.toDouble() ?: 0.0
                    val pImg = info?.getString("imagenUrl") ?: info?.getString("fotoUrl") ?: ""
                    
                    val perfil = _uiState.value.perfilSeleccionado
                    
                    val cumplePerfil = if (perfil == null || perfil.id == "ALL") true
                    else {
                        perfil.filtros.any { filtro ->
                            val marcaMatch = pMarca.trim().equals(filtro.marca.trim(), ignoreCase = true)
                            val categoriaMatch = if (filtro.categorias.isNotEmpty()) {
                                filtro.categorias.any { it.trim().equals(pCat.trim(), ignoreCase = true) }
                            } else true
                            marcaMatch && categoriaMatch
                        }
                    }

                    if (!cumplePerfil) return@mapNotNull null

                    // 🔥 MODIFICACIÓN: Ocultar ceros para vendedores (excepto Huasteca)
                    val esHuasteca = almacen == "Almacen Huasteca"
                    if (!esHuasteca && cant <= 0) return@mapNotNull null

                    Plantilla_Producto(
                        id = prodId, 
                        nombre = pNom, 
                        precio = pPrec, 
                        cantidad = cant, 
                        cantidadDisponible = 0, 
                        imagenUrl = pImg,
                        marca = pMarca,
                        categoria = pCat
                    )
                }.sortedWith(compareBy<Plantilla_Producto>({ it.categoria }, { it.nombre }))

                _uiState.update { it.copy(productos = productosStock, isLoading = false) }
                // Solo cargar dañados si el snapshot vino del almacén actual
                if (_uiState.value.almacenSeleccionado == almacen) {
                    cargarStockDanado(almacen)
                }
            } catch (e: Exception) { Log.e("InventarioVM", "Error procesando stock snapshot", e) }
        }
    }

    fun activarVistaGlobal() {
        val todoPerfil = com.gruposanangel.delivery.data.PerfilVenta("ALL", "TODO", emptyList())
        _uiState.update { it.copy(
            isLoading = true, 
            esVistaGlobal = true, 
            almacenSeleccionado = "VISTA GLOBAL",
            perfilesDisponibles = listOf(todoPerfil),
            perfilSeleccionado = todoPerfil
        ) }
        viewModelScope.launch {
            try {
                val globalStock = inventarioRepo.obtenerStockGlobal()
                val catalogSnap = db.collection("producto").get().await()
                val catalogo = catalogSnap.documents.associateBy { it.id }
                
                val productos = globalStock.mapNotNull { (prodId, cant) ->
                    val info = catalogo[prodId]
                    
                    Plantilla_Producto(
                        id = prodId, 
                        nombre = info?.getString("nombre") ?: "Producto Desconocido ($prodId)", 
                        precio = (info?.get("precio") as? Number)?.toDouble() ?: 0.0, 
                        cantidad = cant, 
                        cantidadDisponible = 0, 
                        imagenUrl = info?.getString("imagenUrl") ?: info?.getString("fotoUrl") ?: "",
                        marca = info?.getString("marca") ?: "Delisa",
                        categoria = info?.getString("categoria") ?: "General"
                    )
                }.sortedWith(compareBy<Plantilla_Producto>({ it.categoria }, { it.nombre }))

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
                val catalogSnap = db.collection("producto").get().await()
                val catalogo = catalogSnap.documents.associateBy { it.id }
                val perfil = _uiState.value.perfilSeleccionado

                val lista = danado.mapNotNull { (prodId, cant) ->
                    val info = catalogo[prodId]
                    
                    val pMarca = info?.getString("marca") ?: "Delisa"
                    val pCat = info?.getString("categoria") ?: "General"
                    val pNom = info?.getString("nombre") ?: "Producto Desconocido ($prodId)"
                    val pPrec = (info?.get("precio") as? Number)?.toDouble() ?: 0.0
                    val pImg = info?.getString("imagenUrl") ?: info?.getString("fotoUrl") ?: ""

                    val cumplePerfil = if (perfil == null || perfil.id == "ALL") true
                    else {
                        perfil.filtros.any { filtro ->
                            val marcaMatch = pMarca.trim().equals(filtro.marca.trim(), ignoreCase = true)
                            val categoriaMatch = if (filtro.categorias.isNotEmpty()) {
                                filtro.categorias.any { it.trim().equals(pCat.trim(), ignoreCase = true) }
                            } else true
                            marcaMatch && categoriaMatch
                        }
                    }

                    if (!cumplePerfil) return@mapNotNull null

                    // 🔥 MODIFICACIÓN: Ocultar ceros para vendedores (excepto Huasteca)
                    val esHuasteca = almacen == "Almacen Huasteca"
                    if (!esHuasteca && cant <= 0) return@mapNotNull null

                    Plantilla_Producto(
                        id = prodId, 
                        nombre = pNom, 
                        precio = pPrec, 
                        cantidad = cant, 
                        cantidadDisponible = 0, 
                        imagenUrl = pImg,
                        marca = pMarca,
                        categoria = pCat
                    )
                }.sortedWith(compareBy<Plantilla_Producto>({ it.categoria }, { it.nombre }))
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
        selectedWarehouseListener?.remove()
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
