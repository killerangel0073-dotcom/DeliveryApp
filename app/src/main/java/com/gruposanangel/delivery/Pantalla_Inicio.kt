package com.gruposanangel.delivery.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.CancelPresentation
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Print
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.gruposanangel.delivery.SegundoPlano.LocationService
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.gruposanangel.delivery.utilidades.hayInternet
import kotlin.math.cos
import kotlin.math.sin
import com.gruposanangel.delivery.SegundoPlano.LocationState






private val bluetoothPermissions = arrayOf(
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_SCAN
)

data class ProductoTicket(
    val nombre: String,
    val cantidad: Int,
    val precio: Double
)


@Composable
fun Pantalla_Inicio(
    onImpresoraSeleccionada: (BluetoothDevice) -> Unit = {}
) {


    val velocidad by LocationState.velocidad.collectAsState()


    var mostrarDialogo by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val estadoInternet = remember { mutableStateOf(hayInternet(context)) }
    val fechaHora = remember { mutableStateOf("") }
    val prefs = context.getSharedPreferences("impresora_prefs", Context.MODE_PRIVATE)




    LaunchedEffect(Unit) {
        while (true) {
            // Actualizamos la fecha/hora
            val formato = SimpleDateFormat(
                "dd 'de' MMMM 'del' yyyy     hh:mm:ss a",
                Locale("es", "ES")
            )
            fechaHora.value = formato.format(Date())

            // Actualizamos el estado de internet
            estadoInternet.value = hayInternet(context)

            kotlinx.coroutines.delay(1000) // cada segundo
        }
    }



    val isPreview = LocalInspectionMode.current

    // Estado para lista de dispositivos emparejados
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
            val dispositivos = adapter?.bondedDevices?.toList() ?: emptyList()
            pairedDevices = dispositivos

            if (dispositivos.isEmpty()) {
                Toast.makeText(context, "No hay impresoras emparejadas", Toast.LENGTH_SHORT).show()
                Log.d("Bluetooth", "Bonded devices vacíos")
            } else {
                Toast.makeText(context, "Se cargaron ${dispositivos.size} dispositivos emparejados", Toast.LENGTH_SHORT).show()
                Log.d("Bluetooth", "Dispositivos: ${dispositivos.map { it.name }}")
            }
        } else {
            Toast.makeText(context, "Permisos de Bluetooth no otorgados", Toast.LENGTH_SHORT).show()
        }

    }

    LaunchedEffect(Unit) {
        // Verificamos permisos
        hasBluetoothPermission = bluetoothPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasBluetoothPermission) {
            // Cargamos los dispositivos emparejados primero
            val adapter = BluetoothAdapter.getDefaultAdapter()
            pairedDevices = adapter?.bondedDevices?.toList() ?: emptyList()
        }

        // Ahora buscamos la impresora guardada
        val savedAddress = prefs.getString("impresora_bluetooth", null)
        if (!savedAddress.isNullOrEmpty()) {
            selectedPrinter = pairedDevices.find { it.address == savedAddress }
            selectedPrinter?.let { onImpresoraSeleccionada(it) }
        }
    }




    Scaffold(

        floatingActionButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp), // agrega espacio desde los bordes
                horizontalArrangement = Arrangement.SpaceBetween

            ) {












            }
        },
        floatingActionButtonPosition = FabPosition.Center // Usamos Center porque ahora tenemos botones en ambos lados


    )











    { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

        // 🔹 Zona superior: Estado de internet y fecha/hora
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (estadoInternet.value) "Conectado a Internet" else "Sin conexión a Internet",
                style = MaterialTheme.typography.bodyLarge,
                color = if (estadoInternet.value) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
            Text(
                text = fechaHora.value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )


        }

        Spacer(modifier = Modifier.height(96.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            //Velocimetro(velocidad = LocationService.velocidadActual.value)
            VelocimetroTeslaRojo(velocidad = velocidad)


            Spacer(modifier = Modifier.height(16.dp))





        }


            Spacer(modifier = Modifier.height(30.dp))



        }
    }





}



















@Composable
fun VelocimetroTeslaRojo(velocidad: Float) {

    // Animación suave al cambiar velocidad
    val velocidadAnimada by animateFloatAsState(
        targetValue = velocidad,
        animationSpec = tween(600),
        label = "velocidadAnimada"
    )

    val maxVelocidad = 120f
    val progreso = (velocidadAnimada / maxVelocidad).coerceIn(0f, 1f)

    // 🔥 Colores ROJO/GRIS estilo tu app
    val rojoPrincipal = Color(0xFFFF0000)
    val rojoOscuro = Color(0xFFB71C1C)
    val grisClaro = Color(0xFFDDDDDD)
    val grisMedio = Color(0xFF999999)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
    ) {

        // 🔵 Aro tipo Tesla minimalista
        Canvas(modifier = Modifier.size(250.dp)) {

            val strokeWidth = 22f
            val radius = size.minDimension / 2f - strokeWidth
            val center = Offset(size.width / 2f, size.height / 2f)

            // Aro gris (fondo)
            drawArc(
                color = grisClaro.copy(alpha = 0.4f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 🔥 Aro rojo activo
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

        // 🔥 Velocidad digital gigante estilo Tesla
        Text(
            text = velocidadAnimada.toInt().toString(),
            style = MaterialTheme.typography.displayLarge,
            color = rojoPrincipal
        )

        // Texto km/h
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
    Pantalla_Inicio()
}
