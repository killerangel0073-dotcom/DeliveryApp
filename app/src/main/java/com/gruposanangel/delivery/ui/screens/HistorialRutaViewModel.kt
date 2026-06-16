package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.data.RepositoryRuta
import com.gruposanangel.delivery.data.RutaEntity
import com.gruposanangel.delivery.data.VentaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class VentaMarker(
    val id: String,
    val clienteNombre: String,
    val total: Double,
    val latLng: LatLng,
    val fotoUrl: String?,
    val fueraDeRango: Boolean
)

data class PuntoHistorial(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val vel: Double = 0.0,
    val bat: Int = 0,
    val ts: Timestamp = Timestamp.now(),
    val acc: Float = 0f,
    val st: String = ""
)

data class Incidencia(
    val tipo: String, // "EXCESO_VELOCIDAD", "PARADA_LARGA"
    val latLng: LatLng,
    val descripcion: String,
    val timestamp: Long
)

data class HistorialUiState(
    val isLoading: Boolean = false,
    val rutas: List<RutaEntity> = emptyList(),
    val selectedRuta: String? = null,
    val selectedDate: Date = Date(),
    val puntos: List<PuntoHistorial> = emptyList(),
    val incidencias: List<Incidencia> = emptyList(),
    val ventas: List<VentaMarker> = emptyList(),
    val distanciaTotalKm: Double = 0.0,
    val velocidadMaxima: Double = 0.0,
    val tiempoTotalMinutos: Long = 0,
    val error: String? = null
)

