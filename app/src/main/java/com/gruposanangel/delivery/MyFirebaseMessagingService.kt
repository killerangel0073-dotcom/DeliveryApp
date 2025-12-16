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
import android.os.Bundle
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
import java.net.URL

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MyFirebaseMessaging"
        private const val CHANNEL_ID = "default_channel"
        private const val CHANNEL_NAME = "Notificaciones"
        private const val PREFS_NAME = "fcm_prefs"
        private const val PREF_TOKEN_KEY = "fcm_token"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /* ============================================================
       CUANDO SE GENERA UN NUEVO TOKEN FCM
    ============================================================ */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo token generado = $token")

        val prefs: SharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_TOKEN_KEY, token).apply()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            saveTokenToFirestore(uid, token)
        }
    }

    /* ============================================================
       CUANDO LLEGA UNA NOTIFICACIÓN
    ============================================================ */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title =
            remoteMessage.notification?.title
                ?: remoteMessage.data["titulo"]
                ?: getString(R.string.app_name)

        val body =
            remoteMessage.notification?.body
                ?: remoteMessage.data["mensaje"]
                ?: ""

        showNotification(title, body, remoteMessage.data)
    }

    /* ============================================================
       MOSTRAR NOTIFICACIÓN
    ============================================================ */
    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        // Después:
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "OPEN_MAPA"  // solo la acción, no extras
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }



        val bundle = Bundle()
        for ((k, v) in data) bundle.putString(k, v)
        intent.putExtras(bundle)

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val pendingFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, intent, pendingFlags
        )

        val smallIconRes = applicationInfo.icon

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body.take(30))  // evita el recorte agresivo de Android
            .setSmallIcon(smallIconRes)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)


        val imageUrl = data["imagen"]

        if (!imageUrl.isNullOrEmpty()) {
            try {
                val bitmap = BitmapFactory.decodeStream(URL(imageUrl).openStream())
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setSummaryText("")  // importante: vacío
                )
                // AÑADIMOS TAMBIÉN BigTextStyle
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando imagen: $imageUrl", e)
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }




        // Permisos Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Permiso POST_NOTIFICATIONS no concedido.")
            return
        }

        NotificationManagerCompat.from(this).notify(notificationId, builder.build())
    }





    /* ============================================================
       CANAL PARA ANDROID 8+
    ============================================================ */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /* ============================================================
       GUARDAR TOKEN EN FIRESTORE
    ============================================================ */
    private fun saveTokenToFirestore(uid: String, token: String) {
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .set(
                mapOf("fcmTokens" to FieldValue.arrayUnion(token)),
                SetOptions.merge()
            )
            .addOnSuccessListener {
                Log.d(TAG, "Token guardado correctamente en Firestore.")
            }
            .addOnFailureListener {
                Log.e(TAG, "Error al guardar token en Firestore", it)
            }
    }
}
