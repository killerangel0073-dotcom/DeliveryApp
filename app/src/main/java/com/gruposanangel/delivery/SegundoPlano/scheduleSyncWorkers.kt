package com.gruposanangel.delivery.SegundoPlano

import android.content.Context
import androidx.work.*

fun scheduleSyncWorkers(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED) // Solo con internet
        .build()

    // 1. Worker de Clientes (Base de la cadena)
    val syncClientesRequest = OneTimeWorkRequestBuilder<SincronizarClientesWorker>()
        .setConstraints(constraints)
        .addTag("SyncChain")
        .build()

    // 2. Worker de Ventas (Dependiente de Clientes)
    val syncVentasRequest = OneTimeWorkRequestBuilder<SincronizarVentasWorker>()
        .setConstraints(constraints)
        .addTag("SyncChain")
        .build()

    // Encadenamiento: Primero Clientes, luego Ventas
    WorkManager.getInstance(context)
        .beginUniqueWork("SincronizacionTotal", ExistingWorkPolicy.KEEP, syncClientesRequest)
        .then(syncVentasRequest)
        .enqueue()

    // Mantener la programación periódica para mantenimiento de fondo
    val periodicClientes = PeriodicWorkRequestBuilder<SincronizarClientesWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()

    val periodicVentas = PeriodicWorkRequestBuilder<SincronizarVentasWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork("PeriodicClientes", ExistingPeriodicWorkPolicy.KEEP, periodicClientes)
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("PeriodicVentas", ExistingPeriodicWorkPolicy.KEEP, periodicVentas)
}