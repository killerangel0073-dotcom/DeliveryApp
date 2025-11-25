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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

// -----------------------------
// MODELOS
// -----------------------------
data class Cliente(
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
fun MapaScreen() {
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
    var expanded by remember { mutableStateOf(false) }
    var selectedCliente by remember { mutableStateOf<Cliente?>(null) }

    // Vendedor seleccionado para mostrar diálogo
    var vendedorSeleccionado by remember { mutableStateOf<VendedorUbicacion?>(null) }


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
                    val ts = doc.getTimestamp("timestamp")
                    if (lat != null && lng != null && ts != null) {
                        VendedorUbicacion(
                            ruta = doc.id,
                            lat = lat,
                            lng = lng,
                            accuracy = acc,
                            speed = doc.getDouble("speed") ?: 0.0,
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
            onMapClick = { selectedCliente = null }
        ) {
            // -----------------------------
            // Vendedores: asegurar estados y animaciones iniciadas UNA VEZ por vendedor
            // -----------------------------
            val minHaloRadius = 15f

            if (!mapIsReady) return@GoogleMap

            vendedores.forEach { vendedor ->

            // Obtener o crear estado animado por vendedor
                val state = vendedorStates.getOrPut(vendedor.ruta) {
                    AnimatableMarker(vendedor.lat, vendedor.lng, maxOf(vendedor.accuracy, minHaloRadius))
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
                LaunchedEffect(vendedor.ruta) {
                    // Hacemos que el radio pulse de 1.0x a 1.1x del accuracy
                    val base = maxOf(vendedor.accuracy, minHaloRadius)
                    state.animRadius.animateTo(
                        targetValue = base * 1.1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1000),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
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



                // Dibujar círculo (halo)
                Circle(
                    center = position,
                    radius = state.animRadius.value.toDouble(),
                    strokeColor = haloColor,
                    strokeWidth = 2f,
                    fillColor = haloColor.copy(alpha = 0.35f)
                )

                // Mostrar marker (uso de markerState persistente)
                Marker(
                    state = markerState,
                    title = vendedor.ruta,
                    snippet = "${"%.1f".format(vendedor.speed)} km/h, ${vendedor.accuracy.toInt()} m, ${tiempoTranscurrido(vendedor.timestamp)}",
                    icon = vendedorIcon,
                    //rotation = state.animRotation.value,
                    onClick = {
                        vendedorSeleccionado = vendedor
                        seguirVendedor = vendedor.ruta   // 🔥 CAMBIAR SEGUIMIENTO AUTOMÁTICO
                        // centrar cámara en el vendedor al hacer click
                        scope.launch {
                            val camera = CameraPosition.Builder()
                                .target(position)
                                .zoom(18f)
                                .build()
                            cameraPositionState.animate(update = CameraUpdateFactory.newCameraPosition(camera), durationMs = 700)
                        }
                        false
                    }
                )
            }

            // -----------------------------
            // Dialogo con info completa del vendedor seleccionado
            // -----------------------------
            vendedorSeleccionado?.let { vendedor ->
                val tiempo = tiempoTranscurrido(vendedor.timestamp)
                AlertDialog(
                    onDismissRequest = { vendedorSeleccionado = null },
                    title = { Text("Vendedor: ${vendedor.ruta}") },
                    text = {
                        Text(
                            "Velocidad: ${"%.1f".format(vendedor.speed)} km/h\n" +
                                    "Precisión: ${vendedor.accuracy.toInt()} m\n" +
                                    "Última actualización: $tiempo\n" +
                                    "Ubicación: ${vendedor.lat}, ${vendedor.lng}"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { vendedorSeleccionado = null }) { Text("Cerrar") }
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
                        vendedorMarkerStates["Ruta 1 Delisa"]?.hideInfoWindow()  // 🔥 CERRAR SNIPPET
                    } else {
                        // Encender seguimiento
                        seguirVendedor = "Ruta 1 Delisa"
                        vendedorMarkerStates["Ruta 1 Delisa"]?.showInfoWindow()  // Opcional: abrir snippet
                    }

                    vendedorSeleccionado = null
                    selectedCliente = null
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ruta1Activa) Color(0xFFFF0000) else Color(0xFFCCCCCC),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),   // 🔥 menos redondeado
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
                        vendedorMarkerStates["Ruta 2 Delisa"]?.hideInfoWindow()  // 🔥 CERRAR SNIPPET
                    } else {
                        // Encender seguimiento
                        seguirVendedor = "Ruta 2 Delisa"
                        vendedorMarkerStates["Ruta 2 Delisa"]?.showInfoWindow()  // Opcional
                    }

                    vendedorSeleccionado = null
                    selectedCliente = null
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (ruta2Activa) Color(0xFFFF0000) else Color(0xFFCCCCCC),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),   // 🔥 menos redondeado
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
                    Toast.makeText(context, "Consultando clientes...", Toast.LENGTH_SHORT).show()
                    val db = FirebaseFirestore.getInstance()
                    // Cargar clientes activos (solo una vez por click)
                    db.collection("clientes")
                        .whereEqualTo("activo", true)
                        .get()
                        .addOnSuccessListener { result ->
                            val lista = result.documents.mapNotNull { doc ->
                                val geo = doc.getGeoPoint("ubicacion")
                                val valor = doc.getString("valor") ?: "medio"
                                if (geo != null) {
                                    Cliente(
                                        nombreNegocio = doc.getString("nombreNegocio") ?: "Sin nombre",
                                        ubicacionLat = geo.latitude,
                                        ubicacionLng = geo.longitude,
                                        valor = valor,
                                        nombreDueno = doc.getString("nombreDueno"),
                                        telefono = doc.getString("telefono"),
                                        fotoUrl = doc.getString("FotografiaCliente")
                                    )
                                } else {
                                    Log.e("MapaScreen", "Documento sin ubicación: ${doc.id}")
                                    null
                                }
                            }
                            clientes = lista
                            markersVisible = clientes.isNotEmpty()
                            Toast.makeText(context, "Clientes cargados: ${clientes.size}", Toast.LENGTH_LONG).show()

                            if (clientes.isNotEmpty()) {
                                val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.builder()
                                clientes.forEach { c -> boundsBuilder.include(LatLng(c.ubicacionLat, c.ubicacionLng)) }
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100),
                                        durationMs = 1000
                                    )
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("MapaScreen", "Error al traer clientes", e)
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000), contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),   // 🔥 menos redondeado
            ) {
                Text("Clientes ${clientes.size}")
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
                    .align(Alignment.BottomCenter),
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

// -----------------------------
// ESTILO MAPA OSCURO (igual que antes)
// -----------------------------
val darkMapStyleJson = """
[
  {"elementType": "geometry","stylers":[{"color":"#242f3e"}]},
  {"elementType": "labels.text.fill","stylers":[{"color":"#746855"}]},
  {"elementType": "labels.text.stroke","stylers":[{"color":"#242f3e"}]},
  {"featureType": "administrative.locality","elementType": "labels.text.fill","stylers":[{"color":"#d59563"}]},
  {"featureType": "poi","elementType": "labels.text.fill","stylers":[{"color":"#d59563"}]},
  {"featureType": "poi.park","elementType": "geometry","stylers":[{"color":"#263c3f"}]},
  {"featureType": "poi.park","elementType": "labels.text.fill","stylers":[{"color":"#6b9a76"}]},
  {"featureType": "road","elementType": "geometry","stylers":[{"color":"#38414e"}]},
  {"featureType": "road","elementType": "geometry.stroke","stylers":[{"color":"#212a37"}]},
  {"featureType": "road","elementType": "labels.text.fill","stylers":[{"color":"#9ca5b3"}]},
  {"featureType": "road.highway","elementType": "geometry","stylers":[{"color":"#746855"}]},
  {"featureType": "road.highway","elementType": "geometry.stroke","stylers":[{"color":"#1f2835"}]},
  {"featureType": "road.highway","elementType": "labels.text.fill","stylers":[{"color":"#f3d19c"}]},
  {"featureType": "transit","elementType": "geometry","stylers":[{"color":"#2f3948"}]},
  {"featureType": "transit.station","elementType": "labels.text.fill","stylers":[{"color":"#d59563"}]},
  {"featureType": "water","elementType": "geometry","stylers":[{"color":"#17263c"}]},
  {"featureType": "water","elementType": "labels.text.fill","stylers":[{"color":"#515c6d"}]},
  {"featureType": "water","elementType": "labels.text.stroke","stylers":[{"color":"#17263c"}]}
]
""".trimIndent()

@Preview(showBackground = true)
@Composable
fun MapaScreenPreview() {
    MapaScreen()
}
