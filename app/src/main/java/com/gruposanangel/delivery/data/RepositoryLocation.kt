package com.gruposanangel.delivery.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Date

class RepositoryLocation(
    private val locationDao: LocationDao
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "RepoLocation"

    suspend fun guardarUbicacion(
        lat: Double,
        lng: Double,
        accuracy: Float,
        speed: Float,
        battery: Int,
        ruta: String,
        status: String
    ) {
        val timestamp = System.currentTimeMillis()
        
        // 1. Intentar subir a Firestore
        val success = subirAFirestore(lat, lng, accuracy, speed, battery, ruta, status, timestamp)
        
        // 2. Si falla o si queremos asegurar persistencia total, guardar en Room como "pendiente"
        if (!success) {
            Log.w(TAG, "Fallo Firestore, guardando en Room para rastro Offline")
            locationDao.insertar(
                LocationEntity(
                    latitude = lat,
                    longitude = lng,
                    accuracy = accuracy,
                    speed = speed,
                    battery = battery,
                    timestamp = timestamp,
                    ruta = ruta,
                    status = status
                )
            )
        }
    }

    private suspend fun subirAFirestore(
        lat: Double,
        lng: Double,
        accuracy: Float,
        speed: Float,
        battery: Int,
        ruta: String,
        status: String,
        ts: Long
    ): Boolean {
        return try {
            val data = mapOf(
                "latitude" to lat,
                "longitude" to lng,
                "accuracy" to accuracy,
                "speed" to speed,
                "battery" to battery,
                "timestamp" to Timestamp(Date(ts)),
                "status" to status
            )
            firestore.collection("locations")
                .document(ruta)
                .set(data, SetOptions.merge())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo a Firestore: ${e.message}")
            false
        }
    }

    suspend fun sincronizarPendientes() {
        val pendientes = locationDao.obtenerPendientes()
        if (pendientes.isEmpty()) return

        Log.d(TAG, "Sincronizando ${pendientes.size} ubicaciones offline...")
        
        for (loc in pendientes) {
            val success = subirAFirestore(
                loc.latitude, loc.longitude, loc.accuracy, 
                loc.speed, loc.battery, loc.ruta, loc.status, loc.timestamp
            )
            if (success) {
                locationDao.eliminarPorId(loc.id)
            } else {
                break // Detener si falla la red nuevamente
            }
        }
    }
}
