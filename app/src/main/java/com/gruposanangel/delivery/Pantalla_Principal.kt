package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.FirebaseDataSource
import kotlinx.coroutines.tasks.await

// ------------------------------------------------------------
// SCREENS
// ------------------------------------------------------------
sealed class Screen(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Inventario : Screen("Inventario", Icons.Default.Category)
    object Clientes : Screen("Clientes", Icons.Default.Groups)
    object Inicio : Screen("Inicio", Icons.Default.HomeWork)
    object Ruta : Screen("  Ruta  ", Icons.Default.AltRoute)
    object Mapa : Screen("    Mapa    ", Icons.Default.Map)
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
    val ventaDao = db.VentaDao()
    val ventaRepository = VentaRepository(ventaDao)
    val usuarioDao = db.usuarioDao()
    val firebaseDataSource = FirebaseDataSource()
    val repoUsuario = RepositoryUsuario(firebaseDataSource, usuarioDao)

    val viewModel: VentaViewModel = viewModel(
        factory = VentaViewModelFactory(
            repositoryInventario = inventarioRepo,
            ventaRepository = ventaRepository,
            repositoryUsuario = repoUsuario
        )
    )

    // 📍 Persistencia del Mapa: El ViewModel vive en el scope de Pantalla_Principal
    val mapaViewModel: MapaViewModel = viewModel()

    val isPreview = LocalInspectionMode.current
    val items = listOf(Screen.Inventario, Screen.Clientes, Screen.Inicio, Screen.Ruta, Screen.Mapa)
    var selectedScreen by remember(startScreen) { 
        mutableStateOf(items.find { it.label == startScreen } ?: Screen.Inicio)
    }
    var displayName by remember { mutableStateOf(if (isPreview) "Usuario de Prueba" else "Cargando...") }
    var photoUrl by remember { mutableStateOf("") }
    var puestoTrabajo by remember { mutableStateOf<String?>(null) }

    // OFFLINE-FIRST: Observamos el usuario desde Room de forma reactiva
    val usuarioActual by repoUsuario.getUsuarioActual().collectAsState(initial = null)

    LaunchedEffect(usuarioActual) {
        usuarioActual?.let {
            displayName = it.nombre
            photoUrl = it.photoUrl ?: ""
            puestoTrabajo = it.puestoTrabajo
        }
    }

    val isAdmin = remember(puestoTrabajo) {
        puestoTrabajo == "CEO1.1" || puestoTrabajo == "Gerente General" || puestoTrabajo == "Supervisor" || puestoTrabajo == "Administración"
    }

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.background(Color(0xFFFF0000))) {
                AnimatedCurvedBottomBarPro(
                    items = items,
                    selectedScreen = selectedScreen,
                    onItemSelected = { screen ->
                        // 🔥 ACTUALIZACIÓN: Navegar físicamente para que el BackStack sepa dónde estamos
                        navController.navigate("delivery?screen=${screen.label}") {
                            popUpTo("delivery?screen=Inicio") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                // 🛡️ ESPACIO DINÁMICO PARA BOTONES DEL SISTEMA
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedScreen == Screen.Inicio) {
                // 🔝 HEADER MODERNO (Perfil y Logout)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Foto de Perfil con Borde
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = 0.1f))
                                .clickable { navController.navigate("perfil_usuario") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPreview || photoUrl.isEmpty()) {
                                Image(
                                    painter = painterResource(R.drawable.repartidor),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hola,",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }

                        // Botón Logout Estilizado
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8F9FA),
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { onLogout() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Logout,
                                    contentDescription = "Salir",
                                    tint = Color.Red,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedScreen) {
                    Screen.Inicio -> {
                        if (isAdmin) {
                            Pantalla_Dashboard_Admin(
                                navController = navController,
                                impresoraBluetooth = impresoraBluetooth,
                                onImpresoraSeleccionada = onImpresoraSeleccionada
                            )
                        } else {
                            PantallaDashboardVendedor(
                                navController = navController,
                                impresoraSeleccionada = impresoraBluetooth,
                                onImpresoraSeleccionada = onImpresoraSeleccionada
                            )
                        }
                    }

                    Screen.Clientes -> repository?.let { PantallaClientes(navController, it) }
                    Screen.Inventario -> PantallaInventario(navController, inventarioRepo)
                    Screen.Ruta -> PaginaVentaScreen(navController, ventaRepository)
                    Screen.Mapa -> {
                        MapaScreen(navController = navController, viewModel = mapaViewModel)
                    }

                }
            }
        }
    }
}

