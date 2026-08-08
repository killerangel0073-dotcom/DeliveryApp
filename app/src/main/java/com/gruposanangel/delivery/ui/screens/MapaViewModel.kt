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

    private val _cameraEvents = MutableSharedFlow<CameraEvent>()
    val cameraEvents = _cameraEvents.asSharedFlow()

    val cameraPositionState = CameraPositionState(
        position = CameraPosition.fromLatLngZoom(LatLng(19.4768, -96.5897), 12f)
    )

    private val RTDB_URL = "https://appventas--san-angel-default-rtdb.firebaseio.com/"
    private val db = FirebaseFirestore.getInstance()
    private val rtdb = FirebaseDatabase.getInstance(RTDB_URL).reference
    private var liveTrackingListener: ValueEventListener? = null
    
    // 🔥 CACHÉ ESTATICO: Para no leer Firestore cada 2 segundos (Ahorro de batería y datos)
    private var cacheUsuariosActivos: Map<String, com.google.firebase.firestore.DocumentSnapshot> = emptyMap()

    init {
        if (repositoryCliente != null) {
            cargarDatosUsuario()
            preCargarUsuarios() // 🔥 Carga inicial para velocidad
        }
    }

    private fun preCargarUsuarios() {
        db.collection("users")
            .whereEqualTo("activo", true)
            .get()
            .addOnSuccessListener { snapshot ->
                cacheUsuariosActivos = snapshot.documents.associateBy { it.id }
            }
    }

    fun sobreescribirAdmin(esAdmin: Boolean) {
        val puestoFake = if (esAdmin) "Supervisor" else "Vendedor"
        _uiState.update { it.copy(puestoTrabajo = puestoFake) }
        cargarClientes(triggerCenter = false)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        escucharUbicacionesVendedores(uid, puestoFake, _uiState.value.miRuta)
    }

    private fun cargarDatosUsuario() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(uid).get().await()
                val puesto = doc.getString("puestoTrabajo") ?: ""
                
                val rRef = doc.getDocumentReference("rutaAsignada")
                val rNom = doc.getString("ultimaRutaNombre")
                val rutaNombre = rRef?.id ?: rNom

                _uiState.update { it.copy(puestoTrabajo = puesto, miRuta = rutaNombre) }
                
                if (puesto.trim() in listOf("CEO", "Gerente General", "Supervisor", "Administración", "Gerente")) {
                    cargarListaRutas()
                }

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
            } catch (e: Exception) { }
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

        val p = puesto.trim().uppercase()
        val esAdmin = p == "CEO" || p.contains("GERENTE") || p.contains("SUPERVISOR") || p.contains("ADMIN") || p.contains("DIRECCION")
        val query = if (esAdmin) rtdb.child("vendedores_en_vivo") else rtdb.child("vendedores_en_vivo").child(uid)

        liveTrackingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        // 🚀 OPTIMIZACIÓN: Usar caché si está disponible, si no, cargar una vez.
                        if (cacheUsuariosActivos.isEmpty()) {
                            val usersSnap = db.collection("users").get().await()
                            cacheUsuariosActivos = usersSnap.documents.filter { it.getBoolean("activo") == true }.associateBy { it.id }
                        }
                        
                        val todosLosUsuarios = cacheUsuariosActivos

                        val liveMap = if (esAdmin) {
                            snapshot.children.associateBy { it.key }
                        } else {
                            mapOf(uid to snapshot)
                        }

                        val rutasAgrupadas = mutableMapOf<String, VendedorUbicacion>()
                        val uidsAMostrar = (liveMap.keys + todosLosUsuarios.keys).distinct()

                        uidsAMostrar.forEach { vUid ->
                            val userDoc = todosLosUsuarios[vUid]
                            val liveDoc = liveMap[vUid]
                            val rRefRaw = userDoc?.get("rutaAsignada")
                            val rRefId = when (rRefRaw) {
                                is com.google.firebase.firestore.DocumentReference -> rRefRaw.id
                                is String -> rRefRaw.split("/").last()
                                else -> null
                            }
                            
                            if (rRefId != null) {
                                val infoVendedor = mapearVendedor(rRefId, liveDoc)
                                val existente = rutasAgrupadas[rRefId]
                                if (existente == null || (infoVendedor.status == "ONLINE" && existente.status != "ONLINE")) {
                                    rutasAgrupadas[rRefId] = infoVendedor
                                }
                            }
                        }
                        val listaFinal = rutasAgrupadas.values.toList().sortedBy { it.ruta }
                        _uiState.update { it.copy(vendedores = listaFinal) }
                    } catch (e: Exception) { }
                }
            }
            override fun onCancelled(error: DatabaseError) { }
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
            
            VendedorUbicacion(nombreRuta, lat, lng, acc, speed, battery, status, com.google.firebase.Timestamp(java.util.Date(tsLong)))
        } else {
            VendedorUbicacion(nombreRuta, 0.0, 0.0, 0f, 0.0, 0, "OFFLINE", com.google.firebase.Timestamp.now())
        }
    }

    fun toggleMarkersVisible() {
        val currentVisible = _uiState.value.markersVisible
        val nextVisible = !currentVisible
        if (nextVisible) {
            if (_uiState.value.clientes.isEmpty()) cargarClientes(triggerCenter = true)
            else viewModelScope.launch { _cameraEvents.emit(CameraEvent.CenterOnClients) }
        }
        _uiState.update { it.copy(markersVisible = nextVisible) }
    }

    private fun cargarClientes(triggerCenter: Boolean = false) {
        _uiState.update { it.copy(isLoading = true) }
        val p = _uiState.value.puestoTrabajo?.trim() ?: ""
        val esAdmin = p in listOf("CEO", "Gerente General", "Supervisor", "Administración", "Gerente")
        val miRuta = _uiState.value.miRuta
        val filtroRuta = _uiState.value.filtroRuta

        viewModelScope.launch(Dispatchers.IO) {
            try {
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
                        Cliente(ent.id, ent.nombreNegocio, ent.ubicacionLat, ent.ubicacionLon, ent.valorCliente, ent.nombreDueno, ent.telefono, ent.fotografiaUrl)
                    }
                    _uiState.update { it.copy(clientes = lista, isLoading = false) }
                    if (triggerCenter && lista.isNotEmpty()) _cameraEvents.emit(CameraEvent.CenterOnClients)
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
        if (actual == ruta) _uiState.update { it.copy(seguirVendedor = null, vendedorSeleccionadoRuta = null) }
        else _uiState.update { it.copy(seguirVendedor = ruta, vendedorSeleccionadoRuta = ruta, selectedCliente = null) }
    }

    override fun onCleared() {
        super.onCleared()
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
