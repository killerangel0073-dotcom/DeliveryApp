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
                    
                    _uiState.update { it.copy(
                        puestoTrabajo = usuario.puestoTrabajo,
                        rutaAsignada = nombreAlmacen,
                        isAdmin = esAdmin,
                        isLoading = false
                    ) }
                    
                    if (esAdmin) {
                        cargarListaAlmacenes()
                    }
                    
                    // 🔥 PRESELECCIÓN POR ROL
                    if (esDirectivo) {
                        // CEO/Gerente: Vista Global (El mundo) por defecto
                        activarVistaGlobal()
                    } else if (esAlmacenRol) {
                        // Almacenistas: Almacen Huasteca por defecto
                        seleccionarAlmacen("Almacen Huasteca")
                    } else if (!nombreAlmacen.isNullOrEmpty()) {
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
        inventarioRepo.obtenerProductosLocal()
            .onEach { entities ->
                val state = _uiState.value
                val almacenActual = state.almacenSeleccionado
                
                // 🛡️ REGLA DE VISIBILIDAD: Solo sobreescribir con Room si no somos Admin 
                // o si estamos viendo una vista que depende de datos locales (como la del vendedor).
                // Para Admins/CEO viendo almacenes ajenos, mandan los listeners de Firebase.
                if (!state.esVistaGlobal && !almacenActual.isNullOrEmpty() && !state.isAdmin) {
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
                        .sortedByDescending { it.cantidad * it.precio }
                    _uiState.update { it.copy(productos = modelos) }
                }
            }
            .launchIn(viewModelScope)
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
                            Plantilla_Producto(prodId, info.nombre, info.precio, cant, 0, info.imagenUrl ?: "")
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
                val lista = danado.mapNotNull { (prodId, cant) ->
                    if (cant <= 0) return@mapNotNull null
                    val info = catalogo.find { it.productoId == prodId } ?: return@mapNotNull null
                    Plantilla_Producto(prodId, info.nombre, info.precio, cant, 0, info.imagenUrl ?: "")
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
