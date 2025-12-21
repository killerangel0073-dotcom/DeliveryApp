package com.gruposanangel.delivery.SegundoPlano

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.location.Location
import android.os.BatteryManager
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.gruposanangel.delivery.R
import java.io.IOException
import com.gruposanangel.delivery.SegundoPlano.LocationState
import kotlin.coroutines.coroutineContext


class LocationService : Service() {




    private var batteryReceiverRegistered = false

    @Volatile private var ubicacionPendiente: Location? = null
    private var lastUploadedLocation: Location? = null // 🔥 Nueva: Controla el rastro real en Firestore
    private var lastUploadTimestamp: Long = 0

    private val DISTANCIA_MINIMA = 7f
    private val INTERVALO_SUBIDA_MOVIMIENTO_MS = 5000L


    private val INTERVALO_MAX_QUIETO_MS = 30_000L
// INTERVALO_SUBIDA_MS eliminado para evitar confusión


    private var lastLocation: Location? = null


    private var wakeLock: PowerManager.WakeLock? = null
    private val TAG = "LocationService"

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val firebaseUser get() = FirebaseAuth.getInstance().currentUser
    private val firestore = FirebaseFirestore.getInstance()
    private var rutaNombreCache: String? = null

    private var velocidadFiltrada = 0f
    private var velocidadFiltradaUI = 0f
    val velocidadActual = mutableStateOf(0f)
    val alertaVelocidad = mutableStateOf<Float?>(null)

    private var alarmaActiva = false
    private var mediaPlayer: MediaPlayer? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    private var lastSupervisorNotifyAt = 0L
    private val supervisorNotifyThrottleMs = 120_000L

    private val CHANNEL_LOCATION = "location_channel_v1"
    private val CHANNEL_ALERTS = "velocidad_alert_channel_v1"

    // ... (Tus variables y configuración de FusedLocationClient)
// En LocationService.kt

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        startForeground(1, buildNotification())
        adquirirWakeLock()
        requestLocationUpdates()
        registrarBatteryReceiver()

        // LANZAMIENTO ÚNICO DE MOTORES
        serviceScope.launch {
            precargarRutaSuspend()
            startHeartbeat() // 🔥 ACTIVAR: Es el que mantiene el "pulso" constante en Firestore
        }

        iniciarLoopDeSubida() // Sigue funcionando para reportes rápidos por movimiento
    }

    // ==========================================
