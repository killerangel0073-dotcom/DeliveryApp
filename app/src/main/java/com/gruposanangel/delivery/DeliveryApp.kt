package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.ClienteRepository

// ---------------------------
// SCREENS
// ---------------------------
sealed class Screen(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Inicio : Screen("Inicio", Icons.Default.Home)
    object Clientes : Screen("Clientes", Icons.Default.Person)
    object Productos : Screen("Productos", Icons.Default.ShoppingCart)
    object Ruta : Screen("Ruta", Icons.Default.DriveEta)
    object Mapa : Screen("Mapa", Icons.Default.LocationOn)
}

// ---------------------------
// NAV HOST
// ---------------------------
@Composable
fun DeliveryAppNav(repository: ClienteRepository?, onLogout: () -> Unit = {}) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "delivery?screen=Inicio") {
        composable(
            "delivery?screen={screen}",
            arguments = listOf(navArgument("screen") { defaultValue = "Inicio" })
        ) { backStackEntry ->
            val screenArg = backStackEntry.arguments?.getString("screen") ?: "Inicio"
            val mappedScreenArg = if (screenArg == "Inventario") "Clientes" else screenArg
            DeliveryApp(navController, mappedScreenArg, repository, onLogout)
        }

        composable("perfil_usuario") { PerfilDeUsuarioScreen(navController) }

        // Crear cliente con repository no nulo
        composable("crear_cliente") {
            CrearClienteScreen(navController, repository = repository!!)
        }
    }
}

// ---------------------------
// BOTTOM BAR
// ---------------------------
@Composable
fun ModernBottomBar(
    items: List<Screen>,
    selectedScreen: Screen,
    onItemSelected: (Screen) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFFFF0000),
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().height(80.dp)
    ) {
        items.forEach { screen ->
            val selected = screen == selectedScreen
            val transition = updateTransition(selected, label = "selectedTransition")
            val iconSize by transition.animateDp { if (it) 36.dp else 28.dp }
            val iconColor by transition.animateColor { if (it) Color.White else Color.White }
            val labelAlpha by transition.animateFloat { if (it) 1f else 0.8f }

            NavigationBarItem(
                selected = selected,
                onClick = { onItemSelected(screen) },
                icon = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(screen.icon, contentDescription = screen.label, tint = iconColor, modifier = Modifier.size(iconSize))
                        Spacer(Modifier.height(2.dp))
                        Text(screen.label, color = iconColor.copy(alpha = labelAlpha), fontSize = 15.sp)
                    }
                },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    unselectedIconColor = Color.White,
                    indicatorColor = Color.White.copy(alpha = 0.2f)
                )
            )
        }
    }
}

// ---------------------------
// MAIN APP
// ---------------------------
@Composable
fun DeliveryApp(
    navController: NavController,
    startScreen: String = "Inicio",
    repository: ClienteRepository? = null,
    onLogout: () -> Unit = {}
) {
    val isPreview = LocalInspectionMode.current
    val items = listOf(Screen.Productos, Screen.Clientes, Screen.Inicio, Screen.Ruta, Screen.Mapa)
    var selectedScreen by remember { mutableStateOf(items.find { it.label == startScreen } ?: Screen.Inicio) }
    var displayName by remember { mutableStateOf(if (isPreview) "Usuario de Prueba" else "Cargando...") }
    var photoUrl by remember { mutableStateOf("") }

    // Cargar datos de Firebase si no es preview
    LaunchedEffect(Unit) {
        if (!isPreview) {
            val currentUser = FirebaseAuth.getInstance().currentUser
            currentUser?.uid?.let { uid ->
                FirebaseFirestore.getInstance().collection("users")
                    .whereEqualTo("uid", uid)
                    .get()
                    .addOnSuccessListener { docs ->
                        if (!docs.isEmpty) {
                            val userDoc = docs.documents[0]
                            displayName = userDoc.getString("display_name") ?: "Usuario"
                            photoUrl = userDoc.getString("photo_url") ?: ""
                        } else displayName = "Usuario"
                    }
                    .addOnFailureListener { displayName = "Error al cargar usuario" }
            }
        }
    }

    Scaffold(
        bottomBar = {
            ModernBottomBar(
                items = items,
                selectedScreen = selectedScreen,
                onItemSelected = { selectedScreen = it }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // HEADER
            if (selectedScreen == Screen.Inicio) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isPreview) 8.dp else 0.dp),
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

            // CONTENIDO PRINCIPAL
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (selectedScreen) {
                    Screen.Inicio -> Text(text = "$displayName", style = MaterialTheme.typography.headlineMedium)
                    Screen.Clientes -> PantallaClientes(navController, repository = repository!!)
                    Screen.Productos -> PantallaProductos(navController)
                    Screen.Ruta -> PaginaVentaScreen(repository = repository!!)
                    Screen.Mapa -> MapaScreen()
                    else -> Text(text = "Pantalla: ${selectedScreen.label}", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}

// ---------------------------
// PRODUCTOS
// ---------------------------
@Composable
fun PantallaProductos(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Pantalla de Productos", style = MaterialTheme.typography.headlineMedium)
        Icon(Icons.Default.ShoppingCart, contentDescription = "Productos", tint = Color(0xFFB71C1C), modifier = Modifier.size(80.dp))
    }
}

// ---------------------------
// PREVIEW
// ---------------------------
@Preview(showBackground = true)
@Composable
fun DeliveryAppPreview() {
    val navController = rememberNavController()
    DeliveryApp(navController = navController, repository = null)
}
