package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.gruposanangel.delivery.data.RepositoryInventario
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
    val nombreVendedor: String = "",
    val photoUrl: String = "",
    val puestoTrabajo: String? = null,
    val rutaNombre: String = "Ruta General",
    val valorInventario: Double = 0.0,
    val ventasHoy: List<com.gruposanangel.delivery.data.VentaEntity> = emptyList(),
    val error: String? = null
)

class DashboardVendedorViewModel(
    private val ventaRepository: VentaRepository,
    private val usuarioRepository: RepositoryUsuario,
    private val inventarioRepository: RepositoryInventario,
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
        cargarDatosPerfil()
        observarInventario()
        
        // 🔥 Sincronización proactiva de la semana completa
        viewModelScope.launch {
            try {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                
                // Si hoy es domingo o el calendario devolvió un lunes futuro, retrocedemos 7 días
                if (cal.timeInMillis > System.currentTimeMillis()) {
                    cal.add(Calendar.DAY_OF_MONTH, -7)
                }
                
                ventaRepository.sincronizarVentasPeriodo(userId, cal.timeInMillis, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error sync inicial semana", e)
            }
        }
    }

    private fun cargarDatosPerfil() {
        usuarioRepository.getUsuarioActual()
            .onEach { user ->
                if (user != null) {
                    _uiState.update { it.copy(
                        nombreVendedor = user.nombre,
                        photoUrl = user.photoUrl ?: "",
                        puestoTrabajo = user.puestoTrabajo,
                        rutaNombre = user.ultimoAlmacenNombre ?: "Ruta General"
                    ) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observarInventario() {
        inventarioRepository.obtenerProductosLocal()
            .onEach { productos ->
                val valorTotal = productos.filter { it.id.contains("_") }
                    .sumOf { it.cantidadDisponible * it.precio }
                _uiState.update { it.copy(valorInventario = valorTotal) }
            }
            .launchIn(viewModelScope)
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

    fun finalizarJornadaYLiquidar(
        ventaTotal: Double,
        efectivoContado: Double,
        diferencia: Double,
        desgloseEfectivo: Map<Int, Int>,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val now = Date()
                
                // 1. Crear registro de Liquidación en Firestore
                val liquidacionData = mapOf(
                    "vendedorId" to userId,
                    "vendedorNombre" to _uiState.value.nombreVendedor,
                    "fecha" to Timestamp(now),
                    "ventaTotal" to ventaTotal,
                    "efectivoContado" to efectivoContado,
                    "diferencia" to diferencia,
                    "desgloseEfectivo" to desgloseEfectivo.mapKeys { it.key.toString() },
                    "almacen" to _uiState.value.rutaNombre,
                    "clientesAtendidos" to _uiState.value.clientesDia,
                    "tipo" to "LIQUIDACION_DIARIA"
                )
                
                db.collection("liquidaciones_diarias").add(liquidacionData).await()
                
                // 2. Finalizar Jornada Oficialmente
                toggleRuta(false)
                
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error en liquidación", e)
                _uiState.update { it.copy(isLoading = false, error = "Error al guardar liquidación: ${e.message}") }
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
                val mensaje = if (activar) {
                    "El colaborador $nombreVendedor ya se encuentra activo en su ruta de distribución."
                } else {
                    "El colaborador $nombreVendedor ha finalizado su jornada de trabajo exitosamente."
                }

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
            // 🔥 CLAVE: Usamos el userId directo del constructor para el filtro inicial
            val ahoraReal = com.gruposanangel.delivery.utilidades.TimeManager.getHoraReal()
            val cal = Calendar.getInstance()
            cal.timeInMillis = ahoraReal
            
            // Definimos el inicio del bloque (4 semanas atrás)
            cal.add(Calendar.WEEK_OF_YEAR, -4)
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            val inicioBloque = cal.timeInMillis

            // Escuchamos todas las ventas del vendedor desde hace 4 semanas hasta "mañana"
            ventaRepository.obtenerVentasPorPeriodoFlow(userId, inicioBloque, ahoraReal + 86400000)
                .collect { todasLasVentas ->
                    val calCalc = Calendar.getInstance()
                    
                    // Lógica de HOY exacta (Basada en Verdad Temporal)
                    calCalc.timeInMillis = ahoraReal
                    calCalc.set(Calendar.HOUR_OF_DAY, 0); calCalc.set(Calendar.MINUTE, 0); calCalc.set(Calendar.SECOND, 0); calCalc.set(Calendar.MILLISECOND, 0)
                    val iniHoy = calCalc.timeInMillis
                    
                    // Lógica de SEMANA exacta (Basada en Verdad Temporal)
                    val calSem = Calendar.getInstance()
                    calSem.timeInMillis = ahoraReal
                    calSem.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    calSem.set(Calendar.HOUR_OF_DAY, 0); calSem.set(Calendar.MINUTE, 0); calSem.set(Calendar.SECOND, 0)
                    if (calSem.timeInMillis > ahoraReal) {
                        calSem.add(Calendar.DAY_OF_MONTH, -7)
                    }
                    val iniSemana = calSem.timeInMillis

                    // Filtrado en memoria
                    // 🔥 Solo considerar ventas que NO estén canceladas para los cálculos financieros
                    val ventasHoy = todasLasVentas.filter { it.fecha >= iniHoy && it.estado != "CANCELADA" }
                    val ventasSemana = todasLasVentas.filter { it.fecha >= iniSemana && it.estado != "CANCELADA" }
                    val ventasBloqueTotal = todasLasVentas.filter { it.estado != "CANCELADA" }.sumOf { it.total }

                    // Cálculo por día de la semana (Lunes a Sábado)
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
                        ventaBloque = ventasBloqueTotal,
                        ventasPorDiaSemana = ventasPorDia,
                        ventasHoy = ventasHoy
                    ) }
                }
        }
    }

    private fun escucharVentasNube() {
        viewModelScope.launch {
            val user = usuarioRepository.obtenerUsuarioActual()
            val puesto = user?.puestoTrabajo?.trim() ?: ""
            val esVendedor = puesto == "Vendedor de Ruta" || puesto == "Suplente de Ruta"
            val idParaSync = if (esVendedor) userId else ""

            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            val inicio = cal.time

            salesListener?.remove()
            
            var query: com.google.firebase.firestore.Query = db.collection("ventas")
                .whereGreaterThanOrEqualTo("fecha", inicio)
            
            if (esVendedor) {
                query = query.whereEqualTo("vendedorId", userId)
            }

            salesListener = query.addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    viewModelScope.launch {
                        ventaRepository.descargarVentasDia(idParaSync)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        salesListener?.remove()
    }
}
