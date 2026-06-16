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
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import kotlinx.coroutines.launch

@Composable
fun EditarClienteScreen(navController: NavController, clienteId: String, repository: RepositoryCliente?) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    
    val vm: EditarClienteViewModel? = if (!isPreview && repository != null) {
        viewModel(factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = EditarClienteViewModel(repository, clienteId) as T
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
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.Red) }
    } else {
        EditarClienteContent(
            uiState = uiState,
            onBack = { navController.popBackStack() },
            onTakePhoto = { launcherCamera.launch(null) },
            onGuardar = { n, d, t, c, e -> vm?.guardarCambios(context, n, d, t, c, e) },
            onEliminar = { showDeleteDialog = true }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar Cliente") },
            text = { Text("¿Estás seguro de que deseas eliminar este cliente? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(onClick = { vm?.eliminarCliente(context); showDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
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
    onGuardar: (String, String, String, String, String) -> Unit,
    onEliminar: () -> Unit
) {
    var nombreNegocio by remember(uiState.cliente) { mutableStateOf(TextFieldValue(uiState.cliente?.nombreNegocio ?: "")) }
    var nombreDueno by remember(uiState.cliente) { mutableStateOf(TextFieldValue(uiState.cliente?.nombreDueno ?: "")) }
    var telefono by remember(uiState.cliente) { mutableStateOf(TextFieldValue(uiState.cliente?.telefono ?: "")) }
    var correo by remember(uiState.cliente) { mutableStateOf(TextFieldValue(uiState.cliente?.correo ?: "")) }
    var tipoExhibidor by remember(uiState.cliente) { mutableStateOf(uiState.cliente?.tipoExhibidor ?: "No asignado") }
    var expanded by remember { mutableStateOf(false) }

    val isLoading = uiState.status is RegistroUiStatus.Loading

    Scaffold(containerColor = Color(0xFFF8F9FA)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Red) }
                    Text("EDITAR CLIENTE", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.DarkGray)
                    Surface(
                        onClick = onEliminar,
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Red.copy(alpha = 0.1f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(150.dp).padding(8.dp), contentAlignment = Alignment.BottomEnd) {
                Card(Modifier.fillMaxSize().clickable { if (!isLoading) onTakePhoto() }, shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    if (uiState.imageBitmap != null) { Image(bitmap = uiState.imageBitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    else { Box(Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) { Image(painter = painterResource(R.drawable.repartidor), contentDescription = null, modifier = Modifier.size(70.dp)) } }
                }
                Surface(shape = CircleShape, color = if (isLoading) Color.Gray else Color.Red, shadowElevation = 4.dp, modifier = Modifier.size(36.dp).clickable { if (!isLoading) onTakePhoto() }) { Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.padding(8.dp)) }
            }
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModernOutlinedField("Nombre del negocio", nombreNegocio, Icons.Outlined.Storefront, { nombreNegocio = it })
                    ModernOutlinedField("Nombre del dueño", nombreDueno, Icons.Outlined.Person, { nombreDueno = it })
                    ModernOutlinedField("Teléfono", telefono, Icons.Outlined.Phone, { telefono = it }, KeyboardType.Number)
                    ModernOutlinedField("Correo", correo, Icons.Outlined.Email, { correo = it }, KeyboardType.Email)
                    ExposedDropdownMenuBox(expanded = expanded && !isLoading, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(value = tipoExhibidor, onValueChange = {}, readOnly = true, label = { Text("Exhibidor") }, leadingIcon = { Icon(Icons.Outlined.Layers, null, tint = Color.Red) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Red, focusedLabelColor = Color.Red))
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("No asignado", "Mesa", "Normal", "Premium").forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { tipoExhibidor = opt; expanded = false }) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (isLoading) { CircularProgressIndicator(color = Color.Red) }
            else { Button(onClick = { onGuardar(nombreNegocio.text, nombreDueno.text, telefono.text, correo.text, tipoExhibidor) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("GUARDAR CAMBIOS", fontWeight = FontWeight.ExtraBold) } }
        }
    }
}
