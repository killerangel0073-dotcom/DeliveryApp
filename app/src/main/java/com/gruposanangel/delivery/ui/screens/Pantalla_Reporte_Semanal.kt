@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.gruposanangel.delivery.ui.screens

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.RepositoryGasto
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.data.VentaDetalleEntity
import com.gruposanangel.delivery.utilidades.ReporteAnaliticasPdf
import com.gruposanangel.delivery.ui.theme.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.isSystemInDarkTheme

data class DiaReporte(
    val nombre: String,
    val fecha: Long,
    val totalVenta: Double,
    val totalPiezas: Int,
    val clientesAtendidos: Int,
    val totalGastos: Double = 0.0,
    val ventas: List<VentaEntity> = emptyList()
)

data class ReporteSemanalUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val totalVentaBrutaSemana: Double = 0.0,
    val totalGastosSemana: Double = 0.0,
    val totalSemanaNeta: Double = 0.0,
    val totalPiezasSemana: Int = 0,
    val dias: List<DiaReporte> = emptyList(),
    val todosLosGastosSemana: List<Gasto> = emptyList(),
    val nombreVendedor: String = "",
    val rutaNombre: String = "",
    val metaSemanal: Double = 70000.0,
    val fechaInicioSemana: Long = 0L,

    // 🔥 FILTROS DE PERFIL
    val perfilesDisponibles: List<com.gruposanangel.delivery.data.PerfilVenta> = emptyList(),
    val perfilSeleccionado: com.gruposanangel.delivery.data.PerfilVenta? = null
)

