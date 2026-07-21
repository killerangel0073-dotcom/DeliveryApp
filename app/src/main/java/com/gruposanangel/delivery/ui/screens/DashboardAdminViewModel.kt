package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.gruposanangel.delivery.data.VentaEntity
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
    val estaEnRuta: Boolean = false // 🔥 Nuevo: Estado de jornada
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
    private val _usersFlow = MutableStateFlow<Map<String, Triple<String, String, String>>>(emptyMap())
    private val _allVendedoresFlow = MutableStateFlow<Set<String>>(emptySet())
    private val _jornadasFlow = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    init {
        escucharUsuarios()
        escucharJornadas()
        escucharRankingConfig()
        cargarDatosAdmin()
        
        // 🔥 MOTOR DE REACTIVIDAD: Combinar los 3 flujos en tiempo real
        combine(_ventasFlow, _usersFlow, _allVendedoresFlow, _jornadasFlow) { ventas, users, vendedores, jornadas ->
            procesarDatos(ventas, users, vendedores, jornadas)
        }.onEach { nuevoEstado ->
            _uiState.update { nuevoEstado }
        }.launchIn(viewModelScope)

        cargarDatosDashboard(Date())
    }

    private fun escucharUsuarios() {
        usersListener?.remove()
        usersListener = db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            
            val usersMap = snapshot.documents.associate { doc ->
                val nombre = doc.getString("nombre") ?: "Sin Nombre"
                val foto = doc.getString("photo_url") ?: ""
                val rutaRef = doc.getDocumentReference("rutaAsignada")
                val rutaNombre = rutaRef?.id ?: "Sin Ruta"
                doc.id to Triple(nombre, rutaNombre, foto)
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
                        VentaEntity(
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
                    }
                    _ventasFlow.value = ventasList
                }
            }
    }

    private fun procesarDatos(
        todasLasVentasRaw: List<VentaEntity>,
        users: Map<String, Triple<String, String, String>>,
        vendedoresUids: Set<String>,
        jornadas: Map<String, Boolean>
    ): DashboardUiState {
        // 🛡️ Filtro rápido usando la caché reactiva
        val todasLasVentas = todasLasVentasRaw.filter { 
            users[it.vendedorId]?.second != "Sin Ruta"
        }

        val resumen = vendedoresUids.mapNotNull { uid ->
            val info = users[uid] ?: return@mapNotNull null
            if (info.second == "Sin Ruta") return@mapNotNull null

            val ventasVendedor = todasLasVentas.filter { it.vendedorId == uid }
            val ventasActivas = ventasVendedor.filter { it.estado != "CANCELADA" }
            val totalVendido = ventasActivas.sumOf { it.total }
            val clientesConVentaReal = ventasActivas.map { it.clienteId }.distinct().size
            val promedio = if (ventasActivas.isNotEmpty()) totalVendido / ventasActivas.size else 0.0

            SellerSummary(
                uid = uid,
                nombre = info.first,
                rutaNombre = info.second,
                photoUrl = info.third,
                totalVendido = totalVendido,
                clientesConVenta = clientesConVentaReal,
                ticketPromedio = promedio,
                ventas = ventasVendedor,
                totalTicketsActivos = ventasActivas.size,
                estaEnRuta = jornadas[uid] ?: false
            )
        }.sortedByDescending { it.totalVendido }

        val ventasActivasGlobal = todasLasVentas.filter { it.estado != "CANCELADA" }
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
            todasLasVentasHoy = todasLasVentas
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
