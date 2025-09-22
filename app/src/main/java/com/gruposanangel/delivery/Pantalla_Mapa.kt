package com.gruposanangel.delivery.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import com.gruposanangel.delivery.R

data class Cliente(
    val nombreNegocio: String,
    val ubicacionLat: Double,
    val ubicacionLng: Double,
    val valor: String,
    val nombreDueno: String?,
    val telefono: String?,
    val fotoUrl: String? // desde FotografiaCliente en Firebase
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapaScreen() {
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState()
    var userLocation by remember { mutableStateOf(LatLng(19.4768, -96.5897)) }

    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var mapStyleJson by remember { mutableStateOf<String?>(null) }

    var clientes by remember { mutableStateOf(listOf<Cliente>()) }
    var markersVisible by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var selectedCliente by remember { mutableStateOf<Cliente?>(null) }

    val scope = rememberCoroutineScope()

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

    // Obtener ubicación actual
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
                            .bearing(0f)
                            .tilt(0f)
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
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                zoomGesturesEnabled = true,
                scrollGesturesEnabled = true
            ),
            properties = MapProperties(
                isMyLocationEnabled = true,
                mapType = mapType,
                mapStyleOptions = mapStyleJson?.let { com.google.android.gms.maps.model.MapStyleOptions(it) }
            ),
            onMapClick = {
                selectedCliente = null // Oculta info al click en el mapa
            }
        ) {
            if (markersVisible) {
                clientes.forEach { cliente ->
                    val icon = when (cliente.valor.lowercase()) {
                        "alto" -> bitmapDescriptorFromVector(context, R.drawable.marcadorverde)
                        "medio" -> bitmapDescriptorFromVector(context, R.drawable.marcadorrojo)
                        "bajo" -> bitmapDescriptorFromVector(context, R.drawable.marcadoramarillo)
                        else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    }

                    Marker(
                        state = MarkerState(LatLng(cliente.ubicacionLat, cliente.ubicacionLng)),
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
        }

        // Botón para centrar ubicación
        FloatingActionButton(
            onClick = {
                scope.launch {
                    val cameraPosition = CameraPosition.Builder()
                        .target(userLocation)
                        .zoom(18f)
                        .bearing(0f)
                        .tilt(0f)
                        .build()
                    cameraPositionState.animate(
                        update = CameraUpdateFactory.newCameraPosition(cameraPosition),
                        durationMs = 1000
                    )
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

        // Opciones de mapa
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExtendedFloatingActionButton(
                text = { Text("Mapa", color = Color.White) },
                icon = { Icon(Icons.Default.Map, contentDescription = "Opciones", tint = Color.White) },
                onClick = { expanded = !expanded },
                containerColor = Color(0xFFFF0000),
                contentColor = Color.White
            )
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

        // Botón para cargar clientes y centrar cámara
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
                                val boundsBuilder = LatLngBounds.builder()
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000), contentColor = Color.White)
            ) {
                Text("Clientes ${clientes.size}")
            }
        }

        // Panel inferior con info del cliente seleccionado
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
    }
}

// Estilo oscuro
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
