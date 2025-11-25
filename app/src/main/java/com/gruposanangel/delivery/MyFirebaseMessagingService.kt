package com.gruposanangel.delivery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Siempre mostrar notificación, incluso si es data message
        val title = remoteMessage.data["titulo"] ?: remoteMessage.notification?.title ?: "Título"
        val message = remoteMessage.data["mensaje"] ?: remoteMessage.notification?.body ?: "Mensaje"
        showNotification(title, message)
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "default_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear canal para Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notificaciones",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para notificaciones de la app"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Cada notificación tiene un ID único
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Nuevo token generado: $token")

        // Guardar token en Firestore para el usuario actual
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val userRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.uid)

            userRef.update("fcmTokens", FieldValue.arrayUnion(token))
                .addOnSuccessListener {
                    Log.d("FCM", "Token guardado en Firestore correctamente")
                }
                .addOnFailureListener { e ->
                    Log.e("FCM", "Error guardando token en Firestore", e)
                }
        }
    }
}
