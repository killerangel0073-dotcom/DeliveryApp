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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.text.style.TextAlign
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

// 🔹 MODELO UNIFICADO EXCLUSIVO PARA ESTA PANTALLA
data class ProductoEditarModelo(
    val id: String,
    val nombre: String,
    val marca: String,
    val categoria: String,
    val subcategoria: String,
    val descripcion: String,
    val precio: Double,
    val imagenUrl: String,
    val cantidadUnitario: String = "",
    val unidadesPorDisplay: String = "",
    val gramosVenta: Long? = null,
    val precioCompra: Double = 0.0
)

@Composable
fun DropdownFieldEditar(
    label: String,
    value: String,
    options: List<String>,
    icon: ImageVector,
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
            leadingIcon = { Icon(icon, null, tint = if (enabled) Color.Red else Color.Gray) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Red,
                focusedLabelColor = Color.Red,
                disabledBorderColor = Color(0xFFEEEEEE),
                disabledLabelColor = Color.LightGray
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontWeight = FontWeight.Medium) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
fun ModernFieldEditar(
    label: String,
    value: TextFieldValue,
    icon: ImageVector,
    maxLines: Int = 1,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (TextFieldValue) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, null, tint = Color.Red) },
        prefix = if (prefix != null) { { Text(prefix) } } else null,
        maxLines = maxLines,
        singleLine = maxLines == 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Red,
            focusedLabelColor = Color.Red
        )
    )
}

fun createImageFileEditar(context: android.content.Context): File {
    val dir = File(context.filesDir, "productos")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "producto_${System.currentTimeMillis()}.jpg")
}

