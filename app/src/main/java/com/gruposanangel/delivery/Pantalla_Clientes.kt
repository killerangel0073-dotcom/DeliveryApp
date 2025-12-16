package com.gruposanangel.delivery.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.Plantilla_Cliente
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.RepositoryCliente
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaClientes(navController: NavController, repository: RepositoryCliente) {
    // Guardamos la búsqueda (rememberSaveable si quieres persistir en rotaciones)
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var buscando by remember { mutableStateOf(false) }

    // Carga los clientes desde la base de datos local (Flow -> State)
    val clientesLocal by repository.obtenerClientesLocal().collectAsState(initial = emptyList())

    // Lista filtrada memorizada: se recalcula sólo cuando cambia la búsqueda o la lista local
    val listaFiltradaState = remember(textFieldValue.text, clientesLocal) {
        derivedStateOf {
            val lista = if (buscando && textFieldValue.text.isNotBlank()) {
                clientesLocal.filter { it.nombreNegocio.contains(textFieldValue.text, ignoreCase = true) }
            } else {
                clientesLocal
            }
            // Mapear a Plantilla_Cliente
            lista.map { dbItem ->
                Plantilla_Cliente(
                    id = dbItem.id,
                    nombreNegocio = dbItem.nombreNegocio,
                    nombreDueno = dbItem.nombreDueno,
                    fotografiaCliente = dbItem.fotografiaUrl ?: "",
                    activo = dbItem.activo
                )
            }
        }
    }
    val listaFiltrada by listaFiltradaState

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // BUSCADOR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        buscando = it.text.isNotEmpty()
                    },
                    placeholder = { Text("Buscar Cliente", color = Color(0xFF888888)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF5F5F5),
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        cursorColor = Color(0xFFB71C1C),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Si no hay clientes -> mensaje vacío
            if (listaFiltrada.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (clientesLocal.isEmpty()) "No hay clientes registrados" else "No se encontraron coincidencias",
                        color = Color(0xFF666666),
                        fontSize = 16.sp
                    )
                }
            } else {
                // LISTA DE CLIENTES
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(listaFiltrada, key = { it.id }) { cliente ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable {
                                    // Navegar a pantalla de ventas con el cliente seleccionado
                                   // navController.navigate("pantalla_ventas2/${cliente.id}")

                                    navController.navigate("detalle_cliente/${cliente.id}") {
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
                                // Construir modelo de imagen robusto: si es ruta local, usar Uri.fromFile
                                val imageModel = remember(cliente.fotografiaCliente) {
                                    val path = cliente.fotografiaCliente
                                    if (path.isNotBlank()) {
                                        val f = File(path)
                                        if (f.exists()) Uri.fromFile(f) else path
                                    } else null
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

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        if (cliente.activo) "Activo" else "Inactivo",
                                        color = if (cliente.activo) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // BOTÓN FLOTANTE que navega a CrearClienteScreen
        FloatingActionButton(
            onClick = { navController.navigate("crear_cliente") },
            containerColor = Color(0xFFFF0000),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Agregar Cliente")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PantallaClientesPreview() {
    val clientesPreview = listOf(
        Plantilla_Cliente("1", "Negocio 1", "Dueño 1", "", true),
        Plantilla_Cliente("2", "Negocio 2", "Dueño 2", "", false),
        Plantilla_Cliente("3", "Negocio 3", "Dueño 3", "", true)
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = TextFieldValue(""),
                    onValueChange = {},
                    placeholder = { Text("Buscar Cliente", color = Color(0xFF888888)) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF5F5F5),
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        cursorColor = Color(0xFFB71C1C),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(clientesPreview) { cliente ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = cliente.fotografiaCliente,
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

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (cliente.activo) "Activo" else "Inactivo",
                                    color = if (cliente.activo) Color(0xFF388E3C) else Color(0xFFD32F2F),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {},
            containerColor = Color(0xFFFF0000),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Agregar Cliente")
        }
    }
}
