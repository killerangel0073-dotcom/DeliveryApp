 package com.gruposanangel.delivery.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import com.gruposanangel.delivery.R
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan2
import androidx.compose.ui.zIndex
import com.gruposanangel.delivery.utilidades.VendorBatteryIndicator
import com.gruposanangel.delivery.utilidades.VendorGpsIndicator
import com.gruposanangel.delivery.utilidades.VendorSpeedIndicator
import com.gruposanangel.delivery.utilidades.VendorSpeedIndicator2


 // -----------------------------
// MODELOS
// -----------------------------
data class Cliente(
    val id: String,
    val nombreNegocio: String,
    val ubicacionLat: Double,
    val ubicacionLng: Double,
    val valor: String,
    val nombreDueno: String?,
    val telefono: String?,
    val fotoUrl: String?
)

data class VendedorUbicacion(
    val ruta: String,
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val speed: Double,
    val battery: Int,
    val status: String,
    val timestamp: com.google.firebase.Timestamp
)

// -----------------------------
// Clase para animaciones por vendedor
// -----------------------------
class AnimatableMarker(
    lat: Double,
    lng: Double,
    initialAccuracy: Float
) {
    val animLat = Animatable(lat.toFloat())
    val animLng = Animatable(lng.toFloat())
    val animRotation = Animatable(0f)
    val animRadius = Animatable(initialAccuracy)
    val animHaloColorFactor = Animatable((initialAccuracy / 100f).coerceIn(0f, 1f))
}

// -----------------------------
// FUNCIONES AUXILIARES
// -----------------------------
fun colorParaPrecision(accuracy: Float): Color {
    val minAcc = 0f
    val maxAcc = 100f
    val fraction = ((accuracy - minAcc) / (maxAcc - minAcc)).coerceIn(0f, 1f)
    // Verde (buena precisión) a rojo (mala)
    return lerp(Color.Green.copy(alpha = 0.3f), Color.Red.copy(alpha = 0.3f), fraction)
}

fun tiempoTranscurrido(timestamp: Timestamp): String {
    val ahora = Date()
    val tiempoMs = ahora.time - timestamp.toDate().time
    val minutos = TimeUnit.MILLISECONDS.toMinutes(tiempoMs)
    val horas = TimeUnit.MILLISECONDS.toHours(tiempoMs)
    val dias = TimeUnit.MILLISECONDS.toDays(tiempoMs)
    return when {
        minutos < 1 -> "justo ahora"
        minutos < 60 -> "hace $minutos min"
        horas < 24 -> "hace $horas h"
        else -> "hace $dias d"
    }
}

fun calcularAngulo(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
    val deltaLng = lng2 - lng1
    val deltaLat = lat2 - lat1
    val angle = atan2(deltaLng, deltaLat) * (180 / PI)
    return angle.toFloat()
}

fun bitmapDescriptorFromPng(
    context: Context,
    drawableResId: Int,
    width: Int = 80,
    height: Int = 80
): BitmapDescriptor {
    val bitmap = BitmapFactory.decodeResource(context.resources, drawableResId)
    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)
    return BitmapDescriptorFactory.fromBitmap(scaledBitmap)
}

