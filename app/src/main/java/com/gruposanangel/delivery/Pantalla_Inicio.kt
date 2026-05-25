package com.gruposanangel.delivery.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Addchart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Print
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.SegundoPlano.LocationState
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.gruposanangel.delivery.utilidades.hayInternet
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.gruposanangel.delivery.utilidades.BatteryIndicator
import kotlinx.coroutines.delay

private val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
} else {
    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
}

data class ProductoTicket(
    val nombre: String,
    val precio: Double,
    val cantidad: Int
)

@SuppressLint("MissingPermission")
@Composable
fun Pantalla_Inicio(
    navController: NavController,
    onImpresoraSeleccionada: (BluetoothDevice) -> Unit = {}
) {
    val estadoVelocidad by LocationState.velocidad.collectAsStateWithLifecycle()
    var ticker by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            ticker = System.currentTimeMillis()
        }
    }

    val velocidadParaUI by remember(estadoVelocidad, ticker) {
        derivedStateOf {
            val tiempoTranscurrido = System.currentTimeMillis() - estadoVelocidad.timestamp
            if (tiempoTranscurrido > 3000) 0f else estadoVelocidad.kmh
        }
    }

    val context = LocalContext.current
    var estadoInternet by remember { mutableStateOf(hayInternet(context)) }
    var fechaHora by remember { mutableStateOf("") }
    val prefs = context.getSharedPreferences("impresora_prefs", Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        val formato = SimpleDateFormat("dd 'de' MMMM 'del' yyyy     hh:mm:ss a", Locale("es", "ES"))
        while (true) {
            fechaHora = formato.format(Date())
            estadoInternet = hayInternet(context)
            delay(1000)
        }
    }

    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var showPrinterDialog by remember { mutableStateOf(false) }
    var selectedPrinter by remember { mutableStateOf<BluetoothDevice?>(null) }
    var hasBluetoothPermission by remember { mutableStateOf(false) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBluetoothPermission = permissions.values.all { it }
        if (hasBluetoothPermission) {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            pairedDevices = adapter?.bondedDevices?.toList() ?: emptyList()
            if (pairedDevices.isEmpty()) {
                Toast.makeText(context, "No hay impresoras emparejadas", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permisos de Bluetooth no otorgados", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        hasBluetoothPermission = bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasBluetoothPermission) {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            pairedDevices = adapter?.bondedDevices?.toList() ?: emptyList()
        }

        val savedAddress = prefs.getString("impresora_bluetooth", null)
        if (!savedAddress.isNullOrEmpty()) {
            selectedPrinter = pairedDevices.find { it.address == savedAddress }
            selectedPrinter?.let { onImpresoraSeleccionada(it) }
        }
    }

    var puestoTrabajo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    puestoTrabajo = doc.getString("puestoTrabajo")
                }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (puedeVerFABInicio(puestoTrabajo)) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            if (!hasBluetoothPermission) {
                                bluetoothPermissionLauncher.launch(bluetoothPermissions)
                                return@FloatingActionButton
                            }
                            val adapter = BluetoothAdapter.getDefaultAdapter()
                            pairedDevices = adapter?.bondedDevices?.toList() ?: emptyList()
                            showPrinterDialog = true
                        },
                        containerColor = Color(0xFFFF0000),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Impresora")
                    }

                    FloatingActionButton(
                        onClick = { navController.navigate("VENDEDOR_INFO_VENTAS") },
                        containerColor = Color(0xFFFF0000),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.BarChart, contentDescription = "Ventas del vendedor")
                    }

                    FloatingActionButton(
                        onClick = { navController.navigate("ventas_room") },
                        containerColor = Color(0xFFFF0000),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Addchart, contentDescription = "Ventas del vendedor")
                    }

                    FloatingActionButton(
                        onClick = { navController.navigate("NOTIFICACIONES") },
                        containerColor = Color(0xFFFF0000),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Message, contentDescription = "Notificación")
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (estadoInternet) "Conectado a Internet" else "Sin conexión a Internet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (estadoInternet) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    BatteryIndicator()
                }
                Text(
                    text = fechaHora,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(96.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                VelocimetroTeslaRojo(velocidad = velocidadParaUI)
                Spacer(modifier = Modifier.height(16.dp))

                if (showPrinterDialog) {
                    PantallaSeleccionImpresora(
                        pairedDevices = pairedDevices,
                        onImpresoraSeleccionada = { device ->
                            selectedPrinter = device
                            prefs.edit().putString("impresora_bluetooth", device.address).apply()
                            onImpresoraSeleccionada(device)
                            showPrinterDialog = false
                        },
                        onCancelar = { showPrinterDialog = false }
                    )
                }
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

fun puedeVerFABInicio(puestoTrabajo: String?): Boolean {
    return puestoTrabajo == "CEO1.1" ||
            puestoTrabajo == "Gerente General" ||
            puestoTrabajo == "Supervisor"
}

@SuppressLint("MissingPermission")
@Composable
fun PantallaSeleccionImpresora(
    pairedDevices: List<BluetoothDevice>,
    onImpresoraSeleccionada: (BluetoothDevice) -> Unit,
    onCancelar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Seleccionar impresora") },
        text = {
            if (pairedDevices.isEmpty()) {
                Text("No hay impresoras emparejadas")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(pairedDevices) { device ->
                        Text(
                            text = device.name ?: "Desconocido",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onImpresoraSeleccionada(device) }
                                .padding(12.dp),
                            color = Color.Black
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun VelocimetroTeslaRojo(velocidad: Float) {
    val velocidadAnimada by animateFloatAsState(
        targetValue = velocidad,
        animationSpec = tween(300),
        label = "velocidadAnimada"
    )

    val maxVelocidad = 120f
    val progreso = (velocidadAnimada / maxVelocidad).coerceIn(0f, 1f)
    val rojoPrincipal = Color(0xFFFF0000)
    val grisClaro = Color(0xFFDDDDDD)
    val grisMedio = Color(0xFF999999)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
    ) {
        Canvas(modifier = Modifier.size(250.dp)) {
            val strokeWidth = 22f
            val radius = size.minDimension / 2f - strokeWidth
            val center = Offset(size.width / 2f, size.height / 2f)

            drawArc(
                color = grisClaro.copy(alpha = 0.4f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = rojoPrincipal,
                startAngle = 135f,
                sweepAngle = 270f * progreso,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = velocidadAnimada.toInt().toString(),
            style = MaterialTheme.typography.displayLarge,
            color = rojoPrincipal
        )

        Text(
            text = "km/h",
            style = MaterialTheme.typography.titleMedium,
            color = grisMedio
        )
    }
}

fun enviarNotificacionprueba(token: String, titulo: String, mensaje: String, imagen: String? = null) {
    val client = OkHttpClient()
    val json = """
        {
            "token": "$token",
            "titulo": "$titulo",
            "mensaje": "$mensaje",
            ${if (imagen != null) "\"imagen\": \"$imagen\"" else ""}
        }
    """.trimIndent()

    val requestBody = json.toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder()
        .url("https://us-central1-appventas--san-angel.cloudfunctions.net/enviarNotificacion")
        .post(requestBody)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("Notificacion", "Error enviando notificación", e)
        }
        override fun onResponse(call: Call, response: Response) {
            Log.d("Notificacion", "Respuesta: ${response.body?.string()}")
        }
    })
}

@Preview(showBackground = true)
@Composable
fun PantallaInicioPreview() {
    val navController = rememberNavController()
    Pantalla_Inicio(navController = navController)
}
