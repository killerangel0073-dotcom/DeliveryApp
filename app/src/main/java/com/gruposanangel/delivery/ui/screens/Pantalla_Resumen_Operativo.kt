package com.gruposanangel.delivery.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.RectF
import android.graphics.Path
import android.graphics.DashPathEffect
import android.graphics.Bitmap
import android.content.Context
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.ui.platform.LocalContext

import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.ShoppingCart
import com.gruposanangel.delivery.ui.theme.*

data class ProdVendidoReporte(
    val nombre: String,
    val cantidad: Int,
    val total: Double
)

data class ResumenOperativoUiState(
    val productos: List<Map<String, Any>> = emptyList(),
    val stockGlobal: Map<String, Int> = emptyMap(),
    val ventas15Dias: List<ProdVendidoReporte> = emptyList(),
    val isLoading: Boolean = true,
    val isFetchingVentas: Boolean = false,
    val isCalculatingPurchase: Boolean = false
)

data class SugerenciaCompra(
    val id: String,
    val nombre: String,
    val cajasAPedir: Int,
    val inversion: Double,
    val bolsitasResultantes: Int,
    val diasCobertura: Double,
    val costoCaja: Double,
    val pesoCajaKilos: Double,
    val pctVentaDirecta: Double = 100.0,
    val pctParaMixes: Double = 0.0,
    val kgVentaDirecta: Double = 0.0,
    val kgParaMixes: Double = 0.0,
    val stockInicialBolsitas: Int = 0,
    val esCompuesto: Boolean = false,
    val aFabricar: Int = 0
)

class ResumenOperativoViewModel(private val inventarioRepo: RepositoryInventario) : ViewModel() {
    private val _uiState = MutableStateFlow(ResumenOperativoUiState())
    val uiState: StateFlow<ResumenOperativoUiState> = _uiState.asStateFlow()

    init {
        cargarDatos()
    }

    fun cargarDatos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val db = FirebaseFirestore.getInstance()
                val result = db.collection("producto")
                    .whereEqualTo("activo", true)
                    .get()
                    .await()
                
                val productos = result.documents.mapNotNull { doc ->
                    val data = doc.data?.toMutableMap() ?: return@mapNotNull null
                    data["id"] = doc.id 
                    data
                }
                val stockGlobal = inventarioRepo.obtenerStockGlobal()
                
