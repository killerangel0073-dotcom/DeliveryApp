package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.ProductoEntity
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.pow
import java.text.NumberFormat
import java.util.Locale



// ---------- CARRUSEL DE CATEGORIAS ----------
@Composable
fun CategoriaCarrusel(categorias: List<String>) {

    // Map de cada categoría a su drawable
    val imagenesCategorias: Map<String, Int> = mapOf(
        "Cacahuates" to R.drawable.cacahuates,
        "Semillas" to R.drawable.semillas,
        "Gomitas" to R.drawable.gomitas,
        "Chocolates" to R.drawable.chocolates,
        "Dulces" to R.drawable.dulces
    )

    val itemWidth = 140.dp
    val itemHeight = 100.dp
    val itemSpacing = 16.dp
    val containerWidthPx = remember { mutableStateOf(0f) }

    val initialIndex = categorias.size / 2
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    var selectedIndex by remember { mutableStateOf(initialIndex) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            contentPadding = PaddingValues(
                horizontal = with(LocalDensity.current) {
                    ((containerWidthPx.value / 2f) - (itemWidth.toPx() / 2f)).coerceAtLeast(0f).toDp()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    containerWidthPx.value = coordinates.size.width.toFloat()
                }
        ) {
            itemsIndexed(categorias, key = { index, _ -> index }) { index, categoria ->
                val isSelected = selectedIndex == index
                var scale by remember { mutableStateOf(1f) }

                // Animaciones suaves para selección
                val overlayAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 0.15f else 0.33f,
                    animationSpec = tween(1000)
                )
                // Elevación y borde animados
                val animatedElevation by animateDpAsState(
                    targetValue = if (isSelected) 16.dp else 8.dp,
                    animationSpec = tween(1000)
                )
                // Borde animado
                val animatedBorderColor by animateColorAsState(
                    targetValue = if (isSelected) Color.Red else Color.Transparent,
                    animationSpec = tween(1000)
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, animatedBorderColor),
                    elevation = CardDefaults.cardElevation(animatedElevation),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .size(itemWidth, itemHeight)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .onGloballyPositioned { coords ->
                            val itemCenter = coords.boundsInParent().center.x
                            val centerScreen = containerWidthPx.value / 2f
                            val distance = (centerScreen - itemCenter).absoluteValue
                            val factor = (distance / (containerWidthPx.value / 2f)).coerceIn(0f, 1f).pow(1.5f)
                            val maxScale = 1.15f
                            val minScale = 0.85f
                            scale = maxScale - (maxScale - minScale) * factor
                        }
                        .clip(RoundedCornerShape(16.dp)) // 👈 Recorta el contenido con la misma forma
                        .clickable {
                            selectedIndex = index
                            coroutineScope.launch { listState.animateScrollToItem(index) }
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(
                                imagenesCategorias[categoria] ?: R.drawable.repartidor // 👈 usa la imagen del mapa o fallback
                            ),
                            contentDescription = categoria,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = overlayAlpha)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                categoria,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}




// ---------- RESUMEN INVENTARIO ----------
@Composable
fun ResumenInventario(
    totalProductos: Int,
    valorTotal: Double,
    notificaciones: List<Notificacion>, // 🔹 lista real
    onNotificacionClick: () -> Unit
) {
    val totalNotificaciones = notificaciones.size













    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total de Piezas", fontSize = 14.sp, color = Color(0xFF555555))
                Spacer(modifier = Modifier.height(4.dp))
                Text("$totalProductos", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF0000))
            }

            AnimatedNotificationButton(totalNotificaciones, onNotificacionClick)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Valor Inventario",
                    fontSize = 14.sp,
                    color = Color(0xFF555555)
                )
                Spacer(modifier = Modifier.height(4.dp))

                val nf = NumberFormat.getNumberInstance(Locale.US).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }

                Text(
                    text = "$${nf.format(valorTotal)}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF0000)
                )
            }
        }
    }
}