fun bitmapDescriptorFromVector(
    context: Context,
    vectorResId: Int,
    width: Int = 72,
    height: Int = 108
): BitmapDescriptor {
    val drawable = ContextCompat.getDrawable(context, vectorResId)!!
    drawable.setBounds(0, 0, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

// -----------------------------
// SCREEN PRINCIPAL
// -----------------------------
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapaScreen(navController: NavController) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Estado principal (clientes / vendedores)
    var clientes by remember { mutableStateOf(listOf<Cliente>()) }
    var vendedores by remember { mutableStateOf(listOf<VendedorUbicacion>()) }

    // Control de mapa y ubicación usuario
    val cameraPositionState = rememberCameraPositionState()
    var userLocation by remember { mutableStateOf(LatLng(19.4768, -96.5897)) }

    // UI
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var mapStyleJson by remember { mutableStateOf<String?>(null) }
    var markersVisible by remember { mutableStateOf(false) }

    var clientesCargados by remember { mutableStateOf(false) } // 🔥 cache


    var expanded by remember { mutableStateOf(false) }
    var selectedCliente by remember { mutableStateOf<Cliente?>(null) }

    // Vendedor seleccionado para mostrar diálogo
    var vendedorSeleccionadoRuta by remember { mutableStateOf<String?>(null) }



    // Botones de vendedor
    var seguirVendedor by remember { mutableStateOf<String?>(null) }


    // Mantener animatables y MarkerState por vendedor (persistente)
    val vendedorStates: SnapshotStateMap<String, AnimatableMarker> = remember { mutableStateMapOf() }
    val vendedorMarkerStates: SnapshotStateMap<String, MarkerState> = remember { mutableStateMapOf() }





    // Íconos: se recuerdan para no crearlos en cada recomposición
// Para evitar el crash por IBitmapDescriptorFactory
    var mapIsReady by remember { mutableStateOf(false) }

// Íconos solo se crean cuando el mapa ya está inicializado
    val vendedorIcon = remember(mapIsReady) {
        if (mapIsReady)
            bitmapDescriptorFromPng(context, R.drawable.marcador_vendedor, 120, 150)
        else null
    }

    val iconClienteAlto = remember(mapIsReady) {
        if (mapIsReady)
            bitmapDescriptorFromVector(context, R.drawable.marcadorverde)
        else null
    }

    val iconClienteMedio = remember(mapIsReady) {
        if (mapIsReady)
            bitmapDescriptorFromVector(context, R.drawable.marcadorrojo)
        else null
    }

    val iconClienteBajo = remember(mapIsReady) {
        if (mapIsReady)
            bitmapDescriptorFromVector(context, R.drawable.marcadoramarillo)
        else null
    }







    // -----------------------------
    // Firestore: listener para locations (se elimina en DisposableEffect)
    // -----------------------------
    DisposableEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        val registration: ListenerRegistration = db.collection("locations")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("MapaScreen", "Error escuchando locations", error)
                    return@addSnapshotListener
                }
                val lista = snapshot?.documents?.mapNotNull { doc ->
                    val lat = doc.getDouble("latitude")
                    val lng = doc.getDouble("longitude")
                    val acc = doc.getDouble("accuracy")?.toFloat() ?: 0f
                    val battery = doc.getLong("battery")?.toInt() ?: 0
                    val status = doc.getString("status") ?: "OFFLINE"
                    val ts = doc.getTimestamp("timestamp")
                    if (lat != null && lng != null && ts != null) {
                        VendedorUbicacion(
                            ruta = doc.id,
                            lat = lat,
                            lng = lng,
                            accuracy = acc,
                            speed = doc.getDouble("speed") ?: 0.0,
                            battery = battery,
                            status = status,
                            timestamp = ts
                        )
                    } else null
                } ?: emptyList()

                // Actualizamos vendedores (esto dispara recomposición)
                vendedores = lista
            }

        onDispose {
            registration.remove()
        }
    }

    // -----------------------------
    // Obtener última ubicación del usuario (una sola vez)
    // -----------------------------
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    userLocation = LatLng(it.latitude, it.longitude)
                    scope.launch {
                        val initialCamera = CameraPosition.Builder()
                            .target(userLocation)
                            .zoom(18f)
                            .build()
                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newCameraPosition(initialCamera),
                            durationMs = 1000
                        )
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            onMapLoaded = { mapIsReady = true },
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = true,
                mapType = mapType,
                mapStyleOptions = mapStyleJson?.let { com.google.android.gms.maps.model.MapStyleOptions(it) }
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                zoomGesturesEnabled = true,
                scrollGesturesEnabled = true
            ),
            onMapClick = {
                // Si tocamos el mapa:
                selectedCliente = null // Cerramos tarjeta de cliente

                // Si hay alguien marcado en "seguirVendedor", restauramos su tarjeta
                if (seguirVendedor != null) {
                    vendedorSeleccionadoRuta = seguirVendedor
                }
            }

        ) {
            // -----------------------------
            // Vendedores: asegurar estados y animaciones iniciadas UNA VEZ por vendedor
            // -----------------------------
            val minHaloRadius = 5f      // mínimo visible
            val maxHaloRadius = 60f    // máximo razonable





            if (!mapIsReady) return@GoogleMap

            vendedores.forEach { vendedor ->










                // Obtener o crear estado animado por vendedor
                val state = vendedorStates.getOrPut(vendedor.ruta) {
                    AnimatableMarker(
                        vendedor.lat,
                        vendedor.lng,
                        vendedor.accuracy.coerceIn(minHaloRadius, maxHaloRadius)
                    )

                }

                // Obtener o crear MarkerState persistente por vendedor
                val markerState = vendedorMarkerStates.getOrPut(vendedor.ruta) {
                    MarkerState(position = LatLng(state.animLat.value.toDouble(), state.animLng.value.toDouble()))
                }

                // Actualizamos la posición del MarkerState a la posición animada
                markerState.position = LatLng(state.animLat.value.toDouble(), state.animLng.value.toDouble())


                // Si este vendedor es el que estamos siguiendo, mover cámara automáticamente
                LaunchedEffect(state.animLat.value, state.animLng.value, seguirVendedor) {
                    if (seguirVendedor == vendedor.ruta) {
                        val newPos = LatLng(state.animLat.value.toDouble(), state.animLng.value.toDouble())

                        cameraPositionState.animate(
                            update = CameraUpdateFactory.newLatLngZoom(newPos, 18f),
                            durationMs = 600
                        )


                        // Abrir el snippet automáticamente
                        markerState.showInfoWindow()
                    }
                }







                // Lanzar animación de lat/lng cuando cambien las coordenadas del vendedor
                LaunchedEffect(vendedor.ruta, vendedor.timestamp.seconds) {
                    // animar lat y lng en paralelo
                    val animSpec = tween<Float>(durationMillis = 800)
                    launch { state.animLat.animateTo(vendedor.lat.toFloat(), animationSpec = animSpec) }
                    launch { state.animLng.animateTo(vendedor.lng.toFloat(), animationSpec = animSpec) }

                    // actualizar rotation aproximada (ángulo entre puntos)
                    val rot = calcularAngulo(state.animLat.value.toDouble(), state.animLng.value.toDouble(), vendedor.lat, vendedor.lng)
                    state.animRotation.animateTo(rot, animationSpec = tween(500))
                    // actualizamos marker position inmediatamente al valor animado final
                    markerState.position = LatLng(state.animLat.value.toDouble(), state.animLng.value.toDouble())
                }

                // Pulsación del halo: iniciar solo una vez por vendedor


                LaunchedEffect(vendedor.ruta, vendedor.accuracy) {
                    val newRadius = vendedor.accuracy
                        .coerceIn(minHaloRadius, maxHaloRadius)

                    state.animRadius.animateTo(newRadius, tween(600))
                }


                // Animar color del halo cuando cambia accuracy
                LaunchedEffect(vendedor.ruta, vendedor.accuracy) {
                    val factor = (vendedor.accuracy / 100f).coerceIn(0f, 1f)
                    state.animHaloColorFactor.animateTo(factor, animationSpec = tween(800))
                }

                // Posición y color animado para dibujar halo y marker
                val position = LatLng(state.animLat.value.toDouble(), state.animLng.value.toDouble())

                val haloColor = lerp(
                    Color(0xFFB0B0B0).copy(alpha = 0.35f),   // gris claro (buena precisión)
                    Color(0xFFFF4444).copy(alpha = 0.35f),   // rojo app (mala precisión)
                    state.animHaloColorFactor.value
                )

                // 🔥 RADIO VISUAL (exagerado para que se vea)
                val visualRadius = (state.animRadius.value * 5f)
                    .coerceIn(30f, 180f)



                // Dibujar círculo (halo)
                Circle(
                    center = position,
                    radius = visualRadius.toDouble(),   // 👈 AQUÍ
                    strokeColor = haloColor,
                    strokeWidth = 2f,
                    fillColor = haloColor.copy(alpha = 0.35f)
                )

                // Mostrar marker (uso de markerState persistente)
                Marker(
                    state = markerState,
                    title = vendedor.ruta,
                    icon = vendedorIcon,
                    onClick = {
                        vendedorSeleccionadoRuta = vendedor.ruta
                        seguirVendedor = vendedor.ruta
                        selectedCliente = null // 👈 Si tocamos vendedor, ocultamos cliente


                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(position, 18f),
                                durationMs = 600
                            )
                        }
                        true
                    }
                )

            }


            // -----------------------------
            // Clientes (solo si markersVisible)
            // -----------------------------
            if (markersVisible && mapIsReady) {
                clientes.forEach { cliente ->
                    if (!mapIsReady) return@forEach

                    val icon = when (cliente.valor.lowercase()) {
                        "alto" -> iconClienteAlto
                        "medio" -> iconClienteMedio
                        "bajo" -> iconClienteBajo
                        else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    }

                    // Usamos MarkerState simple, no es costoso para pocos clientes
                    val markerState = remember(cliente.nombreNegocio, cliente.ubicacionLat, cliente.ubicacionLng) {
                        MarkerState(LatLng(cliente.ubicacionLat, cliente.ubicacionLng))
                    }

                    Marker(
                        state = markerState,
                        title = cliente.nombreNegocio,
                        icon = icon,
                        onClick = {
                            selectedCliente = cliente
                            vendedorSeleccionadoRuta = null // 👈 OCULTAMOS la tarjeta del vendedor
                            scope.launch {
                                cameraPositionState.animate(
                                    update = CameraUpdateFactory.newLatLng(LatLng(cliente.ubicacionLat, cliente.ubicacionLng)),
                                    durationMs = 500
                                )
                            }
                            true
                        }
                    )
                }
            }
        } // fin GoogleMap

        // -----------------------------
        // Botón centrar ubicación
        // -----------------------------
        FloatingActionButton(
            onClick = {
                scope.launch {
                    val cameraPosition = CameraPosition.Builder()
                        .target(userLocation)
                        .zoom(18f)
                        .build()
                    cameraPositionState.animate(update = CameraUpdateFactory.newCameraPosition(cameraPosition), durationMs = 1000)
                }
            },
            containerColor = Color(0xFFFF0000),
            contentColor = Color.White,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomEnd)
                .size(56.dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Mi ubicación")
        }

        // -----------------------------
        // Opciones de mapa
        // -----------------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { expanded = !expanded },
                containerColor = Color(0xFFFF0000),
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Map, contentDescription = "Mapa")
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Estándar") }, onClick = {
                    mapType = MapType.NORMAL; mapStyleJson = null; expanded = false
                })
                DropdownMenuItem(text = { Text("Satélite") }, onClick = {
                    mapType = MapType.SATELLITE; mapStyleJson = null; expanded = false
                })
                DropdownMenuItem(text = { Text("Oscuro") }, onClick = {
                    mapType = MapType.NORMAL; mapStyleJson = darkMapStyleJson; expanded = false
                })
            }
        }

        // ------------------------------------------------------------------
