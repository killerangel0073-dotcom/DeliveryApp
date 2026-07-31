package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.gruposanangel.delivery.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Pantalla_Gestion_Almacenes(navController: NavController) {
    val viewModel: AlmacenViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Gestión de Almacenes", fontWeight = FontWeight.Black) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DelisaRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir Almacén")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = DelisaRed
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Almacenes Registrados",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    items(uiState.almacenes) { almacen ->
                        AlmacenCard(
                            nombre = almacen,
                            onEdit = { showEditDialog = almacen },
                            onDelete = { showDeleteDialog = almacen }
                        )
                    }
                    
                    if (uiState.almacenes.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "No hay almacenes registrados",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Diálogo para Añadir
    if (showAddDialog) {
        var nombre by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Nuevo Almacén", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre del Almacén") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DelisaRed,
                        focusedLabelColor = DelisaRed
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nombre.isNotBlank()) {
                            viewModel.crearAlmacen(nombre) {
                                showAddDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
                ) {
                    Text("CREAR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
    
    // Diálogo para Editar
    showEditDialog?.let { originalName ->
        var nombre by remember { mutableStateOf(originalName) }
        AlertDialog(
            onDismissRequest = { showEditDialog = null },
            title = { Text("Editar Almacén", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre del Almacén") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DelisaGreen,
                        focusedLabelColor = DelisaGreen
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nombre.isNotBlank() && nombre != originalName) {
                            viewModel.editarAlmacen(originalName, nombre) {
                                showEditDialog = null
                            }
                        } else {
                            showEditDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaGreen)
                ) {
                    Text("GUARDAR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = null }) {
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
    
    // Diálogo para Eliminar
    showDeleteDialog?.let { nombre ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("¿Eliminar Almacén?", fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de que deseas eliminar el almacén '$nombre'? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.eliminarAlmacen(nombre)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
                ) {
                    Text("ELIMINAR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun AlmacenCard(nombre: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Default.Warehouse,
                    contentDescription = null,
                    tint = DelisaRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = nombre,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = DelisaGreen)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = DelisaRed)
                }
            }
        }
    }
}
