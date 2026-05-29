package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import com.gruposanangel.delivery.utilidades.PantallaSeleccionImpresora
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun shimmerBrush(showShimmer: Boolean = true, targetValue: Float = 1000f): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            Color.LightGray.copy(alpha = 0.6f),
            Color.LightGray.copy(alpha = 0.2f),
            Color.LightGray.copy(alpha = 0.6f),
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
            .background(Color(0xFFF8F9FA))
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
    val ventaRepository = VentaRepository(db.VentaDao())

    val viewModel: DashboardAdminViewModel = viewModel(
        factory = DashboardAdminViewModelFactory(ventaRepository)
    )

    val uiState by viewModel.uiState.collectAsState()

    DashboardContent(
        uiState = uiState,
        impresoraBluetooth = impresoraBluetooth,
        onImpresoraSeleccionada = onImpresoraSeleccionada,
        onDateSelected = { viewModel.cargarDatosDashboard(it) },
        onNavigate = { route -> navController.navigate(route) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    impresoraBluetooth: BluetoothDevice? = null,
    onImpresoraSeleccionada: (BluetoothDevice) -> Unit = {},
    onDateSelected: (Date) -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    val formatoFecha = SimpleDateFormat("hh:mm a", Locale("es", "MX"))
    val formatoDia = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "MX"))

    var selectedDate by remember { mutableStateOf(Date()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Lógica de Impresora
    var showPrinterDialog by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager }
    val bluetoothAdapter = remember { bluetoothManager.adapter }

    val bluetoothPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN)
    } else {
        arrayOf(android.Manifest.permission.BLUETOOTH, android.Manifest.permission.BLUETOOTH_ADMIN)
    }

    val bluetoothLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            showPrinterDialog = true
        } else {
            android.widget.Toast.makeText(context, "Se requieren permisos de Bluetooth", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var sellerSeleccionado by remember { mutableStateOf<SellerSummary?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.time)
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Color.Red,
                onPrimary = Color.White,
                surface = Color.White,
                onSurface = Color.Black,
                secondary = Color.Red
            )
        ) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            val newDate = Date(it)
                            selectedDate = newDate
                            onDateSelected(newDate)
                        }
                        showDatePicker = false
                    }) { Text("ACEPTAR", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("CANCELAR", color = Color.Gray)
                    }
                }
            ) {
                DatePicker(
                    state = datePickerState,
                    colors = DatePickerDefaults.colors(
                        titleContentColor = Color.DarkGray,
                        headlineContentColor = Color.Red,
                        selectedDayContainerColor = Color.Red,
                        selectedDayContentColor = Color.White,
                        todayContentColor = Color.Red,
                        todayDateBorderColor = Color.Red,
                        weekdayContentColor = Color.Gray
                    )
                )
            }
        }
    }

    if (showSheet && sellerSeleccionado != null) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.Red) }
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
                    color = Color(0xFF4CAF50)
                )
                Text(
                    sellerSeleccionado!!.rutaNombre,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.Red
                )
                Text(
                    "${sellerSeleccionado!!.ventas.size} tickets registrados hoy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
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
            .background(Color(0xFFF8F9FA))
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
                    text = formatoDia.format(selectedDate).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.Gray
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            tint = if (impresoraBluetooth != null) Color.Red else Color.Gray,
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
                            tint = Color.Red,
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
                        formato = formatoMoneda
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AccionCard("Inventario", Icons.Default.Inventory, Color(0xFF00AAFF), Modifier.weight(1f)) {
                            onNavigate("PRODUCTOS")
                        }
                        AccionCard("Cargas", Icons.Default.Category, Color(0xFF4CAF50), Modifier.weight(1f)) {
                            onNavigate("LISTA PRODUCTOS")
                        }
                        AccionCard("Rutas", Icons.AutoMirrored.Filled.AltRoute, Color(0xFFFF9800), Modifier.weight(1f)) {
                            onNavigate("ADMIN_RUTAS")
                        }
                        AccionCard("Usuarios", Icons.Default.People, Color(0xFF9C27B0), Modifier.weight(1f)) {
                            onNavigate("ADMIN_USUARIOS")
                        }
                    }
                }

                item {
                    Text(
                        "Desempeño por Ruta",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(uiState.resumenVendedores, key = { it.uid }) { seller ->
                    VendedorCard(seller, formatoMoneda) {
                        sellerSeleccionado = seller
                        showSheet = true
                    }
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
                        color = Color.DarkGray
                    )
                }

                if (uiState.todasLasVentasHoy.isEmpty()) {
                    item {
                        Text("No hay transacciones registradas hoy.", color = Color.Gray, modifier = Modifier.padding(8.dp))
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
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, color = color, radius = 60.dp),
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(titulo, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.DarkGray)
        }
    }
}

@Composable
fun ResumenTotalCard(total: Double, tickets: Int, promedio: Double, formato: NumberFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Red),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Red, Color(0xFFB71C1C))
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
fun VendedorCard(seller: SellerSummary, formato: NumberFormat, onClick: () -> Unit) {
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
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, color = Color.Red),
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (seller.photoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = seller.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Red.copy(alpha = 0.1f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            Icons.Default.Group,
                            null,
                            tint = Color.Red,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(seller.rutaNombre, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.Black)
                    Text(seller.nombre, fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
                Text(
                    formato.format(seller.totalVendido),
                    fontWeight = FontWeight.Black,
                    color = Color.Red,
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF1F2F6), thickness = 1.dp)
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
                MetricItem("VENTAS", "${seller.ventas.size}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, color = Color.Black, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun MetricDivider() {
    Box(
        modifier = Modifier
            .height(30.dp)
            .width(1.dp)
            .background(Color(0xFFEEEEEE))
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = true, color = Color.Gray),
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Payments, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(venta.clienteNombre, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(fHora.format(Date(venta.fecha)), fontSize = 11.sp, color = Color.Gray)
            }
            Text(formato.format(venta.total), fontWeight = FontWeight.ExtraBold, color = Color.DarkGray)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Admin Dashboard - Con Datos")
@Composable
fun DashboardPreview() {
    val dummyVentas = listOf(
        VentaEntity(id = "1", clienteId = "c1", clienteNombre = "Abarrotes Don Pepe", total = 1540.0, metodoPago = "Efectivo", vendedorId = "v1", fecha = System.currentTimeMillis(), sincronizado = true),
        VentaEntity(id = "2", clienteId = "c2", clienteNombre = "Mini Super El Sol", total = 890.0, metodoPago = "Transferencia", vendedorId = "v1", fecha = System.currentTimeMillis(), sincronizado = true)
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
            onDateSelected = {},
            onNavigate = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Admin Dashboard - Cargando")
@Composable
fun DashboardCargandoPreview() {
    DeliveryTheme {
        DashboardContent(
            uiState = DashboardUiState(isLoading = true),
            onDateSelected = {},
            onNavigate = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Admin Dashboard - Vacío")
@Composable
fun DashboardVacioPreview() {
    DeliveryTheme {
        DashboardContent(
            uiState = DashboardUiState(isLoading = false, todasLasVentasHoy = emptyList(), resumenVendedores = emptyList()),
            onDateSelected = {},
            onNavigate = {}
        )
    }
}
