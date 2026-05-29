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
        val ventaRepo = VentaRepository(db.VentaDao())
        val firebaseDataSource = FirebaseDataSource()
        val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao())
        val repoUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())

        return try {
            // 🔥 1. Obtener UID de forma segura (Prioridad Room para modo Offline)
            val usuarioActual = repoUsuario.obtenerUsuarioActual()
            val uidVendedor = usuarioActual?.uid ?: FirebaseAuth.getInstance().currentUser?.uid

            if (uidVendedor.isNullOrEmpty()) {
                Log.w("SyncWorker", "No se encontró UID del vendedor")
                return Result.failure() // No reintentar si no hay usuario (error fatal de sesión)
            }

            // 🔥 2. Consulta Eficiente en Lote (Batch)
            val pendientes = ventaRepo.obtenerVentasPendientes()
            if (pendientes.isEmpty()) return Result.success()

            val almacenId = inventarioRepo.getAlmacenVendedor(uidVendedor)
                ?: return Result.retry() // Reintentar si el almacén no está cargado aún

            // 🔥 3. Subida en Ráfaga con Manejo de Intermitencia
            for (venta in pendientes) {
                // Detener si el Worker ha sido cancelado (p.ej. por pérdida de red si se configuró así)
                if (isStopped) return Result.retry()

                val detalles = ventaRepo.obtenerDetallesDeVenta(venta.id)
                val productos = detalles.map {
                    Plantilla_Producto(
                        id = it.productoId,
                        nombre = it.nombre,
                        precio = it.precio,
                        cantidad = it.cantidad
                    )
                }

                val (exitoServidor, mensaje) = try {
                    ventaRepo.sincronizarConServidor(
                        ventaLocalId = venta.id,
                        clienteId = venta.clienteId,
                        clienteNombre = venta.clienteNombre,
                        productos = productos,
                        metodoPago = venta.metodoPago,
                        vendedorId = venta.vendedorId,
                        almacenVendedorId = almacenId
                    )
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Error de red al sincronizar ticket ${venta.id}", e)
                    // Si hay un error de red o excepción inesperada, detenemos la ráfaga
                    // y pedimos reintento para las que faltan.
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
                    // Si el servidor responde con error (p.ej. 400), continuamos con la siguiente
                    // para no bloquear toda la cola por un ticket corrupto.
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Falla general en Worker", e)
            Result.retry()
        }
    }
}


