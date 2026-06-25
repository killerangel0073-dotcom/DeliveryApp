package com.gruposanangel.delivery.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.SegundoPlano.LocationState
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import com.gruposanangel.delivery.utilidades.BatteryIndicator
import com.gruposanangel.delivery.utilidades.PantallaSeleccionImpresora
import com.gruposanangel.delivery.utilidades.hayInternet
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN) else arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)

@Composable
fun Pantalla_Inicio(navController: NavController, onImpresoraSeleccionada: (BluetoothDevice) -> Unit = {}) {
    val context = LocalContext.current; val isPreview = LocalInspectionMode.current
    val estadoVelocidad by LocationState.velocidad.collectAsStateWithLifecycle()
    var ticker by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(500); ticker = System.currentTimeMillis() } }
    val velocidad = if (System.currentTimeMillis() - estadoVelocidad.timestamp > 3000) 0f else (estadoVelocidad.kmh + (ticker * 0))
    var internet by remember { mutableStateOf(hayInternet(context)) }
    var fechaHora by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { val fmt = SimpleDateFormat("dd 'de' MMMM, hh:mm:ss a", Locale("es", "MX")); while (true) { fechaHora = fmt.format(Date()); internet = hayInternet(context); delay(1000) } }
    var showPrinters by remember { mutableStateOf(false) }; var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p -> if (p.values.all { it }) { val adapter = BluetoothAdapter.getDefaultAdapter(); devices = adapter?.bondedDevices?.toList() ?: emptyList(); showPrinters = true } }
    var puesto by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (!isPreview) { val uid = FirebaseAuth.getInstance().currentUser?.uid; if (uid != null) FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener { puesto = it.getString("puestoTrabajo") } } }

    PantallaInicioContent(
        velocidad = velocidad, internet = internet, fechaHora = fechaHora, puesto = puesto,
        onPrintClick = { if (bluetoothPermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) { val adapter = BluetoothAdapter.getDefaultAdapter(); devices = adapter?.bondedDevices?.toList() ?: emptyList(); showPrinters = true } else btLauncher.launch(bluetoothPermissions) },
        onVentasClick = { navController.navigate("VENDEDOR_INFO_VENTAS") },
        onVentasRoomClick = { navController.navigate("ventas_room") },
        onNotifClick = { navController.navigate("NOTIFICACIONES") }
    )

    if (showPrinters) { PantallaSeleccionImpresora(devices, onImpresoraSeleccionada = { onImpresoraSeleccionada(it); showPrinters = false }, onCancelar = { showPrinters = false }) }
}

@Composable
fun PantallaInicioContent(velocidad: Float, internet: Boolean, fechaHora: String, puesto: String?, onPrintClick: () -> Unit, onVentasClick: () -> Unit, onVentasRoomClick: () -> Unit, onNotifClick: () -> Unit) {
    Scaffold(
        floatingActionButton = {
            if (puedeVerFABInicio(puesto) || LocalInspectionMode.current) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FloatingActionButton(onClick = onPrintClick, containerColor = Color.Red, contentColor = Color.White) { Icon(Icons.Default.Print, null) }
                    FloatingActionButton(onClick = onVentasClick, containerColor = Color.Red, contentColor = Color.White) { Icon(Icons.Default.BarChart, null) }
                    FloatingActionButton(onClick = onVentasRoomClick, containerColor = Color.Red, contentColor = Color.White) { Icon(Icons.Default.Addchart, null) }
                    FloatingActionButton(onClick = onNotifClick, containerColor = Color.Red, contentColor = Color.White) { Icon(Icons.Default.Message, null) }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (internet) "Conectado" else "Sin conexión", color = if (internet) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f)); BatteryIndicator()
            }
            Text(fechaHora, color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(100.dp)); Box(Modifier.fillMaxWidth(), Alignment.Center) { VelocimetroTeslaRojo(velocidad) }
        }
    }
}

@Composable
fun VelocimetroTeslaRojo(velocidad: Float) {
    val animV by animateFloatAsState(velocidad, tween(300))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(250.dp)) {
            val sw = 20f; val r = size.minDimension / 2 - sw; val c = center
            drawArc(Color.LightGray.copy(0.3f), 135f, 270f, false, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(Color.Red, 135f, 270f * (animV / 120f).coerceIn(0f, 1f), false, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text(animV.toInt().toString(), style = MaterialTheme.typography.displayLarge, color = Color.Red, fontWeight = FontWeight.Black)
        Text("km/h", color = Color.Gray, fontWeight = FontWeight.Bold)
    }
}

fun puedeVerFABInicio(puestoTrabajo: String?): Boolean {
    return puestoTrabajo == "CEO" || puestoTrabajo == "Gerente General" || puestoTrabajo == "Supervisor"
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PantallaInicioPreview() {
    DeliveryTheme { PantallaInicioContent(45f, true, "26 de Septiembre, 10:30 AM", "CEO", {}, {}, {}, {}) }
}
