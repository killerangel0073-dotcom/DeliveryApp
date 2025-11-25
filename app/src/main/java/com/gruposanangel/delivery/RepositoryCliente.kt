package com.gruposanangel.delivery.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.storage.FirebaseStorage
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
    private var listenerRegistration: ListenerRegistration? = null
    private val TAG = "ClienteRepository"

    /**
     * Guarda un cliente localmente en Room.
     */
    suspend fun guardarLocal(cliente: ClienteEntity) {
        try {
            dao.insert(cliente)
            Log.d(TAG, "guardarLocal: cliente insertado localmente id=${cliente.id}")
        } catch (e: Exception) {
            Log.e(TAG, "guardarLocal: error al insertar cliente localmente", e)
            throw e
        }
    }


    /**
     * Obtiene un cliente local por su ID.
     */

    suspend fun obtenerClientesLocalPorId(id: String): ClienteEntity? {
        return dao.getClientePorId(id)
    }


    /**
     * Sincroniza clientes pendientes con Firebase.
     * Subirá fotos locales si existen y actualizará syncStatus y fotografíaUrl.
     */
    suspend fun sincronizarConFirebase() {
        val pendientes = try {
            dao.getClientesPendientes()
        } catch (e: Exception) {
            Log.e(TAG, "sincronizarConFirebase: error obteniendo pendientes", e)
            return
        }

        pendientes.forEach { cliente ->
            try {
                // Determinar si hay que subir la foto o ya es una URL remota
                val downloadUrl: String? = cliente.fotografiaUrl?.let { ruta ->
                    // Si ya es una URL remota (http / https / gs://), no subir nada
                    val lower = ruta.lowercase()
                    if (lower.startsWith("http") || lower.startsWith("gs://")) {
                        ruta
                    } else {
                        val file = File(ruta)
                        if (!file.exists()) {
                            Log.w(TAG, "sincronizarConFirebase: archivo de foto no existe: $ruta")
                            null
                        } else {
                            val fileRef = storage.reference.child("clientes/${cliente.id}.jpg")
                            val bytes = file.readBytes()
                            fileRef.putBytes(bytes).await()
                            fileRef.downloadUrl.await().toString()
                        }
                    }
                }

                // Construir mapa de datos; solo incluir FotografiaCliente si existe downloadUrl
                val data = hashMapOf<String, Any?>(
                    "nombreNegocio" to cliente.nombreNegocio,
                    "nombreDueno" to cliente.nombreDueno,
                    "telefono" to cliente.telefono,
                    "correo" to cliente.correo,
                    "tipoExhibidor" to cliente.tipoExhibidor,
                    "ubicacion" to GeoPoint(cliente.ubicacionLat, cliente.ubicacionLon),
                    "activo" to cliente.activo,
                    "medio" to cliente.medio,
                    "fechaDeCreacion" to Timestamp(Date(cliente.fechaDeCreacion))
                )

                if (!downloadUrl.isNullOrBlank()) {
                    data["FotografiaCliente"] = downloadUrl
                }

                // Subir/actualizar documento con el ID del cliente
                firestore.collection("clientes").document(cliente.id).set(data).await()

                // Actualizar registro local: marcar syncStatus = true y actualizar fotografiaUrl si conseguimos URL remota
                val nuevaRutaFoto = downloadUrl ?: cliente.fotografiaUrl
                dao.update(cliente.copy(syncStatus = true, fotografiaUrl = nuevaRutaFoto))
                Log.d(TAG, "sincronizarConFirebase: cliente sincronizado id=${cliente.id}")

            } catch (e: Exception) {
                // No detengas el loop; registra el error para depuración
                Log.e(TAG, "sincronizarConFirebase: error sincronizando cliente id=${cliente.id}", e)
            }
        }
    }

    /**
     * Escucha cambios de Firebase y mantiene la base local actualizada.
     *
     * NOTA: No eliminará clientes locales que estén pendientes de sincronizar (syncStatus == false).
     * Sí eliminará clientes locales que ya estaban sincronizados pero ya no existan en Firestore.
     */
    fun escucharCambiosFirebase() {
        // Remover cualquier listener previo
        listenerRegistration?.remove()

        listenerRegistration = firestore.collection("clientes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "escucharCambiosFirebase: error escuchando firestore", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val firebaseIds = snapshot.documents.map { it.id }.toSet()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Obtener clientes locales una vez
                        val localClientes = dao.getAllClientes()
                        val localMap = localClientes.associateBy { it.id }

                        // 1) Eliminar clientes locales que estaban sincronizados pero ya no están en Firestore
                        localClientes.forEach { cliente ->
                            if (cliente.syncStatus && !firebaseIds.contains(cliente.id)) {
                                Log.d(TAG, "escucharCambiosFirebase: eliminando local id=${cliente.id} (no existe en Firestore)")
                                dao.deleteById(cliente.id)
                            }
                        }

                        // 2) Insertar/actualizar clientes que vienen desde Firebase
                        snapshot.documents.forEach { doc ->
                            val id = doc.id
                            val fechaMillis = doc.getTimestamp("fechaDeCreacion")?.toDate()?.time
                                ?: System.currentTimeMillis()

                            // Construir entidad desde remoto
                            val clienteFromRemote = ClienteEntity(
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
                                syncStatus = true // viene de la nube => ya sincronizado
                            )

                            // Si existe un local pendiente (syncStatus == false), preservarlo y no sobrescribir
                            val local = localMap[id]
                            if (local != null && !local.syncStatus) {
                                Log.d(TAG, "escucharCambiosFirebase: preservando local pendiente id=$id")
                                // Opcional: podrías intentar reconciliar campos (ej. mantener foto local) — aquí no se sobrescribe
                            } else {
                                // Insertar o reemplazar con los datos de Firestore
                                dao.insert(clienteFromRemote)
                                Log.d(TAG, "escucharCambiosFirebase: insert/replace desde remote id=$id")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "escucharCambiosFirebase: error procesando snapshot", e)
                    }
                }
            }
    }

    /**
     * Detiene la escucha de cambios en Firestore (evitar fugas).
     */
    fun stopEscuchaFirebase() {
        listenerRegistration?.remove()
        listenerRegistration = null
        Log.d(TAG, "stopEscuchaFirebase: listener removido")
    }

    /**
     * Devuelve todos los clientes locales como Flow para ser observados en Compose.
     */
    fun obtenerClientesLocal(): Flow<List<ClienteEntity>> {
        return dao.getAllClientesFlow()
    }
}
