package com.gruposanangel.delivery.ui.screens

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Environment
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
import com.gruposanangel.delivery.data.VentaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

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
    val ventas: List<VentaEntity> = emptyList()
)

data class ReporteSemanalUiState(
    val isLoading: Boolean = false,
    val totalSemana: Double = 0.0,
    val dias: List<DiaReporte> = emptyList(),
    val nombreVendedor: String = "",
    val rutaNombre: String = "",
    val metaSemanal: Double = 70000.0
)

class ReporteSemanalViewModel(
    private val ventaRepository: VentaRepository,
    private val usuarioRepository: RepositoryUsuario,
    private val inventarioRepository: RepositoryInventario,
    private val userId: String,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReporteSemanalUiState())
    val uiState: StateFlow<ReporteSemanalUiState> = _uiState.asStateFlow()

    private val prefs = context.getSharedPreferences("reporte_semanal_prefs", Context.MODE_PRIVATE)

    init {
        val metaGuardada = prefs.getFloat("meta_semanal_$userId", 70000f).toDouble()
        _uiState.update { it.copy(metaSemanal = metaGuardada) }
        cargarDatos()
    }

    fun actualizarMeta(nuevaMeta: Double) {
        viewModelScope.launch {
            prefs.edit().putFloat("meta_semanal_$userId", nuevaMeta.toFloat()).apply()
            _uiState.update { it.copy(metaSemanal = nuevaMeta) }
        }
    }

    private fun cargarDatos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val user = usuarioRepository.obtenerUsuarioActual()
            val nombreVendedor = user?.nombre ?: "Vendedor"
            val rutaNombre = user?.ultimoAlmacenNombre ?: "Ruta General"

            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            
            if (cal.timeInMillis > System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_MONTH, -7)
            }

            val diasReporte = mutableListOf<DiaReporte>()
            var totalSemana = 0.0
            val nombresDias = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

            for (i in 0..6) {
                val inicioDia = cal.timeInMillis
                cal.add(Calendar.DAY_OF_MONTH, 1)
                val finDia = cal.timeInMillis - 1
                
                val ventasDia = ventaRepository.obtenerVentasPorPeriodo(userId, inicioDia, finDia)
                val totalDia = ventasDia.sumOf { it.total }
                
                var totalPiezasDia = 0
                ventasDia.forEach { v ->
                    totalPiezasDia += ventaRepository.obtenerDetallesDeVenta(v.id).sumOf { it.cantidad }
                }

                diasReporte.add(DiaReporte(
                    nombre = nombresDias[i],
                    fecha = inicioDia,
                    totalVenta = totalDia,
                    totalPiezas = totalPiezasDia,
                    clientesAtendidos = ventasDia.map { it.clienteId }.distinct().size,
                    ventas = ventasDia
                ))
                totalSemana += totalDia
            }

            _uiState.update { it.copy(
                isLoading = false,
                totalSemana = totalSemana,
                dias = diasReporte,
                nombreVendedor = nombreVendedor,
                rutaNombre = rutaNombre
            ) }
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
    userId: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: ReporteSemanalViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = 
                ReporteSemanalViewModel(ventaRepository, usuarioRepository, inventarioRepository, userId, context) as T
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    var showMetaDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = GrisFondoPremium,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("ESTADÍSTICAS", fontWeight = FontWeight.Black, fontSize = 18.sp, color = NegroPremium)
                        Text(uiState.rutaNombre.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Rounded.ArrowBackIosNew, null, tint = RojoDelisa)
                    }
                },
                actions = {
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
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = RojoDelisa) }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    BalanceCardPremium(
                        total = uiState.totalSemana,
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
                                        estado = "VENTA"
                                    )
                                }.sortedWith(compareBy({ it.hora }, { it.minutos }))
                            }
                            val stateFake = DashboardVendedorUiState(
                                nombreVendedor = uiState.nombreVendedor,
                                rutaNombre = uiState.rutaNombre,
                                ventaDia = dia.totalVenta
                            )
                            val file = GenerarPDFCierreCarta(context, stateFake, ventasItems, false)
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
                    Text("VENTA TOTAL SEMANAL", color = Color.White.copy(0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
            Spacer(Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val iniciales = listOf("L", "M", "M", "J", "V", "S", "D")
                dias.forEachIndexed { i, dia ->
                    val hFactor = (dia.totalVenta / maxVenta).coerceIn(0.05, 1.0).toFloat()
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .fillMaxHeight(hFactor)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(RojoDelisa, RojoDelisa.copy(0.6f))
                                    )
                                ),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            if (dia.totalVenta > 0 && hFactor > 0.3) {
                                Text(
                                    text = "${(dia.totalVenta / 1000).toInt()}k",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(iniciales[i], fontSize = 10.sp, fontWeight = FontWeight.Black, color = if(dia.totalVenta > 0) NegroPremium else Color.LightGray)
                    }
                }
            }
        }
    }
}

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
                    Text(dia.nombre.uppercase(), fontWeight = FontWeight.Black, fontSize = 14.sp, color = NegroPremium)
                    if (esHoy) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.background(RojoDelisa, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                            Text("HOY", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Text("${dia.clientesAtendidos} ventas | ${dia.totalPiezas} pzas", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formato.format(dia.totalVenta),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = if (dia.totalVenta > 0) NegroPremium else Color.LightGray
                )
                if (dia.totalVenta > 0) {
                    Text(
                        text = "Generar Ticket",
                        modifier = Modifier.clickable { onPdfClick() },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = RojoDelisa
                    )
                }
            }
        }
    }
}

