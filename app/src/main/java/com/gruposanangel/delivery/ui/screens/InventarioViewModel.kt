package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    val esVistaGlobal: Boolean = false
)

class InventarioViewModel(
    private val inventarioRepo: RepositoryInventario,
    private val usuarioRepo: RepositoryUsuario
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventarioUiState())
    val uiState: StateFlow<InventarioUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private var notificationsListener: ListenerRegistration? = null
    
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
                    
                    val nombreAlmacen = usuario.ultimoAlmacenNombre
                    val almacenInicial = if (esAlmacenRol) "Almacen Huasteca" else nombreAlmacen
                    
                    _uiState.update { it.copy(
                        puestoTrabajo = usuario.puestoTrabajo,
                        rutaAsignada = nombreAlmacen,
                        almacenSeleccionado = almacenInicial,
                        isAdmin = esAdmin,
                        isLoading = false
                    ) }
                    
                    if (esAdmin) {
                        cargarListaAlmacenes()
                    }
                    
                    if (esAlmacenRol) {
                        seleccionarAlmacen("Almacen Huasteca")
                    } else if (!nombreAlmacen.isNullOrEmpty()) {
                        escucharNotificaciones(nombreAlmacen)
                        cargarStockDanado(nombreAlmacen)
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
        inventarioRepo.obtenerProductosLocal()
            .onEach { entities ->
                val almacenActual = _uiState.value.almacenSeleccionado
                if (!_uiState.value.esVistaGlobal && !almacenActual.isNullOrEmpty()) {
                    val modelos = entities
                        .filter { it.cantidadDisponible > 0 && it.id.contains(almacenActual) }
                        .map { entity ->
                            Plantilla_Producto(
                                id = entity.id,
                                nombre = entity.nombre,
                                precio = entity.precio,
                                cantidad = entity.cantidadDisponible,
                                imagenUrl = entity.imagenUrl ?: ""
                            )
                        }
                        .sortedByDescending { it.precio }
                    _uiState.update { it.copy(productos = modelos) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun cargarListaAlmacenes() {
        viewModelScope.launch {
            var lista = inventarioRepo.obtenerListaAlmacenes()
            val p = _uiState.value.puestoTrabajo?.trim() ?: ""
            if (p == "Encargado Almacen" || p == "Auxiliar de almacen") {
                lista = lista.filter { it != "Compra Producto" }
            }
            _uiState.update { it.copy(listaAlmacenes = lista) }
        }
    }

    fun seleccionarAlmacen(almacen: String) {
        _uiState.update { it.copy(isLoading = true, almacenSeleccionado = almacen, esVistaGlobal = false) }
        viewModelScope.launch {
            try {
                val stock = inventarioRepo.obtenerStockAlmacen(almacen)
                val danado = inventarioRepo.obtenerStockDanado(almacen)
                
                // Mapear stock a Plantilla_Producto
                val catalogo = inventarioRepo.obtenerProductosLocal().first()
                val productosStock = stock.mapNotNull { (prodId, cant) ->
                    if (cant <= 0) return@mapNotNull null
                    val info = catalogo.find { it.productoId == prodId } ?: return@mapNotNull null
                    Plantilla_Producto(prodId, info.nombre, info.precio, cant, 0, info.imagenUrl ?: "")
                }.sortedByDescending { it.precio }
                
                val productosDanados = danado.mapNotNull { (prodId, cant) ->
                    if (cant <= 0) return@mapNotNull null
                    val info = catalogo.find { it.productoId == prodId } ?: return@mapNotNull null
                    Plantilla_Producto(prodId, info.nombre, info.precio, cant, 0, info.imagenUrl ?: "")
                }.sortedByDescending { it.precio }

                _uiState.update { it.copy(
                    productos = productosStock,
                    productosDanados = productosDanados,
                    isLoading = false
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
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
                    Plantilla_Producto(prodId, info.nombre, info.precio, cant, 0, info.imagenUrl ?: "")
                }.sortedByDescending { it.precio }

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
                val lista = danado.mapNotNull { (prodId, cant) ->
                    if (cant <= 0) return@mapNotNull null
                    val info = catalogo.find { it.productoId == prodId } ?: return@mapNotNull null
                    Plantilla_Producto(prodId, info.nombre, info.precio, cant, 0, info.imagenUrl ?: "")
                }.sortedByDescending { it.precio }
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
