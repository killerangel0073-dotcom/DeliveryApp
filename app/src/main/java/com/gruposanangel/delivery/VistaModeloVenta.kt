package com.gruposanangel.delivery.ui.screens

import ProductoTicketDetalle
import TicketVentaCompleto
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.data.VentaRepository
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Date
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.TimeZone


// ViewModel para manejar la lógica de ventas, incluyendo carga de productos y sincronización con Firestore.
class VistaModeloVenta(

    private val repositoryInventario: RepositoryInventario,
    private val ventaRepository: VentaRepository
) : ViewModel() {





private val firestore = FirebaseFirestore.getInstance()

private val _productos = MutableStateFlow<List<Plantilla_Producto>>(emptyList())
val productos: StateFlow<List<Plantilla_Producto>> get() = _productos

init {
    cargarProductos()
}



private val _ventasPeriodo = MutableStateFlow<List<VentaEntity>>(emptyList())
val ventasPeriodo: StateFlow<List<VentaEntity>> = _ventasPeriodo

fun cargarVentasPorPeriodo(fechaInicio: Date, fechaFin: Date) {
    viewModelScope.launch {
        val ventas = ventaRepository.obtenerVentasPorPeriodo(fechaInicio.time, fechaFin.time)
        _ventasPeriodo.value = ventas
    }
}







// Carga productos desde Firestore y los expone a través de un StateFlow.
private fun cargarProductos() {
    firestore.collection("producto")
        .get()
        .addOnSuccessListener { result ->
            val lista = result.map { doc ->
                Plantilla_Producto(
                    id = doc.id,
                    nombre = doc.getString("nombre") ?: "",
                    precio = doc.getDouble("precio") ?: 0.0,
                    cantidad = 0,
                    imagenUrl = doc.getString("imagenUrl") ?: ""
                )
            }
            _productos.value = lista
        }
        .addOnFailureListener { e ->
            e.printStackTrace()
        }
}


// Guarda una venta localmente en Room y retorna el ID generado.
fun guardarVentaLocal(
    clienteId: String,
    clienteNombre: String,
    clienteImagenUrl: String?,
    productos: List<Plantilla_Producto>,
    metodoPago: String,
    vendedorId: String,
    onResult: (Boolean, Long) -> Unit
) {
    viewModelScope.launch {
        try {
            val total = productos.sumOf { it.precio * it.cantidad }
            val ventaId = ventaRepository.guardarVentaLocal(clienteId, clienteNombre, clienteImagenUrl,productos, total, metodoPago, vendedorId)
            onResult(true, ventaId)
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(false, -1)
        }
    }
}






suspend fun obtenerTicketDirecto(ticketId: Long): TicketVentaCompleto? {
    // Obtenemos la venta usando el método existente en el repositorio
    val venta = ventaRepository.obtenerVentaPorId(ticketId) ?: return null

    // Obtenemos los detalles de la venta
    val detalles = ventaRepository.obtenerDetallesDeVenta(ticketId)

    // Mapear los detalles a productos para el TicketVentaCompleto
    val productos = detalles.map { detalle ->
        ProductoTicketDetalle(
            nombre = detalle.nombre,
            cantidad = detalle.cantidad,
            precio = detalle.precio
        )
    }

    return TicketVentaCompleto(
        numeroTicket = venta.id.toString(),
        cliente = venta.clienteNombre,
        total = venta.total,
        fecha = Date(venta.fecha),
        sincronizado = venta.sincronizado,
        fotoCliente = venta.clienteImagenUrl ?: "",
        productos = productos
    )
}










// Obtiene los detalles de una venta específica por su ID.
suspend fun obtenerDetallesDeVentaSuspend(ticketId: Long) =
    ventaRepository.obtenerDetallesDeVenta(ticketId)




// Carga ventas del día actual.
fun cargarVentasHoy() {
    val tz = TimeZone.getDefault() // usa la misma zona horaria del dispositivo
    val calendar = Calendar.getInstance(tz)

    // Inicio del día
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val inicio = calendar.timeInMillis

    // Fin del día
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)
    val fin = calendar.timeInMillis

    // Llama al repositorio usando los timestamps correctos
    cargarVentasPorPeriodo(Date(inicio), Date(fin))
}




//
suspend fun marcarVentaSincronizada(ventaId: Long, firestoreId: String) {
    ventaRepository.marcarVentaConFirestoreId(ventaId, firestoreId)
}





suspend fun obtenerAlmacenVendedor(uid: String): String? {
    return repositoryInventario.getAlmacenVendedor(uid)
}






























// Función suspend para llamar a la Cloud Function y obtener resultado
suspend fun guardarVentaEnServidorSuspend(
    ventaLocalId: Long,
    clienteId: String,
    clienteNombre: String,
    productos: List<Plantilla_Producto>,
    metodoPago: String,
    vendedorId: String,
    almacenVendedorId: String
): Pair<Boolean, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val url = "https://us-central1-appventas--san-angel.cloudfunctions.net/registrarVenta"
        val client = okhttp3.OkHttpClient()

        val json = org.json.JSONObject().apply {
            put("ventaLocalId", ventaLocalId)
            put("clienteId", clienteId)
            put("clienteNombre", clienteNombre)
            put("productos", org.json.JSONArray().apply {
                productos.forEach { p ->
                    put(org.json.JSONObject().apply {
                        put("id", p.id)
                        put("nombre", p.nombre)
                        put("precio", p.precio)
                        put("cantidad", p.cantidad)
                        put("imagenUrl", p.imagenUrl ?: "")
                    })
                }
            })
            put("metodoPago", metodoPago)
            put("vendedorId", vendedorId)
            put("almacenVendedorId", almacenVendedorId)
        }

        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = okhttp3.Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val respBody = response.body?.string() ?: ""
            Pair(true, respBody)
        } else {
            Pair(false, "Error ${response.code}: ${response.message}")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(false, e.message ?: "Error desconocido")
    }
}







suspend fun obtenerVentaEntityPorId(ticketId: Long): VentaEntity? {
    return ventaRepository.obtenerVentaPorId(ticketId)
}










}

