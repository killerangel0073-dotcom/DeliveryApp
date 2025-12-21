@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.gruposanangel.delivery.R
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.*

private val categoriasPorMarca = mapOf(
    "Delisa" to listOf("Botanas", "Dulces"),
    "El Cazador" to listOf("Carnes frías", "Chiles secos")
)

private val subcategoriasPorCategoria = mapOf(
    "Carnes frías" to listOf("Jamón", "Salchicha"),
    "Chiles secos" to listOf("Guajillo", "Pasilla"),
    "Botanas" to listOf("Cacahuates", "Semillas", "Mix"),
    "Dulces" to listOf("Caramelos", "Chocolates", "Gomitas", "Enchilados")
)

@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    modifier: Modifier = Modifier, // <- agrega esto
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}

@Composable
fun CrearProductoScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Campos de texto
    var nombre by remember { mutableStateOf(TextFieldValue("")) }
    var descripcion by remember { mutableStateOf(TextFieldValue("")) }
    var precio by remember { mutableStateOf(TextFieldValue("")) }

    // Dropdowns
    var marca by rememberSaveable { mutableStateOf("") }
    var categoria by rememberSaveable { mutableStateOf("") }
    var subcategoria by rememberSaveable { mutableStateOf("") }

    // Imagen
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageFile by remember { mutableStateOf<File?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    // Estado UI
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Launchers
    val launcherGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                val file = createImageFile2(context)
                context.contentResolver.openInputStream(it)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                imageFile = file
                imageBitmap = BitmapFactory.decodeFile(file.absolutePath)
            }
        }
    }

    val launcherCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        bmp?.let {
            val file = createImageFile2(context)
            FileOutputStream(file).use { out -> it.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            imageFile = file
            imageBitmap = it
        }
    }

    Scaffold(
        topBar = {
            Box(Modifier.fillMaxWidth()) {
                Text("Agregar Producto", modifier = Modifier.align(Alignment.Center), fontSize = 20.sp)
                IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                }
            }
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Imagen
            val placeholder = painterResource(R.drawable.repartidor)
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .clickable { showDialog = true }
            ) {
                if (imageBitmap != null) Image(
                    bitmap = imageBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                ) else Image(
                    painter = placeholder,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                ModernField("Nombre", nombre) { nombre = it }

                // Marca
                DropdownField(
                    label = "Marca",
                    value = marca.ifEmpty { "Selecciona marca" },
                    options = listOf("Delisa", "El Cazador"),
                    modifier = Modifier.width(200.dp)
                ) {
                    marca = it
                    // Reset categoría y subcategoría
                    val categoriasSafe = categoriasPorMarca[it].orEmpty()
                    categoria = ""
                    subcategoria = ""
                }

                // Categoría
                val categoriasSafe = categoriasPorMarca[marca].orEmpty()
                DropdownField(
                    label = "Categoría",
                    value = if (categoria.isEmpty()) "Selecciona categoría" else categoria,
                    options = if (categoriasSafe.isEmpty()) listOf("Sin categorías") else categoriasSafe,
                    enabled = marca.isNotEmpty(),
                    modifier = Modifier.width(200.dp)
                ) {
                    categoria = it
                    val subcategoriasSafe = subcategoriasPorCategoria[it].orEmpty()
                    subcategoria = ""
                }

                // Subcategoría
                val subcategoriasSafe = subcategoriasPorCategoria[categoria].orEmpty()
                DropdownField(
                    label = "Subcategoría",
                    value = if (subcategoria.isEmpty()) "Selecciona subcategoría" else subcategoria,
                    options = if (subcategoriasSafe.isEmpty()) listOf("Sin subcategoría") else subcategoriasSafe,
                    enabled = categoria.isNotEmpty(),
                    modifier = Modifier.width(200.dp)
                ) {
                    subcategoria = it
                }

                ModernField("Descripción", descripcion, maxLines = 3) { descripcion = it }

                ModernField("Precio", precio, keyboardType = KeyboardType.Number) {
                    if (it.text.all { c -> c.isDigit() || c == '.' }) precio = it
                }
            }

            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(initialAlpha = 0f),
                exit = fadeOut()
            ) {
                errorMessage?.let { Text(it, color = Color.Red, fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(16.dp))
            if (isLoading) CircularProgressIndicator()

            Button(
                onClick = {
                    fun error(msg: String) { errorMessage = msg; scope.launch { delay(1500); errorMessage = null } }
                    val precioDouble = precio.text.toDoubleOrNull()
                    if (imageFile == null) return@Button error("Imagen requerida")
                    if (nombre.text.isBlank()) return@Button error("Nombre requerido")
                    if (categoria.isBlank()) return@Button error("Categoría requerida")
                    if (precioDouble == null) return@Button error("Precio inválido")

                    scope.launch {
                        isLoading = true
                        try {
                            val storage = FirebaseStorage.getInstance()
                            val ref = storage.reference.child("productos/${UUID.randomUUID()}.jpg")
                            val fileUri = Uri.fromFile(imageFile!!)
                            ref.putFile(fileUri).await()
                            val imageUrl = ref.downloadUrl.await().toString()

                            val productoData = hashMapOf(
                                "nombre" to nombre.text,
                                "marca" to marca,
                                "categoria" to categoria,
                                "subcategoria" to subcategoria,
                                "descripcion" to descripcion.text,
                                "precio" to precioDouble,
                                "imagenUrl" to imageUrl,
                                "activo" to true
                            )

                            FirebaseFirestore.getInstance().collection("producto").add(productoData).await()
                            Toast.makeText(context, "Producto creado", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        } catch (e: Exception) {
                            error("Error al guardar producto")
                        } finally {
                            isLoading = false
                        }
                    }

                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))
            ) {
                Text("Guardar Producto", fontSize = 18.sp, color = Color.White)
            }
        }
    }

    // Dialog para elegir imagen
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Imagen del producto") },
            confirmButton = {
                Column {
                    Button(onClick = { launcherCamera.launch(null); showDialog = false }) { Text("Cámara") }
                    Button(onClick = { launcherGallery.launch("image/*"); showDialog = false }) { Text("Galería") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        )
    }
}








@Composable
fun ModernField(
    label: String,
    value: TextFieldValue,
    maxLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (TextFieldValue) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

fun createImageFile2(context: android.content.Context): File {
    val dir = File(context.filesDir, "productos")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "producto_${System.currentTimeMillis()}.jpg")
}

@Preview(showBackground = true)
@Composable
fun CrearProductoPreview() {
    MaterialTheme {
        CrearProductoScreen(navController = rememberNavController())
    }
}
