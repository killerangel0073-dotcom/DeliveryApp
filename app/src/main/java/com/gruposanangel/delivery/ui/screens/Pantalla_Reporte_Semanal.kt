package com.gruposanangel.delivery.ui.screens

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp

private val RojoDelisa = Color(0xFFE53935)
private val NegroPremium = Color(0xFF1E1E24)
private val GrisFondoPremium = Color(0xFFF6F8FA)
private val VerdeExito = Color(0xFF2E7D32)

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
    val totalVentaBrutaSemana: Double = 0.0,
    val totalGastosSemana: Double = 0.0,
    val totalSemanaNeta: Double = 0.0,
    val dias: List<DiaReporte> = emptyList(),
    val todosLosGastosSemana: List<Gasto> = emptyList(),
    val nombreVendedor: String = "",
    val rutaNombre: String = "",
    val metaSemanal: Double = 70000.0,
    val fechaInicioSemana: Long = 0L
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
    private val prefs = context.getSharedPreferences("reporte_semanal_prefs", Context.MODE_PRIVATE)

    init {
        val metaGuardada = prefs.getFloat("meta_semanal_$userId", 70000f).toDouble()
        _uiState.update { it.copy(metaSemanal = metaGuardada) }

        // 🔥 MOTOR REACTIVO BLINDADO (UTC PARA LA LÓGICA DE DATOS)
        _fechaFiltro.filterNotNull().flatMapLatest { fechaLunesUtc ->
            // El picker y el filtro trabajan en UTC para evitar desfases de "Martes a Lunes"
            val inicioMs = fechaLunesUtc
            val finMs = inicioMs + (7L * 24 * 60 * 60 * 1000) - 1

            _uiState.update { it.copy(isLoading = true, fechaInicioSemana = fechaLunesUtc) }

            viewModelScope.launch {
                try {
                    // 🔥 SINCRONIZACIÓN DE PERFIL (Asegura tener el nombre de ruta actualizado)
                    usuarioRepository.syncUsuario(userId)
                    val user = usuarioRepository.obtenerUsuarioLocal(userId)
                    
                    val nombre = user?.nombre ?: "Vendedor"
                    val ruta = user?.ultimaRutaNombre ?: user?.ultimoAlmacenNombre ?: "Sin Ruta"

                    _uiState.update { it.copy(nombreVendedor = nombre, rutaNombre = ruta) }

                    ventaRepository.sincronizarVentasPeriodo(userId, inicioMs, finMs)
                    gastoRepository.descargarGastosPeriodo(userId, inicioMs, finMs)
                } catch (e: Exception) { Log.e("ReporteVM", "Error sync", e) }
            }

            combine(
                ventaRepository.obtenerVentasPorPeriodoFlow(userId, inicioMs, finMs),
                gastoRepository.obtenerGastosPorPeriodoFlow(userId, inicioMs, finMs)
            ) { ventas, gastos ->
                procesarDatosFinal(inicioMs, ventas, gastos)
            }
        }.onEach { nuevoEstado ->
            _uiState.update { nuevoEstado }
        }.launchIn(viewModelScope)

        // Inicializar con el lunes de la semana actual en base a la hora local
        cambiarSemana(System.currentTimeMillis())
    }

    private suspend fun procesarDatosFinal(
        fechaLunes: Long,
        todasLasVentas: List<VentaEntity>,
        todosLosGastosLocal: List<com.gruposanangel.delivery.data.GastoEntity>
    ): ReporteSemanalUiState {
        val nombresDias = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        val diasReporte = mutableListOf<DiaReporte>()
        var totalSemanaBruta = 0.0
        var totalGastosSemana = 0.0

        val cal = Calendar.getInstance().apply { 
            timeInMillis = fechaLunes
        }
        
        val gastosMapeados = todosLosGastosLocal.map { 
            Gasto(it.id, it.monto, it.categoria, it.descripcion, it.fecha, it.vendedorId, it.rutaNombre)
        }

        for (i in 0..6) {
            val inicioDia = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val finDia = cal.timeInMillis - 1

            val ventasDia = todasLasVentas.filter { it.fecha in inicioDia..finDia && it.estado != "CANCELADA" }
            val totalDia = ventasDia.sumOf { it.total }
            
            var totalPiezasDia = 0
            ventasDia.forEach { v ->
                totalPiezasDia += ventaRepository.obtenerDetallesDeVenta(v.id).sumOf { it.cantidad }
            }

            val gastosDia = gastosMapeados.filter { it.fecha in inicioDia..finDia }
            val totalGastosDia = gastosDia.sumOf { it.monto }

            diasReporte.add(DiaReporte(
                nombre = nombresDias[i],
                fecha = inicioDia,
                totalVenta = totalDia,
                totalPiezas = totalPiezasDia,
                clientesAtendidos = ventasDia.map { it.clienteId }.distinct().size,
                totalGastos = totalGastosDia,
                ventas = ventasDia
            ))
            totalSemanaBruta += totalDia
            totalGastosSemana += totalGastosDia
        }

        val currentState = _uiState.value
        val efectivoNetoReal = totalSemanaBruta - totalGastosSemana
        return currentState.copy(
            isLoading = false,
            totalVentaBrutaSemana = totalSemanaBruta,
            totalGastosSemana = totalGastosSemana,
            totalSemanaNeta = totalSemanaBruta, // ✅ VENTA BRUTA en la tarjeta de progreso
            dias = diasReporte,
            todosLosGastosSemana = gastosMapeados,
            fechaInicioSemana = fechaLunes
        )
    }

    fun actualizarMeta(nuevaMeta: Double) {
        viewModelScope.launch {
            prefs.edit().putFloat("meta_semanal_$userId", nuevaMeta.toFloat()).apply()
            _uiState.update { it.copy(metaSemanal = nuevaMeta) }
        }
    }

    fun cambiarSemana(nuevaFecha: Long) {
        viewModelScope.launch {
            // El DatePicker de Material3 devuelve el tiempo en UTC.
            // Para evitar desfases al convertir a hora local, extraemos los campos de fecha
            // y los aplicamos a un calendario local.
            val calUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { 
                timeInMillis = nuevaFecha 
            }
            
            val calLocal = Calendar.getInstance().apply {
                set(calUtc.get(Calendar.YEAR), calUtc.get(Calendar.MONTH), calUtc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            // Retrocedemos hasta encontrar el lunes de esa semana en hora local
            while (calLocal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                calLocal.add(Calendar.DAY_OF_MONTH, -1)
            }
            
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
    
    val dfRange = remember { 
        SimpleDateFormat("dd MMM", Locale("es", "MX"))
    }
    
    val rangoFechas = remember(uiState.fechaInicioSemana) {
        val calEnd = Calendar.getInstance().apply { 
            timeInMillis = uiState.fechaInicioSemana
            add(Calendar.DAY_OF_YEAR, 6)
        }
        "${dfRange.format(Date(uiState.fechaInicioSemana))} - ${dfRange.format(calEnd.time)}".uppercase()
    }

    var showMetaDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = uiState.fechaInicioSemana,
            initialSelectedEndDateMillis = uiState.fechaInicioSemana + (6L * 24 * 60 * 60 * 1000)
        )

        LaunchedEffect(dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis) {
            val selection = dateRangePickerState.selectedEndDateMillis ?: dateRangePickerState.selectedStartDateMillis
            selection?.let { millis ->
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { 
                    timeInMillis = millis 
                }
                while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                }
                val lunesMs = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 6)
                val domingoMs = cal.timeInMillis
                
                if (dateRangePickerState.selectedStartDateMillis != lunesMs || 
                    dateRangePickerState.selectedEndDateMillis != domingoMs) {
                    dateRangePickerState.setSelection(lunesMs, domingoMs)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.95f),
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showDatePicker = false }
                    ) {
                        Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            dateRangePickerState.selectedStartDateMillis?.let {
                                viewModel.cambiarSemana(it)
                            }
                            showDatePicker = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RojoDelisa),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ACEPTAR", fontWeight = FontWeight.Black)
                    }
                }
            },
            dismissButton = null,
            containerColor = Color.White,
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(450.dp)) {
                    DateRangePicker(
                        state = dateRangePickerState,
                        title = { Text("Selecciona la semana", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) },
                        showModeToggle = false,
                        headline = {
                            val start = dateRangePickerState.selectedStartDateMillis
                            val end = dateRangePickerState.selectedEndDateMillis
                            if (start != null && end != null) {
                                val df = SimpleDateFormat("dd MMM", Locale("es", "MX")).apply { 
                                    timeZone = TimeZone.getTimeZone("UTC") 
                                }
                                Text(
                                    text = "${df.format(Date(start))} - ${df.format(Date(end))}".uppercase(),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    fontWeight = FontWeight.Black,
                                    color = RojoDelisa
                                )
                            }
                        },
                        colors = DatePickerDefaults.colors(
                            selectedDayContainerColor = RojoDelisa,
                            dayInSelectionRangeContainerColor = RojoDelisa.copy(alpha = 0.1f),
                            selectedDayContentColor = Color.White,
                            todayContentColor = RojoDelisa,
                            todayDateBorderColor = RojoDelisa
                        )
                    )
                }
            }
        )
    }

    Scaffold(
        containerColor = GrisFondoPremium,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("ESTADÍSTICAS", fontWeight = FontWeight.Black, fontSize = 18.sp, color = NegroPremium)
                        Text("${uiState.rutaNombre.uppercase()} | $rangoFechas", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBackIosNew, null, tint = RojoDelisa)
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Rounded.CalendarMonth, null, tint = RojoDelisa)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            val file = generarPdfReporteSemanal(context, uiState)
                            abrirPdfCierre(context, file)
                        }
                    }) {
                        Icon(Icons.Rounded.PictureAsPdf, null, tint = RojoDelisa)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (uiState.isLoading && uiState.dias.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = RojoDelisa) }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    BalanceCardPremium(
                        total = uiState.totalSemanaNeta,
                        meta = uiState.metaSemanal,
                        formato = formatoMoneda,
                        onEditMeta = { showMetaDialog = true }
                    )
                }

                item {
                    PerformanceChartPremium(
                        dias = uiState.dias,
                        maxVenta = (uiState.dias.maxOfOrNull { it.totalVenta } ?: 0.0).coerceAtLeast(1000.0)
                    )
                }

                item {
                    Text(
                        text = "DETALLE POR DÍA",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.Gray,
                        letterSpacing = 1.5.sp
                    )
                }

                items(uiState.dias) { dia ->
                    TransactionItemPremium(dia, formatoMoneda) {
                        scope.launch {
                            val ventasItems = withContext(Dispatchers.IO) {
                                dia.ventas.map { v ->
                                    val piezas = ventaRepository.obtenerDetallesDeVenta(v.id).sumOf { it.cantidad }
                                    val cal = Calendar.getInstance().apply { timeInMillis = v.fecha }
                                    VentaReporteItem(
                                        cliente = v.clienteNombre, 
                                        piezas = piezas, 
                                        monto = v.total, 
                                        hora = cal.get(Calendar.HOUR_OF_DAY), 
                                        minutos = cal.get(Calendar.MINUTE),
                                        estado = when {
                                            v.estado == "CANCELADA" -> "ANULADA"
                                            v.total > 0 -> "VENTA"
                                            else -> v.motivoVisita?.uppercase() ?: "SIN VENTA"
                                        }
                                    )
                                }.sortedWith(compareBy({ it.hora }, { it.minutos }))
                            }
                            
                            val inicioDia = dia.fecha
                            val finDia = inicioDia + (24L * 60 * 60 * 1000) - 1
                            val gastosDelDia = uiState.todosLosGastosSemana.filter { it.fecha in inicioDia..finDia }

                            val stateFake = DashboardVendedorUiState(
                                nombreVendedor = uiState.nombreVendedor,
                                rutaNombre = uiState.rutaNombre,
                                ventaDia = dia.totalVenta,
                                totalGastosHoy = dia.totalGastos,
                                gastosHoy = gastosDelDia 
                            )
                            val file = GenerarPDFCierreCarta(context, stateFake, ventasItems, false, dia.fecha)
                            abrirPdfCierre(context, file)
                        }
                    }
                }
            }
        }
    }

    if (showMetaDialog) {
        var rawInput by remember { mutableStateOf((uiState.metaSemanal * 100).toLong().toString()) }
        val displayValue = (rawInput.toDoubleOrNull() ?: 0.0) / 100.0
        val formattedDisplay = String.format(Locale.US, "%,.2f", displayValue)

        AlertDialog(
            onDismissRequest = { showMetaDialog = false },
            title = { Text("Ajustar Meta Semanal", fontWeight = FontWeight.Black) },
            text = {
                Column {
                    Text("Ingresa el nuevo objetivo de venta:", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = formattedDisplay,
                        onValueChange = { newValue ->
                            val digits = newValue.filter { it.isDigit() }
                            if (digits.length <= 12) {
                                rawInput = if (digits.isEmpty()) "0" else digits.toLong().toString()
                            }
                        },
                        prefix = { Text("$ ", fontWeight = FontWeight.Black, color = RojoDelisa) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        textStyle = TextStyle(
                            textAlign = TextAlign.End, 
                            fontWeight = FontWeight.Black, 
                            fontSize = 24.sp,
                            color = NegroPremium
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val metaDouble = (rawInput.toDoubleOrNull() ?: 0.0) / 100.0
                        viewModel.actualizarMeta(metaDouble)
                        showMetaDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RojoDelisa),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("GUARDAR META", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMetaDialog = false }) {
                    Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color.White
        )
    }
}

@Composable
fun BalanceCardPremium(total: Double, meta: Double, formato: NumberFormat, onEditMeta: () -> Unit) {
    val progreso = (total / meta).coerceIn(0.0, 1.0).toFloat()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .shadow(15.dp, RoundedCornerShape(32.dp), ambientColor = RojoDelisa.copy(0.4f)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = NegroPremium)
    ) {
        Column(Modifier.padding(28.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("VENTA NETA SEMANAL", color = Color.White.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(formato.format(total), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                }
                Surface(
                    onClick = onEditMeta,
                    color = RojoDelisa,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.padding(12.dp))
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Meta: ${formato.format(meta)}", color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${(progreso * 100).toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            
            Spacer(Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { progreso },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = RojoDelisa,
                trackColor = Color.White.copy(0.1f)
            )
        }
    }
}

@Composable
fun PerformanceChartPremium(dias: List<DiaReporte>, maxVenta: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("RENDIMIENTO POR DÍA", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.LightGray, letterSpacing = 1.sp)
            Spacer(Modifier.height(28.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val iniciales = listOf("L", "M", "M", "J", "V", "S", "D")
                dias.forEachIndexed { i, dia ->
                    val hFactor = (dia.totalVenta / maxVenta).toFloat().coerceIn(0.01f, 1f)
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        if (dia.totalVenta > 0) {
                            val label = if (dia.totalVenta >= 1000) "%.1fk".format(dia.totalVenta / 1000.0) else dia.totalVenta.toInt().toString()
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = RojoDelisa,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(110.dp * hFactor)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(RojoDelisa, RojoDelisa.copy(0.6f))
                                    )
                                )
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(iniciales[i], fontSize = 10.sp, fontWeight = FontWeight.Black, color = if(dia.totalVenta > 0) NegroPremium else Color.LightGray)
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(if(esHoy) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (dia.totalVenta > 0) VerdeExito.copy(0.1f) else Color(0xFFF5F5F5)
            ) {
                Icon(
                    imageVector = if (dia.totalVenta > 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.Rounded.Block,
                    contentDescription = null,
                    tint = if (dia.totalVenta > 0) VerdeExito else Color.Gray,
                    modifier = Modifier.padding(12.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dia.nombre.uppercase(), fontWeight = FontWeight.Black, fontSize = 15.sp, color = NegroPremium)
                    if (esHoy) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.background(RojoDelisa, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                            Text("HOY", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Text("${dia.clientesAtendidos} ventas | ${dia.totalPiezas} pzas", fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formato.format(dia.totalVenta),
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = if (dia.totalVenta > 0) NegroPremium else Color.LightGray
                )
                if (dia.totalVenta > 0) {
                    Surface(
                        onClick = onPdfClick,
                        color = RojoDelisa.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = null,
                                tint = RojoDelisa,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "VER PDF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = RojoDelisa,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

fun generarPdfReporteSemanal(context: Context, state: ReporteSemanalUiState, esDemo: Boolean = false): File {
    val pdfDocument = PdfDocument()
    val pageWidth = 612; val pageHeight = 792
    val nf = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    
    val pDelisaRed = android.graphics.Paint().apply { color = android.graphics.Color.rgb(227, 6, 19); style = android.graphics.Paint.Style.FILL; isAntiAlias = true }
    val pZebra = android.graphics.Paint().apply { color = android.graphics.Color.rgb(242, 242, 242); style = android.graphics.Paint.Style.FILL }
    val pTitle = android.graphics.Paint().apply { textSize = 22f; color = android.graphics.Color.WHITE; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD); isAntiAlias = true }
    val pSub = android.graphics.Paint().apply { textSize = 9f; color = android.graphics.Color.LTGRAY; typeface = android.graphics.Typeface.SANS_SERIF; isAntiAlias = true }
    val pText = android.graphics.Paint().apply { textSize = 11f; color = android.graphics.Color.BLACK; typeface = android.graphics.Typeface.SANS_SERIF; isAntiAlias = true }
    val pBold = android.graphics.Paint().apply { textSize = 11f; color = android.graphics.Color.BLACK; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD); isAntiAlias = true }
    
    val totalTickets = state.dias.sumOf { it.ventas.size }
    val totalPiezas = state.dias.sumOf { it.totalPiezas }
    val ticketPromedioGral = if (totalTickets > 0) state.totalVentaBrutaSemana / totalTickets else 0.0
    val efectivoNetoReal = state.totalVentaBrutaSemana - state.totalGastosSemana // 🔥 CÁLCULO DE PRECISIÓN

    var currentPageNumber = 1
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
    var page = pdfDocument.startPage(pageInfo)
    var canvas = page.canvas

    val headerH = 95f
    val headerGradient = android.graphics.LinearGradient(0f, 0f, 0f, headerH, android.graphics.Color.rgb(227, 6, 19), android.graphics.Color.rgb(160, 0, 10), android.graphics.Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerH, android.graphics.Paint().apply { shader = headerGradient })
    
    val logo = context.getDrawable(com.gruposanangel.delivery.R.drawable.logo)
    logo?.let { it.setBounds(40, 15, 130, 80); it.draw(canvas) }
    
    canvas.drawText("BALANCE SEMANAL EJECUTIVO", 160f, 40f, pTitle)
    pBold.color = android.graphics.Color.WHITE; pBold.textSize = 10f
    canvas.drawText("VENDEDOR: ${state.nombreVendedor.uppercase()}", 160f, 65f, pBold)
    canvas.drawText("RUTA: ${state.rutaNombre.uppercase()}", 160f, 85f, pBold)
    
    pBold.textAlign = android.graphics.Paint.Align.RIGHT
    val dfRange = SimpleDateFormat("dd MMM", Locale("es", "MX"))
    val rangoFechas = if (state.dias.isNotEmpty()) {
        "${dfRange.format(Date(state.dias.first().fecha))} - ${dfRange.format(Date(state.dias.last().fecha))}"
    } else ""
    canvas.drawText(rangoFechas.uppercase(), 572f, 40f, pBold)
    canvas.drawText("DELISA BOTANAS", 572f, 85f, pSub.apply { textSize = 10f; color = android.graphics.Color.WHITE; textAlign = android.graphics.Paint.Align.RIGHT })
    pBold.textAlign = android.graphics.Paint.Align.LEFT; pBold.color = android.graphics.Color.BLACK

    var y = 105f

    val kpiW = 182f; val kpiH = 50f; val kpiGap = 6f
    val startX = (pageWidth - ((kpiW * 3) + (kpiGap * 2))) / 2f
    
    fun drawKpiCard(l: String, v: String, x: Float, curY: Float, bg: Int = android.graphics.Color.rgb(250, 250, 250), txtC: Int = android.graphics.Color.BLACK) {
        canvas.drawRoundRect(x, curY, x + kpiW, curY + kpiH, 10f, 10f, android.graphics.Paint().apply { color = bg; style = android.graphics.Paint.Style.FILL })
        pSub.textAlign = android.graphics.Paint.Align.CENTER; pSub.color = android.graphics.Color.GRAY; pSub.textSize = 8f
        canvas.drawText(l.uppercase(), x + (kpiW / 2f), curY + 18f, pSub)
        pBold.textAlign = android.graphics.Paint.Align.CENTER; pBold.textSize = 12f; pBold.color = txtC
        canvas.drawText(v, x + (kpiW / 2f), curY + 40f, pBold)
    }

    drawKpiCard("Venta Bruta", nf.format(state.totalVentaBrutaSemana), startX, y, android.graphics.Color.rgb(26, 26, 26), android.graphics.Color.WHITE)
    drawKpiCard("Gastos Sem.", "-${nf.format(state.totalGastosSemana)}", startX + kpiW + kpiGap, y, android.graphics.Color.rgb(255, 235, 238), android.graphics.Color.rgb(198, 40, 40))
    drawKpiCard("Efectivo Neto", nf.format(efectivoNetoReal), startX + 2 * (kpiW + kpiGap), y, android.graphics.Color.rgb(232, 245, 233), android.graphics.Color.rgb(46, 125, 50))
    
    y += kpiH + kpiGap

    drawKpiCard("Ventas (Tickets)", "$totalTickets", startX, y)
    drawKpiCard("Ticket Promedio", nf.format(ticketPromedioGral), startX + kpiW + kpiGap, y)
    drawKpiCard("Total Piezas", "$totalPiezas", startX + 2 * (kpiW + kpiGap), y)

    y += kpiH + 1f

    val progress = if (state.metaSemanal > 0) (state.totalSemanaNeta / state.metaSemanal).coerceIn(0.0, 1.2).toFloat() else 0f
    val barWidth = 532f
    val barX = 40f
    val barY = y + 8f
    
    pBold.textSize = 8f; pBold.color = android.graphics.Color.GRAY; pBold.textAlign = android.graphics.Paint.Align.LEFT
    canvas.drawText("CUMPLIMIENTO DE META SEMANAL", barX, barY - 4f, pBold)
    pBold.textAlign = android.graphics.Paint.Align.RIGHT
    canvas.drawText("${(progress * 100).toInt()}%", barX + barWidth, barY - 4f, pBold.apply { color = if(progress >= 1f) android.graphics.Color.rgb(46, 125, 50) else android.graphics.Color.rgb(227, 6, 19) })
    
    canvas.drawRoundRect(barX, barY, barX + barWidth, barY + 5f, 2f, 2f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(240, 240, 240) })
    if (progress > 0) {
        val pBar = android.graphics.Paint().apply { color = if(progress >= 1f) android.graphics.Color.rgb(46, 125, 50) else android.graphics.Color.rgb(227, 6, 19) }
        canvas.drawRoundRect(barX, barY, barX + (barWidth * progress.coerceAtMost(1f)), barY + 5f, 2f, 2f, pBar)
    }

    y += 45f

    canvas.drawText("FLUJO DE INGRESOS POR DÍA", pageWidth / 2f, y, pBold.apply { textAlign = android.graphics.Paint.Align.CENTER; textSize = 12f })
    y += 30f
    val chartH = 120f; val barW = 35f; val chartGap = 40f
    val maxV = (state.dias.maxOfOrNull { it.totalVenta } ?: 1.0).coerceAtLeast(1.0)
    val chartStartX = (pageWidth - (barW * 7 + chartGap * 6)) / 2f

    val pGrid = android.graphics.Paint().apply { color = android.graphics.Color.rgb(240, 240, 240); strokeWidth = 1f }
    for(i in 0..4) {
        val gy = y + chartH - (i * chartH / 4)
        canvas.drawLine(40f, gy, 572f, gy, pGrid)
    }

    state.dias.forEachIndexed { i, dia ->
        val h = (dia.totalVenta / maxV * chartH).toFloat().coerceAtLeast(2f)
        val bx = chartStartX + i * (barW + chartGap)
        
        canvas.drawRoundRect(bx + 2f, y + chartH - h + 2f, bx + barW + 2f, y + chartH, 6f, 6f, android.graphics.Paint().apply { color = android.graphics.Color.argb(30, 0, 0, 0) })
        val grad = android.graphics.LinearGradient(bx, y + chartH - h, bx, y + chartH, android.graphics.Color.rgb(227, 6, 19), android.graphics.Color.rgb(180, 0, 15), android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bx, y + chartH - h, bx + barW, y + chartH, 6f, 6f, android.graphics.Paint().apply { shader = grad; isAntiAlias = true })
        
        pBold.textAlign = android.graphics.Paint.Align.CENTER; pBold.textSize = 9f
        canvas.drawText(dia.nombre.take(3).uppercase(), bx + barW / 2f, y + chartH + 18f, pBold)
        if(dia.totalVenta > 0) {
            pSub.textSize = 8f; pSub.color = android.graphics.Color.BLACK
            canvas.drawText("${"%.1fk".format(dia.totalVenta/1000.0)}", bx + barW / 2f, y + chartH - h - 8f, pSub)
        }
    }
    
    y += chartH + 55f

    fun drawRowHeader(curY: Float) {
        canvas.drawRect(40f, curY, 572f, curY + 25f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(245, 245, 245); style = android.graphics.Paint.Style.FILL })
        pBold.textAlign = android.graphics.Paint.Align.LEFT; pBold.textSize = 9f
        canvas.drawText("DÍA DE LA SEMANA", 50f, curY + 16f, pBold)
        canvas.drawText("CLIENTES", 220f, curY + 16f, pBold)
        canvas.drawText("TICKET PROM.", 330f, curY + 16f, pBold)
        canvas.drawText("TOTAL DÍA", 480f, curY + 16f, pBold)
    }
    
    drawRowHeader(y); y += 30f

    state.dias.forEachIndexed { index, dia ->
        if (index % 2 != 0) canvas.drawRect(40f, y - 5f, 572f, y + 18f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(252, 252, 252) })
        val tPromDia = if (dia.ventas.isNotEmpty()) dia.totalVenta / dia.ventas.size else 0.0
        
        // 🔥 ACTUALIZACIÓN DE PINCELES POR FILA (Evita el arrastre de color del día anterior)
        pText.color = if(dia.totalVenta > 0) android.graphics.Color.BLACK else android.graphics.Color.LTGRAY
        pBold.color = if(dia.totalVenta > 0) android.graphics.Color.rgb(227, 6, 19) else android.graphics.Color.LTGRAY
        
        canvas.drawText(dia.nombre.uppercase(), 50f, y + 12f, if(dia.totalVenta > 0) pBold else pText)
        
        pText.color = if(dia.totalVenta > 0) android.graphics.Color.BLACK else android.graphics.Color.LTGRAY
        canvas.drawText("${dia.clientesAtendidos}", 220f, y + 12f, pText)
        canvas.drawText(nf.format(tPromDia), 330f, y + 12f, pText)
        
        pBold.textAlign = android.graphics.Paint.Align.LEFT
        canvas.drawText(nf.format(dia.totalVenta), 480f, y + 12f, pBold)

        y += 23f
        canvas.drawLine(40f, y - 5f, 572f, y - 5f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(240, 240, 240); strokeWidth = 0.5f })
    }

// 🔥 SALTO DE PÁGINA PREVENTIVO PARA GASTOS
if (y > 600f) {
    pdfDocument.finishPage(page)
    currentPageNumber++
    page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create())
    canvas = page.canvas
    y = 40f
} else {
    y += 25f
}

// 🔥 PINCELES ULTRAS-INTENSOS PARA GASTOS (RE-INICIALIZACIÓN SEGURA)
val pGastoTitle = android.graphics.Paint().apply { 
    color = android.graphics.Color.rgb(227, 6, 19)
    textSize = 10f
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    isAntiAlias = true 
}
val pGastoHeaderRed = android.graphics.Paint().apply { 
    color = android.graphics.Color.rgb(227, 6, 19)
    textSize = 8.5f
    typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    isAntiAlias = true 
}

canvas.drawRect(40f, y, 572f, y + 1.5f, pDelisaRed); y += 15f
canvas.drawText("DESGLOSE DETALLADO DE GASTOS SEMANALES", 45f, y, pGastoTitle); y += 15f

// Header Tabla Gastos
fun drawGastoHeaderLocal(canv: android.graphics.Canvas, curY: Float) {
    val pHeaderBg = android.graphics.Paint().apply { color = android.graphics.Color.rgb(245, 245, 245); style = android.graphics.Paint.Style.FILL }
    canv.drawRect(40f, curY, 572f, curY + 25f, pHeaderBg)
    val textY = curY + 16f
    canv.drawText("FECHA Y HORA", 50f, textY, pGastoHeaderRed)
    canv.drawText("CATEGORÍA", 165f, textY, pGastoHeaderRed)
    canv.drawText("DESCRIPCIÓN / MOTIVO", 280f, textY, pGastoHeaderRed)
    canv.drawText("MONTO", 510f, textY, pGastoHeaderRed)
}

drawGastoHeaderLocal(canvas, y); y += 30f

if (state.todosLosGastosSemana.isEmpty()) {
    canvas.drawText("NO SE REGISTRARON GASTOS EN ESTA SEMANA", pageWidth / 2f, y + 20f, pText.apply { textAlign = android.graphics.Paint.Align.CENTER; color = android.graphics.Color.GRAY })
    y += 40f
} else {
    val fmtGasto = SimpleDateFormat("HH:mm EEEE", Locale("es", "MX"))
    val pRowBlack = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; textSize = 9.5f; typeface = android.graphics.Typeface.SANS_SERIF; isAntiAlias = true }
    val pCatBold = android.graphics.Paint().apply { color = android.graphics.Color.rgb(227, 6, 19); textSize = 9.5f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD); isAntiAlias = true }
    val pAmtRed = android.graphics.Paint().apply { color = android.graphics.Color.rgb(198, 40, 40); textSize = 10.5f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD); isAntiAlias = true }

    state.todosLosGastosSemana.forEachIndexed { index, g ->
        if (y > 720f) {
            pdfDocument.finishPage(page)
            currentPageNumber++
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create())
            canvas = page.canvas
            y = 40f
            drawGastoHeaderLocal(canvas, y); y += 30f
        }
        
        if (index % 2 != 0) {
            canvas.drawRect(40f, y - 5f, 572f, y + 18f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(252, 252, 252); style = android.graphics.Paint.Style.FILL })
        }
        
        canvas.drawText(fmtGasto.format(Date(g.fecha)).uppercase(), 50f, y + 12f, pRowBlack)
        canvas.drawText(g.categoria.uppercase(), 165f, y + 12f, pCatBold)
        val dC = if (g.descripcion.length > 30) g.descripcion.take(30) + ".." else g.descripcion
        canvas.drawText(dC, 280f, y + 12f, pRowBlack)
        canvas.drawText("-${nf.format(g.monto)}", 510f, y + 12f, pAmtRed)
        
        y += 23f
        canvas.drawLine(40f, y - 5f, 572f, y - 5f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(240, 240, 240); strokeWidth = 0.5f })
    }
}

    // --- PIE DE PÁGINA ---
    // 🔥 Salto de página si el footer va a encimar el contenido
    if (y > 730f) {
        pdfDocument.finishPage(page)
        currentPageNumber++
        val pFinal = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
        page = pdfDocument.startPage(pFinal); canvas = page.canvas
    }
    
    val footerYPos = 740f
    canvas.drawRect(40f, footerYPos, 572f, footerYPos + 1f, pDelisaRed)
    pSub.textAlign = android.graphics.Paint.Align.LEFT
    pSub.color = android.graphics.Color.GRAY
    canvas.drawText("REPORTE GENERADO EL ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())}", 50f, footerYPos + 15f, pSub)
    
    pSub.textAlign = android.graphics.Paint.Align.CENTER
    canvas.drawText("INTELIGENCIA DE VENTAS DELISA", pageWidth / 2f, footerYPos + 35f, pSub)

    pdfDocument.finishPage(page)
    val shortRoute = state.rutaNombre.replace("Ruta ", "R").replace(" Delisa", "").replace(" ", "")
    val name = "Estadisticas_Del.${shortRoute}_semana.pdf"
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), name)
    pdfDocument.writeTo(FileOutputStream(file)); pdfDocument.close()
    return file
}
