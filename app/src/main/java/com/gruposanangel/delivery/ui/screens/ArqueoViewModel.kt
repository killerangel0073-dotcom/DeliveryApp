package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.ProductoDao
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaDao
import com.gruposanangel.delivery.data.VentaDetalleEntity
import com.gruposanangel.delivery.data.MovimientoInventarioEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
    val fechaUltimoArqueo: Long = 0L,
    val almacenAuditado: String = ""
)

data class ProductoArqueo(
    val id: String,
    val nombre: String,
    val precio: Double,
    val stockTeorico: Int,
    val stockInicialBitacora: Int = 0,
    val cargasPorDia: List<Int> = listOf(0, 0, 0, 0, 0, 0, 0), // L, M, M, J, V, S, D
    var stockReal: String = "",
    val imagenUrl: String = "",
    val categoria: String = ""
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
    private val remoteStockMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val remoteMovements = MutableStateFlow<List<MovimientoInventarioEntity>>(emptyList())

    init {
        inicializar()
    }

    fun inicializar() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val dbFirestore = FirebaseFirestore.getInstance()
                
                // 1. Obtener datos del Vendedor (para saber su Almacén)
                val userSnap = dbFirestore.collection("users").document(vendedorId).get().await()
                if (!userSnap.exists()) {
                    _uiState.update { it.copy(isLoading = false, error = "Vendedor no encontrado") }
                    return@launch
                }
                
                val nombreVendedor = userSnap.getString("nombre") ?: "Vendedor"
                val almacenAuditado = userSnap.getString("ultimoAlmacenNombre") ?: ""
                _uiState.update { it.copy(nombreVendedor = nombreVendedor, almacenAuditado = almacenAuditado) }

                // 2. Obtener Checkpoint (Último Arqueo)
                buscarUltimoArqueo()
                
                val startPoint = if (fechaUltimoArqueoTS > 0) fechaUltimoArqueoTS else getInicioSemana()
                
                // 3. Sincronizar Ventas (Desde Servidor a Local)
                try {
                    ventaRepo.sincronizarVentasPeriodo(vendedorId, startPoint, System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.e("ARQUEO", "Error sync ventas: ${e.message}")
                }

                // 4. Descargar Stock Actual del Vendedor desde Firestore (Snapshot del sistema)
                descargarStockRemoto(almacenAuditado)

                // 5. Descargar Movimientos (Cargas) desde Firestore
                descargarMovimientosRemotos(vendedorId, startPoint)

                // 6. Iniciar observación combinada
                iniciarObservacionArqueo()
                
            } catch (e: Exception) {
                Log.e("ARQUEO", "Error en inicialización", e)
                _uiState.update { it.copy(isLoading = false, error = "Error: ${e.message}") }
            }
        }
    }

    private fun getInicioSemana(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
        return calendar.timeInMillis
    }

    private suspend fun descargarStockRemoto(almacen: String) {
        if (almacen.isEmpty()) return
        try {
            val stock = inventarioRepo.obtenerStockAlmacen(almacen)
            remoteStockMap.value = stock
            Log.d("ARQUEO", "Stock remoto cargado para $almacen: ${stock.size} productos")
        } catch (e: Exception) {
            Log.e("ARQUEO", "Error cargando stock remoto", e)
        }
    }

    private suspend fun descargarMovimientosRemotos(vId: String, desde: Long) {
        try {
            val db = FirebaseFirestore.getInstance()
            val snap = db.collection("ajustes_inventario")
                .whereEqualTo("vendedorId", vId)
                .whereGreaterThanOrEqualTo("timestamp", desde)
                .get()
                .await()
            
            val movs = snap.documents.mapNotNull { d ->
                try {
                    MovimientoInventarioEntity(
                        id = d.id,
                        productoId = d.getString("productoId") ?: "",
                        nombreProducto = d.getString("nombreProducto") ?: "",
                        cantidad = d.getLong("cantidad")?.toInt() ?: 0,
                        tipo = d.getString("tipo") ?: "",
                        motivo = d.getString("motivo"),
                        vendedorId = d.getString("vendedorId") ?: "",
                        almacenNombre = d.getString("almacenNombre") ?: "",
                        clienteId = d.getString("clienteId"),
                        timestamp = d.getLong("timestamp") ?: 0L,
                        referenciaId = d.getString("referenciaId"),
                        sincronizado = true
                    )
                } catch (e: Exception) { null }
            }
            remoteMovements.value = movs
            Log.d("ARQUEO", "Movimientos remotos cargados: ${movs.size}")
        } catch (e: Exception) {
            Log.e("ARQUEO", "Error cargando movimientos remotos", e)
        }
    }

    private fun iniciarObservacionArqueo() {
        val inicioSemana = getInicioSemana()
        val startPoint = if (fechaUltimoArqueoTS > 0) fechaUltimoArqueoTS else inicioSemana

        combine(
            productoDao.getAllProductosFlow(), // Catálogo local para nombres/precios
            ventaDao.obtenerVentasPorPeriodoFlow(vendedorId, startPoint, Long.MAX_VALUE),
            ventaDao.obtenerDetallesPorPeriodoFlow(vendedorId, startPoint, Long.MAX_VALUE),
            remoteStockMap,
            remoteMovements
        ) { catalogo, ventasRaw, detallesVentas, stockRemoto, movimientos ->
            
            val ventas = ventasRaw.filter { it.estado != "CANCELADA" }
            
            // 🛡️ Auditoría de Unidades y Valor (Basada en Ventas y Movimientos remotos)
            var totalVUnidades = 0
            var valorVSemana = 0.0
            detallesVentas.forEach { d ->
                totalVUnidades += d.cantidad
                valorVSemana += d.cantidad * d.precio
            }

            val totalCargasSemana = movimientos.filter { it.tipo == "CARGA_INVENTARIO" }.sumOf { it.cantidad }
            val totalDevoluciones = movimientos.filter { it.tipo == "ENTRADA_MALO_DEVOLUCION" }.sumOf { it.cantidad }
            
            // Valor de cargas basado en precios del catálogo
            val valorCargasSemana = movimientos.filter { it.tipo == "CARGA_INVENTARIO" }
                .sumOf { m -> (catalogo.find { it.productoId == m.productoId }?.precio ?: 0.0) * m.cantidad }

            val totalEntradasBueno = movimientos.filter { 
                it.tipo == "CARGA_INVENTARIO" || it.tipo == "ENTRADA_CAMBIO_BUENO"
            }.sumOf { it.cantidad }
            
            val totalSalidasBueno = totalVUnidades + movimientos.filter { 
                it.tipo == "SALIDA_CAMBIO_BUENO" || it.tipo == "SALIDA_REPOSICION_BUENO" 
            }.sumOf { it.cantidad }

            // STOCK DEL SISTEMA (Firestore Snapshot)
            val piezasSistema = stockRemoto.values.sum()
            val valorSistema = stockRemoto.entries.sumOf { (id, cant) ->
                (catalogo.find { it.productoId == id }?.precio ?: 0.0) * cant
            }
            
            // INICIAL = Sistema - Entradas + Salidas
            val stockInicialCalculado = if (stockInicialRealMap.isNotEmpty()) {
                stockInicialRealMap.values.sum()
            } else {
                piezasSistema - totalEntradasBueno + totalSalidasBueno
            }

            // Mapeo de Productos para Arqueo (Usando catálogo base + stock remoto)
            val lista = catalogo.filter { !it.id.contains("_") }.mapNotNull { p ->
                val baseId = p.productoId
                val cantRemota = stockRemoto[baseId] ?: 0
                
                // Si no hay stock remoto ni inicial ni movimientos, ocultar del arqueo para no saturar
                val inicialDesdeArqueo = stockInicialRealMap[baseId]
                val movsCarga = movimientos.filter { m -> m.productoId == baseId && m.tipo == "CARGA_INVENTARIO" }
                
                if (cantRemota == 0 && inicialDesdeArqueo == null && movsCarga.isEmpty()) return@mapNotNull null

                val previo = _uiState.value.productosArqueo.find { it.id == baseId }?.stockReal ?: ""
                
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

                val inicial = if (inicialDesdeArqueo != null) {
                    inicialDesdeArqueo
                } else {
                    val pEntradas = totalEntradasBueno // Simplificado o filtrado por ID
                    val pSalidas = (detallesVentas.filter { it.productoId == baseId }.sumOf { it.cantidad })
                    cantRemota - pEntradas + pSalidas
                }

                ProductoArqueo(
                    id = baseId, nombre = p.nombre, precio = p.precio, stockTeorico = cantRemota,
                    stockInicialBitacora = inicial, cargasPorDia = cargasDia,
                    stockReal = previo, imagenUrl = p.imagenUrl ?: "", categoria = p.categoria
                )
            }.sortedWith(compareBy({ it.categoria }, { it.nombre }))

            // Update State
            _uiState.update { current -> 
                current.copy(
                    isLoading = false,
                    totalVentasSemana = ventas.sumOf { it.total },
                    comision = ventas.sumOf { it.total } * 0.03,
                    ingresoTotal = 1800.0 + (ventas.sumOf { it.total } * 0.03),
                    ticketPromedio = if (ventas.isNotEmpty()) ventas.sumOf { it.total } / ventas.size else 0.0,
                    clientesVisitados = ventas.map { it.clienteId }.distinct().size,
                    stockInicial = stockInicialCalculado,
                    totalCargasSemana = totalCargasSemana,
                    totalVentasUnidades = totalVUnidades,
                    totalDevoluciones = totalDevoluciones,
                    saldoTeoricoCalculado = piezasSistema,
                    valorStockInicial = if (stockInicialRealMap.isNotEmpty()) {
                        catalogo.sumOf { p -> (stockInicialRealMap[p.productoId] ?: 0) * p.precio }
                    } else {
                        valorSistema + valorVSemana
                    },
                    valorCargasSemana = valorCargasSemana,
                    valorVentasSemana = valorVSemana,
                    valorDevolucionesSemana = movimientos.filter { it.tipo == "ENTRADA_MALO_DEVOLUCION" }
                        .sumOf { m -> (catalogo.find { it.productoId == m.productoId }?.precio ?: 0.0) * m.cantidad },
                    saldoValorTeoricoCalculado = valorSistema,
                    productosArqueo = lista
                )
            }
        }.launchIn(viewModelScope)
    }

    private suspend fun buscarUltimoArqueo() {
        try {
            val dbFirestore = FirebaseFirestore.getInstance()
            val query = dbFirestore.collection("reportes_arqueo")
                .whereEqualTo("vendedorId", vendedorId)
                .orderBy("fecha", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            if (!query.isEmpty) {
                val doc = query.documents.first()
                fechaUltimoArqueoTS = doc.getTimestamp("fecha")?.toDate()?.time ?: 0L
                val detalle = doc.get("detalle") as? List<Map<String, Any>>
                stockInicialRealMap.clear()
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

    fun refrescarDatos() {
        inicializar()
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
                val arqueoId = "AUDIT_ARQUEO_${java.util.UUID.randomUUID()}"
                val ts = System.currentTimeMillis()

                // 1. GENERAR AJUSTES DE INVENTARIO (Delegar a Cloud Function)
                val batch = db.batch()
                val almacen = _uiState.value.almacenAuditado
                
                _uiState.value.productosArqueo.forEach { p ->
                    val fisico = p.stockReal.toIntOrNull() ?: p.stockTeorico
                    val teorico = p.stockTeorico
                    val diferencial = fisico - teorico

                    if (diferencial != 0) {
                        val tipoAjuste = if (diferencial > 0) "AJUSTE_ARQUEO_SOBRANTE" else "AJUSTE_ARQUEO_FALTANTE"
                        val movId = java.util.UUID.randomUUID().toString()
                        
                        val dataMov = mapOf(
                            "productoId" to p.id,
                            "nombreProducto" to p.nombre,
                            "cantidad" to Math.abs(diferencial),
                            "cantidadFisica" to fisico,
                            "cantidadTeorica" to teorico,
                            "tipo" to tipoAjuste,
                            "vendedorId" to vendedorId,
                            "almacenNombre" to almacen,
                            "timestamp" to ts,
                            "referenciaId" to arqueoId,
                            "metodoAuditoria" to "ARQUEO_SUPERVISOR"
                        )
                        batch.set(db.collection("ajustes_inventario").document(movId), dataMov)
                    }
                    
                    // Actualizar Room localmente para reflejar el cambio inmediato en la UI del supervisor
                    productoDao.updateCantidadDisponible("${p.id}_$almacen", fisico)
                }

                // 2. GUARDAR REPORTE EN FIREBASE
                val arqueoData = mapOf(
                    "vendedorId" to vendedorId,
                    "vendedorNombre" to _uiState.value.nombreVendedor,
                    "almacen" to almacen,
                    "fecha" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "referenciaId" to arqueoId,
                    "autorizadoPor" to autorizador,
                    "piezasTeoricas" to _uiState.value.saldoTeoricoCalculado,
                    "piezasReales" to _uiState.value.productosArqueo.sumOf { it.stockReal.toIntOrNull() ?: 0 },
                    "valorStockInicial" to _uiState.value.valorStockInicial,
                    "valorCargasSemana" to _uiState.value.valorCargasSemana,
                    "valorVentasSemana" to _uiState.value.valorVentasSemana,
                    "valorTeoricoFinal" to _uiState.value.saldoValorTeoricoCalculado,
                    "valorRealContado" to _uiState.value.productosArqueo.sumOf { (it.stockReal.toIntOrNull() ?: 0) * it.precio },
                    "diferenciaMonetaria" to _uiState.value.productosArqueo.sumOf { it.valorDiferencia },
                    "detalle" to _uiState.value.productosArqueo.map { 
                        mapOf(
                            "id" to it.id, "nombre" to it.nombre, "t" to it.stockTeorico, 
                            "r" to (it.stockReal.toIntOrNull() ?: 0), "p" to it.precio, "d" to it.valorDiferencia
                        ) 
                    }
                )

                batch.set(db.collection("reportes_arqueo").document(), arqueoData)
                batch.commit().await()
                
                _uiState.update { it.copy(isLoading = false, reporteGuardado = true, autorizadoPor = autorizador) }
                
            } catch (e: Exception) { 
                Log.e("ARQUEO", "Error en autorizarCierre", e)
                _uiState.update { it.copy(isLoading = false, errorAutorizacion = "Error: ${e.message}") } 
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
