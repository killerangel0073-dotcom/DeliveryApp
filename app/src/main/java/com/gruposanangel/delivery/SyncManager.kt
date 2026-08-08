package com.gruposanangel.delivery

import android.content.Context
import android.util.Log
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.RepositoryGasto
import com.gruposanangel.delivery.data.VentaEntity
import com.gruposanangel.delivery.data.RepositoryInventario
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
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

            // Mapeo de Clientes para atribución local de rutas y FOTOGRAFÍAS
            val clientesLocal = clienteRepository.obtenerClientesLocalNoFlow()
            val clientToRutaMap = clientesLocal.associate { it.id to (it.rutaId ?: "") }
            val clientToFotoMap = clientesLocal.associate { it.id to (it.fotografiaUrl ?: "") }

            val entities = ventasSnap.documents.map { doc ->
                val vClienteId = doc.getString("clienteId") ?: ""
                val vRutaId = doc.getString("rutaId") ?: clientToRutaMap[vClienteId] ?: ""
                val vFoto = doc.getString("clienteImagenUrl") ?: clientToFotoMap[vClienteId] ?: ""
                
                val fRaw = doc.get("fecha")
                val fechaMs = when(fRaw) {
                    is com.google.firebase.Timestamp -> fRaw.toDate().time
                    is Number -> fRaw.toLong()
                    else -> System.currentTimeMillis()
                }

                VentaEntity(
                    id = doc.id,
                    clienteId = vClienteId,
                    clienteNombre = doc.getString("clienteNombre") ?: "Cliente",
                    clienteImagenUrl = vFoto, // 🔥 Aseguramos que la foto se guarde desde el inicio
                    total = (doc.get("total") as? Number)?.toDouble() ?: 0.0,
                    metodoPago = doc.getString("metodoPago") ?: "Efectivo",
                    vendedorId = doc.getString("vendedorId") ?: "",
                    vendedorNombre = doc.getString("vendedorNombre"),
                    almacenId = doc.getString("almacenId") ?: doc.getString("almacenVendedorId"),
                    rutaId = vRutaId,
                    rutaNombre = doc.getString("rutaNombre") ?: vRutaId,
                    fecha = fechaMs,
                    horaDispositivo = doc.getLong("horaDispositivo") ?: fechaMs,
                    horaVerificada = doc.getLong("horaVerificada") ?: fechaMs,
                    alertaTiempo = doc.getBoolean("alertaTiempo") ?: false,
                    latitudVenta = doc.getDouble("latitudVenta") ?: 0.0,
                    longitudVenta = doc.getDouble("longitudVenta") ?: 0.0,
                    fueraDeRango = doc.getBoolean("fueraDeRango") ?: false,
                    fotoEvidenciaVisita = doc.getString("fotoEvidenciaVisita"),
                    sincronizado = true,
                    firestoreId = doc.id,
                    estado = doc.getString("estado") ?: "pagada",
                    motivoVisita = doc.getString("motivoVisita")
                )
            }
            // 🛡️ PROTECCIÓN DE DATOS: Usamos OnConflictStrategy.IGNORE (vía VentaDao)
            // para que los registros de 90 días no borren los detalles (productos)
            // de las ventas que ya tenemos completas en el teléfono.
            ventaRepository.insertarVentas(entities)
            
            // 4. SINCRONIZAR GASTOS
            gastoRepository.descargarGastosPeriodo(uid, noventaDiasAtras.time, System.currentTimeMillis())

            Log.d(TAG, "✅ Blindaje Completado. El dispositivo está listo para operar 100% Offline.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en Sincronización de Blindaje: ${e.message}")
        }
    }
}