// BOTONES RUTA 1 / RUTA 2 – ESQUINA SUPERIOR IZQUIERDA
// ------------------------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            // ---------------- BOTÓN RUTA 1 ----------------
            val ruta1Activa = seguirVendedor == "Ruta 1 Delisa"

            Button(
                onClick = {
                    if (ruta1Activa) {
                        // Apagar seguimiento
                        seguirVendedor = null
                        vendedorSeleccionadoRuta = null

                        vendedorMarkerStates["Ruta 1 Delisa"]?.hideInfoWindow()
                    } else {
                        // Encender seguimiento
                        seguirVendedor = "Ruta 1 Delisa"
                        vendedorSeleccionadoRuta = "Ruta 1 Delisa"
                        vendedorMarkerStates["Ruta 1 Delisa"]?.showInfoWindow()
                    }

                    selectedCliente = null
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ruta1Activa) Color(0xFFFF0000) else Color(0xFFCCCCCC),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Ruta 1", style = MaterialTheme.typography.labelSmall)
            }

            // ---------------- BOTÓN RUTA 2 ----------------
            val ruta2Activa = seguirVendedor == "Ruta 2 Delisa"

            Button(
                onClick = {
                    if (ruta2Activa) {
                        // Apagar seguimiento
                        seguirVendedor = null
                        vendedorSeleccionadoRuta = null
                        vendedorMarkerStates["Ruta 2 Delisa"]?.hideInfoWindow()
                    } else {
                        // Encender seguimiento
                        seguirVendedor = "Ruta 2 Delisa"
                        vendedorSeleccionadoRuta = "Ruta 2 Delisa"
                        vendedorMarkerStates["Ruta 2 Delisa"]?.showInfoWindow()
                    }

                    selectedCliente = null
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ruta2Activa) Color(0xFFFF0000) else Color(0xFFCCCCCC),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Ruta 2", style = MaterialTheme.typography.labelSmall)
            }

        }







        // -----------------------------
        // Botón para cargar clientes y centrar cámara
        // -----------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {



            Button(
                onClick = {

                    // 🔴 OCULTAR CLIENTES
                    if (markersVisible) {
                        markersVisible = false
                        selectedCliente = null
                        Toast.makeText(context, "Clientes ocultados", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // 🟢 MOSTRAR CLIENTES (si ya están en cache)
                    if (clientesCargados) {
                        markersVisible = true
                        Toast.makeText(context, "Clientes mostrados", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // 🟡 CARGAR DESDE FIRESTORE (solo 1 vez)
                    Toast.makeText(context, "Consultando clientes...", Toast.LENGTH_SHORT).show()

                    FirebaseFirestore.getInstance()
                        .collection("clientes")
                        .whereEqualTo("activo", true)
                        .get()
                        .addOnSuccessListener { result ->
                            val lista = result.documents.mapNotNull { doc ->
                                val geo = doc.getGeoPoint("ubicacion")
                                val valor = doc.getString("valor") ?: "medio"
                                geo?.let {
                                    Cliente(
                                        id = doc.id,
                                        nombreNegocio = doc.getString("nombreNegocio") ?: "Sin nombre",
                                        ubicacionLat = it.latitude,
                                        ubicacionLng = it.longitude,
                                        valor = valor,
                                        nombreDueno = doc.getString("nombreDueno"),
                                        telefono = doc.getString("telefono"),
                                        fotoUrl = doc.getString("FotografiaCliente")
                                    )
                                }
                            }

                            clientes = lista
                            clientesCargados = true
                            markersVisible = true

                            Toast.makeText(
                                context,
                                "Clientes cargados: ${clientes.size}",
                                Toast.LENGTH_LONG
                            ).show()

                            // Centrar cámara
                            if (clientes.isNotEmpty()) {
                                val bounds = com.google.android.gms.maps.model.LatLngBounds.builder()
                                clientes.forEach {
                                    bounds.include(LatLng(it.ubicacionLat, it.ubicacionLng))
                                }

                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngBounds(bounds.build(), 100),
                                        durationMs = 900
                                    )
                                }
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Error al cargar clientes", Toast.LENGTH_LONG).show()
                        }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (markersVisible) Color(0xFF666666) else Color(0xFFFF0000),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                val clientesLabel =
                    if (clientes.isEmpty()) "Clientes"
                    else "Clientes ${clientes.size}"

                Text(
                    if (markersVisible)
                        "Ocultar $clientesLabel"
                    else
                        clientesLabel
                )


            }

        }






        // -----------------------------
        // Botón para mostrar info del vendedor seleccionado
        val vendedorSeleccionadoActual =
            vendedores.find { it.ruta == vendedorSeleccionadoRuta }





        vendedorSeleccionadoActual?.let { vendedor ->

            // 1. CÁLCULO DE ESTADO VITAL
            val ahora = System.currentTimeMillis()
            val reporteMs = vendedor.timestamp.toDate().time
            val minutosSinReportar = (ahora - reporteMs) / 60000

            val estadoVital = when {
                minutosSinReportar > 5 -> "ALERTA"
                vendedor.speed > 1.5   -> "MOVING"
                else                   -> "STANDBY"
            }

            // Corregido: Se agrega 'else' para exhaustividad
            val colorEstado = when(estadoVital) {
                "MOVING" -> Color(0xFF00C853)
                "STANDBY" -> Color(0xFFFFA000)
                "ALERTA" -> Color(0xFFD32F2F)
                else -> Color.Gray // Rama de seguridad
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
                    .zIndex(2f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                elevation = CardDefaults.cardElevation(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    // --------- HEADER ---------
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = vendedor.ruta,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF212121),
                                modifier = Modifier.align(Alignment.Center)
                            )

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = colorEstado.copy(alpha = 0.15f),
                                modifier = Modifier.align(Alignment.CenterEnd)
                            ) {
                                Text(
                                    text = when(estadoVital) {
                                        "MOVING" -> "CONDUCIENDO"
                                        "STANDBY" -> "DETENIDO"
                                        "ALERTA" -> "ALERTA"
                                        else -> "DESCONOCIDO" // Rama de seguridad
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    color = colorEstado,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        val subtextoColor = if (estadoVital == "ALERTA") Color(0xFFD32F2F) else Color.Gray
                        Text(
                            text = if (estadoVital == "ALERTA")
                                "⚠️ Sin señal desde hace $minutosSinReportar min"
                            else "Actualizado ${tiempoTranscurrido(vendedor.timestamp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = subtextoColor,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Divider(color = Color(0xFFEEEEEE))
                    Spacer(Modifier.height(12.dp))

                    // --------- INFO GRID + BATERÍA ---------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        VendorGpsIndicator(accuracy = vendedor.accuracy)
                        VendorSpeedIndicator2(speed = vendedor.speed)
                        VendorBatteryIndicator(batteryLevel = vendedor.battery)


                    }
                }
            }
        }





        // -----------------------------
        // Panel inferior para cliente seleccionado
        // -----------------------------
        selectedCliente?.let { cliente ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter)
                    .zIndex(3f) // 🔥 CLAVE
                    .clickable {
                        navController.navigate("detalle_cliente/${cliente.id}?origen=Mapa") {
                            launchSingleTop = true
                        }


                    },


                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = cliente.fotoUrl,
                        contentDescription = cliente.nombreNegocio,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(cliente.nombreNegocio, style = MaterialTheme.typography.titleMedium)
                        Text(cliente.nombreDueno ?: "", style = MaterialTheme.typography.bodyMedium)
                        Text(cliente.telefono ?: "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    } // Box
}


 @Composable
 fun InfoItemUber(
     label: String,
     value: String,
     icon: ImageVector
 ) {
     Column(
         horizontalAlignment = Alignment.CenterHorizontally,
         modifier = Modifier.width(90.dp)
     ) {
         Box(
             modifier = Modifier
                 .size(32.dp)
                 .background(Color(0xFFF5F5F5), shape = CircleShape),
             contentAlignment = Alignment.Center
         ) {
             Icon(
                 imageVector = icon,
                 contentDescription = label,
                 tint = Color(0xFF212121),
                 modifier = Modifier.size(18.dp)
             )
         }

         Spacer(Modifier.height(4.dp))

         Text(
             text = value,
             style = MaterialTheme.typography.bodyMedium,
             color = Color(0xFF212121)
         )

         Text(
             text = label,
             style = MaterialTheme.typography.labelSmall,
             color = Color.Gray
         )
     }
 }

 // -----------------------------
// ESTILO MAPA OSCURO (igual que antes)
// -----------------------------
val darkMapStyleJson = """
[
  {"featureType": "all","elementType": "labels.text.fill","stylers":[{"color":"#f0f0f0"}]},
  {"featureType": "all","elementType": "labels.text.stroke","stylers":[{"color":"#181818"}]},

  {"featureType": "landscape","elementType": "geometry","stylers":[{"color":"#222222"}]},

  {"featureType": "poi","elementType": "geometry","stylers":[{"color":"#2b2b2b"}]},
  {"featureType": "poi","elementType": "labels.text.fill","stylers":[{"color":"#ffffff"}]},

  {"featureType": "road","elementType": "geometry","stylers":[{"color":"#333333"}]},
  {"featureType": "road.highway","elementType": "geometry","stylers":[{"color":"#8a1e1e"}]},
  {"featureType": "road.highway","elementType": "geometry.stroke","stylers":[{"color":"#c74444"}]},

  {"featureType": "water","elementType": "geometry","stylers":[{"color":"#191919"}]}
]
""".trimIndent()





@Preview(showBackground = true)
@Composable
fun MapaScreenPreview() {
    val navController = rememberNavController()
    MapaScreen(navController = navController)
}

