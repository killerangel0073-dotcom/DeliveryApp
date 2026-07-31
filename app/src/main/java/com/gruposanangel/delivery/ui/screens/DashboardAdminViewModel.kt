package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.data.UsuarioEntity
import com.gruposanangel.delivery.VentaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

data class SellerSummary(
    val uid: String,
    val nombre: String,
    val rutaNombre: String,
    val photoUrl: String,
    val totalVendido: Double,
    val clientesConVenta: Int,
    val ticketPromedio: Double,
    val ventas: List<VentaEntity> = emptyList(),
    val totalTicketsActivos: Int = 0,
    val estaEnRuta: Boolean = false,
    val perfilesVenta: List<com.gruposanangel.delivery.data.PerfilVenta> = emptyList(),
    val breakdown: List<PerfilBreakdown> = emptyList()
)

data class DashboardUiState(
    val isLoading: Boolean = true,
    val totalVentasDia: Double = 0.0,
    val totalTicketsDia: Int = 0,
    val totalClientesDia: Int = 0,
    val ticketPromedioGlobal: Double = 0.0,
    val resumenVendedores: List<SellerSummary> = emptyList(),
    val todasLasVentasHoy: List<VentaEntity> = emptyList(),
    val nombreAdmin: String = "",
    val photoUrlAdmin: String = "",
    val puestoAdmin: String = "",
    val error: String? = null,
    val rankingAlto: Double = 0.0,
    val rankingMedio: Double = 0.0,
    val rankingBajo: Double = 0.0
)

