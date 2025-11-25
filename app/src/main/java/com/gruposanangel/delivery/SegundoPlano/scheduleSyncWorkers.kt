package com.gruposanangel.delivery.SegundoPlano

import android.content.Context
import androidx.work.*

fun scheduleSyncWorkers(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED) // Solo con internet
        .build()

    val syncClientesRequest = PeriodicWorkRequestBuilder<SincronizarClientesWorker>(
        15, java.util.concurrent.TimeUnit.MINUTES
    ).setConstraints(constraints)
        .build()

    val syncVentasRequest = PeriodicWorkRequestBuilder<SincronizarVentasWorker>(
        15, java.util.concurrent.TimeUnit.MINUTES
    ).setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "SincronizarClientes",
        ExistingPeriodicWorkPolicy.KEEP,
        syncClientesRequest
    )

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "SincronizarVentas",
        ExistingPeriodicWorkPolicy.KEEP,
        syncVentasRequest
    )
}