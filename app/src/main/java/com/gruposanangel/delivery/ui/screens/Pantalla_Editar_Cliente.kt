package com.gruposanangel.delivery.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.RepositoryRuta
import com.gruposanangel.delivery.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun EditarClienteScreen(navController: NavController, clienteId: String, repository: RepositoryCliente?) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    
    val vm: EditarClienteViewModel? = if (!isPreview && repository != null) {
        val db = AppDatabase.getDatabase(context)
        val repositoryRuta = RepositoryRuta(db.rutaDao(), db.clienteDao())
        viewModel(factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = EditarClienteViewModel(repository, repositoryRuta, clienteId) as T
        })
    } else null

    val uiState by vm?.uiState?.collectAsState() ?: remember { mutableStateOf(EditarClienteUiState()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val launcherCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> bmp?.let { vm?.let { v -> val file = v.createImageFile(context); v.saveBitmap(it, file); v.onImageSelected(file, it) } } }

    LaunchedEffect(uiState.status) { 
        if (uiState.status is RegistroUiStatus.Success) { 
            Toast.makeText(context, "Cliente actualizado", Toast.LENGTH_SHORT).show()
            navController.popBackStack() 
        } else if (uiState.status is RegistroUiStatus.Error) {
            Toast.makeText(context, (uiState.status as RegistroUiStatus.Error).message, Toast.LENGTH_LONG).show()
        }
    }

    if (uiState.isLoadingData) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = DelisaRed) }
    } else {
        EditarClienteContent(
            uiState = uiState,
            onBack = { navController.popBackStack() },
            onTakePhoto = { launcherCamera.launch(null) },
            onGuardar = { n, d, t, c, e, r -> vm?.guardarCambios(context, n, d, t, c, e, r) },
            onEliminar = { showDeleteDialog = true }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar Cliente") },
            text = { Text("¿Estás seguro de que deseas eliminar este cliente? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(onClick = { vm?.eliminarCliente(context); showDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)) {
                    Text("ELIMINAR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("CANCELAR") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditarClienteContent(
    uiState: EditarClienteUiState,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onGuardar: (String, String, String, String, String, String?) -> Unit,
    onEliminar: () -> Unit
) {
    var nombreNegocio by remember(uiState.cliente) { mutableStateOf(TextFieldValue(uiState.cliente?.nombreNegocio ?: "")) }
    var nombreDueno by remember(uiState.cliente) { mutableStateOf(TextFieldValue(uiState.cliente?.nombreDueno ?: "")) }
    var telefono by remember(uiState.cliente) { mutableStateOf(TextFieldValue(uiState.cliente?.telefono ?: "")) }
    var correo by remember(uiState.cliente) { mutableStateOf(TextFieldValue(uiState.cliente?.correo ?: "")) }
    var tipoExhibidor by remember(uiState.cliente) { mutableStateOf(uiState.cliente?.tipoExhibidor ?: "No asignado") }
    var rutaNombreSeleccionado by remember(uiState.cliente) { mutableStateOf(uiState.cliente?.rutaId) }
    
    var expandedExhibidor by remember { mutableStateOf(false) }
    var expandedRuta by remember { mutableStateOf(false) }

    val isLoading = uiState.status is RegistroUiStatus.Loading

    val textoRutaMostrado = uiState.listaRutas.find { it.nombre == rutaNombreSeleccionado }?.nombre ?: rutaNombreSeleccionado ?: "Sin ruta"

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp).shadow(2.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed) }
                    Text("EDITAR CLIENTE", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    Surface(
                        onClick = onEliminar,
                        shape = RoundedCornerShape(12.dp),
                        color = DelisaRed.copy(alpha = 0.1f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = DelisaRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(150.dp).padding(8.dp), contentAlignment = Alignment.BottomEnd) {
                Card(Modifier.fillMaxSize().shadow(4.dp, RoundedCornerShape(24.dp)).clickable { if (!isLoading) onTakePhoto() }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    if (uiState.imageBitmap != null) { Image(bitmap = uiState.imageBitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    else { Box(Modifier.fillMaxSize().background(DelisaRed.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) { Image(painter = painterResource(R.drawable.repartidor), contentDescription = null, modifier = Modifier.size(70.dp)) } }
                }
                Surface(shape = CircleShape, color = if (isLoading) Color.Gray else DelisaRed, shadowElevation = 4.dp, modifier = Modifier.size(36.dp).clickable { if (!isLoading) onTakePhoto() }) { Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.padding(8.dp)) }
            }
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(24.dp)), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModernOutlinedField("Nombre del negocio", nombreNegocio, Icons.Outlined.Storefront, { nombreNegocio = it })
                    ModernOutlinedField("Nombre del dueño", nombreDueno, Icons.Outlined.Person, { nombreDueno = it })
                    ModernOutlinedField("Teléfono", telefono, Icons.Outlined.Phone, { telefono = it }, KeyboardType.Number)
                    ModernOutlinedField("Correo", correo, Icons.Outlined.Email, { correo = it }, KeyboardType.Email)
                    
                    // Selector de Exhibidor
                    ExposedDropdownMenuBox(expanded = expandedExhibidor && !isLoading, onExpandedChange = { expandedExhibidor = !expandedExhibidor }) {
                        OutlinedTextField(
                            value = tipoExhibidor, 
                            onValueChange = {}, 
                            readOnly = true, 
                            label = { Text("Exhibidor") }, 
                            leadingIcon = { Icon(Icons.Outlined.Layers, null, tint = DelisaRed) }, 
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedExhibidor) }, 
                            modifier = Modifier.menuAnchor().fillMaxWidth(), 
                            shape = RoundedCornerShape(16.dp), 
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DelisaRed, 
                                focusedLabelColor = DelisaRed,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        ExposedDropdownMenu(expanded = expandedExhibidor, onDismissRequest = { expandedExhibidor = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            listOf("No asignado", "Mesa", "Normal", "Premium").forEach { opt -> DropdownMenuItem(text = { Text(opt, color = MaterialTheme.colorScheme.onSurface) }, onClick = { tipoExhibidor = opt; expandedExhibidor = false }) }
                        }
                    }

                    // Selector de Ruta
                    ExposedDropdownMenuBox(expanded = expandedRuta && !isLoading, onExpandedChange = { expandedRuta = !expandedRuta }) {
                        OutlinedTextField(
                            value = textoRutaMostrado, 
                            onValueChange = {}, 
                            readOnly = true, 
                            label = { Text("Ruta") }, 
                            leadingIcon = { Icon(Icons.Outlined.Route, null, tint = DelisaRed) }, 
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedRuta) }, 
                            modifier = Modifier.menuAnchor().fillMaxWidth(), 
                            shape = RoundedCornerShape(16.dp), 
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DelisaRed, 
                                focusedLabelColor = DelisaRed,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        ExposedDropdownMenu(expanded = expandedRuta, onDismissRequest = { expandedRuta = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                            uiState.listaRutas.forEach { ruta -> 
                                DropdownMenuItem(
                                    text = { Text(ruta.nombre, color = MaterialTheme.colorScheme.onSurface) }, 
                                    onClick = { 
                                        rutaNombreSeleccionado = ruta.nombre
                                        expandedRuta = false 
                                    }
                                ) 
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (isLoading) { CircularProgressIndicator(color = DelisaRed) }
            else { Button(onClick = { onGuardar(nombreNegocio.text, nombreDueno.text, telefono.text, correo.text, tipoExhibidor, rutaNombreSeleccionado) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)) { Text("GUARDAR CAMBIOS", fontWeight = FontWeight.ExtraBold) } }
        }
    }
}
