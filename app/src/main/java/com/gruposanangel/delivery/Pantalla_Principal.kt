package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaRepository
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
    val ventaDao = AppDatabase.getDatabase(context).VentaDao()
    val ventaRepository = VentaRepository(ventaDao)

    val viewModel: VistaModeloVenta = viewModel(
        factory = VistaModeloVentaFactory(
            repositoryInventario = inventarioRepo,
            ventaRepository = ventaRepository
        )
    )

    val isPreview = LocalInspectionMode.current
    val items = listOf(Screen.Inventario, Screen.Clientes, Screen.Inicio, Screen.Ruta, Screen.Mapa)
    var selectedScreen by remember { mutableStateOf(items.find { it.label == startScreen } ?: Screen.Inicio) }
    var displayName by remember { mutableStateOf(if (isPreview) "Usuario de Prueba" else "Cargando...") }
    var photoUrl by remember { mutableStateOf("") }

    // Cargar datos Firebase
    LaunchedEffect(Unit) {
        if (!isPreview) {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                val uid = currentUser?.uid ?: return@LaunchedEffect

                val docs = FirebaseFirestore.getInstance()
                    .collection("users")
                    .whereEqualTo("uid", uid)
                    .get()
                    .await()

                if (docs.isEmpty) {
                    displayName = "Usuario no encontrado"
                } else {
                    val userDoc = docs.documents.first()
                    displayName = userDoc.getString("nombre") ?: "Usuario"
                    photoUrl = userDoc.getString("photo_url") ?: ""
                }
            } catch (_: Exception) {
                displayName = "Error al cargar usuario"
            }
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedCurvedBottomBarPro(
                items = items,
                selectedScreen = selectedScreen,
                onItemSelected = { selectedScreen = it }
            )
        }

    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedScreen == Screen.Inicio) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = if (isPreview) 8.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val clickableModifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .clickable { navController.navigate("perfil_usuario") }

                    if (isPreview || photoUrl.isEmpty()) {
                        Image(
                            painter = painterResource(R.drawable.repartidor),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = clickableModifier
                        )
                    } else {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = clickableModifier
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = {
                        if (!isPreview) FirebaseAuth.getInstance().signOut()
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Cerrar sesión")
                    }







                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedScreen) {
                    Screen.Inicio -> Pantalla_Inicio(onImpresoraSeleccionada)
                    Screen.Clientes -> repository?.let { PantallaClientes(navController, it) }
                    Screen.Inventario -> PantallaInventario(navController, inventarioRepo)
                    Screen.Ruta -> PaginaVentaScreen(navController, ventaRepository)
                    Screen.Mapa -> {
                        MapaScreen(navController = navController)
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
    val notchRadius = 32.dp
    val liftHeight = 24.dp

    val iconPositions = remember { MutableList(items.size) { 0f } }
    val selectedIndex = items.indexOf(selectedScreen)

    // Detectar si todos los iconos ya reportaron su posición
    val allMeasured = iconPositions.none { it == 0f }

    // Posición inicial PRO (evita que se vaya a 0px)
    val initialX = remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(iconPositions[selectedIndex]) {
        val pos = iconPositions[selectedIndex]
        if (pos != 0f && initialX.value == null) {
            initialX.value = pos
        }
    }

    val notchX by animateFloatAsState(
        targetValue = if (allMeasured) iconPositions[selectedIndex] else (initialX.value ?: 0f),
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 80f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {

        // Fondo con notch profesional + BLUR SUAVE
        Canvas(Modifier.fillMaxSize()) {

            val radius = notchRadius.toPx()
            val lift = liftHeight.toPx()

            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(notchX - radius, 0f)
                quadraticBezierTo(
                    notchX,
                    -lift,
                    notchX + radius,
                    0f
                )
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }

            // BLUR/SOMBRA SUAVE debajo del notch
            drawPath(
                path = path,
                color = Color(0xFFFF0000), // tu rojo original
                alpha = 0.92f
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {

            items.forEachIndexed { index, screen ->

                val isSelected = index == selectedIndex

                // Escala tipo iPhone
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.35f else 1f,
                    animationSpec = spring(
                        dampingRatio = 0.45f,
                        stiffness = 160f
                    )
                )

                // Levantamiento sutil
                val offsetY by animateDpAsState(
                    targetValue = if (isSelected) (-12).dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = 0.60f,
                        stiffness = 140f
                    )
                )

                Column(
                    modifier = Modifier
                        .width(70.dp)
                        .offset(y = offsetY)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemSelected(screen) }
                        .onGloballyPositioned { pos ->
                            iconPositions[index] =
                                pos.positionInParent().x + pos.size.width / 2f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // ICONO CON GLOW PRO+
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = if (isSelected) 20f else 0f
                                shape = CircleShape
                                clip = false
                            }
                    ) {
                        Icon(
                            screen.icon,
                            contentDescription = screen.label,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = screen.label.trim(),
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1
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
