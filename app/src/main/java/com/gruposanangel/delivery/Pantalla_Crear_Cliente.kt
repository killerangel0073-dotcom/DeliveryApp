@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.RepositoryCliente
import kotlinx.coroutines.*

@Composable
fun CrearClienteScreen(
    navController: NavController?,
    repository: RepositoryCliente,
    viewModel: RegistroClienteViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return RegistroClienteViewModel(repository) as T
            }
        }
    )
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    // Estados de los campos (se mantienen en UI para preservar el cursor/selección suave)
    var nombreNegocio by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var nombreDueno by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var telefono by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var correo by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var tipoExhibidor by rememberSaveable { mutableStateOf("Elige una opción") }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val opcionesExibidor = listOf("No asignado", "Exhibidor de Mesa", "Exhibidor Normal", "Exhibidor Premium")
    var showDialog by remember { mutableStateOf(false) }

    val isLoading = uiState.status is RegistroUiStatus.Loading

    // Launchers
    val launcherGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val (file, bmp) = viewModel.processUri(context, it)
                    viewModel.onImageSelected(file, bmp)
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al cargar imagen", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val launcherCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        bmp?.let {
            val file = viewModel.createImageFile(context)
            viewModel.saveBitmap(it, file)
            viewModel.onImageSelected(file, it)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.fetchInitialLocation(context)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Navegación ante éxito
    LaunchedEffect(uiState.status) {
        if (uiState.status is RegistroUiStatus.Success) {
            Toast.makeText(context, "Cliente registrado localmente y en proceso de sincronización", Toast.LENGTH_LONG).show()
            navController?.navigate("delivery?screen=Clientes") {
                popUpTo("delivery") { inclusive = true }
            }
        }
    }

    // Bloqueo de navegación física hacia atrás durante carga
    androidx.activity.compose.BackHandler(enabled = isLoading) { }

    Scaffold(
        topBar = {
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                Text(
                    "Crear Nuevo Cliente",
                    modifier = Modifier.align(Alignment.Center),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                if (!isLoading) {
                    IconButton(
                        onClick = { navController?.popBackStack() },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                }
            }
        },
        containerColor = Color.White
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Foto del cliente
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isLoading) { showDialog = true }
            ) {
                if (uiState.imageBitmap != null) {
                    Image(
                        bitmap = uiState.imageBitmap!!.asImageBitmap(),
                        contentDescription = "Foto",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.repartidor),
                        contentDescription = "Placeholder",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                ModernOutlinedField(
                    "Nombre del negocio",
                    nombreNegocio,
                    onValueChange = { if (!isLoading) nombreNegocio = nombreNegocio.capitalizeWordsWithCursor(it) },
                    readOnly = isLoading
                )
                ModernOutlinedField(
                    "Nombre del dueño",
                    nombreDueno,
                    onValueChange = { if (!isLoading) nombreDueno = nombreDueno.capitalizeWordsWithCursor(it) },
                    readOnly = isLoading
                )
                ModernOutlinedField(
                    "Teléfono",
                    telefono,
                    keyboardType = KeyboardType.Number,
                    onValueChange = { if (!isLoading && it.text.all { ch -> ch.isDigit() }) telefono = it },
                    readOnly = isLoading
                )
                ModernOutlinedField(
                    "Correo (Opcional)",
                    correo,
                    keyboardType = KeyboardType.Email,
                    onValueChange = { if (!isLoading) correo = it },
                    readOnly = isLoading
                )

                ExposedDropdownMenuBox(
                    expanded = expanded && !isLoading,
                    onExpandedChange = { if (!isLoading) expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = tipoExhibidor,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de exhibidor", color = if (tipoExhibidor != "Elige una opción") Color.Red else Color.Gray) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color.Red,
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color.Red,
                            focusedLabelColor = Color.Red
                        ),
                        textStyle = TextStyle(fontSize = 18.sp),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        opcionesExibidor.forEach { opcion ->
                            DropdownMenuItem(text = { Text(opcion) }, onClick = {
                                tipoExhibidor = opcion
                                expanded = false
                            })
                        }
                    }
                }

                ModernOutlinedField(
                    "Ubicación detectada",
                    TextFieldValue(uiState.ubicacionTexto),
                    onValueChange = { },
                    readOnly = true
                )
            }

            Spacer(Modifier.height(20.dp))

            // Feedback de error
            if (uiState.status is RegistroUiStatus.Error) {
                Text(
                    text = (uiState.status as RegistroUiStatus.Error).message,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
            }

            if (isLoading) {
                CircularProgressIndicator(color = Color.Red)
                Spacer(Modifier.height(20.dp))
            }

            Button(
                onClick = {
                    viewModel.guardarCliente(
                        context,
                        nombreNegocio.text,
                        nombreDueno.text,
                        telefono.text,
                        correo.text,
                        tipoExhibidor
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF0000),
                    disabledContainerColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isLoading) "Procesando..." else "Guardar Cliente", fontSize = 18.sp)
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Foto del Cliente") },
            text = { Text("¿Cómo deseas obtener la imagen?") },
            confirmButton = {
                Column(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { launcherCamera.launch(null); showDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("Cámara") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { launcherGallery.launch("image/*"); showDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("Galería") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar", color = Color.Red) }
            }
        )
    }
}

@Composable
fun ModernOutlinedField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLines: Int = 1,
    readOnly: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = if (value.text.isNotBlank()) Color.Red else Color.Gray) },
        singleLine = maxLines == 1,
        maxLines = maxLines,
        readOnly = readOnly,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = Color.Red,
            unfocusedBorderColor = Color.Gray,
            cursorColor = Color.Red,
            focusedLabelColor = Color.Red
        ),
        textStyle = TextStyle(fontSize = 18.sp),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

fun TextFieldValue.capitalizeWordsWithCursor(newValue: TextFieldValue): TextFieldValue {
    val words = newValue.text.split(" ").map { word ->
        if (word.isNotEmpty()) word.replaceFirstChar { it.uppercaseChar() } else word
    }.joinToString(" ")
    val cursor = newValue.selection.start.coerceIn(0, words.length)
    return TextFieldValue(words, selection = androidx.compose.ui.text.TextRange(cursor))
}
