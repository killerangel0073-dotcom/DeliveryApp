package com.gruposanangel.delivery.ui.screens

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.ClienteRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaClientes(navController: NavController, repository: ClienteRepository) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var buscando by remember { mutableStateOf(false) }

    // ✅ Carga los clientes desde la base de datos local
    val clientesLocal by repository.obtenerClientesLocal().collectAsState(initial = emptyList())

    // Filtrado por búsqueda
    val listaFiltrada = if (buscando) {
        clientesLocal.filter { it.nombreNegocio.contains(textFieldValue.text, ignoreCase = true) }
            .map { ClienteVenta(it.nombreNegocio, it.nombreDueno, it.fotografiaUrl ?: "", it.activo) }
    } else {
        clientesLocal.map { ClienteVenta(it.nombreNegocio, it.nombreDueno, it.fotografiaUrl ?: "", it.activo) }
    }

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

            // LISTA DE CLIENTES
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(listaFiltrada) { cliente ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { },
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
        ClienteVenta("Negocio 1", "Dueño 1", "", true),
        ClienteVenta("Negocio 2", "Dueño 2", "", false),
        ClienteVenta("Negocio 3", "Dueño 3", "", true)
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // BUSCADOR simulado
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
                                model = "",
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