// ---------- BOTÓN DE NOTIFICACIONES ----------
@Composable
fun AnimatedNotificationButton(
    notificaciones: Int,
    onNotificacionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var clicked by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (clicked) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val coroutineScope = rememberCoroutineScope()






    Box(modifier = modifier) {
        // Botón de notificaciones
        FloatingActionButton(
            onClick = {
                clicked = true
                onNotificacionClick()
                coroutineScope.launch {
                    kotlinx.coroutines.delay(150)
                    clicked = false
                }
            },
            containerColor = Color(0xFFFF0000),
            contentColor = Color.White,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        ) {
            Icon(Icons.Default.Notifications, contentDescription = "Notificaciones")
        }

        // Badge solo si hay notificaciones
        if (notificaciones > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd) // ✅ Esto lo pone arriba a la derecha del FAB
                    .offset(x = 6.dp, y = (-6).dp) // Ajusta para que quede sobre el borde
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.Red, shape = RoundedCornerShape(50))
                ) {
                    Text(
                        text = notificaciones.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }









}






// ---------- UI PRINCIPAL PURA (para preview y pruebas) ----------
@Composable
fun PantallaInventarioContent(
    navController: NavController,
    plantillaProductos: List<Plantilla_Producto>,
    listaDeNotificaciones: List<Notificacion> = emptyList(),
    rutaAsignada: String? = null, // 🆕 agregamos esto
    rutaCargada: Boolean = true // 🆕 indica si ya cargamos/intentamos cargar la ruta
) {



    val categorias = listOf("Cacahuates", "Semillas", "Gomitas", "Chocolates", "Dulces")
    val totalProductos = plantillaProductos.sumOf { it.cantidad }
    val valorTotal = plantillaProductos.sumOf { it.cantidad * it.precio }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {

            ResumenInventario(
                totalProductos = totalProductos,
                valorTotal = valorTotal,
                notificaciones = listaDeNotificaciones,
                onNotificacionClick = { navController.navigate("NOTIFICACIONES") }
            )

            Spacer(modifier = Modifier.height(16.dp))
            CategoriaCarrusel(categorias)
            Spacer(modifier = Modifier.height(16.dp))



            // Contenido de productos o mensajes
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // ocupa solo el espacio disponible
                contentAlignment = Alignment.Center
            ) {
                when {
                    !rutaCargada -> {
                        // Indicador de carga **solo en el área de productos**
                        CircularProgressIndicator(color = Color(0xFFFF0000))
                    }
                    rutaAsignada == null -> {
                        Text(
                            text = "Usuario sin ruta asignada",
                            color = Color.Gray,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    plantillaProductos.isEmpty() -> {
                        Text(
                            text = "Aún no tienes productos en tu almacén",
                            color = Color.Gray,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(plantillaProductos) { producto ->
                                val totalProducto = producto.cantidad * producto.precio
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = producto.imagenUrl,
                                            placeholder = painterResource(R.drawable.repartidor),
                                            error = painterResource(R.drawable.repartidor),
                                            contentDescription = producto.nombre,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                producto.nombre,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                color = Color.Black
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "Precio: $${producto.precio}",
                                                fontSize = 14.sp,
                                                color = Color(0xFF555555)
                                            )
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(end = 12.dp)
                                        ) {
                                            Text(
                                                "${producto.cantidad}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color(0xFFFF0000)
                                            )
                                            Text(
                                                "Piezas",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF333333)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                "$${"%.2f".format(totalProducto)}",
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 16.sp,
                                                color = Color(0xFFFF0000)
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

        // Botón flotante
        FloatingActionButton(
            onClick = { navController.navigate("LISTA PRODUCTOS") },
            containerColor = Color(0xFFFF0000),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.ShoppingCart, contentDescription = "Agregar Producto")
        }
    }
}



fun ProductoEntity.toModel(): Plantilla_Producto {
    return Plantilla_Producto(
        id = this.id,
        nombre = this.nombre,
        precio = this.precio,
        cantidad = this.cantidadDisponible,   // 👈 ahora coincide con tu modelo
        imagenUrl = this.imagenUrl ?: ""
    )
}




// ---------- UI CON FLOW PARA PRODUCCIÓN ----------
@Composable
fun PantallaInventario(
    navController: NavController,
    inventarioRepo: RepositoryInventario
) {
    val productosEntityState = inventarioRepo
        .obtenerProductosLocal()
        .collectAsState(initial = emptyList())

    val productos = productosEntityState.value.map { it.toModel() }

    val notificaciones = remember { mutableStateListOf<Notificacion>() }

    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    val coroutineScope = rememberCoroutineScope()

    // Estado de la ruta
    var rutaAsignada by remember { mutableStateOf<String?>(null) }
    var rutaCargada by remember { mutableStateOf(false) } // saber si ya intentamos cargar

    val formatoFecha = remember {
        java.text.SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy, hh:mm a", java.util.Locale("es", "MX"))
    }

    LaunchedEffect(uid) {
        if (uid == null) {
            rutaAsignada = null
            rutaCargada = true
            return@LaunchedEffect
        }
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        db.collection("users")
            .whereEqualTo("uid", uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val userDoc = querySnapshot.documents.firstOrNull()
                if (userDoc == null) {
                    rutaAsignada = null
                    rutaCargada = true
                    return@addOnSuccessListener
                }

                val rutaAsignadaRef = userDoc.getDocumentReference("rutaAsignada")

                if (rutaAsignadaRef == null) {
                    rutaAsignada = null
                    rutaCargada = true
                } else {
                    rutaAsignadaRef.get().addOnSuccessListener { rutaSnap ->
                        val almacenRef = rutaSnap.getDocumentReference("almacenAsignado")
                        if (almacenRef == null) {
                            rutaAsignada = null
                            rutaCargada = true
                            return@addOnSuccessListener
                        }

                        almacenRef.get().addOnSuccessListener { almacenSnap ->
                            val nombreAlmacen = almacenSnap.getString("nombre")
                            if (!nombreAlmacen.isNullOrEmpty()) {
                                rutaAsignada = nombreAlmacen





                                // 👇 Aquí escuchamos las notificaciones en tiempo real
                                db.collection("ordenesTransferencia")
                                    .whereEqualTo("destino", nombreAlmacen)
                                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                                    .addSnapshotListener { snapshots, error ->
                                        if (error != null) return@addSnapshotListener
                                        if (snapshots != null) {


                                            val nuevas = snapshots.documents.mapNotNull { doc ->
                                                val estado = doc.getString("estado")
                                                if (estado == "PENDIENTE") { // ✅ solo pendientes
                                                    val fecha = doc.getTimestamp("timestamp")?.toDate()
                                                    val fechaFormateada = fecha?.let { formatoFecha.format(it) } ?: ""
                                                    Notificacion(
                                                        id = doc.id,
                                                        titulo = "Carga de Almacén",
                                                        mensaje = "Nueva carga pendiente",
                                                        fecha = fechaFormateada,
                                                        esCarga = true,
                                                        aceptada = false
                                                    )
                                                } else {
                                                    null
                                                }
                                            }
                                            notificaciones.clear()
                                            notificaciones.addAll(nuevas)
                                        }
                                    }






                            } else {
                                rutaAsignada = null
                            }
                            rutaCargada = true
                        }.addOnFailureListener {
                            rutaAsignada = null
                            rutaCargada = true
                        }
                    }.addOnFailureListener {
                        rutaAsignada = null
                        rutaCargada = true
                    }
                }
            }
            .addOnFailureListener {
                rutaAsignada = null
                rutaCargada = true
            }
    }

    // Siempre renderizamos la pantalla completa (el contenido decide qué mostrar en el área de productos)
    PantallaInventarioContent(navController, productos, notificaciones, rutaAsignada, rutaCargada)
}




// ---------- INTERFAZ DE REPO ----------
interface InventarioRepoInterface {
    fun obtenerProductosLocal(): Flow<List<Plantilla_Producto>>
}

// ---------- FAKE REPO PARA PRUEBAS ----------
class FakeInventarioRepository : InventarioRepoInterface {
    override fun obtenerProductosLocal(): Flow<List<Plantilla_Producto>> = flowOf(
        listOf(
            Plantilla_Producto("1", "Botana X", 12.5, 30, 5,""),
            Plantilla_Producto("2", "Bebida Y", 15.0, 2, 2,""),
            Plantilla_Producto("3", "Dulce Z", 8.0, 5, 5,"")
        )
    )
}

// ---------- PREVIEW ----------
@Preview(showBackground = true)
@Composable
fun PantallaInventarioPreview() {
    val navController = rememberNavController()
    val productosFalsos = listOf(
        Plantilla_Producto("1", "Botana X", 12.5, 3, 1,""),
        Plantilla_Producto("2", "Bebida Y", 15.0, 2, 3,""),
        Plantilla_Producto("3", "Dulce Z", 8.0, 5, 8,"")
    )
    PantallaInventarioContent(navController, productosFalsos)
}
