package com.gruposanangel.delivery.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import kotlinx.coroutines.launch

@Composable
fun CrearClienteScreen(navController: NavController, repository: RepositoryCliente?) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    
    val db = AppDatabase.getDatabase(context)
    val firebaseDataSource = FirebaseDataSource()
    val repoUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())

    val vm: RegistroClienteViewModel? = if (!isPreview && repository != null) {
        viewModel(factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RegistroClienteViewModel(repository, repoUsuario) as T
        })
    } else null

    val uiState by vm?.uiState?.collectAsState() ?: remember { mutableStateOf(RegistroUiState()) }

    // 🔥 NUEVO: Disparar la obtención de ubicación al entrar
    LaunchedEffect(Unit) {
        vm?.fetchInitialLocation(context)
    }

    val launcherCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> bmp?.let { vm?.let { v -> val file = v.createImageFile(context); v.saveBitmap(it, file); v.onImageSelected(file, it) } } }

    LaunchedEffect(uiState.status) {
        when (val status = uiState.status) {
            is RegistroUiStatus.Success -> {
                Toast.makeText(context, "Cliente registrado exitosamente", Toast.LENGTH_SHORT).show()
                // Navegar a la pantalla de ventas con el ID del nuevo cliente
                navController.navigate("pantalla_ventas/${status.clienteId}") {
                    // Opcional: Limpiar la pantalla de creación del historial para que al dar atrás no regrese aquí
                    popUpTo("crear_cliente") { inclusive = true }
                }
            }
            is RegistroUiStatus.Error -> {
                Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                vm?.resetStatus()
            }
            else -> {}
        }
    }

    CrearClienteContent(
        uiState = uiState,
        onBack = { navController.popBackStack() },
        onTakePhoto = { launcherCamera.launch(null) },
        onGuardar = { n, d, t, c, e -> vm?.guardarCliente(context, n, d, t, c, e) },
        onRetryLocation = { vm?.fetchInitialLocation(context) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearClienteContent(
    uiState: RegistroUiState,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onGuardar: (String, String, String, String, String) -> Unit,
    onRetryLocation: () -> Unit
) {
    var nombreNegocio by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var nombreDueno by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var telefono by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var correo by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var tipoExhibidor by rememberSaveable { mutableStateOf("Selecciona Exhibidor") }
    var expanded by remember { mutableStateOf(false) }

    val isLoading = uiState.status is RegistroUiStatus.Loading

    fun formatAsTitleCase(text: String): String {
        if (text.isBlank()) return ""
        val minorWords = listOf("el", "la", "los", "las", "de", "del", "y", "en", "con")
        val words = text.split("\\s+".toRegex())
        
        return words.mapIndexed { index, word ->
            if (word.isBlank()) return@mapIndexed ""
            val lowerWord = word.lowercase()
            if (lowerWord.contains(".")) {
                lowerWord.uppercase()
            } else if (index > 0 && lowerWord in minorWords) {
                lowerWord
            } else {
                lowerWord.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            }
        }.joinToString(" ")
    }

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
                Card(Modifier.fillMaxSize().clickable { if (!isLoading) onTakePhoto() }, shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    if (uiState.imageBitmap != null) { Image(bitmap = uiState.imageBitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    else { Box(Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) { Image(painter = painterResource(R.drawable.repartidor), contentDescription = null, modifier = Modifier.size(70.dp)) } }
                }
                Surface(shape = CircleShape, color = if (isLoading) Color.Gray else Color.Red, shadowElevation = 4.dp, modifier = Modifier.size(36.dp).clickable { if (!isLoading) onTakePhoto() }) { Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.padding(8.dp)) }
            }
            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModernOutlinedField("Nombre del negocio", nombreNegocio, Icons.Outlined.Storefront, { 
                        val formatted = formatAsTitleCase(it.text)
                        nombreNegocio = it.copy(text = formatted)
                    })
                    ModernOutlinedField("Nombre del dueño", nombreDueno, Icons.Outlined.Person, { 
                        val formatted = formatAsTitleCase(it.text)
                        nombreDueno = it.copy(text = formatted)
                    })
                    ModernOutlinedField("Teléfono", telefono, Icons.Outlined.Phone, { telefono = it }, KeyboardType.Number)
                    ModernOutlinedField("Correo", correo, Icons.Outlined.Email, { correo = it }, KeyboardType.Email)
                    ExposedDropdownMenuBox(expanded = expanded && !isLoading, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(value = tipoExhibidor, onValueChange = {}, readOnly = true, label = { Text("Exhibidor") }, leadingIcon = { Icon(Icons.Outlined.Layers, null, tint = Color.Red) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Red, focusedLabelColor = Color.Red, unfocusedBorderColor = if (tipoExhibidor == "Selecciona Exhibidor") Color.Red.copy(alpha = 0.5f) else Color.Gray))
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("No asignado", "Mesa", "Normal", "Premium").forEach { opt -> DropdownMenuItem(text = { Text(opt) }, onClick = { tipoExhibidor = opt; expanded = false }) }
                        }
                    }
                    
                    if (!uiState.isGmsAvailable) {
                        // 🔥 TARJETA DE CAPTURA NATIVA (Diseño Especial para Huawei)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F4)),
                            border = BorderStroke(1.dp, Color.Red.copy(0.2f))
                        ) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Outlined.GpsFixed, null, tint = Color.Red, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("MODO CAPTURA NATIVA", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                                        Text(if (uiState.ubicacionValida) "GPS Conectado" else "Buscando satélites...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (uiState.ubicacionValida) Color(0xFF2E7D32) else Color.Red)
                                    }
                                }
                                
                                Spacer(Modifier.height(12.dp))
                                
                                Text(
                                    text = uiState.ubicacionTexto,
                                    fontSize = 12.sp,
                                    color = Color.DarkGray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                )
                                
                                Spacer(Modifier.height(16.dp))
                                
                                Button(
                                    onClick = onRetryLocation,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Red),
                                    elevation = ButtonDefaults.buttonElevation(2.dp),
                                    border = BorderStroke(1.dp, Color.Red)
                                ) {
                                    Icon(Icons.Outlined.GpsFixed, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("OBTENER UBICACIÓN GPS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        // DISEÑO ESTÁNDAR PARA DISPOSITIVOS CON GOOGLE
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            ModernOutlinedField(
                                label = "Ubicación GPS", 
                                value = TextFieldValue(uiState.ubicacionTexto), 
                                icon = Icons.Outlined.GpsFixed, 
                                onValueChange = {}, 
                                readOnly = true
                            )
                            if (!uiState.ubicacionValida && !isLoading) {
                                IconButton(
                                    onClick = onRetryLocation,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(Icons.Outlined.Refresh, "Reintentar", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            if (isLoading) { CircularProgressIndicator(color = Color.Red) }
            else { Button(onClick = { onGuardar(nombreNegocio.text, nombreDueno.text, telefono.text, correo.text, tipoExhibidor) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("GUARDAR CLIENTE", fontWeight = FontWeight.ExtraBold) } }
        }
    }
}

@Composable
fun ModernOutlinedField(label: String, value: TextFieldValue, icon: ImageVector, onValueChange: (TextFieldValue) -> Unit, keyboardType: KeyboardType = KeyboardType.Text, maxLines: Int = 1, readOnly: Boolean = false) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, leadingIcon = { Icon(icon, null, tint = if (readOnly) Color.Gray else Color.Red) }, singleLine = maxLines == 1, maxLines = maxLines, readOnly = readOnly, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Red, focusedLabelColor = Color.Red, disabledBorderColor = Color(0xFFEEEEEE), disabledTextColor = Color.DarkGray, disabledLabelColor = Color.Gray), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
}

@Preview(showBackground = true, showSystemUi = true, name = "Crear Cliente - Formulario")
@Composable
fun CrearClientePreview() {
    DeliveryTheme { CrearClienteContent(RegistroUiState(), {}, {}, {_,_,_,_,_ ->}, {}) }
}

@Preview(showBackground = true, showSystemUi = true, name = "Crear Cliente - Cargando")
@Composable
fun CrearClienteLoadingPreview() {
    DeliveryTheme { CrearClienteContent(RegistroUiState(status = RegistroUiStatus.Loading), {}, {}, {_,_,_,_,_ ->}, {}) }
}
