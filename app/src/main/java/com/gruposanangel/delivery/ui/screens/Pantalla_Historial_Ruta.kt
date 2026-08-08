@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.ripple.rememberRipple
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.gruposanangel.delivery.R
import coil.compose.AsyncImage
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryRuta
import com.gruposanangel.delivery.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun Pantalla_Historial_Ruta(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val repoRuta = RepositoryRuta(db.rutaDao(), db.clienteDao())
    val viewModel: HistorialRutaViewModel = viewModel(factory = HistorialRutaViewModelFactory(repoRuta))
    val uiState by viewModel.uiState.collectAsState()

    val isDark = ThemeConfig.isActuallyDark

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(19.4768, -96.5897), 12f)
    }

    // 🛡️ ENCUADRE DE CÁMARA SEGURO Y BLINDADO
    LaunchedEffect(uiState.puntos) {
        if (uiState.puntos.size >= 2) {
            try {
                val builder = LatLngBounds.Builder()
                var validCount = 0
                uiState.puntos.forEach { 
                    if (it.lat != 0.0 && it.lng != 0.0) {
                        builder.include(LatLng(it.lat, it.lng))
                        validCount++
                    }
                }
                
                if (validCount >= 2) {
                    val bounds = builder.build()
                    val dist = FloatArray(1)
                    android.location.Location.distanceBetween(
                        bounds.southwest.latitude, bounds.southwest.longitude,
                        bounds.northeast.latitude, bounds.northeast.longitude, dist
                    )
                    
                    if (dist[0] < 50f) {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(bounds.center, 16f), 800)
                    } else {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 150), 800)
                    }
                }
            } catch (e: Exception) {
                Log.e("HistorialRuta", "Error de cámara", e)
            }
        }
    }

    DeliveryTheme(darkTheme = isDark) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Historial de Ruta", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
                    navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
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
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(mapType = MapType.NORMAL),
                    uiSettings = MapUiSettings(zoomControlsEnabled = false),
                    onMapClick = { viewModel.seleccionarVenta(null) }
                ) {
                    // 🛣️ DIBUJO DE RUTA (Pre-calculado y Seguro)
                    uiState.segments.forEach { segment ->
                        Polyline(
                            points = segment.points,
                            color = Color(segment.color.toInt()), 
                            width = 10f,
                            jointType = JointType.ROUND,
                            startCap = RoundCap(),
                            endCap = RoundCap(),
                            geodesic = true
                        )
                    }

                    // 🏁 MARCADORES DE INICIO Y FIN
                    if (uiState.puntos.isNotEmpty()) {
                        val start = uiState.puntos.first()
                        val end = uiState.puntos.last()
                        
                        Marker(
                            state = rememberMarkerState(key = "start_point", position = LatLng(start.lat, start.lng)),
                            title = "Punto de Partida",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                        )
                        Marker(
                            state = rememberMarkerState(key = "end_point", position = LatLng(end.lat, end.lng)),
                            title = "Última Posición",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                        )
                    }

                    // 🚨 MARCADORES DE INCIDENCIAS
                    uiState.incidencias.forEach { incidencia ->
                        val markerIcon = if (incidencia.tipo == "EXCESO_VELOCIDAD") {
                             BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        } else {
                             BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                        }
                        
                        Marker(
                            state = rememberMarkerState(key = "inc_${incidencia.timestamp}", position = incidencia.latLng),
                            title = if (incidencia.tipo == "EXCESO_VELOCIDAD") "🚀 Exceso de Velocidad" else "⏳ Parada Prolongada",
                            snippet = incidencia.descripcion,
                            icon = markerIcon,
                            alpha = 0.9f
                        )
                    }

                    // 📸 MARCADORES DE VENTAS (Thumbnail del Cliente)
                    uiState.ventas.forEach { venta ->
                        key(venta.id, venta.fotoUrl) {
                            val markerState = rememberMarkerState(key = "venta_${venta.id}", position = venta.latLng)
                            
                            MarkerComposable(
                                state = markerState,
                                title = venta.clienteNombre,
                                snippet = "Venta Total: $${venta.total}",
                                onClick = {
                                    viewModel.seleccionarVenta(venta)
                                    false
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (uiState.selectedVenta?.id == venta.id) 64.dp else 48.dp)
                                        .shadow(8.dp, CircleShape)
                                        .background(if (venta.fueraDeRango) DelisaRed else DelisaRedDark, CircleShape)
                                        .padding(2.5.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!venta.fotoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = coil.request.ImageRequest.Builder(LocalContext.current)
                                                .data(venta.fotoUrl)
                                                .allowHardware(false) 
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            placeholder = painterResource(R.drawable.repartidor),
                                            error = painterResource(R.drawable.repartidor)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Storefront,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Panel de Métricas Inferior / Tarjeta Informativa
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .zIndex(10f)
                ) {
                    if (uiState.selectedVenta != null) {
                        VentaInformativaCard(
                            venta = uiState.selectedVenta!!,
                            onClose = { viewModel.seleccionarVenta(null) },
                            onVerDetalle = { 
                                navController.navigate("detalle_venta_admin/${uiState.selectedVenta!!.id}?mostrarAcciones=false")
                            }
                        )
                    } else {
                        HistorialMetricsCard(
                            uiState = uiState,
                            onDemoClick = { viewModel.cargarDemoData() }
                        )
                    }
                }

                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(0.4f)), Alignment.Center) {
                        CircularProgressIndicator(color = DelisaRed)
                    }
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
    val sdf = SimpleDateFormat("dd MMM", Locale.forLanguageTag("es-MX"))

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
        Box {
            Surface(
                onClick = { showRutaMenu = true },
                shape = RoundedCornerShape(12.dp),
                color = DelisaRed.copy(0.1f),
                modifier = Modifier.height(36.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedRuta.take(10), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DelisaRed)
                    Icon(Icons.Default.ArrowDropDown, null, tint = DelisaRed, modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(
                expanded = showRutaMenu, 
                onDismissRequest = { showRutaMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                if (rutas.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Cargando rutas...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { },
                        enabled = false
                    )
                } else {
                    rutas.forEach { ruta ->
                        DropdownMenuItem(
                            text = { Text(ruta, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) }, 
                            onClick = { 
                                onRutaSelect(ruta)
                                showRutaMenu = false 
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Surface(
            onClick = { showDatePicker = true },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.1f),
            modifier = Modifier.height(36.dp)
        ) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(sdf.format(selectedDate), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.Default.CalendarToday, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
    }

    if (showDatePicker) {
        val calendar = Calendar.getInstance().apply { time = selectedDate }
        val utcMillisForPicker = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = utcMillisForPicker)
        val isDark = ThemeConfig.isActuallyDark

        DeliveryTheme(darkTheme = isDark) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val calUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
                            val localDate = Calendar.getInstance().apply {
                                set(calUtc.get(Calendar.YEAR), calUtc.get(Calendar.MONTH), calUtc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.time
                            onDateSelect(localDate)
                        }
                        showDatePicker = false
                    }) { Text("ACEPTAR", fontWeight = FontWeight.Bold, color = DelisaRed) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            ) {
                DatePicker(
                    state = datePickerState,
                    colors = DatePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        headlineContentColor = DelisaRed,
                        selectedDayContainerColor = DelisaRed,
                        selectedDayContentColor = Color.White,
                        todayDateBorderColor = DelisaRed,
                        todayContentColor = DelisaRed,
                        navigationContentColor = MaterialTheme.colorScheme.onSurface,
                        weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
fun VentaInformativaCard(
    venta: VentaMarker,
    onClose: () -> Unit,
    onVerDetalle: () -> Unit
) {
    val fmtMoneda = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!venta.fotoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = venta.fotoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = venta.clienteNombre,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Venta: ${fmtMoneda.format(venta.total)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = DelisaRed
                )
                if (venta.fueraDeRango) {
                    Surface(
                        color = DelisaRed.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            "FUERA DE RANGO",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = DelisaRed
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
                }
                TextButton(
                    onClick = onVerDetalle,
                    colors = ButtonDefaults.textButtonColors(contentColor = DelisaRed)
                ) {
                    Text("DETALLE", fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun HistorialMetricsCard(uiState: HistorialUiState, onDemoClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricTeslaItem("Distancia", "${"%.1f".format(uiState.distanciaTotalKm)} km", Icons.Default.Route)
                MetricTeslaItem("V. Máx", "${uiState.velocidadMaxima.toInt()} km/h", Icons.Default.Speed)
                
                val horas = uiState.tiempoTotalMinutos / 60
                val mins = uiState.tiempoTotalMinutos % 60
                val tiempoTexto = if (horas > 0) "${horas}h ${mins}m" else "${mins} min"
                
                MetricTeslaItem("Tiempo", tiempoTexto, Icons.Default.Timer)
            }
            
            if (uiState.incidencias.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                Spacer(Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = DelisaRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${uiState.incidencias.size} Incidencias detectadas",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (uiState.puntos.isEmpty()) {
                 Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                     Text(uiState.error ?: "Selecciona una ruta y fecha para ver el historial.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
                     Spacer(Modifier.height(12.dp))
                     Button(
                         onClick = onDemoClick,
                         colors = ButtonDefaults.buttonColors(containerColor = DelisaRed.copy(0.1f), contentColor = DelisaRed),
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
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}