class HistorialRutaViewModel(
    private val repositoryRuta: RepositoryRuta
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistorialUiState())
    val uiState: StateFlow<HistorialUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        cargarRutas()
    }

    private fun cargarRutas() {
        viewModelScope.launch {
            repositoryRuta.obtenerRutasLocal().collect { lista ->
                _uiState.update { it.copy(rutas = lista) }
                if (lista.isNotEmpty() && _uiState.value.selectedRuta == null) {
                    seleccionarRuta(lista.first().nombre)
                }
            }
        }
    }

    fun seleccionarRuta(nombre: String) {
        _uiState.update { it.copy(selectedRuta = nombre) }
        cargarHistorial()
    }

    fun seleccionarFecha(fecha: Date) {
        _uiState.update { it.copy(selectedDate = fecha) }
        cargarHistorial()
    }

    /**
     * 🚀 MODO DEMO PREMIUM: Genera un rastro fluido y realista (estilo Uber/Tesla)
     */
    fun cargarDemoData() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch(Dispatchers.Default) {
            val baseLat = 19.4768
            val baseLng = -96.5897
            val puntosDemo = mutableListOf<PuntoHistorial>()
            val ventasDemo = mutableListOf<VentaMarker>()
            val ahora = System.currentTimeMillis() / 1000

            // Generamos 50 puntos siguiendo un trayecto curvo y realista
            for (i in 0 until 50) {
                val t = i.toDouble() / 10.0
                // Trayecto en espiral suave para simular calles
                val lat = baseLat + (0.01 * t * kotlin.math.cos(t * 0.5))
                val lng = baseLng + (0.01 * t * kotlin.math.sin(t * 0.5))
                
                // Velocidad variable
                val vel = when {
                    i in 15..20 -> 98.0
                    i in 35..40 -> 5.0
                    else -> 45.0 + (kotlin.math.sin(t) * 15.0)
                }

                puntosDemo.add(
                    PuntoHistorial(
                        lat = lat,
                        lng = lng,
                        vel = vel,
                        bat = (100 - (i * 0.5)).toInt(),
                        ts = Timestamp(ahora + (i * 60), 0),
                        acc = 5f,
                        st = if (vel > 1.0) "MOVING" else "STOPPED"
                    )
                )

                // 📸 Simular Ventas en puntos estratégicos
                if (i == 10) {
                    ventasDemo.add(VentaMarker("v1", "Tienda La Esquina", 1250.0, LatLng(lat, lng), null, false))
                }
                if (i == 25) {
                    ventasDemo.add(VentaMarker("v2", "Abarrotes El Sol", 890.0, LatLng(lat, lng), null, true))
                }
                if (i == 42) {
                    ventasDemo.add(VentaMarker("v3", "Mini Super Mary", 2100.0, LatLng(lat, lng), null, false))
                }
            }
            
            // Simular una parada real de 12 min en el punto 30
            val listaFinal = puntosDemo.toMutableList()
            val puntoParada = listaFinal[30]
            for (j in 31 until listaFinal.size) {
                val p = listaFinal[j]
                listaFinal[j] = p.copy(ts = Timestamp(p.ts.seconds + 720, 0)) 
            }

            _uiState.update { it.copy(ventas = ventasDemo) }
            procesarDatos(listaFinal)
        }
    }

    private fun cargarHistorial() {
        val state = _uiState.value
        val ruta = state.selectedRuta ?: return
        val fechaStr = dateFormat.format(state.selectedDate)
        val docId = "${ruta}_$fechaStr"

        _uiState.update { it.copy(isLoading = true, puntos = emptyList(), incidencias = emptyList(), ventas = emptyList(), error = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Cargar Puntos de Recorrido
                val doc = db.collection("historial_rutas").document(docId).get().await()
                
                // 2. Cargar Ventas de este día/ruta
                val cal = Calendar.getInstance().apply { time = state.selectedDate; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }
                val start = Timestamp(cal.time)
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
                val end = Timestamp(cal.time)

                // Buscamos quién tiene esta ruta asignada
                val userQuery = db.collection("users").whereEqualTo("ultimoAlmacenNombre", ruta).get().await()
                val sellerIds = userQuery.documents.map { it.id }

                val ventasList = if (sellerIds.isNotEmpty()) {
                    db.collection("ventas")
                        .whereIn("vendedorId", sellerIds)
                        .whereGreaterThanOrEqualTo("fecha", start)
                        .whereLessThanOrEqualTo("fecha", end)
                        .get().await().documents.mapNotNull { d ->
                            val lat = d.getDouble("latitudVenta") ?: 0.0
                            val lng = d.getDouble("longitudVenta") ?: 0.0
                            if (lat != 0.0) {
                                VentaMarker(
                                    id = d.id,
                                    clienteNombre = d.getString("clienteNombre") ?: "Cliente",
                                    total = d.getDouble("total") ?: 0.0,
                                    latLng = LatLng(lat, lng),
                                    fotoUrl = d.getString("fotoEvidenciaVisita") ?: d.getString("clienteImagenUrl"),
                                    fueraDeRango = d.getBoolean("fueraDeRango") ?: false
                                )
                            } else null
                        }
                } else emptyList()

                _uiState.update { it.copy(ventas = ventasList) }

                if (doc.exists()) {
                    val rawList = doc.get("historialRecorrido") as? List<Map<String, Any>> ?: emptyList()
                    val puntos = rawList.map { map ->
                        PuntoHistorial(
                            lat = map["lat"] as? Double ?: 0.0,
                            lng = map["lng"] as? Double ?: 0.0,
                            vel = (map["vel"] as? Number)?.toDouble() ?: 0.0,
                            bat = (map["bat"] as? Long)?.toInt() ?: 0,
                            ts = map["ts"] as? Timestamp ?: Timestamp.now(),
                            acc = (map["acc"] as? Number)?.toFloat() ?: 0f,
                            st = map["st"] as? String ?: ""
                        )
                    }.sortedBy { it.ts.seconds }

                    procesarDatos(puntos)
                } else {
                    _uiState.update { it.copy(isLoading = false, puntos = emptyList(), error = if (ventasList.isEmpty()) "No hay registros para este día." else null) }
                }
            } catch (e: Exception) {
                Log.e("HistorialVM", "Error cargando historial", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun procesarDatos(puntos: List<PuntoHistorial>) {
        if (puntos.isEmpty()) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }

        // 1. Obtener límite de velocidad desde Firestore
        val limitSnap = db.collection("config").document("gps").get().await()
        val limitSpeed = limitSnap.getDouble("limite_velocidad") ?: 70.0

        val incidencias = mutableListOf<Incidencia>()
        var distTotal = 0.0
        var vMax = 0.0

        for (i in puntos.indices) {
            val p = puntos[i]
            if (p.vel > vMax) vMax = p.vel

            // Detección exceso velocidad
            if (p.vel > limitSpeed) {
                incidencias.add(Incidencia(
                    tipo = "EXCESO_VELOCIDAD",
                    latLng = LatLng(p.lat, p.lng),
                    descripcion = "Exceso: ${p.vel.toInt()} km/h",
                    timestamp = p.ts.seconds * 1000
                ))
            }

            // Cálculo distancia
            if (i > 0) {
                val prev = puntos[i - 1]
                val results = FloatArray(1)
                android.location.Location.distanceBetween(prev.lat, prev.lng, p.lat, p.lng, results)
                distTotal += results[0] / 1000.0
            }

            // Detección paradas largas (> 10 min)
            // Lógica simplificada: si entre dos puntos consecutivos hay > 10 min y poca distancia
            if (i > 0) {
                val prev = puntos[i-1]
                val diffMin = (p.ts.seconds - prev.ts.seconds) / 60
                if (diffMin > 10) {
                    incidencias.add(Incidencia(
                        tipo = "PARADA_LARGA",
                        latLng = LatLng(p.lat, p.lng),
                        descripcion = "Parada de $diffMin min",
                        timestamp = prev.ts.seconds * 1000
                    ))
                }
            }
        }

        val tTotal = if (puntos.size > 1) (puntos.last().ts.seconds - puntos.first().ts.seconds) / 60 else 0

        _uiState.update { it.copy(
            isLoading = false,
            puntos = puntos,
            incidencias = incidencias,
            distanciaTotalKm = distTotal,
            velocidadMaxima = vMax,
            tiempoTotalMinutos = tTotal
        ) }
    }
}

class HistorialRutaViewModelFactory(private val repositoryRuta: RepositoryRuta) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HistorialRutaViewModel(repositoryRuta) as T
    }
}
