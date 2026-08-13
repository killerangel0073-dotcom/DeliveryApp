package com.gruposanangel.delivery.data

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Date

class RepositoryCliente(private val dao: ClienteDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var listenerRegistration: ListenerRegistration? = null
    private val TAG = "ClienteRepository"

    // Job para controlar las corrutinas del SnapshotListener y evitar fugas o saturación


    // ============================================================
    // 🔥 1. Guardar local
    // ============================================================
    suspend fun guardarLocal(cliente: ClienteEntity) {
        try {
            dao.insert(cliente)
            Log.d(TAG, "guardarLocal: cliente insertado localmente id=${cliente.id}")
        } catch (e: Exception) {
            Log.e(TAG, "guardarLocal: error al insertar cliente localmente", e)
            throw e
        }
    }


    suspend fun eliminarLocal(id: String) {
        dao.deleteById(id)
    }

    suspend fun obtenerClientesLocalPorId(id: String): ClienteEntity? {
        return dao.getClientePorId(id)
    }

    /**
     * 🔥 Descarga de clientes filtrada por Ruta para Vendedores o masiva para Admin.
     */
    suspend fun descargarClientesFirebase(context: Context) {
        try {
            val usuarioActual = auth.currentUser?.uid ?: return
            val userDoc = firestore.collection("users").document(usuarioActual).get().await()
            val puesto = userDoc.getString("puestoTrabajo") ?: ""
            val esAdmin = puesto in listOf("CEO", "Gerente General")
            
            // Obtener nombre de la ruta asignada
            val rutaRef = userDoc.getDocumentReference("rutaAsignada")
            val nombreRuta = if (rutaRef != null) {
                rutaRef.get().await().getString("nombre")
            } else {
                userDoc.getString("ultimoAlmacenNombre")
            }

            Log.d(TAG, "Iniciando descarga de clientes. Rol: $puesto. Ruta: $nombreRuta")

            // 1. Petición Filtrada
            var query = firestore.collection("clientes") as com.google.firebase.firestore.Query
            
            if (!esAdmin && !nombreRuta.isNullOrEmpty()) {
                // Si es vendedor, solo bajar su ruta
                query = query.whereEqualTo("rutaId", nombreRuta)
            }

            val snapshot = query.get().await()

            if (snapshot.isEmpty) {
                Log.d(TAG, "No hay clientes en Firestore para esta ruta.")
                return
            }

            // 2. Obtener mapa de clientes locales
            val localMap = dao.getAllClientes().associateBy { it.id }

            // 3. Procesar documentos
            val clientesParaGuardar = coroutineScope {
                snapshot.documents.map { doc ->
                    async {
                        val id = doc.id
                        val remoteLastModified = doc.getLong("lastModified") ?: 0L
                        val local = localMap[id]

                        if (local != null) {
                            if (!local.syncStatus) return@async null 
                            if (remoteLastModified <= local.lastModified) return@async null
                        }

                        ClienteEntity(
                            id = id,
                            nombreNegocio = doc.getString("nombreNegocio") ?: "",
                            nombreDueno = doc.getString("nombreDueno") ?: "",
                            telefono = doc.getString("telefono") ?: "",
                            correo = doc.getString("correo") ?: "",
                            tipoExhibidor = doc.getString("tipoExhibidor") ?: "",
                            ubicacionLat = doc.getGeoPoint("ubicacion")?.latitude ?: 0.0,
                            ubicacionLon = doc.getGeoPoint("ubicacion")?.longitude ?: 0.0,
                            fotografiaUrl = doc.getString("FotografiaCliente"),
                            activo = doc.getBoolean("activo") ?: true,
                            valorCliente = doc.getString("valorCliente") ?: "",
                            fechaDeCreacion = doc.getTimestamp("fechaDeCreacion")?.toDate()?.time ?: System.currentTimeMillis(),
                            syncStatus = true,
                            ownerUid = doc.getString("ownerUid") ?: "",
                            lastModified = remoteLastModified,
                            rutaId = when(val r = doc.get("rutaId")) {
                                is String -> r
                                is com.google.firebase.firestore.DocumentReference -> r.id
                                else -> null
                            }
                        )
                    }
                }.awaitAll().filterNotNull()
            }

            if (clientesParaGuardar.isNotEmpty()) {
                dao.insertAll(clientesParaGuardar)
                Log.d(TAG, "Sync exitosa: ${clientesParaGuardar.size} clientes guardados.")
                
                // 🔥 DESCARGA DE FOTOS EN SEGUNDO PLANO (Quirúrgico)
                CoroutineScope(Dispatchers.IO).launch {
                    procesarDescargaFotos(context, clientesParaGuardar)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en descarga de clientes", e)
        }
    }

    private suspend fun procesarDescargaFotos(context: Context, clientes: List<ClienteEntity>) {
        clientes.forEach { cliente ->
            val url = cliente.fotografiaUrl
            if (!url.isNullOrBlank() && (url.startsWith("http") || url.startsWith("gs://"))) {
                val localPath = descargarFotoCliente(url, cliente.id, context)
                if (localPath != null) {
                    dao.actualizarFotoLocal(cliente.id, localPath)
                    Log.d(TAG, "Foto actualizada a local para cliente: ${cliente.id}")
                }
            }
        }
    }


    // ============================================================
    // 🔥 2. Sincronizar con Firebase (sube foto y datos)
    // ============================================================
    suspend fun sincronizarConFirebase(context: Context): Boolean {
        val pendientes = try {
            dao.getClientesPendientes()
        } catch (e: Exception) {
            Log.e(TAG, "sincronizarConFirebase: error obteniendo pendientes", e)
            return false
        }

        if (pendientes.isEmpty()) return true

        val uid = auth.currentUser?.uid ?: "desconocido"
        var todosSincronizados = true

        pendientes.forEach { cliente ->
            val timeToSync = cliente.lastModified // Usamos el tiempo que ya tiene el objeto

            try {
                Log.d(TAG, "Sincronizando cliente: ${cliente.nombreNegocio} (ID: ${cliente.id}) -> Ruta: ${cliente.rutaId}")
                
                // ---------------------------
                // 1) Subir foto a Firebase Storage (Usando putFile para eficiencia)
                // ---------------------------
                var downloadUrl: String? = null
                val rutaFotoLocal = cliente.fotografiaUrl

                if (!rutaFotoLocal.isNullOrBlank()) {
                    val lower = rutaFotoLocal.lowercase()
                    if (lower.startsWith("http") || lower.startsWith("gs://")) {
                        downloadUrl = rutaFotoLocal
                    } else {
                        val file = File(rutaFotoLocal)
                        if (file.exists()) {
                            val ref = storage.reference.child("clientes/${cliente.id}.jpg")
                            // Usamos putFile para evitar cargar todo el archivo en memoria RAM
                            ref.putFile(android.net.Uri.fromFile(file)).await()
                            downloadUrl = ref.downloadUrl.await().toString()
                        } else {
                            Log.w(TAG, "Archivo de foto no encontrado en: $rutaFotoLocal")
                        }
                    }
                }

                // ---------------------------
                // 2) Preparar datos para Firestore
                // 2) Preparar datos para Firestore (Uso de UPDATE para evitar borrar otros campos)
                val data = hashMapOf<String, Any?>(
                    "nombreNegocio" to cliente.nombreNegocio,
                    "nombreDueno" to cliente.nombreDueno,
                    "telefono" to cliente.telefono,
                    "correo" to cliente.correo,
                    "tipoExhibidor" to cliente.tipoExhibidor,
                    "ubicacion" to GeoPoint(cliente.ubicacionLat, cliente.ubicacionLon),
                    "activo" to cliente.activo,
                    "valorCliente" to cliente.valorCliente,
                    "rutaId" to cliente.rutaId,
                    "lastModified" to timeToSync
                )

                if (downloadUrl != null) {
                    data["FotografiaCliente"] = downloadUrl
                }

                // 3) Subir datos a Firestore (Operación crítica)
                firestore.collection("clientes")
                    .document(cliente.id)
                    .update(data)
                    .await()

                Log.d(TAG, "✅ Cliente sincronizado en la Nube: ${cliente.id} con Ruta: ${cliente.rutaId}")

                // ---------------------------
                // 4) ACTUALIZAR LOCALMENTE SOLO SI TODO LO ANTERIOR FUE EXITOSO
                // ---------------------------
                dao.update(
                    cliente.copy(
                        syncStatus = true,
                        ownerUid = uid,
                        lastModified = timeToSync
                    )
                )

                Log.d(TAG, "Cliente sincronizado exitosamente: ${cliente.id}")

            } catch (e: Exception) {
                Log.e(TAG, "Error sincronizando cliente ${cliente.id}, se reintentará luego.", e)
                todosSincronizados = false
            }
        }
        return todosSincronizados
    }


    // ============================================================
    // 🔥 3. Listener de cambios desde Firestore (bidireccional)
    // ============================================================
    fun escucharCambiosFirebase(context: Context) {
        listenerRegistration?.remove()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val usuarioActual = auth.currentUser?.uid ?: return@launch
                val userDoc = firestore.collection("users").document(usuarioActual).get().await()
                val puesto = userDoc.getString("puestoTrabajo") ?: ""
                val esAdmin = puesto in listOf("CEO", "Gerente General")
                
                val rutaRef = userDoc.getDocumentReference("rutaAsignada")
                val nombreRuta = if (rutaRef != null) {
                    rutaRef.get().await().getString("nombre")
                } else {
                    userDoc.getString("ultimoAlmacenNombre")
                }

                var query = firestore.collection("clientes") as com.google.firebase.firestore.Query
                if (!esAdmin && !nombreRuta.isNullOrEmpty()) {
                    query = query.whereEqualTo("rutaId", nombreRuta)
                }

                listenerRegistration = query.addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot == null) return@addSnapshotListener

                    CoroutineScope(Dispatchers.IO).launch {
                        val remoteDocuments = snapshot.documents
                        val localClientes = dao.getAllClientes()
                        val localMap = localClientes.associateBy { it.id }
                        val remoteIds = remoteDocuments.map { it.id }.toSet()

                        // 1. Eliminar locales que ya no corresponden a mi ruta o borrados en nube
                        localClientes.filter { it.syncStatus && !remoteIds.contains(it.id) }.forEach {
                            dao.deleteById(it.id)
                        }

                        // 2. Procesar documentos remotos
                        remoteDocuments.forEach { doc ->
                            val id = doc.id
                            val remoteLastModified = doc.getLong("lastModified") ?: 0L
                            val local = localMap[id]

                            val remoteEntity = ClienteEntity(
                                id = id,
                                nombreNegocio = doc.getString("nombreNegocio") ?: "Sin nombre",
                                nombreDueno = doc.getString("nombreDueno") ?: "Sin dueño",
                                telefono = doc.getString("telefono") ?: "",
                                correo = doc.getString("correo") ?: "",
                                tipoExhibidor = doc.getString("tipoExhibidor") ?: "",
                                ubicacionLat = doc.getGeoPoint("ubicacion")?.latitude ?: 0.0,
                                ubicacionLon = doc.getGeoPoint("ubicacion")?.longitude ?: 0.0,
                                fotografiaUrl = doc.getString("FotografiaCliente"),
                                activo = doc.getBoolean("activo") ?: true,
                                valorCliente = doc.getString("valorCliente") ?: "medio",
                                fechaDeCreacion = doc.getTimestamp("fechaDeCreacion")?.toDate()?.time ?: System.currentTimeMillis(),
                                syncStatus = true,
                                ownerUid = doc.getString("ownerUid") ?: "",
                                lastModified = remoteLastModified,
                                rutaId = when(val r = doc.get("rutaId")) {
                                    is String -> r
                                    is com.google.firebase.firestore.DocumentReference -> r.id
                                    else -> null
                                }
                            )

                            if (local == null || (local.syncStatus && remoteLastModified > local.lastModified)) {
                                dao.insert(remoteEntity)
                                // 🔥 Si es nuevo o cambió, descargar foto
                                if (!remoteEntity.fotografiaUrl.isNullOrBlank()) {
                                    procesarDescargaFotos(context, listOf(remoteEntity))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error iniciando listener", e)
            }
        }
    }


    suspend fun descargarFotoCliente(
        url: String,
        clienteId: String,
        context: Context
    ): String? {
        return try {
            val photosDir = File(context.filesDir, "clientes_photos")
            if (!photosDir.exists()) photosDir.mkdirs()

            val file = File(photosDir, "cliente_$clienteId.jpg")

            // ✅ Si ya existe, NO volver a descargar
            if (file.exists()) {
                Log.d("RepoCliente", "Foto ya existe: ${file.absolutePath}")
                return file.absolutePath
            }

            // 🛡️ ESTRATEGIA SEGURA: Descargar directamente a un archivo (getFile)
            // Esto evita cargar los bytes en la memoria RAM (evita OutOfMemoryError)
            storage.getReferenceFromUrl(url)
                .getFile(file)
                .await()

            Log.d("RepoCliente", "Foto descargada exitosamente a: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e("RepoCliente", "Error descargando foto", e)
            null
        }
    }

    suspend fun actualizarUbicacionClienteLocal(
        clienteId: String,
        lat: Double,
        lon: Double
    ) {
        val now = System.currentTimeMillis()

        dao.actualizarUbicacion(
            clienteId = clienteId,
            lat = lat,
            lon = lon,
            syncStatus = false,
            lastModified = now
        )

        Log.d(TAG, "Ubicación actualizada localmente id=$clienteId")
    }


    fun stopEscuchaFirebase() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }


    fun obtenerClientesLocal(): Flow<List<ClienteEntity>> {
        return dao.getAllClientesFlow()
    }

    suspend fun obtenerClientesLocalNoFlow(): List<ClienteEntity> = withContext(Dispatchers.IO) {
        dao.getAllClientes()
    }
}
