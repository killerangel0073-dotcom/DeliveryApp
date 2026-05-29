package com.gruposanangel.delivery.SegundoPlano

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryLocation
import java.io.IOException
import com.gruposanangel.delivery.SegundoPlano.LocationState
import kotlin.coroutines.coroutineContext





object LocationConfig {
    const val LIMITE_VELOCIDAD = 70f
    const val MARGEN_APAGADO_ALERTA = 68f
    const val VELOCIDAD_MAX_SALTO = 250f
    const val DISTANCIA_MAX_SALTO = 2000f
    const val WAKELOCK_TIMEOUT = 10 * 60 * 1000L // 10 minutos
    const val NOTIFY_THROTTLE_MS = 120_000L
}

class LocationService : Service() {





    private var batteryReceiverRegistered = false
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
    private lateinit var repoLocation: RepositoryLocation
    private var rutaNombreCache: String? = null

    private var velocidadFiltrada = 0f
    private var velocidadFiltradaUI = 0f
    val velocidadActual = mutableStateOf(0f)


    // ===============================
// DOBLE VELOCIDAD (UI vs LÓGICA)
// ===============================

    // UI: más sensible, más fluida
    private var velocidadUIInterna = 0f
    val velocidadUI = mutableStateOf(0f)

    // Lógica: más estable (alertas, Firestore)
    private var velocidadLogicaInterna = 0f
    val velocidadLogica = mutableStateOf(0f)



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
        
        val db = AppDatabase.getDatabase(this)
        repoLocation = RepositoryLocation(db.locationDao())

        if (!tienePermisosUbicacion()) {
            Log.e(TAG, "❌ Sin permisos de ubicación. Cerrando servicio.")
            stopSelf()
            return
        }

        // 1. Centralizamos la creación de canales de una sola vez
        createLocationChannelIfNeeded()
        createAlertsChannelIfNeeded()

        startForeground(1, buildNotification())
        adquirirWakeLock()
        requestLocationUpdates()
        registrarBatteryReceiver()
        iniciarWatchdog()

