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
    val error: String? = null
)

class AnalyticsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()

    private data class TempProd(val id: String, val nom: String, val cant: Int, val prec: Double, val img: String?)

    fun cargarAnaliticas(fechaInicio: Date, fechaFin: Date) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val startTs = Timestamp(comenzarDia(fechaInicio))
                val endTs = Timestamp(terminarDia(fechaFin))

                Log.d("AnalyticsVM", "Iniciando carga de analíticas desde ${startTs.toDate()} hasta ${endTs.toDate()}")

                // 0. Obtener Catálogo para imágenes de respaldo (Fallback)
                val catalogSnap = db.collection("producto").get().await()
                val masterImages = catalogSnap.documents.associate { it.id to it.getString("imagenUrl") }
                
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
                val ventasActivas = ventasSnap.documents.filter { it.getString("estado") != "CANCELADA" }
                val totalVenta = ventasActivas.sumOf { (it.get("total") as? Number)?.toDouble() ?: 0.0 }
                val totalGastos = gastosSnap.documents.sumOf { (it.get("monto") as? Number)?.toDouble() ?: 0.0 }

                // Procesar Top Productos y Ranking Vendedores
                val productosMap = mutableMapOf<String, ProductStat>()
                val vendedoresMap = mutableMapOf<String, SellerStat>()

                // Inicializar vendedores con las ventas (incluso canceladas para el contador)
                ventasSnap.documents.forEach { doc ->
                    val vId = doc.getString("vendedorId") ?: return@forEach
                    val vNom = doc.getString("vendedorNombre") ?: "Vendedor"
                    val esCancelada = doc.getString("estado") == "CANCELADA"
                    val monto = (doc.get("total") as? Number)?.toDouble() ?: 0.0

                    val actual = vendedoresMap[vId] ?: SellerStat(
                        uid = vId, 
                        nombre = vNom, 
                        totalVenta = 0.0, 
                        totalGastos = 0.0, 
                        numTickets = 0, 
                        cancelaciones = 0,
                        fotoUrl = userPhotos[vId]
                    )
                    vendedoresMap[vId] = actual.copy(
                        totalVenta = actual.totalVenta + (if (esCancelada) 0.0 else monto),
                        numTickets = actual.numTickets + (if (esCancelada) 0 else 1),
                        cancelaciones = actual.cancelaciones + (if (esCancelada) 1 else 0)
                    )
                }

                // Obtener productos de las ventas activas (Subcolección)
                coroutineScope {
                    val deferedProds = ventasActivas.map { vDoc ->
                        async {
                            val pSnap = vDoc.reference.collection("productos").get().await()
                            pSnap.documents.map { pDoc ->
                                TempProd(
                                    id = pDoc.id, 
                                    nom = pDoc.getString("nombre") ?: "Producto",
                                    cant = (pDoc.get("cantidad") as? Number)?.toInt() ?: 0,
                                    prec = (pDoc.get("precio") as? Number)?.toDouble() ?: 0.0,
                                    img = pDoc.getString("imagenUrl")
                                )
                            }
                        }
                    }
                    
                    val todasLasVentasProds = deferedProds.awaitAll().flatten()
                    todasLasVentasProds.forEach { tp ->
                        // 🔥 Usar la imagen de la venta, o el catálogo maestro como respaldo
                        val finalImg = if (!tp.img.isNullOrEmpty()) tp.img else masterImages[tp.id]
                        
                        val actual = productosMap[tp.id] ?: ProductStat(tp.id, tp.nom, 0, 0.0, finalImg)
                        productosMap[tp.id] = actual.copy(
                            cantidad = actual.cantidad + tp.cant,
                            monto = actual.monto + (tp.cant * tp.prec)
                        )
                    }
                }

                val topProds = productosMap.values.sortedByDescending { it.cantidad }.take(5)

                // Sumamos gastos a vendedores
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

                // Ventas por día (Tendencia) con orden CRONOLÓGICO
                val calGroup = Calendar.getInstance()
                val ventasDiaMap = ventasActivas.groupBy { 
                    val ts = it.getTimestamp("fecha")?.toDate()?.time ?: 0L
                    calGroup.timeInMillis = ts
                    calGroup.set(Calendar.HOUR_OF_DAY, 0)
                    calGroup.set(Calendar.MINUTE, 0)
                    calGroup.set(Calendar.SECOND, 0)
                    calGroup.set(Calendar.MILLISECOND, 0)
                    calGroup.timeInMillis
                }.mapValues { (_, docs) -> docs.sumOf { (it.get("total") as? Number)?.toDouble() ?: 0.0 } }
                
                // Formato: LUN 01/07
                val displayFormat = java.text.SimpleDateFormat("EEE dd/MM", Locale("es", "MX"))
                val trend = ventasDiaMap.entries.map { 
                    DayStat(
                        fecha = displayFormat.format(Date(it.key)).uppercase(), 
                        monto = it.value,
                        timestamp = it.key
                    ) 
                }.sortedBy { it.timestamp }

                _uiState.update { it.copy(
                    isLoading = false,
                    totalVentaBruta = totalVenta,
                    totalGastos = totalGastos,
                    utilidadOperativa = totalVenta - totalGastos,
                    topProductos = topProds,
                    rankingVendedores = vendedoresMap.values.sortedByDescending { it.totalVenta },
                    desgloseGastos = gastosPorCat,
                    ventasPorDia = trend,
                    error = null
                ) }

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
