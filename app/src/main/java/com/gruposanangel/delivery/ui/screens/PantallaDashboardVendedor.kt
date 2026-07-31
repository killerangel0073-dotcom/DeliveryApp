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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.SegundoPlano.BatteryState
import com.gruposanangel.delivery.SegundoPlano.LocationState
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.ui.theme.*
import com.gruposanangel.delivery.utilidades.PantallaSeleccionImpresora
import com.gruposanangel.delivery.utilidades.VendorBatteryIndicator
import java.text.NumberFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    val ventaRepository = VentaRepository(db.VentaDao(), db.productoDao())
    val firebaseDataSource = FirebaseDataSource()
    val repositoryUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())
    val userId = if (!isPreview) FirebaseAuth.getInstance().currentUser?.uid ?: "" else "preview_user"
    val inventarioRepo = RepositoryInventario(FirebaseDataSource(), db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
    val gastoRepo = RepositoryGasto(db.gastoDao())

    val viewModel: DashboardVendedorViewModel = viewModel(
        key = "Dashboard_$userId",
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = 
                DashboardVendedorViewModel(ventaRepository, repositoryUsuario, inventarioRepo, gastoRepo, userId) as T
        }
    )

    val uiState by viewModel.uiState.collectAsState()
    val estadoVelocidad by LocationState.velocidad.collectAsState()
    val estadoBateria by BatteryState.state.collectAsState()

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

    val isDark = ThemeConfig.isActuallyDark

    DeliveryTheme(darkTheme = isDark) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            DashboardVendedorView(
                uiState = uiState,
                velocidadKmh = estadoVelocidad.kmh,
                bateriaNivel = estadoBateria.level,
                estaCargando = estadoBateria.isCharging,
                impresoraSeleccionada = impresoraSeleccionada,
                onToggleRuta = { viewModel.toggleRuta(it) },
                onArqueoClick = { navController.navigate("PANTALLA_ARQUEO") },
                onNavigate = { navController.navigate(it) },
                onGastoRegistrado = { m, c, d, cb -> viewModel.registrarGasto(m, c, d, cb) },
                onMetaFriturasChange = { viewModel.actualizarMetaFrituras(it) },
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
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(0.3f)), Alignment.Center) {
                    CircularProgressIndicator(color = DelisaRed)
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
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DashboardVendedorView(
    uiState: DashboardVendedorUiState,
    velocidadKmh: Float,
    bateriaNivel: Int,
    estaCargando: Boolean = false,
    impresoraSeleccionada: BluetoothDevice? = null,
    onToggleRuta: (Boolean) -> Unit,
    onArqueoClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onGastoRegistrado: (Double, String, String, () -> Unit) -> Unit,
    onMetaFriturasChange: (Int) -> Unit,
    onConfigurarImpresora: () -> Unit
) {
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showGastoSheet by remember { mutableStateOf(false) }
    var showMetaDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // 🔝 FILA DE ESTADO SUPERIOR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // 🚀 Velocidad
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${velocidadKmh.roundToInt()} km/h",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp
                )
            }

            // ⏲️ Cronómetro Central
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = if (uiState.enRuta) DelisaRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = uiState.tiempoTranscurrido,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = if (uiState.enRuta) DelisaRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                    letterSpacing = (-0.2).sp
                )
            }

            // 🔋 Batería
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
                color = if (uiState.enRuta) DelisaRed.copy(0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                letterSpacing = 1.sp,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(14.dp))
            
            SwipeToActionPremium(
                isActive = uiState.enRuta,
                resetTrigger = showConfirmDialog,
                onActionTriggered = { showConfirmDialog = true }
            )
        }

        // 🔹 SECCIÓN DE GESTIÓN Y MÉTRICAS
        val listState = rememberLazyListState()
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val density = androidx.compose.ui.platform.LocalDensity.current
        
        LaunchedEffect(Unit) {
            val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
            val itemWidthPx = with(density) { 160.dp.toPx() }
            val spacingPx = with(density) { 12.dp.toPx() }
            val totalContentWidthPx = (itemWidthPx * 4) + (spacingPx * 3)
            
            if (totalContentWidthPx > screenWidthPx) {
                val centerOffset = (totalContentWidthPx - screenWidthPx) / 2
                listState.scrollToItem(0, centerOffset.toInt())
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GESTIÓN Y MÉTRICAS DELISA", 
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp), 
                style = MaterialTheme.typography.labelMedium, 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.onSurfaceVariant, 
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center
            )

            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(lazyListState = listState)
            ) {
                item {
                    val comisionDia = uiState.ventaDia * (uiState.comisionPctConfig / 100.0)
                    val sueldoTotalHoy = uiState.sueldoBaseConfig + comisionDia
                    
                    BloqueBolso(
                        titulo = "MI BOLSO",
                        montoTotalHoy = sueldoTotalHoy,
                        comisionHoy = comisionDia,
                        formato = formatoMoneda,
                        modifier = Modifier.width(160.dp)
                    ) {
                        onNavigate("MI_RENDIMIENTO")
                    }
                }
                item {
                    VentaSecundariaCard(
                        titulo = "ESTA SEMANA",
                        monto = uiState.ventaSemana,
                        meta = uiState.metaDia * 6,
                        color = MaterialTheme.colorScheme.onSurface,
                        icon = Icons.Default.CalendarMonth,
                        formato = formatoMoneda,
                        modifier = Modifier.width(160.dp).height(115.dp)
                    ) {
                        onNavigate("REPORTE_SEMANAL")
                    }
                }
                item {
                    VentaSecundariaCard(
                        titulo = "META BLOQUE",
                        monto = uiState.ventaBloque,
                        meta = uiState.metaDia * 24,
                        color = DelisaRed,
                        icon = Icons.Default.Flag,
                        formato = formatoMoneda,
                        modifier = Modifier.width(160.dp).height(115.dp)
                    ) {
                    }
                }
                item {
                    AccionCardVendedor(
                        titulo = "ARQUEO",
                        labelSuperior = "Valor de mercancía",
                        valorPrincipal = formatoMoneda.format(uiState.valorInventario),
                        detalle = "Auditoría de Ruta",
                        icon = Icons.Rounded.Analytics,
                        color = DelisaBlue,
                        modifier = Modifier.width(160.dp)
                    ) {
                        onArqueoClick()
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        VentaPrincipalCard(
            monto = uiState.ventaDia, 
            meta = uiState.metaDia, 
            clientes = uiState.clientesDia, 
            ticketPromedio = uiState.ticketPromedioDia, 
            formato = formatoMoneda,
            onCierreClick = { onNavigate("CIERRE_DIA") },
            onGastoClick = { showGastoSheet = true }
        )

        // 🔥 NUEVA SECCIÓN: DESGLOSE POR PERFIL (Siempre visible si hay múltiples perfiles)
        if (uiState.perfilesVenta.size > 1) {
            Spacer(Modifier.height(16.dp))
            BreakdownPerfiles(
                breakdown = uiState.ventasPorPerfil,
                formato = formatoMoneda,
                metaFrituras = uiState.metaPiezasFrituras,
                onMetaClick = { showMetaDialog = true }
            )
        }

        Spacer(Modifier.height(16.dp))
        WeeklyMiniChart(uiState.ventasPorDiaSemana, uiState.metaDia, formatoMoneda)

        Spacer(Modifier.height(36.dp))
        
        Text(
            text = "HARDWARE Y CONECTIVIDAD",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.5.sp
        )
        
        HardwarePrinterCard(
            selectedPrinter = impresoraSeleccionada,
            onConfigurar = onConfigurarImpresora
        )
        Spacer(Modifier.height(40.dp))
    }

    if (showMetaDialog) {
        var tempMeta by remember { mutableStateOf(uiState.metaPiezasFrituras.toString()) }
        AlertDialog(
            onDismissRequest = { showMetaDialog = false },
            title = { Text("Ajustar Meta Frituras", fontWeight = FontWeight.Black) },
            text = {
                OutlinedTextField(
                    value = tempMeta,
                    onValueChange = { if (it.all { c -> c.isDigit() }) tempMeta = it },
                    label = { Text("Meta en piezas") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val m = tempMeta.toIntOrNull() ?: 200
                        onMetaFriturasChange(m)
                        showMetaDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
                ) {
                    Text("GUARDAR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMetaDialog = false }) {
                    Text("CANCELAR")
                }
            }
        )
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            icon = {
                Icon(
                    imageVector = if (uiState.enRuta) Icons.Rounded.Flag else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = DelisaRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (uiState.enRuta) "Finalizar Jornada" else "Iniciar Jornada",
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = if (uiState.enRuta) 
                        "¿Estás seguro que deseas terminar tu ruta de hoy?" 
                        else "¿Confirmas que vas a iniciar tu ruta de distribución ahora?",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { 
                        onToggleRuta(!uiState.enRuta)
                        showConfirmDialog = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text("CONFIRMAR JORNADA", fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showGastoSheet) {
        ModalGastoSheet(
            gastosHoy = uiState.gastosHoy,
            onDismiss = { showGastoSheet = false },
            onGuardar = { monto, cat, desc ->
                onGastoRegistrado(monto, cat, desc) {
                    showGastoSheet = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalGastoSheet(
    gastosHoy: List<Gasto>,
    onDismiss: () -> Unit,
    onGuardar: (Double, String, String) -> Unit
) {
    var monto by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("Gasolina") }
    var descripcion by remember { mutableStateOf("") }
    val categorias = listOf("Gasolina", "Estacionamiento", "Papelería", "Mantenimiento", "Otros")
    var expanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                text = "REGISTRAR GASTO",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = monto,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) monto = it },
                label = { Text("Monto ($)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                prefix = { Text("$ ") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DelisaRed,
                    focusedLabelColor = DelisaRed,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = categoria,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoría") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DelisaRed,
                        focusedLabelColor = DelisaRed,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                ) {
                    categorias.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                categoria = selectionOption
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción (Opcional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DelisaRed,
                    focusedLabelColor = DelisaRed,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    val m = monto.toDoubleOrNull() ?: 0.0
                    if (m > 0) {
                        onGuardar(m, categoria, descripcion)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
            ) {
                Text("GUARDAR GASTO", fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            if (gastosHoy.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = "GASTOS DE HOY",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                
                val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    gastosHoy.forEach { gasto ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(gasto.categoria, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                if (gasto.descripcion.isNotEmpty()) {
                                    Text(gasto.descripcion, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(formatoMoneda.format(gasto.monto), fontWeight = FontWeight.Black, color = DelisaRed)
                        }
                    }
                }
            }
        }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (selectedPrinter != null) DelisaGreen.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = if (selectedPrinter != null) DelisaGreenDark else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(Modifier.weight(1f)) {
                Text(
                    text = "IMPRESORA TÉRMICA",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = selectedPrinter?.name ?: "No vinculada",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedPrinter != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            
            Button(
                onClick = onConfigurar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedPrinter != null) MaterialTheme.colorScheme.onSurface else DelisaBlue
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = if (selectedPrinter != null) "CAMBIAR" else "VINCULAR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun BloqueBolso(
    titulo: String,
    montoTotalHoy: Double,
    comisionHoy: Double,
    formato: NumberFormat,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bolsoScale"
    )

    Card(
        modifier = modifier
            .height(115.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(bounded = true, color = DelisaGreen.copy(0.1f)),
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(14.dp), 
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titulo, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                Icon(
                    Icons.Rounded.Savings, 
                    null, 
                    tint = DelisaGreen.copy(0.6f), 
                    modifier = Modifier.size(14.dp)
                )
            }
            
            Column {
                Text(
                    text = "GANANCIA HOY:", 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    fontSize = 8.sp, 
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = formato.format(montoTotalHoy), 
                    color = DelisaGreenDark, 
                    fontSize = 19.sp, 
                    fontWeight = FontWeight.Black
                )
            }
            
            Surface(
                color = DelisaGreen.copy(alpha = 0.08f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COMISIÓN:", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant, 
                        fontSize = 8.sp, 
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formato.format(comisionHoy), 
                        color = MaterialTheme.colorScheme.onSurface, 
                        fontSize = 11.sp, 
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun AccionCardVendedor(
    titulo: String,
    labelSuperior: String,
    valorPrincipal: String,
    detalle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "accionScale"
    )

    Card(
        modifier = modifier
            .height(115.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(bounded = true, color = color.copy(alpha = 0.1f)),
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(text = titulo, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Icon(icon, null, tint = color.copy(0.4f), modifier = Modifier.size(16.dp))
            }
            
            Column {
                Text(text = labelSuperior, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(text = valorPrincipal, color = color, fontSize = 17.sp, fontWeight = FontWeight.Black)
            }
            
            Text(text = detalle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VentaPrincipalCard(
    monto: Double, 
    meta: Double, 
    clientes: Int, 
    ticketPromedio: Double, 
    formato: NumberFormat,
    onCierreClick: () -> Unit,
    onGastoClick: () -> Unit
) {
    val progreso = (monto / meta).coerceIn(0.0, 1.0).toFloat()
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp).shadow(8.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("VENTA TOTAL HOY", fontSize = 11.sp, fontWeight = FontWeight.Black, color = DelisaRed)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onGastoClick,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.LocalGasStation, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("GASTOS", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    Surface(
                        onClick = onCierreClick,
                        color = DelisaRed,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Lock, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("CERRAR RUTA", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            Text(formato.format(monto), fontSize = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { progreso }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = DelisaRed, trackColor = DelisaRed.copy(0.1f))
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CLIENTES", fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(clientes.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TICKET PROM.", fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formato.format(ticketPromedio), fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun VentaSecundariaCard(
    titulo: String, 
    monto: Double, 
    meta: Double, 
    color: Color, 
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    formato: NumberFormat, 
    modifier: Modifier,
    onClick: () -> Unit = {}
) {
    val progreso = (monto / meta).coerceIn(0.0, 1.0).toFloat()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "ventaSecScale"
    )

    Card(
        modifier = modifier
            .height(115.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(2.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(bounded = true, color = color.copy(alpha = 0.2f)),
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(titulo, fontSize = 9.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(icon, null, tint = color.copy(0.4f), modifier = Modifier.size(16.dp))
            }
            
            Text(formato.format(monto), fontSize = 17.sp, fontWeight = FontWeight.Black, color = color)
            
            Column {
                LinearProgressIndicator(
                    progress = { progreso }, 
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), 
                    color = color, 
                    trackColor = color.copy(0.1f)
                )
                Spacer(Modifier.height(2.dp))
                Text("${(progreso * 100).toInt()}% de meta", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color.copy(0.6f))
            }
        }
    }
}

@Composable
fun SwipeToActionPremium(isActive: Boolean, resetTrigger: Boolean, onActionTriggered: () -> Unit) {
    val scope = rememberCoroutineScope()
    val densidad = androidx.compose.ui.platform.LocalDensity.current
    
    val botonTamano = 54.dp
    val barraAltura = 64.dp
    var anchoFila by remember { mutableStateOf(0f) }
    val rangoMaximo = (anchoFila - with(densidad) { (botonTamano + 12.dp).toPx() }).coerceAtLeast(0f)
    
    val posicionX = remember { Animatable(0f) }
    val factorProgreso = if (rangoMaximo > 0) (posicionX.value / rangoMaximo) else 0f

    LaunchedEffect(isActive, anchoFila, resetTrigger) {
        if (anchoFila > 0 && !resetTrigger) {
            posicionX.animateTo(
                targetValue = if (isActive) rangoMaximo else 0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barraAltura)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
            .onGloballyPositioned { anchoFila = it.size.width.toFloat() }
    ) {
        val stopInicio = 0f
        val stopMedio = (factorProgreso * 0.85f).coerceIn(0f, 1f)
        val stopFinal = (factorProgreso + 0.35f).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            stopInicio to DelisaRed.copy(alpha = 0.8f),
                            stopMedio to DelisaRed,
                            stopFinal to Color.Transparent
                        )
                    )
                )
        )

        val textColor = if (factorProgreso > 0.45f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = if (isActive) "TERMINAR JORNADA" else "DESLIZA PARA INICIAR",
            color = textColor,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp
        )

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
            label = "btnScale"
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .offset { IntOffset(posicionX.value.roundToInt(), 0) }
                .size(botonTamano)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = if (isActive) 12.dp else 4.dp,
                    shape = CircleShape,
                    spotColor = if (isActive) DelisaRed else Color.Black
                )
                .background(Color.White, CircleShape)
                .border(1.dp, Color.LightGray.copy(alpha = 0.3f), CircleShape) 
                .draggable(
                    orientation = Orientation.Horizontal,
                    interactionSource = interactionSource,
                    state = rememberDraggableState { delta ->
                        val nuevaPos = (posicionX.value + delta).coerceIn(0f, rangoMaximo)
                        scope.launch { posicionX.snapTo(nuevaPos) }
                    },
                    onDragStopped = {
                        scope.launch {
                            val puntoGatillo = rangoMaximo * 0.5f
                            if (isActive) {
                                if (posicionX.value < puntoGatillo) onActionTriggered() 
                                else posicionX.animateTo(rangoMaximo, spring(0.75f))
                            } else {
                                if (posicionX.value > puntoGatillo) onActionTriggered()
                                else posicionX.animateTo(0f, spring(0.75f))
                            }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.KeyboardDoubleArrowLeft else Icons.Default.KeyboardDoubleArrowRight,
                contentDescription = null,
                tint = if (isActive) DelisaRed else Color(0xFF616161),
                modifier = Modifier.size(26.dp)
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
    
    val verdeExito = DelisaGreenDark
    val rojoAlerta = DelisaRed
    val grisNeutro = MaterialTheme.colorScheme.outline
    val fondoBarra = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(2.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = if (diaSeleccionado != null) "DETALLE DIARIO" else "PROMEDIO 6 DÍAS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                if (diaSeleccionado != null) {
                    Surface(
                        color = DelisaRed.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            formato.format(ventasPorDia[diaSeleccionado!!]),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = DelisaRed
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
                                .background(if (diaSeleccionado == i) DelisaRed.copy(0.05f) else fondoBarra)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    diaSeleccionado = if (diaSeleccionado == i) null else i
                                },
                            contentAlignment = Alignment.BottomCenter
                        ) {
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
                            
                            if (diaSeleccionado == i) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(DelisaRed)
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(10.dp))
                        
                        Text(
                            text = dias[i],
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (diaSeleccionado == i) DelisaRed else MaterialTheme.colorScheme.onSurfaceVariant
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
    DeliveryTheme { DashboardVendedorView(state, 45f, 75, false, null, {}, {}, {}, {_,_,_,_ -> }, {}, {}) }
}
