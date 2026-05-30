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


    suspend fun obtenerClientesLocalPorId(id: String): ClienteEntity? {
        return dao.getClientePorId(id)
    }

    /**
     * 🔥 Descarga masiva de clientes desde Firebase para asegurar disponibilidad 100% Offline.
     * Diseñada para ejecutarse al arranque de la app o después del login.
     */
    suspend fun descargarClientesFirebase(context: Context) {
        try {
            Log.d(TAG, "Iniciando descarga masiva de clientes...")

            // 1. Petición directa y de un solo golpe
            val snapshot = firestore.collection("clientes")
                .get()
                .await()

            if (snapshot.isEmpty) {
                Log.d(TAG, "No hay clientes en Firestore para descargar.")
                return
            }

            // 2. Obtener mapa de clientes locales para respetar cambios offline
            val localMap = dao.getAllClientes().associateBy { it.id }

            // 3. Procesar documentos en paralelo para eficiencia
            val clientesParaGuardar = coroutineScope {
                snapshot.documents.map { doc ->
                    async {
                        val id = doc.id
                        val remoteLastModified = doc.getLong("lastModified") ?: 0L
                        val local = localMap[id]

                        // 🛡️ REGLA DE PROTECCIÓN: Respetar datos locales no sincronizados
                        if (local != null) {
                            if (!local.syncStatus) return@async null // Si el vendedor editó offline, no tocar.
                            if (remoteLastModified <= local.lastModified) return@async null // No bajar datos viejos.
                        }

                        // Mapeo a entidad local
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
                            medio = doc.getString("medio") ?: "",
                            fechaDeCreacion = doc.getTimestamp("fechaDeCreacion")?.toDate()?.time ?: System.currentTimeMillis(),
                            syncStatus = true,
                            ownerUid = doc.getString("ownerUid") ?: "",
                            lastModified = remoteLastModified
                        )
                    }
                }.awaitAll().filterNotNull()
            }

            // 4. Guardado masivo en Room
            if (clientesParaGuardar.isNotEmpty()) {
                dao.insertAll(clientesParaGuardar)
                Log.d(TAG, "Se descargaron y guardaron ${clientesParaGuardar.size} clientes nuevos/actualizados.")

                // 5. Descarga de fotos en segundo plano (opcional)
                CoroutineScope(Dispatchers.IO).launch {
                    clientesParaGuardar.forEach { cliente ->
                        val url = cliente.fotografiaUrl
                        if (!url.isNullOrBlank() && url.startsWith("http")) {
                            try {
                                val rutaLocal = descargarFotoCliente(url, cliente.id, context)
                                if (!rutaLocal.isNullOrBlank()) {
                                    // Solo actualizamos si la descarga fue exitosa
                                    dao.update(cliente.copy(fotografiaUrl = rutaLocal))
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error descargando/actualizando foto para ${cliente.id}", e)
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error en descarga masiva de clientes", e)
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
            val now = System.currentTimeMillis()

            try {
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
                // ---------------------------
                val data = hashMapOf<String, Any?>(
                    "nombreNegocio" to cliente.nombreNegocio,
                    "nombreDueno" to cliente.nombreDueno,
                    "telefono" to cliente.telefono,
                    "correo" to cliente.correo,
                    "tipoExhibidor" to cliente.tipoExhibidor,
                    "ubicacion" to GeoPoint(cliente.ubicacionLat, cliente.ubicacionLon),
                    "activo" to cliente.activo,
                    "medio" to cliente.medio,
                    "ownerUid" to uid,
                    "lastModified" to now,
                    "fechaDeCreacion" to Timestamp(Date(cliente.fechaDeCreacion)),
                    "FotografiaCliente" to downloadUrl
                )

                // ---------------------------
                // 3) Subir datos a Firestore (Operación crítica)
                // ---------------------------
                firestore.collection("clientes")
                    .document(cliente.id)
                    .set(data)
                    .await()

                // ---------------------------
                // 4) ACTUALIZAR LOCALMENTE SOLO SI TODO LO ANTERIOR FUE EXITOSO
                // ---------------------------
                dao.update(
                    cliente.copy(
                        syncStatus = true,
                        ownerUid = uid,
                        lastModified = now
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

        // Escuchar la colección completa. Firebase enviará el estado actual inmediatamente.
        listenerRegistration = firestore.collection("clientes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error escuchando firestore: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    Log.d(TAG, "Snapshot vacío o nulo")
                    return@addSnapshotListener
                }

                // Procesamos en un hilo de fondo sin cancelar el job anterior si no es necesario,
                // pero asegurando que no se encimen procesos pesados.
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val remoteDocuments = snapshot.documents
                        val localClientes = dao.getAllClientes()
                        val localMap = localClientes.associateBy { it.id }
                        val remoteIds = remoteDocuments.map { it.id }.toSet()

                        // 1. Eliminar locales que ya no existen en la nube (solo los sincronizados)
                        localClientes.filter { it.syncStatus && !remoteIds.contains(it.id) }.forEach {
                            dao.deleteById(it.id)
                        }

                        // 2. Procesar documentos remotos
                        remoteDocuments.forEach { doc ->
                            val id = doc.id
                            val remoteLastModified = doc.getLong("lastModified") ?: 0L
                            val local = localMap[id]

                            // Mapeo básico
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
                                medio = doc.getString("medio") ?: "medio",
                                fechaDeCreacion = doc.getTimestamp("fechaDeCreacion")?.toDate()?.time ?: System.currentTimeMillis(),
                                syncStatus = true,
                                ownerUid = doc.getString("ownerUid") ?: "",
                                lastModified = remoteLastModified
                            )

                            // Lógica de actualización:
                            // - Si no existe localmente -> Insertar
                            // - Si existe y remote es más nuevo -> Actualizar
                            // - Si existe pero local no está sincronizado (pendiente de subida) -> Ignorar (respetar local)
                            if (local == null || (local.syncStatus && remoteLastModified > local.lastModified)) {
                                dao.insert(remoteEntity)
                                
                                // Descarga de foto opcional si es nueva
                                val url = remoteEntity.fotografiaUrl
                                if (!url.isNullOrBlank() && url.startsWith("http")) {
                                    launch {
                                        val path = descargarFotoCliente(url, id, context)
                                        if (path != null) {
                                            dao.update(remoteEntity.copy(fotografiaUrl = path))
                                        }
                                    }
                                }
                            }
                        }
                        Log.d(TAG, "Sincronización reactiva completada: ${remoteDocuments.size} documentos procesados")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error procesando snapshot", e)
                    }
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
}