// HEARTBEAT LIMPIO Y SUSPENDIDO
// ==========================================
    private suspend fun startHeartbeat() {
        while (coroutineContext.isActive) {
            delay(30_000)

            val user = firebaseUser
            val loc = lastLocation
            val ruta = rutaNombreCache
            val velocidad = velocidadActual.value // <-- Usamos tu variable filtrada

            if (user != null && loc != null && !ruta.isNullOrEmpty()) {
                // Decidimos el status según la realidad del movimiento
                val statusActual = if (velocidad > 1.5f) "MOVING" else "ONLINE"

                val data = mapOf(
                    "latitude" to loc.latitude,
                    "longitude" to loc.longitude,
                    "accuracy" to loc.accuracy,
                    "speed" to velocidad,
                    "timestamp" to Timestamp.now(),
                    "status" to statusActual // <-- Dinámico
                )

                firestore.collection("locations")
                    .document(ruta)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(TAG, "Heartbeat OK ($ruta) - Status: $statusActual")
                    }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        startForeground(1, buildNotification())

        when (intent?.action) {
            ACTION_START -> {
                adquirirWakeLock()
                requestLocationUpdates()
                Log.d(TAG, "Acción START recibida")
            }
            ACTION_STOP -> {
                detenerTodo()
                stopForeground(true)
                stopSelf()
            }
            ACTION_TEST_ALERT -> {
                if (!alarmaActiva) {
                    alarmaActiva = true
                    lanzarAlertaExceso(75f)
                }
            }
        }
        return START_STICKY
    }


    private fun registrarBatteryReceiver() {
        if (batteryReceiverRegistered) return

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
        batteryReceiverRegistered = true
    }


    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)

            val porcentaje = ((level * 100f) / scale).toInt()

            // ✅ CORRECTO: detecta cargando y batería llena
            val isCharging =
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

            val isPlugged = plugged != 0

            Log.d(
                "BATTERY_RECEIVER",
                "nivel=$porcentaje status=$status charging=$isCharging plugged=$isPlugged"
            )

            // ✅ UNA SOLA ACTUALIZACIÓN
            BatteryState.update(
                level = porcentaje,
                isCharging = isCharging,
                isPlugged = isPlugged
            )
        }
    }





    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L) // <--- 1 segundo exacto
            .setMinUpdateIntervalMillis(800L) // Permitir hasta 800ms
            .setMaxUpdateDelayMillis(1000L)
            .setWaitForAccurateLocation(false)
            .build()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            } catch (e: Exception) { Log.e(TAG, "Error GPS", e) }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { procesarNuevaUbicacion(it) }
        }
    }

    private fun procesarNuevaUbicacion(location: Location) {
        // --- FILTROS DE CALIDAD ---
        lastLocation?.let {
            if (location.distanceTo(it) > 80) return
        }
        if (!location.hasAccuracy() || location.accuracy > 80f) return

        // --- CÁLCULO DE VELOCIDAD CRUDA ---
        val speedKmhRaw = if (location.hasSpeed()) {
            location.speed * 3.6f
        } else {
            lastLocation?.let {
                (location.distanceTo(it) / ((location.time - it.time) / 1000f)) * 3.6f
            } ?: 0f
        }

        // --- FILTRO 1: VELOCIDAD PARA LA UI (ULTRA RÁPIDA) ---
        // Usamos alpha 0.8f para que la aguja reaccione de inmediato
        val alphaUI = 0.8f
        velocidadFiltradaUI = alphaUI * speedKmhRaw + (1 - alphaUI) * velocidadFiltradaUI
        val finalSpeedUI = if (velocidadFiltradaUI < 1.5f) 0f else velocidadFiltradaUI

        // Esta es la que va al velocímetro Tesla
        LocationState.updateVelocidad(finalSpeedUI)

        // --- FILTRO 2: VELOCIDAD PARA LOGICA/FIREBASE (ESTABLE) ---
        // Mantenemos tu alpha 0.3f original para reportes limpios
        val alphaLogic = 0.3f
        velocidadFiltrada = alphaLogic * speedKmhRaw + (1 - alphaLogic) * velocidadFiltrada
        val finalSpeedLogic = if (velocidadFiltrada < 1.5f) 0f else velocidadFiltrada

        // Esta se usa para el loop de subida a Firestore
        velocidadActual.value = finalSpeedLogic

        // --- MANEJO DE ALERTAS (Usamos la estable para evitar alarmas por ruido GPS) ---
        if (finalSpeedLogic > 70f && !alarmaActiva) {
            alarmaActiva = true
            lanzarAlertaExceso(finalSpeedLogic)
        } else if (finalSpeedLogic <= 68f && alarmaActiva) {
            alarmaActiva = false
            detenerAlarma()
            alertaVelocidad.value = null
        }

        // --- ACTUALIZACIÓN DE REFERENCIAS ---
        lastLocation = location
        ubicacionPendiente = location
    }

    private fun iniciarLoopDeSubida() {
        serviceScope.launch {
            while (isActive) {
                delay(1000) // Tick de revisión

                val loc = ubicacionPendiente ?: continue
                val user = firebaseUser ?: continue
                val ruta = rutaNombreCache ?: cargarRutaSuspend(user.uid)

                if (ruta.isNullOrEmpty()) continue // No bloqueamos con delays extra, el loop ya espera 1s

                val now = System.currentTimeMillis()
                val tiempoSinSubir = now - lastUploadTimestamp

                // Si es la primera vez, forzamos la subida usando Float.MAX_VALUE
                val distanciaDesdeUltimaSubida = lastUploadedLocation?.distanceTo(loc) ?: Float.MAX_VALUE

                // Lógica de decisión ultra clara
                val debeSubirPorMovimiento = distanciaDesdeUltimaSubida >= DISTANCIA_MINIMA &&
                        tiempoSinSubir >= INTERVALO_SUBIDA_MOVIMIENTO_MS

                val debeSubirPorTiempo = tiempoSinSubir >= INTERVALO_MAX_QUIETO_MS

                if (debeSubirPorMovimiento || debeSubirPorTiempo) {
                    subirUbicacion(loc, ruta, velocidadActual.value)

                    // 🔥 Actualizamos los marcadores de control
                    lastUploadTimestamp = now
                    lastUploadedLocation = loc

                    Log.d(TAG, "✅ Ubicación enviada a Firestore ($ruta). Motivo: ${if(debeSubirPorMovimiento) "Movimiento" else "Tiempo"}")
                }
            }
        }
    }


    private fun subirUbicacion(location: Location, ruta: String, speedKmh: Float) {
        val statusActual = if (speedKmh > 1.5f) "MOVING" else "ONLINE"
        val data = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracy" to location.accuracy,
            "speed" to speedKmh,
            "battery" to BatteryState.state.value.level, // ✅ CORRECTO
            "timestamp" to Timestamp.now(),
            "status" to statusActual
        )

        firestore.collection("locations")
            .document(ruta)
            .set(data, SetOptions.merge())
            .addOnFailureListener { e -> Log.e(TAG, "Error subiendo", e) }
    }


    // ==========================================
    // NOTIFICACIONES AL SUPERVISOR (RESTAURADA)
    // ==========================================

    private fun lanzarAlertaExceso(velocidad: Float) {
        alertaVelocidad.value = velocidad
        reproducirAlarma()
        mostrarNotificacionLocal("⚠️ Exceso de velocidad", "Vas a ${velocidad.toInt()} km/h")

        val now = System.currentTimeMillis()
        if (now - lastSupervisorNotifyAt >= supervisorNotifyThrottleMs) {
            lastSupervisorNotifyAt = now
            serviceScope.launch {
                try {
                    val user = firebaseUser ?: return@launch
                    val ruta = cargarRutaSuspend(user.uid) ?: return@launch
                    val token = obtenerTokenSupervisor() ?: return@launch
                    enviarNotificacionSupervisorVelocidad(token, velocidad.toInt(), ruta, user.uid)
                } catch (e: Exception) { Log.e(TAG, "Error Supervisor Alerta", e) }
            }
        }
    }

    private suspend fun obtenerTokenSupervisor(): String? {
        return try {
            val snap = firestore.collection("users")
                .whereEqualTo("puestoTrabajo", "CEO1.1")
                .whereEqualTo("activo", true)
                .get().await()

            val tokens = snap.documents.firstOrNull()?.get("fcmTokens") as? List<*>
            tokens?.firstOrNull() as? String
        } catch (e: Exception) { null }
    }

    private fun enviarNotificacionSupervisorVelocidad(token: String, velocidad: Int, ruta: String, userId: String) {
        val json = JSONObject().apply {
            put("token", token)
            put("titulo", "Alerta de velocidad")
            put("mensaje", "🚨 Exceso en $ruta: $velocidad km/h")
            put("imagen", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/74/Dominos_pizza_logo.svg/768px-Dominos_pizza_logo.svg.png")
            put("click_action", "OPEN_MAPA")
            put("ventaId", "velocidad-$userId-${System.currentTimeMillis()}")
        }
        val request = Request.Builder()
            .url("https://us-central1-appventas--san-angel.cloudfunctions.net/enviarNotificacion")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()
        OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) { Log.e(TAG, "Error red supervisor", e) }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
        })
    }

    // ==========================================
    // SISTEMA Y UTILIDADES
    // ==========================================

    private fun reproducirAlarma() {
        try {
            if (mediaPlayer?.isPlaying == true) return
            mediaPlayer = MediaPlayer.create(applicationContext, R.raw.aaaeee)?.apply {
                isLooping = true
                setVolume(1.0f, 1.0f)
                start()
            }
        } catch (e: Exception) { Log.e(TAG, "Media Error", e) }
    }

    private fun detenerAlarma() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {} finally { mediaPlayer = null }
    }

    private fun adquirirWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Delisa::WakeLock")

            // Sin parámetros = dura hasta que llames a release() en detenerTodo()
            wakeLock?.acquire()
            Log.d(TAG, "WakeLock: Protegiendo proceso indefinidamente")
        } catch (e: Exception) { Log.e(TAG, "WakeLock Error", e) }
    }

    private suspend fun precargarRutaSuspend(): String? {
        val user = firebaseUser ?: return null
        return cargarRutaSuspend(user.uid)
    }

    private suspend fun cargarRutaSuspend(userId: String): String? {
        if (rutaNombreCache != null) return rutaNombreCache
        return try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            val rutaRef = userDoc.getDocumentReference("rutaAsignada")

            if (rutaRef != null) {
                val snapshot = rutaRef.get().await()
                // Intentamos sacar el nombre, si no existe, usamos el ID del documento (ej. "Ruta 2")
                val nombre = snapshot.getString("nombre") ?: rutaRef.id
                rutaNombreCache = nombre
                Log.d(TAG, "Ruta detectada: $nombre")
                nombre
            } else {
                Log.e(TAG, "El usuario no tiene rutaAsignada en Firestore")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando ruta: ${e.message}")
            null
        }
    }

    private fun buildNotification(): Notification {
        createLocationChannelIfNeeded()
        return NotificationCompat.Builder(this, CHANNEL_LOCATION)
            .setContentTitle("App Delisa")
            .setContentText("Funcionando Correctamente")
            .setSmallIcon(R.drawable.ic_transparent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun mostrarNotificacionLocal(titulo: String, mensaje: String) {
        createAlertsChannelIfNeeded()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val noti = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(this).notify(888, noti)
    }

    private fun createLocationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm?.getNotificationChannel(CHANNEL_LOCATION) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_LOCATION, "Ubicación", NotificationManager.IMPORTANCE_LOW))
            }
        }
    }

    private fun createAlertsChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm?.getNotificationChannel(CHANNEL_ALERTS) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, "Alertas", NotificationManager.IMPORTANCE_HIGH))
            }
        }
    }

    private fun detenerTodo() {

        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver)
            batteryReceiverRegistered = false
        }

        isRunning = false
        try { fusedLocationClient.removeLocationUpdates(locationCallback) } catch (e: Exception) {}
        detenerAlarma()
        serviceJob.cancel()
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }




    override fun onDestroy() {
        super.onDestroy()
        detenerTodo()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var isRunning = false
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_TEST_ALERT = "ACTION_TEST_ALERT"
    }
}