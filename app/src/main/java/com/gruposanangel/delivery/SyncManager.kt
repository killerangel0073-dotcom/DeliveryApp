package com.gruposanangel.delivery

import android.content.Context
import android.util.Log
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.RepositoryGasto
import com.gruposanangel.delivery.data.RepositoryInventario
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

/**
 * SyncManager - Motor de Blindaje Offline.
 * Se encarga de que el teléfono tenga toda la información necesaria para trabajar sin internet.
 */
class SyncManager(
    private val ventaRepository: VentaRepository,
    private val gastoRepository: RepositoryGasto,
    private val clienteRepository: RepositoryCliente,
    private val inventarioRepository: RepositoryInventario,
    private val context: Context
) {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "SyncManager"

    /**
     * Ejecuta una sincronización completa de la unidad operativa.
     * Baja Clientes, Ventas de 90 días e Inventario.
     */
    suspend fun ejecutarSincronizacionMaestra(uid: String, esAdmin: Boolean) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🛡️ Iniciando Sincronización de Blindaje (Local-First) para UID: $uid")

            // 1. SINCRONIZAR CLIENTES (Prioridad Alta)
            clienteRepository.descargarClientesFirebase(context)

            // 2. SINCRONIZAR INVENTARIO
            inventarioRepository.descargarProductosFirebase(uid)

            // 3. SINCRONIZAR VENTAS (90 DÍAS)
            val cal = Calendar.getInstance()
            val hoy = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, -90)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            val noventaDiasAtras = cal.time

            // 🔥 CARGA PRIORITARIA DE DETALLES: Antes de la masiva, aseguramos detalles de hoy/ayer
            // Esto corrige el error donde el desglose por línea sale en 0 y la lentitud al abrir tickets
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                
                // Si es Admin, descargamos TODO lo de hoy de forma global para que el dashboard sea instantáneo.
                // Si es Vendedor, solo lo de su almacén/ruta para no saturar.
                val idParaSync = if (esAdmin) "" else uid
                val almid = if (esAdmin) "" else (userDoc.getString("ultimoAlmacenNombre") ?: "")
                
                Log.d(TAG, "🚀 Ejecutando descarga prioritaria (Admin=$esAdmin): Vendedor='$idParaSync', Almacén='$almid'")
                ventaRepository.descargarVentasDia(idParaSync, almid)
            } catch (e: Exception) {
                Log.w(TAG, "Error en carga prioritaria de detalles hoy: ${e.message}")
            }

            // Bajamos todos los encabezados de ventas de la empresa de los últimos 90 días
            val ventasSnap = db.collection("ventas")
                .whereGreaterThanOrEqualTo("fecha", com.google.firebase.Timestamp(noventaDiasAtras))
                .get().await()

            Log.i(TAG, "📦 Procesando ${ventasSnap.size()} ventas para blindaje local...")

            // 🔥 MEJORA OFFLINE-FIRST: Procesamos cada documento para asegurar que bajen los detalles (productos)
            // Esto garantiza que el celular tenga TODA la info lista para trabajar sin internet.
            coroutineScope {
                ventasSnap.documents.forEach { doc ->
                    launch {
                        try {
                            ventaRepository.procesarDocumentoVenta(doc, db, doc.getString("vendedorId") ?: "")
                        } catch (e: Exception) {
                            Log.w(TAG, "Error procesando venta ${doc.id} en sync: ${e.message}")
                        }
                    }
                }
            }
            
            // 4. SINCRONIZAR GASTOS
            gastoRepository.descargarGastosPeriodo(uid, noventaDiasAtras.time, System.currentTimeMillis())

            // 5. SINCRONIZAR ÓRDENES DE TRANSFERENCIA (2 SEMANAS LUNES-DOMINGO)
            try {
                val calSync = Calendar.getInstance(Locale("es", "MX"))
                calSync.firstDayOfWeek = Calendar.MONDAY
                calSync.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calSync.set(Calendar.HOUR_OF_DAY, 0); calSync.set(Calendar.MINUTE, 0); calSync.set(Calendar.SECOND, 0)
                if (calSync.timeInMillis > System.currentTimeMillis()) calSync.add(Calendar.DAY_OF_YEAR, -7)
                calSync.add(Calendar.DAY_OF_YEAR, -7) // Dos semanas atrás
                val inicioSync = calSync.timeInMillis
                
                // Hasta el domingo de esta semana
                calSync.add(Calendar.DAY_OF_YEAR, 13)
                calSync.set(Calendar.HOUR_OF_DAY, 23); calSync.set(Calendar.MINUTE, 59)
                val finSync = calSync.timeInMillis
                
                inventarioRepository.sincronizarOrdenesPeriodo(inicioSync, finSync)
                inventarioRepository.sincronizarMovimientosPeriodo(inicioSync, finSync)
            } catch (e: Exception) {
                Log.e(TAG, "Error sincronizando órdenes/movimientos en sync maestro: ${e.message}")
            }

            Log.d(TAG, "✅ Blindaje Completado. El dispositivo está listo para operar 100% Offline.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en Sincronización de Blindaje: ${e.message}")
        }
    }
}
