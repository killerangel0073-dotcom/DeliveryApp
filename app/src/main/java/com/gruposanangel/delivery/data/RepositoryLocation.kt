package com.gruposanangel.delivery.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RepositoryLocation(
    private val locationDao: LocationDao
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "RepoLocation"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * VÍA DE AUDITORÍA: Guarda localmente en Room.
     * Ya no intenta subir a Firestore inmediatamente.
     */
    suspend fun guardarUbicacion(
        lat: Double,
        lng: Double,
        accuracy: Float,
        speed: Float,
        battery: Int,
        ruta: String,
        status: String
    ) {
        try {
            locationDao.insertar(
                LocationEntity(
                    latitude = lat,
                    longitude = lng,
                    accuracy = accuracy,
                    speed = speed,
                    battery = battery,
                    timestamp = System.currentTimeMillis(),
                    ruta = ruta,
                    status = status
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando en Room: ${e.message}")
        }
    }

    /**
     * VÍA DE SINCRONIZACIÓN POR RÁFAGA (BATCHING):
     * Sube un bloque de puntos a Firestore en una sola transacción.
     */
    suspend fun sincronizarLoteAFirestore(): Boolean {
        val pendientes = locationDao.obtenerPendientes()
        if (pendientes.isEmpty()) return true

        Log.d(TAG, "🚀 Iniciando ráfaga (Batch) de ${pendientes.size} puntos...")

        // Agrupamos por ruta y fecha por si hay puntos de días anteriores rezagados
        val agrupados = pendientes.groupBy { 
            "${it.ruta}_${dateFormat.format(Date(it.timestamp))}" 
        }

        return try {
            val batch = firestore.batch()

            agrupados.forEach { (docId, puntos) ->
                val docRef = firestore.collection("historial_rutas").document(docId)
                
                // Mapeamos los puntos al formato de Firestore
                val rastroData = puntos.map { 
                    mapOf(
                        "lat" to it.latitude,
                        "lng" to it.longitude,
                        "vel" to it.speed,
                        "bat" to it.battery,
                        "ts" to Timestamp(Date(it.timestamp)),
                        "acc" to it.accuracy,
                        "st" to it.status
                    )
                }

                // Usamos arrayUnion para añadir todos los puntos en UNA sola escritura
                batch.set(
                    docRef, 
                    mapOf("historialRecorrido" to FieldValue.arrayUnion(*rastroData.toTypedArray())),
                    SetOptions.merge()
                )
            }

            batch.commit().await()
            
            // 🛡️ Solo eliminamos de Room si Firestore confirmó éxito total
            locationDao.eliminar(pendientes)
            Log.d(TAG, "✅ Ráfaga exitosa. Room liberado.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Fallo la ráfaga a Firestore: ${e.message}")
            false
        }
    }

    suspend fun obtenerConteoPendientes(): Int = locationDao.obtenerConteoPendientes()
}
