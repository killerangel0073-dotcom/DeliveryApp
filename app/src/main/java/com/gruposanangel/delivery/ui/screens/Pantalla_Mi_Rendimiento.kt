package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class RendimientoUiState(
    val isLoading: Boolean = false,
    val nombreVendedor: String = "",
    val totalVentaSemana: Double = 0.0,
    val diasTrabajados: Int = 0,
    val sueldoBaseAcumulado: Double = 0.0,
    val comisionAcumulada: Double = 0.0,
    val totalGaneSemana: Double = 0.0,
    val fechaInicioSemana: Long = 0L,
    val desgloseDias: List<DiaGane> = emptyList(),
    val sueldoBaseConfig: Double = 300.0,
    val comisionConfig: Double = 3.0
)

data class DiaGane(
    val nombre: String,
    val fecha: Long,
    val venta: Double,
    val comision: Double,
    val trabajado: Boolean
)

class RendimientoViewModel(
    private val ventaRepository: VentaRepository,
    private val usuarioRepository: RepositoryUsuario,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(RendimientoUiState())
    val uiState: StateFlow<RendimientoUiState> = _uiState.asStateFlow()

    private val _fechaFiltro = MutableStateFlow<Long?>(null)
    private val _configPagos = MutableStateFlow(Pair(300.0, 3.0))

    init {
        // Escuchar configuración global de pagos
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("config").document("pagos")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val sueldo = snapshot.getDouble("sueldo_base") ?: 300.0
                    val comision = snapshot.getDouble("comision_porcentaje") ?: 3.0
                    _configPagos.value = Pair(sueldo, comision)
                }
            }

        combine(_fechaFiltro.filterNotNull(), _configPagos) { fechaLunes, config ->
            val (sueldoBase, comisionPct) = config
            val finMs = fechaLunes + (7L * 24 * 60 * 60 * 1000) - 1
            
            _uiState.update { it.copy(
                isLoading = true, 
                fechaInicioSemana = fechaLunes,
                sueldoBaseConfig = sueldoBase,
                comisionConfig = comisionPct
            ) }

            // Cargar datos del perfil si no están
            viewModelScope.launch {
                val user = usuarioRepository.obtenerUsuarioLocal(userId)
                _uiState.update { it.copy(nombreVendedor = user?.nombre ?: "Vendedor") }
                ventaRepository.sincronizarVentasPeriodo(userId, fechaLunes, finMs)
            }

            ventaRepository.obtenerVentasPorPeriodoFlow(userId, fechaLunes, finMs)
                .map { ventas -> procesarRendimiento(fechaLunes, ventas, sueldoBase, comisionPct) }
        }.flatMapLatest { it }
        .onEach { nuevoEstado ->
            _uiState.update { nuevoEstado }
        }.launchIn(viewModelScope)

        // Inicializar con la semana actual
        cambiarSemana(System.currentTimeMillis())
    }

    private fun procesarRendimiento(
        fechaLunes: Long, 
        ventas: List<VentaEntity>,
        sueldoBaseDia: Double,
        comisionPct: Double
    ): RendimientoUiState {
        val nombresDias = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        val diasGane = mutableListOf<DiaGane>()
        
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { 
            timeInMillis = fechaLunes
        }

        var totalVenta = 0.0
        var diasConActividad = 0

        for (i in 0..6) {
            val inicioDia = cal.timeInMillis
            val finDia = inicioDia + (24L * 60 * 60 * 1000) - 1
            
            val ventasDia = ventas.filter { it.fecha in inicioDia..finDia && it.estado != "CANCELADA" }
            val ventaMonto = ventasDia.sumOf { it.total }
            
            val trabajado = ventasDia.isNotEmpty()
            if (trabajado) diasConActividad++

            diasGane.add(DiaGane(
                nombre = nombresDias[i],
                fecha = inicioDia,
                venta = ventaMonto,
                comision = ventaMonto * (comisionPct / 100.0),
                trabajado = trabajado
            ))
            
            totalVenta += ventaMonto
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }

        val sueldoBaseTotal = diasConActividad * sueldoBaseDia
        val comisionTotal = totalVenta * (comisionPct / 100.0)

        return _uiState.value.copy(
            isLoading = false,
            totalVentaSemana = totalVenta,
            diasTrabajados = diasConActividad,
            sueldoBaseAcumulado = sueldoBaseTotal,
            comisionAcumulada = comisionTotal,
            totalGaneSemana = sueldoBaseTotal + comisionTotal,
            desgloseDias = diasGane,
            sueldoBaseConfig = sueldoBaseDia,
            comisionConfig = comisionPct
        )
    }

    fun cambiarSemana(nuevaFecha: Long) {
        viewModelScope.launch {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { 
                timeInMillis = nuevaFecha 
            }
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                cal.add(Calendar.DAY_OF_MONTH, -1)
            }
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            _fechaFiltro.value = cal.timeInMillis
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pantalla_Mi_Rendimiento(
    navController: NavController,
    ventaRepository: VentaRepository,
    usuarioRepository: RepositoryUsuario,
    userId: String
) {
    val context = LocalContext.current
    val viewModel: RendimientoViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = 
                RendimientoViewModel(ventaRepository, usuarioRepository, userId) as T
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    
    var showDatePicker by remember { mutableStateOf(false) }
    
    val dfRange = remember { SimpleDateFormat("dd MMM", Locale.forLanguageTag("es-MX")).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    val rangoFechas = remember(uiState.fechaInicioSemana) {
        val calEnd = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { 
            timeInMillis = uiState.fechaInicioSemana
            add(Calendar.DAY_OF_YEAR, 6)
        }
        "${dfRange.format(Date(uiState.fechaInicioSemana))} - ${dfRange.format(calEnd.time)}".uppercase()
    }

    val isDark = ThemeConfig.isDarkTheme.value ?: isSystemInDarkTheme()

    DeliveryTheme(darkTheme = isDark) {
        if (showDatePicker) {
            val dateRangePickerState = rememberDateRangePickerState(
                initialSelectedStartDateMillis = uiState.fechaInicioSemana,
                initialSelectedEndDateMillis = uiState.fechaInicioSemana + (6L * 24 * 60 * 60 * 1000)
            )

            LaunchedEffect(dateRangePickerState.selectedStartDateMillis, dateRangePickerState.selectedEndDateMillis) {
                val selection = dateRangePickerState.selectedEndDateMillis ?: dateRangePickerState.selectedStartDateMillis
                selection?.let { millis ->
                    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
                    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) { cal.add(Calendar.DAY_OF_MONTH, -1) }
                    val lunesMs = cal.timeInMillis
                    cal.add(Calendar.DAY_OF_MONTH, 6)
                    val domingoMs = cal.timeInMillis
                    if (dateRangePickerState.selectedStartDateMillis != lunesMs || dateRangePickerState.selectedEndDateMillis != domingoMs) {
                        dateRangePickerState.setSelection(lunesMs, domingoMs)
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showDatePicker = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth(0.95f),
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                dateRangePickerState.selectedStartDateMillis?.let { viewModel.cambiarSemana(it) }
                                showDatePicker = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("ACEPTAR", fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                text = {
                    Box(modifier = Modifier.fillMaxWidth().height(450.dp)) {
                        DateRangePicker(
                            state = dateRangePickerState,
                            title = { Text("Selecciona la semana", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                            showModeToggle = false,
                            headline = {
                                val start = dateRangePickerState.selectedStartDateMillis
                                val end = dateRangePickerState.selectedEndDateMillis
                                if (start != null && end != null) {
                                    Text(
                                        text = "${dfRange.format(Date(start))} - ${dfRange.format(Date(end))}".uppercase(),
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        fontWeight = FontWeight.Black,
                                        color = DelisaRed
                                    )
                                }
                            },
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
            )
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { 
                        Column {
                            Text("MI BOLSO", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(rangoFechas, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Rounded.CalendarMonth, null, tint = DelisaRed)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        ResumenGaneCard(
                            totalGane = uiState.totalGaneSemana,
                            formato = formatoMoneda,
                            nombre = uiState.nombreVendedor
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MiniKPI(
                                titulo = "Sueldo Base",
                                valor = formatoMoneda.format(uiState.sueldoBaseAcumulado),
                                subtitulo = "Base: $${uiState.sueldoBaseConfig.toInt()}/día\n${uiState.diasTrabajados} días lab.",
                                color = MaterialTheme.colorScheme.onSurface,
                                icon = Icons.Rounded.WorkHistory,
                                modifier = Modifier.weight(1f)
                            )
                            MiniKPI(
                                titulo = "Comisión",
                                valor = formatoMoneda.format(uiState.comisionAcumulada),
                                subtitulo = "Pct: ${uiState.comisionConfig}%\nS/ Venta: ${formatoMoneda.format(uiState.totalVentaSemana)}",
                                color = DelisaRed,
                                icon = Icons.Rounded.Percent,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Text(
                            text = "DESGLOSE DIARIO",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.5.sp
                        )
                    }

                    items(uiState.desgloseDias.size) { index ->
                        val dia = uiState.desgloseDias[index]
                        DiaGaneItem(dia, formatoMoneda, uiState.sueldoBaseConfig)
                    }
                }

                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(0.4f)), Alignment.Center) {
                        CircularProgressIndicator(color = DelisaRed)
                    }
                }
            }
        }
    }
}

@Composable
fun ResumenGaneCard(totalGane: Double, formato: NumberFormat, nombre: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .shadow(20.dp, RoundedCornerShape(32.dp), ambientColor = DelisaRed.copy(0.4f)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(DelisaRed, DelisaRedDark)
                    )
                )
                .padding(28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "GANANCIA TOTAL ESTIMADA",
                        color = Color.White.copy(0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formato.format(totalGane),
                        color = Color.White,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                }
                
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Rounded.Savings, 
                        null, 
                        tint = Color.White, 
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    nombre.uppercase(), 
                    color = Color.White, 
                    fontSize = 12.sp, 
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun MiniKPI(titulo: String, valor: String, subtitulo: String, color: Color, icon: ImageVector, modifier: Modifier) {
    Card(
        modifier = modifier.height(140.dp).shadow(2.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxSize(), 
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = color.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        icon, 
                        null, 
                        tint = color, 
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            
            Column {
                Text(
                    titulo.uppercase(), 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    fontSize = 9.sp, 
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    valor, 
                    color = color, 
                    fontSize = 22.sp, 
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitulo, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun DiaGaneItem(dia: DiaGane, formato: NumberFormat, sueldoBase: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .shadow(1.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (dia.trabajado) DelisaGreen.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (dia.trabajado) Icons.Rounded.EventAvailable else Icons.Rounded.EventBusy,
                        null,
                        tint = if (dia.trabajado) DelisaGreenDark else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    dia.nombre.uppercase(), 
                    fontWeight = FontWeight.Black, 
                    fontSize = 15.sp, 
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (dia.trabajado) "VENTA: ${formato.format(dia.venta)}" else "SIN ACTIVIDAD",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (dia.trabajado) MaterialTheme.colorScheme.onSurfaceVariant else DelisaRed.copy(0.6f)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formato.format(if (dia.trabajado) sueldoBase + dia.comision else 0.0),
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = if (dia.trabajado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                if (dia.trabajado) {
                    Surface(
                        color = DelisaGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "GANANCIA DÍA", 
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 8.sp, 
                            color = DelisaGreenDark,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