class ReporteSemanalViewModel(
    private val ventaRepository: VentaRepository,
    private val usuarioRepository: RepositoryUsuario,
    private val inventarioRepository: RepositoryInventario,
    private val gastoRepository: RepositoryGasto,
    private val userId: String,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReporteSemanalUiState())
    val uiState: StateFlow<ReporteSemanalUiState> = _uiState.asStateFlow()

    private val _fechaFiltro = MutableStateFlow<Long?>(null)
    private val _perfilFiltro = MutableStateFlow<com.gruposanangel.delivery.data.PerfilVenta?>(null)
    private val prefs = context.getSharedPreferences("reporte_semanal_prefs", Context.MODE_PRIVATE)

    init {
        val metaGuardada = prefs.getFloat("meta_semanal_$userId", 70000f).toDouble()
        _uiState.update { it.copy(metaSemanal = metaGuardada) }

        // 🚀 LÓGICA HÍBRIDA MEJORADA: Atribución por Ruta + Caché Local
        combine(_fechaFiltro.filterNotNull(), _perfilFiltro) { fecha, perfil ->
            fecha to perfil
        }.flatMapLatest { (inicioMs, perfilActivo) ->
            val finMs = inicioMs + (7L * 24 * 60 * 60 * 1000) - 1
            _uiState.update { it.copy(isLoading = true, error = null, fechaInicioSemana = inicioMs) }

            flow {
                try {
                    val dbFs = FirebaseFirestore.getInstance()
                    
                    // 1. Identificar la Unidad Operativa (Ruta/Almacén)
                    val userDoc = dbFs.collection("users").document(userId).get().await()
                    val rutaRef = userDoc.getDocumentReference("rutaAsignada")
                    val rutaDoc = rutaRef?.get()?.await()
                    
                    val almid = rutaDoc?.getDocumentReference("almacenAsignado")?.id ?: userDoc.getString("ultimoAlmacenNombre") ?: ""
                    val rId = rutaDoc?.getString("nombre") ?: userDoc.getString("ultimaRutaNombre") ?: ""
                    val rIdReal = rutaRef?.id ?: userDoc.getString("ultimaRutaId") ?: ""
                    
                    // 2. Mapeo de Clientes (Crucial para ver ventas del vendedor anterior)
                    val clientesSnap = dbFs.collection("clientes").get().await()
                    val clientToRutaMap = clientesSnap.documents.associate { 
                        it.id to (it.getString("rutaId") ?: it.getString("id_ruta") ?: "") 
                    }

                    // 3. Descarga Inclusiva (Igual que Dashboard Admin pero por 7 días)
                    val qVentas = dbFs.collection("ventas")
                        .whereGreaterThanOrEqualTo("fecha", Timestamp(Date(inicioMs)))
                        .whereLessThanOrEqualTo("fecha", Timestamp(Date(finMs)))
                        .get().await()
                    
                    val entities = qVentas.documents.mapNotNull { doc ->
                        val vAlmId = doc.getString("almacenId") ?: doc.getString("almacenVendedorId") ?: ""
                        val vRutaId = doc.getString("rutaId") ?: ""
                        val vClienteId = doc.getString("clienteId") ?: ""
                        val vVendedorId = doc.getString("vendedorId") ?: ""

                        // 🔥 REGLA DE ATRIBUCIÓN: ¿Le pertenece a esta ruta?
                        val esDeEstaRuta = vAlmId == almid || vRutaId == rId || 
                                         clientToRutaMap[vClienteId] == rId || 
                                         (vVendedorId == userId && rId.isEmpty())

                        if (esDeEstaRuta) {
                            VentaEntity(
                                id = doc.id,
                                clienteId = vClienteId,
                                clienteNombre = doc.getString("clienteNombre") ?: "Cliente",
                                total = (doc.get("total") as? Number)?.toDouble() ?: 0.0,
                                metodoPago = doc.getString("metodoPago") ?: "Efectivo",
                                vendedorId = vVendedorId,
                                vendedorNombre = doc.getString("vendedorNombre"),
                                almacenId = almid, // 🛡️ Re-etiquetamos para el caché local
                                rutaId = rId,
                                rutaNombre = rId,
                                fecha = doc.getTimestamp("fecha")?.toDate()?.time ?: 0L,
                                horaDispositivo = doc.getLong("horaDispositivo") ?: 0L,
                                horaVerificada = doc.getLong("horaVerificada") ?: 0L,
                                alertaTiempo = doc.getBoolean("alertaTiempo") ?: false,
                                estado = doc.getString("estado") ?: "pagada",
                                sincronizado = true
                            )
                        } else null
                    }
                    
                    // Guardar en Room para que el Flow de abajo reaccione
                    ventaRepository.insertarVentas(entities)

                    // Sincronizar Gastos
                    gastoRepository.descargarGastosPeriodo(userId, inicioMs, finMs)

                    val result = mapOf(
                        "alm" to almid,
                        "nom" to rId,
                        "real" to rIdReal,
                        "vNom" to (userDoc.getString("nombre") ?: "Vendedor")
                    )
                    emit(result)
                } catch (e: Exception) {
                    Log.e("ReporteVM", "Error sync", e)
                    emit(emptyMap())
                }
            }.flatMapLatest { data ->
                val alm = data["alm"] ?: ""
                val nom = data["nom"] ?: ""
                val real = data["real"] ?: ""
                val vNom = data["vNom"] ?: ""

                _uiState.update { it.copy(nombreVendedor = vNom, rutaNombre = nom) }
                
                combine(
                    if (alm.isNotEmpty()) ventaRepository.obtenerVentasPorUnidadPeriodoFlow(alm, nom, real, inicioMs, finMs)
                    else ventaRepository.obtenerVentasPorPeriodoFlow(userId, inicioMs, finMs),
                    gastoRepository.obtenerGastosPorPeriodoFlow(userId, inicioMs, finMs),
                    inventarioRepository.obtenerProductosLocal()
                ) { ventas, gastos, catalog ->
                    val catalogMap = catalog.associate { it.productoId to (it.marca to it.categoria) }
                    procesarDatosFinal(inicioMs, ventas, gastos, perfilActivo, catalogMap)
                }
            }
        }.onEach { nuevoEstado ->
            _uiState.update { nuevoEstado.copy(isLoading = false) }
            
            // 🔥 Deep Sync de detalles (Productos) para la semana actual si faltan
            if (!nuevoEstado.isLoading) {
                viewModelScope.launch {
                    val firestore = FirebaseFirestore.getInstance()
                    nuevoEstado.dias.flatMap { it.ventas }.forEach { v ->
                        val localDetalles = ventaRepository.obtenerDetallesDeVenta(v.id)
                        if (localDetalles.isEmpty()) {
                            try {
                                var pSnap = firestore.collection("ventas").document(v.id).collection("productos").get().await()
                                if (pSnap.isEmpty) pSnap = firestore.collection("ventas").document(v.id).collection("detalles").get().await()
                                
                                val detEntities = pSnap.documents.map { p ->
                                    VentaDetalleEntity(
                                        ventaId = v.id,
                                        productoId = p.id.split("_")[0],
                                        nombre = p.getString("nombre") ?: "Producto",
                                        precio = (p.get("precio") as? Number)?.toDouble() ?: 0.0,
                                        cantidad = (p.getLong("cantidad") ?: 0L).toInt(),
                                        marca = p.getString("marca") ?: "Delisa",
                                        categoria = p.getString("categoria") ?: "General"
                                    )
                                }
                                ventaRepository.insertarDetalles(detEntities)
                            } catch (e: Exception) { }
                        }
                    }
                }
            }
        }.launchIn(viewModelScope)

        cambiarSemana(System.currentTimeMillis())
    }

    private suspend fun procesarDatosFinal(
        fechaLunes: Long,
        todasLasVentas: List<VentaEntity>,
        todosLosGastosLocal: List<com.gruposanangel.delivery.data.GastoEntity>,
        perfilActivo: com.gruposanangel.delivery.data.PerfilVenta?,
        catalogMap: Map<String, Pair<String, String>>
    ): ReporteSemanalUiState {
        val nombresDias = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        val diasReporte = mutableListOf<DiaReporte>()
        var totalSemanaBruta = 0.0
        var totalGastosSemana = 0.0
        var totalPiezasSemana = 0

        val cal = Calendar.getInstance().apply { timeInMillis = fechaLunes }
        val gastosMapeados = todosLosGastosLocal.map { Gasto(it.id, it.monto, it.categoria, it.descripcion, it.fecha, it.vendedorId, it.rutaNombre) }

        for (i in 0..6) {
            val inicioDia = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val finDia = cal.timeInMillis - 1

            val ventasDiaRaw = todasLasVentas.filter { it.fecha in inicioDia..finDia && it.estado != "CANCELADA" }
            var totalDiaFiltrado = 0.0
            var totalPiezasDiaFiltrado = 0
            val ventasDiaFiltradas = mutableListOf<VentaEntity>()

            for (v in ventasDiaRaw) {
                val detalles = ventaRepository.obtenerDetallesDeVenta(v.id)
                val detallesFiltrados = detalles.filter { d ->
                    if (perfilActivo == null || perfilActivo.id == "ALL") true
                    else {
                        val info = catalogMap[d.productoId]
                        val realM = if (d.marca == "Delisa" && d.categoria == "General") (info?.first ?: d.marca) else d.marca
                        val realC = if (d.marca == "Delisa" && d.categoria == "General") (info?.second ?: d.categoria) else d.categoria
                        perfilActivo.filtros.any { f ->
                            val mMatch = realM.trim().equals(f.marca.trim(), ignoreCase = true)
                            val cMatch = if (f.categorias.isNotEmpty()) f.categorias.any { it.trim().equals(realC.trim(), ignoreCase = true) } else true
                            mMatch && cMatch
                        }
                    }
                }

                if (detallesFiltrados.isNotEmpty() || (perfilActivo?.id == "ALL" || perfilActivo == null)) {
                    val montoTicket = if (perfilActivo == null || perfilActivo.id == "ALL") v.total else detallesFiltrados.sumOf { it.precio * it.cantidad }
                    val piezasTicket = detallesFiltrados.sumOf { it.cantidad }
                    totalDiaFiltrado += montoTicket
                    totalPiezasDiaFiltrado += piezasTicket
                    ventasDiaFiltradas.add(v)
                }
            }

            val gastosDia = gastosMapeados.filter { it.fecha in inicioDia..finDia }
            diasReporte.add(DiaReporte(nombresDias[i], inicioDia, totalDiaFiltrado, totalPiezasDiaFiltrado, ventasDiaFiltradas.map { it.clienteId }.distinct().size, gastosDia.sumOf { it.monto }, ventasDiaFiltradas))
            totalSemanaBruta += totalDiaFiltrado
            totalGastosSemana += gastosDia.sumOf { it.monto }
            totalPiezasSemana += totalPiezasDiaFiltrado
        }

        val current = _uiState.value
        return current.copy(
            isLoading = false,
            totalVentaBrutaSemana = totalSemanaBruta,
            totalGastosSemana = totalGastosSemana,
            totalSemanaNeta = totalSemanaBruta - totalGastosSemana,
            totalPiezasSemana = totalPiezasSemana,
            dias = diasReporte,
            todosLosGastosSemana = gastosMapeados,
            fechaInicioSemana = fechaLunes
        )
    }

    fun seleccionarPerfil(perfil: com.gruposanangel.delivery.data.PerfilVenta) {
        _perfilFiltro.value = perfil
    }

    fun reintentar() {
        val inicio = _fechaFiltro.value
        if (inicio != null) {
            _fechaFiltro.value = null
            _fechaFiltro.value = inicio
        }
    }

    fun actualizarMeta(nuevaMeta: Double) {
        viewModelScope.launch {
            prefs.edit().putFloat("meta_semanal_$userId", nuevaMeta.toFloat()).apply()
            _uiState.update { it.copy(metaSemanal = nuevaMeta) }
        }
    }

    fun cambiarSemana(nuevaFecha: Long) {
        viewModelScope.launch {
            val calUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = nuevaFecha }
            val calLocal = Calendar.getInstance().apply {
                set(calUtc.get(Calendar.YEAR), calUtc.get(Calendar.MONTH), calUtc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            while (calLocal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) { calLocal.add(Calendar.DAY_OF_MONTH, -1) }
            _fechaFiltro.value = calLocal.timeInMillis
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaReporteSemanal(
    navController: NavController,
    ventaRepository: VentaRepository,
    usuarioRepository: RepositoryUsuario,
    inventarioRepository: RepositoryInventario,
    gastoRepository: RepositoryGasto,
    userId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: ReporteSemanalViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = 
                ReporteSemanalViewModel(ventaRepository, usuarioRepository, inventarioRepository, gastoRepository, userId, context) as T
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val dfRange = remember { SimpleDateFormat("dd MMM", Locale("es", "MX")) }
    val rangoFechas = remember(uiState.fechaInicioSemana) {
        val calEnd = Calendar.getInstance().apply { timeInMillis = uiState.fechaInicioSemana; add(Calendar.DAY_OF_YEAR, 6) }
        "${dfRange.format(Date(uiState.fechaInicioSemana))} - ${dfRange.format(calEnd.time)}".uppercase()
    }

    var showMetaDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = uiState.fechaInicioSemana,
            initialSelectedEndDateMillis = uiState.fechaInicioSemana + (6L * 24 * 60 * 60 * 1000)
        )
        DeliveryTheme(darkTheme = ThemeConfig.isActuallyDark) {
            AlertDialog(
                onDismissRequest = { showDatePicker = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth(0.95f),
                confirmButton = {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showDatePicker = false }) { Text("CANCELAR", fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { dateRangePickerState.selectedStartDateMillis?.let { viewModel.cambiarSemana(it) }; showDatePicker = false }, colors = ButtonDefaults.buttonColors(containerColor = DelisaRed), shape = RoundedCornerShape(12.dp)) { Text("ACEPTAR", fontWeight = FontWeight.Black, color = Color.White) }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                text = {
                    Box(Modifier.fillMaxWidth().height(450.dp)) {
                        DateRangePicker(state = dateRangePickerState, title = { Text("Selecciona la semana", Modifier.padding(16.dp), fontWeight = FontWeight.Bold) }, showModeToggle = false, headline = {
                            val start = dateRangePickerState.selectedStartDateMillis
                            val end = dateRangePickerState.selectedEndDateMillis
                            if (start != null && end != null) {
                                val df = SimpleDateFormat("dd MMM", Locale.forLanguageTag("es-MX")).apply { timeZone = TimeZone.getTimeZone("UTC") }
                                Text("${df.format(Date(start))} - ${df.format(Date(end))}".uppercase(), Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Black, color = DelisaRed)
                            }
                        }, colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface, headlineContentColor = DelisaRed, selectedDayContainerColor = DelisaRed, selectedDayContentColor = Color.White, dayInSelectionRangeContainerColor = DelisaRed.copy(alpha = 0.15f), dayInSelectionRangeContentColor = DelisaRed, todayContentColor = DelisaRed, todayDateBorderColor = DelisaRed, currentYearContentColor = DelisaRed, selectedYearContainerColor = DelisaRed, selectedYearContentColor = Color.White))
                    }
                }
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Column { Text("ESTADÍSTICAS", fontWeight = FontWeight.Black, fontSize = 18.sp); Text("${uiState.rutaNombre.uppercase()} | $rangoFechas", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp) } },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Rounded.ArrowBackIosNew, null, tint = DelisaRed) } },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Rounded.CalendarMonth, null, tint = DelisaRed) }
                    IconButton(onClick = { scope.launch { val file = generarPdfReporteSemanal(context, uiState); abrirPdfCierre(context, file) } }) { Icon(Icons.Rounded.PictureAsPdf, null, tint = DelisaRed) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            // 1. CONTENIDO PRINCIPAL (Siempre visible si no hay error)
            if (uiState.error == null) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item { PerfilVentaSelector(perfiles = uiState.perfilesDisponibles, seleccionado = uiState.perfilSeleccionado, onSeleccionar = { viewModel.seleccionarPerfil(it) }) }
                    item { BalanceCardPremium(bruta = uiState.totalVentaBrutaSemana, gastos = uiState.totalGastosSemana, neta = uiState.totalSemanaNeta, meta = uiState.metaSemanal, formato = formatoMoneda, onEditMeta = { showMetaDialog = true }) }
                    item { PerformanceChartPremium(dias = uiState.dias, maxVenta = (uiState.dias.maxOfOrNull { it.totalVenta } ?: 0.0).coerceAtLeast(1000.0)) }
                    item { Text("DETALLE POR DÍA", Modifier.padding(horizontal = 24.dp, vertical = 16.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.5.sp) }
                    items(uiState.dias) { dia ->
                        TransactionItemPremium(dia, formatoMoneda) {
                            scope.launch {
                                val ventasItems = withContext(Dispatchers.IO) {
                                    dia.ventas.map { v ->
                                        val cal = Calendar.getInstance().apply { timeInMillis = v.fecha }
                                        VentaReporteItem(cliente = v.clienteNombre, piezas = 0, monto = v.total, hora = cal.get(Calendar.HOUR_OF_DAY), minutos = cal.get(Calendar.MINUTE), estado = when { v.estado == "CANCELADA" -> "ANULADA"; v.total > 0 -> "VENTA"; else -> v.motivoVisita?.uppercase() ?: "SIN VENTA" })
                                    }.sortedWith(compareBy({ it.hora }, { it.minutos }))
                                }
                                val inicioDia = dia.fecha
                                val finDia = inicioDia + (24L * 60 * 60 * 1000) - 1
                                val gastosDelDia = uiState.todosLosGastosSemana.filter { it.fecha in inicioDia..finDia }
                                val stateFake = DashboardVendedorUiState(nombreVendedor = uiState.nombreVendedor, rutaNombre = uiState.rutaNombre, ventaDia = dia.totalVenta, totalGastosHoy = dia.totalGastos, gastosHoy = gastosDelDia)
                                val file = GenerarPDFCierreCarta(context, stateFake, ventasItems, false, dia.fecha)
                                abrirPdfCierre(context, file)
                            }
                        }
                    }

                    // 🔥 NUEVA SECCIÓN: DESGLOSE DE GASTOS
                    if (uiState.todosLosGastosSemana.isNotEmpty()) {
                        item { 
                            Text(
                                "DETALLE DE GASTOS", 
                                Modifier.padding(horizontal = 24.dp, vertical = 16.dp), 
                                style = MaterialTheme.typography.labelMedium, 
                                fontWeight = FontWeight.Black, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                                letterSpacing = 1.5.sp
                            ) 
                        }
                        items(uiState.todosLosGastosSemana.sortedByDescending { it.fecha }) { gasto ->
                            GastoItemPremium(gasto, formatoMoneda)
                        }
                    }
                }
            }

            // 2. VISTA DE ERROR (Si falla la conexión)
            if (uiState.error != null) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CloudOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Text(uiState.error!!, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { viewModel.reintentar() }, colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)) {
                            Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("REINTENTAR")
                        }
                    }
                }
            }

            // 3. CAPA DE CARGA (OVERLAY) - Siempre al final para estar encima de todo
            AnimatedVisibility(
                visible = uiState.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .clickable(enabled = false) {}, 
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = DelisaRed, modifier = Modifier.size(48.dp), strokeWidth = 5.dp)
                        Spacer(Modifier.height(20.dp))
                        Text("CONSULTANDO NUBE", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, letterSpacing = 1.sp)
                        Text("Obteniendo tickets de la ruta...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showMetaDialog) {
        var rawInput by remember { mutableStateOf((uiState.metaSemanal * 100).toLong().toString()) }
        val displayValue = (rawInput.toDoubleOrNull() ?: 0.0) / 100.0
        val formattedDisplay = String.format(Locale.US, "%,.2f", displayValue)
        AlertDialog(onDismissRequest = { showMetaDialog = false }, containerColor = MaterialTheme.colorScheme.surface, title = { Text("Ajustar Meta Semanal", fontWeight = FontWeight.Black) }, text = {
            Column {
                Text("Ingresa el nuevo objetivo de venta:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = formattedDisplay, onValueChange = { newValue -> val digits = newValue.filter { it.isDigit() }; if (digits.length <= 12) { rawInput = if (digits.isEmpty()) "0" else digits.toLong().toString() } }, prefix = { Text("$ ", fontWeight = FontWeight.Black, color = DelisaRed) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), textStyle = TextStyle(textAlign = TextAlign.End, fontWeight = FontWeight.Black, fontSize = 24.sp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DelisaRed, focusedLabelColor = DelisaRed))
            }
        }, confirmButton = { Button(onClick = { val metaDouble = (rawInput.toDoubleOrNull() ?: 0.0) / 100.0; viewModel.actualizarMeta(metaDouble); showMetaDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = DelisaRed), shape = RoundedCornerShape(12.dp)) { Text("GUARDAR META", fontWeight = FontWeight.Black, color = Color.White) } }, dismissButton = { TextButton(onClick = { showMetaDialog = false }) { Text("CANCELAR", fontWeight = FontWeight.Bold) } }, shape = RoundedCornerShape(28.dp))
    }
}

