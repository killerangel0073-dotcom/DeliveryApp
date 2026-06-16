package com.gruposanangel.delivery.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.gruposanangel.delivery.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class UsuariosAdminUiState(
    val isLoading: Boolean = false,
    val usuarios: List<UsuarioEntity> = emptyList(),
    val usuarioSeleccionado: UsuarioEntity? = null,
    val isNewUserMode: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null,
    val isLoadingIA: Boolean = false,
    val errorIA: String? = null,
    val resultadoIA: String? = null
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
                        credencialElector = doc.getString("credencialElector"),
                        licenciaFotoUrl = doc.getString("licenciaFotoUrl"),
                        licenciaVencimiento = doc.getLong("licenciaVencimiento"),
                        licenciaEstado = doc.getString("licenciaEstado") ?: "PENDIENTE",
                        licenciaUltimaRevision = doc.getLong("licenciaUltimaRevision"),
                        ineFotoUrl = doc.getString("ineFotoUrl"),
                        ineVencimiento = doc.getLong("ineVencimiento"),
                        ineEstado = doc.getString("ineEstado") ?: "PENDIENTE",
                        ineUltimaRevision = doc.getLong("ineUltimaRevision")
                    )
                }
                _uiState.update { it.copy(usuarios = lista, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun seleccionarUsuario(usuario: UsuarioEntity?) {
        _uiState.update { it.copy(
            usuarioSeleccionado = usuario, 
            isNewUserMode = usuario == null,
            errorIA = null,
            resultadoIA = null
        ) }
    }

    fun validarINEConIA(imageFile: File, userUid: String, userNombre: String) {
        _uiState.update { it.copy(isLoadingIA = true, errorIA = null, resultadoIA = "Analizando INE...", successMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(1500)
                val base64Image = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
                val apiKey = "AIzaSyDfp0E9eCAIZS98fUBgK3OvqgGiHhst7k8"
                val url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$apiKey"
                
                val jsonRequest = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", "Analiza este INE de México. Responde SOLO con un JSON puro: {\"es_ine\":true/false,\"nombre_encontrado\":\"String\",\"fecha_vencimiento\":\"dd/mm/aaaa\",\"calidad_lectura\":\"ALTA/BAJA\"}")
                        }).put(JSONObject().apply {
                            put("inline_data", JSONObject().apply { put("mime_type", "image/jpeg"); put("data", base64Image) })
                        }))
                    }))
                }

                val client = OkHttpClient()
                val request = Request.Builder().url(url).post(jsonRequest.toString().toRequestBody("application/json".toMediaType())).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (!response.isSuccessful) throw Exception("Error plataforma")

                val responseText = JSONObject(responseBody).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                val finalData = JSONObject(responseText.substring(responseText.indexOf("{"), responseText.lastIndexOf("}") + 1))
                
                if (!finalData.optBoolean("es_ine", false)) throw Exception("No es un INE válido")

                val storageRef = storage.reference.child("ines/${userUid}_${System.currentTimeMillis()}.jpg")
                val downloadUrl = storageRef.putFile(android.net.Uri.fromFile(imageFile)).await().metadata?.reference?.downloadUrl?.await().toString()
                
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val fechaVenc = try { sdf.parse(finalData.getString("fecha_vencimiento"))?.time } catch (_: Exception) { null }
                val estaVigente = if (fechaVenc != null) fechaVenc > System.currentTimeMillis() else false
                val nombreCoincide = finalData.getString("nombre_encontrado").lowercase().contains(userNombre.split(" ")[0].lowercase())

                val estadoFinal = when {
                    !estaVigente -> "VENCIDA"
                    !nombreCoincide -> "RECHAZADA (No es $userNombre)"
                    else -> "VIGENTE"
                }

                db.collection("users").document(userUid).update(mapOf(
                    "ineFotoUrl" to downloadUrl,
                    "ineVencimiento" to (fechaVenc ?: 0L),
                    "ineEstado" to estadoFinal,
                    "ineUltimaRevision" to System.currentTimeMillis()
                )).await()
                
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingIA = false, successMessage = "INE Validado", resultadoIA = estadoFinal) }
                    cargarUsuarios()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingIA = false, errorIA = e.localizedMessage, resultadoIA = "Error") }
                }
            }
        }
    }

    fun validarLicenciaConIA(imageFile: File, userUid: String, userNombre: String) {
        _uiState.update { it.copy(isLoadingIA = true, errorIA = null, resultadoIA = "Analizando Licencia...", successMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(1500)
                val base64Image = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
                val apiKey = "AIzaSyDfp0E9eCAIZS98fUBgK3OvqgGiHhst7k8"
                val url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$apiKey"
                
                val jsonRequest = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", "Analiza esta Licencia de Conducir Mexicana. Responde SOLO con un JSON puro: {\"es_licencia\":true/false,\"nombre_encontrado\":\"String\",\"fecha_vencimiento\":\"dd/mm/aaaa\",\"calidad_lectura\":\"ALTA/BAJA\"}")
                        }).put(JSONObject().apply {
                            put("inline_data", JSONObject().apply { put("mime_type", "image/jpeg"); put("data", base64Image) })
                        }))
                    }))
                }

                val client = OkHttpClient()
                val request = Request.Builder().url(url).post(jsonRequest.toString().toRequestBody("application/json".toMediaType())).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                if (!response.isSuccessful) throw Exception("Error plataforma")

                val responseText = JSONObject(responseBody).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                val finalData = JSONObject(responseText.substring(responseText.indexOf("{"), responseText.lastIndexOf("}") + 1))
                
                if (!finalData.optBoolean("es_licencia", false)) throw Exception("No es una licencia válida")

                val storageRef = storage.reference.child("licencias/${userUid}_${System.currentTimeMillis()}.jpg")
                val downloadUrl = storageRef.putFile(android.net.Uri.fromFile(imageFile)).await().metadata?.reference?.downloadUrl?.await().toString()
                
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val fechaVenc = try { sdf.parse(finalData.getString("fecha_vencimiento"))?.time } catch (_: Exception) { null }
                val estaVigente = if (fechaVenc != null) fechaVenc > System.currentTimeMillis() else false
                val nombreCoincide = finalData.getString("nombre_encontrado").lowercase().contains(userNombre.split(" ")[0].lowercase())

                val estadoFinal = when {
                    !estaVigente -> "VENCIDA"
                    !nombreCoincide -> "RECHAZADA (No es $userNombre)"
                    else -> "VIGENTE"
                }

                db.collection("users").document(userUid).update(mapOf(
                    "licenciaFotoUrl" to downloadUrl,
                    "licenciaVencimiento" to (fechaVenc ?: 0L),
                    "licenciaEstado" to estadoFinal,
                    "licenciaUltimaRevision" to System.currentTimeMillis()
                )).await()
                
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingIA = false, successMessage = "Licencia Validada", resultadoIA = estadoFinal) }
                    cargarUsuarios()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingIA = false, errorIA = e.localizedMessage, resultadoIA = "Error") }
                }
            }
        }
    }

    fun guardarUsuario(nombre: String, email: String, puesto: String, activo: Boolean, licencia: String, credencial: String, imageFile: File?) {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                var photoUrl: String? = null
                if (imageFile != null) {
                    val ref = storage.reference.child("users/${UUID.randomUUID()}.jpg")
                    ref.putFile(android.net.Uri.fromFile(imageFile)).await()
                    photoUrl = ref.downloadUrl.await().toString()
                }
                val data = mutableMapOf<String, Any>("nombre" to nombre, "email" to email, "puestoTrabajo" to puesto, "activo" to activo, "licenciaConducir" to licencia, "credencialElector" to credencial)
                photoUrl?.let { data["photo_url"] = it }
                
                if (state.isNewUserMode) {
                    val docRef = db.collection("users").add(data).await()
                    val newUid = docRef.id
                    _uiState.update { it.copy(successMessage = "Usuario creado con éxito") }
                    cargarUsuarios()
                    val newEntity = UsuarioEntity(uid = newUid, nombre = nombre, email = email, puestoTrabajo = puesto, activo = activo, licenciaConducir = licencia, credencialElector = credencial)
                    seleccionarUsuario(newEntity)
                } else {
                    state.usuarioSeleccionado?.uid?.let { 
                        db.collection("users").document(it).update(data).await() 
                        _uiState.update { it.copy(successMessage = "Actualizado correctamente") }
                    }
                    cargarUsuarios()
                }
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
                cargarUsuarios()
                seleccionarUsuario(null)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearMessages() { _uiState.update { it.copy(error = null, successMessage = null) } }
}
