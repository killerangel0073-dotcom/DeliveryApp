package com.gruposanangel.delivery.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
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

@Composable
fun CrearProductoScreen(navController: NavController) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val isPreview = LocalInspectionMode.current
    val viewModel: ProductoFormViewModel = viewModel()
    val configState by viewModel.uiState.collectAsState()
    
    var isLoading by remember { mutableStateOf(false) }; var errorMessage by remember { mutableStateOf<String?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }; var imageFile by remember { mutableStateOf<File?>(null) }
    val launcherGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { val file = createImageFile2(context); context.contentResolver.openInputStream(it)?.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }; imageFile = file; imageBitmap = BitmapFactory.decodeFile(file.absolutePath) } } }
    val launcherCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> bmp?.let { val file = createImageFile2(context); FileOutputStream(file).use { out -> it.compress(Bitmap.CompressFormat.JPEG, 85, out) }; imageFile = file; imageBitmap = bmp } }

    CrearProductoContent(
        isLoading = isLoading || configState.isLoading, 
        errorMessage = errorMessage, 
        imageBitmap = imageBitmap,
        marcas = configState.marcas,
        categoriasMap = configState.categoriasPorMarca,
        onBack = { navController.popBackStack() },
        onImageSourceSelected = { isCamera -> if (isCamera) launcherCamera.launch(null) else launcherGallery.launch("image/*") },
        onGuardar = { n, m, c, s, d, p, cu, ud, gv, pc, ce, at ->
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
                            "precioCompra" to pc,
                            "costoEmpaque" to ce,
                            "aplicaTransporte" to at
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
fun CrearProductoContent(
    isLoading: Boolean, 
    errorMessage: String?, 
    imageBitmap: Bitmap?, 
    marcas: List<String> = emptyList(),
    categoriasMap: Map<String, List<String>> = emptyMap(),
    onBack: () -> Unit, 
    onImageSourceSelected: (Boolean) -> Unit, 
    onGuardar: (String, String, String, String, String, Double, String, String, String, Double, Double, Boolean) -> Unit
) {
    var nombre by remember { mutableStateOf(TextFieldValue("")) }; var descripcion by remember { mutableStateOf(TextFieldValue("")) }
    var precio by remember { mutableStateOf(TextFieldValue("")) }
    var cantidadUnitario by remember { mutableStateOf(TextFieldValue("")) }
    var unidadesPorDisplay by remember { mutableStateOf(TextFieldValue("")) }
    var gramosVenta by remember { mutableStateOf(TextFieldValue("")) }
    var precioCompra by remember { mutableStateOf(TextFieldValue("")) }
    var costoEmpaque by remember { mutableStateOf(TextFieldValue("")) }
    var aplicaTransporte by remember { mutableStateOf(true) }
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
                    DropdownField("Marca", marca, marcas.ifEmpty { listOf("Delisa", "El Cazador") }, Icons.Outlined.Bookmark) { marca = it; categoria = ""; subcategoria = "" }
                    DropdownField("Categoría", categoria, categoriasMap[marca].orEmpty(), Icons.Outlined.Category, enabled = marca.isNotEmpty()) { categoria = it; subcategoria = "" }
                    DropdownField("Subcategoría", subcategoria, emptyList(), Icons.Outlined.Layers, enabled = false) { subcategoria = it }
                    ModernField("Descripción", descripcion, Icons.Outlined.Description, maxLines = 3) { descripcion = it }
                    
                    Text(
                        "Configuración de compra",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = DelisaRed,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            ModernField(
                                label = "Peso bolsa o caja", 
                                value = cantidadUnitario, 
                                icon = Icons.Outlined.Scale, 
                                keyboardType = KeyboardType.Number,
                                visualTransformation = SuffixVisualTransformation(" gramos"),
                                textAlign = TextAlign.End
                            ) {
                                if (it.text.all { c -> c.isDigit() }) {
                                    if (it.text.length <= 6) cantidadUnitario = it
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ModernField(
                                label = "Unidades por display", 
                                value = unidadesPorDisplay, 
                                icon = Icons.Outlined.Inventory2, 
                                keyboardType = KeyboardType.Number,
                                visualTransformation = NumericRtlVisualTransformation(),
                                textAlign = TextAlign.End
                            ) { 
                                if (it.text.all { c -> c.isDigit() }) {
                                    if (it.text.length <= 4) unidadesPorDisplay = it 
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            ModernField(
                                label = "Precio bolsa o caja", 
                                value = precioCompra, 
                                icon = Icons.Outlined.ShoppingBag, 
                                keyboardType = KeyboardType.Number,
                                visualTransformation = CurrencyVisualTransformation(),
                                textAlign = TextAlign.End
                            ) {
                                if (it.text.all { c -> c.isDigit() }) {
                                    if (it.text.length <= 7) precioCompra = it
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ModernField(
                                label = "Peso por bolsita", 
                                value = gramosVenta, 
                                icon = Icons.Outlined.Scale, 
                                keyboardType = KeyboardType.Number,
                                visualTransformation = SuffixVisualTransformation(" gramos"),
                                textAlign = TextAlign.End
                            ) {
                                if (it.text.all { c -> c.isDigit() }) {
                                    if (it.text.length <= 5) gramosVenta = it
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            ModernField(
                                label = "Costo de empaque", 
                                value = costoEmpaque, 
                                icon = Icons.Outlined.ShoppingBasket, 
                                keyboardType = KeyboardType.Number,
                                visualTransformation = CurrencyVisualTransformation(),
                                textAlign = TextAlign.End
                            ) {
                                if (it.text.all { c -> c.isDigit() }) {
                                    if (it.text.length <= 7) costoEmpaque = it
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ModernField(
                                label = "Precio al público", 
                                value = precio, 
                                icon = Icons.Outlined.AttachMoney, 
                                keyboardType = KeyboardType.Number,
                                visualTransformation = CurrencyVisualTransformation(),
                                textAlign = TextAlign.End
                            ) {
                                if (it.text.all { c -> c.isDigit() }) {
                                    if (it.text.length <= 7) precio = it
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().background(DelisaRed.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("¿Cobrar Transporte?", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Aplica costo de $5.00 por kilogramo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = aplicaTransporte,
                            onCheckedChange = { aplicaTransporte = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DelisaRed)
                        )
                    }
                }
            }

            // --- CALCULADORA DE COSTOS EN TIEMPO REAL ---
            val pC = (precioCompra.text.toDoubleOrNull() ?: 0.0) / 100.0
            val gB = cantidadUnitario.text.toDoubleOrNull() ?: 1.0
            val gV = gramosVenta.text.toDoubleOrNull() ?: 0.0
            val cE = (costoEmpaque.text.toDoubleOrNull() ?: 0.0) / 100.0
            val pV = (precio.text.toDoubleOrNull() ?: 0.0) / 100.0

            val costoGr = if(gB > 0) pC / gB else 0.0
            val costoContenido = costoGr * gV
            val costoTransporte = if(aplicaTransporte) (gV / 1000.0) * 5.0 else 0.0
            val costoFinal = costoContenido + cE + costoTransporte
            val utilidad = pV - costoFinal
            val margen = if(pV > 0) (utilidad / pV) * 100 else 0.0

            if (costoFinal > 0) {
                val colorEstado = if(utilidad > 0) DelisaGreenDark else DelisaRed
                val colorFondoExtra = if(utilidad > 0) DelisaGreen.copy(alpha = 0.05f) else DelisaRed.copy(alpha = 0.05f)

                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).shadow(4.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, colorEstado.copy(alpha = 0.3f))
                ) {
                    Column(Modifier.background(colorFondoExtra).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Analytics, null, tint = colorEstado, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("ANÁLISIS DE COSTO REAL", fontWeight = FontWeight.Black, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            CostoMiniItem("MATERIA PRIMA", "$ ${String.format(Locale.US, "%.2f", costoContenido)}")
                            CostoMiniItem("EMPAQUE", "$ ${String.format(Locale.US, "%.2f", cE)}")
                            CostoMiniItem("TRANSPORTE", "$ ${String.format(Locale.US, "%.2f", costoTransporte)}")
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            CostoMiniItem("COSTO FINAL", "$ ${String.format(Locale.US, "%.2f", costoFinal)}")
                            CostoMiniItem("UTILIDAD", "$ ${String.format(Locale.US, "%.2f", utilidad)}", color = colorEstado)
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            Text("MARGEN ESTIMADO: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${String.format(Locale.US, "%.1f", margen)}%", fontSize = 18.sp, fontWeight = FontWeight.Black, color = colorEstado)
                        }
                    }
                }
            }

            if (errorMessage != null) { Text(errorMessage, color = DelisaRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp)) }
            Spacer(Modifier.height(24.dp)); if (isLoading) { CircularProgressIndicator(color = DelisaRed) }
            else { Button(onClick = { onGuardar(nombre.text, marca, categoria, subcategoria, descripcion.text, (precio.text.toDoubleOrNull() ?: 0.0) / 100.0, cantidadUnitario.text, unidadesPorDisplay.text, gramosVenta.text, (precioCompra.text.toDoubleOrNull() ?: 0.0) / 100.0, (costoEmpaque.text.toDoubleOrNull() ?: 0.0) / 100.0, aplicaTransporte) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)) { Text("CREAR PRODUCTO", fontWeight = FontWeight.ExtraBold, color = Color.White) } }
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
fun ModernField(
    label: String, 
    value: TextFieldValue, 
    icon: ImageVector, 
    maxLines: Int = 1, 
    prefix: String? = null, 
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textAlign: TextAlign = TextAlign.Start,
    onChange: (TextFieldValue) -> Unit
) {
    OutlinedTextField(
        value = value, 
        onValueChange = onChange, 
        label = { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }, 
        leadingIcon = { Icon(icon, null, tint = DelisaRed) }, 
        prefix = if (prefix != null) { { Text(prefix, color = MaterialTheme.colorScheme.onSurface) } } else null,
        maxLines = maxLines,
        singleLine = maxLines == 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), 
        visualTransformation = visualTransformation,
        textStyle = LocalTextStyle.current.copy(textAlign = textAlign),
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

class CurrencyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        if (rawText.isEmpty()) {
            return TransformedText(AnnotatedString("$ 0.00"), object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = 6
                override fun transformedToOriginal(offset: Int): Int = 0
            })
        }

        val digits = rawText.filter { it.isDigit() }
        val value = digits.toDoubleOrNull() ?: 0.0
        val formatted = "$ " + String.format(Locale.US, "%.2f", value / 100.0)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = formatted.length
            override fun transformedToOriginal(offset: Int): Int = rawText.length
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

class SuffixVisualTransformation(val suffix: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        val formatted = rawText + suffix
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset
            override fun transformedToOriginal(offset: Int): Int {
                // Prevenir crash: si el cursor intenta entrar al sufijo, limitarlo al final del texto real
                if (offset > rawText.length) return rawText.length
                return offset
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

class NumericRtlVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = rawText.length
            override fun transformedToOriginal(offset: Int): Int = rawText.length
        }
        return TransformedText(text, offsetMapping)
    }
}

@Composable
fun CostoMiniItem(label: String, value: String, color: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = if(color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
    }
}

fun createImageFile2(context: android.content.Context): File { val dir = File(context.filesDir, "productos"); if (!dir.exists()) dir.mkdirs(); return File(dir, "cp_${System.currentTimeMillis()}.jpg") }

@Preview(showBackground = true, showSystemUi = true, name = "Crear Producto - Formulario")
@Composable
fun CrearProductoPreview() {
    DeliveryTheme { CrearProductoContent(false, null, null, emptyList(), emptyMap(), {}, {}, {_,_,_,_,_,_,_,_,_,_,_,_ ->}) }
}
