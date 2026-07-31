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
    
    val puestosDisponibles: List<String> = listOf(
        "CEO", "Gerente General", "Supervisor", 
        "Vendedor de Ruta", "Suplente de Ruta",
        "Encargado Almacen", "Auxiliar de almacen",
        "Encargado Produccion", "Auxiliar de Produccion"
    ),
    
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
    val rutaCambioPendienteNombre: String? = null,

    // 🔥 CONFIGURACIÓN DE PERFILES DE VENTA
    val perfilesVentaEdit: List<PerfilVenta> = emptyList(),
    val marcasDisponibles: List<String> = emptyList(),
    val categoriasDisponibles: Map<String, List<String>> = emptyMap()
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
        cargarConfiguracionBase()
        cargarUsuarios()
    }

    private fun cargarConfiguracionBase() {
        viewModelScope.launch {
            try {
                // 1. Cargar Marcas
                val marcasSnap = db.collection("marca").get().await()
                val listaMarcas = marcasSnap.documents.mapNotNull { it.getString("id") ?: it.getString("nombre") }.distinct()

                // 2. Cargar Categorías
                val catsSnap = db.collection("Categorias").get().await()
                val mapaCategorias = mutableMapOf<String, MutableList<String>>()
                
                catsSnap.documents.forEach { doc ->
                    val marcaId = doc.getString("marca_id") ?: "Delisa"
                    val catNombre = doc.getString("nombre") ?: doc.getString("id") ?: ""
                    if (catNombre.isNotEmpty()) {
                        mapaCategorias.getOrPut(marcaId) { mutableListOf() }.add(catNombre)
                    }
                }

                _uiState.update { it.copy(
                    marcasDisponibles = listaMarcas,
                    categoriasDisponibles = mapaCategorias
                ) }
            } catch (e: Exception) {
                Log.e("UsuariosVM", "Error cargando config base", e)
            }
        }
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
                        status = doc.getString("status") ?: "ACTIVO",
                        fechaBaja = doc.getTimestamp("fechaBaja")?.toDate()?.time,
                        motivoBaja = doc.getString("motivoBaja"),
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
                        ultimaRutaNombre = rNombre,
                        perfilesVentaJson = if (doc.get("perfilesVenta") != null) {
                            val raw = doc.get("perfilesVenta") as? List<*>
                            org.json.JSONArray(raw).toString()
                        } else null
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
        val perfiles = try {
            val json = usuario?.perfilesVentaJson
            if (!json.isNullOrBlank()) {
                val array = org.json.JSONArray(json)
                (0 until array.length()).map { i ->
                    val obj = array.getJSONObject(i)
                    val filtrosArr = obj.getJSONArray("filtros")
                    val filtros = (0 until filtrosArr.length()).map { j ->
                        val fObj = filtrosArr.getJSONObject(j)
                        val catsArr = fObj.optJSONArray("categorias")
                        val cats = if (catsArr != null) {
                            (0 until catsArr.length()).map { k -> catsArr.getString(k) }
                        } else emptyList<String>()
                        FiltroPerfil(fObj.getString("marca"), cats)
                    }
                    PerfilVenta(obj.getString("id"), obj.getString("nombre"), filtros)
                }
            } else emptyList()
        } catch (e: Exception) {
            Log.e("UsuariosVM", "Error parseando perfiles", e)
            emptyList()
        }

        _uiState.update { it.copy(
            usuarioSeleccionado = usuario, 
            isNewUserMode = usuario == null,
            resultadoLicencia = null,
            resultadoIne = null,
            errorLicencia = null,
            errorIne = null,
            rutaCambioPendienteId = null,
            rutaCambioPendienteNombre = null,
            perfilesVentaEdit = perfiles
        ) }
    }

    fun agregarPerfil(perfil: PerfilVenta) {
        val current = _uiState.value.perfilesVentaEdit.toMutableList()
        current.add(perfil.copy(id = UUID.randomUUID().toString()))
        _uiState.update { it.copy(perfilesVentaEdit = current) }
    }

    fun eliminarPerfil(perfilId: String) {
        val current = _uiState.value.perfilesVentaEdit.filter { it.id != perfilId }
        _uiState.update { it.copy(perfilesVentaEdit = current) }
    }

    fun actualizarPerfil(perfil: PerfilVenta) {
        val current = _uiState.value.perfilesVentaEdit.map { 
            if (it.id == perfil.id) perfil else it
        }
        _uiState.update { it.copy(perfilesVentaEdit = current) }
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
                    "status" to if (activo) "ACTIVO" else "SUSPENDIDO",
                    "licenciaConducir" to licencia, 
                    "credencialElector" to credencial,
                    "created_time" to com.google.firebase.Timestamp.now(),
                    "perfilesVenta" to state.perfilesVentaEdit.map { perfil ->
                        mapOf(
                            "id" to perfil.id,
                            "nombre" to perfil.nombre,
                            "filtros" to perfil.filtros.map { filtro ->
                                mapOf(
                                    "marca" to filtro.marca,
                                    "categorias" to filtro.categorias
                                )
                            }
                        )
                    }
                )
                photoUrl?.let { data["photo_url"] = it }
                
                val userRef = db.collection("users").document(targetUid)
                
                // --- LÓGICA DE LIBERACIÓN DE RUTA SI CAMBIA DE PUESTO (Vendedor -> Otro) ---
                val snapshotActual = if (!state.isNewUserMode) userRef.get().await() else null
                val puestoAnterior = snapshotActual?.getString("puestoTrabajo")
                val rutaReferencia = snapshotActual?.get("rutaAsignada") as? DocumentReference
                
                val esVendedorAnterior = puestoAnterior?.contains("Vendedor") == true || puestoAnterior?.contains("Suplente") == true
                val esVendedorNuevo = puesto.contains("Vendedor") || puesto.contains("Suplente")
                
                if (esVendedorAnterior && !esVendedorNuevo && rutaReferencia != null) {
                    // El usuario dejó de ser vendedor, liberamos la ruta
                    batch.update(userRef, "rutaAsignada", null)
                    batch.update(rutaReferencia, "vendedorAsignado", null)
                }

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

    fun eliminarUsuario(uid: String, motivo: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val userRef = db.collection("users").document(uid)
                val userSnap = userRef.get().await()
                val rutaRef = userSnap.get("rutaAsignada") as? DocumentReference

                val batch = db.batch()

                // 1. Preparar actualizaciones del usuario
                val userUpdates = mutableMapOf<String, Any?>(
                    "activo" to false,
                    "status" to "BAJA",
                    "fechaBaja" to com.google.firebase.Timestamp.now(),
                    "motivoBaja" to motivo
                )

                // 2. Si tiene ruta, liberarla en ambos documentos
                if (rutaRef != null) {
                    userUpdates["rutaAsignada"] = null
                    batch.update(rutaRef, "vendedorAsignado", null)
                }

                batch.update(userRef, userUpdates)
                batch.commit().await()

                cargarUsuarios()
                seleccionarUsuario(null)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearMessages() { _uiState.update { it.copy(error = null, successMessage = null) } }
}
