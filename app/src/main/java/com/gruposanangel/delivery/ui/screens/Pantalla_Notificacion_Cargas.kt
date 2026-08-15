package com.gruposanangel.delivery.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantila_carga
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.ui.theme.*
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import java.text.NumberFormat

private val RojoDelisa = Color(0xFFE53935)

// 🔹 Modelo de notificación/carga
data class Notificacion(
    val id: String = "",
    val titulo: String,
    val mensaje: String,
    val fecha: String,
    val timestamp: Long = 0L,
    val esCarga: Boolean = false,
    val aceptada: Boolean = false,
    val estado: String = "PENDIENTE",
    val monto: Double = 0.0,
    val diferenciaDinero: Double = 0.0, // 🔥 NUEVO
    val totalPiezas: Int = 0,           // 🔥 NUEVO
    val motivo: String? = null,
    val esEmergencia: Boolean = false,
    val esLiquidacion: Boolean = false
)

/**
 * Pantalla Notificaciones (Lógica)
 */
@Composable
fun PantallaNotificaciones(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val firebaseDataSource = FirebaseDataSource()
    val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
    val usuarioRepo = RepositoryUsuario(firebaseDataSource, db.usuarioDao())

    val viewModel: NotificacionesViewModel = viewModel(
        factory = NotificacionesViewModelFactory(db.productoDao(), inventarioRepo, usuarioRepo)
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.authExito) {
        if (uiState.authExito) {
            val destino = uiState.ultimoAlmacenNombre ?: "No asignado"
            navController.navigate("LISTA PRODUCTOS?origen=Almacen Huasteca&destino=$destino&emergency=true")
            viewModel.resetAuthExito()
        }
    }

    PantallaNotificacionesContent(
        uiState = uiState,
        onBack = {
            // 🔥 CORRECCIÓN: Usar popBackStack para regresar a la pantalla anterior real (Inicio o Inventario)
            // Esto evita forzar la pantalla de Inventario a todos los usuarios.
            if (!navController.popBackStack()) {
                // Fallback si no hay historial (ej: abierto desde notificación push)
                navController.navigate("delivery?screen=Inicio") {
                    popUpTo(0) { inclusive = true }
                }
            }
        },
        onItemClick = { noti ->
            val carga = Plantila_carga(
                id = noti.id,
                nombreCarga = noti.titulo,
                plantillaProductos = emptyList(),
                aceptada = noti.aceptada,
                estado = noti.estado
            )
            navController.currentBackStackEntry?.savedStateHandle?.set("carga", carga)

            if (noti.esCarga) {
                navController.navigate("DETALLE_CARGA")
            } else if (noti.titulo.contains("AUDITORÍA") || noti.titulo.contains("ARQUEO")) {
                navController.navigate("DETALLE_ARQUEO")
            }
        },
        onEmergencyClick = { viewModel.abrirDialogoAutorizacion() },
        onDateRangeSelected = { inicio, fin -> viewModel.actualizarFiltroFechas(inicio, fin) }
    )

    if (uiState.showAuthDialog) {
        DialogoAutorizacionEmergencia(
            onDismiss = { viewModel.cerrarDialogos() },
            onConfirm = { pin -> viewModel.autorizarCarga(pin) },
            isAuthenticating = uiState.isAuthenticating,
            error = uiState.authError
        )
    }
}

