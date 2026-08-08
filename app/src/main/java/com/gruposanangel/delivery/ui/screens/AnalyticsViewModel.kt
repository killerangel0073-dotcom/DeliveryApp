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
                
                // 0.1 Obtener Usuarios, Rutas y Clientes para mapeo completo
                val usersSnap = db.collection("users").get().await()
                val rutasSnap = db.collection("rutas").get().await()
                val clientesSnap = db.collection("clientes").get().await()
                
                val userMap = usersSnap.documents.associateBy { it.id }
                val clientToRutaMap = clientesSnap.documents.associate { 
                    it.id to (it.getString("rutaId") ?: it.getString("id_ruta") ?: "") 
                }
                
                // Mapeo: AlmacenID -> { RutaNombre, VendedorNombre, FotoUrl, VendedorUid }
                val mappingRutas = rutasSnap.documents.mapNotNull { rDoc ->
                    val almRef = rDoc.getDocumentReference("almacenAsignado") ?: return@mapNotNull null
                    val vendRef = rDoc.getDocumentReference("vendedorAsignado")
                    val rutaNom = rDoc.getString("nombre") ?: rDoc.id
                    
                    val vendDoc = vendRef?.let { userMap[it.id] }
                    
                    almRef.id to object {
                        val nombreRuta = rutaNom
                        val nombreVendedor = vendDoc?.getString("nombre") ?: rutaNom.replace("Vendedor ", "")
                        val foto = vendDoc?.getString("photo_url") ?: vendDoc?.getString("foto_url") ?: ""
                        val uid = vendDoc?.id ?: almRef.id
                    }
                }.toMap()

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

                // PROCESAMIENTO
                val productosMap = mutableMapOf<String, ProductStat>()
                val rankingMap = mutableMapOf<String, SellerStat>() // Key: Identificador Unificado de Ruta

                // Inicializar ranking con todas las rutas conocidas (para que salgan aunque estén en $0)
                mappingRutas.forEach { (_, info) ->
                    val idRuta = info.nombreRuta
                    rankingMap[idRuta] = SellerStat(
                        uid = info.uid,
                        nombre = info.nombreVendedor,
                        totalVenta = 0.0,
                        totalGastos = 0.0,
                        numTickets = 0,
                        cancelaciones = 0,
                        fotoUrl = info.foto
                    )
                }

                coroutineScope {
                    val deferedVentasData = ventasSnap.documents.map { vDoc ->
                        async {
                            // 🔥 Prioridad RutaId, Fallback AlmacenId
                            val rIdVenta = vDoc.getString("rutaId") ?: vDoc.getString("rutaNombre")
                            val almId = vDoc.getString("almacenId") ?: vDoc.getString("almacenVendedorId") ?: ""
                            val cId = vDoc.getString("clienteId") ?: ""
                            
                            // 🚀 UNIFICACIÓN CON FALLBACK A CLIENTE:
                            val rankingKey = rIdVenta ?: mappingRutas[almId]?.nombreRuta ?: clientToRutaMap[cId] ?: almId
                            
                            val esCancelada = vDoc.getString("estado") == "CANCELADA"
                            
                            val pSnap = vDoc.reference.collection("productos").get().await()
                            val prodsFiltrados = pSnap.documents.mapNotNull { pDoc ->
                                val pId = pDoc.id
                                val mReal = pDoc.getString("marca") ?: masterCatalog[pId]?.first ?: "Delisa"
                                val cReal = pDoc.getString("categoria") ?: masterCatalog[pId]?.second ?: "General"

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
                            Triple(rankingKey, esCancelada, prodsFiltrados)
                        }
                    }
                    
                    val resultados = deferedVentasData.awaitAll()
                    var totalGlobalFiltrado = 0.0

                    resultados.forEach { (rankingKey, esCancelada, prods) ->
                        if (prods.isEmpty() || rankingKey.isEmpty()) return@forEach 
                        
                        // Si la ruta no estaba inicializada, se crea una entrada de respaldo
                        val stat = rankingMap[rankingKey] ?: SellerStat(rankingKey, rankingKey, 0.0, 0.0, 0, 0)
                        val montoTicketFiltrado = prods.sumOf { it.prec * it.cant }

                        if (esCancelada) {
                            rankingMap[rankingKey] = stat.copy(cancelaciones = stat.cancelaciones + 1)
                        } else {
                            totalGlobalFiltrado += montoTicketFiltrado
                            rankingMap[rankingKey] = stat.copy(
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

                    // Sumamos gastos (Consolidando por nombre de ruta)
                    gastosSnap.documents.forEach { doc ->
                        val monto = (doc.get("monto") as? Number)?.toDouble() ?: 0.0
                        val vId = doc.getString("vendedorId") ?: ""
                        val rIdGasto = doc.getString("rutaId") ?: doc.getString("rutaNombre")
                        
                        val targetRankingId = rIdGasto ?: run {
                            val almIdInferred = doc.getString("almacenId") ?: mappingRutas.entries.find { it.value.uid == vId }?.key
                            mappingRutas[almIdInferred]?.nombreRuta ?: almIdInferred
                        }
                        
                        if (targetRankingId != null && rankingMap.containsKey(targetRankingId)) {
                            val actual = rankingMap[targetRankingId]!!
                            rankingMap[targetRankingId] = actual.copy(totalGastos = actual.totalGastos + monto)
                        }
                    }

                    // Agrupar Gastos por Categoría
                    val gastosPorCat = gastosSnap.documents.groupBy { it.getString("categoria") ?: "Otros" }
                        .map { (cat, docs) -> ExpenseStat(cat, docs.sumOf { (it.get("monto") as? Number)?.toDouble() ?: 0.0 }) }
                        .sortedByDescending { it.total }

                    // Gráfico (Tendencia)
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

                    val finalTotalGastos = gastosSnap.documents.sumOf { (it.get("monto") as? Number)?.toDouble() ?: 0.0 }

                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(
                            isLoading = false,
                            totalVentaBruta = totalGlobalFiltrado,
                            totalGastos = finalTotalGastos,
                            utilidadOperativa = totalGlobalFiltrado - finalTotalGastos,
                            topProductos = productosMap.values.sortedByDescending { it.cantidad }.take(5),
                            rankingVendedores = rankingMap.values.filter { it.numTickets > 0 || it.totalVenta > 0 }.sortedByDescending { it.totalVenta },
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
