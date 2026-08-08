package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.graphics.Color
import com.gruposanangel.delivery.data.RepositoryRuta
import com.gruposanangel.delivery.data.RutaEntity
import com.gruposanangel.delivery.data.VentaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    val tipo: String,
    val latLng: LatLng,
    val descripcion: String,
    val timestamp: Long
)

data class PolylineSegment(
    val points: List<LatLng>,
    val color: Long
)

data class HistorialUiState(
    val isLoading: Boolean = false,
    val rutas: List<RutaEntity> = emptyList(),
    val selectedRuta: String? = null,
    val selectedDate: Date = Date(),
    val puntos: List<PuntoHistorial> = emptyList(),
    val segments: List<PolylineSegment> = emptyList(),
    val incidencias: List<Incidencia> = emptyList(),
    val ventas: List<VentaMarker> = emptyList(),
    val selectedVenta: VentaMarker? = null,
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
    
    private fun getDateFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init {
        sincronizarYLeerRutas()
    }

    private fun sincronizarYLeerRutas() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repositoryRuta.descargarRutasDesdeFirebase()
            
            repositoryRuta.obtenerRutasLocal().take(1).collect { lista ->
                _uiState.update { it.copy(rutas = lista, isLoading = false) }
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

    fun seleccionarVenta(venta: VentaMarker?) {
        _uiState.update { it.copy(selectedVenta = venta) }
    }

    fun cargarDemoData() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.Default) {
            val baseLat = 19.4768
            val baseLng = -96.5897
            val puntosDemo = mutableListOf<PuntoHistorial>()
            val ahora = System.currentTimeMillis() / 1000
            for (i in 0 until 50) {
                puntosDemo.add(PuntoHistorial(baseLat + (0.001 * i), baseLng + (0.001 * i), 40.0, 100, Timestamp(ahora + (i * 60), 0), 5f, "MOVING"))
            }
            procesarDatos(puntosDemo)
        }
    }

    private fun cargarHistorial() {
        val state = _uiState.value
        val rutaNombre = state.selectedRuta ?: return
        val fechaStr = getDateFormat().format(state.selectedDate)
        val docId = "${rutaNombre}_$fechaStr"

        Log.d("HistorialRuta", "🚀 Iniciando carga para: $docId")
        _uiState.update { it.copy(isLoading = true, puntos = emptyList(), incidencias = emptyList(), ventas = emptyList(), error = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. CARGA DE RASTRO GPS (Polilíneas)
                val docTask = db.collection("historial_rutas").document(docId).get().await()
                if (docTask.exists()) {
                    val rawList = docTask.get("historialRecorrido") as? List<Map<String, Any>> ?: emptyList()
                    val puntos = rawList.map { map ->
                        PuntoHistorial(
                            lat = (map["lat"] as? Number)?.toDouble() ?: 0.0,
                            lng = (map["lng"] as? Number)?.toDouble() ?: 0.0,
                            vel = (map["vel"] as? Number)?.toDouble() ?: 0.0,
                            bat = (map["bat"] as? Number)?.toInt() ?: 0,
                            ts = map["ts"] as? Timestamp ?: Timestamp.now(),
                            acc = (map["acc"] as? Number)?.toFloat() ?: 0f,
                            st = map["st"] as? String ?: ""
                        )
                    }.filter { it.lat != 0.0 }.sortedBy { it.ts.seconds }
                    procesarDatos(puntos)
                }

                // 2. PREPARACIÓN DE TIEMPOS
                val cal = Calendar.getInstance().apply { 
                    time = state.selectedDate
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val start = Timestamp(cal.time)
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
                val end = Timestamp(cal.time)

                // 3. OBTENER IDS DE RUTA PARA CONSULTAR VENTAS
                val rutaSnap = db.collection("rutas").whereEqualTo("nombre", rutaNombre).get().await()
                val rDoc = rutaSnap.documents.firstOrNull()
                val aRef = rDoc?.get("almacenAsignado") as? com.google.firebase.firestore.DocumentReference
                val aId = aRef?.id ?: rDoc?.getString("almacenId")
                val vRef = rDoc?.get("vendedorAsignado") as? com.google.firebase.firestore.DocumentReference
                val vId = vRef?.id

                val allSalesDocs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
                
                // Consulta por Almacén (Índice Habilitado)
                if (aId != null) {
                    val q = db.collection("ventas").whereEqualTo("almacenId", aId)
                        .whereGreaterThanOrEqualTo("fecha", start).whereLessThanOrEqualTo("fecha", end).get().await()
                    allSalesDocs.addAll(q.documents)
                }
                
                // Consulta por Vendedor (Respaldo Lizeth)
                if (vId != null) {
                    val q = db.collection("ventas").whereEqualTo("vendedorId", vId)
                        .whereGreaterThanOrEqualTo("fecha", start).whereLessThanOrEqualTo("fecha", end).get().await()
                    allSalesDocs.addAll(q.documents)
                }

                // 4. PROCESAMIENTO DE MARCADORES CON RESCATE DE UBICACIÓN
                val clientesLocales = try { repositoryRuta.obtenerTodosLosClientesLocal() } catch (e: Exception) { emptyList() }
                val mapaClientesLocal = clientesLocales.associateBy { it.id }

                val finalVentas = allSalesDocs.distinctBy { it.id }.map { d ->
                    async {
                        val cIdRaw = d.get("clienteId") ?: d.get("clienteRef")
                        val cId = if (cIdRaw is com.google.firebase.firestore.DocumentReference) cIdRaw.id else cIdRaw?.toString() ?: ""
                        
                        var latV = (d.get("latitudVenta") as? Number)?.toDouble() ?: 0.0
                        var lngV = (d.get("longitudVenta") as? Number)?.toDouble() ?: 0.0
                        var nombreV = d.getString("clienteNombre") ?: "Cliente"
                        var fotoV = d.getString("clienteImagenUrl") ?: d.getString("fotoEvidenciaVisita")

                        // Rescate de ubicación/foto si faltan
                        val local = mapaClientesLocal[cId]
                        if (latV == 0.0 || fotoV.isNullOrBlank()) {
                            if (local != null) {
                                if (latV == 0.0) { latV = local.ubicacionLat; lngV = local.ubicacionLon }
                                if (fotoV.isNullOrBlank()) fotoV = local.fotografiaUrl
                                if (nombreV == "Cliente") nombreV = local.nombreNegocio
                            } else {
                                try {
                                    val cDoc = db.collection("clientes").document(cId).get().await()
                                    if (cDoc.exists()) {
                                        val geo = cDoc.getGeoPoint("ubicacion")
                                        if (latV == 0.0 && geo != null) { latV = geo.latitude; lngV = geo.longitude }
                                        if (fotoV.isNullOrBlank()) fotoV = cDoc.getString("FotografiaCliente")
                                        if (nombreV == "Cliente") nombreV = cDoc.getString("nombreNegocio") ?: "Cliente"
                                    }
                                } catch (e: Exception) { }
                            }
                        }

                        if (latV != 0.0) {
                            VentaMarker(
                                id = d.id, clienteNombre = nombreV,
                                total = (d.get("total") as? Number)?.toDouble() ?: 0.0,
                                latLng = LatLng(latV, lngV), fotoUrl = fotoV?.takeIf { it.isNotEmpty() },
                                fueraDeRango = d.getBoolean("fueraDeRango") ?: false
                            )
                        } else null
                    }
                }.awaitAll().filterNotNull()

                _uiState.update { it.copy(ventas = finalVentas, isLoading = false) }

            } catch (e: Exception) {
                Log.e("HistorialVM", "Fallo total", e)
                _uiState.update { it.copy(isLoading = false, error = "Sin datos para esta fecha.") }
            }
        }
    }

    private suspend fun procesarDatos(puntos: List<PuntoHistorial>) {
        if (puntos.isEmpty()) return
        
        val limitSnap = try { db.collection("config").document("gps").get().await() } catch (e: Exception) { null }
        val limitSpeed = limitSnap?.getDouble("limite_velocidad") ?: 70.0

        val incidencias = mutableListOf<Incidencia>()
        val segments = mutableListOf<PolylineSegment>()
        var currentPoints = mutableListOf<LatLng>()
        var lastColorValue: Long? = null
        
        var distTotal = 0.0
        var vMax = 0.0

        for (i in puntos.indices) {
            val p = puntos[i]
            if (p.vel > vMax) vMax = p.vel

            if (p.vel > limitSpeed) {
                incidencias.add(Incidencia("EXCESO_VELOCIDAD", LatLng(p.lat, p.lng), "Exceso: ${p.vel.toInt()} km/h", p.ts.seconds * 1000))
            }

            val color = when {
                p.vel > 80.0 -> 0xFFFF1744.toInt().toLong()
                p.vel > 50.0 -> 0xFFFFD600.toInt().toLong()
                p.vel < 5.0 -> 0xFF9E9E9E.toInt().toLong()
                else -> 0xFF00E676.toInt().toLong()
            }

            if (lastColorValue == null) {
                lastColorValue = color
                currentPoints.add(LatLng(p.lat, p.lng))
            }

            if (color == lastColorValue) {
                currentPoints.add(LatLng(p.lat, p.lng))
            } else {
                if (currentPoints.size >= 2) segments.add(PolylineSegment(currentPoints.toList(), lastColorValue!!))
                currentPoints = mutableListOf(currentPoints.last(), LatLng(p.lat, p.lng))
                lastColorValue = color
            }

            if (i > 0) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(puntos[i-1].lat, puntos[i-1].lng, p.lat, p.lng, results)
                distTotal += results[0] / 1000.0
                
                val diffMin = (p.ts.seconds - puntos[i-1].ts.seconds) / 60
                if (diffMin > 10) {
                    val desc = if (diffMin / 60 > 0) "Parada de ${diffMin/60}h ${diffMin%60}m" else "Parada de $diffMin min"
                    incidencias.add(Incidencia("PARADA_LARGA", LatLng(p.lat, p.lng), desc, puntos[i-1].ts.seconds * 1000))
                }
            }
        }
        if (currentPoints.size >= 2 && lastColorValue != null) segments.add(PolylineSegment(currentPoints.toList(), lastColorValue!!))

        val tTotal = if (puntos.size > 1) (puntos.last().ts.seconds - puntos.first().ts.seconds) / 60 else 0

        _uiState.update { it.copy(
            puntos = puntos, segments = segments, incidencias = incidencias,
            distanciaTotalKm = distTotal, velocidadMaxima = vMax, tiempoTotalMinutos = tTotal
        ) }
    }
}

class HistorialRutaViewModelFactory(private val repositoryRuta: RepositoryRuta) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HistorialRutaViewModel(repositoryRuta) as T
    }
}