class DashboardAdminViewModel(
    private val ventaRepository: VentaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private var ventasListener: ListenerRegistration? = null
    private var jornadasListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null
    private var rankingListener: ListenerRegistration? = null

    private val _ventasFlow = MutableStateFlow<List<VentaEntity>>(emptyList())
    private val _usersFlow = MutableStateFlow<Map<String, UsuarioEntity>>(emptyMap())
    private val _allVendedoresFlow = MutableStateFlow<Set<String>>(emptySet())
    private val _jornadasFlow = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _detallesFlow = MutableStateFlow<Map<String, List<com.gruposanangel.delivery.data.VentaDetalleEntity>>>(emptyMap())
    private val _catalogFlow = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap()) // 🔥 Catálogo: ID -> Pair(Marca, Categoria)

    init {
        cargarCatalogoMaestro()
        escucharUsuarios()
        escucharJornadas()
        escucharRankingConfig()
        cargarDatosAdmin()
        
        // 🔥 MOTOR DE REACTIVIDAD: Combinar los flujos en tiempo real
        combine(_ventasFlow, _usersFlow, _allVendedoresFlow, _jornadasFlow, _detallesFlow, _catalogFlow) { args ->
            val ventas = args[0] as List<VentaEntity>
            val users = args[1] as Map<String, UsuarioEntity>
            val vendedores = args[2] as Set<String>
            val jornadas = args[3] as Map<String, Boolean>
            val detallesMap = args[4] as Map<String, List<com.gruposanangel.delivery.data.VentaDetalleEntity>>
            val catalog = args[5] as Map<String, Pair<String, String>>

            procesarDatos(ventas, users, vendedores, jornadas, detallesMap, catalog)
        }.onEach { nuevoEstado ->
            _uiState.update { nuevoEstado }
        }.launchIn(viewModelScope)

        cargarDatosDashboard(Date())
    }

    private fun cargarCatalogoMaestro() {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("producto").get().await()
                val map = snapshot.documents.associate { 
                    it.id to ( (it.getString("marca") ?: "Delisa") to (it.getString("categoria") ?: "General") )
                }
                _catalogFlow.value = map
            } catch (e: Exception) { }
        }
    }

    private fun escucharUsuarios() {
        usersListener?.remove()
        usersListener = db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            
            val usersMap = snapshot.documents.associate { doc ->
                val rutaRef = doc.get("rutaAsignada") as? DocumentReference
                val rId = rutaRef?.id
                
                doc.id to UsuarioEntity(
                    uid = doc.id,
                    nombre = doc.getString("nombre") ?: "Sin Nombre",
                    photoUrl = doc.getString("photo_url") ?: "",
                    email = doc.getString("email"),
                    puestoTrabajo = doc.getString("puestoTrabajo"),
                    ultimaRutaId = rId,
                    ultimaRutaNombre = rId ?: "Sin Ruta",
                    perfilesVentaJson = if (doc.get("perfilesVenta") != null) {
                        val raw = doc.get("perfilesVenta") as? List<*>
                        org.json.JSONArray(raw).toString()
                    } else null
                )
            }
            
            val vendedoresSet = snapshot.documents.mapNotNull { doc ->
                val puesto = doc.getString("puestoTrabajo")?.lowercase(Locale.getDefault()) ?: ""
                if (puesto.contains("vendedor") || puesto.contains("ruta")) doc.id else null
            }.toSet()

            _usersFlow.value = usersMap
            _allVendedoresFlow.value = vendedoresSet
        }
    }

    private fun escucharJornadas() {
        jornadasListener?.remove()
        jornadasListener = db.collection("jornadas")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _jornadasFlow.value = snapshot.documents.associate { 
                        it.id to (it.getBoolean("activo") ?: false)
                    }
                }
            }
    }

    private fun escucharRankingConfig() {
        rankingListener?.remove()
        rankingListener = db.collection("config").document("valor_clientes")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    _uiState.update { it.copy(
                        rankingAlto = snapshot.getDouble("alto") ?: 500.0,
                        rankingMedio = snapshot.getDouble("medio") ?: 300.0,
                        rankingBajo = snapshot.getDouble("bajo") ?: 150.0
                    ) }
                }
            }
    }

    private fun cargarDatosAdmin() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    _uiState.update { it.copy(
                        nombreAdmin = doc.getString("nombre") ?: "",
                        photoUrlAdmin = doc.getString("photo_url") ?: "",
                        puestoAdmin = doc.getString("puestoTrabajo") ?: "Administrador"
                    ) }
                }
            } catch (e: Exception) { }
        }
    }

    fun cargarDatosDashboard(fecha: Date) {
        cargarDatosDashboardRango(fecha, fecha)
    }

    fun cargarDatosDashboardRango(fechaInicio: Date, fechaFin: Date) {
        ventasListener?.remove()
        _uiState.update { it.copy(isLoading = true) }

        val cal = Calendar.getInstance()
        cal.time = fechaInicio
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val inicioTs = Timestamp(cal.time)
        
        cal.time = fechaFin
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        val finTs = Timestamp(cal.time)

        ventasListener = db.collection("ventas")
            .whereGreaterThanOrEqualTo("fecha", inicioTs)
            .whereLessThanOrEqualTo("fecha", finTs)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                    return@addSnapshotListener
                }

                snapshot?.let { snap ->
                    val ventasList = snap.documents.map { doc ->
                        val v = VentaEntity(
                            id = doc.id,
                            clienteId = doc.getString("clienteId") ?: "",
                            clienteNombre = doc.getString("clienteNombre") ?: "Cliente",
                            clienteImagenUrl = doc.getString("clienteImagenUrl"),
                            total = (doc.get("total") as? Number)?.toDouble() ?: 0.0,
                            metodoPago = doc.getString("metodoPago") ?: "",
                            vendedorId = doc.getString("vendedorId") ?: "",
                            vendedorNombre = doc.getString("vendedorNombre"),
                            almacenId = doc.getString("almacenId"),
                            fecha = doc.getTimestamp("fecha")?.toDate()?.time ?: 0L,
                            horaDispositivo = doc.getLong("horaDispositivo") ?: 0L,
                            horaVerificada = doc.getLong("horaVerificada") ?: 0L,
                            alertaTiempo = doc.getBoolean("alertaTiempo") ?: false,
                            latitudVenta = doc.getDouble("latitudVenta") ?: 0.0,
                            longitudVenta = doc.getDouble("longitudVenta") ?: 0.0,
                            fueraDeRango = doc.getBoolean("fueraDeRango") ?: false,
                            fotoEvidenciaVisita = doc.getString("fotoEvidenciaVisita"),
                            sincronizado = true,
                            firestoreId = doc.id,
                            estado = doc.getString("estado") ?: "pagada",
                            motivoCancelacion = doc.getString("motivoCancelacion"),
                            canceladoPorNombre = doc.getString("canceladoPorNombre"),
                            fechaCancelacion = doc.getTimestamp("fechaCancelacion")?.toDate()?.time
                        )
                        
                        // 🔥 CARGA DE DETALLES BAJO DEMANDA (Para Admin)
                        cargarDetallesVentaAdmin(v.id)
                        
                        v
                    }
                    _ventasFlow.value = ventasList
                }
            }
    }

    private fun cargarDetallesVentaAdmin(ventaId: String) {
        if (_detallesFlow.value.containsKey(ventaId)) return
        
        viewModelScope.launch {
            try {
                // Buscamos detalles en la subcolección 'productos' o 'detalles'
                var prodsSnap = db.collection("ventas").document(ventaId).collection("productos").get().await()
                if (prodsSnap.isEmpty) {
                    prodsSnap = db.collection("ventas").document(ventaId).collection("detalles").get().await()
                }

                val detalles = prodsSnap.documents.map { d ->
                    val pId = d.id
                    val baseId = pId.split("_")[0]
                    
                    com.gruposanangel.delivery.data.VentaDetalleEntity(
                        ventaId = ventaId,
                        productoId = baseId,
                        nombre = d.getString("nombre") ?: "Producto",
                        precio = (d.get("precio") as? Number)?.toDouble() ?: 0.0,
                        cantidad = (d.getLong("cantidad") ?: 0L).toInt(),
                        marca = d.getString("marca") ?: "Delisa",
                        categoria = d.getString("categoria") ?: "General",
                        stockId = pId
                    )
                }
                
                val current = _detallesFlow.value.toMutableMap()
                current[ventaId] = detalles
                _detallesFlow.value = current
            } catch (e: Exception) {
                Log.e("AdminVM", "Error cargando detalles ticket $ventaId", e)
            }
        }
    }

    private fun procesarDatos(
        todasLasVentasRaw: List<VentaEntity>,
        users: Map<String, UsuarioEntity>,
        vendedoresUids: Set<String>,
        jornadas: Map<String, Boolean>,
        detallesMap: Map<String, List<com.gruposanangel.delivery.data.VentaDetalleEntity>>,
        catalog: Map<String, Pair<String, String>>
    ): DashboardUiState {
        val resumen = vendedoresUids.mapNotNull { uid ->
            val user = users[uid] ?: return@mapNotNull null
            if (user.ultimaRutaNombre == "Sin Ruta") return@mapNotNull null

            val ventasVendedor = todasLasVentasRaw.filter { it.vendedorId == uid }
            val ventasActivas = ventasVendedor.filter { it.estado != "CANCELADA" }
            val totalVendido = ventasActivas.sumOf { it.total }
            val clientesConVentaReal = ventasActivas.map { it.clienteId }.distinct().size
            val promedio = if (ventasActivas.isNotEmpty()) totalVendido / ventasActivas.size else 0.0

            // --- Calcular Breakdown ---
            val perfiles = mutableListOf<com.gruposanangel.delivery.data.PerfilVenta>()
            try {
                val json = user.perfilesVentaJson
                if (!json.isNullOrBlank()) {
                    val array = org.json.JSONArray(json)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val filtrosArr = obj.getJSONArray("filtros")
                        val filtros = (0 until filtrosArr.length()).map { j ->
                            val fObj = filtrosArr.getJSONObject(j)
                            val catsArr = fObj.optJSONArray("categorias")
                            val cats = if (catsArr != null) (0 until catsArr.length()).map { catsArr.getString(it) } else emptyList()
                            com.gruposanangel.delivery.data.FiltroPerfil(fObj.getString("marca"), cats)
                        }
                        perfiles.add(com.gruposanangel.delivery.data.PerfilVenta(obj.getString("id"), obj.getString("nombre"), filtros))
                    }
                }
            } catch (e: Exception) { }

            val todosLosDetallesVendedor = ventasActivas.flatMap { detallesMap[it.id] ?: emptyList() }
            
            val breakdown = perfiles.map { perfil ->
                val detPerfil = todosLosDetallesVendedor.filter { d ->
                    // 🔥 RECUPERACIÓN DE CLASIFICACIÓN (Si el detalle no tiene marca/categoria, usamos el catálogo)
                    val realMarca = if (d.marca == "Delisa" && d.categoria == "General") {
                        catalog[d.productoId]?.first ?: d.marca
                    } else d.marca
                    
                    val realCat = if (d.marca == "Delisa" && d.categoria == "General") {
                        catalog[d.productoId]?.second ?: d.categoria
                    } else d.categoria

                    perfil.filtros.any { f ->
                        val mMatch = realMarca.trim().equals(f.marca.trim(), ignoreCase = true)
                        val cMatch = if (f.categorias.isNotEmpty()) f.categorias.any { it.trim().equals(realCat.trim(), ignoreCase = true) } else true
                        mMatch && cMatch
                    }
                }
                PerfilBreakdown(perfil.id, perfil.nombre, detPerfil.sumOf { it.precio * it.cantidad }, detPerfil.sumOf { it.cantidad })
            }

            SellerSummary(
                uid = uid,
                nombre = user.nombre,
                rutaNombre = user.ultimaRutaNombre ?: "Ruta",
                photoUrl = user.photoUrl ?: "",
                totalVendido = totalVendido,
                clientesConVenta = clientesConVentaReal,
                ticketPromedio = promedio,
                ventas = ventasVendedor,
                totalTicketsActivos = ventasActivas.size,
                estaEnRuta = jornadas[uid] ?: false,
                perfilesVenta = perfiles,
                breakdown = breakdown
            )
        }.sortedByDescending { it.totalVendido }

        val ventasActivasGlobal = todasLasVentasRaw.filter { it.estado != "CANCELADA" }
        val totalVentas = ventasActivasGlobal.sumOf { it.total }
        val totalTickets = ventasActivasGlobal.size
        val totalClientes = ventasActivasGlobal.map { it.clienteId }.distinct().size
        val promedioGlobal = if (totalTickets > 0) totalVentas / totalTickets else 0.0

        return _uiState.value.copy(
            isLoading = false,
            totalVentasDia = totalVentas,
            totalTicketsDia = totalTickets,
            totalClientesDia = totalClientes,
            ticketPromedioGlobal = promedioGlobal,
            resumenVendedores = resumen,
            todasLasVentasHoy = todasLasVentasRaw
        )
    }

    override fun onCleared() {
        super.onCleared()
        ventasListener?.remove()
        jornadasListener?.remove()
        usersListener?.remove()
        rankingListener?.remove()
    }
}

class DashboardAdminViewModelFactory(
    private val ventaRepository: VentaRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DashboardAdminViewModel(ventaRepository) as T
    }
}
