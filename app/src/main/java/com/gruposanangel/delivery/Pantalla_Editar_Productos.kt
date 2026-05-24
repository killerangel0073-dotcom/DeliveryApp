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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
fun DropdownFieldeditar(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
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
fun ModernFieldeditar(
    label: String,
    value: androidx.compose.ui.text.input.TextFieldValue,
    maxLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit
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

fun createImageFileeditar(context: android.content.Context): File {
    val dir = File(context.filesDir, "productos")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "producto_${System.currentTimeMillis()}.jpg")
}

@Composable
fun EditarProductoScreen(
    navController: NavController,
    productoId: String? = null,
    previewProducto: ProductoFirestoreeditar? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()

    // Campos de texto
    var nombre by remember { mutableStateOf(TextFieldValue(previewProducto?.nombre ?: "")) }
    var descripcion by remember { mutableStateOf(TextFieldValue(previewProducto?.descripcion ?: "")) }
    var precio by remember { mutableStateOf(TextFieldValue(previewProducto?.precio?.toString() ?: "")) }

    // Dropdowns
    var marca by rememberSaveable { mutableStateOf(previewProducto?.marca ?: "") }
    var categoria by rememberSaveable { mutableStateOf(previewProducto?.categoria ?: "") }
    var subcategoria by rememberSaveable { mutableStateOf(previewProducto?.subcategoria ?: "") }

    // Imagen
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageFile by remember { mutableStateOf<File?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    // Estado UI
    var isLoading by remember { mutableStateOf(previewProducto != null) }
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

    // Cargar producto desde Firestore si no es Preview
    LaunchedEffect(productoId) {
        if (productoId != null && previewProducto == null) {
            try {
                val doc = db.collection("producto").document(productoId).get().await()
                if (doc.exists()) {
                    nombre = TextFieldValue(doc.getString("nombre") ?: "")
                    descripcion = TextFieldValue(doc.getString("descripcion") ?: "")
                    precio = TextFieldValue(doc.getDouble("precio")?.toString() ?: "")
                    marca = doc.getString("marca") ?: ""
                    categoria = doc.getString("categoria") ?: ""
                    subcategoria = doc.getString("subcategoria") ?: ""

                    val imageUrl = doc.getString("imagenUrl")
                    imageUrl?.let {
                        val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(it)
                        val localFile = createImageFile2(context)
                        storageRef.getFile(localFile).await()
                        imageFile = localFile
                        imageBitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al cargar producto", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        } else {
            isLoading = false
            previewProducto?.imagenUrl?.let {
                // para Preview se puede dejar vacío o usar placeholder
            }
        }
    }

    Scaffold(
        topBar = {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = if (previewProducto != null) "Editar Producto" else "Editar Producto",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 20.sp
                )
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
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }

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
                errorMessage?.let { Text(it, color = Color.Red) }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    fun error(msg: String) { errorMessage = msg; scope.launch { delay(1500); errorMessage = null } }
                    val precioDouble = precio.text.toDoubleOrNull()
                    if (nombre.text.isBlank()) return@Button error("Nombre requerido")
                    if (categoria.isBlank()) return@Button error("Categoría requerida")
                    if (precioDouble == null) return@Button error("Precio inválido")

                    scope.launch {
                        isLoading = true
                        try {
                            val storage = FirebaseStorage.getInstance()
                            val imageUrl = if (imageFile != null) {
                                val ref = storage.reference.child("productos/${UUID.randomUUID()}.jpg")
                                val fileUri = Uri.fromFile(imageFile!!)
                                ref.putFile(fileUri).await()
                                ref.downloadUrl.await().toString()
                            } else null

                            val updateData = mutableMapOf<String, Any>(
                                "nombre" to nombre.text,
                                "marca" to marca,
                                "categoria" to categoria,
                                "subcategoria" to subcategoria,
                                "descripcion" to descripcion.text,
                                "precio" to precioDouble
                            )
                            imageUrl?.let { updateData["imagenUrl"] = it }

                            productoId?.let {
                                db.collection("producto").document(it).update(updateData).await()
                                Toast.makeText(context, "Producto actualizado", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            } ?: run {
                                // Solo para Preview: mostrar Toast
                                Toast.makeText(context, "Producto actualizado (Preview)", Toast.LENGTH_SHORT).show()
                            }

                        } catch (e: Exception) {
                            error("Error al actualizar producto")
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
                Text("Guardar Cambios", fontSize = 18.sp, color = Color.White)
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

data class ProductoFirestoreeditar(
    val id: String,
    val nombre: String,
    val marca: String,
    val categoria: String,
    val subcategoria: String,
    val descripcion: String,
    val precio: Double,
    val imagenUrl: String
)

@Preview(showBackground = true)
@Composable
fun EditarProductoPreview() {
    val productoEjemplo = ProductoFirestoreeditar(
        id = "1",
        nombre = "Botana X",
        marca = "Delisa",
        categoria = "Botanas",
        subcategoria = "Cacahuates",
        descripcion = "Botana deliciosa",
        precio = 12.5,
        imagenUrl = ""
    )
    EditarProductoScreen(navController = rememberNavController(), previewProducto = productoEjemplo)

}
