package com.gruposanangel.delivery.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RepositoryRuta(
    private val rutaDao: RutaDao,
    private val clienteDao: ClienteDao
) {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "RepositoryRuta"

    /**
     * Sincroniza las rutas desde Firestore a Room.
     */
    suspend fun descargarRutasDesdeFirebase() {
        try {
            val snapshot = db.collection("rutas").get().await()
            val rutas = snapshot.documents.map { doc ->
                RutaEntity(
                    id = doc.id,
                    nombre = doc.getString("nombre") ?: "Sin nombre",
                    diasVisita = doc.getString("diasVisita") ?: "",
                    frecuencia = doc.getString("frecuencia") ?: "Semanal",
                    activo = doc.getBoolean("activo") ?: true,
                    lastModified = doc.getLong("lastModified") ?: System.currentTimeMillis()
                )
            }
            rutas.forEach { rutaDao.insertarRuta(it) }
            Log.d(TAG, "Se descargaron ${rutas.size} rutas.")
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando rutas", e)
        }
    }

    fun obtenerRutasLocal(): Flow<List<RutaEntity>> = rutaDao.obtenerRutasFlow()

    /**
     * Guarda una ruta en local y la sube a Firebase.
     */
    suspend fun guardarRuta(ruta: RutaEntity) = withContext(Dispatchers.IO) {
        try {
            rutaDao.insertarRuta(ruta)
            db.collection("rutas").document(ruta.id).set(ruta, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando ruta", e)
        }
    }

    /**
     * Asigna una lista de clientes a una ruta específica.
     */
    suspend fun asignarClientesARuta(rutaId: String, clientesIds: Set<String>) = withContext(Dispatchers.IO) {
        try {
            // 1. Actualizar en Firestore (Batch recomendado, pero haremos set individual por simplicidad)
            val batch = db.batch()
            clientesIds.forEach { id ->
                val ref = db.collection("clientes").document(id)
                batch.update(ref, "rutaId", rutaId)
            }
            batch.commit().await()

            // 2. Actualizar en local (Room)
            clientesIds.forEach { id ->
                val cliente = clienteDao.getClientePorId(id)
                cliente?.let {
                    clienteDao.update(it.copy(rutaId = rutaId, syncStatus = true))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error asignando clientes a ruta", e)
        }
    }
}
