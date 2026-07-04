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
    val totalTicketsActivos: Int = 0 // 🔥 Nuevo
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
)

class DashboardAdminViewModel(
    private val ventaRepository: VentaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private var ventasListener: ListenerRegistration? = null
    private var usersCache: Map<String, Triple<String, String, String>> = emptyMap()
    private var allVendedoresUids: Set<String> = emptySet()

    init {
        // Precargar info de usuarios una vez al inicio
        precargarUsuarios()
        cargarDatosAdmin()
        cargarDatosDashboard(Date())
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

    private fun precargarUsuarios() {
        viewModelScope.launch {
            try {
                val usersSnap = db.collection("users").get().await()
                usersCache = usersSnap.documents.asSequence().associate { doc ->
                    val nombre = doc.getString("nombre") ?: "Sin Nombre"
                    val foto = doc.getString("photo_url") ?: ""
                    val rutaRef = doc.getDocumentReference("rutaAsignada")
                    val rutaNombre = rutaRef?.id ?: "Sin Ruta"
                    doc.id to Triple(nombre, rutaNombre, foto)
                }
                
                allVendedoresUids = usersSnap.documents.asSequence().mapNotNull { doc ->
                    val puesto = doc.getString("puestoTrabajo")?.lowercase(Locale.getDefault()) ?: ""
                    if (puesto.contains("vendedor") || puesto.contains("ruta")) {
                        doc.id
                    } else null
                }.toSet()
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error precargando usuarios", e)
            }
        }
    }

    fun cargarDatosDashboard(fecha: Date) {
        cargarDatosDashboardRango(fecha, fecha)
    }

    fun cargarDatosDashboardRango(fechaInicio: Date, fechaFin: Date) {
        // Cancelar listener previo si existe
        ventasListener?.remove()
        
        _uiState.update { it.copy(isLoading = true) }

        // Rango de fecha
        val cal = Calendar.getInstance()
        
        // Inicio del periodo
        cal.time = fechaInicio
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val inicioTs = Timestamp(cal.time)
        
        // Fin del periodo
        cal.time = fechaFin
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val finTs = Timestamp(cal.time)

        // Escuchar cambios en tiempo real
        ventasListener = db.collection("ventas")
            .whereGreaterThanOrEqualTo("fecha", inicioTs)
            .whereLessThanOrEqualTo("fecha", finTs)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("DashboardVM", "Error en listener", error)
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                    return@addSnapshotListener
                }

                snapshot?.let { snap ->
                    procesarSnapshotVentas(snap.documents.map { doc ->
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
                    })
                }
            }
    }

    private fun procesarSnapshotVentas(todasLasVentasRaw: List<VentaEntity>) {
        viewModelScope.launch {
            // 🔥 RECARGA DE CACHE SIEMPRE: Para detectar cambios en rutas o nuevos usuarios al instante
            try {
                val usersSnap = db.collection("users").get().await()
                usersCache = usersSnap.documents.asSequence().associate { doc ->
                    val nombre = doc.getString("nombre") ?: "Sin Nombre"
                    val foto = doc.getString("photo_url") ?: ""
                    val rutaRef = doc.getDocumentReference("rutaAsignada")
                    val rutaNombre = rutaRef?.id ?: "Sin Ruta"
                    doc.id to Triple(nombre, rutaNombre, foto)
                }
                allVendedoresUids = usersSnap.documents.asSequence().mapNotNull { doc ->
                    val puesto = doc.getString("puestoTrabajo")?.lowercase(Locale.getDefault()) ?: ""
                    if (puesto.contains("vendedor") || puesto.contains("ruta")) {
                        doc.id
                    } else null
                }.toSet()
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error recargando cache de usuarios", e)
            }

            // 🛡️ FILTRO DE CONSISTENCIA: Solo contar ventas de personal con ruta asignada
            val todasLasVentas = todasLasVentasRaw.filter { 
                usersCache[it.vendedorId]?.second != "Sin Ruta"
            }

            val resumen = allVendedoresUids.mapNotNull { uid ->
                val info = usersCache[uid] ?: return@mapNotNull null
                
                // 🚫 REQUISITO: No mostrar vendedores sin ruta asignada
                if (info.second == "Sin Ruta") return@mapNotNull null

                val ventasVendedor = todasLasVentas.filter { it.vendedorId == uid }
                // 🔥 Solo sumar ventas activas (no canceladas) para el total vendido
                val totalVendido = ventasVendedor.filter { it.estado != "CANCELADA" }.sumOf { it.total }
                val clientesConVentaReal = ventasVendedor.filter { it.estado != "CANCELADA" }.map { it.clienteId }.distinct().size
                val promedio = if (ventasVendedor.any { it.estado != "CANCELADA" }) {
                    totalVendido / ventasVendedor.count { it.estado != "CANCELADA" }
                } else 0.0

                SellerSummary(
                    uid = uid,
                    nombre = info.first,
                    rutaNombre = info.second,
                    photoUrl = info.third,
                    totalVendido = totalVendido,
                    clientesConVenta = clientesConVentaReal,
                    ticketPromedio = promedio,
                    ventas = ventasVendedor,
                    totalTicketsActivos = ventasVendedor.count { it.estado != "CANCELADA" } // 🔥
                )
            }.sortedByDescending { it.totalVendido } // Los que más venden arriba

            val totalVentas = todasLasVentas.filter { it.estado != "CANCELADA" }.sumOf { it.total }
            val totalTickets = todasLasVentas.count { it.estado != "CANCELADA" }
            val totalClientes = todasLasVentas.filter { it.estado != "CANCELADA" }.map { it.clienteId }.distinct().size
            val promedioGlobal = if (totalTickets > 0) totalVentas / totalTickets else 0.0

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    totalVentasDia = totalVentas,
                    totalTicketsDia = totalTickets,
                    totalClientesDia = totalClientes,
                    ticketPromedioGlobal = promedioGlobal,
                    resumenVendedores = resumen,
                    todasLasVentasHoy = todasLasVentas
                )
            }
        }
    }

    fun migrarClientesARuta1(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val clientesSnap = db.collection("clientes").get().await()
                var actualizados = 0
                
                val batch = db.batch()
                clientesSnap.documents.forEach { doc ->
                    if (!doc.contains("rutaId")) {
                        batch.update(doc.reference, "rutaId", "Ruta 1 Delisa")
                        actualizados++
                    }
                }
                
                if (actualizados > 0) {
                    batch.commit().await()
                }
                
                _uiState.update { it.copy(isLoading = false) }
                onComplete(actualizados)
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error migrando clientes", e)
                _uiState.update { it.copy(isLoading = false, error = "Error migración: ${e.message}") }
            }
        }
    }

    fun migrarRutasClientes(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val clientesSnap = db.collection("clientes").get().await()
                var actualizados = 0
                
                val batch = db.batch()
                clientesSnap.documents.forEach { doc ->
                    val rutaActual = doc.getString("rutaId")
                    val nuevaRuta = when (rutaActual) {
                        "Vendedor Delisa R1" -> "Ruta 1 Delisa"
                        "Vendedor Delisa R2" -> "Ruta 2 Delisa"
                        else -> null
                    }
                    
                    if (nuevaRuta != null) {
                        batch.update(doc.reference, "rutaId", nuevaRuta)
                        actualizados++
                    }
                }
                
                if (actualizados > 0) {
                    batch.commit().await()
                }
                
                _uiState.update { it.copy(isLoading = false) }
                onComplete(actualizados)
            } catch (e: Exception) {
                Log.e("DashboardVM", "Error migrando clientes", e)
                _uiState.update { it.copy(isLoading = false, error = "Error migración: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ventasListener?.remove()
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
