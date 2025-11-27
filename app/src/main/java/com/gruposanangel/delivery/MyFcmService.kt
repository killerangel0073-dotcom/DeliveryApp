package com.gruposanangel.delivery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.gruposanangel.delivery.ui.screens.PantallaLoginPro // opcional, solo si quieres abrir login específicamente
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.URL
import java.util.*
import kotlin.coroutines.resumeWithException

import kotlin.coroutines.resume


class MyFcmService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFcmService"
        private const val CHANNEL_ID = "default_channel"
        private const val CHANNEL_NAME = "Notificaciones"
        private const val PREFS_NAME = "fcm_prefs"
        private const val PREF_TOKEN_KEY = "fcm_token"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Nuevo token: guardarlo en SharedPreferences y, si hay usuario autenticado, en Firestore.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token generado: $token")

        // Guardar localmente (para asociarlo al login si el usuario aún no está autenticado)
        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_TOKEN_KEY, token).apply()

        // Si hay usuario autenticado, guardar en Firestore (array de tokens)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            saveTokenToFirestore(uid, token)
        } else {
            Log.d(TAG, "No hay usuario autenticado: token guardado localmente y se subirá al login")
        }
    }

    /**
     * Mensajes entrantes (notification + data)
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(
            TAG,
            "Mensaje recibido: data=${remoteMessage.data}, notification=${remoteMessage.notification}"
        )

        // Priorizar notification().title / body si vienen; si no, leer de data
        val title = remoteMessage.notification?.title ?: remoteMessage.data["titulo"]
        ?: getString(R.string.app_name)
        val body = remoteMessage.notification?.body ?: remoteMessage.data["mensaje"] ?: ""

        // Mostrar notificación
        showNotification(title, body, remoteMessage.data)
    }


    /**
     * Construye y muestra la notificación. Añade extras si vienen datos.
     */
    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)

        if (data.isNotEmpty()) {
            val bundle = android.os.Bundle()
            for ((k, v) in data) bundle.putString(k, v)
            launchIntent.putExtras(bundle)
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(this, notificationId, launchIntent, pendingFlags)

        val smallIconRes = try {
            applicationInfo.icon
        } catch (e: Exception) {
            android.R.drawable.ic_dialog_info
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(smallIconRes)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        // 👉 Aquí mejoramos la notificación
        when {
            // Si en "data" viene una imagen → usar BigPictureStyle
            data["imagen"] != null -> {
                val imageUrl = data["imagen"]
                try {
                    val bitmap = BitmapFactory.decodeStream(URL(imageUrl).openStream())
                    builder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon(null as Bitmap?)

                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error cargando imagen de la notificación", e)
                }
            }

            // Si no hay imagen pero el texto es largo → usar BigTextStyle
            body.length > 60 -> {
                builder.setStyle(
                    NotificationCompat.BigTextStyle().bigText(body)
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Permiso POST_NOTIFICATIONS no concedido, no se mostrará la notificación")
            return
        }

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, builder.build())
        }

        Log.d(TAG, "Notificación mostrada (id=$notificationId): $title / $body")
    }





    /**
     * Crea el canal de notificaciones (Android O+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para notificaciones de la app"
                enableLights(true)
                enableVibration(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    /**
     * Guarda token en Firestore como array (evita duplicados con FieldValue.arrayUnion)
     */
    private fun saveTokenToFirestore(uid: String, token: String) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(uid)
        userRef.set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
            .addOnSuccessListener {
                Log.d(
                    TAG,
                    "Token guardado en Firestore correctamente para $uid"
                )
            }
            .addOnFailureListener { e -> Log.e(TAG, "Error guardando token en Firestore", e) }
    }








}





