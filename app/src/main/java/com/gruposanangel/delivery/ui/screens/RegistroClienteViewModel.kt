package com.gruposanangel.delivery.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.gruposanangel.delivery.data.ClienteEntity
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.utilidades.GoogleServicesUtils
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
    data class Success(val clienteId: String) : RegistroUiStatus()
    data class Error(val message: String) : RegistroUiStatus()
}

data class RegistroUiState(
    val status: RegistroUiStatus = RegistroUiStatus.Idle,
    val ubicacionTexto: String = "Cargando ubicación...",
    val ubicacionValida: Boolean = false,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val imageFile: File? = null,
    val imageBitmap: Bitmap? = null,
    val isGmsAvailable: Boolean = true
)

class RegistroClienteViewModel(
    private val repository: RepositoryCliente,
    private val usuarioRepo: com.gruposanangel.delivery.RepositoryUsuario
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegistroUiState())
    val uiState: StateFlow<RegistroUiState> = _uiState.asStateFlow()

    fun onImageSelected(file: File, bitmap: Bitmap) {
        _uiState.update { it.copy(imageFile = file, imageBitmap = bitmap) }
    }

    @SuppressLint("MissingPermission")
    fun fetchInitialLocation(context: Context) {
        val gmsAvailable = GoogleServicesUtils.isGooglePlayServicesAvailable(context)
        _uiState.update { it.copy(isGmsAvailable = gmsAvailable) }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Intentamos usar la ubicación que ya tiene el LocationService (instantáneo)
                val ubicacionActual = com.gruposanangel.delivery.SegundoPlano.LocationState.ultimaUbicacion.value
                
                var location: Location? = ubicacionActual

                // 2. Si no hay, intentamos una petición rápida al GPS
                if (location == null) {
                    if (gmsAvailable) {
                        val fused = LocationServices.getFusedLocationProviderClient(context)
                        location = withTimeoutOrNull(5000) {
                            fused.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                com.google.android.gms.tasks.CancellationTokenSource().token
                            ).await()
                        }
                    } else {
                        // Fallback Nativo para Huawei/Dispositivos sin GMS
                        location = getPreciseLocationNative(context)
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

                // Formatear nombres a Title Case
                val nombreNegocioFormateado = toTitleCase(nombreNegocio)
                val nombreDuenoFormateado = toTitleCase(nombreDueno)

                // 2. Obtener ubicación precisa final
                val finalLocation = getPreciseLocationFinal(context)
                val lat = finalLocation?.latitude ?: currentState.latitud ?: 19.4895
                val lon = finalLocation?.longitude ?: currentState.longitud ?: -96.8289

                // 🔥 OBTENER RUTA DEL VENDEDOR ACTUAL
                val usuarioActual = usuarioRepo.obtenerUsuarioActual()
                val idDeRuta = usuarioActual?.ultimaRutaId ?: usuarioActual?.ultimoAlmacenNombre ?: "Ruta General"

                // 3. Crear Entidad
                val clienteId = UUID.randomUUID().toString()
                val cliente = ClienteEntity(
                    id = clienteId,
                    nombreNegocio = nombreNegocioFormateado,
                    nombreDueno = nombreDuenoFormateado,
                    telefono = telefono,
                    correo = correo,
                    tipoExhibidor = tipoExhibidor,
                    ubicacionLat = lat,
                    ubicacionLon = lon,
                    fotografiaUrl = currentState.imageFile?.absolutePath ?: "",
                    activo = true,
                    medio = "medio",
                    fechaDeCreacion = System.currentTimeMillis(),
                    syncStatus = false,
                    ownerUid = usuarioActual?.uid ?: "",
                    rutaId = idDeRuta // 🔥 SE ASIGNA LA RUTA AQUÍ
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

                _uiState.update { it.copy(status = RegistroUiStatus.Success(clienteId)) }

            } catch (e: Exception) {
                _uiState.update { it.copy(status = RegistroUiStatus.Error("Error: ${e.message}")) }
            }
        }
    }

    private fun toTitleCase(text: String): String {
        if (text.isBlank()) return ""
        val minorWords = listOf("el", "la", "los", "las", "de", "del", "y", "en", "con")
        val words = text.trim().lowercase().split("\\s+".toRegex())
        
        return words.mapIndexed { index, word ->
            // Si la palabra contiene puntos (como S.A. de C.V.), no la tocamos o la dejamos en mayúsculas
            if (word.contains(".")) {
                word.uppercase()
            } else if (index > 0 && word in minorWords) {
                word // Mantener en minúsculas si no es la primera palabra y es una palabra menor
            } else {
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }.joinToString(" ")
    }

    private fun validarCampos(
        nombreNegocio: String,
        nombreDueno: String,
        telefono: String,
        correo: String,
        tipoExhibidor: String,
        state: RegistroUiState
    ): String? {
        if (state.imageFile == null) return "La fotografía del negocio es obligatoria"
        if (nombreNegocio.isBlank()) return "El nombre del negocio es obligatorio"
        if (nombreDueno.isBlank()) return "El nombre del dueño es obligatorio"
        
        // Teléfono opcional, pero si se llena debe tener 10 dígitos
        if (telefono.isNotBlank() && telefono.length < 10) {
            return "El teléfono debe tener al menos 10 dígitos"
        }

        if (correo.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(correo).matches()) return "Correo electrónico inválido"
        if (tipoExhibidor == "Selecciona Exhibidor") return "Debes seleccionar un tipo de exhibidor"
        if (!state.ubicacionValida) return "Ubicación no disponible"
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun getPreciseLocationFinal(context: Context): Location? = withContext(Dispatchers.IO) {
        if (GoogleServicesUtils.isGooglePlayServicesAvailable(context)) {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            try {
                withTimeoutOrNull(5000) {
                    fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                }
            } catch (e: Exception) {
                null
            }
        } else {
            getPreciseLocationNative(context)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getPreciseLocationNative(context: Context): Location? = withContext(Dispatchers.Main) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        val providers = locationManager.getProviders(true)
        if (providers.isEmpty()) return@withContext null

        var bestLocation: Location? = null
        for (provider in providers) {
            val l = locationManager.getLastKnownLocation(provider)
            if (l != null && (bestLocation == null || l.accuracy < bestLocation.accuracy)) {
                bestLocation = l
            }
        }

        if (bestLocation != null && (System.currentTimeMillis() - bestLocation.time) < 60_000 && bestLocation.accuracy < 50f) {
            return@withContext bestLocation
        }

        try {
            withTimeout(10000) {
                suspendCancellableCoroutine { continuation ->
                    val locationListener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            locationManager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(location) { }
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }

                    val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        LocationManager.GPS_PROVIDER
                    } else {
                        providers[0]
                    }

                    locationManager.requestLocationUpdates(provider, 0L, 0f, locationListener)

                    continuation.invokeOnCancellation {
                        locationManager.removeUpdates(locationListener)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            bestLocation
        } catch (e: Exception) {
            bestLocation
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
