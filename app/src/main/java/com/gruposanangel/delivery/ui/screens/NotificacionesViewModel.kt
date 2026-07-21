package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class NotificacionesUiState(
    val isLoading: Boolean = true,
    val notificaciones: List<Notificacion> = emptyList(),
    val error: String? = null,
    val showAuthDialog: Boolean = false,
    val isAuthenticating: Boolean = false,
    val authError: String? = null,
    val successMessage: String? = null,
    val ultimoAlmacenNombre: String? = null,
    val authExito: Boolean = false,
    val fechaInicio: Long = 0L,
    val fechaFin: Long = 0L
)

class NotificacionesViewModel(
    private val productoDao: ProductoDao,
    private val inventarioRepo: RepositoryInventario,
    private val usuarioRepo: RepositoryUsuario
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificacionesUiState())
    val uiState: StateFlow<NotificacionesUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listenerCargas: ListenerRegistration? = null
    private var listenerArqueos: ListenerRegistration? = null
    private val formatoFecha = SimpleDateFormat("EEEE, dd 'de' MMMM, hh:mm a", Locale("es", "MX"))
    
    private val _notificacionesNubeCargas = MutableStateFlow<List<Notificacion>>(emptyList())
    private val _notificacionesNubeArqueos = MutableStateFlow<List<Notificacion>>(emptyList())
    private val _notificacionesLocales = MutableStateFlow<List<Notificacion>>(emptyList())

    init {
        // Inicializar con la semana actual
        val cal = Calendar.getInstance(Locale("es", "MX"))
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val inicio = cal.timeInMillis
        
        val calFin = cal.clone() as Calendar
        calFin.add(Calendar.DAY_OF_YEAR, 6)
        calFin.set(Calendar.HOUR_OF_DAY, 23); calFin.set(Calendar.MINUTE, 59); calFin.set(Calendar.SECOND, 59); calFin.set(Calendar.MILLISECOND, 999)
        val fin = calFin.timeInMillis

        _uiState.update { it.copy(fechaInicio = inicio, fechaFin = fin) }
        
        configurarFlujoMaestro()
    }

    private fun configurarFlujoMaestro() {
        val uidActual = auth.currentUser?.uid ?: ""
        if (uidActual.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, error = "Usuario no identificado") }
            return
        }

        combine(_notificacionesLocales, _notificacionesNubeCargas, _notificacionesNubeArqueos) { locales, cargas, arqueos ->
            Log.d("NOTIF_VM", "Sync: Locales=${locales.size}, Cargas=${cargas.size}, Arqueos=${arqueos.size}")
            (locales + cargas + arqueos)
                .distinctBy { it.id } 
                .sortedByDescending { it.timestamp } 
        }.onEach { lista ->
            _uiState.update { it.copy(notificaciones = lista, isLoading = false) }
        }.catch { e ->
            Log.e("NOTIF_VM", "Error en combine", e)
            _uiState.update { it.copy(isLoading = false) }
        }.launchIn(viewModelScope)

        observarLocales()
        
        viewModelScope.launch {
            try {
                val usuario = usuarioRepo.obtenerUsuarioActual()
                val nombreAlmacen = usuario?.ultimoAlmacenNombre
                _uiState.update { it.copy(ultimoAlmacenNombre = nombreAlmacen) }

                if (!nombreAlmacen.isNullOrEmpty()) {
                    activarListenersNube(nombreAlmacen)
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                Log.e("NOTIF_VM", "Error cargando almacén", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
        
        viewModelScope.launch {
            kotlinx.coroutines.delay(8000)
            if (_uiState.value.isLoading) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private var localesJob: kotlinx.coroutines.Job? = null
    private fun observarLocales() {
        localesJob?.cancel()
        val uidActual = auth.currentUser?.uid ?: ""
        localesJob = inventarioRepo.obtenerMovimientosDesdeFlow(uidActual, _uiState.value.fechaInicio)
            .map { lista ->
                lista.filter { 
                    it.timestamp <= _uiState.value.fechaFin &&
                    ((it.tipo == "CARGA_INVENTARIO" && it.referenciaId?.contains("LOAD") == true) ||
                    it.tipo == "AJUSTE_ARQUEO_FALTANTE" || 
                    it.tipo == "AJUSTE_ARQUEO_SOBRANTE" ||
                    it.tipo == "AJUSTE_ARQUEO_OK")
                }
                .map { mov ->
                    val esArqueo = mov.tipo.contains("ARQUEO")
                    Notificacion(
                        id = mov.referenciaId ?: mov.id,
                        titulo = if (esArqueo) "ARQUEO DE INVENTARIO" else "CARGA MANUAL (LOCAL)",
                        mensaje = if (esArqueo) {
                            val prefijo = when {
                                mov.tipo.contains("FALTANTE") -> "Faltante"
                                mov.tipo.contains("SOBRANTE") -> "Sobrante"
                                else -> "Correcto"
                            }
                            "Resultado de auditoría: $prefijo de ${mov.cantidad} pzas en ${mov.nombreProducto}."
                        } else {
                            "Se cargaron ${mov.cantidad} pzas de ${mov.nombreProducto}."
                        },
                        fecha = try { formatoFecha.format(Date(mov.timestamp)) } catch(_:Exception) { "Reciente" },
                        timestamp = mov.timestamp,
                        esCarga = !esArqueo,
                        aceptada = true
                    )
                }
            }
            .onEach { _notificacionesLocales.value = it }
            .launchIn(viewModelScope)
    }

    fun actualizarFiltroFechas(inicio: Long, fin: Long) {
        _uiState.update { it.copy(fechaInicio = inicio, fechaFin = fin, isLoading = true) }
        observarLocales()
        val almacen = _uiState.value.ultimoAlmacenNombre
        if (!almacen.isNullOrEmpty()) {
            activarListenersNube(almacen)
        }
    }

    private fun activarListenersNube(nombreAlmacen: String) {
        listenerCargas?.remove()
        listenerArqueos?.remove()

        val inicioTs = com.google.firebase.Timestamp(Date(_uiState.value.fechaInicio))
        val finTs = com.google.firebase.Timestamp(Date(_uiState.value.fechaFin))

        listenerCargas = db.collection("ordenesTransferencia")
            .whereEqualTo("destino", nombreAlmacen)
            .whereGreaterThanOrEqualTo("timestamp", inicioTs)
            .whereLessThanOrEqualTo("timestamp", finTs)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("NOTIF_VM", "Error cargas", e)
                    return@addSnapshotListener
                }
                val ords = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val ts = data["timestamp"] as? com.google.firebase.Timestamp
                    val estado = data["estado"] as? String ?: "PENDIENTE"
                    val motivo = data["motivoCancelacion"] as? String
                    
                    // 🔥 Calcular el monto total de la carga
                    val productosRaw = data["productos"] as? List<Map<String, Any>> ?: emptyList()
                    val totalMonto = productosRaw.sumOf { p ->
                        val cant = (p["cantidad"] as? Number)?.toDouble() ?: 0.0
                        val prec = (p["precio"] as? Number)?.toDouble() ?: 0.0
                        cant * prec
                    }
                    
                    Notificacion(
                        id = doc.id,
                        titulo = if (estado == "CANCELADA") "CARGA ANULADA" else "Carga de Almacén",
                        mensaje = if (estado == "CANCELADA") 
                            "Esta transferencia fue cancelada"
                        else 
                            "Transferencia desde " + (doc.getString("origen") ?: "Almacén"),
                        fecha = ts?.toDate()?.let { formatoFecha.format(it) } ?: "Pendiente",
                        timestamp = ts?.seconds?.let { it * 1000 } ?: 0L,
                        esCarga = true,
                        aceptada = estado == "COMPLETADA" || estado == "ACEPTADA",
                        estado = estado,
                        monto = totalMonto,
                        motivo = motivo
                    )
                } ?: emptyList()
                _notificacionesNubeCargas.value = ords
            }

        listenerArqueos = db.collection("ajustes_inventario")
            .whereEqualTo("almacenNombre", nombreAlmacen)
            .whereIn("tipo", listOf("AJUSTE_ARQUEO_FALTANTE", "AJUSTE_ARQUEO_SOBRANTE", "AJUSTE_ARQUEO_OK"))
            .limit(200)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("NOTIF_VM", "Error arqueos", e)
                    return@addSnapshotListener
                }
                
                val arqs = snapshot?.documents?.groupBy { it.getString("referenciaId") ?: it.id }
                    ?.mapNotNull { (refId, docs) ->
                        val first = docs.first()
                        val tsRaw = first.get("timestamp")
                        val tsMillis = when (tsRaw) {
                            is com.google.firebase.Timestamp -> tsRaw.toDate().time
                            is Number -> tsRaw.toLong()
                            else -> 0L
                        }

                        if (tsMillis < _uiState.value.fechaInicio || tsMillis > _uiState.value.fechaFin) return@mapNotNull null
                        
                        val totalFaltantes = docs.filter { it.getString("tipo") == "AJUSTE_ARQUEO_FALTANTE" }.sumOf { it.getLong("cantidad")?.toInt() ?: 0 }
                        val totalSobrantes = docs.filter { it.getString("tipo") == "AJUSTE_ARQUEO_SOBRANTE" }.sumOf { it.getLong("cantidad")?.toInt() ?: 0 }
                        
                        Notificacion(
                            id = refId,
                            titulo = "AUDITORÍA FINALIZADA",
                            mensaje = "Arqueo registrado. Resumen: -$totalFaltantes faltantes, +$totalSobrantes sobrantes.",
                            fecha = if (tsMillis > 0) formatoFecha.format(Date(tsMillis)) else "Reciente",
                            timestamp = tsMillis,
                            esCarga = false,
                            aceptada = true
                        )
                    } ?: emptyList()
                _notificacionesNubeArqueos.value = arqs
            }
    }

    fun abrirDialogoAutorizacion() { _uiState.update { it.copy(showAuthDialog = true, authError = null) } }
    fun cerrarDialogos() { _uiState.update { it.copy(showAuthDialog = false, authError = null) } }

    fun autorizarCarga(pass: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, authError = null) }
            try {
                val userQuery = db.collection("users")
                    .whereEqualTo("contraseña", pass.trim())
                    .whereEqualTo("activo", true)
                    .get().await()
                
                if (userQuery.isEmpty && pass.trim() != "8888") {
                    _uiState.update { it.copy(isAuthenticating = false, authError = "Contraseña incorrecta") }
                } else {
                    _uiState.update { it.copy(isAuthenticating = false, showAuthDialog = false, authExito = true) }
                }
            } catch (e: Exception) {
                if (pass.trim() == "8888") {
                    _uiState.update { it.copy(isAuthenticating = false, showAuthDialog = false, authExito = true) }
                } else {
                    _uiState.update { it.copy(isAuthenticating = false, authError = "Error de red. Usa PIN maestro.") }
                }
            }
        }
    }

    fun resetAuthExito() { _uiState.update { it.copy(authExito = false) } }
    fun clearMessages() { _uiState.update { it.copy(successMessage = null, error = null) } }

    override fun onCleared() {
        super.onCleared()
        listenerCargas?.remove()
        listenerArqueos?.remove()
    }
}

class NotificacionesViewModelFactory(
    private val productoDao: ProductoDao,
    private val inventarioRepo: RepositoryInventario,
    private val usuarioRepo: RepositoryUsuario
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NotificacionesViewModel(productoDao, inventarioRepo, usuarioRepo) as T
    }
}
