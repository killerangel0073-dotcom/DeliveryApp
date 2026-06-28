package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.ui.theme.DeliveryTheme

// ------------------------------------------------------------
// SCREENS
// ------------------------------------------------------------
sealed class Screen(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Inventario : Screen("Inventario", Icons.Default.Category)
    object Clientes : Screen("Clientes", Icons.Default.Groups)
    object Inicio : Screen("Inicio", Icons.Default.HomeWork)
    object Ruta : Screen("  Ruta  ", Icons.Default.AltRoute)
    object Mapa : Screen("    Mapa    ", Icons.Default.Map)
    
    // SCREENS ALMACÉN
    object AlmacenGeneral : Screen("General", Icons.Default.Inventory)
    object CargasVendedores : Screen("Surtir", Icons.Default.LocalShipping)
    object HistorialAlmacen : Screen("Historial", Icons.Default.History)
}

// ------------------------------------------------------------
// PANTALLA PRINCIPAL
// ------------------------------------------------------------
@Composable
fun Pantalla_Principal(
    navController: NavController,
    startScreen: String = "Inicio",
    repository: RepositoryCliente? = null,
    inventarioRepo: RepositoryInventario,
    onLogout: () -> Unit = {},
    impresoraBluetooth: BluetoothDevice? = null,
    onImpresoraSeleccionada: (BluetoothDevice) -> Unit = {}
) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val ventaRepository = VentaRepository(db.VentaDao(), db.productoDao())
    val repoUsuario = RepositoryUsuario(FirebaseDataSource(), usuarioDao = db.usuarioDao())

    // OFFLINE-FIRST: Observamos el usuario desde Room de forma reactiva
    val usuarioActual by repoUsuario.getUsuarioActual().collectAsState(initial = null)
    val isPreview = LocalInspectionMode.current

    val puestoActual = remember(usuarioActual) { usuarioActual?.puestoTrabajo?.trim() ?: "" }

    // 🔥 VALIDACIONES ROBUSTAS (Ignoran mayúsculas/minúsculas y variaciones de género/acentos)
    val isAdmin = remember(puestoActual) {
        val p = puestoActual.uppercase()
        p == "CEO" || p == "GERENTE GENERAL" || p == "SUPERVISOR" || p.contains("ADMINISTRACI")
    }

    val esVendedor = remember(puestoActual) {
        puestoActual.contains("Vendedor", ignoreCase = true) || puestoActual.contains("Suplente", ignoreCase = true)
    }

    val esAlmacen = remember(puestoActual) {
        puestoActual.contains("Almacen", ignoreCase = true) || puestoActual.contains("Bodega", ignoreCase = true)
    }

    val esProduccion = remember(puestoActual) {
        puestoActual.contains("Produccion", ignoreCase = true) || puestoActual.contains("Planta", ignoreCase = true)
    }

    // 🚀 LISTA DINÁMICA SEGÚN ROL - ORDEN SOLICITADO PARA ALMACÉN
    val navigationItems = remember(isAdmin, esVendedor, esAlmacen, esProduccion) {
        when {
            isAdmin -> listOf(Screen.Inventario, Screen.Clientes, Screen.Inicio, Screen.Ruta, Screen.Mapa)
            esAlmacen -> listOf(Screen.CargasVendedores, Screen.AlmacenGeneral, Screen.HistorialAlmacen)
            else -> listOf(Screen.Inventario, Screen.Clientes, Screen.Inicio, Screen.Ruta, Screen.Mapa)
        }
    }

    // 🎯 SCREEN SELECCIONADA (Sincronizada con la URL del Navegador)
    val selectedScreen = remember(startScreen, navigationItems) { 
        if (esAlmacen && (startScreen == "Inicio" || startScreen == "")) {
            Screen.AlmacenGeneral 
        } else {
            navigationItems.find { it.label == startScreen } ?: navigationItems.first()
        }
    }

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.background(Color(0xFFFF0000))) {
                AnimatedCurvedBottomBarPro(
                    items = navigationItems,
                    selectedScreen = selectedScreen,
                    onItemSelected = { screen ->
                        if (selectedScreen != screen) {
                            navController.navigate("delivery?screen=${screen.label}") {
                                popUpTo(navController.graph.startDestinationId) { 
                                    saveState = true 
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Mostrar Header en Inicio (Admin/Vendedor) o en General (Almacén)
            val mostrarHeader = selectedScreen == Screen.Inicio || (esAlmacen && selectedScreen == Screen.AlmacenGeneral)
            
            if (mostrarHeader) {
                ModernProfileHeader(
                    nombre = usuarioActual?.nombre ?: (if(isPreview) "Admin Test" else "Cargando..."),
                    puesto = puestoActual.ifEmpty { "Cargando..." },
                    photoUrl = usuarioActual?.photoUrl ?: "",
                    enRuta = usuarioActual?.ultimoAlmacenNombre != null,
                    onLogout = onLogout,
                    onProfileClick = { navController.navigate("perfil_usuario") }
                )
                Spacer(Modifier.height(8.dp))
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedScreen) {
                    Screen.Inicio -> {
                        when {
                            isAdmin -> Pantalla_Dashboard_Admin(navController, impresoraBluetooth, onImpresoraSeleccionada)
                            esVendedor -> PantallaDashboardVendedor(navController, impresoraBluetooth, onImpresoraSeleccionada)
                            esProduccion -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Factory, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                                        Spacer(Modifier.height(16.dp))
                                        Text("MODULO PRODUCCIÓN", fontWeight = FontWeight.Black, color = Color.Gray)
                                    }
                                }
                            }
                            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.Red) }
                        }
                    }
                    Screen.AlmacenGeneral, Screen.Inventario -> {
                        PantallaInventario(navController, inventarioRepo)
                    }
                    Screen.CargasVendedores -> {
                        MovimientosInventarioScreen(
                            navController = navController,
                            impresoraBluetooth = impresoraBluetooth,
                            onImpresoraSeleccionada = onImpresoraSeleccionada,
                            isTabMode = true
                        )
                    }
                    Screen.HistorialAlmacen -> {
                        PantallaHistorialCargas(navController = navController)
                    }
                    Screen.Clientes -> repository?.let { PantallaClientes(navController, it, isAdmin) }
                    Screen.Ruta -> PaginaVentaScreen(navController, ventaRepository)
                    Screen.Mapa -> MapaScreen(navController = navController, viewModel = viewModel())
                }
            }
        }
    }
}

