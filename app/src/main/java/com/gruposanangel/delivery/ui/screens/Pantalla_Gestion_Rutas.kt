package com.gruposanangel.delivery.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.ui.theme.DeliveryTheme

@Composable
fun Pantalla_Gestion_Rutas(navController: NavController) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context); val repoRuta = RepositoryRuta(db.rutaDao(), db.clienteDao())
    val viewModel: GestionRutasViewModel = viewModel(factory = GestionRutasViewModelFactory(repoRuta, RepositoryCliente(db.clienteDao())))
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(uiState.successMessage) { uiState.successMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessages() } }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaGestionRutasContent(uiState: GestionRutasUiState, onBack: () -> Unit, onRutaBaseSelect: (String) -> Unit, onDaySelect: (String) -> Unit, onWeekSelect: (String) -> Unit, onToggleCliente: (String) -> Unit, onSave: () -> Unit) {
    var vistaMapa by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("GESTIÓN DE RUTAS", fontWeight = FontWeight.Black, color = Color.DarkGray, style = MaterialTheme.typography.labelLarge) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Red) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onSave, containerColor = Color.Red, contentColor = Color.White, icon = { Icon(Icons.Default.Save, null) }, text = { Text("GUARDAR", fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(16.dp)) },
        containerColor = Color(0xFFF8F9FA)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(if (uiState.rutas.isEmpty()) listOf("Ruta 1") else uiState.rutas.map { it.id }.distinct()) { id -> RutaChip(id, uiState.selectedRutaBase == id) { onRutaBaseSelect(id) } } }
                    LazyRow(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("Lun", "Mar", "Mie", "Jue", "Vie", "Sab")) { d -> RutaChip(d, uiState.selectedDay == d) { onDaySelect(d) } } }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Par", "Non").forEach { w -> Box(Modifier.weight(1f)) { RutaChip("Semana $w", uiState.selectedWeek == w) { onWeekSelect(w) } } } }
                }
            }
            TabRow(selectedTabIndex = if (vistaMapa) 1 else 0, containerColor = Color.White, contentColor = Color.Red, indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[if (vistaMapa) 1 else 0]), color = Color.Red) }) {
                Tab(selected = !vistaMapa, onClick = { vistaMapa = false }, text = { Text("LISTA") })
                Tab(selected = vistaMapa, onClick = { vistaMapa = true }, text = { Text("MAPA") })
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (vistaMapa) { VistaMapaRutas(clientes = uiState.clientesDisponibles, selectedIds = uiState.selectedClientIds, onToggle = onToggleCliente) }
                else { VistaListaRutas(clientes = uiState.clientesDisponibles, selectedIds = uiState.selectedClientIds, onToggle = onToggleCliente) }
                if (uiState.isLoading) { Box(Modifier.fillMaxSize().background(Color.White.copy(0.5f)), Alignment.Center) { CircularProgressIndicator(color = Color.Red) } }
            }
        }
    }
}

@Composable
fun RutaChip(nombre: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(modifier = Modifier.clickable { onClick() }, shape = RoundedCornerShape(12.dp), color = if (isSelected) Color.Red else Color(0xFFF1F2F6)) {
        Text(nombre, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = if (isSelected) Color.White else Color.DarkGray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun VistaListaRutas(clientes: List<ClienteEntity>, selectedIds: Set<String>, onToggle: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(clientes, key = { it.id }) { c ->
            val sel = selectedIds.contains(c.id)
            Card(modifier = Modifier.fillMaxWidth().clickable { onToggle(c.id) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = if (sel) androidx.compose.foundation.BorderStroke(2.dp, Color.Red) else null) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = c.fotografiaUrl, contentDescription = null, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentScale = ContentScale.Crop, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(c.nombreNegocio, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text(c.nombreDueno, fontSize = 12.sp, color = Color.Gray) }
                    Checkbox(checked = sel, onCheckedChange = { onToggle(c.id) }, colors = CheckboxDefaults.colors(checkedColor = Color.Red))
                }
            }
        }
    }
}

@Composable
fun VistaMapaRutas(clientes: List<ClienteEntity>, selectedIds: Set<String>, onToggle: (String) -> Unit) {
    var selMap by remember { mutableStateOf<ClienteEntity?>(null) }
    val camPos = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(19.4768, -96.5897), 12f) }
    Box(Modifier.fillMaxSize()) {
        GoogleMap(Modifier.fillMaxSize(), cameraPositionState = camPos, onMapClick = { selMap = null }) {
            clientes.forEach { c -> Marker(state = MarkerState(LatLng(c.ubicacionLat, c.ubicacionLon)), title = c.nombreNegocio, icon = if (selectedIds.contains(c.id)) BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED) else BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN), onClick = { selMap = c; true }) }
        }
        AnimatedVisibility(visible = selMap != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp, start = 16.dp, end = 16.dp).zIndex(5f)) {
            selMap?.let { c ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(12.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = c.fotografiaUrl, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.nombreNegocio, fontWeight = FontWeight.Black, fontSize = 16.sp); Text(c.nombreDueno, color = Color.Gray, fontSize = 13.sp)
                            Button(onClick = { onToggle(c.id) }, colors = ButtonDefaults.buttonColors(containerColor = if (selectedIds.contains(c.id)) Color.DarkGray else Color.Red), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 8.dp).height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) { Text(if (selectedIds.contains(c.id)) "QUITAR" else "AGREGAR", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        }
                        IconButton(onClick = { selMap = null }) { Icon(Icons.Default.Close, null) }
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
