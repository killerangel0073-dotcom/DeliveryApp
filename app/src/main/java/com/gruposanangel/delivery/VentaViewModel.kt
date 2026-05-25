package com.gruposanangel.delivery.ui.screens

import ProductoTicketDetalle
import TicketVentaCompleto
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaDetalleEntity
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.data.VentaRepository
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

data class VentaUiState(
    val productosEnCarrito: List<Plantilla_Producto> = emptyList(),
    val estaProcesando: Boolean = false,
    val totalVenta: Double = 0.0,
    val estadoRuta: EstadoRuta = EstadoRuta.Cargando
)

sealed class EstadoRuta {
    object Cargando : EstadoRuta()
    object SinRuta : EstadoRuta()
    data class Error(val mensaje: String) : EstadoRuta()
    data class ConRuta(val nombreAlmacen: String, val almacenId: String) : EstadoRuta()
}

/**
 * VentaViewModel UNIFICADO
 * Maneja el flujo de ventas, control de stock, historial y reportes.
 */
class VentaViewModel(
    private val repositoryInventario: RepositoryInventario,
    private val ventaRepository: VentaRepository,
    private val repositoryUsuario: RepositoryUsuario
) : ViewModel() {

    // --- Estados ---

    private val _uiState = MutableStateFlow(VentaUiState())
    val uiState: StateFlow<VentaUiState> = _uiState.asStateFlow()

    private val _ventasPeriodo = MutableStateFlow<List<VentaEntity>>(emptyList())
    val ventasPeriodo: StateFlow<List<VentaEntity>> = _ventasPeriodo.asStateFlow()

    // Alias para mantener compatibilidad con pantallas que usaban ventasHoy
    val ventasHoy: StateFlow<List<VentaEntity>> = _ventasPeriodo

    // --- Inicialización y Carga de Datos ---

    fun cargarProductosIniciales(listaBase: List<Plantilla_Producto>) {
        if (_uiState.value.productosEnCarrito.isEmpty()) {
            val carritoInicial = listaBase.map { it.copy(cantidad = 0) }
            _uiState.update { it.copy(productosEnCarrito = carritoInicial) }
        }
    }

    fun cargarVentasHoy() {
        val tz = TimeZone.getDefault()
        val calendar = Calendar.getInstance(tz)
        
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
                val ventas = ventaRepository.obtenerVentasPorPeriodo(fechaInicio.time, fechaFin.time)
                _ventasPeriodo.value = ventas
            } catch (e: Exception) {
                Log.e("VentaViewModel", "Error cargando ventas por periodo", e)
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
                val nuevoEstado = if (usuario?.ultimaRutaId != null && usuario.ultimoAlmacenId != null) {
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

    // --- Lógica del Carrito y Ventas ---

    fun actualizarCantidad(index: Int, nuevaCantidad: Int) {
        _uiState.update { state ->
            val lista = state.productosEnCarrito.toMutableList()
            if (index in lista.indices) {
                val producto = lista[index]
                val cantidadDisponibleSegura = producto.cantidadDisponible.coerceAtLeast(0)
                val cantidadSegura = nuevaCantidad.coerceIn(0, cantidadDisponibleSegura)
                
                lista[index] = producto.copy(cantidad = cantidadSegura)
                
                val nuevoTotal = lista.sumOf { 
                    val p = it.precio.coerceAtLeast(0.0)
                    val c = it.cantidad.coerceAtLeast(0)
                    p * c
                }
                state.copy(productosEnCarrito = lista, totalVenta = nuevoTotal)
            } else {
                state
            }
        }
    }

    fun procesarVenta(
        clienteId: String,
        clienteNombre: String,
        clienteFotoUrl: String?,
        metodoPago: String,
        hayInternet: Boolean,
        onResultado: (Boolean, String, String) -> Unit
    ) {
        if (_uiState.value.estaProcesando) return

        _uiState.update { it.copy(estaProcesando = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 🔥 CORRECCIÓN: Obtener el UID de forma segura y 100% offline desde Room
                val usuarioActual = repositoryUsuario.obtenerUsuarioActual()
                val uidVendedor = usuarioActual?.uid ?: FirebaseAuth.getInstance().currentUser?.uid ?: ""

                if (uidVendedor.isEmpty()) {
                    throw Exception("No se pudo identificar al vendedor. Inicie sesión nuevamente.")
                }

                val productosVenta = _uiState.value.productosEnCarrito.filter { it.cantidad > 0 }

                if (productosVenta.isEmpty()) throw Exception("El carrito está vacío")

                val totalVenta = productosVenta.sumOf { 
                    val p = it.precio.coerceAtLeast(0.0)
                    val c = it.cantidad.coerceAtLeast(0)
                    p * c
                }

                // 1. Guardar Local
                val ventaLocalId = ventaRepository.guardarVentaLocal(
                    clienteId = clienteId,
                    clienteNombre = clienteNombre,
                    clienteImagenUrl = clienteFotoUrl,
                    productos = productosVenta,
                    total = totalVenta,
                    metodoPago = metodoPago,
                    vendedorId = uidVendedor
                )

                // 2. Descontar Stock Local (Inmediato)
                productosVenta.forEach {
                    repositoryInventario.actualizarCantidadProducto(it.id, it.cantidad)
                }

                // 3. Sincronización Remota
                var mensajeFinal = "Venta guardada localmente"
                val estadoRutaActual = _uiState.value.estadoRuta

                if (hayInternet && estadoRutaActual is EstadoRuta.ConRuta) {
                    val (exito, response) = ventaRepository.sincronizarConServidor(
                        ventaLocalId = ventaLocalId,
                        clienteId = clienteId,
                        clienteNombre = clienteNombre,
                        productos = productosVenta,
                        metodoPago = metodoPago,
                        vendedorId = uidVendedor,
                        almacenVendedorId = estadoRutaActual.almacenId
                    )

                    if (exito) {
                        val firestoreId = try {
                            val json = JSONObject(response)
                            if (json.has("ventaId")) json.getString("ventaId") else null
                        } catch (e: Exception) {
                            null
                        }
                        if (firestoreId != null) {
                            ventaRepository.marcarVentaConFirestoreId(ventaLocalId, firestoreId)
                            mensajeFinal = "Venta sincronizada correctamente"
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    enviarNotificacionSegundoPlano(clienteNombre, totalVenta, clienteFotoUrl, ventaLocalId)
                    limpiarCarrito()
                    _uiState.update { it.copy(estaProcesando = false) }
                    onResultado(true, mensajeFinal, ventaLocalId)
                }

            } catch (e: Exception) {
                Log.e("VentaViewModel", "Error procesando venta", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(estaProcesando = false) }
                    onResultado(false, e.message ?: "Error desconocido", "")
                }
            }
        }
    }

    private fun limpiarCarrito() {
        _uiState.update { state ->
            val carritoLimpio = state.productosEnCarrito.map { it.copy(cantidad = 0) }
            state.copy(productosEnCarrito = carritoLimpio, totalVenta = 0.0)
        }
    }

    // --- Consultas de Tickets ---

    suspend fun obtenerTicketCompleto(ticketId: String): TicketVentaCompleto? {
        return withContext(Dispatchers.IO) {
            ventaRepository.obtenerTicketCompleto(ticketId)
        }
    }

    suspend fun obtenerDetallesDeVenta(ventaId: String): List<VentaDetalleEntity> {
        return withContext(Dispatchers.IO) {
            ventaRepository.obtenerDetallesDeVenta(ventaId)
        }
    }

    suspend fun obtenerVentaPorId(ventaId: String): VentaEntity? {
        return withContext(Dispatchers.IO) {
            ventaRepository.obtenerVentaPorId(ventaId)
        }
    }

    // --- Notificaciones ---

    private fun enviarNotificacionSegundoPlano(cliente: String, total: Double, foto: String?, localId: String) {
        viewModelScope.launch {
            try {
                val token = obtenerTokenSupervisor() ?: return@launch
                val vendedor = obtenerNombreVendedor()
                val ruta = obtenerNombreRuta()
                val firestoreId = ventaRepository.obtenerFirestoreIdDeVenta(localId) ?: localId

                enviarNotificacionVenta(
                    token = token,
                    vendedorNombre = vendedor,
                    rutaAsignada = ruta,
                    clienteNombre = cliente,
                    totalVenta = total,
                    clienteFotoUrl = foto,
                    ventaId = firestoreId
                )
            } catch (e: Exception) {
                Log.e("VentaViewModel", "Error en notificación", e)
            }
        }
    }

    private fun enviarNotificacionVenta(
        token: String, vendedorNombre: String, rutaAsignada: String,
        clienteNombre: String, totalVenta: Double, clienteFotoUrl: String?, ventaId: String
    ) {
        val client = OkHttpClient()
        val totalFormateado = "$${"%.2f".format(totalVenta)}"
        val mensaje = "📦 RUTA: $rutaAsignada\n👤 CLIENTE: $clienteNombre\n💰 TOTAL: $totalFormateado"

        val json = JSONObject().apply {
            put("token", token)
            put("titulo", "Nueva venta registrada")
            put("mensaje", mensaje)
            put("imagen", clienteFotoUrl ?: "https://upload.wikimedia.org/wikipedia/commons/thumb/7/74/Dominos_pizza_logo.svg/768px-Dominos_pizza_logo.svg.png")
            put("estilo", "bigpicture")
            put("ventaId", ventaId)
            put("click_action", "OPEN_TICKET_DETAIL")
        }

        val request = Request.Builder()
            .url("https://us-central1-appventas--san-angel.cloudfunctions.net/enviarNotificacion")
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
        })
    }

    private suspend fun obtenerNombreRuta(): String {
        return try {
            val usuario = repositoryUsuario.obtenerUsuarioActual()
            usuario?.ultimaRutaNombre ?: "Sin ruta"
        } catch (e: Exception) { "Sin ruta" }
    }

    private suspend fun obtenerNombreVendedor(): String {
        return try {
            val usuario = repositoryUsuario.obtenerUsuarioActual()
            usuario?.nombre ?: "Vendedor"
        } catch (e: Exception) { "Vendedor" }
    }

    private suspend fun obtenerTokenSupervisor(): String? {
        return try {
            repositoryUsuario.obtenerTokenSupervisor()
        } catch (e: Exception) { null }
    }
}

/**
 * Factory Unificado para VentaViewModel
 */
class VentaViewModelFactory(
    private val repositoryInventario: RepositoryInventario,
    private val ventaRepository: VentaRepository,
    private val repositoryUsuario: RepositoryUsuario
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VentaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VentaViewModel(repositoryInventario, ventaRepository, repositoryUsuario) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
