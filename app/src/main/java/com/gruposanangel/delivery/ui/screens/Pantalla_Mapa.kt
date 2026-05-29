@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.gruposanangel.delivery.utilidades.VendorBatteryIndicator
import com.gruposanangel.delivery.utilidades.VendorGpsIndicator
import com.gruposanangel.delivery.utilidades.VendorSpeedIndicator2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

@SuppressLint("MissingPermission")
@Composable
fun MapaScreen(
    navController: NavController,
    viewModel: MapaViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var mapIsReady by remember { mutableStateOf(false) }

    val vendedorStates: SnapshotStateMap<String, AnimatableMarker> = remember { mutableStateMapOf() }
    val vendedorMarkerStates: SnapshotStateMap<String, MarkerState> = remember { mutableStateMapOf() }

    // Icono de vendedor (PNG) - Mantener tamaño original/previo
    val vendedorIcon = remember(mapIsReady) {
        if (mapIsReady) bitmapDescriptorFromPng(context, R.drawable.marcador_vendedor, 120, 150) else null
    }

    // ✅ CORRECCIÓN TAMAÑO ICONOS CLIENTES (100x160)
    // Usamos una función local especial para no romper tus otras pantallas
    val iconAlto = remember(mapIsReady) {
        if (mapIsReady) bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadorverde, 100, 160) else null
    }
    val iconMedio = remember(mapIsReady) {
        if (mapIsReady) bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadorrojo, 100, 160) else null
    }
    val iconBajo = remember(mapIsReady) {
        if (mapIsReady) bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadoramarillo, 100, 160) else null
    }

    // 🟢 ICONO PARA CLIENTE SELECCIONADO (Usando el verde personalizado)
    val iconSeleccionado = remember(mapIsReady) {
        if (mapIsReady) bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadorverde, 100, 160) else null
    }

    val mostrarTarjetaInferior = uiState.selectedCliente != null || uiState.vendedorSeleccionadoRuta != null

    // ✅ ESCUCHA DE EVENTOS DE CÁMARA AUTOMÁTICOS (CENTRO AL ACTIVAR CLIENTES)
    LaunchedEffect(Unit) {
        viewModel.cameraEvents.collect { event ->
            if (event is CameraEvent.CenterOnClients && uiState.clientes.isNotEmpty()) {
                try {
                    val builder = LatLngBounds.Builder()
                    uiState.clientes.forEach { builder.include(LatLng(it.ubicacionLat, it.ubicacionLng)) }
                    viewModel.cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), 200), 1200)
                } catch (e: Exception) { }
            }
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
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
                if (uiState.seguirVendedor != null) {
                    viewModel.selectVendedor(uiState.seguirVendedor)
                }
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

                LaunchedEffect(state.animLat.value, state.animLng.value, uiState.seguirVendedor) {
                    if (uiState.seguirVendedor == vendedor.ruta) {
                        val newPos = LatLng(state.animLat.value.toDouble(), state.animLng.value.toDouble())
                        viewModel.cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(newPos, 18f), 600)
                        markerState.showInfoWindow()
                    }
                }

                LaunchedEffect(vendedor.ruta, vendedor.timestamp.seconds) {
                    val animSpec = tween<Float>(800)
                    launch { state.animLat.animateTo(vendedor.lat.toFloat(), animSpec) }
                    launch { state.animLng.animateTo(vendedor.lng.toFloat(), animSpec) }

                    val rot = calcularAngulo(state.animLat.value.toDouble(), state.animLng.value.toDouble(), vendedor.lat, vendedor.lng)
                    state.animRotation.animateTo(rot, tween(500))
                }

                val haloColor = lerp(Color(0xFFB0B0B0).copy(0.35f), Color(0xFFFF4444).copy(0.35f), state.animHaloColorFactor.value)
                val visualRadius = (state.animRadius.value * 5f).coerceIn(30f, 180f)

                Circle(
                    center = markerState.position,
                    radius = visualRadius.toDouble(),
                    strokeColor = haloColor,
                    strokeWidth = 2f,
                    fillColor = haloColor.copy(0.2f)
                )

                Marker(
                    state = markerState,
                    title = vendedor.ruta,
                    icon = vendedorIcon,
                    onClick = {
                        viewModel.selectVendedor(vendedor.ruta)
                        scope.launch { viewModel.cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(markerState.position, 18f)) }
                        true
                    }
                )
            }

            // DIBUJAR CLIENTES
            if (uiState.markersVisible) {
                uiState.clientes.forEach { cliente ->
                    val isSelected = uiState.selectedCliente?.id == cliente.id

                    val icon = if (isSelected) iconSeleccionado else when (cliente.valor.lowercase()) {
                        "alto" -> iconAlto
                        "medio" -> iconMedio
                        "bajo" -> iconBajo
                        else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    }
                    val mState = remember(cliente.id) { MarkerState(LatLng(cliente.ubicacionLat, cliente.ubicacionLng)) }

                    Marker(
                        state = mState,
                        zIndex = if (isSelected) 2f else 1f,
                        title = cliente.nombreNegocio,
                        icon = icon,
                        onClick = {
                            viewModel.selectCliente(cliente)
                            // 🔍 ZOOM SUAVE AL SELECCIONAR CLIENTE
                            scope.launch { 
                                viewModel.cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(mState.position, 16.5f), 
                                    800
                                ) 
                            }
                            true
                        }
                    )
                }
            }
        }

        // CONTROLES DE CAPAS Y RUTAS PREMIUM
        Box(Modifier.fillMaxSize().padding(16.dp)) {

            // 📍 Mi Ubicación Inteligente
            FloatingActionButton(
                onClick = {
                    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            location?.let {
                                scope.launch {
                                    viewModel.cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 18f))
                                }
                            }
                        }
                    }
                },
                containerColor = Color.Red,
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (mostrarTarjetaInferior) 190.dp else 0.dp)
                    .size(50.dp),
                shape = CircleShape
            ) { Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(22.dp)) }

            // 🛣️ Botones de Ruta Laterales (Chips)
            Column(
                Modifier.align(Alignment.TopStart).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                uiState.vendedores.forEach { v ->
                    val activo = uiState.seguirVendedor == v.ruta
                    Card(
                        modifier = Modifier
                            .clickable { viewModel.toggleSeguirVendedor(v.ruta) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (activo) Color.Red else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = if (activo) Color.White else Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = v.ruta.replace(" Delisa", ""),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = if (activo) Color.White else Color.DarkGray
                            )
                        }
                    }
                }
            }

            // 🌍 Capas de Mapa (Esquina Superior Derecha)
            var expanded by remember { mutableStateOf(false) }
            Column(Modifier.align(Alignment.TopEnd).padding(top = 16.dp), horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = { expanded = true },
                    containerColor = Color.White,
                    contentColor = Color.DarkGray,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    elevation = FloatingActionButtonDefaults.elevation(3.dp)
                ) {
                    Icon(Icons.Default.Layers, null, modifier = Modifier.size(20.dp), tint = Color.Red)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Mapa Estándar") }, onClick = { viewModel.setMapType(MapType.NORMAL); expanded = false })
                    DropdownMenuItem(text = { Text("Vista Satélite") }, onClick = { viewModel.setMapType(MapType.SATELLITE); expanded = false })
                    DropdownMenuItem(text = { Text("Modo Oscuro") }, onClick = { viewModel.setMapType(MapType.NORMAL, darkMapStyleJson); expanded = false })
                }
            }

            // 🏷️ Toggle Cobertura de Clientes (Lógica centralizada en ViewModel)
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .clickable { viewModel.toggleMarkersVisible() },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.markersVisible) Color.DarkGray else Color.White
                ),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.Red, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = null,
                            tint = if (uiState.markersVisible) Color.White else Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (uiState.markersVisible) "Ocultar Clientes (${uiState.clientes.size})" else "Mostrar Clientes",
                        color = if (uiState.markersVisible) Color.White else Color.DarkGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 📇 CONTENEDOR DE TARJETAS INFERIORES ELÉGANTES
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .zIndex(10f)
        ) {
            if (uiState.selectedCliente != null) {
                ClienteCard(uiState.selectedCliente!!, navController)
            } else if (uiState.vendedorSeleccionadoRuta != null) {
                val vendedor = uiState.vendedores.find { it.ruta == uiState.vendedorSeleccionadoRuta }
                vendedor?.let { VendedorInfoCard(it) }
            }
        }
    }
}

