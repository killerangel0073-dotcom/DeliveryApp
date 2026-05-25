package com.gruposanangel.delivery.SegundoPlano

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryCliente

class SincronizarClientesWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val clienteRepo = RepositoryCliente(db.clienteDao())

        return try {
            val exito = clienteRepo.sincronizarConFirebase(applicationContext)
            if (exito) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error fatal en sincronización", e)
            Result.retry()
        }
    }
}