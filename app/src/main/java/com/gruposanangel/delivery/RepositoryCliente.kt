package com.gruposanangel.delivery.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Date

class RepositoryCliente(private val dao: ClienteDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var listenerRegistration: ListenerRegistration? = null
    private val TAG = "ClienteRepository"


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


    // ============================================================
    // 🔥 2. Sincronizar con Firebase (sube foto, datos y metadata)
    // ============================================================
    suspend fun sincronizarConFirebase() {
        val pendientes = try {
            dao.getClientesPendientes()
        } catch (e: Exception) {
            Log.e(TAG, "sincronizarConFirebase: error obteniendo pendientes", e)
            return
        }

        val uid = auth.currentUser?.uid ?: "desconocido"

        pendientes.forEach { cliente ->

            val now = System.currentTimeMillis()

            try {

                // ---------------------------
                // 1) Subir foto si es local
                // ---------------------------
                val downloadUrl: String? = cliente.fotografiaUrl?.let { ruta ->
                    val lower = ruta.lowercase()
                    if (lower.startsWith("http") || lower.startsWith("gs://")) {
                        ruta
                    } else {
                        val file = File(ruta)
                        if (!file.exists()) {
                            Log.w(TAG, "Foto local inexistente: $ruta")
                            null
                        } else {
                            val ref = storage.reference.child("clientes/${cliente.id}.jpg")
                            ref.putBytes(file.readBytes()).await()
                            ref.downloadUrl.await().toString()
                        }
                    }
                }

                // ---------------------------
                // 2) Mapear datos para Firestore
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

                    // 🔥 Nuevos campos
                    "ownerUid" to uid,
                    "lastModified" to now,

                    "fechaDeCreacion" to Timestamp(Date(cliente.fechaDeCreacion))
                )

                if (!downloadUrl.isNullOrBlank())
                    data["FotografiaCliente"] = downloadUrl

                // ---------------------------
                // 3) Subir a Firestore
                // ---------------------------
                firestore.collection("clientes")
                    .document(cliente.id)
                    .set(data)
                    .await()

                // ---------------------------
                // 4) Actualizar local como sincronizado
                // ---------------------------
                val nuevaRuta = downloadUrl ?: cliente.fotografiaUrl

                dao.update(
                    cliente.copy(
                        syncStatus = true,
                        fotografiaUrl = nuevaRuta,
                        ownerUid = uid,
                        lastModified = now
                    )
                )

                Log.d(TAG, "Cliente sincronizado: ${cliente.id}")

            } catch (e: Exception) {
                Log.e(TAG, "Error sincronizando cliente ${cliente.id}", e)
            }
        }
    }

    // ============================================================
    // 🔥 3. Listener de cambios desde Firestore (bidireccional)
    // ============================================================
    fun escucharCambiosFirebase() {

        listenerRegistration?.remove()

        listenerRegistration = firestore.collection("clientes")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    Log.e(TAG, "Error escuchando firestore", error)
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val remoteIds = snapshot.documents.map { it.id }.toSet()

                CoroutineScope(Dispatchers.IO).launch {

                    val localClientes = dao.getAllClientes()
                    val localMap = localClientes.associateBy { it.id }

                    // ======================================================
                    // 1) Eliminar locales si NO están en Firestore
                    // ======================================================
                    localClientes.forEach { local ->
                        if (local.syncStatus && !remoteIds.contains(local.id)) {
                            dao.deleteById(local.id)
                        }
                    }

                    // ======================================================
                    // 2) Insertar / actualizar desde Firestore
                    // ======================================================
                    snapshot.documents.forEach { doc ->

                        val id = doc.id

                        val fechaMillis =
                            doc.getTimestamp("fechaDeCreacion")?.toDate()?.time
                                ?: System.currentTimeMillis()

                        val lastModified =
                            doc.getLong("lastModified")
                                ?: System.currentTimeMillis()

                        val remote = ClienteEntity(
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
                            medio = doc.getString("medio") ?: "medio",

                            fechaDeCreacion = fechaMillis,
                            syncStatus = true,

                            ownerUid = doc.getString("ownerUid") ?: "",
                            lastModified = lastModified
                        )

                        val local = localMap[id]

                        // ======================================================
                        // 🔥 Reglas para resolver conflicto local vs remoto
                        // ======================================================
                        if (local != null) {

                            if (!local.syncStatus) {
                                // local modificado → lo preservamos
                                return@forEach
                            }

                            if (remote.lastModified <= local.lastModified) {
                                // remoto es más viejo → no sobreescribimos
                                return@forEach
                            }
                        }

                        // remoto gana → actualizamos local
                        dao.insert(remote)
                    }
                }
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
