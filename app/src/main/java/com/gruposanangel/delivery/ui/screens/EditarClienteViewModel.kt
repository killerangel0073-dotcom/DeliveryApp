package com.gruposanangel.delivery.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.data.ClienteEntity
import com.gruposanangel.delivery.data.RepositoryCliente
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.*

data class EditarClienteUiState(
    val status: RegistroUiStatus = RegistroUiStatus.Idle,
    val ubicacionTexto: String = "Cargando ubicación...",
    val ubicacionValida: Boolean = false,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val imageFile: File? = null,
    val imageBitmap: Bitmap? = null,
    val isLoadingData: Boolean = true,
    val cliente: ClienteEntity? = null
)

class EditarClienteViewModel(
    private val repository: RepositoryCliente,
    private val clienteId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditarClienteUiState())
    val uiState: StateFlow<EditarClienteUiState> = _uiState.asStateFlow()

    init {
        cargarDatosCliente()
    }

    private fun cargarDatosCliente() {
        viewModelScope.launch {
            val cliente = repository.obtenerClientesLocalPorId(clienteId)
            if (cliente != null) {
                var bitmap: Bitmap? = null
                var file: File? = null
                
                if (!cliente.fotografiaUrl.isNullOrBlank()) {
                    file = File(cliente.fotografiaUrl)
                    if (file.exists()) {
                        bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    }
                }

                _uiState.update { it.copy(
                    cliente = cliente,
                    isLoadingData = false,
                    latitud = cliente.ubicacionLat,
                    longitud = cliente.ubicacionLon,
                    ubicacionTexto = "${cliente.ubicacionLat}, ${cliente.ubicacionLon}",
                    ubicacionValida = true,
                    imageFile = file,
                    imageBitmap = bitmap
                ) }
            } else {
                _uiState.update { it.copy(isLoadingData = false, status = RegistroUiStatus.Error("Cliente no encontrado")) }
            }
        }
    }

    fun onImageSelected(file: File, bitmap: Bitmap) {
        _uiState.update { it.copy(imageFile = file, imageBitmap = bitmap) }
    }

    fun guardarCambios(
        context: Context,
        nombreNegocio: String,
        nombreDueno: String,
        telefono: String,
        correo: String,
        tipoExhibidor: String
    ) {
        val currentState = _uiState.value
        val original = currentState.cliente ?: return
        if (currentState.status is RegistroUiStatus.Loading) return

        viewModelScope.launch {
            _uiState.update { it.copy(status = RegistroUiStatus.Loading) }

            try {
                // 1. Validaciones
                if (nombreNegocio.isBlank()) {
                    _uiState.update { it.copy(status = RegistroUiStatus.Error("El nombre del negocio es obligatorio")) }
                    return@launch
                }

                // 2. Crear Entidad Actualizada
                val clienteActualizado = original.copy(
                    nombreNegocio = nombreNegocio,
                    nombreDueno = nombreDueno,
                    telefono = telefono,
                    correo = correo,
                    tipoExhibidor = tipoExhibidor,
                    fotografiaUrl = currentState.imageFile?.absolutePath ?: original.fotografiaUrl,
                    syncStatus = false,
                    lastModified = System.currentTimeMillis()
                )

                // 3. Guardar Localmente
                withContext(Dispatchers.IO) {
                    repository.guardarLocal(clienteActualizado)
                }

                // 4. Sincronizar
                viewModelScope.launch(Dispatchers.IO) {
                    repository.sincronizarConFirebase(context)
                }

                _uiState.update { it.copy(status = RegistroUiStatus.Success) }

            } catch (e: Exception) {
                _uiState.update { it.copy(status = RegistroUiStatus.Error("Error: ${e.message}")) }
            }
        }
    }

    fun eliminarCliente(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(status = RegistroUiStatus.Loading) }
            try {
                // 1. Eliminar local
                withContext(Dispatchers.IO) {
                    repository.eliminarLocal(clienteId)
                }
                
                // 2. Eliminar remoto
                withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance().collection("clientes").document(clienteId).delete().await()
                }

                _uiState.update { it.copy(status = RegistroUiStatus.Success) }
            } catch (e: Exception) {
                _uiState.update { it.copy(status = RegistroUiStatus.Error("Error al eliminar: ${e.message}")) }
            }
        }
    }

    // Helpers para imagen (reutilizados)
    fun createImageFile(context: Context): File {
        val photosDir = File(context.filesDir, "clientes_photos")
        if (!photosDir.exists()) photosDir.mkdirs()
        return File(photosDir, "cliente_${System.currentTimeMillis()}.jpg")
    }

    fun saveBitmap(bitmap: Bitmap, file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        }
    }
}
