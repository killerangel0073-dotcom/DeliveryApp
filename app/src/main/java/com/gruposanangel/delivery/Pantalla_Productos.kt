@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.utilidades.DialogoConfirmacion
import kotlinx.coroutines.launch

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
fun ListaProductosScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = FirebaseFirestore.getInstance()
    var showDialogEliminar by remember { mutableStateOf<String?>(null) } // guarda el ID del producto a eliminar

    var productos by remember { mutableStateOf<List<ProductoFirestore>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Cargar productos desde Firestore
    LaunchedEffect(Unit) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Productos", fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFFF0000)
                )
            } else if (productos.isEmpty()) {
                Text(
                    text = "No hay productos registrados",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Gray,
                    fontSize = 18.sp
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(productos, key = { it.id }) { producto ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(8.dp),
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
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        producto.nombre,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        "${producto.marca} • ${producto.categoria} • ${producto.subcategoria}",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        "$${"%.2f".format(producto.precio)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFFFF0000)
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(onClick = {
                                        navController.navigate("EDITAR_PRODUCTOS/${producto.id}")
                                    }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Editar",
                                            tint = Color(0xFF00AAFF)
                                        )
                                    }





                                     // Dentro del Card de cada producto, reemplaza el IconButton de eliminar así:
                                    IconButton(onClick = { showDialogEliminar = producto.id }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Eliminar",
                                            tint = Color.Red
                                        )
                                    }




                                }
                            }
                        }
                    }
                }
            }

            val scope = rememberCoroutineScope()
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
                    navController.navigate("CREAR_PRODUCTO")
                    scope.launch {
                        kotlinx.coroutines.delay(150)
                        clicked = false
                    }
                },
                containerColor = Color(0xFFFF0000),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Producto")
            }


        }


        // Luego, al final del Box (fuera del LazyColumn), agregamos el DialogoConfirmacion:
        if (showDialogEliminar != null) {
            DialogoConfirmacion(
                titulo = "Eliminar Producto",
                mensaje = "¿Seguro que deseas eliminar este producto?",
                onConfirmar = {
                    val id = showDialogEliminar!!
                    scope.launch {
                        db.collection("producto").document(id)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Producto eliminado", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Error al eliminar", Toast.LENGTH_SHORT).show()
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
            nombre = "Botana X",
            marca = "Delisa",
            categoria = "Botanas",
            subcategoria = "Cacahuates",
            descripcion = "Botana deliciosa",
            precio = 12.5,
            imagenUrl = ""
        ),
        ProductoFirestore(
            id = "2",
            nombre = "Dulce Y",
            marca = "El Cazador",
            categoria = "Dulces",
            subcategoria = "Chocolates",
            descripcion = "Dulce rico",
            precio = 8.0,
            imagenUrl = ""
        )
    )

    Box(
        Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(productosFalsos, key = { it.id }) { producto ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp),
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
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                producto.nombre,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "${producto.marca} • ${producto.categoria} • ${producto.subcategoria}",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Text(
                                "$${"%.2f".format(producto.precio)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFFFF0000)
                            )
                        }
                    }
                }
            }
        }

        // Preview FABs
        FloatingActionButton(
            onClick = { /* Acción */ },
            containerColor = Color(0xFFFF0000),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Agregar Producto")
        }


    }
}