fun generarPdfReporteSemanal(context: Context, state: ReporteSemanalUiState): File {
    val pdfDocument = PdfDocument()
    val pageWidth = 612; val pageHeight = 792
    val nf = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    
    // --- PINCELES PREMIUM ---
    val pDelisaRed = android.graphics.Paint().apply { color = android.graphics.Color.rgb(227, 6, 19); style = android.graphics.Paint.Style.FILL; isAntiAlias = true }
    val pTitle = android.graphics.Paint().apply { textSize = 22f; color = android.graphics.Color.WHITE; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD); isAntiAlias = true }
    val pSub = android.graphics.Paint().apply { textSize = 9f; color = android.graphics.Color.LTGRAY; typeface = android.graphics.Typeface.SANS_SERIF; isAntiAlias = true }
    val pText = android.graphics.Paint().apply { textSize = 11f; color = android.graphics.Color.BLACK; typeface = android.graphics.Typeface.SANS_SERIF; isAntiAlias = true }
    val pBold = android.graphics.Paint().apply { textSize = 11f; color = android.graphics.Color.BLACK; typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD); isAntiAlias = true }
    
    // --- CÁLCULOS ---
    val diasActivos = state.dias.filter { it.totalVenta > 0 }
    val totalTickets = state.dias.sumOf { it.ventas.size }
    val ticketPromedioGral = if (totalTickets > 0) state.totalSemana / totalTickets else 0.0
    val totalPiezas = state.dias.sumOf { it.totalPiezas }

    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    // --- ENCABEZADO "TESLA RED HUD" ---
    val headerH = 110f
    val headerGradient = android.graphics.LinearGradient(0f, 0f, 0f, headerH, android.graphics.Color.rgb(227, 6, 19), android.graphics.Color.rgb(160, 0, 10), android.graphics.Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, pageWidth.toFloat(), headerH, android.graphics.Paint().apply { shader = headerGradient })
    
    val logo = context.getDrawable(com.gruposanangel.delivery.R.drawable.logo)
    logo?.let { it.setBounds(40, 20, 130, 85); it.draw(canvas) }
    
    canvas.drawText("BALANCE SEMANAL EJECUTIVO", 160f, 45f, pTitle)
    pBold.color = android.graphics.Color.WHITE; pBold.textSize = 10f
    canvas.drawText("VENDEDOR: ${state.nombreVendedor.uppercase()}", 160f, 75f, pBold)
    canvas.drawText("RUTA: ${state.rutaNombre.uppercase()}", 160f, 95f, pBold)
    
    pBold.textAlign = android.graphics.Paint.Align.RIGHT
    val dfRange = SimpleDateFormat("dd MMM", Locale("es", "MX"))
    val rangoFechas = "${dfRange.format(Date(state.dias.first().fecha))} - ${dfRange.format(Date(state.dias.last().fecha))}"
    canvas.drawText(rangoFechas.uppercase(), 572f, 45f, pBold)
    // Corregido: Ajustado margen derecho para que no se salga
    canvas.drawText("DELISA BOTANAS", 572f, 95f, pSub.apply { textSize = 10f; color = android.graphics.Color.WHITE; textAlign = android.graphics.Paint.Align.RIGHT })
    pBold.textAlign = android.graphics.Paint.Align.LEFT; pBold.color = android.graphics.Color.BLACK

    var y = 145f

    // --- KPIs PRINCIPALES ---
    val kpiW = 135f; val kpiH = 50f; val kpiGap = 8f
    val startX = (pageWidth - ((kpiW * 4) + (kpiGap * 3))) / 2f
    
    fun drawKpiCard(l: String, v: String, x: Float, bg: Int = android.graphics.Color.rgb(250, 250, 250)) {
        canvas.drawRoundRect(x, y, x + kpiW, y + kpiH, 10f, 10f, android.graphics.Paint().apply { color = bg; style = android.graphics.Paint.Style.FILL })
        pSub.textAlign = android.graphics.Paint.Align.CENTER; pSub.color = android.graphics.Color.GRAY; pSub.textSize = 8f
        canvas.drawText(l.uppercase(), x + kpiW / 2f, y + 18f, pSub)
        pBold.textAlign = android.graphics.Paint.Align.CENTER; pBold.textSize = 13f
        canvas.drawText(v, x + kpiW / 2f, y + 40f, pBold)
    }

    drawKpiCard("Venta Bruta", nf.format(state.totalSemana), startX, android.graphics.Color.rgb(26, 26, 26).also { pBold.color = android.graphics.Color.WHITE })
    pBold.color = android.graphics.Color.BLACK
    drawKpiCard("Meta Semanal", nf.format(state.metaSemanal), startX + kpiW + kpiGap)
    drawKpiCard("Ticket Prom.", nf.format(ticketPromedioGral), startX + 2 * (kpiW + kpiGap))
    drawKpiCard("Total Piezas", "$totalPiezas", startX + 3 * (kpiW + kpiGap))

    // --- INDICADOR DE CUMPLIMIENTO (CON ESPACIADO AJUSTADO) ---
    val progress = if (state.metaSemanal > 0) (state.totalSemana / state.metaSemanal).coerceIn(0.0, 1.2).toFloat() else 0f
    val barWidth = 532f
    val barX = 40f
    val barY = y + 80f // Aumentado de 62f a 80f para dar más aire
    
    pBold.textSize = 8f; pBold.color = android.graphics.Color.GRAY; pBold.textAlign = android.graphics.Paint.Align.LEFT
    canvas.drawText("CUMPLIMIENTO DE META SEMANAL", barX, barY - 6f, pBold)
    pBold.textAlign = android.graphics.Paint.Align.RIGHT
    canvas.drawText("${(progress * 100).toInt()}%", barX + barWidth, barY - 6f, pBold.apply { color = if(progress >= 1f) android.graphics.Color.rgb(46, 125, 50) else android.graphics.Color.rgb(227, 6, 19) })
    
    canvas.drawRoundRect(barX, barY, barX + barWidth, barY + 6f, 3f, 3f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(240, 240, 240) })
    if (progress > 0) {
        val pBar = android.graphics.Paint().apply { color = if(progress >= 1f) android.graphics.Color.rgb(46, 125, 50) else android.graphics.Color.rgb(227, 6, 19) }
        canvas.drawRoundRect(barX, barY, barX + (barWidth * progress.coerceAtMost(1f)), barY + 6f, 3f, 3f, pBar)
    }

    y += 105f // Aumentado para que lo que sigue no se encime

    // --- GRÁFICA DE BARRAS ESPECTACULAR ---
    canvas.drawText("FLUJO DE INGRESOS POR DÍA", pageWidth / 2f, y, pBold.apply { textAlign = android.graphics.Paint.Align.CENTER; textSize = 12f })
    y += 30f
    val chartH = 120f; val barW = 35f; val chartGap = 40f
    val maxV = (state.dias.maxOfOrNull { it.totalVenta } ?: 1.0).coerceAtLeast(1.0)
    val chartStartX = (pageWidth - (barW * 7 + chartGap * 6)) / 2f

    // Líneas de fondo
    val pGrid = android.graphics.Paint().apply { color = android.graphics.Color.rgb(240, 240, 240); strokeWidth = 1f }
    for(i in 0..4) {
        val gy = y + chartH - (i * chartH / 4)
        canvas.drawLine(40f, gy, 572f, gy, pGrid)
    }

    state.dias.forEachIndexed { i, dia ->
        val h = (dia.totalVenta / maxV * chartH).toFloat().coerceAtLeast(2f)
        val bx = chartStartX + i * (barW + chartGap)
        
        // Sombra de la barra
        canvas.drawRoundRect(bx + 2f, y + chartH - h + 2f, bx + barW + 2f, y + chartH, 6f, 6f, android.graphics.Paint().apply { color = android.graphics.Color.argb(30, 0, 0, 0) })
        // Barra principal
        val grad = android.graphics.LinearGradient(bx, y + chartH - h, bx, y + chartH, android.graphics.Color.rgb(227, 6, 19), android.graphics.Color.rgb(180, 0, 15), android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bx, y + chartH - h, bx + barW, y + chartH, 6f, 6f, android.graphics.Paint().apply { shader = grad; isAntiAlias = true })
        
        pBold.textAlign = android.graphics.Paint.Align.CENTER; pBold.textSize = 9f
        canvas.drawText(dia.nombre.take(3).uppercase(), bx + barW / 2f, y + chartH + 18f, pBold)
        if(dia.totalVenta > 0) {
            pSub.textSize = 8f; pSub.color = android.graphics.Color.BLACK
            canvas.drawText("${(dia.totalVenta/1000).toInt()}k", bx + barW / 2f, y + chartH - h - 8f, pSub)
        }
    }
    
    y += chartH + 55f

    // --- TABLA DE DESGLOSE DIARIO (LIMPIA Y MODERNA) ---
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
        
        pText.color = if(dia.totalVenta > 0) android.graphics.Color.BLACK else android.graphics.Color.LTGRAY
        canvas.drawText(dia.nombre.uppercase(), 50f, y + 12f, if(dia.totalVenta > 0) pBold else pText)
        canvas.drawText("${dia.clientesAtendidos}", 220f, y + 12f, pText)
        canvas.drawText(nf.format(tPromDia), 330f, y + 12f, pText)
        
        pBold.textAlign = android.graphics.Paint.Align.LEFT
        pBold.color = if(dia.totalVenta > 0) android.graphics.Color.rgb(227, 6, 19) else android.graphics.Color.LTGRAY
        canvas.drawText(nf.format(dia.totalVenta), 480f, y + 12f, pBold)
        
        y += 23f
        canvas.drawLine(40f, y - 5f, 572f, y - 5f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(240, 240, 240); strokeWidth = 0.5f })
    }

    // --- PIE DE PÁGINA ---
    canvas.drawRect(40f, 740f, 572f, 741f, pDelisaRed)
    // Corregido: Alineación y márgenes para evitar que se salga a la izquierda
    pSub.textAlign = android.graphics.Paint.Align.LEFT
    pSub.color = android.graphics.Color.GRAY
    canvas.drawText("REPORTE GENERADO EL ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())}", 50f, 755f, pSub)
    
    pSub.textAlign = android.graphics.Paint.Align.CENTER
    canvas.drawText("INTELIGENCIA DE VENTAS DELISA", pageWidth / 2f, 775f, pSub)

    pdfDocument.finishPage(page)
    val name = "BALANCE_SEMANAL_${state.nombreVendedor.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), name)
    pdfDocument.writeTo(FileOutputStream(file)); pdfDocument.close()
    return file
}
