package com.gruposanangel.delivery.SegundoPlano

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.model.Plantilla_Producto
import org.json.JSONObject

class SincronizarVentasWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val ventaRepo = VentaRepository(db.VentaDao(), db.productoDao())
        val firebaseDataSource = FirebaseDataSource()
        val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
        val repoUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())

        return try {
            // 🔥 1. Obtener UID de forma segura (Prioridad Room para modo Offline)
            val usuarioActual = repoUsuario.obtenerUsuarioActual()
            val uidVendedor = usuarioActual?.uid ?: FirebaseAuth.getInstance().currentUser?.uid

            if (uidVendedor.isNullOrEmpty()) {
                Log.w("SyncWorker", "No se encontró UID del vendedor")
                return Result.failure()
            }

            // 🔥 1.1. Sincronizar Ajustes de Inventario (NUEVO)
            val ajustesPendientes = db.movimientoInventarioDao().obtenerMovimientosPendientes()
            for (ajuste in ajustesPendientes) {
                if (isStopped) return Result.retry()
                try {
                    // Reutilizamos la lógica del repo para subir a Firestore
                    inventarioRepo.sincronizarMovimiento(ajuste)
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Error sincronizando ajuste ${ajuste.id}")
                }
            }

            // 🔥 2. Consulta Eficiente en Lote (Batch)
            val pendientes = ventaRepo.obtenerVentasPendientes()
            if (pendientes.isEmpty()) return Result.success()

            // 🔥 3. Subida en Ráfaga con Manejo de Intermitencia
            for (venta in pendientes) {
                // Detener si el Worker ha sido cancelado
                if (isStopped) return Result.retry()

                val detalles = ventaRepo.obtenerDetallesDeVenta(venta.id)
                val productos = detalles.map {
                    Plantilla_Producto(
                        id = it.productoId, // Ya está limpio desde guardarVentaLocal
                        nombre = it.nombre,
                        precio = it.precio,
                        cantidad = it.cantidad
                    )
                }

                // Usamos los metadatos persistidos en la venta
                val almacenId = venta.almacenId ?: ""
                val nombreVendedor = venta.vendedorNombre ?: "Vendedor"

                if (almacenId.isEmpty()) {
                    Log.e("SyncWorker", "Ticket ${venta.id} no tiene almacenId asignado. Saltando para evitar error en nube.")
                    continue
                }

                val (exitoServidor, mensaje) = try {
                    ventaRepo.sincronizarConServidor(
                        ventaLocalId = venta.id,
                        clienteId = venta.clienteId,
                        clienteNombre = venta.clienteNombre,
                        productos = productos,
                        metodoPago = venta.metodoPago,
                        vendedorId = venta.vendedorId,
                        vendedorNombre = nombreVendedor,
                        almacenVendedorId = almacenId,
                        fotoEvidenciaLocal = venta.fotoEvidenciaVisita,
                        fueraDeRango = venta.fueraDeRango,
                        latitudVenta = venta.latitudVenta,
                        longitudVenta = venta.longitudVenta,
                        fecha = venta.fecha // 🔥 Pasamos la hora original capturada
                    )
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Error de red al sincronizar ticket ${venta.id}", e)
                    return Result.retry()
                }

                if (exitoServidor) {
                    val firestoreId = try {
                        val json = JSONObject(mensaje)
                        json.optString("ventaId", "")
                    } catch (e: Exception) { "" }

                    if (firestoreId.isNotEmpty()) {
                        ventaRepo.marcarVentaConFirestoreId(venta.id, firestoreId)
                        Log.d("SyncWorker", "Ticket ${venta.id} sincronizado OK")
                    }
                } else {
                    Log.e("SyncWorker", "Servidor rechazó ticket ${venta.id}: $mensaje")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Falla general en Worker", e)
            Result.retry()
        }
    }
}


