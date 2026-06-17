package com.gruposanangel.delivery.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantila_carga
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.ui.theme.DeliveryTheme

// 🔹 Modelo de notificación/carga
data class Notificacion(
    val id: String = "",
    val titulo: String,
    val mensaje: String,
    val fecha: String,
    val timestamp: Long = 0L, // Para ordenamiento correcto
    val esCarga: Boolean = false,
    val aceptada: Boolean = false
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
            navController.navigate("delivery?screen=Inventario") {
                launchSingleTop = true
                popUpTo(0) { inclusive = true }
            }
        },
        onItemClick = { noti ->
            if (noti.esCarga) {
                val carga = Plantila_carga(
                    id = noti.id,
                    nombreCarga = noti.titulo,
                    plantillaProductos = emptyList(),
                    aceptada = noti.aceptada
                )
                navController.currentBackStackEntry?.savedStateHandle?.remove<Plantila_carga>("carga")
                navController.currentBackStackEntry?.savedStateHandle?.set("carga", carga)
                navController.navigate("DETALLE_CARGA")
            }
        },
        onEmergencyClick = { viewModel.abrirDialogoAutorizacion() }
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
    onEmergencyClick: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CENTRO DE MENSAJES", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.DarkGray, letterSpacing = 1.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar", tint = Color.Red) } },
                actions = {
                    IconButton(onClick = onEmergencyClick) {
                        Icon(Icons.Default.WifiOff, "Carga Emergencia", tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.Red, strokeWidth = 3.dp)
                }
            } else if (uiState.notificaciones.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(Modifier.height(16.dp))
                        Text("Sin notificaciones nuevas", fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(uiState.notificaciones) { noti ->
                        NotificacionItem(noti) { onItemClick(noti) }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificacionItem(noti: Notificacion, onClick: () -> Unit) {
    val esEmergencia = noti.titulo.contains("EMERGENCIA") || noti.titulo.contains("MANUAL")
    val borderColor = when {
        esEmergencia -> Color.Red.copy(alpha = 0.5f)
        !noti.aceptada && noti.esCarga -> Color.Red.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (esEmergencia || (!noti.aceptada && noti.esCarga)) 1.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (esEmergencia) Color(0xFFFFF5F5) else Color.White
        ),
        elevation = CardDefaults.cardElevation(if (noti.aceptada && !esEmergencia) 1.dp else 4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            esEmergencia -> Color.Red
                            noti.esCarga && noti.aceptada -> Color(0xFFE8F5E9)
                            noti.esCarga -> Color.Red.copy(alpha = 0.08f)
                            else -> Color(0xFFF1F2F6)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    esEmergencia -> Icons.Default.FlashOn
                    noti.esCarga && noti.aceptada -> Icons.Default.CheckCircle
                    noti.esCarga -> Icons.Default.Inventory
                    noti.titulo.contains("Pedido") -> Icons.Default.LocalShipping
                    else -> Icons.Default.Notifications
                }
                val tint = when {
                    esEmergencia -> Color.White
                    noti.esCarga && noti.aceptada -> Color(0xFF2E7D32)
                    noti.esCarga -> Color.Red
                    else -> Color.Gray
                }
                Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(
                        text = noti.titulo.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = if (esEmergencia || (noti.esCarga && !noti.aceptada)) Color.Red else Color.Gray,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = noti.fecha,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 9.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.End
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = noti.mensaje,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (esEmergencia) FontWeight.ExtraBold else FontWeight.Bold,
                    color = Color.Black,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (esEmergencia) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color.Red, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "AUTORIZADO OFFLINE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.SyncDisabled, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                } else if (noti.esCarga && !noti.aceptada) {
                    Spacer(Modifier.height(8.dp))
                    Surface(color = Color.Red, shape = RoundedCornerShape(8.dp)) {
                        Text(text = "PENDIENTE DE ACEPTAR", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
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
        title = { Text("Autorización de Emergencia", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Esta función es solo para cuando no hay internet. Un supervisor debe autorizar físicamente el ingreso del producto.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña de Supervisor") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (error != null) Text(error, color = Color.Red, fontSize = 12.sp)
                if (isAuthenticating) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color.Red)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.length >= 4 && !isAuthenticating,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { Text("AUTORIZAR") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isAuthenticating) { Text("CANCELAR") }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

private fun getMockNotificaciones() = listOf(
    Notificacion(id = "1", titulo = "Pedido #123", mensaje = "Tu pedido está en camino \uD83D\uDE9A", fecha = "Lunes, 10:45 AM"),
    Notificacion(id = "3", titulo = "Carga de Almacén", mensaje = "Tienes una carga pendiente por aceptar", fecha = "Hoy, 11:00 AM", esCarga = true, aceptada = false),
    Notificacion(id = "4", titulo = "Carga de Almacén", mensaje = "Carga aceptada exitosamente ✅", fecha = "26/09, 09:15 AM", esCarga = true, aceptada = true)
)

@Preview(showBackground = true, showSystemUi = true, name = "Notificaciones - Con Mensajes")
@Composable
fun PantallaNotificacionesPreview() {
    DeliveryTheme {
        PantallaNotificacionesContent(
            uiState = NotificacionesUiState(isLoading = false, notificaciones = getMockNotificaciones()),
            onBack = {},
            onItemClick = {}
        )
    }
}
