@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
    val precioCompra: Double = 0.0,
    val costoEmpaque: Double = 0.0,
    val aplicaTransporte: Boolean = true,
    val esCompuesto: Boolean = false,
    val ingredientes: List<IngredienteBusqueda> = emptyList()
)

data class IngredienteBusqueda(
    val id: String,
    val nombre: String,
    val gramos: Double,
    val precioCompraBase: Double = 0.0, // precioCompra del producto base
    val cantidadUnitarioBase: Double = 1.0 // cantidadUnitario del producto base
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
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) },
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

class CurrencyVisualTransformationEditar : VisualTransformation {
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

class SuffixVisualTransformationEditar(val suffix: String) : VisualTransformation {
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

class NumericRtlVisualTransformationEditar : VisualTransformation {
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
fun CostoMiniItemEditar(label: String, value: String, color: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = if(color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color)
    }
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
    val configViewModel: ProductoFormViewModel = viewModel()
    val configState by configViewModel.uiState.collectAsState()

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
    var costoEmpaque by remember { mutableStateOf(TextFieldValue(previewProducto?.costoEmpaque?.toString() ?: "")) }
    var aplicaTransporte by remember { mutableStateOf(previewProducto?.aplicaTransporte ?: true) }

    // --- NUEVOS CAMPOS PARA PRODUCTOS COMPUESTOS (MIXES) ---
    var esCompuesto by remember { mutableStateOf(previewProducto?.esCompuesto ?: false) }
    val ingredientes = remember { mutableStateListOf<IngredienteBusqueda>().apply { addAll(previewProducto?.ingredientes ?: emptyList()) } }
    var todosLosProductos by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var showAddIngredientDialog by remember { mutableStateOf(false) }

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
                    
                    // Convertir precios de Firestore (Double) a Formato Centavos (String) para los campos nuevos
                    val pVentaVal = doc.getDouble("precio") ?: 0.0
                    precio = TextFieldValue((pVentaVal * 100).toLong().toString())
                    
                    marca = doc.getString("marca") ?: ""
                    categoria = doc.getString("categoria") ?: ""
                    subcategoria = doc.getString("subcategoria") ?: ""
                    
                    val cUVal = doc.get("cantidadUnitario")?.toString()?.toLongOrNull() ?: 0L
                    cantidadUnitario = TextFieldValue(cUVal.toString())
                    
                    val uDVal = doc.get("unidadesPorDisplay")?.toString()?.toLongOrNull() ?: 0L
                    unidadesPorDisplay = TextFieldValue(uDVal.toString())
                    
                    val gVVal = doc.get("gramosVenta")?.toString()?.toLongOrNull() ?: 0L
                    gramosVenta = TextFieldValue(gVVal.toString())
                    
                    val pCompraVal = doc.getDouble("precioCompra") ?: 0.0
                    precioCompra = TextFieldValue((pCompraVal * 100).toLong().toString()) 
                    
                    val cEmpaqueVal = doc.getDouble("costoEmpaque") ?: 0.0
                    costoEmpaque = TextFieldValue((cEmpaqueVal * 100).toLong().toString())

                    aplicaTransporte = doc.getBoolean("aplicaTransporte") ?: true

                    esCompuesto = doc.getBoolean("esCompuesto") ?: false
                    val ingList = doc.get("ingredientes") as? List<Map<String, Any>>
                    ingredientes.clear()
                    ingList?.forEach { i ->
                        ingredientes.add(IngredienteBusqueda(
                            id = i["id"]?.toString() ?: "",
                            nombre = i["nombre"]?.toString() ?: "",
                            gramos = (i["gramos"] as? Number)?.toDouble() ?: 0.0
                        ))
                    }

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

