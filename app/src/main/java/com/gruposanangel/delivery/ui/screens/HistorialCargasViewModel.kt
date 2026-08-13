package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class CargaResumen(
    val id: String,
    val origen: String,
    val destino: String,
    val estado: String,
    val fechaFormateada: String,
    val timestamp: Long,
    val totalPiezas: Int,
    val montoTotal: Double,
    val productos: List<Plantilla_Producto>,
    val esEmergencia: Boolean = false,
    val metodoAuditoria: String? = null
)

data class HistorialCargasUiState(
    val isLoading: Boolean = false,
    val cargas: List<CargaResumen> = emptyList(),
    val arqueos: List<CargaResumen> = emptyList(), // 🔥 Nueva lista separada
    val listaVendedores: List<String> = emptyList(),
    val filtroVendedor: String = "Todos",
    val fechaInicio: Long = System.currentTimeMillis(),
    val fechaFin: Long = System.currentTimeMillis(),
    val error: String? = null
)

class HistorialCargasViewModel(
    private val usuarioRepo: com.gruposanangel.delivery.RepositoryUsuario,
    private val inventarioRepo: RepositoryInventario
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistorialCargasUiState())
    val uiState: StateFlow<HistorialCargasUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private var snapshotListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX"))
    
    // 🔥 NUEVO: Para control de permisos en la UI
    var userRole by mutableStateOf("")
    private var userUid = ""
    private var userName = ""

    init {
        // Cargar datos del usuario
        viewModelScope.launch {
            val user = usuarioRepo.obtenerUsuarioActual()
            userRole = user?.puestoTrabajo ?: ""
            userUid = user?.uid ?: ""
            userName = user?.nombre ?: "Admin"
        }
        // Establecer rango de la semana actual (Lunes a Domingo) por defecto
        val cal = Calendar.getInstance(Locale("es", "MX"))
        cal.firstDayOfWeek = Calendar.MONDAY
        
        // Ir al lunes de esta semana
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        
        // Si hoy es domingo y el Calendar lo movió al lunes de MAÑANA, retroceder 7 días
        if (cal.timeInMillis > System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, -7)
        }
        
        val inicio = cal.timeInMillis
        
        // Calcular el domingo (inicio + 6 días)
        val calEnd = cal.clone() as Calendar
        calEnd.add(Calendar.DAY_OF_YEAR, 6)
        calEnd.set(Calendar.HOUR_OF_DAY, 23)
        calEnd.set(Calendar.MINUTE, 59)
        calEnd.set(Calendar.SECOND, 59)
        calEnd.set(Calendar.MILLISECOND, 999)
        val fin = calEnd.timeInMillis
        
        _uiState.update { it.copy(fechaInicio = inicio, fechaFin = fin) }
        
        cargarVendedores()
        activarListenerCargas()
    }

    private fun cargarVendedores() {
        viewModelScope.launch {
            try {
                // Obtener almacenes que son vendedores
                val snapshot = db.collection("almacenes").get().await()
                val vendedores = snapshot.documents
                    .map { it.id }
                    .filter { it.startsWith("Vendedor") }
                    .sorted()
                _uiState.update { it.copy(listaVendedores = listOf("Todos") + vendedores) }
            } catch (e: Exception) {
                Log.e("HistorialCargasVM", "Error cargando vendedores", e)
            }
        }
    }

    fun cargarHistorial() {
        // Esta función ahora simplemente refresca el listener
        activarListenerCargas()
    }

    private fun activarListenerCargas() {
        snapshotListener?.remove()
        
        val state = _uiState.value
        // 🔥 LIMPIEZA ATÓMICA: Borramos la lista anterior y errores antes de iniciar la nueva búsqueda
        _uiState.update { it.copy(isLoading = true, cargas = emptyList(), error = null) }

        // Buscamos ordenes de transferencia cuyo destino sea un vendedor o vengan del almacén central
        var query = db.collection("ordenesTransferencia")
            .whereGreaterThanOrEqualTo("timestamp", Timestamp(Date(state.fechaInicio)))
            .whereLessThanOrEqualTo("timestamp", Timestamp(Date(state.fechaFin)))

        // Aplicamos el filtro de destino si no es "Todos"
        if (state.filtroVendedor != "Todos") {
            query = query.whereEqualTo("destino", state.filtroVendedor)
        }

        snapshotListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("HistorialCargasVM", "Error en listener: ${error.message}")
                _uiState.update { it.copy(isLoading = false, error = error.message) }
                return@addSnapshotListener
            }

            viewModelScope.launch {
                val listaCargas = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val ts = data["timestamp"] as? Timestamp
                    val timestamp = ts?.toDate()?.time ?: 0L
                    
                    val productosRaw = data["productos"] as? List<Map<String, Any>> ?: emptyList()
                    val productos = productosRaw.map { p ->
                        Plantilla_Producto(
                            id = p["productoId"] as? String ?: "",
                            nombre = p["nombre"] as? String ?: "",
                            precio = (p["precio"] as? Number)?.toDouble() ?: 0.0,
                            cantidad = (p["cantidad"] as? Number)?.toInt() ?: 0
                        )
                    }

                    CargaResumen(
                        id = doc.id,
                        origen = data["origen"] as? String ?: "",
                        destino = data["destino"] as? String ?: "",
                        estado = data["estado"] as? String ?: "PENDIENTE",
                        fechaFormateada = sdf.format(Date(timestamp)),
                        timestamp = timestamp,
                        totalPiezas = productos.sumOf { it.cantidad },
                        montoTotal = productos.sumOf { it.cantidad * it.precio },
                        productos = productos,
                        esEmergencia = data["esEmergencia"] as? Boolean ?: false
                    )
                } ?: emptyList()

                // 🔥 2. CONSULTAR TAMBIÉN LOS ARQUEOS (Ajustes de auditoría) con Blindaje
                try {
                    var arqueosQuery: com.google.firebase.firestore.Query = db.collection("ajustes_inventario")
                        .whereIn("tipo", listOf("AJUSTE_ARQUEO_FALTANTE", "AJUSTE_ARQUEO_SOBRANTE", "AJUSTE_ARQUEO_OK"))
                        .whereGreaterThanOrEqualTo("timestamp", state.fechaInicio)
                        .whereLessThanOrEqualTo("timestamp", state.fechaFin)
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)

                    if (state.filtroVendedor != "Todos") {
                        arqueosQuery = arqueosQuery.whereEqualTo("almacenNombre", state.filtroVendedor)
                    }

                    val arqueosSnap = arqueosQuery.get().await()
                    
                    // Agrupamos los micro-ajustes por su referenciaId (El Arqueo Completo)
                    val catalogo = inventarioRepo.obtenerProductosLocal().first()

                    val listaArqueos = arqueosSnap.documents.groupBy { it.getString("referenciaId") ?: "SIN_ID" }
                        .mapNotNull { (referenciaId, docs) ->
                            if (referenciaId == "SIN_ID") return@mapNotNull null
                            val primerDoc = docs.first()
                            val timestamp = primerDoc.getLong("timestamp") ?: 0L
                            val metodo = primerDoc.getString("metodoAuditoria")
                            
                            val productos = docs.map { d ->
                                val tipo = d.getString("tipo")
                                val cant = d.getLong("cantidad")?.toInt() ?: 0
                                val prodId = d.getString("productoId") ?: ""
                                val precio = catalogo.find { it.productoId == prodId }?.precio ?: 0.0
                                Plantilla_Producto(
                                    id = prodId,
                                    nombre = d.getString("nombreProducto") ?: "Producto",
                                    precio = precio, 
                                    cantidad = if (tipo == "AJUSTE_ARQUEO_FALTANTE") -cant else cant
                                )
                            }

                            CargaResumen(
                                id = referenciaId,
                                origen = if (metodo == "LIQUIDACION") "LIQUIDACIÓN" else "AUDITORÍA FÍSICA",
                                destino = primerDoc.getString("almacenNombre") ?: "",
                                estado = if (metodo == "LIQUIDACION") "LIQUIDADO" else "ARQUEADO",
                                fechaFormateada = sdf.format(Date(timestamp)),
                                timestamp = timestamp,
                                totalPiezas = productos.sumOf { it.cantidad },
                                montoTotal = productos.sumOf { it.cantidad * it.precio }, // Ahora sí calculamos el valor de la diferencia
                                productos = productos,
                                metodoAuditoria = metodo
                            )
                        }.sortedByDescending { it.timestamp }

                    _uiState.update { it.copy(
                        cargas = listaCargas.sortedByDescending { it.timestamp },
                        arqueos = listaArqueos,
                        isLoading = false
                    ) }
                } catch (e: Exception) {
                    Log.e("HistorialCargasVM", "Error en Arqueos (Falta índice Firestore?): ${e.message}")
                    // Si fallan los arqueos por falta de índice, al menos mostramos las cargas normales
                    _uiState.update { it.copy(
                        cargas = listaCargas.sortedByDescending { it.timestamp },
                        isLoading = false,
                        error = if (e.message?.contains("index") == true) "Preparando base de datos de auditoría..." else e.message
                    ) }
                }
            }
        }
    }

    fun actualizarFiltroVendedor(vendedor: String) {
        _uiState.update { it.copy(filtroVendedor = vendedor) }
        activarListenerCargas()
    }

    fun actualizarFechas(inicio: Long, fin: Long) {
        _uiState.update { it.copy(fechaInicio = inicio, fechaFin = fin) }
        activarListenerCargas()
    }

    /**
     * 🔥 CANCELAR CARGA (Solo CEO/Gerente y si está PENDIENTE)
     */
    fun cancelarCarga(ordenId: String, motivo: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                db.collection("ordenesTransferencia").document(ordenId).update(
                    mapOf(
                        "estado" to "CANCELADA",
                        "motivoCancelacion" to motivo,
                        "canceladoPorUid" to userUid,
                        "canceladoPorNombre" to userName,
                        "fechaCancelacion" to FieldValue.serverTimestamp()
                    )
                ).await()
                
                _uiState.update { it.copy(isLoading = false) }
                onComplete(true, "Carga cancelada exitosamente")
                
            } catch (e: Exception) {
                Log.e("HistorialCargasVM", "Error cancelando carga: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
                onComplete(false, e.message ?: "Error desconocido")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        snapshotListener?.remove()
    }
}

class HistorialCargasViewModelFactory(
    private val usuarioRepo: com.gruposanangel.delivery.RepositoryUsuario,
    private val inventarioRepo: RepositoryInventario
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = 
        HistorialCargasViewModel(usuarioRepo, inventarioRepo) as T
}
