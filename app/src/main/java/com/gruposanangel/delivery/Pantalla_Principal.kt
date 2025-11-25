package com.gruposanangel.delivery.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.gruposanangel.delivery.data.VentaDao
import com.gruposanangel.delivery.data.VentaRepository
import kotlinx.coroutines.tasks.await



// ---------------------------
// SCREENS
// ---------------------------
sealed class Screen(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Inventario : Screen("Inventario", Icons.Default.Category)
    object Clientes : Screen("Clientes", Icons.Default.Groups)
    object Inicio : Screen("Inicio", Icons.Default.HomeWork)
    object Ruta : Screen("  Ruta  ", Icons.Default.AltRoute)
    object Mapa : Screen("    Mapa    ", Icons.Default.Map)
}









// ---------------------------
// Pantalla principal con barra inferior y contenido dinámico
// ---------------------------
@Composable
fun Pantalla_Principal(
    navController: androidx.navigation.NavController,
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

    // 🔹 Cargar datos de Firebase solo en runtime
    LaunchedEffect(Unit) {
        if (!isPreview) {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                val uid = currentUser?.uid

                if (uid == null) {
                    displayName = "Usuario no autenticado"
                    return@LaunchedEffect
                }

                val docs = FirebaseFirestore.getInstance()
                    .collection("users")
                    .whereEqualTo("uid", uid)
                    .get()
                    .await()

                if (docs.isEmpty) {
                    displayName = "Usuario no encontrado"
                    photoUrl = ""
                } else {
                    val userDoc = docs.documents[0]
                    displayName = userDoc.getString("nombre") ?: "Usuario"
                    photoUrl = userDoc.getString("photo_url") ?: ""
                }
            } catch (e: Exception) {
                displayName = "Error al cargar usuario"
                photoUrl = ""
            }
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedCurvedBottomBar2(
                items = items,
                selectedScreen = selectedScreen,
                onItemSelected = { selectedScreen = it }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Barra superior solo en Inicio
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
                            contentDescription = "Foto de perfil",
                            contentScale = ContentScale.Crop,
                            modifier = clickableModifier
                        )
                    } else {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Foto de perfil",
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

            // Contenido según la pantalla seleccionada
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (selectedScreen) {
                    Screen.Inicio -> Pantalla_Inicio(onImpresoraSeleccionada = onImpresoraSeleccionada)
                    Screen.Clientes -> repository?.let { PantallaClientes(navController, it) }

                    Screen.Inventario -> PantallaProductos(navController, inventarioRepo)

                    Screen.Ruta -> {
                        PaginaVentaScreen(
                            navController = navController,
                            ventaRepository = ventaRepository
                        )
                    }

                    Screen.Mapa -> MapaScreen()
                    else -> Box {} // opción vacía por seguridad
                }
            }

        }
    }
}

@Composable
fun PantallaProductos(
    navController: NavController,
    inventarioRepo: RepositoryInventario
) {
    PantallaInventario(navController, inventarioRepo)
}

@Composable
fun AnimatedCurvedBottomBar2(
    items: List<Screen>,
    selectedScreen: Screen,
    onItemSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val barHeight = 70.dp
    val indicatorHeight = 20.dp
    val indicatorRadius = 28.dp

    val iconPositions = remember { mutableStateListOf<Float>() }
    val selectedIndex = items.indexOf(selectedScreen)

    Box(modifier = modifier.height(barHeight).fillMaxWidth()) {
        val targetX = iconPositions.getOrNull(selectedIndex) ?: 0f
        val animatedX by animateFloatAsState(
            targetValue = targetX,
            animationSpec = spring(
                dampingRatio = 0.5f,
                stiffness = 50f
            )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val notchWidth = indicatorRadius.toPx() * 2
            val notchHeight = indicatorHeight.toPx()

            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, size.height)
                lineTo(size.width, size.height)
                lineTo(size.width, 0f)

                lineTo(animatedX + notchWidth / 2, 0f)
                quadraticBezierTo(animatedX, -notchHeight, animatedX - notchWidth / 2, 0f)
                close()
            }

            drawPath(path, color = Color(0xFFFF0000))


        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, screen ->
                val isSelected = screen == selectedScreen

                val offsetY by animateDpAsState(
                    targetValue = if (isSelected) (-16).dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = 0.5f,
                        stiffness = 50f
                    )
                )

                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.3f else 1f,
                    animationSpec = spring(
                        dampingRatio = 0.5f,
                        stiffness = 50f
                    )
                )









                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .offset(y = offsetY)
                        .clickable { onItemSelected(screen) }
                        .onGloballyPositioned { coordinates ->
                            val centerX =
                                coordinates.positionInParent().x + coordinates.size.width / 2
                            if (iconPositions.size <= index) {
                                iconPositions.add(centerX)
                            } else {
                                iconPositions[index] = centerX
                            }
                        }
                ) {
                    Icon(
                        screen.icon,
                        contentDescription = screen.label,
                        tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp * scale)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = screen.label,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}








// ---------------------------
// Preview
// ---------------------------
@Preview(showBackground = true)
@Composable
fun PantallaPrincipal_Preview() {
    Scaffold(
        bottomBar = {
            AnimatedCurvedBottomBar2(
                items = listOf(Screen.Inventario, Screen.Clientes, Screen.Inicio, Screen.Ruta, Screen.Mapa),
                selectedScreen = Screen.Inicio,
                onItemSelected = {}
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Pantalla Principal (Preview)", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Se muestran solo componentes estáticos para el Preview")
        }
    }
}