@Composable
fun BalanceCardPremium(bruta: Double, gastos: Double, neta: Double, meta: Double, formato: NumberFormat, onEditMeta: () -> Unit) {
    val progreso = (bruta / meta).coerceIn(0.0, 1.0).toFloat()
    Card(Modifier.fillMaxWidth().padding(20.dp).shadow(15.dp, RoundedCornerShape(32.dp), ambientColor = DelisaRed.copy(0.4f)), shape = RoundedCornerShape(32.dp)) {
        Box(Modifier.background(Brush.verticalGradient(listOf(DelisaRed, DelisaRedDark)))) {
            Column(Modifier.padding(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("VENTA BRUTA SEMANAL", color = Color.White.copy(0.7f), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                        Text(formato.format(bruta), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black, lineHeight = 44.sp)
                    }
                    Surface(onClick = onEditMeta, color = Color.White.copy(alpha = 0.15f), shape = CircleShape, modifier = Modifier.size(42.dp)) { Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.padding(10.dp)) }
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("GASTOS TOTALES", color = Color.White.copy(0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("-${formato.format(gastos)}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black) }
                    Column(horizontalAlignment = Alignment.End) { Text("EFECTIVO NETO", color = Color.White.copy(0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(formato.format(neta), color = DelisaGreenLight, fontSize = 18.sp, fontWeight = FontWeight.Black) }
                }
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Objetivo: ${formato.format(meta)}", color = Color.White.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${(progreso * 100).toInt()}%", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progreso }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), color = Color.White, trackColor = Color.White.copy(0.2f))
            }
        }
    }
}

@Composable
fun PerformanceChartPremium(dias: List<DiaReporte>, maxVenta: Double) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp).shadow(2.dp, RoundedCornerShape(28.dp)), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp)) {
            Text("RENDIMIENTO POR DÍA", fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                val iniciales = listOf("L", "M", "M", "J", "V", "S", "D")
                dias.forEachIndexed { i, dia ->
                    val hFactor = (dia.totalVenta / maxVenta).toFloat().coerceIn(0.01f, 1f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(Modifier.height(20.dp), contentAlignment = Alignment.BottomCenter) { if (dia.totalVenta > 0) { Text("${if (dia.totalVenta >= 1000) "%.1fk".format(dia.totalVenta / 1000.0) else dia.totalVenta.toInt()}", fontSize = 9.sp, fontWeight = FontWeight.Black, color = DelisaRed, maxLines = 1) } }
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.width(24.dp).height(110.dp), contentAlignment = Alignment.BottomCenter) { Box(Modifier.fillMaxWidth().fillMaxHeight(hFactor).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(Brush.verticalGradient(listOf(DelisaRed, DelisaRed.copy(0.6f))))) }
                        Spacer(Modifier.height(8.dp))
                        Text(iniciales[i], fontSize = 10.sp, fontWeight = FontWeight.Black, color = if(dia.totalVenta > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItemPremium(dia: DiaReporte, formato: NumberFormat, onPdfClick: () -> Unit) {
    val esHoy = SimpleDateFormat("ddMM", Locale.US).format(Date()) == SimpleDateFormat("ddMM", Locale.US).format(Date(dia.fecha))
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).shadow(if(esHoy) 4.dp else 1.dp, RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), shape = CircleShape, color = if (dia.totalVenta > 0) DelisaGreen.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant) { Icon(imageVector = if (dia.totalVenta > 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.Rounded.Block, contentDescription = null, tint = if (dia.totalVenta > 0) DelisaGreenDark else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(dia.nombre.uppercase(), fontWeight = FontWeight.Black, fontSize = 15.sp); if (esHoy) { Spacer(Modifier.width(8.dp)); Box(Modifier.background(DelisaRed, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) { Text("HOY", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black) } } }
                Text("${dia.clientesAtendidos} ventas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formato.format(dia.totalVenta), fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (dia.totalVenta > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                if (dia.totalVenta > 0) { Surface(onClick = onPdfClick, color = DelisaRed.copy(alpha = 0.1f), shape = RoundedCornerShape(10.dp), modifier = Modifier.padding(top = 6.dp)) { Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.PictureAsPdf, null, tint = DelisaRed, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("VER PDF", fontSize = 10.sp, fontWeight = FontWeight.Black, color = DelisaRed, letterSpacing = 0.5.sp) } } }
            }
        }
    }
}

@Composable
fun GastoItemPremium(gasto: Gasto, formato: NumberFormat) {
    val dfGasto = remember { SimpleDateFormat("dd/MM", Locale.US) }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .shadow(1.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = when {
                gasto.categoria.contains("Gasolina", true) -> Icons.Rounded.LocalGasStation
                gasto.categoria.contains("Comida", true) -> Icons.Rounded.Restaurant
                gasto.categoria.contains("Reparación", true) -> Icons.Rounded.Build
                else -> Icons.Rounded.Payments
            }
            
            Surface(
                Modifier.size(44.dp), 
                shape = CircleShape, 
                color = DelisaRed.copy(0.1f)
            ) { 
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = DelisaRed, 
                    modifier = Modifier.padding(12.dp)
                ) 
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        gasto.categoria.uppercase(), 
                        fontWeight = FontWeight.Black, 
                        fontSize = 12.sp,
                        color = DelisaRed
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        dfGasto.format(Date(gasto.fecha)), 
                        fontSize = 11.sp, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    gasto.descripcion, 
                    fontSize = 13.sp, 
                    color = MaterialTheme.colorScheme.onSurface, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Text(
                "-${formato.format(gasto.monto)}", 
                fontWeight = FontWeight.Black, 
                fontSize = 16.sp, 
                color = DelisaRed
            )
        }
    }
}

fun generarPdfReporteSemanal(context: Context, state: ReporteSemanalUiState, esDemo: Boolean = false): File {
    val pdfDocument = PdfDocument(); val nf = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val pDelisaRed = android.graphics.Paint().apply { color = android.graphics.Color.rgb(227, 6, 19); isAntiAlias = true }
    val pTitle = android.graphics.Paint().apply { textSize = 22f; color = android.graphics.Color.WHITE; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD); isAntiAlias = true }
    val pSub = android.graphics.Paint().apply { textSize = 9f; color = android.graphics.Color.LTGRAY; isAntiAlias = true }
    val pText = android.graphics.Paint().apply { textSize = 11f; color = android.graphics.Color.BLACK; isAntiAlias = true }
    val pBold = android.graphics.Paint().apply { textSize = 11f; color = android.graphics.Color.BLACK; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD); isAntiAlias = true }
    val totalTickets = state.dias.sumOf { it.ventas.size }; val totalPiezas = state.totalPiezasSemana
    val ticketPromedioGral = if (totalTickets > 0) state.totalVentaBrutaSemana / totalTickets else 0.0
    val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create(); var page = pdfDocument.startPage(pageInfo); var canvas = page.canvas
    canvas.drawRect(0f, 0f, 612f, 95f, android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 0f, 95f, android.graphics.Color.rgb(227, 6, 19), android.graphics.Color.rgb(160, 0, 10), android.graphics.Shader.TileMode.CLAMP) })
    context.getDrawable(com.gruposanangel.delivery.R.drawable.logo)?.let { it.setBounds(40, 15, 130, 80); it.draw(canvas) }
    canvas.drawText(if (state.perfilSeleccionado?.id == "ALL" || state.perfilSeleccionado == null) "BALANCE SEMANAL" else "BALANCE: ${state.perfilSeleccionado.nombre.uppercase()}", 160f, 40f, pTitle)
    pBold.color = android.graphics.Color.WHITE; pBold.textSize = 10f; canvas.drawText("VENDEDOR: ${state.nombreVendedor.uppercase()}", 160f, 65f, pBold); canvas.drawText("RUTA: ${state.rutaNombre.uppercase()}", 160f, 85f, pBold)
    pBold.textAlign = android.graphics.Paint.Align.RIGHT; val dfRange = SimpleDateFormat("dd MMM", Locale("es", "MX")); canvas.drawText("${dfRange.format(Date(state.dias.firstOrNull()?.fecha ?: 0L))} - ${dfRange.format(Date(state.dias.lastOrNull()?.fecha ?: 0L))}".uppercase(), 572f, 40f, pBold); canvas.drawText("DELISA BOTANAS", 572f, 85f, pSub.apply { textAlign = android.graphics.Paint.Align.RIGHT; color = android.graphics.Color.WHITE }); pBold.textAlign = android.graphics.Paint.Align.LEFT; pBold.color = android.graphics.Color.BLACK
    var y = 105f; val kpiW = 182f; val kpiH = 50f; val kpiGap = 6f; val startX = (612 - ((kpiW * 3) + (kpiGap * 2))) / 2f
    fun drawKpi(l: String, v: String, x: Float, bg: Int, txtC: Int) { canvas.drawRoundRect(x, y, x + kpiW, y + kpiH, 10f, 10f, android.graphics.Paint().apply { color = bg }); pSub.textAlign = android.graphics.Paint.Align.CENTER; pSub.color = android.graphics.Color.GRAY; canvas.drawText(l.uppercase(), x + kpiW/2, y + 18f, pSub); pBold.textAlign = android.graphics.Paint.Align.CENTER; pBold.textSize = 12f; pBold.color = txtC; canvas.drawText(v, x + kpiW/2, y + 40f, pBold) }
    drawKpi("Venta Bruta", nf.format(state.totalVentaBrutaSemana), startX, android.graphics.Color.rgb(26, 26, 26), android.graphics.Color.WHITE); drawKpi("Gastos Sem.", "-${nf.format(state.totalGastosSemana)}", startX + kpiW + kpiGap, android.graphics.Color.rgb(255, 235, 238), android.graphics.Color.rgb(198, 40, 40)); drawKpi("Efectivo Neto", nf.format(state.totalSemanaNeta), startX + 2*(kpiW + kpiGap), android.graphics.Color.rgb(232, 245, 233), android.graphics.Color.rgb(46, 125, 50)); y += kpiH + kpiGap
    drawKpi("Ventas (Tickets)", "$totalTickets", startX, android.graphics.Color.rgb(250, 250, 250), android.graphics.Color.BLACK); drawKpi("Ticket Promedio", nf.format(ticketPromedioGral), startX + kpiW + kpiGap, android.graphics.Color.rgb(250, 250, 250), android.graphics.Color.BLACK); drawKpi("Total Piezas", "$totalPiezas", startX + 2*(kpiW + kpiGap), android.graphics.Color.rgb(250, 250, 250), android.graphics.Color.BLACK); y += kpiH + 50f
    canvas.drawText("FLUJO DE INGRESOS POR DÍA", 306f, y, pBold.apply { textAlign = android.graphics.Paint.Align.CENTER; textSize = 12f }); y += 30f; val maxV = (state.dias.maxOfOrNull { it.totalVenta } ?: 1.0).coerceAtLeast(1.0); val bW = 35f; val gap = 40f; val sX = (612 - (bW*7 + gap*6)) / 2f
    state.dias.forEachIndexed { i, dia -> val h = (dia.totalVenta / maxV * 120f).toFloat().coerceAtLeast(2f); canvas.drawRoundRect(sX + i*(bW+gap), y + 120f - h, sX + i*(bW+gap) + bW, y + 120f, 6f, 6f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(227, 6, 19) }); pBold.textSize = 9f; canvas.drawText(dia.nombre.take(3).uppercase(), sX + i*(bW+gap) + bW/2, y + 138f, pBold) }; y += 200f
    canvas.drawRect(40f, y, 572f, y + 25f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(245, 245, 245) }); pBold.textAlign = android.graphics.Paint.Align.LEFT; canvas.drawText("DÍA", 50f, y+16f, pBold); canvas.drawText("CLIENTES", 220f, y+16f, pBold); canvas.drawText("TOTAL", 480f, y+16f, pBold); y += 30f
    state.dias.forEach { dia -> canvas.drawText(dia.nombre.uppercase(), 50f, y+12f, pText); canvas.drawText("${dia.clientesAtendidos}", 220f, y+12f, pText); canvas.drawText(nf.format(dia.totalVenta), 480f, y+12f, pBold.apply { color = android.graphics.Color.rgb(227, 6, 19) }); y += 23f }; y += 20f

    // --- NUEVA SECCIÓN: DESGLOSE DE GASTOS SEMANALES ---
    if (state.todosLosGastosSemana.isNotEmpty()) {
        y += 10f
        if (y > 700f) {
            pdfDocument.finishPage(page); page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(612, 792, 2).create()); canvas = page.canvas; y = 40f
        }

        pBold.textAlign = android.graphics.Paint.Align.CENTER
        canvas.drawText("DESGLOSE DE GASTOS SEMANALES", 306f, y, pBold.apply { textSize = 12f }); y += 30f
        
        fun drawExpensesHeader(canv: android.graphics.Canvas, curY: Float) {
            canv.drawRect(40f, curY, 572f, curY + 1.2f, pDelisaRed)
            pBold.textAlign = android.graphics.Paint.Align.LEFT
            canv.drawText("FECHA", 50f, curY + 16f, pBold)
            canv.drawText("CATEGORÍA", 140f, curY + 16f, pBold)
            canv.drawText("DESCRIPCIÓN", 300f, curY + 16f, pBold)
            canv.drawText("MONTO", 510f, curY + 16f, pBold)
        }
        drawExpensesHeader(canvas, y); y += 25f
        
        val dfGasto = SimpleDateFormat("dd/MM", Locale.US)
        state.todosLosGastosSemana.sortedBy { it.fecha }.forEach { gasto ->
            if (y > 730f) {
                pdfDocument.finishPage(page); page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(612, 792, 3).create()); canvas = page.canvas; y = 40f; drawExpensesHeader(canvas, y); y += 25f
            }
            canvas.drawRect(40f, y, 572f, y + 20f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(252, 252, 252) })
            canvas.drawText(dfGasto.format(Date(gasto.fecha)), 50f, y + 14f, pText)
            canvas.drawText(gasto.categoria.take(18).uppercase(), 140f, y + 14f, pText.apply { textSize = 9f })
            canvas.drawText(gasto.descripcion.take(30), 300f, y + 14f, pText.apply { textSize = 10f })
            canvas.drawText(nf.format(gasto.monto), 510f, y + 14f, pBold.apply { textAlign = android.graphics.Paint.Align.LEFT; color = android.graphics.Color.rgb(198, 40, 40) })
            y += 20f
        }
    }

    canvas.drawRect(40f, 740f, 572f, 741f, pDelisaRed); pSub.textAlign = android.graphics.Paint.Align.LEFT; canvas.drawText("GENERADO: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())}", 50f, 755f, pSub); pdfDocument.finishPage(page); val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Estadisticas_${state.rutaNombre.replace(" ","")}.pdf"); pdfDocument.writeTo(FileOutputStream(file)); pdfDocument.close(); return file
}
