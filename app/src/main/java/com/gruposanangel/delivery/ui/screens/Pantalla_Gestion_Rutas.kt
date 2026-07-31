package com.gruposanangel.delivery.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "MAPA_DEBUG"

@Composable
fun Pantalla_Gestion_Rutas(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val repoRuta = RepositoryRuta(db.rutaDao(), db.clienteDao())
    val viewModel: GestionRutasViewModel = viewModel(factory = GestionRutasViewModelFactory(repoRuta, RepositoryCliente(db.clienteDao())))
    val uiState by viewModel.uiState.collectAsState()

    val isDark = ThemeConfig.isActuallyDark

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    DeliveryTheme(darkTheme = isDark) {
        PantallaGestionRutasContent(
            uiState = uiState,
            onBack = { navController.popBackStack() },
            onRutaBaseSelect = { viewModel.setRutaBase(it) },
            onDaySelect = { viewModel.setDay(it) },
            onWeekSelect = { viewModel.setWeek(it) },
            onToggleCliente = { viewModel.toggleSeleccionCliente(it) },
            onSave = { viewModel.guardarConfiguracionRuta() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaGestionRutasContent(
    uiState: GestionRutasUiState,
    onBack: () -> Unit,
    onRutaBaseSelect: (String) -> Unit,
    onDaySelect: (String) -> Unit,
    onWeekSelect: (String) -> Unit,
    onToggleCliente: (String) -> Unit,
    onSave: () -> Unit
) {
    var vistaMapa by remember { mutableStateOf(false) }
    var expandRuta by remember { mutableStateOf(false) }
    var expandDia by remember { mutableStateOf(false) }
    var expandSemana by remember { mutableStateOf(false) }

    val estadoCamaraMapa = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(19.4768, -96.5897), 12f)
    }

    val clientesConCoordenadas by remember(uiState.clientesDisponibles) {
        derivedStateOf {
            uiState.clientesDisponibles.filter { it.ubicacionLat != 0.0 && it.ubicacionLon != 0.0 }
        }
    }

    LaunchedEffect(clientesConCoordenadas, vistaMapa) {
        if (vistaMapa && clientesConCoordenadas.isNotEmpty()) {
            try {
                val bounds = withContext(Dispatchers.Default) {
                    val builder = LatLngBounds.Builder()
                    clientesConCoordenadas.forEach { builder.include(LatLng(it.ubicacionLat, it.ubicacionLon)) }
                    builder.build()
                }
                kotlinx.coroutines.delay(400)
                estadoCamaraMapa.animate(CameraUpdateFactory.newLatLngBounds(bounds, 180), 1000)
            } catch (e: Exception) {
                Log.e(TAG, "Error encuadre al abrir mapa", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurar Rutas", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    FilterChip(
                        selected = vistaMapa,
                        onClick = { vistaMapa = !vistaMapa },
                        label = { Text(if (vistaMapa) "Ver Lista" else "Ver Mapa", fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(if (vistaMapa) Icons.Default.ListAlt else Icons.Default.Map, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = DelisaRed,
                            selectedContainerColor = DelisaRed.copy(alpha = 0.1f),
                            selectedLabelColor = DelisaRed,
                            selectedLeadingIconColor = DelisaRed
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true, 
                            selected = vistaMapa, 
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), 
                            selectedBorderColor = DelisaRed.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(99.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSave,
                containerColor = DelisaRed,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(20.dp)) },
                text = { Text("Guardar Cambios", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                shape = RoundedCornerShape(18.dp),
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            Box(Modifier.fillMaxSize()) {

                VistaMapaRutas(
                    clientesValidos = clientesConCoordenadas,
                    selectedIds = uiState.selectedClientIds,
                    cameraState = estadoCamaraMapa,
                    onToggle = onToggleCliente,
                    isMapVisible = vistaMapa, 
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 84.dp)
                )

                if (!vistaMapa) {
                    VistaListaRutas(
                        clientes = uiState.clientesDisponibles,
                        selectedIds = uiState.selectedClientIds,
                        onToggle = onToggleCliente,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(top = 84.dp)
                    )
                }

                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(0.6f)), Alignment.Center) { CircularProgressIndicator(color = DelisaRed, strokeWidth = 3.dp) }
                }
            }

            // Selectores superiores flotantes
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .zIndex(10f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val listaRutas = if (uiState.rutas.isEmpty()) listOf("Ruta 1") else uiState.rutas.map { it.id }.distinct()
                Box(Modifier.weight(1.1f)) {
                    ExposedDropdownMenuBox(expanded = expandRuta, onExpandedChange = { expandRuta = !expandRuta }) {
                        MenuSelectorFlotante(label = "Ruta", valor = uiState.selectedRutaBase ?: "Seleccionar", activo = expandRuta, modifier = Modifier.menuAnchor())
                        ExposedDropdownMenu(expanded = expandRuta, onDismissRequest = { expandRuta = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            listaRutas.forEach { id ->
                                DropdownMenuItem(text = { Text(id, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) }, onClick = { onRutaBaseSelect(id); expandRuta = false })
                            }
                        }
                    }
                }

                Box(Modifier.weight(1.2f)) {
                    ExposedDropdownMenuBox(expanded = expandDia, onExpandedChange = { expandDia = !expandDia }) {
                        val diaCompletoVisual = when(uiState.selectedDay) {
                            "Lun" -> "Lunes"
                            "Mar" -> "Martes"
                            "Mie" -> "Miércoles"
                            "Jue" -> "Jueves"
                            "Vie" -> "Viernes"
                            "Sab" -> "Sábado"
                            else -> uiState.selectedDay ?: "Elegir"
                        }
                        MenuSelectorFlotante(label = "Día", valor = diaCompletoVisual, activo = expandDia, modifier = Modifier.menuAnchor())
                        ExposedDropdownMenu(expanded = expandDia, onDismissRequest = { expandDia = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            mapOf("Lun" to "Lunes", "Mar" to "Martes", "Mie" to "Miércoles", "Jue" to "Jueves", "Vie" to "Viernes", "Sab" to "Sábado").forEach { (clave, valor) ->
                                DropdownMenuItem(text = { Text(valor, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) }, onClick = { onDaySelect(clave); expandDia = false })
                            }
                        }
                    }
                }

                Box(Modifier.weight(0.9f)) {
                    ExposedDropdownMenuBox(expanded = expandSemana, onExpandedChange = { expandSemana = !expandSemana }) {
                        MenuSelectorFlotante(label = "Ciclo", valor = if (uiState.selectedWeek != null) "Sem. ${uiState.selectedWeek}" else "Todos", activo = expandSemana, modifier = Modifier.menuAnchor())
                        ExposedDropdownMenu(expanded = expandSemana, onDismissRequest = { expandSemana = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            listOf("Par", "Non").forEach { w ->
                                DropdownMenuItem(text = { Text("Semana $w", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) }, onClick = { onWeekSelect(w); expandSemana = false })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuSelectorFlotante(label: String, valor: String, activo: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().shadow(if (activo) 6.dp else 2.dp, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (activo) DelisaRed.copy(0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Text(valor, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, maxLines = 1)
            }
            Icon(Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VistaListaRutas(clientes: List<ClienteEntity>, selectedIds: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    // 🚀 Agrupamos los seleccionados arriba de la lista
    val clientesOrdenados = remember(clientes, selectedIds) {
        clientes.sortedByDescending { selectedIds.contains(it.id) }
    }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items = clientesOrdenados, key = { it.id }) { c ->
            val sel = selectedIds.contains(c.id)
            
            // Animación de escala sutil al aparecer/cambiar posición
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + expandVertically(),
                modifier = Modifier.animateItemPlacement()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(c.id) }.shadow(if (sel) 4.dp else 1.dp, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        width = if (sel) 2.dp else 1.dp,
                        color = if (sel) DelisaRed.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AsyncImage(model = c.fotografiaUrl, contentDescription = null, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
                            if (sel) {
                                Box(modifier = Modifier.size(18.dp).background(DelisaRed, CircleShape).padding(2.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.nombreNegocio, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(2.dp))
                            Text(c.nombreDueno, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Surface(shape = RoundedCornerShape(6.dp), color = if (sel) DelisaRed.copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant) {
                                Text(text = if (sel) "INCLUIDO EN RUTA" else "DISPONIBLE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (sel) DelisaRed else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                            }
                        }
                        IconButton(onClick = { onToggle(c.id) }, colors = IconButtonDefaults.iconButtonColors(containerColor = if (sel) DelisaRed else MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.size(36.dp)) {
                            Icon(imageVector = if (sel) Icons.Default.RemoveCircleOutline else Icons.Default.AddCircleOutline, contentDescription = null, tint = if (sel) Color.White else DelisaRed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VistaMapaRutas(
    clientesValidos: List<ClienteEntity>,
    selectedIds: Set<String>,
    cameraState: CameraPositionState,
    onToggle: (String) -> Unit,
    isMapVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selMap by remember { mutableStateOf<ClienteEntity?>(null) }

    var iconRojo by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var iconVerde by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var iconAmarillo by remember { mutableStateOf<BitmapDescriptor?>(null) }

    val alfaMarcadores = remember { Animatable(0f) }
    var yaAnimado by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        try {
            withContext(Dispatchers.IO) {
                val rojo = bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadorrojo, 100, 160)
                val verde = bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadorverde, 100, 160)
                val amarillo = bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadoramarillo, 100, 160)

                withContext(Dispatchers.Main) {
                    iconRojo = rojo
                    iconVerde = verde
                    iconAmarillo = amarillo
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error precargando marcadores", e)
        }
    }

    LaunchedEffect(isMapVisible) {
        if (isMapVisible && !yaAnimado) {
            kotlinx.coroutines.delay(250)
            alfaMarcadores.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 700)
            )
            yaAnimado = true
        } else if (yaAnimado) {
            alfaMarcadores.snapTo(1f)
        }
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            onMapClick = { selMap = null },
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) {
            clientesValidos.forEach { c ->
                val isTouched = c == selMap
                val isSelected = selectedIds.contains(c.id)


                // 1. Verde si se está tocando (foco)
                // 2. Amarillo si ya está agregado a la ruta
                // 3. Rojo por defecto
                val markerIcon = when {
                    isTouched -> iconVerde
                    isSelected -> iconAmarillo
                    else -> iconRojo
                }

                key(c.id) {
                    Marker(
                        state = rememberMarkerState(position = LatLng(c.ubicacionLat, c.ubicacionLon)),
                        title = c.nombreNegocio,
                        icon = markerIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                        alpha = alfaMarcadores.value,
                        onClick = {
                            selMap = c
                            true
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selMap != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp, start = 16.dp, end = 16.dp).zIndex(5f)
        ) {
            selMap?.let { c ->
                Card(Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = c.fotografiaUrl, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.nombreNegocio, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(c.nombreDueno, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            val isSelected = selectedIds.contains(c.id)
                            Button(
                                onClick = { onToggle(c.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) DelisaRed.copy(alpha = 0.6f) else DelisaRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(top = 8.dp).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                            ) {
                                Text(if (isSelected) "QUITAR DE RUTA" else "AGREGAR A HOJA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = { selMap = null }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(30.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Gestión Rutas - Lista")
@Composable
fun GestionRutasPreview() {
    val clientes = listOf(ClienteEntity("1", "Abarrotes Don Pepe", "Pepe", "", "", "", 19.47, -96.58, null, true, "", 0L, true))
    DeliveryTheme { PantallaGestionRutasContent(GestionRutasUiState(clientesDisponibles = clientes, selectedClientIds = setOf("1"), selectedDay = "Lun", selectedWeek = "Par"), {}, {}, {}, {}, {}, {}) }
}