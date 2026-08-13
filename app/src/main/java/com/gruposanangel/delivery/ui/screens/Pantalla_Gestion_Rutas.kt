package com.gruposanangel.delivery.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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
import kotlinx.coroutines.launch
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
            onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
            onSave = { viewModel.guardarConfiguracionRuta() },
            onRefreshResumen = { viewModel.cargarResumenItinerarios() },
            onMantenerClientes = { r, d, w -> viewModel.mantenerClientesCambiarParametros(r, d, w) }
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
    onSearchQueryChanged: (String) -> Unit,
    onSave: () -> Unit,
    onRefreshResumen: () -> Unit = {},
    onMantenerClientes: (String?, String?, String?) -> Unit = { _, _, _ -> }
) {
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var vistaMapa by remember { mutableStateOf(false) }
    var expandRuta by remember { mutableStateOf(false) }
    var expandDia by remember { mutableStateOf(false) }
    var expandSemana by remember { mutableStateOf(false) }
    var showResumen by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // Tipo a cambiar, Valor nuevo

    var textFieldValue by remember { mutableStateOf(TextFieldValue(uiState.searchQuery)) }
    
    val hasSelection = uiState.selectedRutaBase != null && uiState.selectedDay != null && uiState.selectedWeek != null

    // ✨ RELOJ DE ANIMACIÓN MAESTRO (Para optimizar rendimiento)
    val infiniteTransition = rememberInfiniteTransition(label = "masterGlowTransition")
    val masterGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "masterGlowAlpha"
    )

    // ✨ LÓGICA DE FEEDBACK PARA GUARDADO INVÁLIDO
    var lastErrorTime by remember { mutableLongStateOf(0L) }
    
    val handleInvalidSave = {
        val now = System.currentTimeMillis()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // Anti-Spam: Solo mostrar Snackbar si han pasado más de 2 segundos
        if (now - lastErrorTime > 2000) {
            lastErrorTime = now
            scope.launch {
                snackbarHostState.showSnackbar("⚠️ Selecciona Ruta, Día y Ciclo para guardar")
            }
        }
    }

    val estadoCamaraMapa = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(19.4768, -96.5897), 12f)
    }

    val clientesConCoordenadas by remember(uiState.clientesDisponibles) {
        derivedStateOf {
            uiState.clientesDisponibles.filter { it.ubicacionLat != 0.0 && it.ubicacionLon != 0.0 }
        }
    }

    val clientesSeleccionadosConCoordenadas by remember(uiState.clientesDisponibles, uiState.selectedClientIds) {
        derivedStateOf {
            uiState.clientesDisponibles.filter { 
                uiState.selectedClientIds.contains(it.id) && it.ubicacionLat != 0.0 && it.ubicacionLon != 0.0 
            }
        }
    }

    LaunchedEffect(clientesConCoordenadas, clientesSeleccionadosConCoordenadas, vistaMapa, uiState.selectedRutaBase, uiState.selectedDay, uiState.selectedWeek) {
        if (vistaMapa) {
            val listaParaEncuadrar = if (clientesSeleccionadosConCoordenadas.isNotEmpty()) {
                clientesSeleccionadosConCoordenadas
            } else {
                clientesConCoordenadas
            }

            if (listaParaEncuadrar.isNotEmpty()) {
                try {
                    val bounds = withContext(Dispatchers.Default) {
                        val builder = LatLngBounds.Builder()
                        listaParaEncuadrar.forEach { builder.include(LatLng(it.ubicacionLat, it.ubicacionLon)) }
                        builder.build()
                    }
                    kotlinx.coroutines.delay(400)
                    estadoCamaraMapa.animate(CameraUpdateFactory.newLatLngBounds(bounds, 180), 1000)
                } catch (e: Exception) {
                    Log.e(TAG, "Error encuadre al abrir mapa", e)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Column(verticalArrangement = Arrangement.Center) {
                        Text("Gestión Rutas", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
                        if (hasSelection && uiState.selectedClientIds.isNotEmpty()) {
                            Text(
                                text = "${uiState.selectedClientIds.size} CLIENTES SELECCIONADOS",
                                fontSize = 10.sp,
                                color = DelisaRed,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    IconButton(onClick = { onRefreshResumen(); showResumen = true }) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilterChip(
                        selected = vistaMapa,
                        onClick = { vistaMapa = !vistaMapa },
                        label = { Text(if (vistaMapa) "Lista" else "Mapa", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        leadingIcon = { Icon(if (vistaMapa) Icons.AutoMirrored.Filled.ListAlt else Icons.Default.Map, null, modifier = Modifier.size(16.dp)) },
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
            Button(
                onClick = {
                    if (hasSelection) {
                        showConfirmDialog = true
                    } else {
                        handleInvalidSave()
                    }
                },
                enabled = true,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasSelection) DelisaRed else Color.Gray.copy(alpha = 0.6f),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Text(
                    text = if (uiState.esRutaExistente) "GUARDAR CAMBIOS" else "GUARDAR RUTA",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            Box(Modifier.fillMaxSize()) {

                VistaMapaRutas(
                    clientesValidos = clientesConCoordenadas,
                    selectedIds = uiState.selectedClientIds,
                    cameraState = estadoCamaraMapa,
                    onToggle = { onToggleCliente(it) },
                    isMapVisible = vistaMapa, 
                    masterGlowAlpha = masterGlowAlpha,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 145.dp)
                )

                if (!vistaMapa) {
                    VistaListaRutas(
                        clientes = uiState.clientesDisponibles,
                        selectedIds = uiState.selectedClientIds,
                        onToggle = { onToggleCliente(it) },
                        masterGlowAlpha = masterGlowAlpha,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(top = 145.dp)
                    )
                }
                
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(0.6f)), Alignment.Center) { CircularProgressIndicator(color = DelisaRed, strokeWidth = 3.dp) }
                }
            }

            // Selectores superiores flotantes y Buscador
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .zIndex(10f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val listaRutas = if (uiState.rutas.isEmpty()) listOf("Ruta 1") else uiState.rutas.map { it.id }.distinct()
                    
                    // Selector RUTA
                    Box(Modifier.weight(1f)) {
                        MenuSelectorFlotante(
                            label = "Ruta", 
                            valor = uiState.selectedRutaBase ?: "Elegir", 
                            activo = expandRuta, 
                            onClick = { expandRuta = true }
                        )
                        DropdownMenu(
                            expanded = expandRuta, 
                            onDismissRequest = { expandRuta = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface).width(120.dp)
                        ) {
                            listaRutas.forEach { id ->
                                DropdownMenuItem(
                                    text = { Text(id, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) }, 
                                    onClick = { 
                                        if (uiState.selectedClientIds.isNotEmpty() && uiState.selectedRutaBase != id) {
                                            showCopyDialog = "Ruta" to id
                                        } else {
                                            onRutaBaseSelect(id) 
                                        }
                                        expandRuta = false 
                                    }
                                )
                            }
                        }
                    }

                    // Selector DÍA
                    Box(Modifier.weight(1f)) {
                        val diaCompletoVisual = when(uiState.selectedDay) {
                            "Lun" -> "Lunes"
                            "Mar" -> "Martes"
                            "Mie" -> "Miér"
                            "Jue" -> "Jueves"
                            "Vie" -> "Viernes"
                            "Sab" -> "Sábado"
                            else -> uiState.selectedDay ?: "Elegir"
                        }
                        MenuSelectorFlotante(
                            label = "Día", 
                            valor = diaCompletoVisual, 
                            activo = expandDia, 
                            onClick = { expandDia = true }
                        )
                        DropdownMenu(
                            expanded = expandDia, 
                            onDismissRequest = { expandDia = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface).width(120.dp)
                        ) {
                            mapOf("Lun" to "Lunes", "Mar" to "Martes", "Mie" to "Miércoles", "Jue" to "Jueves", "Vie" to "Viernes", "Sab" to "Sábado").forEach { (clave, valor) ->
                                DropdownMenuItem(
                                    text = { Text(valor, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) }, 
                                    onClick = { 
                                        if (uiState.selectedClientIds.isNotEmpty() && uiState.selectedDay != clave) {
                                            showCopyDialog = "Dia" to clave
                                        } else {
                                            onDaySelect(clave)
                                        }
                                        expandDia = false 
                                    }
                                )
                            }
                        }
                    }

                    // Selector CICLO
                    Box(Modifier.weight(0.9f)) {
                        MenuSelectorFlotante(
                            label = "Ciclo", 
                            valor = if (uiState.selectedWeek != null) "S. ${uiState.selectedWeek}" else "Todos", 
                            activo = expandSemana, 
                            onClick = { expandSemana = true }
                        )
                        DropdownMenu(
                            expanded = expandSemana, 
                            onDismissRequest = { expandSemana = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface).width(120.dp)
                        ) {
                            listOf("Par", "Non").forEach { w ->
                                DropdownMenuItem(
                                    text = { Text("Semana $w", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp) }, 
                                    onClick = { 
                                        if (uiState.selectedWeek != null && uiState.selectedWeek != w && uiState.selectedClientIds.isNotEmpty()) {
                                            showCopyDialog = "Semana" to w
                                        } else {
                                            onWeekSelect(w)
                                        }
                                        expandSemana = false 
                                    }
                                )
                            }
                        }
                    }
                }

                // 🔍 BARRA DE BÚSQUEDA
                OutlinedTextField(
                    value = textFieldValue, 
                    onValueChange = {
                        textFieldValue = it
                        onSearchQueryChanged(it.text)
                    },
                    placeholder = { Text("Buscar negocio o dueño...") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = DelisaRed) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DelisaRed, 
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        cursorColor = DelisaRed,
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                icon = { Icon(Icons.Default.Save, null, tint = DelisaRed, modifier = Modifier.size(32.dp)) },
                title = { Text("¿Guardar Itinerario?", fontWeight = FontWeight.Black) },
                text = {
                    Column {
                        Text("Se guardará la configuración para:")
                        Spacer(Modifier.height(8.dp))
                        Text("Ruta: ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text("${uiState.selectedRutaBase}", fontWeight = FontWeight.Black, color = DelisaRed)
                        Text("Día: ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text("${uiState.selectedDay} - Ciclo ${uiState.selectedWeek}", fontWeight = FontWeight.Black, color = DelisaRed)
                        Spacer(Modifier.height(12.dp))
                        Text("Total de clientes seleccionados: ", fontSize = 14.sp)
                        Text("${uiState.selectedClientIds.size}", fontWeight = FontWeight.Black, color = DelisaRed, fontSize = 18.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onSave()
                            showConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
                    ) {
                        Text("CONFIRMAR", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp)
            )
        }

        if (showCopyDialog != null) {
            AlertDialog(
                onDismissRequest = { showCopyDialog = null },
                icon = { Icon(Icons.Default.ContentCopy, null, tint = DelisaRed) },
                title = { Text("¿Cambiar y Copiar Clientes?", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                text = {
                    Text("Tienes clientes seleccionados. ¿Deseas cargarlos en el nuevo destino o prefieres cargar lo que ya esté guardado allí?")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val (tipo, valor) = showCopyDialog!!
                            when(tipo) {
                                "Ruta" -> onMantenerClientes(valor, null, null)
                                "Dia" -> onMantenerClientes(null, valor, null)
                                "Semana" -> onMantenerClientes(null, null, valor)
                            }
                            showCopyDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
                    ) {
                        Text("MANTENER Y COPIAR", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            val (tipo, valor) = showCopyDialog!!
                            when(tipo) {
                                "Ruta" -> onRutaBaseSelect(valor)
                                "Dia" -> onDaySelect(valor)
                                "Semana" -> onWeekSelect(valor)
                            }
                            showCopyDialog = null
                        }
                    ) {
                        Text("CARGAR GUARDADO", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        if (showResumen) {
            ModalBottomSheet(
                onDismissRequest = { showResumen = false },
                dragHandle = { BottomSheetDefaults.DragHandle(color = DelisaRed) }
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
                    Text("Resumen de Rutas Guardadas", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Spacer(Modifier.height(16.dp))
                    if (uiState.itinerariosResumen.isEmpty()) {
                        Text("No hay rutas configuradas aún", color = Color.Gray)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                            items(uiState.itinerariosResumen) { iti ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)),
                                    onClick = {
                                        onRutaBaseSelect(iti.rutaId)
                                        onDaySelect(iti.diaSemana)
                                        onWeekSelect(iti.frecuencia)
                                        showResumen = false
                                    }
                                ) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Route, null, tint = DelisaRed)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(iti.rutaId, fontWeight = FontWeight.Bold)
                                            Text("${iti.diaSemana} - Ciclo ${iti.frecuencia}", fontSize = 12.sp)
                                        }
                                        Badge(containerColor = DelisaRed) {
                                            Text("${iti.clientesOrdenados.size}", color = Color.White, modifier = Modifier.padding(4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuSelectorFlotante(label: String, valor: String, activo: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().shadow(if (activo) 6.dp else 2.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (activo) DelisaRed.copy(0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, fontSize = 9.sp, color = DelisaRed, fontWeight = FontWeight.Black)
                Text(valor, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, maxLines = 1)
            }
            Icon(Icons.Default.ExpandMore, null, tint = DelisaRed, modifier = Modifier.size(12.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VistaListaRutas(
    clientes: List<ClienteEntity>, 
    selectedIds: Set<String>, 
    onToggle: (String) -> Unit, 
    masterGlowAlpha: Float,
    modifier: Modifier = Modifier
) {
    // 🚀 Agrupamos los seleccionados arriba de la lista
    val clientesOrdenados = remember(clientes, selectedIds) {
        clientes.sortedByDescending { selectedIds.contains(it.id) }
    }

    val listState = rememberLazyListState()

    // ✨ AUTO-SCROLL AL PRINCIPIO CUANDO CAMBIE LA LISTA (Por filtros o selección)
    LaunchedEffect(clientesOrdenados.size, selectedIds.size) {
        if (clientesOrdenados.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier, 
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp), 
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    val colorValor = when(c.valorCliente.lowercase()) {
                        "alto" -> DelisaGreen
                        "medio" -> DelisaYellow
                        "bajo" -> DelisaRed
                        else -> Color.Gray
                    }

                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            // Foto con Efecto Neon Glow Moderno (Usa alpha maestro)
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .drawBehind {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(colorValor.copy(alpha = masterGlowAlpha * 0.4f), Color.Transparent),
                                                center = center,
                                                radius = size.width * 0.7f
                                            ),
                                            radius = size.width * 0.7f
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    modifier = Modifier.size(70.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        brush = Brush.linearGradient(
                                            0.0f to colorValor.copy(alpha = 0.8f),
                                            0.5f to colorValor.copy(alpha = 0.2f),
                                            1.0f to colorValor.copy(alpha = 0.8f)
                                        )
                                    ),
                                    tonalElevation = 2.dp
                                ) {
                                    AsyncImage(
                                        model = c.fotografiaUrl, 
                                        contentDescription = null, 
                                        placeholder = painterResource(R.drawable.repartidor), 
                                        error = painterResource(R.drawable.repartidor), 
                                        contentScale = ContentScale.Crop, 
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
                                    )
                                }
                            }
                            if (sel) {
                                Box(modifier = Modifier.size(18.dp).background(DelisaRed, CircleShape).padding(2.dp).zIndex(1f), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
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
    masterGlowAlpha: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selMap by remember { mutableStateOf<ClienteEntity?>(null) }

    var iconRojo by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var iconVerde by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var iconAmarillo by remember { mutableStateOf<BitmapDescriptor?>(null) }
    
    // Iconos para estados especiales
    var iconSeleccionadoNegro by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var iconEnRutaNaranja by remember { mutableStateOf<BitmapDescriptor?>(null) }

    val alfaMarcadores = remember { Animatable(0f) }
    var yaAnimado by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        try {
            withContext(Dispatchers.IO) {
                // Tamaños normales (100x160)
                iconRojo = bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadorrojo, 100, 160)
                iconVerde = bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadorverde, 100, 160)
                iconAmarillo = bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadoramarillo, 100, 160)
                iconEnRutaNaranja = bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadornaranja, 130, 208)
                
                // Tamaño seleccionado (130x208)
                iconSeleccionadoNegro = bitmapDescriptorFromVectorLocallyResized(context, R.drawable.marcadornegro, 130, 208)
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
                val isSelectedOnMap = c.id == (selMap?.id ?: "")
                val isIncludedInRoute = selectedIds.contains(c.id)

                // ✨ Lógica de Marcadores: Seleccionado (Negro), En Ruta (Naranja), Normal (Color)
                val markerIcon = when {
                    isSelectedOnMap -> iconSeleccionadoNegro
                    isIncludedInRoute -> iconEnRutaNaranja
                    else -> when (c.valorCliente.lowercase()) {
                        "alto" -> iconVerde
                        "medio" -> iconAmarillo
                        "bajo" -> iconRojo
                        else -> iconRojo
                    }
                }

                key(c.id) {
                    Marker(
                        state = rememberMarkerState(position = LatLng(c.ubicacionLat, c.ubicacionLon)),
                        zIndex = if (isSelectedOnMap) 2f else 1f,
                        title = c.nombreNegocio,
                        icon = markerIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                        alpha = 1.0f,
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
                val colorValor = when(c.valorCliente.lowercase()) {
                    "alto" -> DelisaGreen
                    "medio" -> DelisaYellow
                    "bajo" -> DelisaRed
                    else -> Color.Gray
                }
                
                Card(Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Foto con Efecto Neon Glow Moderno (Usa alpha maestro)
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(colorValor.copy(alpha = masterGlowAlpha * 0.4f), Color.Transparent),
                                            center = center,
                                            radius = size.width * 0.7f
                                        ),
                                        radius = size.width * 0.7f
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(70.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = 1.5.dp,
                                    brush = Brush.linearGradient(
                                        0.0f to colorValor.copy(alpha = 0.8f),
                                        0.5f to colorValor.copy(alpha = 0.2f),
                                        1.0f to colorValor.copy(alpha = 0.8f)
                                    )
                                ),
                                tonalElevation = 2.dp
                            ) {
                                AsyncImage(
                                    model = c.fotografiaUrl, 
                                    contentDescription = null, 
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)), 
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
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
    DeliveryTheme { PantallaGestionRutasContent(GestionRutasUiState(clientesDisponibles = clientes, selectedClientIds = setOf("1"), selectedDay = "Lun", selectedWeek = "Par"), {}, {}, {}, {}, {}, {}, {}, {}) }
}
