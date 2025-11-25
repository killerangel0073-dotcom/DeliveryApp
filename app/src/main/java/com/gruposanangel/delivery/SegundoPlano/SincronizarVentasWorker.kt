package com.gruposanangel.delivery.SegundoPlano

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaRepository
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.ui.screens.VistaModeloVenta
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class SincronizarVentasWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val ventaRepo = VentaRepository(db.VentaDao())
        val inventarioRepo = RepositoryInventario(db.productoDao())
        val vistaModelo = VistaModeloVenta(inventarioRepo, ventaRepo)

        return try {
            val uidVendedor = FirebaseAuth.getInstance().currentUser?.uid
            if (uidVendedor.isNullOrEmpty()) return Result.retry()

            val pendientes = ventaRepo.obtenerVentasPendientes()
            if (pendientes.isEmpty()) return Result.success()

            val almacenId = inventarioRepo.getAlmacenVendedor(uidVendedor)
                ?: return Result.retry()

            pendientes.forEach { venta ->
                val detalles = ventaRepo.obtenerDetallesDeVenta(venta.id)
                val productos = detalles.map {
                    Plantilla_Producto(
                        id = it.productoId,
                        nombre = it.nombre,
                        precio = it.precio,
                        cantidad = it.cantidad
                    )
                }

                // Llamada suspend al servidor de manera segura
                val (exitoServidor, mensaje) = try {
                    vistaModelo.guardarVentaEnServidorSuspend(
                        ventaLocalId = venta.id,
                        clienteId = venta.clienteId,
                        clienteNombre = venta.clienteNombre,
                        productos = productos,
                        metodoPago = venta.metodoPago,
                        vendedorId = venta.vendedorId,
                        almacenVendedorId = almacenId
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    Pair(false, e.message ?: "Error desconocido")
                }

                if (exitoServidor) {
                    val firestoreId = try { JSONObject(mensaje).optString("ventaId", null) } catch (e: Exception) { null }
                    if (!firestoreId.isNullOrEmpty()) {
                        ventaRepo.marcarVentaConFirestoreId(venta.id, firestoreId)
                        Log.d("SincronizarVentas", "Venta ${venta.id} sincronizada con ID $firestoreId")
                    } else {
                        Log.e("SincronizarVentas", "Venta ${venta.id} sincronizada pero no se recibió FirestoreId")
                    }
                } else {
                    Log.e("SincronizarVentas", "Error sincronizando venta ${venta.id}: $mensaje")
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}


