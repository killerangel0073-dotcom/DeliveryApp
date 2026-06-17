package com.gruposanangel.delivery.ui.screens

import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.content
import com.google.firebase.vertexai.type.generationConfig
import com.gruposanangel.delivery.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class UsuariosAdminUiState(
    val isLoading: Boolean = false,
    val usuarios: List<UsuarioEntity> = emptyList(),
    val rutas: List<RutaEntity> = emptyList(),
    val usuarioSeleccionado: UsuarioEntity? = null,
    val isNewUserMode: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null,
    
    val isLoadingLicencia: Boolean = false,
    val resultadoLicencia: String? = null,
    val errorLicencia: String? = null,
    
    val isLoadingIne: Boolean = false,
    val resultadoIne: String? = null,
    val errorIne: String? = null,
    
    val nombreExtraidoDeIA: String? = null,
    val showRutaConfirmation: Boolean = false,
    val rutaConfirmationMessage: String? = null,
    val rutaPendingAssignment: RutaEntity? = null,
    val isRemovingRutaPropuesta: Boolean = false,
    
    val rutaCambioPendienteId: String? = null,
    val rutaCambioPendienteNombre: String? = null
)

class UsuariosAdminViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UsuariosAdminUiState())
    val uiState: StateFlow<UsuariosAdminUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Inicializar Vertex AI para Firebase usando la forma estándar de Kotlin
    private val vertexAI = Firebase.vertexAI
    private val model = vertexAI.generativeModel(
        modelName = "gemini-2.5-flash",
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    init {
        cargarUsuarios()
    }

    fun cargarUsuarios() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val rutasSnapshot = db.collection("rutas").get().await()
                val listaRutas = rutasSnapshot.documents.map { doc ->
                    RutaEntity(
                        id = doc.id,
                        nombre = doc.getString("nombre") ?: "Sin nombre",
                        diasVisita = doc.getString("diasVisita") ?: "",
                        frecuencia = doc.getString("frecuencia") ?: "Semanal",
                        activo = doc.getBoolean("activo") ?: true
                    )
                }

                val snapshot = db.collection("users").get().await()
                val lista = snapshot.documents.map { doc ->
                    val rutaRef = doc.get("rutaAsignada") as? DocumentReference
                    val rId = rutaRef?.id
                    val rNombre = listaRutas.find { it.id == rId }?.nombre ?: (if (rId != null) "Ruta Desconocida" else "Sin Ruta")
                    
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
                        ineUltimaRevision = doc.getLong("ineUltimaRevision"),
                        ultimaRutaId = rId,
                        ultimaRutaNombre = rNombre
                    )
                }
                _uiState.update { it.copy(usuarios = lista, rutas = listaRutas, isLoading = false) }
                
                _uiState.value.usuarioSeleccionado?.let { current ->
                    val updated = lista.find { it.uid == current.uid }
                    if (updated != null) {
                        _uiState.update { it.copy(usuarioSeleccionado = updated) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun seleccionarUsuario(usuario: UsuarioEntity?) {
        _uiState.update { it.copy(
            usuarioSeleccionado = usuario, 
            isNewUserMode = usuario == null,
            resultadoLicencia = null,
            resultadoIne = null,
            errorLicencia = null,
            errorIne = null,
            rutaCambioPendienteId = null,
            rutaCambioPendienteNombre = null
        ) }
    }

    fun proponerRuta(ruta: RutaEntity?) {
        val user = _uiState.value.usuarioSeleccionado ?: return
        val currentRutaId = _uiState.value.rutaCambioPendienteId ?: user.ultimaRutaId
        if (currentRutaId == ruta?.id) return
        if (ruta == null && currentRutaId == "NONE") return

        val body = if (ruta == null) {
            "¿Seguro que deseas quitar la ruta actual (${_uiState.value.rutaCambioPendienteNombre ?: user.ultimaRutaNombre}) a ${user.nombre}?"
        } else {
            var b = "¿Deseas asignar la ruta '${ruta.nombre}' a ${user.nombre}?"
            if (currentRutaId != null && currentRutaId != "NONE") {
                val nombreActual = _uiState.value.rutaCambioPendienteNombre ?: user.ultimaRutaNombre
                b += "\n\n⚠️ Se le quitará su ruta actual ($nombreActual)."
            }
            val otroUsuario = _uiState.value.usuarios.find { it.ultimaRutaId == ruta.id && it.uid != user.uid }
            if (otroUsuario != null) {
                b += "\n\n⚠️ ADVERTENCIA: La ruta ya está asignada a ${otroUsuario.nombre}. Se le dará de baja de esta ruta y se asignará a ${user.nombre}."
            }
            b
        }

        _uiState.update { it.copy(
            showRutaConfirmation = true,
            rutaConfirmationMessage = body,
            rutaPendingAssignment = ruta,
            isRemovingRutaPropuesta = (ruta == null)
        ) }
    }

    fun confirmarPropuestaRuta() {
        val propuesta = _uiState.value.rutaPendingAssignment
        val isRemoving = _uiState.value.isRemovingRutaPropuesta
        _uiState.update { it.copy(
            showRutaConfirmation = false,
            rutaPendingAssignment = null,
            isRemovingRutaPropuesta = false,
            rutaCambioPendienteId = if (isRemoving) "NONE" else propuesta?.id,
            rutaCambioPendienteNombre = if (isRemoving) "Sin ruta asignada" else propuesta?.nombre
        ) }
    }

    fun cancelarConfirmacionRuta() {
        _uiState.update { it.copy(showRutaConfirmation = false, rutaPendingAssignment = null, rutaConfirmationMessage = null, isRemovingRutaPropuesta = false) }
    }

    fun validarINEConIA(imageFile: File, userUid: String, userNombre: String) {
        _uiState.update { it.copy(isLoadingIne = true, errorIne = null, resultadoIne = "Analizando INE...", successMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                val prompt = "Analiza este INE de México. Responde SOLO con un JSON puro: {\"es_ine\":true/false,\"nombre_completo\":\"Nombre Apellidos\",\"fecha_vencimiento\":\"dd/mm/aaaa\",\"calidad_lectura\":\"ALTA/BAJA\"}. Asegúrate de que 'nombre_completo' esté en formato 'Nombre Apellidos' (no apellidos primero)."

                val response = model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )

                val responseText = response.text ?: throw Exception("Sin respuesta de IA")
                val cleanJson = responseText.substring(responseText.indexOf("{"), responseText.lastIndexOf("}") + 1)
                val finalData = JSONObject(cleanJson)
                
                if (!finalData.optBoolean("es_ine", false)) throw Exception("No es un INE válido")

                val nombreCompleto = finalData.optString("nombre_completo", "Nombre No Detectado")
                val fechaVencStr = finalData.optString("fecha_vencimiento", "")
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val fechaVenc = try { sdf.parse(fechaVencStr)?.time } catch (_: Exception) { null }
                val estaVigente = if (fechaVenc != null) fechaVenc > System.currentTimeMillis() else false
                
                val nombreCoincide = if (userNombre != "N") {
                    nombreCompleto.lowercase().contains(userNombre.split(" ")[0].lowercase())
                } else true

                val estadoFinal = when {
                    !estaVigente -> "VENCIDA"
                    !nombreCoincide -> "RECHAZADA (No es $userNombre)"
                    else -> "VIGENTE"
                }

                if (userUid != "new_user") {
                    val storageRef = storage.reference.child("ines/${userUid}_${System.currentTimeMillis()}.jpg")
                    val downloadUrl = storageRef.putFile(android.net.Uri.fromFile(imageFile)).await().metadata?.reference?.downloadUrl?.await().toString()
                    
                    db.collection("users").document(userUid).update(mapOf(
                        "ineFotoUrl" to downloadUrl,
                        "ineVencimiento" to (fechaVenc ?: 0L),
                        "ineEstado" to estadoFinal,
                        "ineUltimaRevision" to System.currentTimeMillis()
                    )).await()
                }
                
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(
                        isLoadingIne = false, 
                        successMessage = "INE Analizado", 
                        resultadoIne = estadoFinal,
                        nombreExtraidoDeIA = nombreCompleto
                    ) }
                    if (userUid != "new_user") cargarUsuarios()
                }
            } catch (e: Exception) {
                Log.e("VERTEX_IA", "Error INE", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingIne = false, errorIne = e.localizedMessage, resultadoIne = "Error") }
                }
            }
        }
    }

    fun validarLicenciaConIA(imageFile: File, userUid: String, userNombre: String) {
        _uiState.update { it.copy(isLoadingLicencia = true, errorLicencia = null, resultadoLicencia = "Analizando Licencia...", successMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                val prompt = "Analiza esta Licencia de Conducir Mexicana. Responde SOLO con un JSON puro: {\"es_licencia\":true/false,\"nombre_encontrado\":\"String\",\"fecha_vencimiento\":\"dd/mm/aaaa\",\"calidad_lectura\":\"ALTA/BAJA\"}"

                val response = model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )

                val responseText = response.text ?: throw Exception("Sin respuesta de IA")
                val cleanJson = responseText.substring(responseText.indexOf("{"), responseText.lastIndexOf("}") + 1)
                val finalData = JSONObject(cleanJson)
                
                if (!finalData.optBoolean("es_licencia", false)) throw Exception("No es una licencia válida")

                val fechaVencStr = finalData.optString("fecha_vencimiento", "")
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val fechaVenc = try { sdf.parse(fechaVencStr)?.time } catch (_: Exception) { null }
                val estaVigente = if (fechaVenc != null) fechaVenc > System.currentTimeMillis() else false
                
                val nombreDetectado = finalData.optString("nombre_encontrado", "")
                val nombreCoincide = if (userNombre != "N") {
                    nombreDetectado.lowercase().contains(userNombre.split(" ")[0].lowercase())
                } else true

                val estadoFinal = when {
                    !estaVigente -> "VENCIDA"
                    !nombreCoincide -> "RECHAZADA (No es $userNombre)"
                    else -> "VIGENTE"
                }

                if (userUid != "new_user") {
                    val storageRef = storage.reference.child("licencias/${userUid}_${System.currentTimeMillis()}.jpg")
                    val downloadUrl = storageRef.putFile(android.net.Uri.fromFile(imageFile)).await().metadata?.reference?.downloadUrl?.await().toString()
                    
                    db.collection("users").document(userUid).update(mapOf(
                        "licenciaFotoUrl" to downloadUrl,
                        "licenciaVencimiento" to (fechaVenc ?: 0L),
                        "licenciaEstado" to estadoFinal,
                        "licenciaUltimaRevision" to System.currentTimeMillis()
                    )).await()
                }
                
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingLicencia = false, successMessage = "Licencia Validada", resultadoLicencia = estadoFinal) }
                    if (userUid != "new_user") cargarUsuarios()
                }
            } catch (e: Exception) {
                Log.e("VERTEX_IA", "Error Licencia", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingLicencia = false, errorLicencia = e.localizedMessage, resultadoLicencia = "Error") }
                }
            }
        }
    }

    fun clearNombreExtraido() {
        _uiState.update { it.copy(nombreExtraidoDeIA = null) }
    }

    fun guardarUsuario(nombre: String, email: String, puesto: String, activo: Boolean, licencia: String, credencial: String, imageFile: File?, password: String = "") {
        val state = _uiState.value
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                var targetUid = ""
                if (state.isNewUserMode) {
                    if (email.isEmpty() || password.length < 6) {
                        throw Exception("El correo es obligatorio y la contraseña debe tener al menos 6 caracteres.")
                    }
                    val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                    targetUid = authResult.user?.uid ?: throw Exception("Error al crear usuario en Auth")
                } else {
                    targetUid = state.usuarioSeleccionado?.uid ?: ""
                }

                val batch = db.batch()
                var photoUrl: String? = null
                if (imageFile != null) {
                    val ref = storage.reference.child("users/${UUID.randomUUID()}.jpg")
                    ref.putFile(android.net.Uri.fromFile(imageFile)).await()
                    photoUrl = ref.downloadUrl.await().toString()
                }

                val data = mutableMapOf<String, Any>(
                    "uid" to targetUid,
                    "nombre" to nombre, 
                    "email" to email, 
                    "puestoTrabajo" to puesto, 
                    "activo" to activo, 
                    "licenciaConducir" to licencia, 
                    "credencialElector" to credencial,
                    "created_time" to com.google.firebase.Timestamp.now()
                )
                photoUrl?.let { data["photo_url"] = it }
                
                val userRef = db.collection("users").document(targetUid)
                if (state.isNewUserMode) {
                    batch.set(userRef, data, com.google.firebase.firestore.SetOptions.merge())
                } else {
                    batch.update(userRef, data)
                }

                val cambioRutaId = state.rutaCambioPendienteId
                if (cambioRutaId != null && targetUid.isNotEmpty()) {
                    if (cambioRutaId == "NONE") {
                        val rutaAnteriorId = state.usuarioSeleccionado?.ultimaRutaId
                        batch.update(userRef, "rutaAsignada", null)
                        if (rutaAnteriorId != null) {
                            val rutaRef = db.collection("rutas").document(rutaAnteriorId)
                            batch.update(rutaRef, "vendedorAsignado", null)
                        }
                    } else {
                        val nuevaRutaRef = db.collection("rutas").document(cambioRutaId)
                        if (state.usuarioSeleccionado?.ultimaRutaId != null) {
                            val rutaAnteriorRef = db.collection("rutas").document(state.usuarioSeleccionado.ultimaRutaId)
                            batch.update(rutaAnteriorRef, "vendedorAsignado", null)
                        }
                        val otroUsuario = state.usuarios.find { it.ultimaRutaId == cambioRutaId && it.uid != targetUid }
                        if (otroUsuario != null) {
                            val otroUserRef = db.collection("users").document(otroUsuario.uid)
                            batch.update(otroUserRef, "rutaAsignada", null)
                        }
                        batch.update(userRef, "rutaAsignada", nuevaRutaRef)
                        batch.update(nuevaRutaRef, "vendedorAsignado", userRef)
                    }
                }

                batch.commit().await()
                _uiState.update { it.copy(
                    successMessage = if (state.isNewUserMode) "Usuario creado y autenticado con éxito" else "Actualizado correctamente",
                    rutaCambioPendienteId = null,
                    rutaCambioPendienteNombre = null
                ) }
                cargarUsuarios()
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
