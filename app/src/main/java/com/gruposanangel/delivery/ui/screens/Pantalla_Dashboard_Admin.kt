package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.PerfilVenta
import com.gruposanangel.delivery.ui.theme.*
import com.gruposanangel.delivery.utilidades.PantallaSeleccionImpresora
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun shimmerBrush(showShimmer: Boolean = true, targetValue: Float = 1000f): Brush {
    val baseColor = if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray
    return if (showShimmer) {
        val shimmerColors = listOf(
            baseColor.copy(alpha = 0.6f),
            baseColor.copy(alpha = 0.2f),
            baseColor.copy(alpha = 0.6f),
        )

        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation = transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(800), repeatMode = RepeatMode.Reverse
            ), label = "shimmerTranslate"
        )
        Brush.linearGradient(
            colors = shimmerColors,
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset(x = translateAnimation.value, y = translateAnimation.value)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset.Zero
        )
    }
}

@Composable
fun DashboardSkeleton() {
    val brush = shimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(24.dp)).background(brush))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).height(90.dp).clip(RoundedCornerShape(16.dp)).background(brush))
            Box(Modifier.weight(1f).height(90.dp).clip(RoundedCornerShape(16.dp)).background(brush))
        }
        Box(Modifier.width(180.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(20.dp)).background(brush))
        Box(Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(20.dp)).background(brush))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pantalla_Dashboard_Admin(
    navController: NavController,
    impresoraBluetooth: BluetoothDevice? = null,
    onImpresoraSeleccionada: (BluetoothDevice) -> Unit = {}
) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val ventaRepository = VentaRepository(db.VentaDao(), db.productoDao())

    val viewModel: DashboardAdminViewModel = viewModel(
        factory = DashboardAdminViewModelFactory(ventaRepository)
    )

    val uiState by viewModel.uiState.collectAsState()

    DashboardContent(
        uiState = uiState,
        impresoraBluetooth = impresoraBluetooth,
        onImpresoraSeleccionada = onImpresoraSeleccionada,
        onDateRangeSelected = { inicio, fin -> viewModel.cargarDatosDashboardRango(inicio, fin) },
        onNavigate = { route -> navController.navigate(route) },
        onAnalyticsClick = { start, end -> navController.navigate("analytics_admin/$start/$end") }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    impresoraBluetooth: BluetoothDevice? = null,
    onImpresoraSeleccionada: (BluetoothDevice) -> Unit = {},
    onDateRangeSelected: (Date, Date) -> Unit,
    onNavigate: (String) -> Unit,
    onAnalyticsClick: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val formatoFecha = SimpleDateFormat("hh:mm a", Locale("es", "MX"))
    val formatoDia = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "MX"))
    val formatoRango = SimpleDateFormat("d MMM", Locale("es", "MX"))

    var startDate by remember { mutableStateOf(Date()) }
    var endDate by remember { mutableStateOf(Date()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val textoFecha = if (startDate.time == endDate.time) {
        formatoDia.format(startDate).uppercase()
    } else {
        "${formatoRango.format(startDate)} - ${formatoRango.format(endDate)}".uppercase()
    }

    // Lógica de Velocidad
    var showSpeedDialog by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("config_gps", Context.MODE_PRIVATE) }
    var speedLimit by remember { mutableFloatStateOf(prefs.getFloat("limite_velocidad", 70f)) }

    // Lógica de Configuración Financiera
    var showFinanceDialog by remember { mutableStateOf(false) }
    var sueldoBaseBase by remember { mutableStateOf(300.0) }
    var comisionBase by remember { mutableStateOf(3.0) }

    // Lógica de Ranking de Clientes (Niveles de Tienda)
    var showRankingDialog by remember { mutableStateOf(false) }

    // 🔥 SINCRONIZACIÓN EN TIEMPO REAL CON FIREBASE
    LaunchedEffect(Unit) {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        // 1. Escuchar GPS para la velocidad
        firestore.collection("config").document("gps")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val fireLimit = snapshot.getDouble("limite_velocidad")?.toFloat() ?: 70f
                    speedLimit = fireLimit
                    prefs.edit().putFloat("limite_velocidad", fireLimit).apply()
                }
            }

        // 2. Escuchar PAGOS para sueldo y comisión
        firestore.collection("config").document("pagos")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    sueldoBaseBase = snapshot.getDouble("sueldo_base") ?: 300.0
                    comisionBase = snapshot.getDouble("comision_porcentaje") ?: 3.0
                }
            }
    }

    // Lógica de Impresora
    var showPrinterDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    
    // 🔥 USAMOS UNA REFERENCIA ESTABLE AL ADAPTER
    val bluetoothManager = remember(context) { context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager }
    val bluetoothAdapter = remember(bluetoothManager) { bluetoothManager.adapter }

    val bluetoothPermissions = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(android.Manifest.permission.BLUETOOTH, android.Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    val bluetoothLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            try {
                pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                showPrinterDialog = true
            } catch (e: SecurityException) {
                android.widget.Toast.makeText(context, "Error de seguridad Bluetooth", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(context, "Se requieren permisos de Bluetooth", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var sellerSeleccionado by remember { mutableStateOf<SellerSummary?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startDate.time,
            initialSelectedEndDateMillis = endDate.time
        )
        val isDark = ThemeConfig.isActuallyDark

        DeliveryTheme(darkTheme = isDark) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        
                        if (startMillis != null) {
                            // 🔥 CORRECCIÓN DE DESFASE (UTC a Local)
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                            
                            cal.timeInMillis = startMillis
                            val sDate = Calendar.getInstance().apply {
                                set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                            }.time
                            
                            val eDate = if (endMillis != null) {
                                cal.timeInMillis = endMillis
                                Calendar.getInstance().apply {
                                    set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                                }.time
                            } else sDate

                            startDate = sDate
                            endDate = eDate
                            onDateRangeSelected(sDate, eDate)
                        }
                        showDatePicker = false
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
                    title = { Text("Selecciona Periodo", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                    headline = { Text("Filtro de Ventas", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface) },
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

    if (showSheet && sellerSeleccionado != null) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = DelisaRed) }
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    "Ventas de ${sellerSeleccionado!!.nombre}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DelisaGreen
                )
                Text(
                    sellerSeleccionado!!.rutaNombre,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = DelisaRed
                )
                Text(
                    "${sellerSeleccionado!!.ventas.size} tickets registrados hoy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxHeight(0.6f)
                ) {
                    items(sellerSeleccionado!!.ventas.sortedByDescending { it.fecha }) { venta ->
                        CardVentaAdmin(venta, formatoMoneda, formatoFecha) {
                            showSheet = false
                            onNavigate("detalle_venta_admin/${venta.id}")
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(16.dp)) {
                DashboardSkeleton()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = textoFecha,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 🚀 BOTÓN RANKING DE TIENDAS
                    IconButton(
                        onClick = { showRankingDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = "Configurar Ranking",
                            tint = WarningOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    // 🚀 BOTÓN CONFIGURACIÓN FINANCIERA
                    IconButton(
                        onClick = { showFinanceDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Configurar Pagos",
                            tint = DelisaGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    // 🚀 BOTÓN DE VELOCIDAD
                    IconButton(
                        onClick = { showSpeedDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Configurar Velocidad",
                            tint = DelisaRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    // 🖨️ BOTÓN DE IMPRESORA (IZQUIERDA DEL CALENDARIO)
                    IconButton(
                        onClick = {
                            val hasPermission = bluetoothPermissions.all {
                                androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            }
                            if (hasPermission) {
                                pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                                showPrinterDialog = true
                            } else {
                                bluetoothLauncher.launch(bluetoothPermissions)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Impresora",
                            tint = if (impresoraBluetooth != null) DelisaRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    IconButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = "Fecha",
                            tint = DelisaRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (showPrinterDialog) {
                PantallaSeleccionImpresora(
                    pairedDevices = pairedDevices,
                    onImpresoraSeleccionada = { 
                        onImpresoraSeleccionada(it)
                        showPrinterDialog = false
                    },
                    onCancelar = { showPrinterDialog = false }
                )
            }

            if (showFinanceDialog) {
                var tempSueldo by remember { mutableStateOf(sueldoBaseBase.toInt().toString()) }
                var tempComision by remember { mutableStateOf(comisionBase.toString()) }
                
                AlertDialog(
                    onDismissRequest = { showFinanceDialog = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = {
                        Text(
                            text = "Configuración de Pagos",
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    icon = {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = DelisaGreen,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Ajusta los valores base para el cálculo de ganancias de los vendedores.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(24.dp))
                            
                            OutlinedTextField(
                                value = tempSueldo,
                                onValueChange = { if (it.all { c -> c.isDigit() }) tempSueldo = it },
                                label = { Text("Sueldo Base Diario") },
                                prefix = { Text("$ ") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DelisaGreen, focusedLabelColor = DelisaGreen)
                            )
                            
                            Spacer(Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = tempComision,
                                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) tempComision = it },
                                label = { Text("Porcentaje Comisión") },
                                suffix = { Text("%") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DelisaGreen, focusedLabelColor = DelisaGreen)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val nuevoSueldo = tempSueldo.toDoubleOrNull() ?: 300.0
                                val nuevaComision = tempComision.toDoubleOrNull() ?: 3.0
                                
                                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                firestore.collection("config").document("pagos")
                                    .set(mapOf(
                                        "sueldo_base" to nuevoSueldo,
                                        "comision_porcentaje" to nuevaComision
                                    ), com.google.firebase.firestore.SetOptions.merge())
                                
                                showFinanceDialog = false
                                android.widget.Toast.makeText(context, "Configuración financiera actualizada", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DelisaGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("GUARDAR", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFinanceDialog = false }) {
                            Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }

            if (showRankingDialog) {
                var tempAlto by remember { mutableStateOf(uiState.rankingAlto.toInt().toString()) }
                var tempMedio by remember { mutableStateOf(uiState.rankingMedio.toInt().toString()) }
                var tempBajo by remember { mutableStateOf(uiState.rankingBajo.toInt().toString()) }

                AlertDialog(
                    onDismissRequest = { showRankingDialog = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = {
                        Text(
                            text = "Ranking de Tiendas",
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    icon = {
                        Icon(
                            Icons.Default.Stars,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(32.dp)
                        )
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Define los umbrales de venta mensual para categorizar a los clientes.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(24.dp))

                            OutlinedTextField(
                                value = tempAlto,
                                onValueChange = { if (it.all { c -> c.isDigit() }) tempAlto = it },
                                label = { Text("Umbral ALTO (Verde)") },
                                prefix = { Text("$ ") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DelisaGreenDark, focusedLabelColor = DelisaGreenDark)
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = tempMedio,
                                onValueChange = { if (it.all { c -> c.isDigit() }) tempMedio = it },
                                label = { Text("Umbral MEDIO (Amarillo)") },
                                prefix = { Text("$ ") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = WarningOrange, focusedLabelColor = WarningOrange)
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = tempBajo,
                                onValueChange = { if (it.all { c -> c.isDigit() }) tempBajo = it },
                                label = { Text("Umbral BAJO (Rojo)") },
                                prefix = { Text("$ ") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DelisaRed, focusedLabelColor = DelisaRed)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val nAlto = tempAlto.toDoubleOrNull() ?: 500.0
                                val nMedio = tempMedio.toDoubleOrNull() ?: 300.0
                                val nBajo = tempBajo.toDoubleOrNull() ?: 150.0

                                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                firestore.collection("config").document("valor_clientes")
                                    .set(mapOf(
                                        "alto" to nAlto,
                                        "medio" to nMedio,
                                        "bajo" to nBajo
                                    ), com.google.firebase.firestore.SetOptions.merge())

                                showRankingDialog = false
                                android.widget.Toast.makeText(context, "Ranking de tiendas actualizado", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningOrange),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("GUARDAR", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRankingDialog = false }) {
                            Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }

            if (showSpeedDialog) {
                var tempValue by remember { mutableStateOf(speedLimit.toInt().toString()) }
                AlertDialog(
                    onDismissRequest = { showSpeedDialog = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, null, tint = DelisaRed)
                            Spacer(Modifier.width(12.dp))
                            Text("Límite de Velocidad", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    text = {
                        Column {
                            Text("Define la velocidad máxima permitida (km/h) para los vendedores antes de disparar la alarma.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = tempValue,
                                onValueChange = { if (it.length <= 3 && it.all { c -> c.isDigit() }) tempValue = it },
                                label = { Text("Límite km/h") },
                                suffix = { Text("km/h") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DelisaRed, focusedLabelColor = DelisaRed)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val nuevoLimite = tempValue.toFloatOrNull() ?: 70f
                                
                                // Guardar en Firebase (Para sincronizar con vendedores)
                                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                firestore.collection("config").document("gps")
                                    .set(mapOf("limite_velocidad" to nuevoLimite), com.google.firebase.firestore.SetOptions.merge())
                                
                                speedLimit = nuevoLimite
                                prefs.edit().putFloat("limite_velocidad", nuevoLimite).apply()
                                showSpeedDialog = false
                                android.widget.Toast.makeText(context, "Límite global actualizado a ${nuevoLimite.toInt()} km/h", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("GUARDAR", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSpeedDialog = false }) {
                            Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }

            if (showPasswordDialog) {
                AlertDialog(
                    onDismissRequest = { 
                        showPasswordDialog = false
                        passwordInput = ""
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { 
                        Text(
                            "Acceso Restringido", 
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Ingresa la contraseña de administrador para ver el resumen financiero.", 
                                fontSize = 14.sp, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { if (it.length <= 4) passwordInput = it },
                                label = { Text("Contraseña") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DelisaRed, focusedLabelColor = DelisaRed)
                            )
                        }
                    },
                    confirmButton = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { 
                                showPasswordDialog = false
                                passwordInput = ""
                            }) {
                                Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    if (passwordInput == "0000") {
                                        showPasswordDialog = false
                                        passwordInput = ""
                                        onNavigate("RESUMEN_OPERATIVO")
                                    } else {
                                        android.widget.Toast.makeText(context, "Contraseña Incorrecta", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("ENTRAR", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ResumenTotalCard(
                        total = uiState.totalVentasDia,
                        tickets = uiState.totalTicketsDia,
                        promedio = uiState.ticketPromedioGlobal,
                        formato = formatoMoneda,
                        onClick = { onAnalyticsClick(startDate.time, endDate.time) }
                    )
                }

                // 🔥 2. CARRUSEL HORIZONTAL DE ACCIONES (Sin amontonarse)
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            AccionCard("Productos", Icons.Default.Inventory, Color(0xFF00AAFF), Modifier.width(100.dp)) {
                                onNavigate("PRODUCTOS")
                            }
                        }
                        item {
                            AccionCard("Finanzas", Icons.Default.AccountBalanceWallet, Color(0xFF4CAF50), Modifier.width(100.dp)) {
                                showPasswordDialog = true
                            }
                        }
                        item {
                            AccionCard("Cargas", Icons.Default.Category, Color(0xFF607D8B), Modifier.width(100.dp)) {
                                onNavigate("LISTA PRODUCTOS")
                            }
                        }
                        item {
                            AccionCard("Arqueo", Icons.Default.Analytics, Color(0xFFE91E63), Modifier.width(100.dp)) {
                                onNavigate("PANTALLA_ARQUEO")
                            }
                        }
                        item {
                            AccionCard("Rutas", Icons.AutoMirrored.Filled.AltRoute, Color(0xFFFF9800), Modifier.width(100.dp)) {
                                onNavigate("ADMIN_RUTAS")
                            }
                        }
                        item {
                            AccionCard("Usuarios", Icons.Default.People, Color(0xFF9C27B0), Modifier.width(100.dp)) {
                                onNavigate("ADMIN_USUARIOS")
                            }
                        }
                        item {
                            AccionCard("Almacenes", Icons.Default.Warehouse, Color(0xFFFF5722), Modifier.width(100.dp)) {
                                onNavigate("ADMIN_ALMACENES")
                            }
                        }
                        item {
                            AccionCard("Auditorías", Icons.Default.FactCheck, Color(0xFF607D8B), Modifier.width(100.dp)) {
                                onNavigate("HISTORIAL_CARGAS")
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Desempeño por Ruta",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(uiState.resumenVendedores, key = { it.uid }) { seller ->
                    VendedorCard(
                        seller = seller, 
                        formato = formatoMoneda,
                        onClick = {
                            sellerSeleccionado = seller
                            showSheet = true
                        },
                        onPhotoClick = { uid ->
                            onNavigate("REPORTE_SEMANAL?userId=$uid")
                        }
                    )
                }

                if (uiState.resumenVendedores.isEmpty()) {
                    item {
                        Text("No se encontraron rutas activas con transacciones.", color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }

                item {
                    Text(
                        "Últimas Ventas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (uiState.todasLasVentasHoy.isEmpty()) {
                    item {
                        Text("No hay transacciones registradas hoy.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                    }
                }

                items(uiState.todasLasVentasHoy.sortedByDescending { it.fecha }) { venta ->
                    CardVentaAdmin(venta, formatoMoneda, formatoFecha) {
                        onNavigate("detalle_venta_admin/${venta.id}")
                    }
                }
            }
        }
    }
}

@Composable
fun AccionCard(titulo: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "accionCardScale"
    )

    Card(
        modifier = modifier
            .height(90.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, color = color, radius = 60.dp),
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ResumenTotalCard(
    total: Double, 
    tickets: Int, 
    promedio: Double, 
    formato: NumberFormat,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "totalCardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(8.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, color = Color.White),
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = DelisaRed)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DelisaRed, DelisaRedDark)
                    )
                )
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.TrendingUp, null, tint = Color.White, modifier = Modifier.size(32.dp))
                Text("VENTA TOTAL DEL DÍA", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    formato.format(total),
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TICKETS", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("$tickets", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.3f)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TICKET PROM.", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(formato.format(promedio), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
fun VendedorCard(
    seller: SellerSummary, 
    formato: NumberFormat, 
    onClick: () -> Unit,
    onPhotoClick: (String) -> Unit // 🔥 Nuevo: Clic en la foto
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "vendedorCardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(4.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, color = DelisaRed.copy(alpha = 0.1f)),
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 📸 FOTO CON SUPER EFECTO (Clickable para Reporte Semanal)
                val photoInteractionSource = remember { MutableInteractionSource() }
                val isPhotoPressed by photoInteractionSource.collectIsPressedAsState()
                val photoScale by animateFloatAsState(
                    targetValue = if (isPhotoPressed) 1.3f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                    label = "photoScale"
                )

                val infiniteTransition = rememberInfiniteTransition(label = "photoEffect")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(tween(1400, easing = LinearOutSlowInEasing), RepeatMode.Restart),
                    label = "glowAlpha"
                )
                val glowScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.8f,
                    animationSpec = infiniteRepeatable(tween(1400, easing = LinearOutSlowInEasing), RepeatMode.Restart),
                    label = "glowScale"
                )
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
                    label = "rotation"
                )

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                    // 🔥 EFECTO DE BORDE ANIMADO Y PULSO (Solo si está en ruta)
                    if (seller.estaEnRuta) {
                        // Borde de Neón Rotatorio
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .graphicsLayer { rotationZ = rotation }
                                .background(
                                    Brush.sweepGradient(listOf(DelisaRed, Color.White.copy(alpha = 0.1f), DelisaRed)),
                                    CircleShape
                                )
                        )
                        
                        // Onda de Choque (Pulso)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer {
                                    scaleX = glowScale
                                    scaleY = glowScale
                                    alpha = glowAlpha
                                }
                                .background(DelisaRed.copy(alpha = 0.5f), CircleShape)
                        )
                    }

                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = photoScale
                                scaleY = photoScale
                            }
                            .size(44.dp)
                            .shadow(if (isPhotoPressed) 12.dp else 2.dp, RoundedCornerShape(12.dp), spotColor = DelisaRed)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(
                                interactionSource = photoInteractionSource,
                                indication = rememberRipple(bounded = false, radius = 30.dp, color = DelisaRed),
                                onClick = { onPhotoClick(seller.uid) }
                            )
                    ) {
                        if (seller.photoUrl.isNotEmpty()) {
                            AsyncImage(
                                model = seller.photoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(DelisaRed.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Group,
                                    null,
                                    tint = DelisaRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // 🟢 LUZ DE ESTADO (JORNADA)
                        Box(
                            modifier = Modifier
                                .offset(x = 3.dp, y = 3.dp)
                                .size(14.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (seller.estaEnRuta) DelisaGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
                
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(seller.rutaNombre, fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = if (seller.estaEnRuta) "RUTA ACTIVA" else "JORNADA DETENIDA",
                        fontSize = 9.sp,
                        color = if (seller.estaEnRuta) DelisaGreenDark else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    formato.format(seller.totalVendido),
                    fontWeight = FontWeight.Black,
                    color = DelisaRed,
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricItem("CLIENTES", "${seller.clientesConVenta}", Modifier.weight(1f))
                MetricDivider()
                MetricItem("TICKET PROM.", formato.format(seller.ticketPromedio), Modifier.weight(1.3f))
                MetricDivider()
                MetricItem("VENTAS", "${seller.totalTicketsActivos}", Modifier.weight(1f))
            }
            
            // 🔥 NUEVA SECCIÓN: DESGLOSE POR PERFIL (En tarjeta de administrador)
            if (seller.perfilesVenta.size > 1) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                BreakdownPerfiles(
                    breakdown = seller.breakdown,
                    formato = formato,
                    mostrarTitulo = false, // No hace falta título aquí, ya estamos en el vendedor
                    compacto = true // Estilo más pequeño para el dashboard admin
                )
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun MetricDivider() {
    Box(
        modifier = Modifier
            .height(30.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

@Composable
fun CardVentaAdmin(
    venta: VentaEntity,
    formato: NumberFormat,
    fHora: SimpleDateFormat,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        label = "ventaCardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (venta.estado == "CANCELADA") MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = if (venta.estado == "CANCELADA") Icons.Default.Cancel else Icons.Default.Payments
            val tint = if (venta.estado == "CANCELADA") DelisaRed else MaterialTheme.colorScheme.onSurfaceVariant
            
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("FOLIO #${venta.id.takeLast(6).uppercase()}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (venta.estado == "CANCELADA") {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = DelisaRed, shape = RoundedCornerShape(4.dp)) {
                            Text("ANULADA", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(
                    text = venta.clienteNombre, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 15.sp,
                    style = if (venta.estado == "CANCELADA") androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else androidx.compose.ui.text.TextStyle.Default,
                    color = if (venta.estado == "CANCELADA") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Text(fHora.format(Date(venta.fecha)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = formato.format(venta.total), 
                fontWeight = FontWeight.ExtraBold, 
                color = if (venta.estado == "CANCELADA") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Admin Dashboard - Con Datos")
@Composable
fun DashboardPreview() {
    val dummyVentas = listOf(
        VentaEntity(
            id = "1", clienteId = "c1", clienteNombre = "Abarrotes Don Pepe", clienteImagenUrl = null,
            total = 1540.0, metodoPago = "Efectivo", vendedorId = "v1", vendedorNombre = "Juan",
            almacenId = "a1", fecha = System.currentTimeMillis(), horaDispositivo = System.currentTimeMillis(),
            horaVerificada = System.currentTimeMillis(), alertaTiempo = false,
            latitudVenta = 0.0, longitudVenta = 0.0, fueraDeRango = false, fotoEvidenciaVisita = null,
            sincronizado = true, firestoreId = null
        ),
        VentaEntity(
            id = "2", clienteId = "c2", clienteNombre = "Mini Super El Sol", clienteImagenUrl = null,
            total = 890.0, metodoPago = "Transferencia", vendedorId = "v1", vendedorNombre = "Juan",
            almacenId = "a1", fecha = System.currentTimeMillis(), horaDispositivo = System.currentTimeMillis(),
            horaVerificada = System.currentTimeMillis(), alertaTiempo = false,
            latitudVenta = 0.0, longitudVenta = 0.0, fueraDeRango = false, fotoEvidenciaVisita = null,
            sincronizado = true, firestoreId = null
        )
    )
    val dummySellers = listOf(
        SellerSummary("v1", "Juan Pérez", "Ruta 1 Delisa", "", 2430.0, 12, 202.5, dummyVentas)
    )
    val uiState = DashboardUiState(
        isLoading = false,
        totalVentasDia = 15450.0,
        totalTicketsDia = 45,
        totalClientesDia = 38,
        ticketPromedioGlobal = 343.33,
        resumenVendedores = dummySellers,
        todasLasVentasHoy = dummyVentas
    )

    DeliveryTheme {
        DashboardContent(
            uiState = uiState,
            onDateRangeSelected = { _, _ -> },
            onNavigate = {},
            onAnalyticsClick = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Admin Dashboard - Cargando")
@Composable
fun DashboardCargandoPreview() {
    DeliveryTheme {
        DashboardContent(
            uiState = DashboardUiState(isLoading = true),
            onDateRangeSelected = { _, _ -> },
            onNavigate = {},
            onAnalyticsClick = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Admin Dashboard - Vacío")
@Composable
fun DashboardVacioPreview() {
    DeliveryTheme {
        DashboardContent(
            uiState = DashboardUiState(isLoading = false, todasLasVentasHoy = emptyList(), resumenVendedores = emptyList()),
            onDateRangeSelected = { _, _ -> },
            onNavigate = {},
            onAnalyticsClick = { _, _ -> }
        )
    }
}
