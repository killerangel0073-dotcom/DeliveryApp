package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.ProductoDao
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaDao
import com.gruposanangel.delivery.data.VentaDetalleEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

data class ArqueoUiState(
    val isLoading: Boolean = false,
    val totalVentasSemana: Double = 0.0,
    val comision: Double = 0.0,
    val ingresoTotal: Double = 0.0,
    val ticketPromedio: Double = 0.0,
    val clientesVisitados: Int = 0,
    val stockInicial: Int = 0,
    val totalCargasSemana: Int = 0,
    val totalVentasUnidades: Int = 0,
    val totalDevoluciones: Int = 0,
    val saldoTeoricoCalculado: Int = 0,
    val valorStockInicial: Double = 0.0,
    val valorCargasSemana: Double = 0.0,
    val valorVentasSemana: Double = 0.0,
    val valorDevolucionesSemana: Double = 0.0,
    val saldoValorTeoricoCalculado: Double = 0.0,
    val productosArqueo: List<ProductoArqueo> = emptyList(),
    val error: String? = null,
    val errorAutorizacion: String? = null,
    val reporteGuardado: Boolean = false,
    val autorizadoPor: String? = null,
    val nombreVendedor: String = "",
    val fechaUltimoArqueo: Long = 0L
)

data class ProductoArqueo(
    val id: String,
    val nombre: String,
    val precio: Double,
    val stockTeorico: Int,
    val stockInicialBitacora: Int = 0,
    val cargasPorDia: List<Int> = listOf(0, 0, 0, 0, 0, 0, 0), // L, M, M, J, V, S, D
    var stockReal: String = "",
    val imagenUrl: String = ""
) {
    val totalCargas: Int get() = cargasPorDia.sum()
    val diferencia: Int get() = (stockReal.toIntOrNull() ?: 0) - stockTeorico
    val valorDiferencia: Double get() = diferencia * precio
    
    // Diferencia solicitada: Inicial + Cargas - Arqueo
    val difBitacora: Int get() = stockInicialBitacora + totalCargas - (stockReal.toIntOrNull() ?: 0)
}

