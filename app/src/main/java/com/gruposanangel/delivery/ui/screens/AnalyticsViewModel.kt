package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.*

data class ProductStat(
    val id: String,
    val nombre: String,
    val cantidad: Int,
    val monto: Double,
    val imagenUrl: String? = null
)

data class SellerStat(
    val uid: String,
    val nombre: String,
    val totalVenta: Double,
    val totalGastos: Double,
    val numTickets: Int,
    val cancelaciones: Int,
    val fotoUrl: String? = null
)

data class ExpenseStat(
    val categoria: String,
    val total: Double
)

data class DayStat(
    val fecha: String,
    val monto: Double,
    val timestamp: Long
)

data class AnalyticsUiState(
    val isLoading: Boolean = false,
    val totalVentaBruta: Double = 0.0,
    val totalGastos: Double = 0.0,
    val utilidadOperativa: Double = 0.0,
    val topProductos: List<ProductStat> = emptyList(),
    val rankingVendedores: List<SellerStat> = emptyList(),
    val desgloseGastos: List<ExpenseStat> = emptyList(),
    val ventasPorDia: List<DayStat> = emptyList(),
    val error: String? = null,

    // 🔥 FILTROS DE PERFIL (SOLO LOS QUE ESTÁN EN USO)
    val perfilesDisponibles: List<com.gruposanangel.delivery.data.PerfilVenta> = emptyList(),
    val perfilSeleccionado: com.gruposanangel.delivery.data.PerfilVenta? = null
)

class AnalyticsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()

    private var currentStartDate: Date? = null
    private var currentEndDate: Date? = null

    init {
        cargarConfiguracionFiltros()
    }

    private fun cargarConfiguracionFiltros() {
        viewModelScope.launch {
            try {
                // 1. Escanear todos los usuarios para ver qué perfiles tienen asignados
                val usersSnap = db.collection("users").whereEqualTo("activo", true).get().await()
                val perfilesUnicos = mutableMapOf<String, com.gruposanangel.delivery.data.PerfilVenta>()

                usersSnap.documents.forEach { doc ->
                    val perfilesRaw = doc.get("perfilesVenta") as? List<Map<String, Any>>
                    perfilesRaw?.forEach { pMap ->
                        val nombre = pMap["nombre"] as? String ?: ""
                        val filtrosRaw = pMap["filtros"] as? List<Map<String, Any>>
                        
                        val filtros = filtrosRaw?.map { fMap ->
                            val marca = fMap["marca"] as? String ?: ""
                            val cats = (fMap["categorias"] as? List<*>)?.mapNotNull { it.toString() } ?: emptyList()
                            com.gruposanangel.delivery.data.FiltroPerfil(marca, cats)
                        } ?: emptyList()

                        if (nombre.isNotEmpty()) {
                            // Usamos el nombre como clave para consolidar perfiles idénticos entre vendedores
                            perfilesUnicos[nombre] = com.gruposanangel.delivery.data.PerfilVenta(nombre, nombre, filtros)
                        }
                    }
                }

                _uiState.update { it.copy(
                    perfilesDisponibles = perfilesUnicos.values.sortedBy { it.nombre }
                ) }
            } catch (e: Exception) {
                Log.e("AnalyticsVM", "Error cargando perfiles en uso", e)
            }
        }
    }

    fun seleccionarPerfil(perfil: com.gruposanangel.delivery.data.PerfilVenta?) {
        _uiState.update { it.copy(perfilSeleccionado = perfil) }
        val start = currentStartDate
        val end = currentEndDate
        if (start != null && end != null) {
            cargarAnaliticas(start, end)
        }
    }

    private data class TempProd(val id: String, val nom: String, val cant: Int, val prec: Double, val img: String?, val marca: String, val cat: String)

    fun cargarAnaliticas(fechaInicio: Date, fechaFin: Date) {
        currentStartDate = fechaInicio
        currentEndDate = fechaFin
        
        val perfilFiltro = _uiState.value.perfilSeleccionado
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val startTs = Timestamp(comenzarDia(fechaInicio))
                val endTs = Timestamp(terminarDia(fechaFin))

                Log.d("AnalyticsVM", "Iniciando carga de analíticas desde ${startTs.toDate()} hasta ${endTs.toDate()}")

                // 0. Obtener Catálogo para imágenes de respaldo y clasificación
                val catalogSnap = db.collection("producto").get().await()
                val masterImages = catalogSnap.documents.associate { it.id to it.getString("imagenUrl") }
                val masterCatalog = catalogSnap.documents.associate { 
                    it.id to ((it.getString("marca") ?: "Delisa") to (it.getString("categoria") ?: "General"))
                }
                
                // 0.1 Obtener Usuarios para fotos de vendedores
                val usersSnap = db.collection("users").get().await()
                val userPhotos = usersSnap.documents.associate { it.id to it.getString("photo_url") }

                // 1. Obtener Ventas
                val ventasSnap = db.collection("ventas")
                    .whereGreaterThanOrEqualTo("fecha", startTs)
                    .whereLessThanOrEqualTo("fecha", endTs)
                    .get().await()

                Log.d("AnalyticsVM", "Ventas recuperadas: ${ventasSnap.size()}")

                // 2. Obtener Gastos
                val gastosSnap = db.collection("gastos")
                    .whereGreaterThanOrEqualTo("timestamp", startTs)
                    .whereLessThanOrEqualTo("timestamp", endTs)
                    .get().await()

                Log.d("AnalyticsVM", "Gastos recuperados: ${gastosSnap.size()}")

                // PROCESAMIENTO DE VENTAS Y PRODUCTOS
                val totalGastos = gastosSnap.documents.sumOf { (it.get("monto") as? Number)?.toDouble() ?: 0.0 }

                val productosMap = mutableMapOf<String, ProductStat>()
                val vendedoresMap = mutableMapOf<String, SellerStat>()

                // Pre-cargar vendedores desde la lista de usuarios para asegurar que aparezcan aunque no tengan ventas
                usersSnap.documents.forEach { doc ->
                    vendedoresMap[doc.id] = SellerStat(
                        uid = doc.id,
                        nombre = doc.getString("nombre") ?: "Vendedor",
                        totalVenta = 0.0,
                        totalGastos = 0.0,
                        numTickets = 0,
                        cancelaciones = 0,
                        fotoUrl = doc.getString("photo_url")
                    )
                }

                // Obtener productos de las ventas y procesar montos filtrados por vendedor
                coroutineScope {
                    val deferedVentasData = ventasSnap.documents.map { vDoc ->
                        async {
                            val vId = vDoc.getString("vendedorId") ?: ""
                            val esCancelada = vDoc.getString("estado") == "CANCELADA"
                            
                            val pSnap = vDoc.reference.collection("productos").get().await()
                            val prodsFiltrados = pSnap.documents.mapNotNull { pDoc ->
                                val pId = pDoc.id
                                
                                // RECUPERACIÓN DE CLASIFICACIÓN
                                val mReal = pDoc.getString("marca") ?: masterCatalog[pId]?.first ?: "Delisa"
                                val cReal = pDoc.getString("categoria") ?: masterCatalog[pId]?.second ?: "General"

                                // APLICAR FILTRO DE PERFIL OPERATIVO
                                if (perfilFiltro != null && perfilFiltro.id != "ALL") {
                                    val cumple = perfilFiltro.filtros.any { f ->
                                        val matchMarca = mReal.trim().equals(f.marca.trim(), ignoreCase = true)
                                        val matchCat = if (f.categorias.isNotEmpty()) {
                                            f.categorias.any { it.trim().equals(cReal.trim(), ignoreCase = true) }
                                        } else true
                                        matchMarca && matchCat
                                    }
                                    if (!cumple) return@mapNotNull null
                                }

                                TempProd(
                                    id = pId, 
                                    nom = pDoc.getString("nombre") ?: "Producto",
                                    cant = (pDoc.get("cantidad") as? Number)?.toInt() ?: 0,
                                    prec = (pDoc.get("precio") as? Number)?.toDouble() ?: 0.0,
                                    img = pDoc.getString("imagenUrl"),
                                    marca = mReal,
                                    cat = cReal
                                )
                            }
                            Triple(vId, esCancelada, prodsFiltrados)
                        }
                    }
                    
                    val resultados = deferedVentasData.awaitAll()
                    var totalGlobalFiltrado = 0.0

                    resultados.forEach { (vId, esCancelada, prods) ->
                        if (prods.isEmpty()) return@forEach 
                        
                        val stat = vendedoresMap[vId] ?: return@forEach
                        val montoTicketFiltrado = prods.sumOf { it.prec * it.cant }

                        if (esCancelada) {
                            vendedoresMap[vId] = stat.copy(cancelaciones = stat.cancelaciones + 1)
                        } else {
                            totalGlobalFiltrado += montoTicketFiltrado
                            vendedoresMap[vId] = stat.copy(
                                totalVenta = stat.totalVenta + montoTicketFiltrado,
                                numTickets = stat.numTickets + 1
                            )
                            
                            prods.forEach { tp ->
                                val finalImg = if (!tp.img.isNullOrEmpty()) tp.img else masterImages[tp.id]
                                val actualP = productosMap[tp.id] ?: ProductStat(tp.id, tp.nom, 0, 0.0, finalImg)
                                productosMap[tp.id] = actualP.copy(
                                    cantidad = actualP.cantidad + tp.cant,
                                    monto = actualP.monto + (tp.cant * tp.prec)
                                )
                            }
                        }
                    }

                    // Sumamos gastos a vendedores (Esto no depende del perfil, es operativo de la ruta)
                    gastosSnap.documents.forEach { doc ->
                        val vId = doc.getString("vendedorId") ?: return@forEach
                        val monto = (doc.get("monto") as? Number)?.toDouble() ?: 0.0
                        val actual = vendedoresMap[vId]
                        if (actual != null) {
                            vendedoresMap[vId] = actual.copy(totalGastos = actual.totalGastos + monto)
                        }
                    }

                    // Agrupar Gastos por Categoría
                    val gastosPorCat = gastosSnap.documents.groupBy { it.getString("categoria") ?: "Otros" }
                        .map { (cat, docs) -> ExpenseStat(cat, docs.sumOf { (it.get("monto") as? Number)?.toDouble() ?: 0.0 }) }
                        .sortedByDescending { it.total }

                    // Ventas por día (Tendencia)
                    val calGroup = Calendar.getInstance()
                    val ventasDiaMap = ventasSnap.documents.filter { it.getString("estado") != "CANCELADA" }.groupBy { 
                        val ts = it.getTimestamp("fecha")?.toDate()?.time ?: 0L
                        calGroup.timeInMillis = ts
                        calGroup.set(Calendar.HOUR_OF_DAY, 0); calGroup.set(Calendar.MINUTE, 0); calGroup.set(Calendar.SECOND, 0); calGroup.set(Calendar.MILLISECOND, 0)
                        calGroup.timeInMillis
                    }.mapValues { (_, docs) -> 
                        docs.sumOf { (it.get("total") as? Number)?.toDouble() ?: 0.0 }
                    }
                    
                    val displayFormat = java.text.SimpleDateFormat("EEE dd/MM", Locale("es", "MX"))
                    val trend = ventasDiaMap.entries.map { e ->
                        DayStat(fecha = displayFormat.format(Date(e.key)).uppercase(), monto = e.value, timestamp = e.key) 
                    }.sortedBy { it.timestamp }

                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(
                            isLoading = false,
                            totalVentaBruta = totalGlobalFiltrado,
                            totalGastos = totalGastos,
                            utilidadOperativa = totalGlobalFiltrado - totalGastos,
                            topProductos = productosMap.values.sortedByDescending { it.cantidad }.take(5),
                            rankingVendedores = vendedoresMap.values.filter { it.numTickets > 0 || it.totalVenta > 0 }.sortedByDescending { it.totalVenta },
                            desgloseGastos = gastosPorCat,
                            ventasPorDia = trend,
                            error = null
                        ) }
                    }
                }

            } catch (e: Exception) {
                Log.e("AnalyticsVM", "Error procesando analíticas", e)
                _uiState.update { it.copy(isLoading = false, error = "Error de conexión o datos: ${e.message}") }
            }
        }
    }

    private fun comenzarDia(d: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = d
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun terminarDia(d: Date): Date {
        val cal = Calendar.getInstance()
        cal.time = d
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return cal.time
    }
}
