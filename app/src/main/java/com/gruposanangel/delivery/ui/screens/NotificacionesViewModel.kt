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
    val authExito: Boolean = false
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
    private var listenerRegistration: ListenerRegistration? = null
    private val formatoFecha = SimpleDateFormat("EEEE, dd 'de' MMMM, hh:mm a", Locale("es", "MX"))
    
    private val _notificacionesNube = MutableStateFlow<List<Notificacion>>(emptyList())
    private val _notificacionesLocales = MutableStateFlow<List<Notificacion>>(emptyList())

    init {
        configurarFlujoMaestro()
    }

    private fun configurarFlujoMaestro() {
        val uidActual = auth.currentUser?.uid ?: ""
        if (uidActual.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, error = "Usuario no identificado") }
            return
        }

        // 1. COMBINACIÓN INTELIGENTE (LOCAL + NUBE)
        combine(_notificacionesLocales, _notificacionesNube) { locales, nube ->
            Log.d("NOTIF_VM", "Sync: Locales=${locales.size}, Nube=${nube.size}")
            (locales + nube)
                .distinctBy { it.id } // Evita duplicados cuando la local se sube a la nube
                .sortedByDescending { it.timestamp }
        }.onEach { lista ->
            _uiState.update { it.copy(notificaciones = lista, isLoading = false) }
        }.launchIn(viewModelScope)

        // 2. OBSERVADOR LOCAL (ROOM) - Prioridad #1
        viewModelScope.launch {
            val hace7Dias = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            inventarioRepo.obtenerMovimientosDesdeFlow(uidActual, hace7Dias)
                .map { lista ->
                    lista.filter { 
                        it.tipo == "CARGA_INVENTARIO" && it.referenciaId?.contains("LOAD") == true 
                    }
                    .distinctBy { it.referenciaId ?: it.id }
                    .map { mov ->
                        Notificacion(
                            id = mov.referenciaId ?: mov.id,
                            titulo = "CARGA MANUAL (LOCAL)",
                            mensaje = "Se cargaron ${mov.cantidad} pzas de ${mov.nombreProducto}.",
                            fecha = try { formatoFecha.format(Date(mov.timestamp)) } catch(_:Exception) { "Reciente" },
                            timestamp = mov.timestamp,
                            esCarga = true,
                            aceptada = true
                        )
                    }
                }
                .onEach { _notificacionesLocales.value = it }
                .launchIn(viewModelScope)
        }

        // 3. OBTENER PERFIL Y ACTIVAR NUBE
        viewModelScope.launch {
            try {
                val usuario = usuarioRepo.obtenerUsuarioActual()
                val nombreAlmacen = usuario?.ultimoAlmacenNombre
                _uiState.update { it.copy(ultimoAlmacenNombre = nombreAlmacen) }

                if (!nombreAlmacen.isNullOrEmpty()) {
                    activarListenerNube(nombreAlmacen)
                }
            } catch (e: Exception) {
                Log.e("NOTIF_VM", "Error cargando almacén", e)
            }
        }
    }

    private fun activarListenerNube(nombreAlmacen: String) {
        listenerRegistration?.remove()
        listenerRegistration = db.collection("ordenesTransferencia")
            .whereEqualTo("destino", nombreAlmacen)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                val lista = snapshot?.documents?.mapNotNull { doc ->
                    val ts = doc.getTimestamp("timestamp")
                    Notificacion(
                        id = doc.id,
                        titulo = "Carga de Almacén",
                        mensaje = "Transferencia desde " + (doc.getString("origen") ?: "Almacén"),
                        fecha = ts?.toDate()?.let { formatoFecha.format(it) } ?: "Pendiente",
                        timestamp = ts?.seconds?.let { it * 1000 } ?: 0L,
                        esCarga = true,
                        aceptada = doc.getString("estado") == "COMPLETADA" || doc.getString("estado") == "ACEPTADA"
                    )
                } ?: emptyList()
                
                _notificacionesNube.value = lista
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
        listenerRegistration?.remove()
    }
}

class NotificacionesViewModelFactory(
    private val productoDao: ProductoDao,
    private val inventarioRepo: RepositoryInventario,
    private val usuarioRepo: RepositoryUsuario
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = 
        NotificacionesViewModel(productoDao, inventarioRepo, usuarioRepo) as T
}