class ArqueoViewModel(
    private val ventaDao: VentaDao,
    private val productoDao: ProductoDao,
    private val inventarioRepo: RepositoryInventario,
    private val usuarioRepo: RepositoryUsuario,
    private val ventaRepo: VentaRepository,
    private val vendedorId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArqueoUiState())
    val uiState: StateFlow<ArqueoUiState> = _uiState.asStateFlow()

    private val stockInicialRealMap = mutableMapOf<String, Int>()
    private var fechaUltimoArqueoTS = 0L

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            buscarUltimoArqueo()
            cargarDatosResumen()
            observarProductosArqueo()
        }
    }

    private suspend fun buscarUltimoArqueo() {
        try {
            val dbFirestore = FirebaseFirestore.getInstance()
            val query = dbFirestore.collection("reportes_arqueo")
                .whereEqualTo("vendedorId", vendedorId)
                .orderBy("fecha", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            if (!query.isEmpty) {
                val doc = query.documents.first()
                fechaUltimoArqueoTS = doc.getTimestamp("fecha")?.toDate()?.time ?: 0L
                val detalle = doc.get("detalle") as? List<Map<String, Any>>
                detalle?.forEach { item ->
                    val id = item["id"] as? String
                    val real = (item["r"] as? Number)?.toInt() ?: 0
                    if (id != null) stockInicialRealMap[id] = real
                }
            }
            _uiState.update { it.copy(fechaUltimoArqueo = fechaUltimoArqueoTS) }
        } catch (e: Exception) {
            Log.e("ARQUEO", "Error buscando último arqueo: ${e.message}")
        }
    }

    private fun cargarDatosResumen() {
        viewModelScope.launch {
            try {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
                val inicioSemana = calendar.timeInMillis

                // El punto de partida real para esta auditoría es el último arqueo
                val startPoint = if (fechaUltimoArqueoTS > 0) fechaUltimoArqueoTS else inicioSemana

                // 1. Sincronizamos las ventas del servidor antes de mostrar el resumen
                ventaRepo.sincronizarVentasPeriodo(vendedorId, startPoint, System.currentTimeMillis())

                val ventas = ventaDao.obtenerVentasPorPeriodo(vendedorId, startPoint, System.currentTimeMillis())
                var totalVUnidades = 0; var valorVSemana = 0.0
                ventas.forEach { v ->
                    val detalles = ventaDao.obtenerDetallesPorVenta(v.id)
                    totalVUnidades += detalles.sumOf { it.cantidad }
                    valorVSemana += detalles.sumOf { it.cantidad * it.precio }
                }

                val movimientos = inventarioRepo.obtenerMovimientosDesde(vendedorId, startPoint)
                val totalCargasSemana = movimientos.filter { it.tipo == "CARGA_INVENTARIO" }.sumOf { it.cantidad }
                val totalDevoluciones = movimientos.filter { it.tipo == "ENTRADA_MALO_DEVOLUCION" }.sumOf { it.cantidad }

                // Entradas y Salidas que afectan el stock bueno
                val totalEntradasBueno = movimientos.filter { 
                    it.tipo == "CARGA_INVENTARIO" || it.tipo == "ENTRADA_CAMBIO_BUENO"
                }.sumOf { it.cantidad }
                
                val totalSalidasBueno = totalVUnidades + movimientos.filter { 
                    it.tipo == "SALIDA_CAMBIO_BUENO" || it.tipo == "SALIDA_REPOSICION_BUENO" 
                }.sumOf { it.cantidad }

                val invActual = productoDao.getAllProductosFlow().first().filter { it.id.contains("_") }
                val piezasActuales = invActual.sumOf { it.cantidadDisponible }
                val valorActual = invActual.sumOf { it.cantidadDisponible * it.precio }
                
                val usuarioActual = usuarioRepo.obtenerUsuarioActual()

                // El INICIAL es la suma del último arqueo real, o reconstrucción si no hay
                val stockInicialCalculado = if (stockInicialRealMap.isNotEmpty()) {
                    stockInicialRealMap.values.sum()
                } else {
                    piezasActuales - totalEntradasBueno + totalSalidasBueno
                }

                _uiState.update { it.copy(
                    totalVentasSemana = ventas.sumOf { it.total },
                    nombreVendedor = usuarioActual?.nombre ?: "Vendedor",
                    comision = ventas.sumOf { it.total } * 0.03,
                    ingresoTotal = 1800.0 + (ventas.sumOf { it.total } * 0.03),
                    ticketPromedio = if (ventas.isNotEmpty()) ventas.sumOf { it.total } / ventas.size else 0.0,
                    clientesVisitados = ventas.map { it.clienteId }.distinct().size,
                    stockInicial = stockInicialCalculado,
                    totalCargasSemana = totalCargasSemana,
                    totalVentasUnidades = totalVUnidades,
                    totalDevoluciones = totalDevoluciones,
                    saldoTeoricoCalculado = piezasActuales,
                    valorStockInicial = if (stockInicialRealMap.isNotEmpty()) {
                        invActual.sumOf { p -> (stockInicialRealMap[p.id] ?: 0) * p.precio }
                    } else {
                        valorActual + valorVSemana
                    },
                    valorVentasSemana = valorVSemana,
                    saldoValorTeoricoCalculado = valorActual
                ) }
            } catch (e: Exception) { _uiState.update { it.copy(error = e.message) } }
        }
    }

    private fun observarProductosArqueo() {
        productoDao.getAllProductosFlow().onEach { productos ->
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
            val inicioSemana = calendar.timeInMillis

            // Punto de partida para CARGAS: fecha del último arqueo
            val startPoint = if (fechaUltimoArqueoTS > 0) fechaUltimoArqueoTS else inicioSemana

            val movimientos = inventarioRepo.obtenerMovimientosDesde(vendedorId, inicioSemana)
            val ventas = ventaDao.obtenerVentasPorPeriodo(vendedorId, inicioSemana, System.currentTimeMillis())
            
            val detallesVentas = mutableListOf<VentaDetalleEntity>()
            ventas.forEach { v -> detallesVentas.addAll(ventaDao.obtenerDetallesPorVenta(v.id)) }

            val lista = productos.filter { it.id.contains("_") }.map { p ->
                val previo = _uiState.value.productosArqueo.find { it.id == p.id }?.stockReal ?: ""
                val baseId = p.productoId
                
                // Cargas Posteriores al último arqueo
                val movsCarga = movimientos.filter { m ->
                    m.productoId == baseId && 
                    m.tipo == "CARGA_INVENTARIO" && 
                    m.timestamp > startPoint
                }
                
                val cargasDia = MutableList(7) { 0 }
                movsCarga.forEach { m ->
                    val calMov = Calendar.getInstance().apply { timeInMillis = m.timestamp }
                    val index = when (calMov.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> 0
                        Calendar.TUESDAY -> 1
                        Calendar.WEDNESDAY -> 2
                        Calendar.THURSDAY -> 3
                        Calendar.FRIDAY -> 4
                        Calendar.SATURDAY -> 5
                        Calendar.SUNDAY -> 6
                        else -> -1
                    }
                    if (index != -1) cargasDia[index] += m.cantidad
                }

                // El INICIAL viene del último arqueo real (Checkpoint)
                val inicialDesdeArqueo = stockInicialRealMap[p.id]
                
                val inicial = if (inicialDesdeArqueo != null) {
                    inicialDesdeArqueo
                } else {
                    // Respaldo: Reconstrucción teórica desde inicio de semana
                    val totalEntradasBueno = movimientos.filter { 
                        it.productoId == baseId && (it.tipo == "CARGA_INVENTARIO" || it.tipo == "ENTRADA_CAMBIO_BUENO") 
                    }.sumOf { it.cantidad }
                    
                    val totalSalidasBueno = (detallesVentas.filter { it.productoId == baseId || it.stockId == p.id }.sumOf { it.cantidad }) +
                                           movimientos.filter { it.productoId == baseId && (it.tipo == "SALIDA_CAMBIO_BUENO" || it.tipo == "SALIDA_REPOSICION_BUENO") }.sumOf { it.cantidad }
                    
                    p.cantidadDisponible - totalEntradasBueno + totalSalidasBueno
                }

                ProductoArqueo(
                    id = p.id, 
                    nombre = p.nombre, 
                    precio = p.precio, 
                    stockTeorico = p.cantidadDisponible,
                    stockInicialBitacora = inicial,
                    cargasPorDia = cargasDia,
                    stockReal = previo, 
                    imagenUrl = p.imagenUrl ?: ""
                )
            }
            _uiState.update { it.copy(productosArqueo = lista, isLoading = false) }
        }.launchIn(viewModelScope)
    }

    fun actualizarStockReal(productoId: String, valor: String) {
        _uiState.update { state -> state.copy(productosArqueo = state.productosArqueo.map { if (it.id == productoId) it.copy(stockReal = valor) else it }) }
    }

    fun autorizarCierre(pass: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorAutorizacion = null) }
            try {
                val db = FirebaseFirestore.getInstance()
                val userQuery = db.collection("users")
                    .whereEqualTo("contraseña", pass.trim())
                    .whereEqualTo("activo", true)
                    .get()
                    .await()
                
                if (userQuery.isEmpty) {
                    _uiState.update { it.copy(isLoading = false, errorAutorizacion = "Contraseña incorrecta o usuario inactivo") }
                    return@launch
                }

                val autorizador = userQuery.documents.first().getString("nombre") ?: "Administrador"
                val usuarioVendedor = usuarioRepo.obtenerUsuarioActual()

                // 1. ACTUALIZAR STOCK REAL EN ROOM
                _uiState.value.productosArqueo.forEach { p ->
                    val cantFinal = p.stockReal.toIntOrNull() ?: p.stockTeorico
                    productoDao.updateCantidadDisponible(p.id, cantFinal)
                }

                // 2. GUARDAR REPORTE EN FIREBASE
                val arqueoData = mapOf(
                    "vendedorId" to vendedorId,
                    "vendedorNombre" to (usuarioVendedor?.nombre ?: "Vendedor"),
                    "almacen" to (usuarioVendedor?.ultimoAlmacenNombre ?: "Sin Almacen"),
                    "fecha" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "autorizadoPor" to autorizador,
                    "piezasTeoricas" to _uiState.value.saldoTeoricoCalculado,
                    "piezasReales" to _uiState.value.productosArqueo.sumOf { it.stockReal.toIntOrNull() ?: 0 },
                    "detalle" to _uiState.value.productosArqueo.map { mapOf("id" to it.id, "nombre" to it.nombre, "t" to it.stockTeorico, "r" to (it.stockReal.toIntOrNull() ?: 0)) }
                )

                db.collection("reportes_arqueo").add(arqueoData).await()
                _uiState.update { it.copy(isLoading = false, reporteGuardado = true, autorizadoPor = autorizador) }
                
            } catch (e: Exception) { 
                Log.e("ARQUEO", "Error en autorizarCierre", e)
                _uiState.update { it.copy(isLoading = false, errorAutorizacion = "Error de servidor: ${e.message}") } 
            }
        }
    }
}

class ArqueoViewModelFactory(
    private val vDao: VentaDao, 
    private val pDao: ProductoDao,
    private val iRepo: RepositoryInventario, 
    private val uRepo: RepositoryUsuario,
    private val vRepo: VentaRepository,
    private val vId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = 
        ArqueoViewModel(vDao, pDao, iRepo, uRepo, vRepo, vId) as T
}