                _uiState.value = _uiState.value.copy(productos = productos, stockGlobal = stockGlobal, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    suspend fun obtenerVentasPeriodo(inicio: Date, fin: Date): List<ProdVendidoReporte> {
        val db = FirebaseFirestore.getInstance()
        val calFin = Calendar.getInstance().apply {
            time = fin
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }
        val tsInicio = com.google.firebase.Timestamp(inicio)
        val tsFin = com.google.firebase.Timestamp(calFin.time)

        val result = db.collection("ventas")
            .whereGreaterThanOrEqualTo("fecha", tsInicio)
            .whereLessThanOrEqualTo("fecha", tsFin)
            .get()
            .await()
        
        val mapa = mutableMapOf<String, ProdVendidoReporte>()
        coroutineScope {
            val tasks = result.documents.map { doc ->
                async {
                    val estado = doc.getString("estado")?.lowercase() ?: ""
                    if (estado != "pagada" && estado != "finalizada" && estado != "venta") return@async
                    val productosData = doc.get("productos") ?: doc.get("detalles")
                    var encontroProductos = false
                    when (productosData) {
                        is List<*> -> { productosData.forEach { (it as? Map<*, *>)?.let { p -> acumularProducto(mapa, p); encontroProductos = true } } }
                        is Map<*, *> -> { productosData.values.forEach { (it as? Map<*, *>)?.let { p -> acumularProducto(mapa, p); encontroProductos = true } } }
                    }
                    if (!encontroProductos) {
                        try {
                            var subProds = db.collection("ventas").document(doc.id).collection("productos").get().await()
                            if (subProds.isEmpty) subProds = db.collection("ventas").document(doc.id).collection("detalles").get().await()
                            subProds.documents.forEach { pDoc -> pDoc.data?.let { p -> acumularProducto(mapa, p) } }
                        } catch (e: Exception) { }
                    }
                }
            }
            tasks.awaitAll()
        }
        return mapa.values.toList()
    }

    suspend fun generarPlanCompra(presupuesto: Double): List<SugerenciaCompra> {
        val cal = Calendar.getInstance(); val fin = cal.time
        cal.add(Calendar.DAY_OF_YEAR, -30); val inicio = cal.time
        val ventas30Dias = obtenerVentasPeriodo(inicio, fin)
        val stockActualMap = _uiState.value.stockGlobal
        val productosCatalogo = _uiState.value.productos

        // 1. Preparar métricas iniciales
        val metricas = productosCatalogo.mapNotNull { prod ->
            val id = prod["id"] as? String ?: ""
            val nombre = prod["nombre"] as? String ?: "Sin Nombre"
            val isCompuesto = prod["esCompuesto"] as? Boolean ?: false
            
            val precioCompraUnit = prod["precioCompra"]?.toString()?.toDoubleOrNull() ?: 0.0
            val cantUnitGramos = prod["cantidadUnitario"]?.toString()?.toDoubleOrNull() ?: 0.0
            val gramosVenta = prod["gramosVenta"]?.toString()?.toDoubleOrNull() ?: 0.0
            val unidadesDisplay = prod["unidadesPorDisplay"]?.toString()?.toIntOrNull() ?: 1
            
            if (gramosVenta <= 0) return@mapNotNull null

            val rendimiento = if (cantUnitGramos > 0) cantUnitGramos / gramosVenta else 0.0
            val bolsitasPorCaja = (rendimiento * unidadesDisplay).toInt()
            val costoCaja = precioCompraUnit * unidadesDisplay

            val vendido30 = ventas30Dias.find { it.nombre == nombre }?.cantidad ?: 0
            val ventaDiaria = vendido30 / 30.0
            
            // Stock real en bolsitas individuales
            val stockEnBolsitas = stockActualMap[id] ?: 0
            
            mutableMapOf(
                "id" to id, "nombre" to nombre, "costoCaja" to costoCaja, "bolsitasPorCaja" to bolsitasPorCaja,
                "ventaDiariaPropia" to ventaDiaria, "ventaDiariaTotal" to ventaDiaria,
                "stockActual" to stockEnBolsitas, "cajasCompradas" to 0, "esCompuesto" to isCompuesto,
                "gramosVenta" to gramosVenta
            )
        }

        // 2. Explosión de demanda para Mixes
        metricas.forEach { item ->
            if (item["esCompuesto"] as Boolean) {
                val prodId = item["id"] as String
                val ingredientes = productosCatalogo.find { it["id"] == prodId }?.get("ingredientes") as? List<Map<String, Any>>
                val ventaDiariaMix = item["ventaDiariaPropia"] as Double
                ingredientes?.forEach { ing ->
                    val idIng = ing["id"]?.toString() ?: ""
                    val gramosEnMix = (ing["gramos"] as? Number)?.toDouble() ?: 0.0
                    val metricaIngrediente = metricas.find { it["id"] == idIng }
                    if (metricaIngrediente != null) {
                        val gramosVentaBase = productosCatalogo.find { it["id"] == idIng }?.get("gramosVenta")?.toString()?.toDoubleOrNull() ?: 1.0
                        val extraVentaDiaria = (ventaDiariaMix * gramosEnMix) / gramosVentaBase
                        metricaIngrediente["ventaDiariaTotal"] = (metricaIngrediente["ventaDiariaTotal"] as Double) + extraVentaDiaria
                    }
                }
            }
        }

        // 3. Algoritmo de Compra
        var presupuestoRestante = presupuesto
        val plan = metricas.toMutableList()
        while (presupuestoRestante > 0) {
            // 🔥 Validación de Venta Mínima: Solo sugerir compra si la venta diaria total es > 0.1
            val masUrgente = plan.filter { 
                !(it["esCompuesto"] as Boolean) && 
                (it["costoCaja"] as Double) > 0 && 
                (it["costoCaja"] as Double) <= presupuestoRestante &&
                (it["ventaDiariaTotal"] as Double) > 0.1 
            }
                .minByOrNull { 
                    val stockTotal = (it["stockActual"] as Int) + ((it["cajasCompradas"] as Int) * (it["bolsitasPorCaja"] as Int))
                    val vD = it["ventaDiariaTotal"] as Double
                    if (vD <= 0.1) 9999.0 else stockTotal / vD
                } ?: break 
            masUrgente["cajasCompradas"] = (masUrgente["cajasCompradas"] as Int) + 1
            presupuestoRestante -= (masUrgente["costoCaja"] as Double)
        }

        // 4. Formatear resultados finales
        return plan.map { 
            val id = it["id"] as String
            val cajas = it["cajasCompradas"] as Int
            val bolsitasCaja = it["bolsitasPorCaja"] as Int
            val vDPropia = it["ventaDiariaPropia"] as Double
            val vDTotal = it["ventaDiariaTotal"] as Double
            val stockInicial = it["stockActual"] as Int
            val esCompuesto = it["esCompuesto"] as Boolean
            val gVenta = it["gramosVenta"] as Double
            
            // --- CÁLCULO DE COBERTURA Y STOCK FINAL ---
            var coberturaFinal: Double
            var stockFinal: Int
            
            if (esCompuesto) {
                // Para Mixes, la cobertura depende de sus ingredientes
                val ingredientes = productosCatalogo.find { p -> p["id"] == id }?.get("ingredientes") as? List<Map<String, Any>>
                val coberturasIng = ingredientes?.mapNotNull { ing ->
                    val mIng = plan.find { p -> p["id"] == ing["id"] } ?: return@mapNotNull null
                    val sTotal = (mIng["stockActual"] as Int) + ((mIng["cajasCompradas"] as Int) * (mIng["bolsitasPorCaja"] as Int))
                    val vDIngTotal = mIng["ventaDiariaTotal"] as Double
                    if (vDIngTotal > 0.1) sTotal / vDIngTotal else 9999.0
                }
                val minCobIng = if (!coberturasIng.isNullOrEmpty()) coberturasIng.minOrNull() ?: 0.0 else 0.0
                
                // Si el Mix en sí no tiene venta, marcamos cobertura como -1 (Sin movimiento)
                if (vDPropia <= 0.1) {
                    coberturaFinal = -1.0 
                    stockFinal = stockInicial
                } else {
                    coberturaFinal = minCobIng
                    stockFinal = (vDPropia * coberturaFinal).toInt()
                }
            } else {
                stockFinal = stockInicial + (cajas * bolsitasCaja)
                coberturaFinal = if (vDTotal > 0.1) stockFinal / vDTotal else -1.0
            }
            
            val prodOrig = productosCatalogo.find { p -> p["id"] == id }
            val pesoKilosCaja = ((prodOrig?.get("cantidadUnitario")?.toString()?.toDoubleOrNull() ?: 0.0) * (prodOrig?.get("unidadesPorDisplay")?.toString()?.toDoubleOrNull() ?: 1.0)) / 1000.0

            // --- REPARTO DE KG Y PIEZAS (SOLO VS MIX) ---
            val kgComprados = cajas * pesoKilosCaja
            val ratioSolo = if (vDTotal > 0) vDPropia / vDTotal else 1.0
            val kgSolo = kgComprados * ratioSolo
            
            val aFabCalculado = if (esCompuesto) {
                (stockFinal - stockInicial).coerceAtLeast(0)
            } else {
                // Solo las piezas que resultan de los kilos para venta directa
                if (gVenta > 0) (kgSolo * 1000 / gVenta).toInt() else 0
            }

            SugerenciaCompra(
                id = id,
                nombre = it["nombre"] as String,
                cajasAPedir = cajas,
                inversion = cajas * (it["costoCaja"] as Double),
                bolsitasResultantes = stockInicial + aFabCalculado,
                diasCobertura = coberturaFinal,
                costoCaja = it["costoCaja"] as Double,
                pesoCajaKilos = pesoKilosCaja,
                pctVentaDirecta = ratioSolo * 100.0,
                pctParaMixes = (1.0 - ratioSolo) * 100.0,
                kgVentaDirecta = kgSolo,
                kgParaMixes = kgComprados * (1.0 - ratioSolo),
                stockInicialBolsitas = stockInicial,
                esCompuesto = esCompuesto,
                aFabricar = aFabCalculado
            )
        }.sortedWith(compareByDescending<SugerenciaCompra> { it.cajasAPedir > 0 }.thenByDescending { it.inversion }.thenBy { it.diasCobertura })
    }

    private fun acumularProducto(mapa: MutableMap<String, ProdVendidoReporte>, p: Map<*, *>) {
        val nombre = p["nombre"] as? String ?: "Sin Nombre"
        val cant = (p["cantidad"] as? Number)?.toInt() ?: 0
        val precio = (p["precio"] as? Number)?.toDouble() ?: 0.0
        if (cant <= 0) return
        synchronized(mapa) {
            val actual = mapa[nombre] ?: ProdVendidoReporte(nombre, 0, 0.0)
            mapa[nombre] = actual.copy(cantidad = actual.cantidad + cant, total = actual.total + (cant * precio))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaResumenOperativo(navController: NavController) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val viewModel: ResumenOperativoViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getDatabase(context)
            val repo = RepositoryInventario(FirebaseDataSource(), db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
            return ResumenOperativoViewModel(repo) as T
        }
    })
    val uiState by viewModel.uiState.collectAsState()
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))

    var showDatePicker by remember { mutableStateOf(false) }; var showPurchaseDialog by remember { mutableStateOf(false) }
    var isGeneratingPdf by remember { mutableStateOf(false) }; var processingMessage by remember { mutableStateOf("PROCESANDO REPORTE") }
    var startDate by remember { mutableStateOf(Date()) }; var endDate by remember { mutableStateOf(Date()) }

    if (showPurchaseDialog) {
        var budgetInput by remember { mutableStateOf("45000") }
        AlertDialog(onDismissRequest = { showPurchaseDialog = false }, title = { Text("Asistente de Compra Inteligente", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("¿Cuánto deseas invertir en total?", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = budgetInput, onValueChange = { if (it.all { c -> c.isDigit() }) budgetInput = it }, label = { Text("Presupuesto de Inversión") }, prefix = { Text("$ ") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("* El sistema comprará cajas completas priorizando los productos con menos días de stock.", fontSize = 10.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pres = budgetInput.toDoubleOrNull() ?: 0.0; showPurchaseDialog = false
                    scope.launch { processingMessage = "CALCULANDO PLAN DE COMPRA"; isGeneratingPdf = true
                        try { val plan = viewModel.generarPlanCompra(pres)
                            if (plan.isNotEmpty()) { val file = generarPdfSugerenciaCompra(context, plan, pres, viewModel.uiState.value.productos)
                                abrirPdfResumen(context, file)
                            } else Toast.makeText(context, "Presupuesto insuficiente", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) { Toast.makeText(context, "Error al generar plan", Toast.LENGTH_SHORT).show() }
                        finally { isGeneratingPdf = false; processingMessage = "PROCESANDO REPORTE" }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("GENERAR PLAN", fontWeight = FontWeight.Bold) }
            }, dismissButton = { TextButton(onClick = { showPurchaseDialog = false }) { Text("CANCELAR") } }
        )
    }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(initialSelectedStartDateMillis = startDate.time, initialSelectedEndDateMillis = endDate.time)
        val isDark = ThemeConfig.isDarkTheme.value ?: isSystemInDarkTheme()
        
        DeliveryTheme(darkTheme = isDark) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false }, 
                confirmButton = {
                    TextButton(onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis; val endMillis = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null) {
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")); cal.timeInMillis = startMillis
                            val sDate = Calendar.getInstance().apply { set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 0, 0, 0) }.time
                            val eDate = if (endMillis != null) { cal.timeInMillis = endMillis; Calendar.getInstance().apply { set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), 23, 59, 59) }.time } else sDate
                            startDate = sDate; endDate = eDate; showDatePicker = false
                            scope.launch { isGeneratingPdf = true
                                try { val ventas = viewModel.obtenerVentasPeriodo(sDate, eDate)
                                    if (ventas.isNotEmpty()) { val file = generarPdfVentasPeriodo(context, ventas, sDate, eDate); abrirPdfResumen(context, file)
                                    } else Toast.makeText(context, "No hay ventas", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) { } finally { isGeneratingPdf = false }
                            }
                        }
                    }) { Text("ACEPTAR", fontWeight = FontWeight.Bold, color = DelisaRed) }
                }, 
                dismissButton = { 
                    TextButton(onClick = { showDatePicker = false }) { 
                        Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant) 
                    } 
                },
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                DateRangePicker(
                    state = dateRangePickerState,
                    modifier = Modifier.height(500.dp),
                    title = { Text("Seleccionar Periodo", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                    headline = { Text("Filtro de Reporte", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface) },
                    colors = DatePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        headlineContentColor = DelisaRed,
                        selectedDayContainerColor = DelisaRed,
                        selectedDayContentColor = Color.White,
                        todayContentColor = DelisaRed,
                        todayDateBorderColor = DelisaRed,
                        dayInSelectionRangeContainerColor = DelisaRed.copy(alpha = 0.15f),
                        dayInSelectionRangeContentColor = DelisaRed,
                        navigationContentColor = MaterialTheme.colorScheme.onSurface,
                        weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        yearContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        currentYearContentColor = DelisaRed,
                        selectedYearContainerColor = DelisaRed,
                        selectedYearContentColor = Color.White
                    )
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("RESUMEN OPERATIVO", fontWeight = FontWeight.Black, fontSize = 16.sp) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed) } }, actions = {
                if (!uiState.isLoading) {
                    IconButton(onClick = { showPurchaseDialog = true }) { Icon(Icons.Rounded.ShoppingCart, null, tint = DelisaBlue) }
                    IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Rounded.BarChart, null, tint = DelisaGreen) }
                    IconButton(onClick = { scope.launch { isGeneratingPdf = true; try { val file = generarPdfInventarioGlobal(context, uiState.productos, uiState.stockGlobal); abrirPdfResumen(context, file) } finally { isGeneratingPdf = false } } }) { Icon(Icons.Rounded.PictureAsPdf, null, tint = DelisaRed) }
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
        }, containerColor = MaterialTheme.colorScheme.background) { padding ->
            if (uiState.isLoading) { Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator(color = DelisaRed) }
            } else {
                val margins = uiState.productos.map { calcularMargen(it) }
                val maxMargen = if (margins.isNotEmpty()) margins.maxOrNull() ?: 0.0 else 0.0
                val minMargen = if (margins.isNotEmpty()) margins.minOrNull() ?: 0.0 else 0.0
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { ResumenGeneralFinanzas(uiState.productos) }
                    item { Text("Análisis de Rentabilidad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp)) }
                    items(uiState.productos.sortedByDescending { calcularMargen(it) }) { prod -> CardProductoFinanzas(prod, formatoMoneda, minMargen, maxMargen) }
                }
            }
        }
        if (isGeneratingPdf) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(enabled = false){}, contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.padding(32.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(16.dp)) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(80.dp), DelisaRed, 6.dp, trackColor = DelisaRed.copy(0.1f)); Icon(Icons.Rounded.PictureAsPdf, null, tint = DelisaRed, modifier = Modifier.size(32.dp)) }
                        Spacer(Modifier.height(24.dp)); Text(processingMessage, fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, letterSpacing = 1.sp)
                        Text("Estamos analizando las ventas y\nconstruyendo tu documento...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ResumenGeneralFinanzas(productos: List<Map<String, Any>>) {
    val margenPromedio = if (productos.isNotEmpty()) productos.map { calcularMargen(it) }.filter { it > 0 }.average() else 0.0
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(8.dp)) {
        Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(DelisaRed, DelisaRedDark))).padding(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Color.White, modifier = Modifier.size(32.dp))
                Text("MARGEN DE UTILIDAD PROM.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${String.format(Locale.US, "%.1f", margenPromedio)}%", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
                Text("Cálculo basado en configuración de compra/venta", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun CardProductoFinanzas(prod: Map<String, Any>, formato: NumberFormat, minMargen: Double, maxMargen: Double) {
    val nombre = prod["nombre"] as? String ?: "Producto"
    val pVenta = prod["precio"]?.toString()?.toDoubleOrNull() ?: 0.0
    val pCompra = prod["precioCompra"]?.toString()?.toDoubleOrNull() ?: 0.0
    val gBolsa = prod["cantidadUnitario"]?.toString()?.toDoubleOrNull() ?: 0.0
    val display = prod["unidadesPorDisplay"]?.toString()?.toDoubleOrNull() ?: 0.0
    val gVenta = prod["gramosVenta"]?.toString()?.toDoubleOrNull() ?: 0.0
    val costoPz = if (gBolsa > 0) (pCompra / gBolsa) * gVenta else 0.0
    val utilidad = pVenta - costoPz; val margen = if (pVenta > 0) (utilidad / pVenta) * 100 else 0.0
    val colorMargen = if (maxMargen == minMargen) DelisaGreen else {
        val ratio = (margen - minMargen) / (maxMargen - minMargen)
        when { ratio >= 0.75 -> DelisaGreenDark; ratio >= 0.50 -> DelisaGreen; ratio >= 0.25 -> WarningOrange; else -> ErrorRed }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(3.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colorMargen.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Inventory2, null, tint = colorMargen, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(nombre, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("Costo Display: ${formato.format(pCompra)} ($display pzas)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Surface(color = colorMargen, shape = RoundedCornerShape(8.dp)) { Text("${String.format(Locale.US, "%.0f", margen)}%", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.height(16.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp); Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { InfoFinanzasItem("COSTO UNIT.", formato.format(costoPz), MaterialTheme.colorScheme.onSurface); InfoFinanzasItem("PRECIO VENTA", formato.format(pVenta), MaterialTheme.colorScheme.onSurface); InfoFinanzasItem("UTILIDAD PZ", formato.format(utilidad), colorMargen) }
            if (display > 0 && gBolsa > 0 && gVenta > 0) Text("* Rendimiento: aprox. ${String.format(Locale.US, "%.0f", (display * gBolsa) / gVenta)} bolsitas por display", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable fun InfoFinanzasItem(l: String, v: String, c: Color) { Column { Text(l, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(2.dp)); Text(v, fontSize = 15.sp, fontWeight = FontWeight.Black, color = c) } }

fun calcularMargen(prod: Map<String, Any>): Double {
    val pV = prod["precio"]?.toString()?.toDoubleOrNull() ?: 0.0; val pC = prod["precioCompra"]?.toString()?.toDoubleOrNull() ?: 0.0
    val gB = prod["cantidadUnitario"]?.toString()?.toDoubleOrNull() ?: 0.0; val gV = prod["gramosVenta"]?.toString()?.toDoubleOrNull() ?: 0.0
    val cP = if (gB > 0) (pC / gB) * gV else 0.0; return if (pV > 0) ((pV - cP) / pV) * 100 else 0.0
}

fun generarPdfInventarioGlobal(context: Context, productos: List<Map<String, Any>>, stockGlobal: Map<String, Int>): File {
    val pdfDocument = PdfDocument(); val pageWidth = 612; val pageHeight = 792; val nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX")); val fechaReporte = df.format(Date())
    val pDelisaRed = Paint().apply { color = android.graphics.Color.rgb(227, 6, 19); style = Paint.Style.FILL; isAntiAlias = true }
    val pZebra = Paint().apply { color = android.graphics.Color.rgb(248, 249, 250); style = Paint.Style.FILL }
    val pTitle = Paint().apply { textSize = 20f; color = android.graphics.Color.WHITE; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pSubTitle = Paint().apply { textSize = 9f; color = android.graphics.Color.rgb(220, 220, 220); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); isAntiAlias = true }
    val pBold = Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pText = Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); isAntiAlias = true }
    val pLine = Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 0.5f; style = Paint.Style.STROKE; isAntiAlias = true }

    var currentPageNumber = 1; var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
    var page = pdfDocument.startPage(pageInfo); var canvas = page.canvas

    val itemsCalculados = productos.map { prod ->
        val id = prod["id"] as? String ?: ""
        val productoIdField = prod["productoId"] as? String ?: ""
        val stock = stockGlobal[id] ?: stockGlobal[productoIdField] ?: 0
        val pVenta = prod["precio"]?.toString()?.toDoubleOrNull() ?: 0.0
        Triple(prod, stock, stock * pVenta)
    }.filter { it.second > 0 }.sortedByDescending { it.third }

    val totalValorVenta = itemsCalculados.sumOf { it.third }; val totalProductos = itemsCalculados.sumOf { it.second }; val totalSKUs = itemsCalculados.size
    val totalPages = if (itemsCalculados.size <= 24) 1 else 1 + Math.ceil((itemsCalculados.size - 24).toDouble() / 31).toInt()

    fun drawHeaderHUD(canv: android.graphics.Canvas, pNum: Int) {
        if (pNum == 1) {
            val headerH = 90f; val gradient = LinearGradient(0f, 0f, 0f, headerH, android.graphics.Color.rgb(227, 6, 19), android.graphics.Color.rgb(150, 0, 10), Shader.TileMode.CLAMP)
            canv.drawRect(0f, 0f, pageWidth.toFloat(), headerH, Paint().apply { shader = gradient; isAntiAlias = true })
            canv.drawLine(0f, headerH, pageWidth.toFloat(), headerH, Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 1.2f })
            canv.drawText("REPORTE DE INVENTARIO GLOBAL", 40f, 35f, pTitle)
            canv.drawText("DELISA BOTANAS | VALORIZACIÓN ESTRATÉGICA DE STOCK", 40f, 55f, pSubTitle); canv.drawText("GENERADO: $fechaReporte", 40f, 75f, pSubTitle)
            pBold.color = android.graphics.Color.WHITE; pBold.textSize = 8f; pBold.textAlign = Paint.Align.RIGHT
            canv.drawText("HOJA $pNum DE $totalPages", pageWidth - 40f, 35f, pBold); pBold.textAlign = Paint.Align.LEFT; pBold.color = android.graphics.Color.BLACK; pBold.textSize = 10f
            val logo = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, com.gruposanangel.delivery.R.drawable.logo)
            logo?.let { it.setBounds(pageWidth - 140, 45, pageWidth - 40, 85); it.draw(canv) }
        } else {
            pSubTitle.color = android.graphics.Color.GRAY; pSubTitle.textAlign = Paint.Align.RIGHT; canv.drawText("HOJA $pNum DE $totalPages", pageWidth - 40f, 30f, pSubTitle)
            pSubTitle.textAlign = Paint.Align.LEFT; canv.drawLine(40f, 35f, pageWidth - 40f, 35f, pLine)
        }
    }
    drawHeaderHUD(canvas, currentPageNumber); var y = 120f
    fun drawKPICard(canv: android.graphics.Canvas, label: String, value: String, x: Float, w: Float, colorInt: Int = android.graphics.Color.BLACK) {
        val rect = RectF(x, y, x + w, y + 45f); canv.drawRoundRect(rect, 8f, 8f, Paint().apply { this.color = android.graphics.Color.rgb(245, 245, 245); style = Paint.Style.FILL })
        canv.drawRoundRect(rect, 8f, 8f, Paint().apply { this.color = android.graphics.Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f })
        pSubTitle.color = android.graphics.Color.GRAY; pSubTitle.textAlign = Paint.Align.CENTER; canv.drawText(label.uppercase(), x + w/2, y + 15f, pSubTitle)
        pBold.textSize = 12f; pBold.textAlign = Paint.Align.CENTER; pBold.color = colorInt; canv.drawText(value, x + w/2, y + 35f, pBold); pBold.textAlign = Paint.Align.LEFT; pBold.textSize = 10f; pBold.color = android.graphics.Color.BLACK
    }
    val kpiW = (pageWidth - 100f) / 3f
    drawKPICard(canvas, "Valor Total Mercado", nf.format(totalValorVenta), 40f, kpiW, android.graphics.Color.rgb(46, 125, 50))
    drawKPICard(canvas, "Total de Piezas", "$totalProductos uds", 40f + kpiW + 10f, kpiW); drawKPICard(canvas, "SKUs Activos", "$totalSKUs productos", 40f + 2*(kpiW + 10f), kpiW); y += 75f

    fun drawTableHeader(canv: android.graphics.Canvas, curY: Float) {
        canv.drawRect(40f, curY, pageWidth - 40f, curY + 22f, pDelisaRed); pBold.color = android.graphics.Color.WHITE
        canv.drawText("PRODUCTO / DESCRIPCIÓN", 45f, curY + 15f, pBold); canv.drawText("STOCK", 320f, curY + 15f, pBold); canv.drawText("P. VENTA", 400f, curY + 15f, pBold); canv.drawText("VALOR TOTAL", 490f, curY + 15f, pBold); pBold.color = android.graphics.Color.BLACK
    }
    drawTableHeader(canvas, y); y += 22f

    itemsCalculados.forEachIndexed { index, (prod, stock, valorStock) ->
        val rowH = 20f
        if (y > pageHeight - 80f) {
            pdfDocument.finishPage(page); currentPageNumber++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); page = pdfDocument.startPage(pageInfo); canvas = page.canvas
            drawHeaderHUD(canvas, currentPageNumber); y = 62f; drawTableHeader(canvas, y); y += 22f
        }
        if (index % 2 == 0) canvas.drawRect(40f, y, pageWidth - 40f, y + rowH, pZebra)
        val nombre = prod["nombre"] as? String ?: "Sin Nombre"; val pVenta = prod["precio"]?.toString()?.toDoubleOrNull() ?: 0.0
        canvas.drawText(nombre.take(45), 45f, y + 14f, pText); canvas.drawText("$stock", 320f, y + 14f, pText); canvas.drawText(nf.format(pVenta), 400f, y + 14f, pText); canvas.drawText(nf.format(valorStock), 490f, y + 14f, pBold); y += 20f; canvas.drawLine(40f, y, pageWidth - 40f, y, pLine)
    }

    val footerY = pageHeight - 60f
    if (y > footerY - 40f) { pdfDocument.finishPage(page); currentPageNumber++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; drawHeaderHUD(canvas, currentPageNumber) }
    canvas.drawLine(40f, footerY, pageWidth - 40f, footerY, pLine)
    try {
        val qrContent = "DELISA_INV|VAL:${totalValorVenta}|QTY:${totalProductos}|DATE:${fechaReporte}"
        val bitMatrix = QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 150, 150); val qrBitmap = Bitmap.createBitmap(150, 150, Bitmap.Config.RGB_565)
        for (i in 0 until 150) for (j in 0 until 150) qrBitmap.setPixel(i, j, if (bitMatrix.get(i, j)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        canvas.drawBitmap(qrBitmap, null, RectF(40f, footerY + 5f, 85f, footerY + 50f), null)
    } catch (e: Exception) { }
    pSubTitle.color = android.graphics.Color.GRAY; pSubTitle.textAlign = Paint.Align.RIGHT; canvas.drawText("SISTEMA DE GESTIÓN DELISA BOTANAS - CONTROL FINANCIERO", pageWidth - 40f, footerY + 20f, pSubTitle); canvas.drawText("ESTE DOCUMENTO ES UNA VALORIZACIÓN DE ACTIVOS AL CORTE.", pageWidth - 40f, footerY + 35f, pSubTitle)
    pdfDocument.finishPage(page); val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Inventario_Global_${System.currentTimeMillis()}.pdf"); pdfDocument.writeTo(FileOutputStream(file)); pdfDocument.close(); return file
}

fun generarPdfVentasPeriodo(context: Context, ventas: List<ProdVendidoReporte>, inicio: Date, fin: Date): File {
    val pdfDocument = PdfDocument(); val pageWidth = 612; val pageHeight = 792; val nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    val df = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-MX")); val dfConHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX")); val fechaReporte = dfConHora.format(Date())
    val rangoTexto = if (df.format(inicio) == df.format(fin)) df.format(inicio) else "${df.format(inicio)} AL ${df.format(fin)}"
    val pSuccessGreen = Paint().apply { color = android.graphics.Color.rgb(46, 125, 50); style = Paint.Style.FILL; isAntiAlias = true }
    val pZebra = Paint().apply { color = android.graphics.Color.rgb(248, 249, 250); style = Paint.Style.FILL }
    val pTitle = Paint().apply { textSize = 20f; color = android.graphics.Color.WHITE; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pSubTitle = Paint().apply { textSize = 9f; color = android.graphics.Color.rgb(220, 220, 220); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); isAntiAlias = true }
    val pBold = Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pText = Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); isAntiAlias = true }
    val pLine = Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 0.5f; style = Paint.Style.STROKE; isAntiAlias = true }

    var currentPageNumber = 1; var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); var page = pdfDocument.startPage(pageInfo); var canvas = page.canvas
    val granTotalVentas = ventas.sumOf { it.total }; val granTotalPiezas = ventas.sumOf { it.cantidad }; val ticketPromedio = if (ventas.isNotEmpty()) granTotalVentas / ventas.size else 0.0
    val totalPages = if (ventas.size <= 25) 1 else 1 + Math.ceil((ventas.size - 25).toDouble() / 31).toInt()

    fun drawHeaderHUD(canv: android.graphics.Canvas, pNum: Int) {
        if (pNum == 1) {
            val headerH = 100f; val gradient = LinearGradient(0f, 0f, 0f, headerH, android.graphics.Color.rgb(46, 125, 50), android.graphics.Color.rgb(27, 94, 32), Shader.TileMode.CLAMP)
            canv.drawRect(0f, 0f, pageWidth.toFloat(), headerH, Paint().apply { shader = gradient; isAntiAlias = true })
            canv.drawLine(0f, headerH, pageWidth.toFloat(), headerH, Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 1.2f })
            canv.drawText("REPORTE EJECUTIVO DE VENTAS", 40f, 35f, pTitle); canv.drawText("PERIODO: $rangoTexto", 40f, 55f, pSubTitle); canv.drawText("DELISA BOTANAS | ANÁLISIS DE PARTICIPACIÓN DE MERCADO", 40f, 75f, pSubTitle)
            pBold.color = android.graphics.Color.WHITE; pBold.textSize = 8f; pBold.textAlign = Paint.Align.RIGHT
            canv.drawText("HOJA $pNum DE $totalPages", pageWidth - 40f, 35f, pBold); pBold.textAlign = Paint.Align.LEFT; pBold.color = android.graphics.Color.BLACK; pBold.textSize = 10f
            val logo = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, com.gruposanangel.delivery.R.drawable.logo)
            logo?.let { it.setBounds(pageWidth - 140, 40, pageWidth - 40, 90); it.draw(canv) }
        } else {
            pSubTitle.color = android.graphics.Color.GRAY; pSubTitle.textAlign = Paint.Align.RIGHT; canv.drawText("HOJA $pNum DE $totalPages", pageWidth - 40f, 30f, pSubTitle); pSubTitle.textAlign = Paint.Align.LEFT; canv.drawLine(40f, 35f, pageWidth - 40f, 35f, pLine)
        }
    }
    drawHeaderHUD(canvas, currentPageNumber); var y = 130f
    fun drawKPICard(canv: android.graphics.Canvas, label: String, value: String, x: Float, w: Float, colorInt: Int = android.graphics.Color.BLACK) {
        val rect = RectF(x, y, x + w, y + 45f); canv.drawRoundRect(rect, 8f, 8f, Paint().apply { this.color = android.graphics.Color.rgb(245, 245, 245); style = Paint.Style.FILL })
        canv.drawRoundRect(rect, 8f, 8f, Paint().apply { this.color = android.graphics.Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f })
        pSubTitle.color = android.graphics.Color.GRAY; pSubTitle.textAlign = Paint.Align.CENTER; canv.drawText(label.uppercase(), x + w/2, y + 15f, pSubTitle)
        pBold.textSize = 12f; pBold.textAlign = Paint.Align.CENTER; pBold.color = colorInt; canv.drawText(value, x + w/2, y + 35f, pBold); pBold.textAlign = Paint.Align.LEFT; pBold.textSize = 10f; pBold.color = android.graphics.Color.BLACK
    }
    val kpiW = (pageWidth - 110f) / 3f
    drawKPICard(canvas, "Ingresos Totales", nf.format(granTotalVentas), 40f, kpiW, android.graphics.Color.rgb(46, 125, 50))
    drawKPICard(canvas, "Piezas Vendidas", "$granTotalPiezas uds", 40f + kpiW + 15f, kpiW); drawKPICard(canvas, "Ticket Prom.", nf.format(ticketPromedio), 40f + 2*(kpiW + 15f), kpiW); y += 80f

    fun drawTableHeader(canv: android.graphics.Canvas, curY: Float) {
        canv.drawRect(40f, curY, pageWidth - 40f, curY + 22f, pSuccessGreen); pBold.color = android.graphics.Color.WHITE
        canv.drawText("PRODUCTO", 45f, curY + 15f, pBold); canv.drawText("CANT.", 280f, curY + 15f, pBold); canv.drawText("P. PROM.", 350f, curY + 15f, pBold); canv.drawText("INGRESOS", 440f, curY + 15f, pBold); canv.drawText("% PART.", 525f, curY + 15f, pBold); pBold.color = android.graphics.Color.BLACK
    }
    drawTableHeader(canvas, y); y += 22f

    ventas.sortedByDescending { it.total }.forEachIndexed { index, v ->
        val rowH = 20f
        if (y > pageHeight - 80f) { pdfDocument.finishPage(page); currentPageNumber++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; drawHeaderHUD(canvas, currentPageNumber); y = 62f; drawTableHeader(canvas, y); y += 22f }
        if (index % 2 == 0) canvas.drawRect(40f, y, pageWidth - 40f, y + rowH, pZebra)
        val pProm = if (v.cantidad > 0) v.total / v.cantidad else 0.0; val pPart = if (granTotalVentas > 0) (v.total / granTotalVentas) * 100 else 0.0
        canvas.drawText(v.nombre.take(35), 45f, y + 14f, pText); canvas.drawText("${v.cantidad}", 280f, y + 14f, pText); canvas.drawText(nf.format(pProm), 350f, y + 14f, pText); canvas.drawText(nf.format(v.total), 440f, y + 14f, pBold); canvas.drawText("${String.format(Locale.US, "%.1f", pPart)}%", 525f, y + 14f, pText); y += 20f; canvas.drawLine(40f, y, pageWidth - 40f, y, pLine)
    }

    val footerY = pageHeight - 60f
    if (y > footerY - 40f) { pdfDocument.finishPage(page); currentPageNumber++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; drawHeaderHUD(canvas, currentPageNumber) }
    canvas.drawLine(40f, footerY, pageWidth - 40f, footerY, pLine)
    try {
        val qrContent = "DELISA_SALES|PER:${rangoTexto}|TOT:${granTotalVentas}|DATE:${fechaReporte}"
        val bitMatrix = QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 150, 150); val qrBitmap = Bitmap.createBitmap(150, 150, Bitmap.Config.RGB_565)
        for (i in 0 until 150) for (j in 0 until 150) qrBitmap.setPixel(i, j, if (bitMatrix.get(i, j)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        canvas.drawBitmap(qrBitmap, null, RectF(40f, footerY + 5f, 85f, footerY + 50f), null)
    } catch (e: Exception) { }
    pSubTitle.color = android.graphics.Color.GRAY; pSubTitle.textAlign = Paint.Align.RIGHT; canvas.drawText("DOCUMENTO GENERADO POR SISTEMA DE INTELIGENCIA COMERCIAL DELISA", pageWidth - 40f, footerY + 20f, pSubTitle); canvas.drawText("PROPIEDAD PRIVADA - GRUPO SAN ANGEL", pageWidth - 40f, footerY + 35f, pSubTitle)
    pdfDocument.finishPage(page); val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Ventas_Periodo_${System.currentTimeMillis()}.pdf"); pdfDocument.writeTo(FileOutputStream(file)); pdfDocument.close(); return file
}