@Composable
fun ModernProfileHeader(
    nombre: String, puesto: String, photoUrl: String, enRuta: Boolean, onLogout: () -> Unit, onProfileClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.94f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow), label = "")

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).clickable(interactionSource = interactionSource, indication = null, onClick = onProfileClick)) {
                Surface(modifier = Modifier.size(52.dp).border(2.dp, Color.Red.copy(0.1f), CircleShape), shape = CircleShape, color = Color(0xFFF8F9FA)) {
                    if (photoUrl.isNotEmpty()) AsyncImage(model = photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Image(painter = painterResource(R.drawable.repartidor), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(Modifier.size(14.dp).background(Color.White, CircleShape).padding(2.dp)) {
                    Box(Modifier.fillMaxSize().background(if (enRuta) Color(0xFF4CAF50) else Color.LightGray, CircleShape))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = nombre, fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color(0xFF1A1A1A), maxLines = 1)
                Surface(color = Color.Red.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Text(text = puesto.uppercase(), color = Color.Red, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            IconButton(onClick = onLogout, modifier = Modifier.size(40.dp).background(Color(0xFFFDECEA), CircleShape)) {
                Icon(imageVector = Icons.Default.Logout, contentDescription = "Salir", tint = Color.Red, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun AnimatedCurvedBottomBarPro(items: List<Screen>, selectedScreen: Screen, onItemSelected: (Screen) -> Unit) {
    val barHeight = 72.dp; val notchRadius = 42.dp; val liftHeight = 16.dp
    val iconPositions = remember { mutableStateListOf<Float>().apply { repeat(items.size) { add(0f) } } }
    val selectedIndex = items.indexOf(selectedScreen).coerceAtLeast(0)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenWidthPx = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() }
    
    val notchX by animateFloatAsState(
        targetValue = if (iconPositions.size > selectedIndex && iconPositions[selectedIndex] != 0f) iconPositions[selectedIndex] else screenWidthPx / 2f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = ""
    )

    Box(modifier = Modifier.fillMaxWidth().height(barHeight).graphicsLayer { clip = false }) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = notchRadius.toPx(); val lift = liftHeight.toPx()
            val path = Path().apply {
                moveTo(0f, 0f); lineTo(notchX - radius * 1.2f, 0f)
                cubicTo(notchX - radius * 0.8f, 0f, notchX - radius * 0.5f, -lift, notchX, -lift)
                cubicTo(notchX + radius * 0.5f, -lift, notchX + radius * 0.8f, 0f, notchX + radius * 1.2f, 0f)
                lineTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(path = path, color = Color(0xFFFF0000), alpha = 0.95f)
        }
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { index, screen ->
                val isSelected = index == selectedIndex
                val scale by animateFloatAsState(targetValue = if (isSelected) 1.35f else 1f, animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "")
                val offsetY by animateDpAsState(targetValue = if (isSelected) (-14).dp else 0.dp, animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "")

                Column(
                    modifier = Modifier.weight(1f).offset(y = offsetY).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onItemSelected(screen) }
                        .onGloballyPositioned { pos -> if(index < iconPositions.size) iconPositions[index] = pos.positionInParent().x + pos.size.width / 2f },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(44.dp).graphicsLayer { scaleX = scale; scaleY = scale }.background(color = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent, shape = CircleShape), contentAlignment = Alignment.Center) {
                        Icon(imageVector = screen.icon, contentDescription = screen.label, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(text = screen.label.trim(), color = Color.White, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PantallaPrincipal_Preview() {
    DeliveryTheme {
        Scaffold(bottomBar = { AnimatedCurvedBottomBarPro(items = listOf(Screen.Inicio, Screen.Inventario, Screen.Mapa), selectedScreen = Screen.Inicio, onItemSelected = {}) }) { innerPadding ->
            Box(Modifier.padding(innerPadding).fillMaxSize())
        }
    }
}
