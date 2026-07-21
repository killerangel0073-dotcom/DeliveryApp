package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.MapType
import com.gruposanangel.delivery.data.RepositoryCliente
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
    val miRuta: String? = null,
    val filtroRuta: String = "Todas las Rutas",
    val listaRutas: List<String> = listOf("Todas las Rutas")
)

class MapaViewModel(
    private var repositoryCliente: RepositoryCliente? = null
) : ViewModel() {

    fun setRepository(repo: RepositoryCliente) {
        if (this.repositoryCliente == null) {
            this.repositoryCliente = repo
            cargarDatosUsuario()
            if (_uiState.value.markersVisible) {
                cargarClientes()
            }
        }
    }

    private val _uiState = MutableStateFlow(MapaUiState())
    val uiState: StateFlow<MapaUiState> = _uiState.asStateFlow()

    // 🌊 Canal de eventos de cámara (para animaciones automáticas)
    private val _cameraEvents = MutableSharedFlow<CameraEvent>()
    val cameraEvents = _cameraEvents.asSharedFlow()

    // 📍 Persistencia de la cámara entre pantallas
    val cameraPositionState = CameraPositionState(
        position = CameraPosition.fromLatLngZoom(LatLng(19.4768, -96.5897), 12f)
    )

    private val RTDB_URL = "https://appventas--san-angel-default-rtdb.firebaseio.com/"
    private val db = FirebaseFirestore.getInstance()
    private val rtdb = FirebaseDatabase.getInstance(RTDB_URL).reference // 🚀 RTDB con URL explícita
    private var locationsListener: ListenerRegistration? = null
    private var liveTrackingListener: ValueEventListener? = null

    init {
        if (repositoryCliente != null) {
            cargarDatosUsuario()
        }
    }

    private fun cargarDatosUsuario() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(uid).get().await()
                val puesto = doc.getString("puestoTrabajo") ?: ""
                val esAdmin = puesto.trim() in listOf("CEO", "Gerente General", "Supervisor", "Administración")

                val rutaRef = doc.getDocumentReference("rutaAsignada")
                var rutaNombre: String? = null
                if (rutaRef != null) {
                    rutaNombre = rutaRef.id // Ej: "Ruta 2 Delisa"
                }
                
                _uiState.update { it.copy(puestoTrabajo = puesto, miRuta = rutaNombre) }
                
                if (esAdmin) {
                    cargarListaRutas()
                }

                // 🚀 Una vez que sabemos quién es, activamos la escucha correcta
                escucharUbicacionesVendedores(uid, puesto, rutaNombre)
            } catch (e: Exception) {
                Log.e("MapaVM", "Error cargando datos de usuario", e)
            }
        }
    }

    private fun cargarListaRutas() {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("rutas").get().await()
                val rutas = snapshot.documents.mapNotNull { it.getString("nombre") }.sorted()
                _uiState.update { it.copy(listaRutas = listOf("Todas las Rutas") + rutas) }
            } catch (e: Exception) {
                Log.e("MapaVM", "Error cargando rutas", e)
            }
        }
    }

    fun actualizarFiltroRuta(ruta: String) {
        _uiState.update { it.copy(filtroRuta = ruta, clientes = emptyList()) }
        if (_uiState.value.markersVisible) {
            cargarClientes(triggerCenter = true)
        }
    }

    private fun escucharUbicacionesVendedores(uid: String, puesto: String, miRuta: String?) {
        liveTrackingListener?.let { rtdb.removeEventListener(it) }

        val p = puesto.trim()
        val esAdmin = p == "CEO" || p == "Gerente General" || p == "Supervisor" || p == "Administración"
        val query = if (esAdmin) rtdb.child("vendedores_en_vivo") else rtdb.child("vendedores_en_vivo").child(uid)

        Log.d("MapaVM", "📡 RTDB: Iniciando escucha para $puesto (Admin: $esAdmin)")

        liveTrackingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch(Dispatchers.IO) {
                    if (esAdmin) {
                        // --- LÓGICA ADMIN: CRUZAR DATOS ---
                        val vendedoresMaestros = try {
                            db.collection("users")
                                .whereIn("puestoTrabajo", listOf("Vendedor de Ruta", "Suplente de Ruta"))
                                .whereEqualTo("activo", true)
                                .get().await().documents.mapNotNull { doc ->
                                    val rRef = doc.getDocumentReference("rutaAsignada")
                                    // 🛡️ FILTRO: Solo considerar vendedores que tengan una ruta asignada
                                    if (rRef != null) {
                                        Pair(doc.id, rRef.id)
                                    } else {
                                        null
                                    }
                                }
                        } catch (e: Exception) { emptyList() }

                        val liveMap = snapshot.children.associateBy { it.key }
                        val listaFinal = vendedoresMaestros.map { (vUid, nRuta) ->
                            val liveDoc = liveMap[vUid]
                            mapearVendedor(nRuta, liveDoc)
                        }
                        _uiState.update { it.copy(vendedores = listaFinal.sortedBy { it.ruta }) }
                    } else {
                        // --- LÓGICA VENDEDOR: SOLO ÉL MISMO ---
                        val miInfoLive = mapearVendedor(miRuta ?: "Mi Ruta", snapshot)
                        _uiState.update { it.copy(vendedores = listOf(miInfoLive)) }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("MapaVM", "Error RTDB Live Tracking", error.toException())
            }
        }

        query.addValueEventListener(liveTrackingListener!!)
    }

    private fun mapearVendedor(nombreRuta: String, doc: DataSnapshot?): VendedorUbicacion {
        return if (doc != null && doc.exists()) {
            val lat = doc.child("latitude").getValue(Double::class.java) ?: 0.0
            val lng = doc.child("longitude").getValue(Double::class.java) ?: 0.0
            val acc = doc.child("accuracy").getValue(Double::class.java)?.toFloat() ?: 10f
            val speed = doc.child("speed").getValue(Double::class.java) ?: 0.0
            val battery = doc.child("battery").getValue(Int::class.java) ?: 0
            val status = doc.child("status").getValue(String::class.java) ?: "ONLINE"
            val tsLong = doc.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
            
            VendedorUbicacion(
                ruta = nombreRuta,
                lat = lat,
                lng = lng,
                accuracy = acc,
                speed = speed,
                battery = battery,
                status = status,
                timestamp = com.google.firebase.Timestamp(java.util.Date(tsLong))
            )
        } else {
            VendedorUbicacion(
                ruta = nombreRuta,
                lat = 0.0,
                lng = 0.0,
                accuracy = 0f,
                speed = 0.0,
                battery = 0,
                status = "OFFLINE",
                timestamp = com.google.firebase.Timestamp.now()
            )
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

        val p = _uiState.value.puestoTrabajo?.trim() ?: ""
        val esAdmin = p in listOf("CEO", "Gerente General", "Supervisor", "Administración")
        val miRuta = _uiState.value.miRuta
        val filtroRuta = _uiState.value.filtroRuta

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 🔥 CAMBIO A OFFLINE-FIRST CON FLOW LOCAL
                repositoryCliente?.obtenerClientesLocal()?.collect { entidades ->
                    val filtrados = entidades.filter { ent ->
                        val cumpleActivo = ent.activo
                        val cumpleRuta = if (esAdmin) {
                            filtroRuta == "Todas las Rutas" || ent.rutaId == filtroRuta
                        } else {
                            miRuta.isNullOrEmpty() || ent.rutaId == miRuta
                        }
                        cumpleActivo && cumpleRuta
                    }

                    val lista = filtrados.map { ent ->
                        Cliente(
                            id = ent.id,
                            nombreNegocio = ent.nombreNegocio,
                            ubicacionLat = ent.ubicacionLat,
                            ubicacionLng = ent.ubicacionLon,
                            valor = ent.valorCliente,
                            nombreDueno = ent.nombreDueno,
                            telefono = ent.telefono,
                            fotoUrl = ent.fotografiaUrl
                        )
                    }

                    _uiState.update { it.copy(clientes = lista, isLoading = false) }
                    
                    if (triggerCenter && lista.isNotEmpty()) {
                        _cameraEvents.emit(CameraEvent.CenterOnClients)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun startRealtimeSync(context: android.content.Context) {
        repositoryCliente?.escucharCambiosFirebase(context)
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
        liveTrackingListener?.let { rtdb.child("vendedores_en_vivo").removeEventListener(it) }
        repositoryCliente?.stopEscuchaFirebase()
    }
}

class MapaViewModelFactory(private val repository: RepositoryCliente) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm = MapaViewModel()
        vm.setRepository(repository)
        return vm as T
    }
}
