package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val productos: List<Plantilla_Producto>
)

data class HistorialCargasUiState(
    val isLoading: Boolean = false,
    val cargas: List<CargaResumen> = emptyList(),
    val listaVendedores: List<String> = emptyList(),
    val filtroVendedor: String = "Todos",
    val fechaInicio: Long = System.currentTimeMillis(),
    val fechaFin: Long = System.currentTimeMillis(),
    val error: String? = null
)

class HistorialCargasViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HistorialCargasUiState())
    val uiState: StateFlow<HistorialCargasUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private var snapshotListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX"))

    init {
        // Establecer rango de hoy por defecto
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val inicio = cal.timeInMillis
        
        val calEnd = Calendar.getInstance()
        calEnd.set(Calendar.HOUR_OF_DAY, 23); calEnd.set(Calendar.MINUTE, 59); calEnd.set(Calendar.SECOND, 59)
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

            val lista = snapshot?.documents?.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val ts = data["timestamp"] as? Timestamp
                val timestamp = ts?.toDate()?.time ?: 0L
                
                // Mapeo de productos igual que en Notificaciones
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
                    productos = productos
                )
            }?.sortedByDescending { it.timestamp } ?: emptyList()

            _uiState.update { it.copy(cargas = lista, isLoading = false) }
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

    override fun onCleared() {
        super.onCleared()
        snapshotListener?.remove()
    }
}
