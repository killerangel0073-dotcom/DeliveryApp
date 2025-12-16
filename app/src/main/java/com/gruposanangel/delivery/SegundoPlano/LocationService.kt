package com.gruposanangel.delivery.SegundoPlano

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
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
import android.os.PowerManager
import android.location.Location


class LocationService : Service() {






    private var lastLocation: Location? = null

    val DISTANCIA_MINIMA = 5f // metros


    private var wakeLock: PowerManager.WakeLock? = null

    private val TAG = "LocationService"

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val firebaseUser get() = FirebaseAuth.getInstance().currentUser
    private val firestore = FirebaseFirestore.getInstance()
    private var rutaNombreCache: String? = null

    // Suavizado de velocidad
    private var velocidadFiltrada = 0f

    // Estado visible para UI
    val velocidadActual = mutableStateOf(0f)
    val alertaVelocidad = mutableStateOf<Float?>(null)

    // Estado alarma
    private var alarmaActiva = false

    private var mediaPlayer: MediaPlayer? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    private var lastSupervisorNotifyAt = 0L
    private val supervisorNotifyThrottleMs = 120_000L // 2 minutos

    private val CHANNEL_LOCATION = "location_channel_v1"
    private val CHANNEL_ALERTS = "velocidad_alert_channel_v1"

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // 1. Iniciar Foreground inmediatamente
        startForeground(1, buildNotification())
        adquirirWakeLock()

        // 2. Iniciar Location Updates inmediatamente (es el core)
        requestLocationUpdates()

        // 3. Iniciar Coroutines que SÍ dependen de la ruta o son asíncronas
        serviceScope.launch {
            // Precargar la ruta (una sola vez)
            precargarRutaSuspend()

            // Iniciar Heartbeat (que ya chequea por ruta/usuario en su loop)
            startHeartbeat()
        }

        // 4. (Opcional) Usar Watchdog de forma muy espaciada o eliminarlo
        programarWatchdog() // Si lo mantienes, hazlo para 15-30 min