    // Carga de catálogo para ingredientes
    LaunchedEffect(Unit) {
        try {
            val db = FirebaseFirestore.getInstance()
            val snap = db.collection("producto").whereEqualTo("activo", true).get().await()
            todosLosProductos = snap.documents.mapNotNull { it.data?.plus("id" to it.id) }
        } catch (e: Exception) { }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
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
                    .padding(vertical = 8.dp)
                    .shadow(2.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = DelisaRed)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (productoId == null && previewProducto == null) "NUEVO PRODUCTO" else "EDITAR PRODUCTO",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DelisaRed)
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
                        .shadow(4.dp, RoundedCornerShape(24.dp))
                        .clickable { showDialog = true },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                            Modifier.fillMaxSize().background(DelisaRed.copy(alpha = 0.05f)),
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
                    color = DelisaRed,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Información General",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    ModernFieldEditar("Nombre del Producto", nombre, Icons.Outlined.Label) { nombre = it }

                    DropdownFieldEditar(
                        label = "Marca",
                        value = marca.ifEmpty { "Selecciona marca" },
                        options = configState.marcas.ifEmpty { listOf("Delisa", "El Cazador") },
                        icon = Icons.Outlined.Bookmark
                    ) {
                        marca = it
                        categoria = ""
                        subcategoria = ""
                    }

                    val categoriasSafe = configState.categoriasPorMarca[marca].orEmpty()
                    DropdownFieldEditar(
                        label = "Categoría",
                        value = if (categoria.isEmpty()) "Selecciona categoría" else categoria,
                        options = if (categoriasSafe.isEmpty()) listOf("Sin categorías") else categoriasSafe,
                        enabled = marca.isNotEmpty(),
                        icon = Icons.Outlined.Category
                    ) {
                        categoria = it
                        subcategoria = ""
                    }

                    DropdownFieldEditar(
                        label = "Subcategoría",
                        value = if (subcategoria.isEmpty()) "Selecciona subcategoría" else subcategoria,
                        options = listOf("Sin subcategoría"),
                        enabled = false,
                        icon = Icons.Outlined.Layers
                    ) {
                        subcategoria = it
                    }

                    ModernFieldEditar("Descripción", descripcion, Icons.Outlined.Description, maxLines = 3) { descripcion = it }

                    // --- SECCIÓN DE PRODUCTO COMPUESTO (MIX) ---
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().background(DelisaRed.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Producto Compuesto (Mix)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Activa si este producto se arma con otros (Ej. Mix Bandera)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = esCompuesto,
                            onCheckedChange = { esCompuesto = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DelisaRed)
                        )
                    }

