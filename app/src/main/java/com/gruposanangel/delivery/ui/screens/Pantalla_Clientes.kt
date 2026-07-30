package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.Plantilla_Cliente
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PantallaClientes(navController: NavController, repository: RepositoryCliente?, isAdmin: Boolean = false) {
    val isPreview = LocalInspectionMode.current
    if (isPreview || repository == null) {
        PantallaClientesContent(
            uiState = ClienteUiState(clientes = listOf(Plantilla_Cliente("1", "Tienda A", "Juan", "", true), Plantilla_Cliente("2", "Abarrotes B", "Mary", "", true))),
            onSearchQueryChanged = {}, onClienteClick = {}, onCarritoClick = {}, onNuevoCliente = {}, isAdmin = isAdmin, onEditClick = {}
        )
    } else {
        val viewModel: ClienteViewModel = viewModel(factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ClienteViewModel(repository) as T
        })
        val uiState by viewModel.uiState.collectAsState()
        val context = LocalContext.current
        
        LaunchedEffect(Unit) {
            if (uiState.clientes.isEmpty()) {
                viewModel.syncData(context)
            }
        }

        PantallaClientesContent(
            uiState = uiState,
            onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
            onClienteClick = { id -> navController.navigate("detalle_cliente/$id?origen=Clientes") },
            onCarritoClick = { id -> navController.navigate("pantalla_ventas/$id") },
            onNuevoCliente = { navController.navigate("crear_cliente") },
            isAdmin = isAdmin,
            onEditClick = { id -> navController.navigate("editar_cliente/$id") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaClientesContent(uiState: ClienteUiState, onSearchQueryChanged: (String) -> Unit, onClienteClick: (String) -> Unit, onCarritoClick: (String) -> Unit, onNuevoCliente: () -> Unit, isAdmin: Boolean, onEditClick: (String) -> Unit) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNuevoCliente, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("NUEVO", fontWeight = FontWeight.Black)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.searchQuery, 
                onValueChange = onSearchQueryChanged, 
                placeholder = { 
                    val label = if (uiState.totalClientes > 0) "Buscar Cliente (${uiState.totalClientes} disponibles)" else "Buscar negocio..."
                    Text(label) 
                },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, cursorColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.height(16.dp))
            if (uiState.isLoading) { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) } }
            else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(uiState.clientes, key = { it.id }) { cliente ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.97f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                            label = "clientCardScale"
                        )

                        Card(
                            shape = RoundedCornerShape(24.dp), 
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), 
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .shadow(if (isPressed) 2.dp else 4.dp, RoundedCornerShape(24.dp))
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = androidx.compose.material.ripple.rememberRipple(bounded = true, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    onClick = { onClienteClick(cliente.id) }
                                )
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = cliente.fotografiaCliente, contentDescription = null, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentScale = ContentScale.Crop, modifier = Modifier.size(70.dp).clip(RoundedCornerShape(16.dp)))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(cliente.nombreNegocio, fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text(cliente.nombreDueno, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    if (cliente.distanciaTexto.isNotEmpty()) {
                                        val enRango = cliente.distanciaMetros in 0f..200f
                                        val colorTexto = if (enRango) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                        val colorFondo = colorTexto.copy(alpha = 0.12f)

                                        Spacer(Modifier.height(4.dp))
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(colorFondo)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.LocationOn,
                                                    null,
                                                    tint = colorTexto,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(Modifier.width(2.dp))
                                                Text(
                                                    cliente.distanciaTexto,
                                                    color = colorTexto,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                                Surface(
                                    onClick = { if (isAdmin) onEditClick(cliente.id) else onCarritoClick(cliente.id) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isAdmin) Icons.Default.EditNote else Icons.Default.ShoppingCart,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
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

@Preview(showBackground = true, showSystemUi = true, name = "Clientes - Lista")
@Composable
fun PantallaClientesPreview() {
    val clientes = listOf(Plantilla_Cliente("1", "Abarrotes La Pasadita", "Don Chon", "", true))
    DeliveryTheme { PantallaClientesContent(ClienteUiState(clientes = clientes, isLoading = false), {}, {}, {}, {}, true, {}) }
}

@Preview(showBackground = true, showSystemUi = true, name = "Clientes - Cargando")
@Composable
fun PantallaClientesLoadingPreview() {
    DeliveryTheme { PantallaClientesContent(ClienteUiState(isLoading = true), {}, {}, {}, {}, false, {}) }
}
