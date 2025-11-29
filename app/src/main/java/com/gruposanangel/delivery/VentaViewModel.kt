package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaRepository
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.tasks.await
import java.io.IOException


class VentaViewModel(
    private val repositoryInventario: RepositoryInventario,
    private val ventaRepository: VentaRepository
) : ViewModel() {

    // --- Estado de la UI ---

    private val _productosEnCarrito = mutableStateListOf<Plantilla_Producto>()
    val productosEnCarrito: List<Plantilla_Producto> get() = _productosEnCarrito

    private val _estadoRuta = MutableStateFlow<EstadoRuta>(EstadoRuta.Cargando)
    val estadoRuta: StateFlow<EstadoRuta> = _estadoRuta.asStateFlow()

    private val _estaProcesando = MutableStateFlow(false)
    val estaProcesando: StateFlow<Boolean> = _estaProcesando.asStateFlow()


    // --- Inicialización ---

    fun cargarProductosIniciales(listaBase: List<Plantilla_Producto>) {
        if (_productosEnCarrito.isEmpty()) {
            _productosEnCarrito.addAll(listaBase.map { it.copy(cantidad = 0) })
        }
    }

    fun verificarRutaAsignada() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                _estadoRuta.value = EstadoRuta.SinRuta
                return@launch
            }

            try {
                val db = FirebaseFirestore.getInstance()

                val userQuery = db.collection("users")
                    .whereEqualTo("uid", uid)
                    .get().await()

                val userDoc = userQuery.documents.firstOrNull()
                if (userDoc == null) {
                    _estadoRuta.value = EstadoRuta.SinRuta
                    return@launch
                }

                val rutaRef = userDoc.get("rutaAsignada") as? com.google.firebase.firestore.DocumentReference
                if (rutaRef == null) {
                    _estadoRuta.value = EstadoRuta.SinRuta
                    return@launch
                }

                val rutaSnap = rutaRef.get().await()
                val almacenRef = rutaSnap.get("almacenAsignado") as? com.google.firebase.firestore.DocumentReference
                    ?: run {
                        _estadoRuta.value = EstadoRuta.SinRuta
                        return@launch
                    }

                val almacenSnap = almacenRef.get().await()
                val nombreAlmacen = almacenSnap.getString("nombre") ?: ""
                val idAlmacen = almacenSnap.id

                if (nombreAlmacen.isNotEmpty()) {
                    _estadoRuta.value = EstadoRuta.ConRuta(nombreAlmacen, idAlmacen)
                } else {
                    _estadoRuta.value = EstadoRuta.SinRuta
                }

            } catch (e: Exception) {
                _estadoRuta.value = EstadoRuta.Error(e.message ?: "Error desconocido")
            }
        }
    }


    // --- Carrito ---

    fun actualizarCantidad(index: Int, nuevaCantidad: Int) {
        if (index in _productosEnCarrito.indices) {
            val producto = _productosEnCarrito[index]
            val cantidadSegura = nuevaCantidad.coerceIn(0, producto.cantidadDisponible)
            _productosEnCarrito[index] = producto.copy(cantidad = cantidadSegura)
        }
    }


    // --- PROCESO DE VENTA COMPLETO ---

    fun procesarVenta(
        clienteId: String,
        clienteNombre: String,
        clienteFotoUrl: String?,
        metodoPago: String,
        hayInternet: Boolean,
        onResultado: (Boolean, String, Long) -> Unit
    ) {
        if (_estaProcesando.value) return

        _estaProcesando.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uidVendedor = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val productosVenta = _productosEnCarrito.filter { it.cantidad > 0 }

                // 1. Guardar Venta Local
                val ventaLocalId = ventaRepository.guardarVentaLocal(
                    clienteId = clienteId,
                    clienteNombre = clienteNombre,
                    clienteImagenUrl = clienteFotoUrl,
                    productos = productosVenta,
                    total = productosVenta.sumOf { it.precio * it.cantidad },
                    metodoPago = metodoPago,
                    vendedorId = uidVendedor
                )

                if (ventaLocalId <= 0) {
                    withContext(Dispatchers.Main) {
                        _estaProcesando.value = false
                        onResultado(false, "No se pudo guardar la venta local", 0)
                    }
                    return@launch
                }

                // 2. Actualizar inventario
                productosVenta.forEach {
                    repositoryInventario.actualizarCantidadProducto(it.id, it.cantidad)
                }

                // 3. Sincronizar con servidor
                var mensajeFinal = "Venta guardada localmente"
                val estadoRutaActual = _estadoRuta.value

                if (hayInternet && estadoRutaActual is EstadoRuta.ConRuta) {

                    val (exito, response) = guardarVentaEnServidorSuspend(
                        ventaLocalId,
                        clienteId,
                        clienteNombre,
                        productosVenta,
                        metodoPago,
                        uidVendedor,
                        estadoRutaActual.almacenId
                    )

                    if (exito) {
                        val firestoreId = JSONObject(response).optString("ventaId", null)

                        if (firestoreId != null) {
                            ventaRepository.marcarVentaConFirestoreId(ventaLocalId, firestoreId)
                            mensajeFinal = "Venta sincronizada correctamente"
                        } else {
                            mensajeFinal = "Sincronizada pero sin ID válido"
                        }
                    } else {
                        mensajeFinal = "Guardada local (error de sincronización)"
                    }
                }

                // 4. Finalizar
                // ---- Aquí se limpia carrito y se llama onResultado ----
                // 4. Finalizar
                withContext(Dispatchers.Main) {

                    // Obtener token del supervisor
                    val tokenSupervisor = obtenerTokenSupervisor()
                    if (!tokenSupervisor.isNullOrEmpty()) {
                        val totalVenta = productosVenta.sumOf { it.precio * it.cantidad }


                        // Determinar ID de venta a enviar
                        val ventaIdParaNotificacion = if (hayInternet && estadoRutaActual is EstadoRuta.ConRuta) {
                            // Intentar usar firestoreId si existe
                            val (exito, response) = guardarVentaEnServidorSuspend(
                                ventaLocalId,
                                clienteId,
                                clienteNombre,
                                productosVenta,
                                metodoPago,
                                uidVendedor,
                                estadoRutaActual.almacenId
                            )
                            if (exito) JSONObject(response).optString("ventaId", ventaLocalId.toString())
                            else ventaLocalId.toString()
                        } else {
                            ventaLocalId.toString()
                        }

                        // Enviar notificación
                        enviarNotificacionVenta(
                            token = tokenSupervisor,
                            vendedorNombre = obtenerNombreVendedor(),
                            rutaAsignada = obtenerNombreRuta(),


                            clienteNombre = clienteNombre,
                            totalVenta = totalVenta,
                            clienteFotoUrl = clienteFotoUrl,
                            ventaId = ventaIdParaNotificacion

                        )

                    }

                    limpiarCarrito()
                    _estaProcesando.value = false
                    onResultado(true, mensajeFinal, ventaLocalId)
                }


            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _estaProcesando.value = false
                    onResultado(false, e.message ?: "Error desconocido", 0)
                }
            }
        }
    }


    private fun limpiarCarrito() {
        _productosEnCarrito.replaceAll { it.copy(cantidad = 0) }


    }



    fun enviarNotificacionVenta(
        token: String,
        vendedorNombre: String,
        rutaAsignada: String,

        clienteNombre: String,
        totalVenta: Double,
        clienteFotoUrl: String?,
        ventaId: String // <-- nuevo parámetro
    ) {
        val client = OkHttpClient()

        //val mensaje = "$rutaAsignada al cliente $clienteNombre por $${"%.2f".format(totalVenta)}"
        //val mensaje = "📦  $rutaAsignada al cliente $clienteNombre por 💰 $${"%.2f".format(totalVenta)}"
        val mensaje = """
                  📦 RUTA: $rutaAsignada
                  👤 CLIENTE: $clienteNombre
                  💰 TOTAL: $${"%.2f".format(totalVenta)}
                   """.trimIndent()





        val json = JSONObject().apply {
            put("token", token)
            put("titulo", "Nueva venta registrada")
            put("mensaje", mensaje)
            put("imagen", clienteFotoUrl ?: "https://upload.wikimedia.org/wikipedia/commons/thumb/7/74/Dominos_pizza_logo.svg/768px-Dominos_pizza_logo.svg.png")
            put("estilo", "bigpicture") // Le indicamos a la Cloud Function que use BigPicture
            put("ventaId", ventaId) // <-- aquí enviamos el ID
        }

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://us-central1-appventas--san-angel.cloudfunctions.net/enviarNotificacion")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("Notificacion", "Error enviando notificación", e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                Log.d("Notificacion", "Respuesta: ${response.body?.string()}")
            }
        })
    }


    //

    private suspend fun obtenerNombreRuta(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return "Sin ruta"

        return try {
            val db = FirebaseFirestore.getInstance()
            val userDoc = db.collection("users").document(uid).get().await()
            val rutaRef = userDoc.get("rutaAsignada") as? com.google.firebase.firestore.DocumentReference
                ?: return "Sin ruta"

            val rutaSnap = rutaRef.get().await()
            rutaSnap.getString("nombre") ?: "Sin ruta"

        } catch (e: Exception) {
            "Sin ruta"
        }
    }


    private suspend fun obtenerNombreVendedor(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return "Vendedor"
        return try {
            val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
            doc.getString("nombre") ?: "Vendedor"
        } catch (e: Exception) {
            "Vendedor"
        }
    }


    //
    private suspend fun obtenerTokenSupervisor(): String? {
        return try {
            val db = FirebaseFirestore.getInstance()
            val querySnapshot = db.collection("users")
                .whereEqualTo("puestoTrabajo", "CEO1.1")
                .whereEqualTo("activo", true)
                .get()
                .await()

            val supervisorDoc = querySnapshot.documents.firstOrNull()
            supervisorDoc?.get("fcmTokens")?.let { tokens ->
                if (tokens is List<*>) tokens.firstOrNull() as? String
                else null
            }
        } catch (e: Exception) {
            null
        }
    }



    // --- NETWORKING ---

    private suspend fun guardarVentaEnServidorSuspend(
        ventaLocalId: Long,
        clienteId: String,
        clienteNombre: String,
        productos: List<Plantilla_Producto>,
        metodoPago: String,
        vendedorId: String,
        almacenVendedorId: String
    ): Pair<Boolean, String> {

        val url = "https://us-central1-appventas--san-angel.cloudfunctions.net/registrarVenta"
        val client = OkHttpClient()

        val json = JSONObject().apply {
            put("ventaLocalId", ventaLocalId)
            put("clienteId", clienteId)
            put("clienteNombre", clienteNombre)
            put("productos", JSONArray().apply {
                productos.forEach { p ->
                    put(JSONObject().apply {
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

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        return try {
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) Pair(true, respBody)
            else Pair(false, "Error ${response.code}: ${response.message}")
        } catch (e: Exception) {
            Pair(false, e.message ?: "Error desconocido")
        }
    }
}


// Estados para UI
sealed class EstadoRuta {
    object Cargando : EstadoRuta()
    object SinRuta : EstadoRuta()
    data class Error(val mensaje: String) : EstadoRuta()
    data class ConRuta(val nombreAlmacen: String, val almacenId: String) : EstadoRuta()
}


// Factory
class VentaViewModelFactory(
    private val repositoryInventario: RepositoryInventario,
    private val ventaRepository: VentaRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VentaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VentaViewModel(repositoryInventario, ventaRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}







//

class VentaViewModelPreview : ViewModel() {

    private val _productos = mutableStateListOf<Plantilla_Producto>()
    val productosEnCarrito: List<Plantilla_Producto> get() = _productos

    private val _estadoRuta = MutableStateFlow<EstadoRuta>(EstadoRuta.ConRuta("Almacén Demo", "PREVIEW"))
    val estadoRuta = _estadoRuta.asStateFlow()

    private val _estaProcesando = MutableStateFlow(false)
    val estaProcesando = _estaProcesando.asStateFlow()

    fun cargarProductosIniciales(productos: List<Plantilla_Producto>) {
        _productos.clear()
        _productos.addAll(productos)
    }

    fun actualizarCantidad(index: Int, cantidad: Int) {
        if (index in _productos.indices) {
            val p = _productos[index]
            _productos[index] = p.copy(cantidad = cantidad)
        }
    }

    fun procesarVenta(
        clienteId: String,
        clienteNombre: String,
        clienteFotoUrl: String?,
        metodoPago: String,
        hayInternet: Boolean,
        onResultado: (Boolean, String, Long) -> Unit
    ) {
        onResultado(true, "Venta simulada en preview", 9999L)
    }
}