                    if (esCompuesto) {
                        Text(
                            "INGREDIENTES DEL MIX",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = DelisaRed,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = TextAlign.Center
                        )

                        ingredientes.forEachIndexed { index, ing ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(ing.nombre, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Base: ${ing.id.takeLast(6)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedTextField(
                                        value = if(ing.gramos == 0.0) "" else ing.gramos.toString(),
                                        onValueChange = { 
                                            val newVal = it.toDoubleOrNull() ?: 0.0
                                            ingredientes[index] = ing.copy(gramos = newVal)
                                        },
                                        label = { Text("Gramos", fontSize = 10.sp) },
                                        suffix = { Text("g") },
                                        modifier = Modifier.width(90.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = DelisaRed,
                                            focusedLabelColor = DelisaRed,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                    IconButton(onClick = { ingredientes.removeAt(index) }) {
                                        Icon(Icons.Outlined.Delete, null, tint = DelisaRed)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { showAddIngredientDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.surface)
                            Spacer(Modifier.width(8.dp))
                            Text("AÑADIR INGREDIENTE", fontSize = 12.sp, color = MaterialTheme.colorScheme.surface)
                        }

                        // Cálculo de Costo Sugerido (Suma de ingredientes)
                        val costoSugerido = ingredientes.sumOf { ing ->
                            val prodBase = todosLosProductos.find { it["id"] == ing.id }
                            val pCompra = (prodBase?.get("precioCompra") as? Number)?.toDouble() ?: 0.0
                            val cUnit = (prodBase?.get("cantidadUnitario") as? Number)?.toDouble() ?: 1.0
                            (pCompra / cUnit) * ing.gramos
                        }
                        
                        // Añadir costo de empaque al Mix si se desea considerar empaque final
                        val costoFinalMix = costoSugerido + (costoEmpaque.text.toDoubleOrNull() ?: 0.0)

                        if (costoFinalMix > 0) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = DelisaGreenLight.copy(alpha = if(isSystemInDarkTheme()) 0.2f else 1f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Analytics, null, tint = if(isSystemInDarkTheme()) Color(0xFF81C784) else DelisaGreenDark)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Costo de Producción Sugerido", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$ ${String.format(Locale.US, "%.2f", costoFinalMix)}", fontWeight = FontWeight.Black, color = if(isSystemInDarkTheme()) Color(0xFF81C784) else DelisaGreenDark)
                                        Text("* Incluye ingredientes base + empaque.", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    if (!esCompuesto) {
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
                                ModernFieldEditar(
                                    label = "Peso bolsa o caja", 
                                    value = cantidadUnitario, 
                                    icon = Icons.Outlined.Scale, 
                                    keyboardType = KeyboardType.Number,
                                    visualTransformation = SuffixVisualTransformationEditar(" gramos"),
                                    textAlign = TextAlign.End
                                ) {
                                    if (it.text.all { c -> c.isDigit() }) {
                                        if (it.text.length <= 6) cantidadUnitario = it
                                    }
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                ModernFieldEditar(
                                    label = "Unidades por display", 
                                    value = unidadesPorDisplay, 
                                    icon = Icons.Outlined.Inventory2, 
                                    keyboardType = KeyboardType.Number,
                                    visualTransformation = NumericRtlVisualTransformationEditar(),
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
                                ModernFieldEditar(
                                    label = "Precio bolsa o caja", 
                                    value = precioCompra, 
                                    icon = Icons.Outlined.ShoppingBag, 
                                    keyboardType = KeyboardType.Number,
                                    visualTransformation = CurrencyVisualTransformationEditar(),
                                    textAlign = TextAlign.End
                                ) {
                                    if (it.text.all { c -> c.isDigit() }) {
                                        if (it.text.length <= 7) precioCompra = it
                                    }
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                ModernFieldEditar(
                                    label = "Peso por bolsita", 
                                    value = gramosVenta, 
                                    icon = Icons.Outlined.Scale, 
                                    keyboardType = KeyboardType.Number,
                                    visualTransformation = SuffixVisualTransformationEditar(" gramos"),
                                    textAlign = TextAlign.End
                                ) {
                                    if (it.text.all { c -> c.isDigit() }) {
                                        if (it.text.length <= 5) gramosVenta = it
                                    }
                                }
                            }
                        }
                    } else {
                        // Si es compuesto, forzamos Gramos Bolsita a la suma de ingredientes
                        val sumaGramos = ingredientes.sumOf { it.gramos }
                        LaunchedEffect(sumaGramos) {
                            gramosVenta = TextFieldValue(sumaGramos.toInt().toString())
                        }
                        ModernFieldEditar(
                            label = "Gramos Totales Mix", 
                            value = gramosVenta, 
                            icon = Icons.Outlined.Scale, 
                            keyboardType = KeyboardType.Number,
                            visualTransformation = SuffixVisualTransformationEditar(" gramos"),
                            textAlign = TextAlign.End
                        ) {
                            if (it.text.all { c -> c.isDigit() }) gramosVenta = it
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            ModernFieldEditar(
                                label = "Costo de empaque", 
                                value = costoEmpaque, 
                                icon = Icons.Outlined.ShoppingBasket, 
                                keyboardType = KeyboardType.Number,
                                visualTransformation = CurrencyVisualTransformationEditar(),
                                textAlign = TextAlign.End
                            ) {
                                if (it.text.all { c -> c.isDigit() }) {
                                    if (it.text.length <= 7) costoEmpaque = it
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            ModernFieldEditar(
                                label = "Precio al público", 
                                value = precio, 
                                icon = Icons.Outlined.AttachMoney, 
                                keyboardType = KeyboardType.Number,
                                visualTransformation = CurrencyVisualTransformationEditar(),
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

            // Si es compuesto, el costo de materia prima (relleno) viene de la suma de ingredientes
            val costoMateriaPrima = if (esCompuesto) {
                ingredientes.sumOf { ing ->
                    val prodBase = todosLosProductos.find { it["id"] == ing.id }
                    val pCBase = (prodBase?.get("precioCompra") as? Number)?.toDouble() ?: 0.0
                    val cUBase = (prodBase?.get("cantidadUnitario") as? Number)?.toDouble() ?: 1.0
                    (pCBase / cUBase) * ing.gramos
                }
            } else {
                if(gB > 0) (pC / gB) * gV else 0.0
            }

            val costoTransporte = if(aplicaTransporte) (gV / 1000.0) * 5.0 else 0.0
            val costoFinal = costoMateriaPrima + cE + costoTransporte
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
                            CostoMiniItemEditar(if(esCompuesto) "MATERIAL MIX" else "MATERIA PRIMA", "$ ${String.format(Locale.US, "%.2f", costoMateriaPrima)}")
                            CostoMiniItemEditar("EMPAQUE", "$ ${String.format(Locale.US, "%.2f", cE)}")
                            CostoMiniItemEditar("TRANSPORTE", "$ ${String.format(Locale.US, "%.2f", costoTransporte)}")
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            CostoMiniItemEditar("COSTO FINAL", "$ ${String.format(Locale.US, "%.2f", costoFinal)}")
                            CostoMiniItemEditar("UTILIDAD", "$ ${String.format(Locale.US, "%.2f", utilidad)}", color = colorEstado)
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

            Spacer(Modifier.height(20.dp))

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                errorMessage?.let {
                    Text(
                        it,
                        color = DelisaRed,
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
                                    "precio" to ((precio.text.toDoubleOrNull() ?: 0.0) / 100.0),
                                    "cantidadUnitario" to (cantidadUnitario.text.toLongOrNull() ?: 0L),
                                    "unidadesPorDisplay" to (unidadesPorDisplay.text.toLongOrNull() ?: 0L),
                                    "gramosVenta" to (gramosVenta.text.toLongOrNull() ?: 0L),
                                    "precioCompra" to ((precioCompra.text.toDoubleOrNull() ?: 0.0) / 100.0),
                                    "costoEmpaque" to ((costoEmpaque.text.toDoubleOrNull() ?: 0.0) / 100.0),
                                    "aplicaTransporte" to aplicaTransporte,
                                    "esCompuesto" to esCompuesto,
                                    "ingredientes" to ingredientes.map { mapOf(
                                        "id" to it.id,
                                        "nombre" to it.nombre,
                                        "gramos" to it.gramos
                                    ) }
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
                colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
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
        DialogoSeleccionImagen(
            onDismiss = { showDialog = false },
            onCameraSelected = { 
                launcherCamera.launch(null)
                showDialog = false 
            },
            onGallerySelected = { 
                launcherGallery.launch("image/*")
                showDialog = false
            }
        )
    }

    // --- DIÁLOGO PARA SELECCIONAR INGREDIENTES ---
    if (showAddIngredientDialog) {
        var query by remember { mutableStateOf("") }
        val filtrados = todosLosProductos.filter { 
            (it["nombre"] as? String)?.contains(query, ignoreCase = true) == true
        }

        AlertDialog(
            onDismissRequest = { showAddIngredientDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Añadir Ingrediente", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(Modifier.height(400.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Buscar producto...") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = DelisaRed) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DelisaRed,
                            focusedLabelColor = DelisaRed,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(filtrados) { prod ->
                            val id = prod["id"]?.toString() ?: ""
                            val nombreP = prod["nombre"]?.toString() ?: ""
                            
                            // Evitar duplicados
                            if (ingredientes.none { it.id == id }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            ingredientes.add(IngredienteBusqueda(id, nombreP, 0.0))
                                            showAddIngredientDialog = false
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Inventory2, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(nombreP, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddIngredientDialog = false }) { Text("CERRAR", color = DelisaRed) }
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