@Composable
fun EditarProductoScreen(
    navController: NavController,
    productoId: String? = null,
    previewProducto: ProductoEditarModelo? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current

    // Campos de texto dinámicos vinculados
    var nombre by remember { mutableStateOf(TextFieldValue(previewProducto?.nombre ?: "")) }
    var descripcion by remember { mutableStateOf(TextFieldValue(previewProducto?.descripcion ?: "")) }
    var precio by remember { mutableStateOf(TextFieldValue(previewProducto?.precio?.toString() ?: "")) }

    // Dropdowns vinculados
    var marca by rememberSaveable { mutableStateOf(previewProducto?.marca ?: "") }
    var categoria by rememberSaveable { mutableStateOf(previewProducto?.categoria ?: "") }
    var subcategoria by rememberSaveable { mutableStateOf(previewProducto?.subcategoria ?: "") }

    var cantidadUnitario by remember { mutableStateOf(TextFieldValue(previewProducto?.cantidadUnitario ?: "")) }
    var unidadesPorDisplay by remember { mutableStateOf(TextFieldValue(previewProducto?.unidadesPorDisplay ?: "")) }
    var gramosVenta by remember { mutableStateOf(TextFieldValue(previewProducto?.gramosVenta?.toString() ?: "")) }
    var precioCompra by remember { mutableStateOf(TextFieldValue(previewProducto?.precioCompra?.toString() ?: "")) }

    // Imagen vinculada
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageFile by remember { mutableStateOf<File?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    // Estado UI inteligente
    var isLoading by remember { mutableStateOf(!isPreview && productoId != null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Lanzadores de imágenes
    val launcherGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                val file = createImageFileEditar(context)
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
            val file = createImageFileEditar(context)
            FileOutputStream(file).use { out -> it.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            imageFile = file
            imageBitmap = it
        }
    }

    // Carga de Firestore controlada para que NO se ejecute en el Preview
    LaunchedEffect(productoId) {
        if (!isPreview && productoId != null && previewProducto == null) {
            try {
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("producto").document(productoId).get().await()
                if (doc.exists()) {
                    nombre = TextFieldValue(doc.getString("nombre") ?: "")
                    descripcion = TextFieldValue(doc.getString("descripcion") ?: "")
                    precio = TextFieldValue(doc.getDouble("precio")?.toString() ?: "")
                    marca = doc.getString("marca") ?: ""
                    categoria = doc.getString("categoria") ?: ""
                    subcategoria = doc.getString("subcategoria") ?: ""
                    cantidadUnitario = TextFieldValue(doc.get("cantidadUnitario")?.toString() ?: "")
                    unidadesPorDisplay = TextFieldValue(doc.get("unidadesPorDisplay")?.toString() ?: "")
                    gramosVenta = TextFieldValue(doc.get("gramosVenta")?.toString() ?: "")
                    precioCompra = TextFieldValue(doc.get("precioCompra")?.toString() ?: "")

                    val imageUrl = doc.getString("imagenUrl")
                    if (!imageUrl.isNullOrEmpty()) {
                        try {
                            val storageRef = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)
                            val localFile = createImageFileEditar(context)
                            storageRef.getFile(localFile).await()
                            imageFile = localFile
                            imageBitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al cargar producto", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8F9FA)
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // CABECERA INTEGRADA PREMIUM
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = Color.Red)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (productoId == null && previewProducto == null) "NUEVO PRODUCTO" else "EDITAR PRODUCTO",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.DarkGray,
                        fontSize = 15.sp
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.Red)
                }
                return@Column
            }

            Spacer(Modifier.height(16.dp))

            // CONTENEDOR DE FOTO DINÁMICO
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showDialog = true },
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.repartidor),
                                contentDescription = null,
                                modifier = Modifier.size(70.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Red,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { showDialog = true }
                ) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        null,
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // FORMULARIO CON ICONOS OUTLINED MODERNOS
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Información General",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.DarkGray
                    )

                    ModernFieldEditar("Nombre del Producto", nombre, Icons.Outlined.Label) { nombre = it }

                    DropdownFieldEditar(
                        label = "Marca",
                        value = marca.ifEmpty { "Selecciona marca" },
                        options = listOf("Delisa", "El Cazador"),
                        icon = Icons.Outlined.Bookmark
                    ) {
                        marca = it
                        categoria = ""
                        subcategoria = ""
                    }

                    val categoriasSafe = categoriasPorMarca[marca].orEmpty()
                    DropdownFieldEditar(
                        label = "Categoría",
                        value = if (categoria.isEmpty()) "Selecciona categoría" else categoria,
                        options = if (categoriasSafe.isEmpty()) listOf("Sin categorías") else java.util.ArrayList(categoriasSafe),
                        enabled = marca.isNotEmpty(),
                        icon = Icons.Outlined.Category
                    ) {
                        categoria = it
                        subcategoria = ""
                    }

                    val subcategoriasSafe = subcategoriasPorCategoria[categoria].orEmpty()
                    DropdownFieldEditar(
                        label = "Subcategoría",
                        value = if (subcategoria.isEmpty()) "Selecciona subcategoría" else subcategoria,
                        options = if (subcategoriasSafe.isEmpty()) listOf("Sin subcategoría") else java.util.ArrayList(subcategoriasSafe),
                        enabled = categoria.isNotEmpty(),
                        icon = Icons.Outlined.Layers
                    ) {
                        subcategoria = it
                    }

                    ModernFieldEditar("Descripción", descripcion, Icons.Outlined.Description, maxLines = 3) { descripcion = it }

                    Text(
                        "CONFIGURACIÓN DE COMPRA",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.Red,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            ModernFieldEditar("Gramos Bolsa", cantidadUnitario, Icons.Outlined.Scale, keyboardType = KeyboardType.Number) {
                                if (it.text.all { c -> c.isDigit() }) cantidadUnitario = it
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ModernFieldEditar("Display", unidadesPorDisplay, Icons.Outlined.Inventory2, keyboardType = KeyboardType.Number) {
                                if (it.text.all { c -> c.isDigit() }) unidadesPorDisplay = it 
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            ModernFieldEditar("Precio Compra", precioCompra, Icons.Outlined.ShoppingBag, keyboardType = KeyboardType.Number, prefix = "$") {
                                if (it.text.all { c -> c.isDigit() || c == '.' }) precioCompra = it
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ModernFieldEditar("Gramos Bolsita", gramosVenta, Icons.Outlined.ShoppingBag, keyboardType = KeyboardType.Number) {
                                if (it.text.all { c -> c.isDigit() }) gramosVenta = it
                            }
                        }
                    }
                    
                    Text("Solo números: Ej. 900, 20, 45", fontSize = 10.sp, color = Color.Gray)

                    ModernFieldEditar("Precio al Público", precio, Icons.Outlined.AttachMoney, keyboardType = KeyboardType.Number, prefix = "$") {
                        if (it.text.all { c -> c.isDigit() || c == '.' }) precio = it
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                errorMessage?.let {
                    Text(
                        it,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // BOTÓN PRINCIPAL CORPORATIVO
            Button(
                onClick = {
                    fun error(msg: String) { errorMessage = msg; scope.launch { delay(2000); errorMessage = null } }
                    val precioDouble = precio.text.toDoubleOrNull()
                    if (nombre.text.isBlank()) return@Button error("El nombre es requerido")
                    if (marca.isBlank()) return@Button error("La marca es requerida")
                    if (categoria.isBlank()) return@Button error("La categoría es requerida")
                    if (precioDouble == null) return@Button error("Ingresa un precio válido")

                    if (!isPreview) {
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
                                    "precio" to precioDouble,
                                    "cantidadUnitario" to (cantidadUnitario.text.toLongOrNull() ?: 0L),
                                    "unidadesPorDisplay" to (unidadesPorDisplay.text.toLongOrNull() ?: 0L),
                                    "gramosVenta" to (gramosVenta.text.toLongOrNull() ?: 0L),
                                    "precioCompra" to (precioCompra.text.toDoubleOrNull() ?: 0.0)
                                )
                                imageUrl?.let { updateData["imagenUrl"] = it }

                                if (productoId != null) {
                                    val db = FirebaseFirestore.getInstance()
                                    db.collection("producto").document(productoId).update(updateData).await()
                                    Toast.makeText(context, "Producto actualizado con éxito", Toast.LENGTH_SHORT).show()
                                } else {
                                    val db = FirebaseFirestore.getInstance()
                                    db.collection("producto").add(updateData).await()
                                    Toast.makeText(context, "Producto guardado con éxito", Toast.LENGTH_SHORT).show()
                                }
                                navController.popBackStack()
                            } catch (e: Exception) {
                                error("Error al guardar en la base de datos")
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("GUARDAR CAMBIOS", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // Diálogo de Cámara / Galería
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color.White,
            title = { Text("Imagen del Producto", fontWeight = FontWeight.Black) },
            text = { Text("Selecciona el origen para actualizar la foto del producto.") },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { launcherCamera.launch(null); showDialog = false }) {
                        Icon(Icons.Outlined.PhotoCamera, null, tint = Color.Red)
                        Spacer(Modifier.width(4.dp))
                        Text("CÁMARA", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = { launcherGallery.launch("image/*"); showDialog = false }) {
                        Icon(Icons.Outlined.Collections, null, tint = Color.Red)
                        Spacer(Modifier.width(4.dp))
                        Text("GALERÍA", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun EditarProductoPreview() {
    val productoEjemplo = ProductoEditarModelo(
        id = "1",
        nombre = "Papas Fritas Adobadas Delisa",
        marca = "Delisa",
        categoria = "Botanas",
        subcategoria = "Mix",
        descripcion = "Papas fritas crujientes con un toque delicioso de chile y limón.",
        precio = 15.0,
        imagenUrl = "",
        cantidadUnitario = "900",
        unidadesPorDisplay = "20",
        gramosVenta = 45L,
        precioCompra = 150.0
    )
    MaterialTheme {
        EditarProductoScreen(
            navController = rememberNavController(),
            previewProducto = productoEjemplo
        )
    }
}