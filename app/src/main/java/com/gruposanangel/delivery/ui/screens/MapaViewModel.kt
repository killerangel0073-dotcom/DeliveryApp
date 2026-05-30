package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.MapType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class CameraEvent {
    object CenterOnClients : CameraEvent()
}

data class MapaUiState(
    val isLoading: Boolean = false,
    val vendedores: List<VendedorUbicacion> = emptyList(),
    val clientes: List<Cliente> = emptyList(),
    val markersVisible: Boolean = false,
    val mapType: MapType = MapType.NORMAL,
    val mapStyleJson: String? = null,
    val selectedCliente: Cliente? = null,
    val seguirVendedor: String? = null,
    val vendedorSeleccionadoRuta: String? = null,
    val error: String? = null,
    val puestoTrabajo: String? = null,
    val miRuta: String? = null
)

class MapaViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(MapaUiState())
    val uiState: StateFlow<MapaUiState> = _uiState.asStateFlow()

    // 🌊 Canal de eventos de cámara (para animaciones automáticas)
    private val _cameraEvents = MutableSharedFlow<CameraEvent>()
    val cameraEvents = _cameraEvents.asSharedFlow()

    // 📍 Persistencia de la cámara entre pantallas
    val cameraPositionState = CameraPositionState(
        position = CameraPosition.fromLatLngZoom(LatLng(19.4768, -96.5897), 12f)
    )

    private val db = FirebaseFirestore.getInstance()
    private var locationsListener: ListenerRegistration? = null

    init {
        escucharUbicacionesVendedores()
        cargarDatosUsuario()
    }

    private fun cargarDatosUsuario() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(uid).get().await()
                val puesto = doc.getString("puestoTrabajo")
                val rutaRef = doc.getDocumentReference("rutaAsignada")
                var rutaNombre: String? = null
                if (rutaRef != null) {
                    rutaNombre = rutaRef.id // Ej: "Ruta 2 Delisa"
                }
                
                _uiState.update { it.copy(puestoTrabajo = puesto, miRuta = rutaNombre) }
            } catch (e: Exception) {
                Log.e("MapaVM", "Error cargando datos de usuario", e)
            }
        }
    }

    private fun escucharUbicacionesVendedores() {
        locationsListener?.remove()

        // 🚀 Forzamos a que el proceso de escucha y filtrado de Firebase corra en un hilo de fondo
        locationsListener = db.collection("locations")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("MapaVM", "Error en listener de ubicaciones", error)
                    return@addSnapshotListener
                }

                // Lanzamos una corrutina en el hilo de Entrada/Salida (IO) para procesar los datos pesados
                viewModelScope.launch(Dispatchers.IO) {
                    val lista = snapshot?.documents?.mapNotNull { doc ->
                        val lat = doc.getDouble("latitude")
                        val lng = doc.getDouble("longitude")
                        val acc = doc.getDouble("accuracy")?.toFloat() ?: 0f
                        val ts = doc.getTimestamp("timestamp")

                        if (lat != null && lng != null && ts != null) {
                            VendedorUbicacion(
                                ruta = doc.id,
                                lat = lat,
                                lng = lng,
                                accuracy = acc,
                                speed = doc.getDouble("speed") ?: 0.0,
                                battery = doc.getLong("battery")?.toInt() ?: 0,
                                status = doc.getString("status") ?: "OFFLINE",
                                timestamp = ts
                            )
                        } else null
                    } ?: emptyList()

                    // Regresamos al hilo principal únicamente a actualizar el estado visual de Compose
                    _uiState.update { it.copy(vendedores = lista) }
                }
            }
    }

    fun toggleMarkersVisible() {
        val currentVisible = _uiState.value.markersVisible
        val nextVisible = !currentVisible
        
        if (nextVisible) {
            if (_uiState.value.clientes.isEmpty()) {
                cargarClientes(triggerCenter = true)
            } else {
                viewModelScope.launch { _cameraEvents.emit(CameraEvent.CenterOnClients) }
            }
        }
        
        _uiState.update { it.copy(markersVisible = nextVisible) }
    }

    private fun cargarClientes(triggerCenter: Boolean = false) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = db.collection("clientes")
                    .whereEqualTo("activo", true)
                    .get()
                    .await()

                val lista = result.documents.mapNotNull { doc ->
                    val geo = doc.getGeoPoint("ubicacion")
                    val valor = doc.getString("medio") ?: "medio"
                    geo?.let {
                        Cliente(
                            id = doc.id,
                            nombreNegocio = doc.getString("nombreNegocio") ?: "Sin nombre",
                            ubicacionLat = it.latitude,
                            ubicacionLng = it.longitude,
                            valor = valor,
                            nombreDueno = doc.getString("nombreDueno"),
                            telefono = doc.getString("telefono"),
                            fotoUrl = doc.getString("FotografiaCliente")
                        )
                    }
                }

                _uiState.update { it.copy(clientes = lista, isLoading = false) }
                
                if (triggerCenter && lista.isNotEmpty()) {
                    _cameraEvents.emit(CameraEvent.CenterOnClients)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun setMapType(type: MapType, styleJson: String? = null) {
        _uiState.update { it.copy(mapType = type, mapStyleJson = styleJson) }
    }

    fun selectCliente(cliente: Cliente?) {
        _uiState.update { it.copy(selectedCliente = cliente, vendedorSeleccionadoRuta = null) }
    }

    fun selectVendedor(ruta: String?) {
        _uiState.update { it.copy(vendedorSeleccionadoRuta = ruta, seguirVendedor = ruta, selectedCliente = null) }
    }

    fun toggleSeguirVendedor(ruta: String) {
        val actual = _uiState.value.seguirVendedor
        if (actual == ruta) {
            _uiState.update { it.copy(seguirVendedor = null, vendedorSeleccionadoRuta = null) }
        } else {
            _uiState.update { it.copy(seguirVendedor = ruta, vendedorSeleccionadoRuta = ruta, selectedCliente = null) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationsListener?.remove()
    }
}