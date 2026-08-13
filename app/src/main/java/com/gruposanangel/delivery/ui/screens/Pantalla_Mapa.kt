@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.ui.theme.*
import com.gruposanangel.delivery.utilidades.VendorBatteryIndicator
import com.gruposanangel.delivery.utilidades.VendorGpsIndicator
import com.gruposanangel.delivery.utilidades.VendorSpeedIndicator2
import kotlinx.coroutines.launch
import java.util.Date

@SuppressLint("MissingPermission")
@Composable
fun MapaScreen(
    navController: NavController,
    viewModel: MapaViewModel,
    isAdminOverride: Boolean? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    // 📏 VARIABLES ADAPTATIVAS SEGÚN EL TAMAÑO DE PANTALLA
    val esPantallaPequena = screenWidth < 380
    val tamIconoLateral = if (esPantallaPequena) 18.dp else 22.dp
    val tamBotonLateral = if (esPantallaPequena) 38.dp else 44.dp
    val tamTextoRuta = if (esPantallaPequena) 10.sp else 12.sp
    val tamIconoRuta = if (esPantallaPequena) 14.dp else 16.dp
    val paddingBotones = if (esPantallaPequena) 8.dp else 16.dp

    val isDark = ThemeConfig.isActuallyDark

    // 🔥 SINCRONIZACIÓN EN TIEMPO REAL Y AUTO-TEMA DE MAPA
    LaunchedEffect(isAdminOverride) {
        if (isAdminOverride != null) {
            viewModel.sobreescribirAdmin(isAdminOverride)
        }
        viewModel.startRealtimeSync(context)
        if (uiState.mapStyleJson == null && uiState.mapType == MapType.NORMAL) {
            if (isDark) {
                viewModel.setMapType(MapType.NORMAL, darkMapStyleJson)
            }
        }
    }

    LaunchedEffect(isDark) {
        if (uiState.mapType == MapType.NORMAL) {
            viewModel.setMapType(MapType.NORMAL, if (isDark) darkMapStyleJson else null)
        }
    }

    // 🔔 GESTIÓN DE ERRORES
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    var mapIsReady by remember { mutableStateOf(false) }

    val vendedorStates: SnapshotStateMap<String, AnimatableMarker> = remember { mutableStateMapOf() }
    val vendedorMarkerStates: SnapshotStateMap<String, MarkerState> = remember { mutableStateMapOf() }

    // Iconos
    val vendedorIcon = remember(mapIsReady) { if (mapIsReady) bitmapDescriptorFromPng(context, R.drawable.marcador_vendedor, 120, 150) else null }
    val iconAlto = remember(mapIsReady) { if (mapIsReady) bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadorverde, 100, 160) else null }
    val iconMedio = remember(mapIsReady) { if (mapIsReady) bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadoramarillo, 100, 160) else null }
    val iconBajo = remember(mapIsReady) { if (mapIsReady) bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadorrojo, 100, 160) else null }
    val iconAltoSel = remember(mapIsReady) { if (mapIsReady) bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadorverde, 130, 208) else null }
    val iconMedioSel = remember(mapIsReady) { if (mapIsReady) bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadoramarillo, 130, 208) else null }
    val iconBajoSel = remember(mapIsReady) { if (mapIsReady) bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadorrojo, 130, 208) else null }
    val iconHojaRuta = remember(mapIsReady) { if (mapIsReady) bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadornaranja, 130, 208) else null }
    val iconNegroSel = remember(mapIsReady) { if (mapIsReady) bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadornegro, 130, 208) else null }

    // 📏 CÁLCULO DE ELEVACIÓN DINÁMICA
    val hayRuta = uiState.modoHojaRuta && uiState.itinerarioActivo != null
    val hayDetalle = uiState.selectedCliente != null || uiState.vendedorSeleccionadoRuta != null
    
    val paddingFab by animateDpAsState(
        targetValue = when {
            hayRuta && hayDetalle -> 210.dp
            hayDetalle -> 140.dp
            hayRuta -> 80.dp
            else -> 0.dp
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "fabPadding"
    )

    // ✅ ESCUCHA DE EVENTOS DE CÁMARA
    LaunchedEffect(Unit) {
        viewModel.cameraEvents.collect { event ->
            if (event is CameraEvent.CenterOnClients) {
                val listaAEncuadrar = if (uiState.modoHojaRuta) uiState.clientesRuta else uiState.clientes
                if (listaAEncuadrar.isNotEmpty()) {
                    try {
                        val builder = LatLngBounds.Builder()
                        listaAEncuadrar.forEach { builder.include(LatLng(it.ubicacionLat, it.ubicacionLng)) }
                        viewModel.cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 200), 1200)
                    } catch (e: Exception) { }
                }
            }
        }
    }

    // ✅ CENTRADO INICIAL GPS
    LaunchedEffect(Unit) {
        if (viewModel.cameraPositionState.isMoving.not() && viewModel.cameraPositionState.position.target.latitude == 19.4768) {
            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val pos = LatLng(it.latitude, it.longitude)
                        scope.launch {
                            viewModel.cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                        }
                    }
                }
            }
        }
    }

    // 📱 CONTENEDOR PRINCIPAL - ELIMINADO SCAFFOLD PARA EDGE-TO-EDGE
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GoogleMap(
            onMapLoaded = { mapIsReady = true },
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = viewModel.cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = true,
                mapType = uiState.mapType,
                mapStyleOptions = uiState.mapStyleJson?.let { MapStyleOptions(it) }
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false
            ),
            onMapClick = {
                viewModel.selectCliente(null)
                viewModel.selectVendedor(null)
            }
        ) {
            if (!mapIsReady) return@GoogleMap

            // DIBUJAR VENDEDORES
            uiState.vendedores.forEach { vendedor ->
                val state = vendedorStates.getOrPut(vendedor.ruta) {
                    AnimatableMarker(vendedor.lat, vendedor.lng, vendedor.accuracy.coerceIn(5f, 60f))
                }
                val markerState = vendedorMarkerStates.getOrPut(vendedor.ruta) {
                    MarkerState(position = LatLng(state.animLat.value.toDouble(), state.animLng.value.toDouble()))
                }

                markerState.position = LatLng(state.animLat.value.toDouble(), state.animLng.value.toDouble())

                LaunchedEffect(vendedor.lat, vendedor.lng, uiState.seguirVendedor) {
                    if (uiState.seguirVendedor == vendedor.ruta && vendedor.lat != 0.0) {
                        val targetPos = LatLng(vendedor.lat, vendedor.lng)
                        val distResults = FloatArray(1)
                        android.location.Location.distanceBetween(viewModel.cameraPositionState.position.target.latitude, viewModel.cameraPositionState.position.target.longitude, targetPos.latitude, targetPos.longitude, distResults)
                        if (distResults[0] > 5f) viewModel.cameraPositionState.animate(CameraUpdateFactory.newLatLng(targetPos), 1000)
                        markerState.showInfoWindow()
                    }
                }

                LaunchedEffect(vendedor.ruta, vendedor.timestamp.seconds) {
                    val animSpec = tween<Float>(800)
                    launch { state.animLat.animateTo(vendedor.lat.toFloat(), animSpec) }
                    launch { state.animLng.animateTo(vendedor.lng.toFloat(), animSpec) }
                    state.animRotation.animateTo(calcularAngulo(state.animLat.value.toDouble(), state.animLng.value.toDouble(), vendedor.lat, vendedor.lng), tween(500))
                }

                val haloColor = lerp(MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f), DelisaRed.copy(0.35f), state.animHaloColorFactor.value)
                Circle(center = markerState.position, radius = (state.animRadius.value * 5f).coerceIn(30f, 180f).toDouble(), strokeColor = haloColor, strokeWidth = 2f, fillColor = haloColor.copy(0.2f))
                Marker(state = markerState, title = vendedor.ruta, icon = vendedorIcon, onClick = { viewModel.selectVendedor(vendedor.ruta); scope.launch { viewModel.cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(markerState.position, 18f)) }; true })
            }

            // 🛡️ DIBUJAR CLIENTES - LÓGICA ULTRA-ESTABLE
            val idsRuta = remember(uiState.modoHojaRuta, uiState.clientesRuta) { 
                if (uiState.modoHojaRuta) uiState.clientesRuta.map { it.id }.toSet() else emptySet() 
            }
            val combinada = (uiState.clientes + uiState.clientesRuta).distinctBy { it.id }

            combinada.forEach { cliente ->
                val esRuta = idsRuta.contains(cliente.id)
                val esCobertura = uiState.markersVisible && uiState.clientes.any { it.id == cliente.id }
                val visible = esRuta || esCobertura
                
                val isSelected = uiState.selectedCliente?.id == cliente.id
                val markerIcon = remember(cliente.valor, isSelected, esRuta, mapIsReady) {
                    if (!mapIsReady) return@remember null
                    if (esRuta) { if (isSelected) iconNegroSel else iconHojaRuta }
                    else {
                        when (cliente.valor.lowercase()) {
                            "alto" -> if (isSelected) iconAltoSel else iconAlto
                            "medio" -> if (isSelected) iconMedioSel else iconMedio
                            "bajo" -> if (isSelected) iconBajoSel else iconBajo
                            else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        }
                    }
                }

                key(cliente.id) {
                    Marker(
                        state = rememberMarkerState(key = cliente.id, position = LatLng(cliente.ubicacionLat, cliente.ubicacionLng)),
                        visible = visible,
                        zIndex = if (esRuta) (if (isSelected) 3f else 2.5f) else (if (isSelected) 2f else 1f),
                        title = cliente.nombreNegocio,
                        icon = markerIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                        onClick = { viewModel.selectCliente(cliente); scope.launch { viewModel.cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(cliente.ubicacionLat, cliente.ubicacionLng), 16.5f), 800) }; true }
                    )
                }
            }
        }

        // CONTROLES FLOTANTES
        val p = uiState.puestoTrabajo?.trim() ?: ""
        val esAdminEfectivo = p in listOf("CEO", "Gerente General", "Supervisor", "Administración", "Gerente")

        // 🛣️ BOTONES DE RUTA LATERALES (TOP START)
        Column(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = paddingBotones, top = paddingBotones),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (esAdminEfectivo) {
                uiState.vendedores.forEach { v ->
                    val activo = uiState.seguirVendedor == v.ruta
                    val cardBg = if (activo) DelisaRed else if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    val textTint = if (activo) Color.White else if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    
                    Card(
                        modifier = Modifier.shadow(4.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).clickable { viewModel.toggleSeguirVendedor(v.ruta) }, 
                        shape = RoundedCornerShape(16.dp), 
                        colors = CardDefaults.cardColors(containerColor = cardBg)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalShipping, null, tint = if (activo) Color.White else DelisaRed, modifier = Modifier.size(tamIconoRuta))
                            Spacer(Modifier.width(6.dp)); Text(text = v.ruta.replace(" Delisa", ""), fontSize = tamTextoRuta, fontWeight = FontWeight.Black, color = textTint)
                        }
                    }
                }
            } else {
                uiState.miRuta?.let { rutaAsignada ->
                    val activo = uiState.seguirVendedor == rutaAsignada
                    val cardBg = if (activo) DelisaRed else if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    val textTint = if (activo) Color.White else if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

                    Card(
                        modifier = Modifier.shadow(4.dp, RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp)).clickable { viewModel.toggleSeguirVendedor(rutaAsignada) }, 
                        shape = RoundedCornerShape(12.dp), 
                        colors = CardDefaults.cardColors(containerColor = cardBg)
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MyLocation, null, tint = if (activo) Color.White else DelisaRed, modifier = Modifier.size(tamIconoRuta))
                            Spacer(Modifier.width(6.dp)); Text(text = "MI RUTA", fontSize = tamTextoRuta, fontWeight = FontWeight.Black, color = textTint)
                        }
                    }
                }
            }
        }

        // 🌍 CAPAS Y VISOR (TOP END)
        var showLayerSheet by remember { mutableStateOf(false) }
        var showRouteSelector by remember { mutableStateOf(false) }
        var showRouteMenu by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()
        Column(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(paddingBotones).padding(top = paddingBotones), horizontalAlignment = Alignment.End) {
            val surfaceColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
            Surface(onClick = { showLayerSheet = true }, color = surfaceColor, shadowElevation = 4.dp, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(tamBotonLateral)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Layers, null, modifier = Modifier.size(tamIconoLateral), tint = DelisaRed) }
            }
            Spacer(Modifier.height(10.dp))
            Surface(onClick = { showRouteSelector = true }, color = if (uiState.modoHojaRuta) WarningOrange else surfaceColor, shadowElevation = 4.dp, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(tamBotonLateral)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Assignment, null, modifier = Modifier.size(tamIconoLateral), tint = if (uiState.modoHojaRuta) Color.White else WarningOrange) }
            }
            if (esAdminEfectivo) {
                Spacer(Modifier.height(10.dp))
                Surface(onClick = { navController.navigate("HISTORIAL_RUTA") }, color = surfaceColor, shadowElevation = 4.dp, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(tamBotonLateral)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.History, null, modifier = Modifier.size(tamIconoLateral), tint = DelisaRed) }
                }
            }
        }

        // 🏷️ TOGGLE CLIENTES (TOP CENTER) - REDISEÑO PROFESIONAL DELISA
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = paddingBotones),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val activo = uiState.markersVisible
            val bgColor by animateColorAsState(
                targetValue = if (activo) {
                    if (isDark) DelisaRed.copy(alpha = 0.85f) else DelisaRed
                } else {
                    if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                },
                animationSpec = tween(400), label = "toggleBg"
            )
            val contentColor by animateColorAsState(
                targetValue = if (activo) Color.White else if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                animationSpec = tween(400), label = "toggleContent"
            )

            Surface(
                modifier = Modifier.shadow(if (activo) 8.dp else 4.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                color = bgColor,
                contentColor = contentColor,
                border = if (!activo) BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)) else null
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // LADO IZQUIERDO: TOGGLE VISIBILIDAD
                    Row(
                        modifier = Modifier
                            .clickable { viewModel.toggleMarkersVisible() }
                            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                color = contentColor,
                                modifier = Modifier.size(if (esPantallaPequena) 14.dp else 16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (activo) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(if (esPantallaPequena) 16.dp else 18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (activo) "CLIENTES: ${uiState.clientes.size}" else "MOSTRAR CLIENTES",
                            fontWeight = FontWeight.Black,
                            fontSize = if (esPantallaPequena) 10.sp else 12.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // SEPARADOR SUTIL (SOLO ADMIN)
                    if (esAdminEfectivo) {
                        Box(Modifier.width(1.dp).height(24.dp).background(contentColor.copy(alpha = 0.2f)))
                        
                        // LADO DERECHO: LUPITA FILTRO
                        Box(
                            modifier = Modifier
                                .clickable { showRouteMenu = !showRouteMenu }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (showRouteMenu) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Filtrar",
                                modifier = Modifier.size(if (esPantallaPequena) 16.dp else 18.dp)
                            )
                        }
                    }
                }
            }

            // 🔍 FILTRO DE RUTAS VERTICAL - INTEGRACIÓN TOTAL
            AnimatedVisibility(
                visible = showRouteMenu && esAdminEfectivo && uiState.listaRutas.size > 1,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Surface(
                    color = if (isDark) DelisaRed.copy(alpha = 0.75f) else DelisaRed.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .offset(y = (-10).dp)
                        .width(IntrinsicSize.Max)
                ) {
                    Column(
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        uiState.listaRutas.forEachIndexed { index, ruta ->
                            val selected = uiState.filtroRuta == ruta
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        viewModel.actualizarFiltroRuta(ruta)
                                        showRouteMenu = false // Cerrar al seleccionar
                                    }
                                    .background(if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                                    .padding(horizontal = 24.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ruta.replace("Todas las Rutas", "TODOS").replace(" Delisa", "").uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            if (index < uiState.listaRutas.size - 1) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 📍 BOTÓN MI UBICACIÓN
        FloatingActionButton(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let { scope.launch { viewModel.cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 18f)) } }
                    }
                }
            },
            containerColor = DelisaRed, contentColor = Color.White, shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = paddingFab.coerceAtLeast(0.dp))
                .size(if (esPantallaPequena) 44.dp else 50.dp)
                .then(if (isDark) Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape) else Modifier)
        ) { Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(if (esPantallaPequena) 18.dp else 22.dp)) }

        // 📇 TARJETAS INFERIORES
        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 16.dp).zIndex(10f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (uiState.modoHojaRuta && uiState.itinerarioActivo != null) {
                Card(modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp)), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.5.dp, WarningOrange.copy(alpha = 0.4f))) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(WarningOrange.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = WarningOrange, modifier = Modifier.size(20.dp)) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                uiState.itinerarioActivo?.let { iti ->
                                    Text(text = iti.rutaId.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.width(8.dp)); Surface(color = WarningOrange, shape = RoundedCornerShape(6.dp)) { Text(text = "${uiState.clientesRuta.size} PUNTOS", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                                }
                            }
                            uiState.itinerarioActivo?.let { iti ->
                                val diaC = when(iti.diaSemana) { "Lun" -> "Lunes"; "Mar" -> "Martes"; "Mie" -> "Miércoles"; "Jue" -> "Jueves"; "Vie" -> "Viernes"; "Sab" -> "Sábado"; else -> iti.diaSemana }
                                Text(text = "$diaC - Semana ${iti.frecuencia}", color = WarningOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        VerticalDivider(Modifier.height(30.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        IconButton(onClick = { viewModel.limpiarHojaRuta() }, modifier = Modifier.padding(start = 8.dp).size(32.dp)) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            if (uiState.selectedCliente != null) { ClienteCard(uiState.selectedCliente!!, navController, isAdminOverride) }
            else if (uiState.vendedorSeleccionadoRuta != null) { uiState.vendedores.find { it.ruta == uiState.vendedorSeleccionadoRuta }?.let { VendedorInfoCard(it) } }
        }

        // DIALOGOS
        if (showRouteSelector) {
            var selRuta by remember { mutableStateOf(uiState.filtroRuta.ifEmpty { uiState.listaRutas.getOrNull(1) ?: "" }) }
            var selDia by remember { mutableStateOf("Lun") }
            var selCiclo by remember { mutableStateOf("Par") }
            AlertDialog(
                onDismissRequest = { showRouteSelector = false },
                title = { 
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(48.dp).background(DelisaRed.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = DelisaRed, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Hoja de Ruta", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                        val rutasF = remember(uiState.listaRutas) { uiState.listaRutas.filter { it != "Todas las Rutas" } }
                        if (rutasF.isNotEmpty()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text("SELECCIONA RUTA", fontSize = 11.sp, fontWeight = FontWeight.Black, color = DelisaRed, letterSpacing = 1.2.sp)
                                Spacer(Modifier.height(12.dp))
                                
                                val selectedIndex = rutasF.indexOf(selRuta).coerceAtLeast(0)
                                TabRow(
                                    selectedTabIndex = selectedIndex,
                                    containerColor = Color.Transparent,
                                    divider = {},
                                    indicator = { tabPositions ->
                                        if (selectedIndex < tabPositions.size) {
                                            TabRowDefaults.SecondaryIndicator(
                                                Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                                color = DelisaRed,
                                                height = 3.dp
                                            )
                                        }
                                    }
                                ) {
                                    rutasF.forEach { r ->
                                        Tab(
                                            selected = selRuta == r,
                                            onClick = { selRuta = r },
                                            text = { 
                                                Text(
                                                    text = r.replace(" Delisa", "").uppercase(), 
                                                    fontSize = 13.sp, 
                                                    fontWeight = if(selRuta == r) FontWeight.Black else FontWeight.Bold,
                                                    maxLines = 1
                                                ) 
                                            },
                                            selectedContentColor = DelisaRed,
                                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("DÍA DE VISITA", fontSize = 10.sp, fontWeight = FontWeight.Black, color = DelisaRed, letterSpacing = 1.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                listOf("Lun", "Mar", "Mie", "Jue", "Vie", "Sab").forEach { d ->
                                    val isSelected = selDia == d
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) DelisaRed else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                                            .clickable { selDia = d },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = d,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("CICLO / SEMANA", fontSize = 10.sp, fontWeight = FontWeight.Black, color = DelisaRed, letterSpacing = 1.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                listOf("Par", "Non").forEach { c ->
                                    val isSelected = selCiclo == c
                                    Surface(
                                        onClick = { selCiclo = c },
                                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(36.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) DelisaRed else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                                        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("Semana $c", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.cargarHojaRuta(selRuta, selDia, selCiclo); showRouteSelector = false },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("VISUALIZAR RUTA", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showRouteSelector = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CERRAR VENTANA", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surface
            )
        }

        if (showLayerSheet) {
            ModalBottomSheet(onDismissRequest = { showLayerSheet = false }, sheetState = sheetState, containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White, dragHandle = { BottomSheetDefaults.DragHandle(color = DelisaRed.copy(0.4f)) }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
                    Text("TIPO DE MAPA", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        MapTypeOption("Estándar", Icons.Default.Map, uiState.mapType == MapType.NORMAL && uiState.mapStyleJson == null) { viewModel.setMapType(MapType.NORMAL); showLayerSheet = false }
                        MapTypeOption("Satélite", Icons.Default.Satellite, uiState.mapType == MapType.SATELLITE) { viewModel.setMapType(MapType.SATELLITE); showLayerSheet = false }
                        MapTypeOption("Noche", Icons.Default.DarkMode, uiState.mapStyleJson != null) { viewModel.setMapType(MapType.NORMAL, darkMapStyleJson); showLayerSheet = false }
                    }
                }
            }
        }
    }
}

@Composable
fun ClienteCard(cliente: Cliente, navController: NavController, isAdminOverride: Boolean? = null) {
    val isDark = isSystemInDarkTheme()
    Card(
        modifier = Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(24.dp)).clickable { navController.navigate("detalle_cliente/${cliente.id}?origen=Mapa") },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) else null
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = cliente.fotoUrl, contentDescription = null, modifier = Modifier.size(70.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(cliente.nombreNegocio, fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(cliente.nombreDueno ?: "Propietario no registrado", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Call, null, tint = DelisaRed, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(cliente.telefono ?: "Sin número", color = DelisaRed, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }
            Box(modifier = Modifier.size(54.dp).graphicsLayer { }.clip(CircleShape).background(DelisaRed.copy(alpha = 0.12f)).clickable { navController.navigate("pantalla_ventas/${cliente.id}?origen=Mapa&isAdminOverride=${isAdminOverride ?: true}") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.ShoppingCart, "Venta", tint = DelisaRed, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
fun VendedorInfoCard(vendedor: VendedorUbicacion) {
    val isDark = isSystemInDarkTheme()
    Card(
        modifier = Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) else null
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(vendedor.ruta, fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                val moving = vendedor.speed > 0.5
                Surface(shape = RoundedCornerShape(10.dp), color = (if (moving) DelisaGreen else WarningOrange).copy(alpha = 0.15f)) {
                    Text(text = if (moving) "EN MOVIMIENTO" else "UNIDAD DETENIDA", color = if (moving) DelisaGreenDark else WarningOrange, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
            Text("Último reporte: ${tiempoTranscurrido(vendedor.timestamp)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 1.dp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VendorGpsIndicator(vendedor.accuracy); VendorSpeedIndicator2(vendedor.speed); VendorBatteryIndicator(vendedor.battery)
            }
        }
    }
}

@Composable
fun MapTypeOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, modifier = Modifier.size(64.dp), shape = RoundedCornerShape(16.dp), color = if (selected) DelisaRed.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), border = if (selected) BorderStroke(2.dp, DelisaRed) else null) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (selected) DelisaRed else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) DelisaRed else MaterialTheme.colorScheme.onSurface)
    }
}

fun bitmapDescriptorFromVectorLocallyResized(context: Context, vectorResId: Int, width: Int, height: Int): BitmapDescriptor {
    val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return BitmapDescriptorFactory.defaultMarker()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    vectorDrawable.setBounds(0, 0, width, height)
    vectorDrawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
