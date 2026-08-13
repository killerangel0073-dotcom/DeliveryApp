package com.gruposanangel.delivery.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.gruposanangel.delivery.Itinerario
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

    /**
     * Obtiene todos los clientes de la base de datos local para mapeo rápido.
     */
    suspend fun obtenerTodosLosClientesLocal(): List<ClienteEntity> = withContext(Dispatchers.IO) {
        clienteDao.getAllClientes()
    }

    /**
     * Obtiene un cliente por su ID desde la DB local.
     */
    suspend fun obtenerClienteLocal(id: String): ClienteEntity? = withContext(Dispatchers.IO) {
        clienteDao.getClientePorId(id)
    }

    /**
     * Guarda un itinerario en la colección puente 'rutas_itinerarios'.
     * No toca la colección 'clientes'.
     */
    suspend fun guardarItinerario(itinerario: Itinerario) = withContext(Dispatchers.IO) {
        try {
            // 🛡️ Mapeo manual para limpieza absoluta de campos 'fantasma' (stability)
            val data = hashMapOf(
                "id" to itinerario.id,
                "rutaId" to itinerario.rutaId,
                "diaSemana" to itinerario.diaSemana,
                "frecuencia" to itinerario.frecuencia,
                "activo" to itinerario.activo,
                "lastUpdated" to itinerario.lastUpdated,
                "clientesOrdenados" to itinerario.clientesOrdenados.map {
                    mapOf(
                        "clienteId" to it.clienteId,
                        "ordenVisita" to it.ordenVisita
                    )
                }
            )

            db.collection("rutas_itinerarios")
                .document(itinerario.id)
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Itinerario guardado (Limpio): ${itinerario.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando itinerario", e)
            throw e
        }
    }

    /**
     * Obtiene un itinerario específico de Firestore.
     */
    suspend fun obtenerItinerario(id: String): Itinerario? {
        return try {
            val doc = db.collection("rutas_itinerarios").document(id).get().await()
            doc.toObject(Itinerario::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo itinerario", e)
            null
        }
    }

    /**
     * Descarga todos los itinerarios configurados en la colección 'rutas_itinerarios'.
     */
    suspend fun obtenerTodosLosItinerarios(): List<Itinerario> = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("rutas_itinerarios").get().await()
            snapshot.toObjects(Itinerario::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo todos los itinerarios", e)
            emptyList()
        }
    }
}