        Log.d(TAG, "Service created - foreground started")
    }


    private fun startHeartbeat() {
        serviceScope.launch {
            while (isActive) {
                delay(30_000) // cada 30 segundos
                val user = firebaseUser
                val lastLoc = lastLocation
                if (user != null && lastLoc != null) {
                    val rutaNombre = rutaNombreCache
                    if (rutaNombre.isNullOrEmpty()) {
                        Log.w(TAG, "Heartbeat no subido: ruta aún no cargada")
                        continue
                    }


                    val locationData = mapOf(
                        "latitude" to lastLoc.latitude,
                        "longitude" to lastLoc.longitude,
                        "accuracy" to lastLoc.accuracy,
                        "speed" to velocidadActual.value,
                        "timestamp" to Timestamp.now()
                    )

                    firestore.collection("locations")
                        .document(rutaNombre)
                        .set(locationData, SetOptions.merge())
                        .addOnSuccessListener { Log.d(TAG, "Heartbeat subido") }
                        .addOnFailureListener { e -> Log.e(TAG, "Error heartbeat", e) }
                } else if (user == null) {
                    Log.w(TAG, "firebaseUser es null -> no se sube heartbeat")
                } else {
                    Log.w(TAG, "lastLocation es null -> no se sube heartbeat")
                }
            }
        }
    }





    // Lanzar alerta
    private fun lanzarAlertaExceso(velocidad: Float) {
        alertaVelocidad.value = velocidad
        reproducirAlarma()
        mostrarNotificacionLocal("⚠️ Exceso de velocidad", "Vas a ${velocidad.toInt()} km/h")

        val now = System.currentTimeMillis()
        if (now - lastSupervisorNotifyAt >= supervisorNotifyThrottleMs) {
            lastSupervisorNotifyAt = now
            serviceScope.launch {
                try {
                    val user = firebaseUser ?: run {
                        Log.w(TAG, "Usuario no autenticado, no se enviará notificación al supervisor")
                        return@launch
                    }
                    val rutaNombre = cargarRutaSuspend(user.uid)
                    if (rutaNombre.isNullOrEmpty()) {
                        Log.w(TAG, "Ruta aún no disponible -> no se envía heartbeat")
                        return@launch
                    }

                    val token = obtenerTokenSupervisor()
                    if (token.isNullOrEmpty()) {
                        Log.w(TAG, "Token supervisor no disponible, no se envía notificación")
                        return@launch
                    }
                    enviarNotificacionSupervisorVelocidad(token, velocidad.toInt(), rutaNombre, user.uid)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en coroutine de notificación al supervisor", e)
                }
            }
        } else {
            Log.d(TAG, "Notificación al supervisor throttled. Pasaron ${now - lastSupervisorNotifyAt} ms")
        }
    }

    private suspend fun cargarRutaSuspend(userId: String): String? {
        rutaNombreCache?.let { return it }

        return try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            val rutaRef = userDoc.getDocumentReference("rutaAsignada")
            if (rutaRef != null) {
                val rutaDoc = rutaRef.get().await()
                val nombreRuta = rutaDoc.getString("nombre")
                if (nombreRuta != null) rutaNombreCache = nombreRuta
                nombreRuta
            } else {
                Log.w(TAG, "Usuario no tiene rutaAsignada")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando ruta suspend", e)
            null
        }
    }




    private fun adquirirWakeLock() {
        try {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DeliveryApp::LocationWakeLock"
            )
            // opción: wakeLock?.acquire(timeout) para evitar quedarse eternamente si quieres
            wakeLock?.acquire()
            Log.d(TAG, "WakeLock adquirido")
        } catch (e: Exception) {
            Log.e(TAG, "Error adquiriendo WakeLock", e)
        }
    }

    private fun mostrarNotificacionLocal(titulo: String, mensaje: String) {
        createAlertsChannelIfNeeded()
        val noti = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Permiso POST_NOTIFICATIONS no concedido; no se muestra notificación local")
                return
            }
        }

        try {
            NotificationManagerCompat.from(this).notify(888, noti)
            Log.d(TAG, "Notificación local mostrada")
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando notificación local", e)
        }
    }

    private fun createAlertsChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.let {
                val existing = it.getNotificationChannel(CHANNEL_ALERTS)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ALERTS,
                        "Alertas de velocidad",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notificaciones de alertas por exceso de velocidad"
                        enableLights(true)
                        enableVibration(true)
                    }
                    it.createNotificationChannel(channel)
                    Log.d(TAG, "Canal de alertas creado")
                }
            }
        }
    }

    private suspend fun obtenerTokenSupervisor(): String? {
        return try {
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("puestoTrabajo", "CEO1.1")
                .whereEqualTo("activo", true)
                .get()
                .await()

            val supervisorDoc = querySnapshot.documents.firstOrNull()
            supervisorDoc?.get("fcmTokens")?.let { tokens ->
                if (tokens is List<*>) tokens.firstOrNull() as? String else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo token supervisor", e)
            null
        }
    }

    private fun enviarNotificacionSupervisorVelocidad(token: String, velocidad: Int, ruta: String, userId: String) {
        val client = OkHttpClient()
        val mensaje = "🚨 Exceso de velocidad detectado\n🛣 Ruta: $ruta\n⚡ Velocidad: ${velocidad} km/h"
        val json = JSONObject().apply {
            put("token", token)
            put("titulo", "Alerta de velocidad")
            put("mensaje", mensaje)
            put("imagen", "https://upload.wikimedia.org/wikipedia/commons/thumb/7/74/Dominos_pizza_logo.svg/768px-Dominos_pizza_logo.svg.png")
            put("click_action", "OPEN_MAPA")
            put("ventaId", "velocidad-$userId-${System.currentTimeMillis()}")
            put("ventaIdLong", 0)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://us-central1-appventas--san-angel.cloudfunctions.net/enviarNotificacion")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("Notificacion", "Error enviando notificación al supervisor", e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                Log.d("Notificacion", "Respuesta: ${response.body?.string()}")
            }
        })
    }

    private fun cargarRutaSiNoExiste(userId: String, callback: (String?) -> Unit) {
        if (rutaNombreCache != null) {
            callback(rutaNombreCache)
            return
        }
        firestore.collection("rutas")
            .whereEqualTo("vendedorAsignado", firestore.document("users/$userId"))
            .limit(1)
            .get()
            .addOnSuccessListener { q ->
                if (!q.isEmpty) rutaNombreCache = q.documents[0].getString("nombre")
                callback(rutaNombreCache)
            }
            .addOnFailureListener {
                Log.e(TAG, "Error obteniendo ruta", it)
                callback(null)
            }
    }



    // LOCATION UPDATES
    private fun crearLocationRequest(): LocationRequest {
        return LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1500L // 🔥 base Uber
        )
            .setMinUpdateIntervalMillis(1000L)  // recibir updates muy rápidos si hay cambios
            .setMaxUpdateDelayMillis(1500L)     // no esperar demasiado
            .setWaitForAccurateLocation(false)  // actualizaciones aunque GPS aún ajuste precisión
            .build()
    }


    private fun suavizarUbicacion(newLoc: Location): Location {
        val last = lastLocation ?: return newLoc
        val lat = last.latitude * 0.7 + newLoc.latitude * 0.3
        val lng = last.longitude * 0.7 + newLoc.longitude * 0.3
        val loc = Location(newLoc)
        loc.latitude = lat
        loc.longitude = lng
        loc.speed = newLoc.speed
        loc.accuracy = newLoc.accuracy
        return loc
    }





    private fun requestLocationUpdates() {
        val request = crearLocationRequest()

        if (
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "🔥 Uber-level location updates activos (1–2s)")
        }
    }


    private fun esUbicacionValida(location: Location): Boolean {
        if (!location.hasAccuracy()) return false
        if (location.accuracy > 100f) return false
        return true
    }






    // 🔥 Nueva función para precargar ruta
    private suspend fun precargarRutaSuspend(): String? {
        val user = firebaseUser
        if (user == null) {
            Log.w(TAG, "Usuario no autenticado -> no se precarga ruta")
            return null
        }

        return try {
            val ruta = cargarRutaSuspend(user.uid)
            if (ruta != null) {
                rutaNombreCache = ruta
                Log.d(TAG, "Ruta precargada: $ruta")
            } else {
                Log.w(TAG, "Ruta no encontrada en Firestore")
            }
            ruta
        } catch (e: Exception) {
            Log.e(TAG, "Error precargando ruta", e)
            null
        }
    }


    // Modificamos locationCallback para no subir si ruta no está lista
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {

                // 1️⃣ Tomamos una copia local de lastLocation para evitar problemas de smart cast
                val lastLoc = lastLocation

                // 2️⃣ Ignorar saltos GPS grandes (>100m)
                if (lastLoc != null && location.distanceTo(lastLoc) > 100) {
                    Log.w(TAG, "Salto GPS detectado: ${location.distanceTo(lastLoc)}m, ignorado")
                    continue
                }

                // 3️⃣ Filtrar ubicaciones inválidas
                if (!esUbicacionValida(location)) continue

                // 4️⃣ Ubicación suavizada para UI / mapa
                val locationParaMapa = suavizarUbicacion(location)

                // 5️⃣ Ubicación “real” para cálculos y Firebase
                val lastLocationAnterior = lastLoc
                val locationReal = location

                // 6️⃣ Calcular velocidad
                val speedKmh = calcularVelocidad(locationReal, lastLocationAnterior)

                // 7️⃣ Suavizar velocidad y actualizar estado
                val speedKmhSuavizado = filtrarVelocidad(speedKmh)
                velocidadActual.value = speedKmhSuavizado
                LocationState.updateVelocidad(speedKmhSuavizado)

                // 8️⃣ Actualizamos lastLocation “real”
                lastLocation = locationReal

                // 9️⃣ Alertas de velocidad
                if (speedKmhSuavizado > 70f && !alarmaActiva) {
                    alarmaActiva = true
                    lanzarAlertaExceso(speedKmhSuavizado)
                } else if (speedKmhSuavizado <= 68f && alarmaActiva) {
                    alarmaActiva = false
                    detenerAlarma()
                    alertaVelocidad.value = null
                }

                // 🔟 Subir ubicación a Firebase solo si hay distancia suficiente
                val ruta = rutaNombreCache
                val user = firebaseUser
                if (user != null && !ruta.isNullOrEmpty() && lastLocationAnterior != null) {
                    if (lastLocationAnterior.distanceTo(locationReal) >= DISTANCIA_MINIMA) {
                        subirUbicacion(locationReal, ruta)
                    }

                }
            }
        }
    }


    private fun calcularVelocidad(actual: Location, anterior: Location?): Float {
        if (anterior == null) return 0f
        val distancia = actual.distanceTo(anterior) // metros
        val tiempo = (actual.time - anterior.time) / 1000f // segundos
        if (tiempo <= 0f) return 0f
        val velocidadMps = distancia / tiempo
        return velocidadMps * 3.6f // km/h
    }





    private fun subirUbicacion(location: Location, ruta: String) {
        val locationData = mapOf(
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracy" to location.accuracy,
            "speed" to velocidadActual.value,
            "timestamp" to Timestamp.now()
        )

        firestore.collection("locations")
            .document(ruta)
            .set(locationData, SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "Ubicación subida para ruta $ruta") }
            .addOnFailureListener { e -> Log.e(TAG, "Error subiendo ubicación", e) }
    }





    private fun filtrarVelocidad(kmh: Float): Float {
        val alpha = 0.2f // más reactivo
        velocidadFiltrada = alpha * kmh + (1 - alpha) * velocidadFiltrada
        return velocidadFiltrada
    }


    private fun buildNotification(): Notification {
        createLocationChannelIfNeeded()
        val builder = NotificationCompat.Builder(this, CHANNEL_LOCATION)
            .setContentTitle("App Delisa")  // título vacío
            .setContentText("Funcionando Correctamente")   // texto vacío
            .setSmallIcon(R.drawable.ic_transparent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN) // prioridad mínima
            .setCategory(Notification.CATEGORY_SERVICE) // categoría servicio

        return builder.build()
    }


    private fun createLocationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.let {
                val existing = it.getNotificationChannel(CHANNEL_LOCATION)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_LOCATION,
                        "Rastreo de ubicación",
                        NotificationManager.IMPORTANCE_LOW // 👈 CAMBIO CLAVE
                    ).apply {
                        description = "Servicio de rastreo en segundo plano"
                        setSound(null, null)      // 🔇 NUNCA sonido
                        enableVibration(false)    // 🔕 sin vibración
                        enableLights(false)       // 💡 sin luces
                    }
                    it.createNotificationChannel(channel)
                    Log.d(TAG, "Canal de location creado SILENCIOSO")
                }
            }
        }
    }



    private fun reproducirAlarma() {
        try {
            if (mediaPlayer?.isPlaying == true) return
            val ctx = applicationContext
            mediaPlayer = MediaPlayer.create(ctx, R.raw.aaaeee)?.apply {
                isLooping = true
                start()
            }
            if (mediaPlayer == null) Log.e(TAG, "MediaPlayer.create devolvió null - revisa R.raw.aaaeee")
            else Log.d(TAG, "Alarma reproducida")
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir alarma", e)
        }
    }

    private fun detenerAlarma() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            mediaPlayer = null
            Log.d(TAG, "Alarma detenida")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo alarma", e)
        }
    }

    private fun programarWatchdog() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, WatchdogReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)

            // 👇 CAMBIAR AQUÍ: 15 (minutos) * 60 (segundos) * 1000 (ms)
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 15 * 60 * 1000L, // <--- AHORA CADA 15 MINUTOS
                pendingIntent
            )
            Log.d(TAG, "Watchdog programado (+15min)")
        } catch (e: Exception) {
            Log.e(TAG, "Error programando Watchdog", e)
        }
    }

    companion object {
        var isRunning = false
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_TEST_ALERT = "ACTION_TEST_ALERT"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true               // <-- reafirmar
        // refuerzo del blindaje
        startForeground(1, buildNotification())

        when (intent?.action) {
            ACTION_START -> {
                // Reafirmar estado y (re)iniciar cosas si es necesario
                adquirirWakeLock()
                requestLocationUpdates()
                programarWatchdog()
            }

            ACTION_STOP -> {
                try {
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al remover location updates", e)
                }
                detenerAlarma()
                velocidadActual.value = 0f
                LocationState.reset()
                stopForeground(true)
                stopSelf()
            }

            ACTION_TEST_ALERT -> {
                if (!alarmaActiva) {
                    alarmaActiva = true
                    lanzarAlertaExceso(70f)

                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error al remover location updates en onDestroy", e)
        }

        detenerAlarma()
        velocidadActual.value = 0f
        LocationState.reset()

        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
            Log.d(TAG, "WakeLock liberado")
        } catch (e: Exception) {
            Log.e(TAG, "Error liberando WakeLock", e)
        }

        stopForeground(true)
        serviceJob.cancel()
        Log.d(TAG, "Service destroyed, job cancelled")
    }
}
