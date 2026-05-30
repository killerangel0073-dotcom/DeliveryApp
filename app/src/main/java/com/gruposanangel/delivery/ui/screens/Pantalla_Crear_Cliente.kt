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
fun CrearClienteScreen(navController: NavController, repository: RepositoryCliente?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current
    
    val vm: RegistroClienteViewModel? = if (!isPreview && repository != null) {
        viewModel(factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RegistroClienteViewModel(repository) as T
        })
    } else null

    val uiState by vm?.uiState?.collectAsState() ?: remember { mutableStateOf(RegistroUiState()) }

    val launcherGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { try { vm?.let { v -> val (file, bmp) = v.processUri(context, it); v.onImageSelected(file, bmp) } } catch (e: Exception) { } } } }
    val launcherCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> bmp?.let { vm?.let { v -> val file = v.createImageFile(context); v.saveBitmap(it, file); v.onImageSelected(file, it) } } }

    LaunchedEffect(uiState.status) { if (uiState.status is RegistroUiStatus.Success) { Toast.makeText(context, "Cliente registrado", Toast.LENGTH_SHORT).show(); navController.popBackStack() } }

    CrearClienteContent(
        uiState = uiState,
        onBack = { navController.popBackStack() },
        onImageSourceSelected = { isCamera -> if (isCamera) launcherCamera.launch(null) else launcherGallery.launch("image/*") },
        onGuardar = { n, d, t, c, e -> vm?.guardarCliente(context, n, d, t, c, e) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearClienteContent(
    uiState: RegistroUiState,
    onBack: () -> Unit,
    onImageSourceSelected: (Boolean) -> Unit,
    onGuardar: (String, String, String, String, String) -> Unit
) {
    var nombreNegocio by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var nombreDueno by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var telefono by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var correo by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var tipoExhibidor by rememberSaveable { mutableStateOf("No asignado") }
    var expanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    val isLoading = uiState.status is RegistroUiStatus.Loading

    Scaffold(containerColor = Color(0xFFF8F9FA)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Red) }
                    Text("NUEVO CLIENTE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.DarkGray)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(150.dp).padding(8.dp), contentAlignment = Alignment.BottomEnd) {
                Card(Modifier.fillMaxSize().clickable { showDialog = true }, shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    if (uiState.imageBitmap != null) { Image(bitmap = uiState.imageBitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    else { Box(Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) { Image(painter = painterResource(R.drawable.repartidor), contentDescription = null, modifier = Modifier.size(70.dp)) } }
                }
                Surface(shape = CircleShape, color = if (isLoading) Color.Gray else Color.Red, shadowElevation = 4.dp, modifier = Modifier.size(36.dp).clickable { showDialog = true }) { Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.padding(8.dp)) }
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
                    ModernOutlinedField("Ubicación GPS", TextFieldValue(uiState.ubicacionTexto), Icons.Outlined.GpsFixed, {}, readOnly = true)
                }
            }
            Spacer(Modifier.height(24.dp))
            if (isLoading) { CircularProgressIndicator(color = Color.Red) }
            else { Button(onClick = { onGuardar(nombreNegocio.text, nombreDueno.text, telefono.text, correo.text, tipoExhibidor) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("GUARDAR CLIENTE", fontWeight = FontWeight.ExtraBold) } }
        }
    }

    if (showDialog) {
        AlertDialog(onDismissRequest = { showDialog = false }, containerColor = Color.White, title = { Text("Foto", fontWeight = FontWeight.Black) }, text = { Text("Origen de la foto") },
            confirmButton = { Row {
                TextButton(onClick = { onImageSourceSelected(true); showDialog = false }) { Text("CÁMARA", color = Color.Red) }
                TextButton(onClick = { onImageSourceSelected(false); showDialog = false }) { Text("GALERÍA", color = Color.Red) }
            } }
        )
    }
}

@Composable
fun ModernOutlinedField(label: String, value: TextFieldValue, icon: ImageVector, onValueChange: (TextFieldValue) -> Unit, keyboardType: KeyboardType = KeyboardType.Text, maxLines: Int = 1, readOnly: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, leadingIcon = { Icon(icon, null, tint = if (readOnly) Color.Gray else Color.Red) }, singleLine = maxLines == 1, maxLines = maxLines, readOnly = readOnly, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Red, focusedLabelColor = Color.Red, disabledBorderColor = Color(0xFFEEEEEE), disabledTextColor = Color.DarkGray, disabledLabelColor = Color.Gray), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
}

@Preview(showBackground = true, showSystemUi = true, name = "Crear Cliente - Formulario")
@Composable
fun CrearClientePreview() {
    DeliveryTheme { CrearClienteContent(RegistroUiState(), {}, {}, {_,_,_,_,_ ->}) }
}

@Preview(showBackground = true, showSystemUi = true, name = "Crear Cliente - Cargando")
@Composable
fun CrearClienteLoadingPreview() {
    DeliveryTheme { CrearClienteContent(RegistroUiState(status = RegistroUiStatus.Loading), {}, {}, {_,_,_,_,_ ->}) }
}
