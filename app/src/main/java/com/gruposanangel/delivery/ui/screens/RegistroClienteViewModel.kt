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

sealed class RegistroUiStatus {
    object Idle : RegistroUiStatus()
    object Loading : RegistroUiStatus()
    object Success : RegistroUiStatus()
    data class Error(val message: String) : RegistroUiStatus()
}

data class RegistroUiState(
    val status: RegistroUiStatus = RegistroUiStatus.Idle,
    val ubicacionTexto: String = "Cargando ubicación...",
    val ubicacionValida: Boolean = false,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val imageFile: File? = null,
    val imageBitmap: Bitmap? = null
)

class RegistroClienteViewModel(
    private val repository: RepositoryCliente
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistroUiState())
    val uiState: StateFlow<RegistroUiState> = _uiState.asStateFlow()

    fun onImageSelected(file: File, bitmap: Bitmap) {
        _uiState.update { it.copy(imageFile = file, imageBitmap = bitmap) }
    }

    @SuppressLint("MissingPermission")
    fun fetchInitialLocation(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Intentamos usar la ubicación que ya tiene el LocationService (instantáneo)
                val ubicacionActual = com.gruposanangel.delivery.SegundoPlano.LocationState.ultimaUbicacion.value
                
                var location: Location? = ubicacionActual

                // 2. Si no hay, intentamos una petición rápida al GPS
                if (location == null) {
                    val fused = LocationServices.getFusedLocationProviderClient(context)
                    location = withTimeoutOrNull(5000) {
                        fused.getCurrentLocation(
                            Priority.PRIORITY_HIGH_ACCURACY,
                            com.google.android.gms.tasks.CancellationTokenSource().token
                        ).await()
                    }
                }

                if (location != null) {
                    val address = getAddressFromLocation(context, location)
                    _uiState.update {
                        it.copy(
                            ubicacionTexto = address,
                            ubicacionValida = true,
                            latitud = location.latitude,
                            longitud = location.longitude
                        )
                    }
                } else {
                    // Si falla todo, notificamos pero permitimos un reintento o usamos fallback seguro
                    _uiState.update {
                        it.copy(
                            ubicacionTexto = "No se pudo obtener ubicación exacta. Reintente.",
                            ubicacionValida = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        ubicacionTexto = "Error de GPS. Verifique sus ajustes.",
                        ubicacionValida = false
                    )
                }
            }
        }
    }

    private fun getAddressFromLocation(context: Context, location: Location): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0) ?: "${location.latitude}, ${location.longitude}"
        } catch (e: Exception) {
            "${location.latitude}, ${location.longitude}"
        }
    }

    fun guardarCliente(
        context: Context,
        nombreNegocio: String,
        nombreDueno: String,
        telefono: String,
        correo: String,
        tipoExhibidor: String
    ) {
        val currentState = _uiState.value
        if (currentState.status is RegistroUiStatus.Loading) return

        viewModelScope.launch {
            _uiState.update { it.copy(status = RegistroUiStatus.Loading) }

            try {
                // 1. Validaciones
                val error = validarCampos(nombreNegocio, nombreDueno, telefono, correo, tipoExhibidor, currentState)
                if (error != null) {
                    _uiState.update { it.copy(status = RegistroUiStatus.Error(error)) }
                    return@launch
                }

                // 2. Obtener ubicación precisa final
                val finalLocation = getPreciseLocationFinal(context)
                val lat = finalLocation?.latitude ?: currentState.latitud ?: 19.4895
                val lon = finalLocation?.longitude ?: currentState.longitud ?: -96.8289

                // 3. Crear Entidad
                val clienteId = UUID.randomUUID().toString()
                val cliente = ClienteEntity(
                    id = clienteId,
                    nombreNegocio = nombreNegocio,
                    nombreDueno = nombreDueno,
                    telefono = telefono,
                    correo = correo,
                    tipoExhibidor = tipoExhibidor,
                    ubicacionLat = lat,
                    ubicacionLon = lon,
                    fotografiaUrl = currentState.imageFile?.absolutePath ?: "",
                    activo = true,
                    medio = "App Android",
                    fechaDeCreacion = System.currentTimeMillis(),
                    syncStatus = false
                )

                // 4. Guardar Localmente
                withContext(Dispatchers.IO) {
                    repository.guardarLocal(cliente)
                }

                // 5. Lanzar sincronización en background
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        repository.sincronizarConFirebase(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                _uiState.update { it.copy(status = RegistroUiStatus.Success) }

            } catch (e: Exception) {
                _uiState.update { it.copy(status = RegistroUiStatus.Error("Error: ${e.message}")) }
            }
        }
    }

    private fun validarCampos(
        nombreNegocio: String,
        nombreDueno: String,
        telefono: String,
        correo: String,
        tipoExhibidor: String,
        state: RegistroUiState
    ): String? {
        if (state.imageFile == null) return "La fotografía es obligatoria"
        if (nombreNegocio.isBlank()) return "El nombre del negocio es obligatorio"
        if (nombreDueno.isBlank()) return "El nombre del dueño es obligatorio"
        if (telefono.length < 10) return "El teléfono debe tener al menos 10 dígitos"
        if (correo.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(correo).matches()) return "Correo electrónico inválido"
        if (tipoExhibidor == "Elige una opción") return "Selecciona un tipo de exhibidor"
        if (!state.ubicacionValida) return "Ubicación no disponible"
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun getPreciseLocationFinal(context: Context): Location? = withContext(Dispatchers.IO) {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        try {
            withTimeoutOrNull(5000) {
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            }
        } catch (e: Exception) {
            null
        }
    }

    fun resetStatus() {
        _uiState.update { it.copy(status = RegistroUiStatus.Idle) }
    }

    // Helpers para imagen
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

    suspend fun processUri(context: Context, uri: android.net.Uri): Pair<File, Bitmap> = withContext(Dispatchers.IO) {
        val dest = createImageFile(context)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        val bmp = BitmapFactory.decodeFile(dest.absolutePath)
        dest to bmp
    }
}
