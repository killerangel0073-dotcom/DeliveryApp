package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.utilidades.FcmUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false,
    val isUserLoggedIn: Boolean = false
)

/**
 * ViewModel para el inicio de sesión con enfoque Offline-First.
 */
class LoginViewModel(private val usuarioRepository: RepositoryUsuario) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // checkUserSession se elimina porque la sesión ahora se gestiona 
    // de forma centralizada y reactiva en MainActivity.kt

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Por favor, llena todos los campos") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                // 1. Autenticación en Firebase
                val uid = usuarioRepository.login(email, password)

                // 2. Sincronización inmediata con Room (Offline-First)
                usuarioRepository.syncUsuario(uid)

                // 3. Registrar Token FCM para Notificaciones Push
                FcmUtils.updateFcmToken(uid)

                // 4. Notificar éxito
                _uiState.update { it.copy(isLoading = false, loginSuccess = true) }

            } catch (e: Exception) {
                val message = when (e) {
                    is com.google.firebase.auth.FirebaseAuthInvalidUserException,
                    is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Usuario o contraseña incorrectos"
                    else -> "Error: ${e.message}"
                }
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * Factory para instanciar el LoginViewModel con sus dependencias.
 */
class LoginViewModelFactory(private val repository: RepositoryUsuario) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return LoginViewModel(repository) as T
    }
}
