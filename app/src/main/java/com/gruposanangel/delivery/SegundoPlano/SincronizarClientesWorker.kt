package com.gruposanangel.delivery.SegundoPlano

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
            clienteRepo.sincronizarConFirebase()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}