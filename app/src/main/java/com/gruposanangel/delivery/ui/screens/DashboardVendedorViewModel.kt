package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class DashboardVendedorUiState(
    val isLoading: Boolean = false,
    val enRuta: Boolean = false,
    val horaInicioRuta: Long? = null,
    val tiempoTranscurrido: String = "00:00:00",
    val ventaDia: Double = 0.0,
    val clientesDia: Int = 0,
    val ticketPromedioDia: Double = 0.0,
    val ventaSemana: Double = 0.0,
    val ventaBloque: Double = 0.0,
    val ventasPorDiaSemana: List<Double> = listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0), // L, M, M, J, V, S
    val metaDia: Double = 11666.0,
    val error: String? = null
)

class DashboardVendedorViewModel(
    private val ventaRepository: VentaRepository,
    private val usuarioRepository: RepositoryUsuario,
    private val userId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardVendedorUiState())
    val uiState: StateFlow<DashboardVendedorUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private var salesListener: ListenerRegistration? = null

    init {
        cargarEstadoJornada()
        iniciarContadorTiempo()
        observarVentasReactivo()
        escucharVentasNube()
        
        // 🔥 Sincronización proactiva inicial
        viewModelScope.launch {
            ventaRepository.descargarVentasDia(userId)
        }
    }

    private fun cargarEstadoJornada() {
        viewModelScope.launch {
            try {
                val doc = db.collection("jornadas").document(userId).get().await()
                if (doc.exists()) {
                    val activo = doc.getBoolean("activo") ?: false
                    val inicio = doc.getTimestamp("horaInicio")?.toDate()?.time
                    _uiState.update { it.copy(enRuta = activo, horaInicioRuta = if (activo) inicio else null) }
                }
            } catch (e: Exception) { }
        }
    }

    private fun iniciarContadorTiempo() {
        viewModelScope.launch {
            while (true) {
                val inicio = _uiState.value.horaInicioRuta
                if (_uiState.value.enRuta && inicio != null) {
                    val diff = System.currentTimeMillis() - inicio
                    val horas = diff / 3600000
                    val minutos = (diff % 3600000) / 60000
                    val segundos = (diff % 60000) / 1000
                    val timeStr = String.format(Locale.US, "%02d:%02d:%02d", horas, minutos, segundos)
                    _uiState.update { it.copy(tiempoTranscurrido = timeStr) }
                } else {
                    _uiState.update { it.copy(tiempoTranscurrido = "00:00:00") }
                }
                delay(1000)
            }
        }
    }

    fun toggleRuta(activar: Boolean) {
        viewModelScope.launch {
            val now = Date()
            val prevEnRuta = _uiState.value.enRuta
            val prevInicio = _uiState.value.horaInicioRuta
            
            _uiState.update { it.copy(enRuta = activar, horaInicioRuta = if (activar) now.time else null) }
            
            try {
                val data = mapOf(
                    "activo" to activar,
                    "horaInicio" to if (activar) Timestamp(now) else null,
                    "vendedorId" to userId,
                    "ultimaActualizacion" to Timestamp(now)
                )
                db.collection("jornadas").document(userId).set(data).await()
                
                // 🔔 Enviar notificación a directivos
                notificarEstadoRuta(activar, now)
                
            } catch (e: Exception) {
                _uiState.update { it.copy(enRuta = prevEnRuta, horaInicioRuta = prevInicio, error = "Error al sincronizar con el servidor") }
            }
        }
    }

    private fun notificarEstadoRuta(activar: Boolean, fecha: Date) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Obtener datos del vendedor directamente de Firestore
                val vendedorDoc = db.collection("users").document(userId).get().await()
                val nombreVendedor = vendedorDoc.getString("nombre") ?: "Vendedor"

                // Seguridad: Asegurar que nunca viaje un "" vacío en la imagen
                val fotoDoc = vendedorDoc.getString("photo_url")
                val fotoVendedor = if (!fotoDoc.isNullOrEmpty()) {
                    fotoDoc
                } else {

                    "https://intercar.com.mx/wp-content/uploads/2021/06/rutas-de-distribucion-logistica.png"

                }

                // 2. Formatear la hora de forma limpia (Ejemplo: 07:45 am)
                val fmtHora = SimpleDateFormat("hh:mm a", Locale.forLanguageTag("es-MX"))
                val horaStr = fmtHora.format(fecha)

                // 🚀 ACOMODO DE TEXTO: La hora se incrusta en el título para que Android no la recorte
                val titulo = if (activar) "🚀 Inicio Ruta  $horaStr" else "🏁 Fin Jornada [$horaStr]"
                val mensaje = "El colaborador $nombreVendedor ya se encuentra activo en su ruta de distribución."

                // 3. Obtener tokens de directivos
                val tokens = usuarioRepository.obtenerTokensDirectivos()
                if (tokens.isEmpty()) {
                    Log.w("DashboardVM", "No hay directivos para notificar")
                    return@launch
                }

                // 4. Enviar la petición POST a tu Cloud Function
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("tokens", JSONArray(tokens))
                    put("titulo", titulo)
                    put("mensaje", mensaje)
                    put("imagen", fotoVendedor)
                    put("ventaId", "JORNADA")
                }

                val request = Request.Builder()
                    .url("https://us-central1-appventas--san-angel.cloudfunctions.net/enviarNotificacion")
                    .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d("DashboardVM", "✅ Notificación masiva de jornada enviada con éxito")
                    } else {
                        Log.e("DashboardVM", "❌ Error en Cloud Function: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardVM", "❌ Error crítico notificacion jornada", e)
            }
        }
    }

    private fun observarVentasReactivo() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val weekOfYear = cal.get(Calendar.WEEK_OF_YEAR)
            val blockNumber = ((weekOfYear - 1) / 4)
            cal.set(Calendar.WEEK_OF_YEAR, (blockNumber * 4) + 1)
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val inicioBloque = cal.timeInMillis

            ventaRepository.obtenerVentasPorPeriodoFlow(userId, inicioBloque, System.currentTimeMillis() + 86400000)
                .collect { todasLasVentas ->
                    val ventasMias = todasLasVentas
                    val now = System.currentTimeMillis()
                    val calCalc = Calendar.getInstance()
                    
                    calCalc.timeInMillis = now
                    calCalc.set(Calendar.HOUR_OF_DAY, 0); calCalc.set(Calendar.MINUTE, 0); calCalc.set(Calendar.SECOND, 0); calCalc.set(Calendar.MILLISECOND, 0)
                    val iniHoy = calCalc.timeInMillis
                    
                    calCalc.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    val iniSemana = calCalc.timeInMillis

                    val ventasHoy = ventasMias.filter { it.fecha >= iniHoy }
                    val ventasSemana = ventasMias.filter { it.fecha >= iniSemana }

                    val ventasPorDia = MutableList(6) { 0.0 }
                    val calDia = Calendar.getInstance()
                    ventasSemana.forEach { venta ->
                        calDia.timeInMillis = venta.fecha
                        val dayOfWeek = calDia.get(Calendar.DAY_OF_WEEK)
                        val index = when (dayOfWeek) {
                            Calendar.MONDAY -> 0
                            Calendar.TUESDAY -> 1
                            Calendar.WEDNESDAY -> 2
                            Calendar.THURSDAY -> 3
                            Calendar.FRIDAY -> 4
                            Calendar.SATURDAY -> 5
                            else -> -1
                        }
                        if (index != -1) {
                            ventasPorDia[index] += venta.total
                        }
                    }

                    val totalHoy = ventasHoy.sumOf { it.total }
                    val clientesHoy = ventasHoy.map { it.clienteId }.distinct().size
                    val ticketPromedio = if (ventasHoy.isNotEmpty()) totalHoy / ventasHoy.size else 0.0

                    _uiState.update { it.copy(
                        isLoading = false,
                        ventaDia = totalHoy,
                        clientesDia = clientesHoy,
                        ticketPromedioDia = ticketPromedio,
                        ventaSemana = ventasSemana.sumOf { v -> v.total },
                        ventaBloque = ventasMias.sumOf { v -> v.total },
                        ventasPorDiaSemana = ventasPorDia
                    ) }
                }
        }
    }

    private fun escucharVentasNube() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val inicio = cal.time

        salesListener?.remove()
        salesListener = db.collection("ventas")
            .whereGreaterThanOrEqualTo("fecha", inicio)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    viewModelScope.launch {
                        ventaRepository.descargarVentasDia(userId)
                    }
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        salesListener?.remove()
    }
}