fun generarPdfSugerenciaCompra(context: Context, plan: List<SugerenciaCompra>, presupuesto: Double, productosCatalogo: List<Map<String, Any>>): File {
    val pdfDocument = PdfDocument(); val pageWidth = 612; val pageHeight = 792; val nf = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX")); val fechaReporte = df.format(Date())
    val pInfoBlue = Paint().apply { color = android.graphics.Color.rgb(0, 100, 200); style = Paint.Style.FILL; isAntiAlias = true }
    val pZebra = Paint().apply { color = android.graphics.Color.rgb(248, 249, 250); style = Paint.Style.FILL }
    val pTitle = Paint().apply { textSize = 20f; color = android.graphics.Color.WHITE; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pSubTitle = Paint().apply { textSize = 9f; color = android.graphics.Color.rgb(220, 220, 220); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); isAntiAlias = true }
    val pBold = Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); isAntiAlias = true }
    val pText = Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); isAntiAlias = true }
    val pLine = Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 0.5f; style = Paint.Style.STROKE; isAntiAlias = true }

    var currentPageNumber = 1; var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); var page = pdfDocument.startPage(pageInfo); var canvas = page.canvas
    val totalInversionFinal = plan.sumOf { it.inversion }; val totalCajas = plan.sumOf { it.cajasAPedir }; val pesoTotalCompra = plan.sumOf { it.pesoCajaKilos * it.cajasAPedir }; val coberturaPromedio = if (plan.isNotEmpty()) plan.map { it.diasCobertura }.average() else 0.0
    val totalPages = if (plan.size <= 25) 1 else 1 + Math.ceil((plan.size - 25).toDouble() / 32).toInt()

    fun drawHeaderHUD(canv: android.graphics.Canvas, pNum: Int, colorHUD: Int = android.graphics.Color.rgb(0, 100, 200), titulo: String = "PLAN ESTRATÉGICO DE COMPRAS") {
        if (pNum == 1) {
            val headerH = 100f; val gradient = LinearGradient(0f, 0f, 0f, headerH, colorHUD, android.graphics.Color.BLACK, Shader.TileMode.CLAMP)
            canv.drawRect(0f, 0f, pageWidth.toFloat(), headerH, Paint().apply { shader = gradient; isAntiAlias = true })
            canv.drawLine(0f, headerH, pageWidth.toFloat(), headerH, Paint().apply { color = android.graphics.Color.BLACK; strokeWidth = 1.2f })
            canv.drawText(titulo, 40f, 35f, pTitle); 
            pSubTitle.color = android.graphics.Color.rgb(220, 220, 220); pSubTitle.textAlign = Paint.Align.LEFT
            canv.drawText("ASISTENTE DE INVERSIÓN INTELIGENTE | OPTIMIZACIÓN DE CAPITAL", 40f, 55f, pSubTitle); canv.drawText("GENERADO: $fechaReporte", 40f, 75f, pSubTitle)
            pBold.color = android.graphics.Color.WHITE; pBold.textSize = 8f; pBold.textAlign = Paint.Align.RIGHT
            canv.drawText("HOJA $pNum", pageWidth - 40f, 35f, pBold); pBold.textAlign = Paint.Align.LEFT; pBold.color = android.graphics.Color.BLACK; pBold.textSize = 10f
            val logo = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, com.gruposanangel.delivery.R.drawable.logo)
            logo?.let { it.setBounds(pageWidth - 140, 40, pageWidth - 40, 90); it.draw(canv) }
        } else {
            pSubTitle.color = android.graphics.Color.GRAY; pSubTitle.textAlign = Paint.Align.RIGHT; canv.drawText("HOJA $pNum", pageWidth - 40f, 30f, pSubTitle); pSubTitle.textAlign = Paint.Align.LEFT; canv.drawLine(40f, 35f, pageWidth - 40f, 35f, pLine)
        }
    }
    drawHeaderHUD(canvas, currentPageNumber); var y = 130f
    fun drawKPICard(canv: android.graphics.Canvas, label: String, value: String, x: Float, w: Float, colorInt: Int = android.graphics.Color.BLACK) {
        val rect = RectF(x, y, x + w, y + 45f); canv.drawRoundRect(rect, 8f, 8f, Paint().apply { this.color = android.graphics.Color.rgb(245, 245, 245); style = Paint.Style.FILL })
        canv.drawRoundRect(rect, 8f, 8f, Paint().apply { this.color = android.graphics.Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.5f })
        pSubTitle.color = android.graphics.Color.GRAY; pSubTitle.textAlign = Paint.Align.CENTER; canv.drawText(label.uppercase(), x + w/2, y + 15f, pSubTitle)
        pBold.textSize = 12f; pBold.textAlign = Paint.Align.CENTER; pBold.color = colorInt; canv.drawText(value, x + w/2, y + 35f, pBold); pBold.textAlign = Paint.Align.LEFT; pBold.textSize = 10f; pBold.color = android.graphics.Color.BLACK
    }
    val kpiW = (pageWidth - 120f) / 4f
    drawKPICard(canvas, "Presupuesto Utilizado", nf.format(totalInversionFinal), 40f, kpiW, android.graphics.Color.rgb(0, 120, 215))
    drawKPICard(canvas, "Cajas a Solicitar", "$totalCajas cajas", 40f + kpiW + 15f, kpiW); drawKPICard(canvas, "Peso Total Compra", "${String.format(Locale.US, "%.1f", pesoTotalCompra)} kg", 40f + 2*(kpiW + 15f), kpiW); drawKPICard(canvas, "Cobertura Proy.", "${String.format(Locale.US, "%.1f", coberturaPromedio)} días", 40f + 3*(kpiW + 15f), kpiW); y += 80f

    fun drawTableHeader(canv: android.graphics.Canvas, curY: Float) {
        canv.drawRect(40f, curY, pageWidth - 40f, curY + 22f, pInfoBlue); pBold.color = android.graphics.Color.WHITE
        canv.drawText("PRODUCTO", 45f, curY + 15f, pBold); pBold.textAlign = Paint.Align.CENTER; canv.drawText("CAJAS", 210f, curY + 15f, pBold); pBold.textAlign = Paint.Align.LEFT; canv.drawText("PESO/C", 250f, curY + 15f, pBold); canv.drawText("COSTO/C", 320f, curY + 15f, pBold); canv.drawText("INVERSIÓN", 415f, curY + 15f, pBold); canv.drawText("COB. (DÍAS)", 515f, curY + 15f, pBold); pBold.color = android.graphics.Color.BLACK
    }
    drawTableHeader(canvas, y); y += 22f
    plan.forEachIndexed { index, item ->
        val rowH = 26f
        if (y > pageHeight - 80f) { pdfDocument.finishPage(page); currentPageNumber++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; drawHeaderHUD(canvas, currentPageNumber); y = 62f; drawTableHeader(canvas, y); y += 22f }
        if (index % 2 == 0) canvas.drawRect(40f, y, pageWidth - 40f, y + rowH, pZebra)
        canvas.drawText(item.nombre.take(20), 45f, y + 14f, pBold)
        if (item.pctParaMixes > 0) {
            val pSmall = Paint(pText).apply { textSize = 7f; color = android.graphics.Color.GRAY; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC) }
            canvas.drawText("USO: ${String.format(Locale.US, "%.0f", item.pctVentaDirecta)}% Venta Directa | ${String.format(Locale.US, "%.0f", item.pctParaMixes)}% Producción Mixes", 45f, y + 23f, pSmall)
        }
        pText.textAlign = Paint.Align.CENTER; canvas.drawText("${item.cajasAPedir}", 210f, y + 16f, pText); pText.textAlign = Paint.Align.LEFT
        canvas.drawText("${String.format(Locale.US, "%.1f", item.pesoCajaKilos)}kg", 250f, y + 16f, pText); canvas.drawText(nf.format(item.costoCaja), 320f, y + 16f, pText); canvas.drawText(nf.format(item.inversion), 415f, y + 16f, pBold); 
        val textCob = if (item.diasCobertura < 0) "Sin mov." else "${String.format(Locale.US, "%.0f", item.diasCobertura)} d"
        canvas.drawText(textCob, 515f, y + 16f, pText); y += rowH; canvas.drawLine(40f, y, pageWidth - 40f, y, pLine)
    }

    val footerY = pageHeight - 60f
    if (y > footerY - 40f) { pdfDocument.finishPage(page); currentPageNumber++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; drawHeaderHUD(canvas, currentPageNumber) }
    canvas.drawLine(40f, footerY, pageWidth - 40f, footerY, pLine)
    try {
        val qrContent = "DELISA_BUY|BUDGET:${presupuesto}|INVEST:${totalInversionFinal}|DATE:${fechaReporte}"
        val bitMatrix = QRCodeWriter().encode(qrContent, BarcodeFormat.QR_CODE, 150, 150); val qrBitmap = Bitmap.createBitmap(150, 150, Bitmap.Config.RGB_565)
        for (i in 0 until 150) for (j in 0 until 150) qrBitmap.setPixel(i, j, if (bitMatrix.get(i, j)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        canvas.drawBitmap(qrBitmap, null, RectF(40f, footerY + 5f, 85f, footerY + 50f), null)
    } catch (e: Exception) { }
    pSubTitle.color = android.graphics.Color.GRAY; pSubTitle.textAlign = Paint.Align.RIGHT; canvas.drawText("SISTEMA DE ASISTENCIA DE COMPRAS DELISA - PROPIEDAD PRIVADA", pageWidth - 40f, footerY + 20f, pSubTitle); canvas.drawText("EL PLAN PRIORIZA PRODUCTOS CON MAYOR VELOCIDAD DE VENTA.", pageWidth - 40f, footerY + 35f, pSubTitle)
    pdfDocument.finishPage(page)

    // --- SECCIÓN 2: PLAN DE PRODUCCIÓN (COLOR NARANJA) ---
    currentPageNumber = 1; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); page = pdfDocument.startPage(pageInfo); canvas = page.canvas
    val colorOrange = android.graphics.Color.rgb(255, 100, 0); drawHeaderHUD(canvas, currentPageNumber, colorOrange, "PLAN OPERATIVO DE PRODUCCIÓN"); y = 130f
    
    // Calculamos el total de piezas a fabricar
    val totalPiezasAFabricar = plan.sumOf { it.aFabricar }
    drawKPICard(canvas, "Piezas a Fabricar", "$totalPiezasAFabricar uds", 40f, (pageWidth - 100f)/3f, colorOrange)

    fun drawProdTableHeader(canv: android.graphics.Canvas, curY: Float) {
        canv.drawRect(40f, curY, pageWidth - 40f, curY + 22f, Paint().apply { color = colorOrange; isAntiAlias = true }); pBold.color = android.graphics.Color.WHITE
        canv.drawText("PRODUCTO", 45f, curY + 15f, pBold); 
        canv.drawText("STOCK", 215f, curY + 15f, pBold); 
        canv.drawText("COMPRA KG", 285f, curY + 15f, pBold); 
        canv.drawText("A FABRICAR", 375f, curY + 15f, pBold); 
        canv.drawText("TOTAL", 465f, curY + 15f, pBold); 
        canv.drawText("COB.", 525f, curY + 15f, pBold);
        pBold.color = android.graphics.Color.BLACK
    }
    drawProdTableHeader(canvas, y); y += 22f
    
    // Ordenamos para que salgan los mixes primero o destacados
    plan.sortedByDescending { it.esCompuesto }.forEachIndexed { index, item ->
        val prodOrig = productosCatalogo.find { it["id"] == item.id }
        val isMix = item.esCompuesto
        
        // --- LÓGICA DE FILA ---
        val stockAnt = item.stockInicialBolsitas
        val aFab = item.aFabricar
        val compraKgParaEstaFila = if (isMix) {
            val ingredientes = prodOrig?.get("ingredientes") as? List<Map<String, Any>>
            ingredientes?.sumOf { ing -> ((ing["gramos"] as? Number)?.toDouble() ?: 0.0) * aFab / 1000.0 } ?: 0.0
        } else {
            item.kgVentaDirecta
        }

        val rowH = if (isMix) 38f else 24f
        if (y > pageHeight - 80f) { pdfDocument.finishPage(page); currentPageNumber++; pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create(); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; drawHeaderHUD(canvas, currentPageNumber, colorOrange, "PLAN OPERATIVO DE PRODUCCIÓN"); y = 62f; drawProdTableHeader(canvas, y); y += 22f }
        
        if (index % 2 == 0) canvas.drawRect(40f, y, pageWidth - 40f, y + rowH, pZebra)
        
        canvas.drawText(item.nombre.take(22), 45f, y + 14f, pBold)
        canvas.drawText("${stockAnt}", 215f, y + 14f, pText)
        canvas.drawText("${String.format(Locale.US, "%.1f", compraKgParaEstaFila)}kg", 285f, y + 14f, pText)
        canvas.drawText("${aFab}", 375f, y + 14f, pBold)
        canvas.drawText("${stockAnt + aFab}", 465f, y + 14f, pText)
        val textCobProd = if (item.diasCobertura < 0) "N/A" else "${item.diasCobertura.toInt()}d"
        canvas.drawText(textCobProd, 525f, y + 14f, pText)

        if (isMix) {
            val ingredientes = prodOrig?.get("ingredientes") as? List<Map<String, Any>>
            val pSmall = Paint(pText).apply { textSize = 7f; color = android.graphics.Color.GRAY; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC) }
            val breakdown = ingredientes?.joinToString(" | ") { ing -> 
                val kgIng = ((ing["gramos"] as? Number)?.toDouble() ?: 0.0) * aFab / 1000.0
                "${ing["nombre"]}: ${String.format(Locale.US, "%.1f", kgIng)}kg" 
            } ?: ""
            canvas.drawText("RECETA REQUERIDA: $breakdown", 45f, y + 28f, pSmall)
        }
        
        y += rowH
        canvas.drawLine(40f, y, pageWidth - 40f, y, pLine)
    }

    canvas.drawLine(40f, pageHeight - 60f, pageWidth - 40f, pageHeight - 60f, pLine); pSubTitle.color = android.graphics.Color.GRAY; pSubTitle.textAlign = Paint.Align.RIGHT; canvas.drawText("PLAN COORDINADO DE COMPRA Y MANUFACTURA DELISA", pageWidth - 40f, pageHeight - 40f, pSubTitle)
    pdfDocument.finishPage(page); val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Plan_Estrategico_${System.currentTimeMillis()}.pdf"); pdfDocument.writeTo(FileOutputStream(file)); pdfDocument.close(); return file
}

fun abrirPdfResumen(context: Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file); val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(uri, "application/pdf"); addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(android.content.Intent.createChooser(intent, "Abrir Reporte"))
    } catch (e: Exception) { Toast.makeText(context, "No se pudo abrir el PDF", Toast.LENGTH_SHORT).show() }
}
