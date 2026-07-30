package com.gruposanangel.delivery.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.text.style.TextAlign
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.ui.theme.*
import com.gruposanangel.delivery.utilidades.DialogoSeleccionImagen
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.*

private val categoriasPorMarca = mapOf("Delisa" to listOf("Botanas", "Dulces"), "El Cazador" to listOf("Carnes frías", "Chiles secos"))
private val subcategoriasPorCategoria = mapOf("Carnes frías" to listOf("Jamón", "Salchicha"), "Chiles secos" to listOf("Guajillo", "Pasilla"), "Botanas" to listOf("Cacahuates", "Semillas", "Mix"), "Dulces" to listOf("Caramelos", "Chocolates", "Gomitas", "Enchilados"))

@Composable
fun CrearProductoScreen(navController: NavController) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val isPreview = LocalInspectionMode.current
    var isLoading by remember { mutableStateOf(false) }; var errorMessage by remember { mutableStateOf<String?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }; var imageFile by remember { mutableStateOf<File?>(null) }
    val launcherGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { val file = createImageFile2(context); context.contentResolver.openInputStream(it)?.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }; imageFile = file; imageBitmap = BitmapFactory.decodeFile(file.absolutePath) } } }
    val launcherCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> bmp?.let { val file = createImageFile2(context); FileOutputStream(file).use { out -> it.compress(Bitmap.CompressFormat.JPEG, 85, out) }; imageFile = file; imageBitmap = bmp } }

    CrearProductoContent(
        isLoading = isLoading, errorMessage = errorMessage, imageBitmap = imageBitmap,
        onBack = { navController.popBackStack() },
        onImageSourceSelected = { isCamera -> if (isCamera) launcherCamera.launch(null) else launcherGallery.launch("image/*") },
        onGuardar = { n, m, c, s, d, p, cu, ud, gv, pc ->
            if (imageFile == null) { errorMessage = "Imagen requerida"; scope.launch { delay(1500); errorMessage = null }; return@CrearProductoContent }
            if (!isPreview) {
                scope.launch {
                    isLoading = true; try {
                        val ref = FirebaseStorage.getInstance().reference.child("productos/${UUID.randomUUID()}.jpg")
                        ref.putFile(Uri.fromFile(imageFile!!)).await(); val url = ref.downloadUrl.await().toString()
                        FirebaseFirestore.getInstance().collection("producto").add(mapOf(
                            "nombre" to n, 
                            "marca" to m, 
                            "categoria" to c, 
                            "subcategoria" to s, 
                            "descripcion" to d, 
                            "precio" to p, 
                            "imagenUrl" to url, 
                            "activo" to true,
                            "cantidadUnitario" to (cu.toLongOrNull() ?: 0L),
                            "unidadesPorDisplay" to (ud.toLongOrNull() ?: 0L),
                            "gramosVenta" to (gv.toLongOrNull() ?: 0L),
                            "precioCompra" to pc
                        )).await()
                        navController.popBackStack()
                    } catch (e: Exception) { errorMessage = "Error al guardar" } finally { isLoading = false }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearProductoContent(isLoading: Boolean, errorMessage: String?, imageBitmap: Bitmap?, onBack: () -> Unit, onImageSourceSelected: (Boolean) -> Unit, onGuardar: (String, String, String, String, String, Double, String, String, String, Double) -> Unit) {
    var nombre by remember { mutableStateOf(TextFieldValue("")) }; var descripcion by remember { mutableStateOf(TextFieldValue("")) }; var precio by remember { mutableStateOf(TextFieldValue("")) }
    var cantidadUnitario by remember { mutableStateOf(TextFieldValue("")) }
    var unidadesPorDisplay by remember { mutableStateOf(TextFieldValue("")) }
    var gramosVenta by remember { mutableStateOf(TextFieldValue("")) }
    var precioCompra by remember { mutableStateOf(TextFieldValue("")) }
    var marca by rememberSaveable { mutableStateOf("") }; var categoria by rememberSaveable { mutableStateOf("") }; var subcategoria by rememberSaveable { mutableStateOf("") }; var showDialog by remember { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .shadow(2.dp, RoundedCornerShape(24.dp)), 
                shape = RoundedCornerShape(24.dp), 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed) }
                    Text("NUEVO PRODUCTO", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(16.dp)); Box(Modifier.size(150.dp).padding(8.dp), contentAlignment = Alignment.BottomEnd) {
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(4.dp, RoundedCornerShape(24.dp))
                        .clickable { showDialog = true }, 
                    shape = RoundedCornerShape(24.dp), 
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    if (imageBitmap != null) { Image(bitmap = imageBitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    else { Box(Modifier.fillMaxSize().background(DelisaRed.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) { Image(painter = painterResource(R.drawable.repartidor), contentDescription = null, modifier = Modifier.size(70.dp)) } }
                }
                Surface(shape = CircleShape, color = DelisaRed, shadowElevation = 4.dp, modifier = Modifier.size(36.dp).clickable { showDialog = true }) { Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.padding(8.dp)) }
            }
            Spacer(Modifier.height(24.dp)); Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(24.dp)), 
                shape = RoundedCornerShape(24.dp), 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ModernField("Nombre", nombre, Icons.Outlined.Label) { nombre = it }
                    DropdownField("Marca", marca, listOf("Delisa", "El Cazador"), Icons.Outlined.Bookmark) { marca = it; categoria = ""; subcategoria = "" }
                    DropdownField("Categoría", categoria, categoriasPorMarca[marca].orEmpty(), Icons.Outlined.Category, enabled = marca.isNotEmpty()) { categoria = it; subcategoria = "" }
                    DropdownField("Subcategoría", subcategoria, subcategoriasPorCategoria[categoria].orEmpty(), Icons.Outlined.Layers, enabled = categoria.isNotEmpty()) { subcategoria = it }
                    ModernField("Descripción", descripcion, Icons.Outlined.Description, maxLines = 3) { descripcion = it }
                    
                    Text(
                        "CONFIGURACIÓN DE COMPRA",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = DelisaRed,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            ModernField("Gramos Bolsa", cantidadUnitario, Icons.Outlined.Scale, keyboardType = KeyboardType.Number) {
                                if (it.text.all { c -> c.isDigit() }) cantidadUnitario = it
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ModernField("Display", unidadesPorDisplay, Icons.Outlined.Inventory2, keyboardType = KeyboardType.Number) { 
                                if (it.text.all { c -> c.isDigit() }) unidadesPorDisplay = it 
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            ModernField("Precio Compra", precioCompra, Icons.Outlined.ShoppingBag, keyboardType = KeyboardType.Number, prefix = "$") {
                                if (it.text.all { c -> c.isDigit() || c == '.' }) precioCompra = it
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ModernField("Gramos Bolsita", gramosVenta, Icons.Outlined.ShoppingBag, keyboardType = KeyboardType.Number) {
                                if (it.text.all { c -> c.isDigit() }) gramosVenta = it
                            }
                        }
                    }

                    ModernField("Precio", precio, Icons.Outlined.AttachMoney, keyboardType = KeyboardType.Number, prefix = "$") { if (it.text.all { c -> c.isDigit() || c == '.' }) precio = it }
                }
            }
            if (errorMessage != null) { Text(errorMessage, color = DelisaRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp)) }
            Spacer(Modifier.height(24.dp)); if (isLoading) { CircularProgressIndicator(color = DelisaRed) }
            else { Button(onClick = { onGuardar(nombre.text, marca, categoria, subcategoria, descripcion.text, precio.text.toDoubleOrNull() ?: 0.0, cantidadUnitario.text, unidadesPorDisplay.text, gramosVenta.text, precioCompra.text.toDoubleOrNull() ?: 0.0) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)) { Text("CREAR PRODUCTO", fontWeight = FontWeight.ExtraBold, color = Color.White) } }
        }
    }
    if (showDialog) {
        DialogoSeleccionImagen(
            onDismiss = { showDialog = false },
            onCameraSelected = { onImageSourceSelected(true); showDialog = false },
            onGallerySelected = { onImageSourceSelected(false); showDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(label: String, value: String, options: List<String>, icon: ImageVector, enabled: Boolean = true, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = !expanded }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, 
            onValueChange = {}, 
            readOnly = true, 
            enabled = enabled, 
            label = { Text(label) }, 
            leadingIcon = { Icon(icon, null, tint = if (enabled) DelisaRed else MaterialTheme.colorScheme.onSurfaceVariant) }, 
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, 
            modifier = Modifier.menuAnchor().fillMaxWidth(), 
            shape = RoundedCornerShape(16.dp), 
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DelisaRed, 
                focusedLabelColor = DelisaRed,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        )
        ExposedDropdownMenu(
            expanded = expanded, 
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) { options.forEach { opt -> DropdownMenuItem(text = { Text(opt, color = MaterialTheme.colorScheme.onSurface) }, onClick = { expanded = false; onSelect(opt) }) } }
    }
}

@Composable
fun ModernField(label: String, value: TextFieldValue, icon: ImageVector, maxLines: Int = 1, prefix: String? = null, keyboardType: KeyboardType = KeyboardType.Text, onChange: (TextFieldValue) -> Unit) {
    OutlinedTextField(
        value = value, 
        onValueChange = onChange, 
        label = { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, 
        leadingIcon = { Icon(icon, null, tint = DelisaRed) }, 
        prefix = if (prefix != null) { { Text(prefix, color = MaterialTheme.colorScheme.onSurface) } } else null,
        maxLines = maxLines,
        singleLine = maxLines == 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), 
        modifier = Modifier.fillMaxWidth(), 
        shape = RoundedCornerShape(16.dp), 
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DelisaRed, 
            focusedLabelColor = DelisaRed,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

fun createImageFile2(context: android.content.Context): File { val dir = File(context.filesDir, "productos"); if (!dir.exists()) dir.mkdirs(); return File(dir, "cp_${System.currentTimeMillis()}.jpg") }

@Preview(showBackground = true, showSystemUi = true, name = "Crear Producto - Formulario")
@Composable
fun CrearProductoPreview() {
    DeliveryTheme { CrearProductoContent(false, null, null, {}, {}, {_,_,_,_,_,_,_,_,_,_ ->}) }
}
