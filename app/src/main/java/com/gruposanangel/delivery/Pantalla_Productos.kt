@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// 🔹 AQUÍ ESTÁ EL MODELO DE DATOS QUE FALTABA (Ya no marcará Unresolved reference)
data class ProductoFirestore(
    val id: String,
    val nombre: String,
    val marca: String,
    val categoria: String,
    val subcategoria: String,
    val descripcion: String,
    val precio: Double,
    val imagenUrl: String
)

@Composable
fun ListaProductosScreen(
    navController: NavController,
    previewProductos: List<ProductoFirestore>? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current

    var showDialogEliminar by remember { mutableStateOf<String?>(null) }
    var productos by remember { mutableStateOf<List<ProductoFirestore>>(previewProductos ?: emptyList()) }
    var isLoading by remember { mutableStateOf(!isPreview) }
    val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    // 🔍 ESTADO DEL BUSCADOR
    var textoBusqueda by remember { mutableStateOf("") }

    // Filtrado inteligente: busca por nombre o por marca
    val productosFiltrados = remember(productos, textoBusqueda) {
        productos.filter {
            it.nombre.contains(textoBusqueda, ignoreCase = true) ||
                    it.marca.contains(textoBusqueda, ignoreCase = true)
        }
    }

    // Cargar de Firestore en tiempo real
    LaunchedEffect(Unit) {
        if (!isPreview) {
            val db = FirebaseFirestore.getInstance()
            db.collection("producto")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        isLoading = false
                        Toast.makeText(context, "Error al cargar productos", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        productos = snapshot.documents.map { doc ->
                            ProductoFirestore(
                                id = doc.id,
                                nombre = doc.getString("nombre") ?: "",
                                marca = doc.getString("marca") ?: "",
                                categoria = doc.getString("categoria") ?: "",
                                subcategoria = doc.getString("subcategoria") ?: "",
                                descripcion = doc.getString("descripcion") ?: "",
                                precio = doc.getDouble("precio") ?: 0.0,
                                imagenUrl = doc.getString("imagenUrl") ?: ""
                            )
                        }
                        isLoading = false
                    }
                }
        }
    }

    Scaffold(
        containerColor = Color(0xFFF8F9FA)
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // 🔹 BARRA DE BÚSQUEDA MODERNA CON BOTÓN ATRÁS INTEGRADO
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(3.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flecha elegante para ir hacia atrás (Dashboard)
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Volver",
                                tint = Color.Red
                            )
                        }

                        // Input del Buscador
                        TextField(
                            value = textoBusqueda,
                            onValueChange = { textoBusqueda = it },
                            placeholder = {
                                Text(
                                    "Busca por nombre o marca",
                                    color = Color.Gray,
                                    fontSize = 15.sp
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = Color.LightGray)
                            },
                            trailingIcon = {
                                if (textoBusqueda.isNotEmpty()) {
                                    IconButton(onClick = { textoBusqueda = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = Color.Gray)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                    }
                }

                // LISTADO DE PRODUCTOS FILTRADOS
                if (isLoading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Red)
                    }
                } else if (productosFiltrados.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (textoBusqueda.isEmpty()) "No hay productos registrados" else "No se encontraron resultados",
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(productosFiltrados, key = { it.id }) { producto ->
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = producto.imagenUrl,
                                        placeholder = painterResource(R.drawable.repartidor),
                                        error = painterResource(R.drawable.repartidor),
                                        contentDescription = producto.nombre,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(75.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = producto.nombre,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = "${producto.marca} • ${producto.categoria}",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = formatoMoneda.format(producto.precio),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = Color.Red
                                        )
                                    }

                                    // 🔹 REEMPLAZA EL BLOQUE DE ICONOS ANTIGUO POR ESTE NUEVO:
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp) // Un poco más de espacio entre ellos
                                    ) {
                                        // 🖊️ BOTÓN EDITAR MODERNO
                                        Surface(
                                            onClick = { navController.navigate("EDITAR_PRODUCTOS/${producto.id}") },
                                            shape = CircleShape,
                                            color = Color(0xFF00AAFF).copy(alpha = 0.1f), // Fondo azul ultra suave
                                            modifier = Modifier.size(36.dp) // Tamaño perfecto
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Edit, // 👈 Versión Outlined (ligera)
                                                    contentDescription = "Editar",
                                                    tint = Color(0xFF00AAFF), // Azul vibrante
                                                    modifier = Modifier.size(18.dp) // Icono estilizado
                                                )
                                            }
                                        }

                                        // 🗑️ BOTÓN ELIMINAR MODERNO
                                        Surface(
                                            onClick = { showDialogEliminar = producto.id },
                                            shape = CircleShape,
                                            color = Color.Red.copy(alpha = 0.1f), // Fondo rojo suave corporativo
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Delete, // 👈 Versión Outlined (ligera)
                                                    contentDescription = "Eliminar",
                                                    tint = Color.Red, // Rojo Delisa
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // BOTÓN FLOTANTE (FAB) ANIMADO PARA AGREGAR NUEVO
            var clicked by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(
                targetValue = if (clicked) 1.2f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )

            FloatingActionButton(
                onClick = {
                    clicked = true
                    scope.launch {
                        delay(100)
                        clicked = false
                        navController.navigate("CREAR_PRODUCTO")
                    }
                },
                containerColor = Color.Red,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Producto", modifier = Modifier.size(28.dp))
            }
        }

        // DIÁLOGO DE CONFIRMACIÓN DE ELIMINACIÓN
        if (showDialogEliminar != null) {
            DialogoConfirmacion(
                titulo = "Eliminar Producto",
                mensaje = "¿Estás seguro de que deseas eliminar este producto del sistema? Esta acción no se puede deshacer y borrará el artículo de Firebase permanentemente.",
                textoConfirmar = "Eliminar",
                textoCancelar = "Cancelar",
                colorConfirmar = Color.Red,
                onConfirmar = {
                    val id = showDialogEliminar!!
                    if (!isPreview) {
                        val db = FirebaseFirestore.getInstance()
                        db.collection("producto").document(id)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Producto eliminado con éxito", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Error al eliminar el producto", Toast.LENGTH_SHORT).show()
                            }
                    }
                    showDialogEliminar = null
                },
                onCancelar = { showDialogEliminar = null }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ListaProductosPreview() {
    val productosFalsos = listOf(
        ProductoFirestore(
            id = "1",
            nombre = "Papas Adobadas Delisa 45g",
            marca = "Delisa",
            categoria = "Botanas",
            subcategoria = "Mix",
            descripcion = "Botana deliciosa",
            precio = 13.0,
            imagenUrl = ""
        ),
        ProductoFirestore(
            id = "2",
            nombre = "Salchicha Viena El Cazador",
            marca = "El Cazador",
            categoria = "Carnes frías",
            subcategoria = "Salchicha",
            descripcion = "Salchicha premium",
            precio = 45.5,
            imagenUrl = ""
        )
    )

    MaterialTheme {
        ListaProductosScreen(
            navController = rememberNavController(),
            previewProductos = productosFalsos
        )
    }
}