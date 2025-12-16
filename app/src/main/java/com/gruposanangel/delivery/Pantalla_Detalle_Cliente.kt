@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.ClienteEntity
import com.gruposanangel.delivery.data.RepositoryCliente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Store
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*




@Composable
fun DetalleClienteScreen(
    clienteId: String,
    navController: NavController?,
    repository: RepositoryCliente?
) {

    val snackbarHostState = remember { SnackbarHostState() }

    val isPreview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()
    var showMapFull by remember { mutableStateOf(false) }
    var editandoUbicacion by remember { mutableStateOf(false) }



    var cliente by remember { mutableStateOf<ClienteEntity?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var showImageFull by remember { mutableStateOf(false) }



    LaunchedEffect(clienteId) {
        cargando = true
        cliente = if (!isPreview && repository != null) {
            withContext(Dispatchers.IO) {
                repository.obtenerClientesLocalPorId(clienteId)
            }
        } else {
            ClienteEntity(
                id = "1",
                nombreNegocio = "Tienda Don Pepe",
                nombreDueno = "José Pérez",
                telefono = "2281234567",
                correo = "donpepe@mail.com",
                tipoExhibidor = "Exhibidor Premium",
                ubicacionLat = 0.0,
                ubicacionLon = 0.0,
                fotografiaUrl = "",
                activo = true,
                medio = "medio",
                fechaDeCreacion = System.currentTimeMillis(),
                syncStatus = true
            )
        }
        cargando = false
    }

    val c = cliente ?: return

    val imageModel = remember(c.fotografiaUrl) {
        val path = c.fotografiaUrl ?: ""
        if (path.isNotBlank()) {
            val f = File(path)
            if (f.exists()) Uri.fromFile(f) else path
        } else null
    }

    // ===============================
    // 📱 FULLSCREEN IMAGE
    // ===============================
    // ===============================
// 📱 FULLSCREEN IMAGE (BLUR STYLE)
// ===============================
    if (showImageFull) {
        Dialog(
            onDismissRequest = { showImageFull = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {

                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(40.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                )

                // 🔍 Estados de zoom
                var scale by remember { mutableStateOf(1f) }
                var offsetX by remember { mutableStateOf(0f) }
                var offsetY by remember { mutableStateOf(0f) }

                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 4f) // 👈 zoom min/max
                    offsetX += panChange.x
                    offsetY += panChange.y
                }

                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 12.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(22.dp))
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                        .transformable(state = transformState)
                )



                IconButton(
                    onClick = { showImageFull = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(20.dp)
                        .size(44.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(50)
                        )
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
        }

    }

    if (showMapFull) {
        Dialog(
            onDismissRequest = {
                showMapFull = false
                editandoUbicacion = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {

            val posicionInicial = LatLng(c.ubicacionLat, c.ubicacionLon)

            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(posicionInicial, 17f)
            }

            Box(modifier = Modifier.fillMaxSize()) {

                // ===============================
                // 🧊 MAPA BORROSO DE FONDO
                // ===============================
                GoogleMap(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(30.dp),
                    cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(posicionInicial, 16f)
                    },
                    uiSettings = MapUiSettings(
                        scrollGesturesEnabled = false,
                        zoomGesturesEnabled = false
                    )
                )

                // 🕶️ CAPA OSCURA
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.30f))
                )

                // ===============================
                // 🃏 CARD CON MAPA EDITABLE
                // ===============================
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f)
                        .align(Alignment.Center)
                        .padding(16.dp),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(14.dp)
                ) {

                    Box(modifier = Modifier.fillMaxSize()) {


                        val context = LocalContext.current
                        var mapReady by remember { mutableStateOf(false) }

                        val iconCliente = remember(mapReady, c.medio) {
                            if (!mapReady) return@remember null

                            when (c.medio.lowercase()) {
                                "alto" -> bitmapDescriptorFromVector(context, R.drawable.marcadorverde)
                                "medio" -> bitmapDescriptorFromVector(context, R.drawable.marcadorrojo)
                                "bajo" -> bitmapDescriptorFromVector(context, R.drawable.marcadoramarillo)
                                else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                            }
                        }


                        // 🗺️ MAPA EDITABLE

                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraPositionState,
                            onMapLoaded = { mapReady = true },
                            uiSettings = MapUiSettings(
                                zoomControlsEnabled = true,
                                scrollGesturesEnabled = true,
                                zoomGesturesEnabled = true
                            )
                        ) {
                            if (!editandoUbicacion && mapReady && iconCliente != null) {
                                Marker(
                                    state = MarkerState(position = posicionInicial),
                                    title = "Ubicación del cliente",
                                    icon = iconCliente
                                )
                            }
                        }


                        // 📌 PIN FIJO AL CENTRO (modo edición)
                        if (editandoUbicacion) {
                            Icon(
                                painter = painterResource(R.drawable.marcadorrojo),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier
                                    .size(48.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                }

                // ===============================
                // 🔘 BOTÓN PRINCIPAL
                // ===============================
                Button(
                    onClick = {
                        if (editandoUbicacion) {
                            val nuevaPosicion = cameraPositionState.position.target

                            scope.launch(Dispatchers.IO) {
                                repository?.actualizarUbicacionClienteLocal(
                                    clienteId = c.id,
                                    lat = nuevaPosicion.latitude,
                                    lon = nuevaPosicion.longitude
                                )
                                repository?.sincronizarConFirebase()

                                // 🔄 Recargar el cliente actualizado
                                val clienteActualizado = repository?.obtenerClientesLocalPorId(c.id)
                                withContext(Dispatchers.Main) {
                                    cliente = clienteActualizado
                                    editandoUbicacion = false
                                    showMapFull = false

                                    // Mostrar snackbar
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Ubicación actualizada",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            }
                        } else {
                            editandoUbicacion = true
                        }
                    },


                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .fillMaxWidth(0.85f),
                    shape = RoundedCornerShape(12.dp), // 👈 menos redondeado
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF0000),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (editandoUbicacion)
                            "Guardar ubicación"
                        else
                            "Modificar ubicación",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }


                // ===============================
                // ❌ BOTÓN CERRAR
                // ===============================
                IconButton(
                    onClick = {
                        showMapFull = false
                        editandoUbicacion = false
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(28.dp)
                        .size(44.dp)
                        .background(
                            Color.Black.copy(alpha = 0.45f),
                            RoundedCornerShape(50)
                        )
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
        }
    }









    Scaffold(

        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->

        if (cargando) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val heroHeight = (screenHeight * 0.45f).coerceIn(260.dp, 400.dp)
        val mapHeight = (screenHeight * 0.28f).coerceIn(180.dp, 240.dp)

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            // ===============================
            // 🔥 HERO IMAGE
            // ===============================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .clickable { showImageFull = true }
                ) {

                    AsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        placeholder = painterResource(R.drawable.repartidor),
                        error = painterResource(R.drawable.repartidor),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    IconButton(
                        onClick = {
                            navController?.navigate("delivery?screen=Clientes") {
                                popUpTo("delivery?screen=Clientes") { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(20.dp)
                            .size(42.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(50)
                            )
                    ) {
                        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp)
                    ) {
                        Text(
                            c.nombreNegocio,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Surface(
                            color = if (c.activo) Color(0xFF2E7D32) else Color(0xFFC62828),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                if (c.activo) "Activo" else "Inactivo",
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(6.dp)) }

            // ===============================
            // 📋 INFO CLIENTE
            // ===============================
            item {
                val rojoIconos = Color(0xFFFF0000)

                Column(Modifier.padding(horizontal = 16.dp)) {

                    InfoItem(
                        icon = { Icon(Icons.Default.Edit, null, tint = rojoIconos) },
                        label = "Dueño",
                        value = c.nombreDueno
                    )

                    InfoItem(
                        icon = { Icon(Icons.Default.Call, null, tint = rojoIconos) },
                        label = "Teléfono",
                        value = c.telefono
                    )

                    InfoItem(
                        icon = { Icon(Icons.Default.Email, null, tint = rojoIconos) },
                        label = "Correo",
                        value = c.correo
                    )

                    InfoItem(
                        icon = { Icon(Icons.Default.Store, null, tint = rojoIconos) },
                        label = "Exhibidor",
                        value = c.tipoExhibidor
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            // ===============================
            // 🗺️ MAPA
            // ===============================
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(mapHeight)
                    ) {
                        MapaClienteDetalle(
                            lat = c.ubicacionLat,
                            lon = c.ubicacionLon,
                            valor = c.medio
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showMapFull = true }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }



}





@Composable
fun MapaClienteDetalle(
    lat: Double,
    lon: Double,
    valor: String
) {
    val context = LocalContext.current
    val posicionCliente = LatLng(lat, lon)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(posicionCliente, 16f)
    }

    LaunchedEffect(lat, lon) {
        val zoom = cameraPositionState.position.zoom.coerceIn(15f, 17f)
        cameraPositionState.animate(
            update = CameraUpdateFactory.newCameraPosition(
                CameraPosition(LatLng(lat, lon), zoom, cameraPositionState.position.tilt, cameraPositionState.position.bearing)
            )
        )
    }




    // 🔥 Esperar a que el mapa esté listo
    var mapReady by remember { mutableStateOf(false) }

    // 🔥 Selección del icono EXACTO que ya usas
    val iconCliente = remember(mapReady, valor) {
        if (!mapReady) return@remember null

        when (valor.lowercase()) {
            "alto" -> bitmapDescriptorFromVector(context, R.drawable.marcadorverde)
            "medio" -> bitmapDescriptorFromVector(context, R.drawable.marcadorrojo)
            "bajo" -> bitmapDescriptorFromVector(context, R.drawable.marcadoramarillo)
            else -> BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapLoaded = { mapReady = true },
            properties = MapProperties(
                isMyLocationEnabled = false
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                scrollGesturesEnabled = true,
                zoomGesturesEnabled = true
            )
        ) {
            if (mapReady && iconCliente != null) {
                Marker(
                    state = MarkerState(position = posicionCliente),
                    title = "Ubicación del cliente",
                    icon = iconCliente
                )
            }
        }
    }
}



@Composable
private fun InfoItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}




@Preview(showBackground = true)
@Composable
fun DetalleClientePreview() {
    DetalleClienteScreen(
        clienteId = "1",
        navController = rememberNavController(),
        repository = null
    )
}
