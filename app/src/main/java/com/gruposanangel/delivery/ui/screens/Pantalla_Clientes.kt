package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.Plantilla_Cliente
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PantallaClientes(navController: NavController, repository: RepositoryCliente?, isAdmin: Boolean = false) {
    val isPreview = LocalInspectionMode.current
    if (isPreview || repository == null) {
        PantallaClientesContent(
            uiState = ClienteUiState(clientes = listOf(Plantilla_Cliente("1", "Tienda A", "Juan", "", true), Plantilla_Cliente("2", "Abarrotes B", "Mary", "", true))),
            onSearchQueryChanged = {}, onClienteClick = {}, onCarritoClick = {}, onNuevoCliente = {}, isAdmin = isAdmin, onEditClick = {}, onToggleCiclo = {}
        )
    } else {
        val context = LocalContext.current
        val db = AppDatabase.getDatabase(context)
        val repoUsuario = RepositoryUsuario(FirebaseDataSource(), db.usuarioDao())
        val ventaRepo = com.gruposanangel.delivery.VentaRepository(db.VentaDao(), db.productoDao())
        
        val viewModel: ClienteViewModel = viewModel(factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = 
                ClienteViewModel(repository, repoUsuario, ventaRepo) as T
        })
        val uiState by viewModel.uiState.collectAsState()
        
        LaunchedEffect(isAdmin) {
            viewModel.configurarModo(isAdmin)
        }
        
        LaunchedEffect(Unit) {
            if (uiState.clientes.isEmpty()) {
                viewModel.syncData(context)
            }
        }

        PantallaClientesContent(
            uiState = uiState,
            onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
            onClienteClick = { id -> navController.navigate("detalle_cliente/$id?origen=Clientes") },
            onCarritoClick = { id -> navController.navigate("pantalla_ventas/$id?isAdminOverride=$isAdmin") },
            onNuevoCliente = { navController.navigate("crear_cliente") },
            isAdmin = isAdmin,
            onEditClick = { id -> navController.navigate("editar_cliente/$id") },
            onToggleCiclo = { viewModel.toggleFiltroCicloAnterior() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaClientesContent(
    uiState: ClienteUiState, 
    onSearchQueryChanged: (String) -> Unit, 
    onClienteClick: (String) -> Unit, 
    onCarritoClick: (String) -> Unit, 
    onNuevoCliente: () -> Unit, 
    isAdmin: Boolean, 
    onEditClick: (String) -> Unit,
    onToggleCiclo: () -> Unit
) {
    val context = LocalContext.current
    val fmtMoneda = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("es-MX"))

    // 🔥 FIX DEFINITIVO: Estado local puro para el buscador. 
    // NO sincronizar con el ViewModel para evitar que el cursor salte.
    var textFieldValue by remember { mutableStateOf(TextFieldValue(uiState.searchQuery)) }

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
        Column(Modifier.fillMaxSize().padding(
            start = 16.dp, 
            end = 16.dp, 
            top = 0.dp, // 🔥 Pegado al borde superior del contenedor
            bottom = padding.calculateBottomPadding()
        )) {
            // 🔥 CABECERA DE GUÍA HISTÓRICA (Margen reducido)
            if (uiState.visitasPasadasCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EditNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            val fechaTexto = uiState.fechaCicloAnterior?.let {
                                val dateStr = java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.forLanguageTag("es-MX")).format(java.util.Date(it)).uppercase()
                                "HACE 14 DIAS ($dateStr)"
                            } ?: "HACE 14 DIAS"
                            Text("GUIA: $fechaTexto", fontWeight = FontWeight.Black, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("${uiState.visitasPasadasCount} CLIENTES VISITADOS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("META:", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = fmtMoneda.format(uiState.metaVentaPasada),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = DelisaRed
                                    )
                                }
                            }
                            FilterChip(
                                selected = uiState.filtrandoSoloCicloAnterior,
                                onClick = onToggleCiclo,
                                label = { Text(if (uiState.filtrandoSoloCicloAnterior) "VER TODO" else "VER GUÍA", fontSize = 10.sp, fontWeight = FontWeight.Black) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = textFieldValue, 
                onValueChange = {
                    textFieldValue = it
                    onSearchQueryChanged(it.text)
                },
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(cliente.nombreNegocio, fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                        if (cliente.visitadoAnteriormente) {
                                            Spacer(Modifier.width(6.dp))
                                            Box(Modifier.clip(CircleShape).background(DelisaBlue.copy(alpha = 0.2f)).size(16.dp), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.EditNote, null, tint = DelisaBlueDark, modifier = Modifier.size(10.dp))
                                            }
                                        }
                                    }
                                    Text(cliente.nombreDueno, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // 1. BADGE HISTÓRICO (Independiente del GPS)
                                        if (cliente.visitadoAnteriormente) {
                                            val colorBadge = if (cliente.montoCompraPasada > 0) DelisaBlueDark else Color.Gray
                                            val fondoBadge = if (cliente.montoCompraPasada > 0) DelisaBlue.copy(alpha = 0.12f) else Color.Gray.copy(alpha = 0.1f)
                                            
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(fondoBadge)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = fmtMoneda.format(cliente.montoCompraPasada),
                                                    color = colorBadge,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                            Spacer(Modifier.width(6.dp))
                                        }

                                        // 2. BADGE DE DISTANCIA (Solo si hay señal GPS)
                                        if (cliente.distanciaTexto.isNotEmpty()) {
                                            val enRango = cliente.distanciaMetros in 0f..200f
                                            val colorTexto = if (enRango) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                            val colorFondo = colorTexto.copy(alpha = 0.12f)

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
    DeliveryTheme { PantallaClientesContent(ClienteUiState(clientes = clientes, isLoading = false), {}, {}, {}, {}, true, {}, {}) }
}

@Preview(showBackground = true, showSystemUi = true, name = "Clientes - Cargando")
@Composable
fun PantallaClientesLoadingPreview() {
    DeliveryTheme { PantallaClientesContent(ClienteUiState(isLoading = true), {}, {}, {}, {}, false, {}, {}) }
}
