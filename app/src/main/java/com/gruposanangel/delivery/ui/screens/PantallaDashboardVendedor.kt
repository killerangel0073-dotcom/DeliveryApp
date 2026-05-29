package com.gruposanangel.delivery.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.SegundoPlano.BatteryState
import com.gruposanangel.delivery.SegundoPlano.LocationState
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import com.gruposanangel.delivery.utilidades.PantallaSeleccionImpresora
import com.gruposanangel.delivery.utilidades.VendorBatteryIndicator
import java.text.NumberFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val RojoDelisa = Color(0xFFE53935)
private val NegroPremium = Color(0xFF1E1E24)
private val GrisFondoPremium = Color(0xFFF6F8FA)
private val GrisTextoSecundario = Color(0xFF757575)

private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
} else {
    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
}

@Composable
fun PantallaDashboardVendedor(
    navController: NavController,
    impresoraSeleccionada: BluetoothDevice? = null,
    onImpresoraSeleccionada: (BluetoothDevice) -> Unit = {}
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val db = AppDatabase.getDatabase(context)
    val ventaRepository = VentaRepository(db.VentaDao())
    val firebaseDataSource = FirebaseDataSource()
    val repositoryUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())
    val userId = if (!isPreview) FirebaseAuth.getInstance().currentUser?.uid ?: "" else "preview_user"

    val viewModel: DashboardVendedorViewModel = viewModel(
        key = "Dashboard_$userId",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = 
                DashboardVendedorViewModel(ventaRepository, repositoryUsuario, userId) as T
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val estadoVelocidad by LocationState.velocidad.collectAsState()
    val estadoBateria by BatteryState.state.collectAsState()

    // Lógica de Impresora
    var showPrinterDialog by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    
    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    val bluetoothAdapter = remember { bluetoothManager.adapter }

    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) {
            pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            showPrinterDialog = true
        } else {
            Toast.makeText(context, "Se requieren permisos de Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        DashboardVendedorView(
            uiState = uiState,
            velocidadKmh = estadoVelocidad.kmh,
            bateriaNivel = estadoBateria.level,
            estaCargando = estadoBateria.isCharging,
            impresoraSeleccionada = impresoraSeleccionada,
            onToggleRuta = { viewModel.toggleRuta(it) },
            onConfigurarImpresora = {
                val hasPermission = bluetoothPermissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                if (hasPermission) {
                    pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                    showPrinterDialog = true
                } else {
                    bluetoothLauncher.launch(bluetoothPermissions)
                }
            }
        )

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(0.3f)), Alignment.Center) {
                CircularProgressIndicator(color = RojoDelisa)
            }
        }

        if (showPrinterDialog) {
            PantallaSeleccionImpresora(
                pairedDevices = pairedDevices,
                onImpresoraSeleccionada = { 
                    onImpresoraSeleccionada(it)
                    showPrinterDialog = false
                    Toast.makeText(context, "Impresora vinculada: ${it.name}", Toast.LENGTH_SHORT).show()
                },
                onCancelar = { showPrinterDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardVendedorView(
    uiState: DashboardVendedorUiState,
    velocidadKmh: Float,
    bateriaNivel: Int,
    estaCargando: Boolean = false,
    impresoraSeleccionada: BluetoothDevice? = null,
    onToggleRuta: (Boolean) -> Unit,
    onConfigurarImpresora: () -> Unit
) {
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    var showConfirmDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(GrisFondoPremium).verticalScroll(rememberScrollState())) {
        // 🔝 FILA DE ESTADO SUPERIOR ESTABILIZADA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // 🚀 Velocidad (Izquierda)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = NegroPremium.copy(0.4f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${velocidadKmh.roundToInt()} km/h",
                    fontWeight = FontWeight.Bold,
                    color = NegroPremium,
                    fontSize = 13.sp
                )
            }

            // ⏲️ Cronómetro Central (Totalmente Centrado)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = if (uiState.enRuta) RojoDelisa else NegroPremium.copy(0.2f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = uiState.tiempoTranscurrido,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = if (uiState.enRuta) RojoDelisa else NegroPremium.copy(0.5f),
                    letterSpacing = (-0.2).sp
                )
            }

            // 🔋 Batería Moderna (Derecha)
            VendorBatteryIndicator(
                batteryLevel = bateriaNivel,
                isCharging = estaCargando,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // SECCIÓN INFERIOR DEL HEADER
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (uiState.enRuta) "SEGUIMIENTO LOGÍSTICO ACTIVO" else "JORNADA DETENIDA",
                fontWeight = FontWeight.ExtraBold,
                color = if (uiState.enRuta) RojoDelisa.copy(0.6f) else Color.LightGray,
                letterSpacing = 1.sp,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(14.dp))
            
            // 🔥 Barra de Acción con Reset automático si se cancela el diálogo
            SwipeToActionPremium(
                isActive = uiState.enRuta,
                resetTrigger = showConfirmDialog, // Observamos el estado del diálogo para sincronizar posición
                onActionTriggered = { showConfirmDialog = true }
            )
        }

        Spacer(Modifier.height(16.dp))

        // 🔹 MÉTRICAS DE VENTA
        Text("MÉTRICAS DE VENTA DELISA", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = GrisTextoSecundario, letterSpacing = 1.5.sp)
        VentaPrincipalCard(uiState.ventaDia, uiState.metaDia, uiState.clientesDia, uiState.ticketPromedioDia, formatoMoneda)
        Spacer(Modifier.height(16.dp))
        WeeklyMiniChart(uiState.ventasPorDiaSemana, uiState.metaDia, formatoMoneda)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), Arrangement.spacedBy(14.dp)) {
            VentaSecundariaCard("SEMANA", uiState.ventaSemana, uiState.metaDia * 6, NegroPremium, formatoMoneda, Modifier.weight(1f))
            VentaSecundariaCard("BLOQUE", uiState.ventaBloque, uiState.metaDia * 24, RojoDelisa, formatoMoneda, Modifier.weight(1f))
        }

        Spacer(Modifier.height(36.dp))
        
        // 🔹 HARDWARE Y CONECTIVIDAD
        Text(
            text = "HARDWARE Y CONECTIVIDAD",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = GrisTextoSecundario,
            letterSpacing = 1.5.sp
        )
        
        HardwarePrinterCard(
            selectedPrinter = impresoraSeleccionada,
            onConfigurar = onConfigurarImpresora
        )
        Spacer(Modifier.height(40.dp))
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { 
                Text(
                    text = if (uiState.enRuta) "¿Terminar Jornada?" else "¿Empezar Ruta?",
                    fontWeight = FontWeight.Black,
                    color = RojoDelisa
                ) 
            },
            text = { 
                Text(
                    text = if (uiState.enRuta) 
                        "Estás por terminar tu ruta, ¿estás seguro?" 
                    else 
                        "Estás por empezar tu ruta, ¿estás seguro?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ) 
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("CANCELAR", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onToggleRuta(!uiState.enRuta); showConfirmDialog = false },
                        colors = ButtonDefaults.buttonColors(RojoDelisa),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CONFIRMAR", fontWeight = FontWeight.Black)
                    }
                }
            },
            dismissButton = null,
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun HardwarePrinterCard(selectedPrinter: BluetoothDevice?, onConfigurar: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (selectedPrinter != null) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = if (selectedPrinter != null) Color(0xFF2E7D32) else Color.Gray,
                    modifier = Modifier.padding(10.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    text = "IMPRESORA TÉRMICA",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Gray
                )
                Text(
                    text = selectedPrinter?.name ?: "No vinculada",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedPrinter != null) NegroPremium else Color.LightGray
                )
            }
            
            Button(
                onClick = onConfigurar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedPrinter != null) NegroPremium else RojoDelisa
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = if (selectedPrinter != null) "CAMBIAR" else "VINCULAR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun VentaPrincipalCard(monto: Double, meta: Double, clientes: Int, ticketPromedio: Double, formato: NumberFormat) {
    val progreso = (monto / meta).coerceIn(0.0, 1.0).toFloat()
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp).shadow(8.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(24.dp)) {
            Text("VENTA TOTAL HOY", fontSize = 11.sp, fontWeight = FontWeight.Black, color = RojoDelisa)
            Text(formato.format(monto), fontSize = 40.sp, fontWeight = FontWeight.Black, color = NegroPremium)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { progreso }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = RojoDelisa, trackColor = RojoDelisa.copy(0.1f))
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CLIENTES", fontSize = 10.sp, fontWeight = FontWeight.Black, color = GrisTextoSecundario)
                    Text(clientes.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TICKET PROM.", fontSize = 10.sp, fontWeight = FontWeight.Black, color = GrisTextoSecundario)
                    Text(formato.format(ticketPromedio), fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun VentaSecundariaCard(titulo: String, monto: Double, meta: Double, color: Color, formato: NumberFormat, modifier: Modifier) {
    val progreso = (monto / meta).coerceIn(0.0, 1.0).toFloat()
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(titulo, fontSize = 10.sp, fontWeight = FontWeight.Black, color = GrisTextoSecundario)
            Text(formato.format(monto), fontSize = 18.sp, fontWeight = FontWeight.Black, color = color)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { progreso }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = color, trackColor = color.copy(0.1f))
        }
    }
}

