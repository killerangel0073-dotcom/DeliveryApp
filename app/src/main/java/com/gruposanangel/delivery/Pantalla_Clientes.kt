package com.gruposanangel.delivery.ui.screens

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.RepositoryCliente
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CarritoAnimado(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition()

    // Animación de pulso infinito original que a ti te gusta
    val scaleBase by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    var scaleExtra by remember { mutableStateOf(1f) }
    val scale = scaleBase * scaleExtra
    val scope = rememberCoroutineScope()

    Icon(
        imageVector = Icons.Default.ShoppingCart,
        contentDescription = "Ver ventas",
        tint = Color(0xFFFF0000), // Tu rojo brillante original
        modifier = Modifier
            .size(32.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable {
                scaleExtra = 1.5f
                onClick()
                scope.launch {
                    kotlinx.coroutines.delay(150)
                    scaleExtra = 1f
                }
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaClientes(navController: NavController, repository: RepositoryCliente) {
    val viewModel: ClienteViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ClienteViewModel(repository) as T
            }
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    // Regresamos al fondo Blanco Puro original de tu diseño
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // BUSCADOR (Tu estilo original con la lupa añadida para guiar al ojo)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Buscar Cliente", color = Color(0xFF888888)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF888888))
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF5F5F5),
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        cursorColor = Color(0xFFB71C1C),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.Red)
                }
            } else if (uiState.clientes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No se encontraron clientes",
                        color = Color(0xFF666666),
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.clientes, key = { it.id }) { cliente ->
                        // Tus tarjetas con sombra pesada de 8.dp original
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable {
                                    navController.navigate("detalle_cliente/${cliente.id}?origen=Clientes") {
                                        launchSingleTop = true
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val imageModel = remember(cliente.fotografiaCliente) {
                                    val path = cliente.fotografiaCliente
                                    if (path.isBlank()) {
                                        null
                                    } else {
                                        val file = File(path)
                                        if (file.exists()) {
                                            Uri.fromFile(file)
                                        } else {
                                            null
                                        }
                                    }
                                }

                                AsyncImage(
                                    model = imageModel,
                                    contentDescription = cliente.nombreNegocio,
                                    placeholder = painterResource(R.drawable.repartidor),
                                    error = painterResource(R.drawable.repartidor),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        cliente.nombreNegocio,
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        cliente.nombreDueno,
                                        color = Color(0xFF555555),
                                        fontSize = 14.sp
                                    )
                                }

                                // Tu carrito parpadeante original
                                CarritoAnimado {
                                    navController.navigate("pantalla_ventas/${cliente.id}")
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { navController.navigate("crear_cliente") },
            containerColor = Color(0xFFFF0000),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = "Agregar Cliente")
        }
    }
}