// ------------------------------------------------------------
// CURVED BOTTOM BAR MEJORADA
// ------------------------------------------------------------
@Composable
fun AnimatedCurvedBottomBarPro(
    items: List<Screen>,
    selectedScreen: Screen,
    onItemSelected: (Screen) -> Unit
) {
    val barHeight = 72.dp
    val notchRadius = 42.dp // Más ancha para que no se vea delgada
    val liftHeight = 16.dp // Más baja (antes 26.dp)

    val iconPositions = remember { MutableList(items.size) { 0f } }
    val selectedIndex = items.indexOf(selectedScreen)

    // 🎯 OPTIMIZACIÓN: Calculamos el centro de la pantalla como posición inicial por defecto
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val defaultCenterX = screenWidthPx / 2f

    // Detectar si todos los iconos ya reportaron su posición
    val allMeasured = iconPositions.none { it == 0f }

    // Posición inicial PRO: Empieza en el centro exacto para evitar el salto desde la izquierda
    val initialX = remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(iconPositions[selectedIndex]) {
        val pos = iconPositions[selectedIndex]
        if (pos != 0f && initialX.value == null) {
            initialX.value = pos
        }
    }

    val notchX by animateFloatAsState(
        targetValue = if (allMeasured) iconPositions[selectedIndex] else (initialX.value ?: defaultCenterX),
        animationSpec = spring(
            dampingRatio = 0.6f, 
            stiffness = Spring.StiffnessLow // Movimiento más elegante y "pesado"
        ),
        label = "notchAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .graphicsLayer { clip = false } // Evita cortes en el notch al subir
    ) {
        // Fondo con notch profesional + Gradiente sutil
        Canvas(Modifier.fillMaxSize()) {
            val radius = notchRadius.toPx()
            val lift = liftHeight.toPx()

            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(notchX - radius * 1.2f, 0f)
                // Curva de entrada suave
                cubicTo(
                    notchX - radius * 0.8f, 0f,
                    notchX - radius * 0.5f, -lift,
                    notchX, -lift
                )
                // Curva de salida suave
                cubicTo(
                    notchX + radius * 0.5f, -lift,
                    notchX + radius * 0.8f, 0f,
                    notchX + radius * 1.2f, 0f
                )
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }

            drawPath(
                path = path,
                color = Color(0xFFFF0000),
                alpha = 0.95f
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, screen ->
                val isSelected = index == selectedIndex
                
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.35f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
                    label = "iconScale"
                )

                val offsetY by animateDpAsState(
                    targetValue = if (isSelected) (-14).dp else 0.dp,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                    label = "iconOffset"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = offsetY)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemSelected(screen) }
                        .onGloballyPositioned { pos ->
                            iconPositions[index] = pos.positionInParent().x + pos.size.width / 2f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 🌟 EFECTO PREMIUM: Contenedor con Resplandor Sutil
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .background(
                                color = if (isSelected) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.label,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // 🏷️ Texto con entrada escalonada (Visibilidad mejorada para inactivos)
                    val textAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.8f,
                        animationSpec = tween(300),
                        label = "textAlpha"
                    )

                    Text(
                        text = screen.label.trim(),
                        color = Color.White.copy(alpha = textAlpha),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer {
                            scaleX = if (isSelected) 1.1f else 1.0f
                            scaleY = if (isSelected) 1.1f else 1.0f
                        }
                    )
                }
            }
        }
    }
}


// ------------------------------------------------------------
// PREVIEW
// ------------------------------------------------------------
@Preview(showBackground = true)
@Composable
fun PantallaPrincipal_Preview() {
    Scaffold(
        bottomBar = {
            AnimatedCurvedBottomBarPro(
                items = listOf(Screen.Inventario, Screen.Clientes, Screen.Inicio, Screen.Ruta, Screen.Mapa),
                selectedScreen = Screen.Inicio,
                onItemSelected = {}
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Pantalla Principal (Preview)")
        }
    }
}
