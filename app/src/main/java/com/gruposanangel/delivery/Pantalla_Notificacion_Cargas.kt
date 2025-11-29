package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gruposanangel.delivery.model.Plantila_carga
import com.gruposanangel.delivery.model.Plantilla_Producto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


// 🔹 Modelo de notificación/carga
data class Notificacion(
    val id: String = "",
    val titulo: String,
    val mensaje: String,
    val fecha: String,
    val esCarga: Boolean = false,
    val aceptada: Boolean = false
)
@Composable
fun PantallaNotificaciones(navController: NavController) {

    val formatoFecha = SimpleDateFormat("EEEE, dd 'de' MMMM 'de' yyyy, hh:mm a", Locale("es", "MX"))
    val isPreview = LocalInspectionMode.current

    var isLoading by remember { mutableStateOf(true) }
    val notificaciones = remember { mutableStateListOf<Notificacion>() }

    if (!isPreview) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val db = FirebaseFirestore.getInstance()

        if (uid != null) {
            db.collection("users")
                .whereEqualTo("uid", uid)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val userDoc = querySnapshot.documents.firstOrNull()
                    if (userDoc != null) {
                        val rutaAsignadaRef = userDoc.getDocumentReference("rutaAsignada")
                        if (rutaAsignadaRef != null) {
                            rutaAsignadaRef.get().addOnSuccessListener { rutaSnap ->
                                val almacenRef = rutaSnap.getDocumentReference("almacenAsignado")
                                if (almacenRef != null) {
                                    almacenRef.get().addOnSuccessListener { almacenSnap ->
                                        val nombreAlmacen = almacenSnap.getString("nombre")
                                        if (!nombreAlmacen.isNullOrEmpty()) {
                                            db.collection("ordenesTransferencia")
                                                .whereEqualTo("destino", nombreAlmacen)
                                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                                .get() // <--- Solo un get() para simplificar
                                                .addOnSuccessListener { snapshots ->
                                                    val nuevas = snapshots.documents.map { doc ->
                                                        val fechaTimestamp = doc.getTimestamp("timestamp")?.toDate()
                                                        val fechaFormateada = fechaTimestamp?.let { formatoFecha.format(it) } ?: ""
                                                        Notificacion(
                                                            id = doc.id,
                                                            titulo = "Carga de Almacén",
                                                            mensaje = "Nueva carga asignada",
                                                            fecha = fechaFormateada,
                                                            esCarga = true,
                                                            aceptada = doc.getString("estado") == "ACEPTADA"
                                                        )
                                                    }
                                                    notificaciones.clear()
                                                    notificaciones.addAll(nuevas)
                                                    isLoading = false // <--- Siempre se actualiza
                                                }
                                                .addOnFailureListener {
                                                    isLoading = false
                                                }
                                        } else {
                                            isLoading = false // No hay almacen
                                        }
                                    }.addOnFailureListener {
                                        isLoading = false
                                    }
                                } else {
                                    isLoading = false
                                }
                            }.addOnFailureListener {
                                isLoading = false
                            }
                        } else {
                            isLoading = false
                        }
                    } else {
                        isLoading = false
                    }
                }
                .addOnFailureListener {
                    isLoading = false
                }
        } else {
            isLoading = false
        }
    }





    else {
        // Preview solo se agrega si la lista está vacía
        if (notificaciones.isEmpty()) {
            notificaciones.addAll(
                listOf(
                    Notificacion(
                        id = "1",
                        titulo = "Pedido #123",
                        mensaje = "Tu pedido está en camino 🚚",
                        fecha = "Hoy, 10:45 AM"
                    ),
                    Notificacion(
                        id = "2",
                        titulo = "Inventario",
                        mensaje = "Se agotaron piezas de Botanas",
                        fecha = "Ayer, 6:30 PM"
                    ),
                    Notificacion(
                        id = "3",
                        titulo = "Carga de Almacén",
                        mensaje = "Tienes una carga pendiente por aceptar",
                        fecha = "Hoy, 11:00 AM",
                        esCarga = true,
                        aceptada = false
                    ),
                    Notificacion(
                        id = "4",
                        titulo = "Carga de Almacén",
                        mensaje = "Carga aceptada exitosamente ✅",
                        fecha = "26/09/2025",
                        esCarga = true,
                        aceptada = true
                    )
                )
            )
        }
        isLoading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {




            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {





                    navController.navigate("delivery?screen=Inventario") {
                        launchSingleTop = true
                        popUpTo(0) { inclusive = true }
                    }





                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Regresar",
                        tint = Color(0xFFFF0000),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Notificaciones",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
            }

            Divider(color = Color.LightGray)

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFF0000))
                    }
                }
                notificaciones.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sin notificaciones",
                            fontSize = 18.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                else -> {



                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(notificaciones) { noti ->



                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (noti.esCarga) {
                                            // Crear objeto de carga
                                            val carga = Plantila_carga(
                                                id = noti.id,
                                                nombreCarga = noti.titulo,
                                                plantillaProductos = emptyList(), // aquí podrías cargar los productos de Firestore
                                                aceptada = noti.aceptada
                                            )

                                            // Guardar en el SavedStateHandle del backStackEntry actual
                                            navController.currentBackStackEntry?.savedStateHandle?.set("carga", carga)


                                            // Navegar al detalle asegurando singleTop
                                            navController.navigate("DETALLE_CARGA") {
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(4.dp)
                            ) {


                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (noti.esCarga) {
                                        val icon = if (noti.aceptada) Icons.Default.CheckCircle else Icons.Default.Notifications
                                        val tint = if (noti.aceptada) Color(0xFF4CAF50) else Color(0xFFFF0000)
                                        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = noti.titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = noti.mensaje, fontSize = 14.sp, color = Color(0xFF555555))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(text = noti.fecha, fontSize = 12.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PantallaNotificacionesPreview() {
    val navController = rememberNavController()
    PantallaNotificaciones(navController)
}
