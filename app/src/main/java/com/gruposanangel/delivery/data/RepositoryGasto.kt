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

    /**
     * Sincroniza todos los gastos de una RUTA de los últimos 90 días.
     */
    suspend fun sincronizarRuta90Dias(rutaId: String, rutaNombre: String) = withContext(Dispatchers.IO) {
        if (rutaId.isEmpty() && rutaNombre.isEmpty()) return@withContext
        
        val noventaDiasAtras = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, -90)
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
        }.time

        try {
            Log.d(TAG, "📡 Sincronizando GASTOS para Ruta: $rutaNombre")
            
            // Consultamos por Nombre de Ruta o ID para asegurar legacy y nuevo
            val snapshot = db.collection("gastos")
                .whereGreaterThanOrEqualTo("timestamp", Timestamp(noventaDiasAtras))
                .get().await()

            var procesados = 0
            snapshot.documents.forEach { doc ->
                val rNomDoc = doc.getString("rutaNombre") ?: ""
                val rIdDoc = doc.getString("rutaId") ?: ""
                
                // Si coincide con nuestra ruta
                if (rIdDoc == rutaId || rNomDoc == rutaNombre) {
                    val id = doc.id
                    val monto = (doc.get("monto") as? Number)?.toDouble() ?: 0.0
                    val categoria = doc.getString("categoria") ?: "Otros"
                    val descripcion = doc.getString("descripcion") ?: ""
                    
                    val fRaw = doc.get("timestamp") ?: doc.get("fecha")
                    val fechaMs = when(fRaw) {
                        is Timestamp -> fRaw.toDate().time
                        is Number -> fRaw.toLong()
                        else -> 0L
                    }
                    
                    if (fechaMs > 0) {
                        val vIdDoc = vIdFromRaw(doc.get("vendedorId") ?: doc.get("vendedorRef"))
                        val vNom = doc.getString("vendedorNombre") ?: ""
                        
                        gastoDao.insertar(GastoEntity(id, monto, categoria, descripcion, fechaMs, vIdDoc, vNom, rNomDoc, true))
                        procesados++
                    }
                }
            }
            Log.d(TAG, "✅ Gastos de ruta guardados: $procesados")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sincronizando gastos de ruta: ${e.message}")
        }
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

    /**
     * Sincroniza el catálogo de gastos fijos (Renta, Sueldos, etc)
     */
    suspend fun descargarGastosFijos() = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("config_gastos_fijos")
                .whereEqualTo("activo", true)
                .get().await()

            snapshot.documents.forEach { doc ->
                val id = doc.id
                val monto = (doc.get("monto") as? Number)?.toDouble() ?: 0.0
                val desc = doc.getString("descripcion") ?: ""
                val peri = doc.getString("periodicidad") ?: "MENSUAL"
                
                gastoDao.insertar(GastoEntity(
                    id = id,
                    monto = monto,
                    categoria = "FIJO",
                    descripcion = desc,
                    fecha = System.currentTimeMillis(),
                    vendedorId = "ADMIN",
                    vendedorNombre = "SISTEMA",
                    rutaNombre = "GLOBAL",
                    sincronizado = true,
                    esFijo = true,
                    periodicidad = peri,
                    activo = true
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando fijos: ${e.message}")
        }
    }

    suspend fun guardarGastoFijo(descripcion: String, monto: Double, peri: String, categoria: String = "FIJO") = withContext(Dispatchers.IO) {
        val id = java.util.UUID.randomUUID().toString()
        val data = mapOf(
            "descripcion" to descripcion,
            "monto" to monto,
            "periodicidad" to peri,
            "categoria" to categoria,
            "activo" to true,
            "timestamp" to Timestamp.now()
        )
        try {
            db.collection("config_gastos_fijos").document(id).set(data).await()
            gastoDao.insertar(GastoEntity(
                id = id, monto = monto, categoria = categoria, descripcion = descripcion,
                fecha = System.currentTimeMillis(), vendedorId = "ADMIN", vendedorNombre = "SISTEMA",
                rutaNombre = "GLOBAL", sincronizado = true, esFijo = true,
                periodicidad = peri, activo = true
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando fijo: ${e.message}")
        }
    }

    suspend fun eliminarGastoFijo(id: String) = withContext(Dispatchers.IO) {
        try {
            db.collection("config_gastos_fijos").document(id).update("activo", false).await()
            gastoDao.eliminarGastoPorId(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando fijo: ${e.message}")
        }
    }

    suspend fun actualizarGastoFijo(id: String, descripcion: String, monto: Double, peri: String) = withContext(Dispatchers.IO) {
        val data = mapOf(
            "descripcion" to descripcion,
            "monto" to monto,
            "periodicidad" to peri,
            "timestamp" to Timestamp.now()
        )
        try {
            db.collection("config_gastos_fijos").document(id).update(data).await()
            // Actualizar localmente
            val local = GastoEntity(
                id = id, monto = monto, categoria = "FIJO", descripcion = descripcion,
                fecha = System.currentTimeMillis(), vendedorId = "ADMIN", vendedorNombre = "SISTEMA",
                rutaNombre = "GLOBAL", sincronizado = true, esFijo = true,
                periodicidad = peri, activo = true
            )
            gastoDao.insertar(local)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando fijo: ${e.message}")
        }
    }

    fun obtenerGastosFijosActivos(): Flow<List<GastoEntity>> = gastoDao.obtenerGastosFijosActivos()
}
