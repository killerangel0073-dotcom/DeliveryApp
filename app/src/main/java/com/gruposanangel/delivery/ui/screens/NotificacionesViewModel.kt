package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

data class NotificacionesUiState(
    val isLoading: Boolean = true,
    val notificaciones: List<Notificacion> = emptyList(),
    val error: String? = null
)

class NotificacionesViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(NotificacionesUiState())
    val uiState: StateFlow<NotificacionesUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listenerRegistration: ListenerRegistration? = null
    private val formatoFecha = SimpleDateFormat("EEEE, dd 'de' MMMM, hh:mm a", Locale("es", "MX"))

    init {
        iniciarEscuchaNotificaciones()
    }

    private fun iniciarEscuchaNotificaciones() {
        val uid = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            try {
                // 1. Obtener el almacén del usuario
                val userQuery = db.collection("users").whereEqualTo("uid", uid).get().await()
                val userDoc = userQuery.documents.firstOrNull() ?: return@launch
                
                val rutaRef = userDoc.getDocumentReference("rutaAsignada")
                val rutaSnap = rutaRef?.get()?.await()
                val almacenRef = rutaSnap?.getDocumentReference("almacenAsignado")
                val almacenSnap = almacenRef?.get()?.await()
                val nombreAlmacen = almacenSnap?.getString("nombre") ?: userDoc.getString("ultimoAlmacenNombre")

                if (nombreAlmacen.isNullOrEmpty()) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                // 2. Configurar Listener en tiempo real
                listenerRegistration?.remove()
                listenerRegistration = db.collection("ordenesTransferencia")
                    .whereEqualTo("destino", nombreAlmacen)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            _uiState.update { it.copy(error = e.message, isLoading = false) }
                            return@addSnapshotListener
                        }

                        val lista = snapshot?.documents?.map { doc ->
                            val fechaTimestamp = doc.getTimestamp("timestamp")?.toDate()
                            val fechaFormateada = fechaTimestamp?.let { formatoFecha.format(it) } ?: ""
                            val estado = doc.getString("estado") ?: "PENDIENTE"
                            
                            Notificacion(
                                id = doc.id,
                                titulo = "Carga de Almacén",
                                mensaje = "Transferencia desde " + (doc.getString("origen") ?: "Almacén"),
                                fecha = fechaFormateada,
                                esCarga = true,
                                aceptada = estado == "COMPLETADA" || estado == "ACEPTADA"
                            )
                        } ?: emptyList()

                        _uiState.update { it.copy(notificaciones = lista, isLoading = false) }
                    }

            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