/**
 * Pantalla Notificaciones (Vista Pura)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaNotificacionesContent(
    uiState: NotificacionesUiState,
    onBack: () -> Unit,
    onItemClick: (Notificacion) -> Unit,
    onEmergencyClick: () -> Unit = {},
    onDateRangeSelected: (Long, Long) -> Unit = { _, _ -> }
) {
    val showRangePicker = remember { mutableStateOf(false) }

    if (showRangePicker.value) {
        DateRangePickerDialogMaterial3(
            initialStartDate = uiState.fechaInicio,
            initialEndDate = uiState.fechaFin,
            onRangeSelected = { inicio, fin ->
                showRangePicker.value = false
                onDateRangeSelected(inicio, fin)
            },
            onDismiss = { showRangePicker.value = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CENTRO DE MENSAJES", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, letterSpacing = 1.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar", tint = DelisaRed) } },
                actions = {
                    IconButton(onClick = { showRangePicker.value = true }) {
                        Icon(Icons.Default.CalendarMonth, "Filtrar por fecha", tint = DelisaRed)
                    }
                    IconButton(onClick = onEmergencyClick) {
                        Icon(Icons.Default.WifiOff, "Carga Emergencia", tint = DelisaRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 🔥 TRANSICIÓN FLUIDA: Skeleton -> Contenido Real
            Crossfade(targetState = uiState.isLoading, label = "notifContentTransition") { isLoading ->
                if (isLoading) {
                    NotificacionesSkeleton()
                } else {
                    if (uiState.notificaciones.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Notifications, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                Spacer(Modifier.height(16.dp))
                                Text("Sin notificaciones en este periodo", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { showRangePicker.value = true }) {
                                    Text("Cambiar fechas", color = DelisaRed)
                                }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(uiState.notificaciones, key = { it.id }) { noti ->
                                NotificacionItem(noti) { onItemClick(noti) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialogMaterial3(
    initialStartDate: Long,
    initialEndDate: Long,
    onRangeSelected: (Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartDate,
        initialSelectedEndDateMillis = initialEndDate
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DateRangePicker(
                    state = state,
                    modifier = Modifier.weight(1f),
                    title = { Text("Selecciona el periodo", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                    headline = { 
                        val start = state.selectedStartDateMillis
                        val end = state.selectedEndDateMillis
                        if (start != null && end != null) {
                            val sdf = SimpleDateFormat("dd MMM", Locale.forLanguageTag("es-MX"))
                            Text("${sdf.format(Date(start))} - ${sdf.format(Date(end))}", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface)
                        } else {
                            Text("Rango de fechas", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    showModeToggle = false,
                    colors = DatePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        headlineContentColor = DelisaRed,
                        selectedDayContainerColor = DelisaRed,
                        selectedDayContentColor = Color.White,
                        dayInSelectionRangeContainerColor = DelisaRed.copy(alpha = 0.15f),
                        dayInSelectionRangeContentColor = DelisaRed,
                        todayContentColor = DelisaRed,
                        todayDateBorderColor = DelisaRed
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val start = state.selectedStartDateMillis
                            val end = state.selectedEndDateMillis
                            if (start != null) {
                                // Si solo seleccionó una fecha, usamos la misma para inicio y fin
                                onRangeSelected(start, end ?: (start + 86399999))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
                    ) { Text("APLICAR", color = Color.White) }
                }
            }
        }
    }
}

@Composable
fun NotificacionItem(noti: Notificacion, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1f, animationSpec = spring(0.6f, 300f), label = "")
    
    val esCancelada = noti.estado == "CANCELADA"
    val esAceptada = noti.aceptada && !esCancelada
    val esLiquidacion = noti.esLiquidacion || noti.titulo.contains("LIQUIDACIÓN")
    val esArqueo = !noti.esCarga || noti.titulo.contains("AUDITORÍA") || noti.titulo.contains("ARQUEO")
    
    val statusColor = when {
        esCancelada -> Color.Gray
        esLiquidacion -> MaterialTheme.colorScheme.onSurface
        noti.esEmergencia -> DelisaRed
        noti.esCarga -> if (esAceptada) DelisaGreen else DelisaRed
        esArqueo -> WarningOrange
        else -> MaterialTheme.colorScheme.onSurfaceVariant 
    }

    val formatoMoneda = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { 
                scaleX = scale
                scaleY = scale 
                alpha = if (esCancelada) 0.8f else 1f
            }
            .shadow(if (isPressed) 1.dp else 4.dp, RoundedCornerShape(24.dp))
            .border(
                width = if (esLiquidacion) 1.5.dp else if (!esAceptada && noti.esCarga) 2.dp else 1.dp,
                color = statusColor.copy(alpha = if (!esAceptada && noti.esCarga || esLiquidacion) 0.4f else 0.2f),
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(
                interactionSource = interactionSource, 
                indication = androidx.compose.material.ripple.rememberRipple(bounded = true, color = statusColor.copy(alpha = 0.1f)), 
                onClick = onClick
            ), 
        shape = RoundedCornerShape(24.dp), 
        colors = CardDefaults.cardColors(
            containerColor = when {
                esCancelada -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Icono Circular
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(0.08f))
                    .border(0.5.dp, statusColor.copy(0.15f), CircleShape), 
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        noti.esEmergencia -> Icons.Default.FlashOn
                        esCancelada -> Icons.Default.Cancel
                        esLiquidacion -> Icons.Default.Warehouse
                        esArqueo -> Icons.AutoMirrored.Filled.FactCheck
                        !esAceptada -> Icons.Default.Inventory
                        else -> Icons.Default.CheckCircle
                    }, 
                    contentDescription = null, 
                    tint = statusColor, 
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = noti.titulo, 
                    fontWeight = if (!esAceptada && noti.esCarga) FontWeight.ExtraBold else FontWeight.Black,
                    fontSize = 15.sp, 
                    color = if (esCancelada) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = noti.fecha, 
                    fontSize = 11.sp, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant, 
                    maxLines = 1
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Etiqueta Unificada
                val tagText = when {
                    noti.esEmergencia -> "EMERGENCIA"
                    esLiquidacion -> "LIQUIDACIÓN"
                    esArqueo -> "ARQUEO"
                    else -> "CARGA NORMAL"
                }
                val estadoVisible = if (esLiquidacion) "COMPLETADA" else noti.estado
                val textoEtiqueta = if (estadoVisible.isNotBlank()) "$tagText • $estadoVisible" else tagText
                
                Surface(
                    color = statusColor.copy(alpha = 0.08f), 
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(if (esLiquidacion) 1.dp else 0.5.dp, statusColor.copy(alpha = 0.15f))
                ) { 
                    Text(
                        text = textoEtiqueta.uppercase(), 
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), 
                        fontSize = 9.sp, 
                        fontWeight = FontWeight.Black, 
                        color = if (esCancelada) Color.Gray else statusColor, 
                        maxLines = 1,
                        letterSpacing = 0.5.sp
                    ) 
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 75.dp)) {
                // Cantidad de piezas/productos
                Text(
                    text = "${noti.totalPiezas} pzas", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 12.sp, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                
                // Monto Principal
                if (noti.monto > 0 || esArqueo) {
                    Text(
                        text = formatoMoneda.format(noti.monto), 
                        fontWeight = FontWeight.Black, 
                        fontSize = 16.sp, 
                        color = if (esCancelada) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Diferencia (Solo Arqueos/Liquidaciones)
                if (esArqueo) {
                    val colorDif = when {
                        noti.diferenciaDinero < 0 -> DelisaRed
                        noti.diferenciaDinero > 0 -> DelisaBlue
                        else -> DelisaGreenDark
                    }
                    val prefijo = if (noti.diferenciaDinero > 0) "Dif: +" else "Dif: "
                    Text(
                        text = prefijo + formatoMoneda.format(noti.diferenciaDinero), 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.ExtraBold, 
                        color = if (esCancelada) Color.Gray else colorDif,
                        maxLines = 1
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), 
                modifier = Modifier.padding(start = 8.dp).size(20.dp)
            )
        }
    }
}

@Composable
fun DialogoAutorizacionEmergencia(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isAuthenticating: Boolean,
    error: String?
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!isAuthenticating) onDismiss() },
        title = { Text("Autorización de Emergencia", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Esta función es solo para cuando no hay internet. Un supervisor debe autorizar físicamente el ingreso del producto.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña de Supervisor") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DelisaRed,
                        focusedLabelColor = DelisaRed,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                if (error != null) Text(error, color = DelisaRed, fontSize = 12.sp)
                if (isAuthenticating) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DelisaRed)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.length >= 4 && !isAuthenticating,
                colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
            ) { Text("AUTORIZAR", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isAuthenticating) { Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun NotificacionesSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val skeletonColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(skeletonColor)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Notificaciones")
@Composable
fun PantallaNotificacionesPreview() {
    DeliveryTheme {
        PantallaNotificacionesContent(
            uiState = NotificacionesUiState(isLoading = false),
            onBack = {},
            onItemClick = {}
        )
    }
}