// =============================================================
// COMPOSABLES SECUNDARIOS
// =============================================================

@Composable
fun ClienteCard(cliente: Cliente, navController: NavController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate("detalle_cliente/${cliente.id}?origen=Mapa") },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = cliente.fotoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(70.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF5F5F5)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(cliente.nombreNegocio, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.DarkGray)
                Text(cliente.nombreDueno ?: "Propietario no registrado", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Call, null, tint = Color.Red, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(cliente.telefono ?: "Sin número", color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }

            // 🛒 BOTÓN DE VENTA RÁPIDA ANIMADO "PREMIUM"
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.85f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
                label = "buttonScale"
            )

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(CircleShape)
                    .background(Color.Red.copy(alpha = 0.1f))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = rememberRipple(bounded = true, color = Color.Red, radius = 27.dp),
                        onClick = { navController.navigate("pantalla_ventas/${cliente.id}?origen=Mapa") }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = "Venta",
                    tint = Color.Red,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun VendedorInfoCard(vendedor: VendedorUbicacion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(10.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(vendedor.ruta, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color.DarkGray)
                val moving = vendedor.speed > 0.5
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = (if (moving) Color(0xFF2E7D32) else Color(0xFFE65100)).copy(0.1f)
                ) {
                    Text(
                        text = if (moving) "EN MOVIMIENTO" else "UNIDAD DETENIDA",
                        color = if (moving) Color(0xFF2E7D32) else Color(0xFFE65100),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Text("Último reporte: ${tiempoTranscurrido(vendedor.timestamp)}", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF5F5F5), thickness = 1.dp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VendorGpsIndicator(accuracy = vendedor.accuracy)
                VendorSpeedIndicator2(speed = vendedor.speed)
                VendorBatteryIndicator(batteryLevel = vendedor.battery)
            }
        }
    }
}

// 🌍 Lógica de carga de vectores redimensionados local (Blindada contra breaking changes globales)
fun bitmapDescriptorFromVectorLocallyResized(context: Context, vectorResId: Int, width: Int, height: Int): BitmapDescriptor {
    val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return BitmapDescriptorFactory.defaultMarker()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    vectorDrawable.setBounds(0, 0, width, height)
    vectorDrawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

