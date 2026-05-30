@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryRuta
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun Pantalla_Historial_Ruta(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val repoRuta = RepositoryRuta(db.rutaDao(), db.clienteDao())
    val viewModel: HistorialRutaViewModel = viewModel(factory = HistorialRutaViewModelFactory(repoRuta))
    val uiState by viewModel.uiState.collectAsState()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(19.4768, -96.5897), 12f)
    }

    // Encuadre automático cuando cargan puntos
    LaunchedEffect(uiState.puntos) {
        if (uiState.puntos.isNotEmpty()) {
            val builder = LatLngBounds.Builder()
            uiState.puntos.forEach { builder.include(LatLng(it.lat, it.lng)) }
            val bounds = builder.build()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 150), 1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Ruta", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                actions = {
                    RouteAndDatePicker(
                        selectedRuta = uiState.selectedRuta ?: "Seleccionar",
                        selectedDate = uiState.selectedDate,
                        rutas = uiState.rutas.map { it.nombre },
                        onRutaSelect = { viewModel.seleccionarRuta(it) },
                        onDateSelect = { viewModel.seleccionarFecha(it) }
                    )
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapType = MapType.NORMAL),
                uiSettings = MapUiSettings(zoomControlsEnabled = false)
            ) {
                // 🛣️ DIBUJO DE RUTA SEGMENTADA POR VELOCIDAD (Heatmap Style)
                if (uiState.puntos.size > 1) {
                    for (i in 0 until uiState.puntos.size - 1) {
                        val p1 = uiState.puntos[i]
                        val p2 = uiState.puntos[i+1]
                        
                        // Color dinámico según la velocidad del segmento
                        val segmentColor = when {
                            p1.vel > 80.0 -> Color(0xFFFF1744) // Rojo vibrante (Exceso)
                            p1.vel > 50.0 -> Color(0xFFFFD600) // Amarillo (Rápido)
                            p1.vel < 5.0 -> Color(0xFF9E9E9E)  // Gris (Detenido/Tráfico)
                            else -> Color(0xFF00E676)          // Verde (Normal)
                        }

                        Polyline(
                            points = listOf(LatLng(p1.lat, p1.lng), LatLng(p2.lat, p2.lng)),
                            color = segmentColor,
                            width = 12f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                            geodesic = true
                        )
                    }
                }

                // 🏁 MARCADORES DE INICIO Y FIN
                if (uiState.puntos.isNotEmpty()) {
                    val start = uiState.puntos.first()
                    val end = uiState.puntos.last()
                    
                    Marker(
                        state = rememberMarkerState(position = LatLng(start.lat, start.lng)),
                        title = "Punto de Partida",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                    Marker(
                        state = rememberMarkerState(position = LatLng(end.lat, end.lng)),
                        title = "Última Posición",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }

                // 🚨 MARCADORES DE INCIDENCIAS ESTILIZADOS
                uiState.incidencias.forEach { incidencia ->
                    val markerIcon = if (incidencia.tipo == "EXCESO_VELOCIDAD") {
                         BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    } else {
                         BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                    }
                    
                    Marker(
                        state = rememberMarkerState(position = incidencia.latLng),
                        title = if (incidencia.tipo == "EXCESO_VELOCIDAD") "🚀 Exceso de Velocidad" else "⏳ Parada Prolongada",
                        snippet = incidencia.descripcion,
                        icon = markerIcon,
                        alpha = 0.9f
                    )
                }
            }

            // Panel de Métricas Inferior (Tesla Style)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .zIndex(10f)
            ) {
                HistorialMetricsCard(
                    uiState = uiState,
                    onDemoClick = { viewModel.cargarDemoData() }
                )
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(0.4f)), Alignment.Center) {
                    CircularProgressIndicator(color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun RouteAndDatePicker(
    selectedRuta: String,
    selectedDate: Date,
    rutas: List<String>,
    onRutaSelect: (String) -> Unit,
    onDateSelect: (Date) -> Unit
) {
    var showRutaMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
        // Selector de Ruta
        Box {
            Surface(
                onClick = { showRutaMenu = true },
                shape = RoundedCornerShape(12.dp),
                color = Color.Red.copy(0.1f),
                modifier = Modifier.height(36.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedRuta.take(10), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(expanded = showRutaMenu, onDismissRequest = { showRutaMenu = false }) {
                rutas.forEach { ruta ->
                    DropdownMenuItem(text = { Text(ruta) }, onClick = { onRutaSelect(ruta); showRutaMenu = false })
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Selector de Fecha
        Surface(
            onClick = { showDatePicker = true },
            shape = RoundedCornerShape(12.dp),
            color = Color.DarkGray.copy(0.1f),
            modifier = Modifier.height(36.dp)
        ) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(sdf.format(selectedDate), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                Icon(Icons.Default.CalendarToday, null, tint = Color.DarkGray, modifier = Modifier.size(14.dp))
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.time)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { onDateSelect(Date(it)) }
                    showDatePicker = false
                }) { Text("ACEPTAR") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun HistorialMetricsCard(uiState: HistorialUiState, onDemoClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(12.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricTeslaItem("Distancia", "${"%.1f".format(uiState.distanciaTotalKm)} km", Icons.Default.Route)
                MetricTeslaItem("V. Máx", "${uiState.velocidadMaxima.toInt()} km/h", Icons.Default.Speed)
                MetricTeslaItem("Tiempo", "${uiState.tiempoTotalMinutos} min", Icons.Default.Timer)
            }
            
            if (uiState.incidencias.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.Gray.copy(0.1f))
                Spacer(Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${uiState.incidencias.size} Incidencias detectadas (Excesos y paradas)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray
                    )
                }
            } else if (uiState.puntos.isEmpty()) {
                 Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                     Text(uiState.error ?: "Selecciona una ruta y fecha para ver el historial.", color = Color.Gray, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                     Spacer(Modifier.height(12.dp))
                     Button(
                         onClick = onDemoClick,
                         colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.1f), contentColor = Color.Red),
                         shape = RoundedCornerShape(12.dp),
                         modifier = Modifier.height(36.dp)
                     ) {
                         Text("Ver Demo Tesla Style", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                     }
                 }
            }
        }
    }
}

@Composable
fun MetricTeslaItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.Black)
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
    }
}
