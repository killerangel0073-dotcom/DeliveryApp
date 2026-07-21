package com.gruposanangel.delivery.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

class RepositoryGasto(private val gastoDao: GastoDao) {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "RepositoryGasto"

    fun obtenerGastosHoy(vendedorId: String): Flow<List<GastoEntity>> {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        return gastoDao.obtenerGastosHoyFlow(vendedorId, cal.timeInMillis)
    }

    suspend fun guardarGastoLocal(gasto: GastoEntity) {
        gastoDao.insertar(gasto)
    }

    suspend fun obtenerGastosPorPeriodo(vendedorId: String, inicio: Long, fin: Long): List<GastoEntity> = withContext(Dispatchers.IO) {
        if (vendedorId.isEmpty()) {
            gastoDao.obtenerTodasGastosPorPeriodo(inicio, fin)
        } else {
            gastoDao.obtenerGastosPorPeriodo(vendedorId, inicio, fin)
        }
    }

    fun obtenerGastosPorPeriodoFlow(vendedorId: String, inicio: Long, fin: Long): Flow<List<GastoEntity>> {
        return if (vendedorId.isEmpty()) {
            gastoDao.obtenerTodasGastosPorPeriodoFlow(inicio, fin)
        } else {
            gastoDao.obtenerGastosPorPeriodoFlow(vendedorId, inicio, fin)
        }
    }

    fun obtenerGastosPorRutaPeriodoFlow(rutaNombre: String, inicio: Long, fin: Long): Flow<List<GastoEntity>> {
        return gastoDao.obtenerGastosPorRutaPeriodoFlow(rutaNombre, inicio, fin)
    }

    private fun vIdFromRaw(raw: Any?): String {
        return if (raw is DocumentReference) raw.id else raw?.toString() ?: ""
    }

    suspend fun descargarGastosPeriodo(vendedorId: String, inicio: Long, fin: Long) = withContext(Dispatchers.IO) {
        try {
            // 🛡️ Ampliamos ventana para evitar desfases (como en ventas)
            val startTs = Timestamp(Date(inicio - (24 * 60 * 60 * 1000))) 
            val endTs = Timestamp(Date(fin + (24 * 60 * 60 * 1000)))
            
            Log.d(TAG, "📡 Sincronizando Gastos para UID: $vendedorId en periodo ${Date(inicio)} a ${Date(fin)}")

            // 🛡️ BÚSQUEDA SEGURA: Filtramos solo por tiempo en Firestore 
            // Esto evita problemas de índices compuestos o discrepancias en el nombre del campo vendedorId/vendedorRef
            val snapshot = db.collection("gastos")
                .whereGreaterThanOrEqualTo("timestamp", startTs)
                .whereLessThanOrEqualTo("timestamp", endTs)
                .get().await()

            Log.i(TAG, "📊 Gastos encontrados en Firestore (total periodo): ${snapshot.size()}")
            
            var procesados = 0
            snapshot.documents.forEach { doc ->
                try {
                    // Extraer Vendedor ID con soporte para String o Reference y varios nombres de campo
                    val vIdDoc = vIdFromRaw(doc.get("vendedorId") ?: doc.get("vendedorRef") ?: doc.get("vendedorid"))
                    
                    // Filtrar por el vendedor solicitado (o todos si está vacío)
                    if (vendedorId.isEmpty() || vIdDoc == vendedorId) {
                        val id = doc.id
                        val monto = (doc.get("monto") as? Number)?.toDouble() ?: 0.0
                        val categoria = doc.getString("categoria") ?: "Otros"
                        val descripcion = doc.getString("descripcion") ?: ""
                        
                        // Soporte para timestamp o fecha (y tipos Timestamp o Number)
                        val fRaw = doc.get("timestamp") ?: doc.get("fecha")
                        val fechaMs = when(fRaw) {
                            is Timestamp -> fRaw.toDate().time
                            is Number -> fRaw.toLong()
                            else -> 0L
                        }
                        
                        if (fechaMs > 0) {
                            val vNom = doc.getString("vendedorNombre") ?: ""
                            val rNom = doc.getString("rutaNombre") ?: ""
                            
                            gastoDao.insertar(GastoEntity(id, monto, categoria, descripcion, fechaMs, vIdDoc, vNom, rNom, true))
                            procesados++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error parseando gasto ${doc.id}: ${e.message}")
                }
            }
            Log.d(TAG, "✅ Gastos guardados en local: $procesados")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error crítico descargando gastos: ${e.message}", e)
        }
    }

    suspend fun sincronizarPendientes() = withContext(Dispatchers.IO) {
        try {
            val pendientes = gastoDao.obtenerGastosPendientes()
            for (gasto in pendientes) {
                val data = mapOf(
                    "monto" to gasto.monto,
                    "categoria" to gasto.categoria,
                    "descripcion" to gasto.descripcion,
                    "vendedorId" to gasto.vendedorId,
                    "vendedorNombre" to gasto.vendedorNombre,
                    "rutaNombre" to gasto.rutaNombre,
                    "timestamp" to Timestamp(Date(gasto.fecha))
                )
                
                db.collection("gastos").document(gasto.id).set(data).await()
                gastoDao.marcarComoSincronizado(gasto.id)
                Log.d(TAG, "Gasto ${gasto.id} sincronizado")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sincronizando gastos: ${e.message}")
        }
    }
}
