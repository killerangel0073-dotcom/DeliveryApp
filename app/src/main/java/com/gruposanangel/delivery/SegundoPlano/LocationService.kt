package com.gruposanangel.delivery.SegundoPlano

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.utilidades.enviarNotificacion

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val firebaseUser get() = FirebaseAuth.getInstance().currentUser
    private val firestore = FirebaseFirestore.getInstance()  // 🔹 Firestore en lugar de Realtime Database
    private var rutaNombreCache: String? = null

    // variable para suavizado (mantenemos el nombre)
    private var velocidadFiltrada = 0f

    // Iniciar el servicio en primer plano
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForeground(1, buildNotification())
        requestLocationUpdates()
    }

    //
    private fun lanzarAlertaExceso(velocidad: Float) {

        // Avisar a la UI
        alertaVelocidad.value = velocidad

        // Reproducir sonido de alarma
        reproducirAlarma()

        // Notificación local
        mostrarNotificacionLocal(
            "⚠️ Exceso de velocidad",
            "Vas a ${velocidad.toInt()} km/h"
        )

        // Notificar supervisor (modifica el token)
        enviarNotificacionSupervisor(velocidad)
    }

    private fun reproducirAlarma() {
        try {
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            ringtone = android.media.RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun mostrarNotificacionLocal(titulo: String, mensaje: String) {
        val channelId = "velocidad_alert_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alertas de velocidad",
                NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val noti = NotificationCompat.Builder(this, channelId)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(888, noti)
    }

    private fun enviarNotificacionSupervisor(velocidad: Float) {
        val tokenSupervisor = "TOKEN_AQUI"

        enviarNotificacion(
            token = tokenSupervisor,
            titulo = "Alerta de velocidad",
            mensaje = "Un repartidor va a ${velocidad.toInt()} km/h",
            imagen = null
        )
    }

    // Cargar el nombre de la ruta si no está cargado
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
                if (!q.isEmpty) {
                    rutaNombreCache = q.documents[0].getString("nombre")
                }
                callback(rutaNombreCache)
            }
            .addOnFailureListener { callback(null) }
    }

    // Solicitar actualizaciones de ubicación
    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // 1 segundo
        )
            .setMinUpdateDistanceMeters(1f)   // Si se movió al menos 1m
            .build()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    // Callback para manejar las actualizaciones de ubicación
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            var anyValidLocation = false

            for (location in result.locations) {
                val lat = location.latitude
                val lng = location.longitude
                val accuracy = location.accuracy // en metros

                // Validar precisión del GPS (si es mala, saltamos esta location)
                if (accuracy > 18f) {
                    // No return: sólo salta a la siguiente location
                    continue
                }

                // Validar que la velocidad exista
                if (!location.hasSpeed()) {
                    // Si no tiene speed, no podemos fiarnos; saltamos
                    continue
                }

                // Si API >= O, validamos speedAccuracy; si no, lo omitimos
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val speedAcc = location.speedAccuracyMetersPerSecond
                    if (speedAcc > 1.5f) {
                        // inexacto para velocidad
                        continue
                    }
                }

                // Convertir m/s → km/h
                val speedMps = location.speed
                val speedKmh = (speedMps * 3.6f).coerceAtLeast(0f)

                // Suavizar velocidad
                val speedKmhSuavizado = filtrarVelocidad(speedKmh)

                // Actualizar velocidad para la UI
                velocidadActual.value = speedKmhSuavizado

                anyValidLocation = true

                // ENCENDIDO/APAGADO DE ALARMA usando la velocidad suavizada
                // Histeresis: se apaga a <=48 para evitar rebotes alrededor de 50
                if (speedKmhSuavizado > 50f && !alarmaActiva) {
                    alarmaActiva = true
                    lanzarAlertaExceso(speedKmhSuavizado.toFloat())
                } else if (speedKmhSuavizado <= 48f) {
                    // apagamos cuando baja lo suficiente
                    alarmaActiva = false
                }

                // Subir a Firestore solo si hay usuario logueado
                firebaseUser?.let { user ->
                    val locationData = mapOf(
                        "latitude" to lat,
                        "longitude" to lng,
                        "accuracy" to accuracy,
                        "speed" to speedKmh, // guardamos el valor sin suavizar si lo prefieres
                        "timestamp" to Timestamp.now() // ✅ Timestamp de Firestore
                    )

                    cargarRutaSiNoExiste(user.uid) { rutaNombre ->
                        if (rutaNombre != null) {
                            firestore.collection("locations")
                                .document(rutaNombre)
                                .set(locationData)
                        }
                    }
                }
            } // end for

            // Si no hubo ninguna location válida en este resultado, informar 0
            if (!anyValidLocation) {
                velocidadActual.value = 0f
            }
        }
    }

    private fun filtrarVelocidad(kmh: Float): Float {
        val alpha = 0.25f  // suavizado leve sin retraso
        velocidadFiltrada = alpha * kmh + (1 - alpha) * velocidadFiltrada
        return velocidadFiltrada
    }

    // Construir la notificación para el servicio en primer plano
    private fun buildNotification(): Notification {
        val channelId = "location_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Rastreo de ubicación",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Rastreo activo")
            .setContentText("La aplicación está rastreando la ubicación.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"

        const val ACTION_TEST_ALERT = "ACTION_TEST_ALERT"

        /** ⭐ Velocidad actual en km/h que leerá la UI */
        var velocidadActual = mutableStateOf(0f)
        /** ⭐ Aviso para la UI cuando se exceda velocidad */
        var alertaVelocidad = mutableStateOf<Float?>(null)

        var alarmaActiva = false
        var ringtone: android.media.Ringtone? = null

        fun detenerAlarma() {
            try {
                ringtone?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_START -> {
                startForeground(1, buildNotification())
                requestLocationUpdates()
            }
            ACTION_STOP -> {
                // Detener actualizaciones de ubicación
                fusedLocationClient.removeLocationUpdates(locationCallback)
                // Detener cualquier sonido
                detenerAlarma()
                // Actualizar velocidad en tiempo real para la UI
                velocidadActual.value = 0f
                // Detener el servicio en primer plano
                stopForeground(true)
                // Detener el servicio
                stopSelf()
            }
            ACTION_TEST_ALERT -> {
                lanzarAlertaExceso(50f) // Fuerza sonido, notificación y alerta real
            }
        }
        return START_NOT_STICKY
    }

    // Detener el servicio y las actualizaciones de ubicación
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        // Detener actualizaciones de ubicación
        fusedLocationClient.removeLocationUpdates(locationCallback)
        // Detener cualquier sonido
        detenerAlarma()
        // Actualizar velocidad en tiempo real para la UI
        velocidadActual.value = 0f
        // Detener el servicio en primer plano
        stopForeground(true)
    }
}