        serviceScope.launch {
            precargarRutaSuspend()
            // Iniciamos el motor único de sincronización
            iniciarLoopDeSincronizacionUnificado()
        }
    }

    // ==========================================
    // MOTOR ÚNICO DE SINCRONIZACIÓN (OPTIMIZADO)
    // ==========================================
    private suspend fun iniciarLoopDeSincronizacionUnificado() {
        while (coroutineContext.isActive) {
            delay(1000) // Tick de revisión cada segundo

            val loc = lastLocation ?: continue
            val user = firebaseUser ?: continue
            val ruta = rutaNombreCache ?: cargarRutaSuspend(user.uid) ?: continue

            val now = System.currentTimeMillis()
            val tiempoSinSubir = now - lastUploadTimestamp
            
            // Calculamos distancia desde el último punto sincronizado
            val distanciaDesdeUltimaSubida = lastUploadedLocation?.distanceTo(loc) ?: Float.MAX_VALUE
            
            val velocidad = velocidadActual.value
            val statusActual = if (velocidad > 1.5f) "MOVING" else "ONLINE"

            // 1. Condición de MOVIMIENTO: > 7 metros y han pasado al menos 5 segundos
            val debeSubirPorMovimiento = distanciaDesdeUltimaSubida >= DISTANCIA_MINIMA && 
                                        tiempoSinSubir >= INTERVALO_SUBIDA_MOVIMIENTO_MS

            // 2. Condición de TIEMPO (HEARTBEAT): > 30 segundos (esté quieto o no)
            val debeSubirPorTiempo = tiempoSinSubir >= INTERVALO_MAX_QUIETO_MS

            if (debeSubirPorMovimiento || debeSubirPorTiempo) {
                repoLocation.guardarUbicacion(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    accuracy = loc.accuracy,
                    speed = velocidad,
                    battery = BatteryState.state.value.level,
                    ruta = ruta,
                    status = statusActual
                )
                
                // Intentar vaciar cola de Room si hay internet
                repoLocation.sincronizarPendientes()
                
                lastUploadTimestamp = now
                lastUploadedLocation = Location(loc)
                
                Log.d(TAG, "📡 Sincronización procesada ($ruta). Motivo: ${if(debeSubirPorMovimiento) "Movimiento" else "Heartbeat/Tiempo"}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!tienePermisosUbicacion()) {
            Log.e(TAG, "❌ Sin permisos en onStartCommand")
            stopSelf()
            return START_NOT_STICKY
        }
        
        startForeground(1, buildNotification())
        isRunning = true

        if (intent == null || intent.action == null) {
            Log.d(TAG, "Reinicio por el sistema detectado (Sticky)")
            adquirirWakeLock()
            requestLocationUpdates()
            iniciarWatchdog()
        } else {
            when (intent.action) {
                ACTION_START -> {
                    adquirirWakeLock()
                    requestLocationUpdates()
                }
                ACTION_STOP -> {
                    detenerTodo()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
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

    private fun tienePermisosUbicacion(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return fine && background
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
        // Configuramos para que SEA AGRESIVO buscando GPS real, no caché
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMaxUpdateDelayMillis(5000L)
            .setWaitForAccurateLocation(true) // 🔥 CLAVE: No acepta lo primero que encuentre
            .setMinUpdateDistanceMeters(0f)
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



    private fun iniciarWatchdog() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, WatchdogReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + 20 * 60 * 1000L

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
                if (alarmManager.canScheduleExactAlarms()) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAt, pendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                } else {
                    Log.w("Watchdog", "No se puede programar alarma exacta: permiso no concedido")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
            Log.d("Watchdog", "Watchdog programado correctamente")
        } catch (se: SecurityException) {
            Log.e("Watchdog", "No se puede programar alarma exacta: ${se.message}")
        }
    }






    private fun procesarNuevaUbicacion(location: Location) {
        // --- 1. FILTRO DE ANTIGÜEDAD (ANTI-CACHÉ) ---
        val edadMs = System.currentTimeMillis() - location.time
        if (edadMs > 90_000) {
            Log.w(TAG, "Ignorada: Ubicación vieja detectada (Caché de la casa)")
            return
        }

        // --- 2. FILTRO DE PRECISIÓN ---
        if (!location.hasAccuracy() || location.accuracy > 80f) {
            Log.w(TAG, "Ignorada: Baja precisión (${location.accuracy}m)")
            return
        }

        // --- 3. FILTRO DE SALTOS IMPOSIBLES (CON AUTO-RESET) ---
        lastLocation?.let {
            val distancia = location.distanceTo(it)
            val deltaSegundos = (location.elapsedRealtimeNanos - it.elapsedRealtimeNanos) / 1_000_000_000f

            if (deltaSegundos > 0) {
                val velocidadCalculada = (distancia / deltaSegundos) * 3.6f

                // Si el salto es mayor a 250km/h Y la distancia es grande
                if (velocidadCalculada > 250f && distancia > 2000) {
                    Log.e(TAG, "⚠️ Salto detectado ($velocidadCalculada km/h). Reseteando caché para validar nueva zona.")

                    // 🔥 LA CLAVE: Borramos el pasado.
                    // Esto causa que el PRÓXIMO punto que llegue se acepte sin preguntas.
                    lastLocation = null
                    lastUploadedLocation = null
                    return // Ignoramos ESTE punto actual, pero el que viene en 1 segundo entrará OK.
                }
            }
        }

        // 🔥 AJUSTE: Log de éxito para saber exactamente cuándo el GPS está sano
        Log.i(TAG, "✅ GPS OK → Provider: ${location.provider} | Precisión: ${location.accuracy}m")

        // --- 4. CÁLCULO DE VELOCIDAD (VERSIÓN ROBUSTA) ---
        val speedKmhRaw = if (location.hasSpeed()) {
            location.speed * 3.6f
        } else {
            lastLocation?.let {
                val d = location.distanceTo(it)
                val t = (location.elapsedRealtimeNanos - it.elapsedRealtimeNanos) / 1_000_000_000f
                if (t > 0) (d / t) * 3.6f else 0f
            } ?: 0f
        }








        // Suavizado para la UI y Lógica
// ===============================
// 1️⃣ VELOCIDAD PARA UI (RESPONSIVA)
// ===============================
        velocidadUIInterna = 0.65f * speedKmhRaw + 0.35f * velocidadUIInterna
        val velocidadFinalUI = if (velocidadUIInterna < 0.8f) 0f else velocidadUIInterna

        velocidadUI.value = velocidadFinalUI
        LocationState.updateVelocidad(velocidadFinalUI)

// ===============================
// 2️⃣ VELOCIDAD LÓGICA (ESTABLE)
// ===============================
        velocidadLogicaInterna = 0.25f * speedKmhRaw + 0.75f * velocidadLogicaInterna
        val velocidadFinalLogica = if (velocidadLogicaInterna < 1.5f) 0f else velocidadLogicaInterna

        velocidadLogica.value = velocidadFinalLogica
        velocidadActual.value = velocidadFinalLogica // 🔒 Mantienes compatibilidad total






        // --- 5. ACTUALIZACIÓN DE REFERENCIA ÚNICA ---
        lastLocation = Location(location).apply {
            // Forzamos que el tiempo del objeto sea el tiempo actual del sistema
            time = System.currentTimeMillis()
        }



        // --- 6. AUTO-DISPARO DE ALERTA CON UMBRAL ---
        val limiteMaximo = 70f
        val margenApagado = 68f // 🚩 Umbral pequeño para que no "parpadee" la alarma

        if (velocidadActual.value > limiteMaximo) {
            if (!alarmaActiva) {
                alarmaActiva = true
                lanzarAlertaExceso(velocidadActual.value)
                Log.w(TAG, "🚨 ALERTA: Exceso detectado (>70 km/h)")
            }
        } else if (velocidadActual.value < margenApagado) {
            // Solo se apaga cuando baja de 68 km/h
            if (alarmaActiva) {
                alarmaActiva = false
                detenerAlarma()
                alertaVelocidad.value = null
                Log.i(TAG, "✅ Velocidad normalizada (<68 km/h)")
            }
        }


    }



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


    // Variable de clase (UNA SOLA INSTANCIA)
    private val httpClient = OkHttpClient()

    private fun enviarNotificacionSupervisorVelocidad(
        token: String,
        velocidad: Int,
        ruta: String,
        userId: String
    ) {
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

        // 🔥 AQUÍ ESTÁ LA CLAVE
        httpClient.newCall(request).enqueue(object : okhttp3.Callback {

            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, "Error red supervisor", e)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
            }
        })
    }


    // ==========================================
    // SISTEMA Y UTILIDADES
    // ==========================================

    private var vibrando = false // Control de estado para evitar saturar el hardware

    private fun gestionarVibracion(encender: Boolean) {
        // 🛡️ Blindaje: Si ya estamos en el estado deseado, no hacemos nada
        if (encender && vibrando) return
        if (!encender && !vibrando) return

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }

            if (encender) {
                vibrando = true
                val timings = longArrayOf(0, 500, 500) // Espera 0, Vibra 500, Espera 500

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Forzamos intensidad máxima (255)
                    val amplitudes = intArrayOf(0, 255, 0)
                    val effect = android.os.VibrationEffect.createWaveform(timings, amplitudes, 0) // 0 = repite
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timings, 0)
                }
                Log.d(TAG, "📳 Motor de vibración: ACTIVADO (Máxima potencia)")
            } else {
                vibrando = false
                vibrator.cancel()
                Log.d(TAG, "📳 Motor de vibración: APAGADO")
            }
        } catch (e: Exception) {
            vibrando = false // Reset en caso de error
            Log.e(TAG, "❌ Error en motor de vibración: ${e.message}")
        }
    }

    private fun reproducirAlarma() {
        try {
            // 1. Si ya está sonando, no hacemos nada
            if (mediaPlayer?.isPlaying == true) return

            // 2. Limpieza previa por seguridad
            detenerAlarma()

            // 🔥 ENCENDER VIBRACIÓN
            gestionarVibracion(true)

            // 3. Crear e iniciar
            mediaPlayer = MediaPlayer.create(applicationContext, R.raw.aaaeee)?.apply {

                // 🔴 MANEJO DE ERROR AQUÍ
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    detenerAlarma()
                    true // true = error manejado, evita crash
                }

                isLooping = true
                setVolume(1.0f, 1.0f)
                start()
            }

            Log.d(TAG, "Alarma iniciada correctamente")

        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir sonido: ${e.message}")
        }
    }



    private fun detenerAlarma() {
        try {

            // 🔥 APAGAR VIBRACIÓN
            gestionarVibracion(false)

            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al liberar MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }
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






    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "Cierre por swipe detectado. Activando Watchdog de emergencia.")

        val intent = Intent(applicationContext, WatchdogReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 5_000,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 5_000,
                pendingIntent
            )
        }

        super.onTaskRemoved(rootIntent)
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