package com.gruposanangel.delivery.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

data class UsuariosAdminUiState(
    val isLoading: Boolean = false,
    val usuarios: List<UsuarioEntity> = emptyList(),
    val usuarioSeleccionado: UsuarioEntity? = null,
    val isNewUserMode: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null
)

class UsuariosAdminViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UsuariosAdminUiState())
    val uiState: StateFlow<UsuariosAdminUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    init {
        cargarUsuarios()
    }

    fun cargarUsuarios() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val snapshot = db.collection("users").get().await()
                val lista = snapshot.documents.map { doc ->
                    UsuarioEntity(
                        uid = doc.id,
                        nombre = doc.getString("nombre") ?: "",
                        email = doc.getString("email"),
                        photoUrl = doc.getString("photo_url"),
                        puestoTrabajo = doc.getString("puestoTrabajo"),
                        activo = doc.getBoolean("activo") ?: true,
                        licenciaConducir = doc.getString("licenciaConducir"),
                        credencialElector = doc.getString("credencialElector")
                    )
                }
                _uiState.update { it.copy(usuarios = lista, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun seleccionarUsuario(usuario: UsuarioEntity?) {
        if (usuario == null) {
            _uiState.update { it.copy(usuarioSeleccionado = null, isNewUserMode = true) }
        } else {
            _uiState.update { it.copy(usuarioSeleccionado = usuario, isNewUserMode = false) }
        }
    }

    fun guardarUsuario(
        nombre: String,
        email: String,
        puesto: String,
        activo: Boolean,
        licencia: String,
        credencial: String,
        imageFile: File?
    ) {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                var photoUrl: String? = null
                
                // 1. Subir imagen si se seleccionó una nueva
                if (imageFile != null) {
                    val ref = storage.reference.child("users/${UUID.randomUUID()}.jpg")
                    val fileUri = android.net.Uri.fromFile(imageFile)
                    ref.putFile(fileUri).await()
                    photoUrl = ref.downloadUrl.await().toString()
                }

                val data = mutableMapOf<String, Any>(
                    "nombre" to nombre,
                    "email" to email,
                    "puestoTrabajo" to puesto,
                    "activo" to activo,
                    "licenciaConducir" to licencia,
                    "credencialElector" to credencial
                )
                
                photoUrl?.let { data["photo_url"] = it }

                if (state.isNewUserMode) {
                    db.collection("users").add(data).await()
                    _uiState.update { it.copy(successMessage = "Usuario creado con éxito") }
                } else {
                    val uid = state.usuarioSeleccionado?.uid ?: return@launch
                    db.collection("users").document(uid).update(data).await()
                    _uiState.update { it.copy(successMessage = "Usuario actualizado correctamente") }
                }
                cargarUsuarios()
                seleccionarUsuario(null)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun eliminarUsuario(uid: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).delete().await()
                _uiState.update { it.copy(successMessage = "Usuario eliminado definitivamente") }
                cargarUsuarios()
                seleccionarUsuario(null)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