@Composable
fun SwipeToActionPremium(isActive: Boolean, resetTrigger: Boolean, onActionTriggered: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ancho by remember { mutableFloatStateOf(0f) }
    val densidad = LocalContext.current.resources.displayMetrics.density
    val botonPx = 60f * densidad
    val rangoPx = (ancho - botonPx - (8f * densidad)).coerceAtLeast(0f)
    
    // 💡 Offset inicial persistente para evitar saltos al cargar
    val offsetX = remember { Animatable(0f) }

    // 🌬️ Animación de Respiración sutil
    val infiniteTransition = rememberInfiniteTransition(label = "respiracion")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scaleAnim by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // 🎯 Sincronización inteligente de posición
    LaunchedEffect(isActive, rangoPx, resetTrigger) {
        if (rangoPx > 0f && !resetTrigger) {
            val target = if (isActive) rangoPx else 0f
            // Solo animamos si la diferencia es significativa (evita micro-saltos)
            if (kotlin.math.abs(offsetX.value - target) > 1f) {
                offsetX.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .graphicsLayer {
                scaleX = scaleAnim
                scaleY = scaleAnim
            }
            .background(
                brush = Brush.horizontalGradient(
                    if (isActive) listOf(RojoDelisa, Color(0xFFC62828)) 
                    else listOf(Color(0xFFEAECEF), Color(0xFFD1D5DB))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .border(1.dp, if (isActive) Color.White.copy(0.2f) else Color.Transparent, RoundedCornerShape(20.dp))
            .padding(5.dp)
            .onGloballyPositioned { 
                val newAncho = it.size.width.toFloat()
                if (ancho == 0f && newAncho > 0f) {
                    // Posicionamiento inicial sin animación
                    scope.launch { 
                        val initialRango = (newAncho - botonPx - (8f * densidad)).coerceAtLeast(0f)
                        offsetX.snapTo(if (isActive) initialRango else 0f)
                    }
                }
                ancho = newAncho
            }
    ) {
        Text(
            text = if (isActive) "← DESLIZA PARA TERMINAR" else "DESLIZA PARA INICIAR →",
            color = (if (isActive) Color.White else NegroPremium.copy(0.6f)).copy(alpha = alphaAnim),
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
        
        Box(
            Modifier
                .width(60.dp)
                .fillMaxHeight()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(0f, rangoPx))
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            val medio = rangoPx / 2f
                            if (offsetX.value > medio) {
                                // Intento de activar (Hacia la derecha)
                                if (!isActive) {
                                    onActionTriggered() // Abre diálogo, nos quedamos en el % actual
                                } else {
                                    // Ya estaba activa, regresamos al final
                                    offsetX.animateTo(rangoPx, spring(Spring.DampingRatioMediumBouncy))
                                }
                            } else {
                                // Intento de desactivar (Hacia la izquierda)
                                if (isActive) {
                                    onActionTriggered() // Abre diálogo, nos quedamos en el % actual
                                } else {
                                    // Ya estaba desactivada, regresamos al inicio
                                    offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                }
                            }
                        }
                    }
                )
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .background(Color.White, RoundedCornerShape(16.dp)),
            Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.KeyboardDoubleArrowLeft else Icons.Default.KeyboardDoubleArrowRight,
                contentDescription = null,
                tint = if (isActive) RojoDelisa else NegroPremium,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun WeeklyMiniChart(ventasPorDia: List<Double>, metaDia: Double, formato: NumberFormat) {
    var diaSeleccionado by remember { mutableStateOf<Int?>(null) }
    val maxVenta = remember(ventasPorDia) { 
        (ventasPorDia.maxOfOrNull { it } ?: 0.0).coerceAtLeast(metaDia) 
    }
    
    // Colores Modernos "Tesla Style"
    val verdeExito = Color(0xFF2E7D32)
    val rojoAlerta = Color(0xFFE53935)
    val grisNeutro = Color(0xFFBDBDBD)
    val fondoBarra = Color(0xFFF1F4F9)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "DESEMPEÑO SEMANAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.LightGray,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = if (diaSeleccionado != null) "DETALLE DIARIO" else "PROMEDIO 6 DÍAS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NegroPremium
                    )
                }
                
                if (diaSeleccionado != null) {
                    Surface(
                        color = RojoDelisa.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            formato.format(ventasPorDia[diaSeleccionado!!]),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = RojoDelisa
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(28.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val dias = listOf("LUN", "MAR", "MIE", "JUE", "VIE", "SAB")
                ventasPorDia.forEachIndexed { i, monto ->
                    val pctMeta = (monto / metaDia).toFloat()
                    val hFactor = (monto / maxVenta).coerceIn(0.08, 1.0).toFloat()
                    
                    val barColor = when {
                        pctMeta >= 1.0f -> verdeExito
                        pctMeta >= 0.75f -> rojoAlerta
                        else -> grisNeutro
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(100.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (diaSeleccionado == i) RojoDelisa.copy(0.05f) else fondoBarra)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    diaSeleccionado = if (diaSeleccionado == i) null else i
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Barra de progreso con gradiente sutil
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(hFactor)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                barColor.copy(alpha = 0.8f),
                                                barColor
                                            )
                                        )
                                    )
                            )
                            
                            // Indicador de "Selección" en la base
                            if (diaSeleccionado == i) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(RojoDelisa)
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(10.dp))
                        
                        Text(
                            text = dias[i],
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (diaSeleccionado == i) RojoDelisa else Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Vendedor Dashboard - Ruta Activa")
@Composable
fun DashboardVendedorActivePreview() {
    val sampleVentas = listOf(4500.0, 3200.0, 5100.0, 4800.0, 2900.0, 1500.0)
    val state = DashboardVendedorUiState(
        enRuta = true, 
        tiempoTranscurrido = "04:15:22", 
        ventaDia = 3450.0, 
        metaDia = 5000.0, 
        clientesDia = 12, 
        ticketPromedioDia = 287.5, 
        ventaSemana = 15000.0, 
        ventaBloque = 60000.0,
        ventasPorDiaSemana = sampleVentas
    )
    DeliveryTheme { DashboardVendedorView(state, 45f, 75, false, null, {}, {}) }
}

@Preview(showBackground = true, showSystemUi = true, name = "Vendedor Dashboard - Detenido")
@Composable
fun DashboardVendedorStoppedPreview() {
    val state = DashboardVendedorUiState(enRuta = false, tiempoTranscurrido = "00:00:00", ventaDia = 0.0, metaDia = 5000.0)
    DeliveryTheme { DashboardVendedorView(state, 0f, 100, false, null, {}, {}) }
}
