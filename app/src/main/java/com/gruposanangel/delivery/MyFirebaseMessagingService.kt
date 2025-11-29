package com.gruposanangel.delivery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
        val title = remoteMessage.data["titulo"] ?: remoteMessage.notification?.title ?: "Título"
        val message = remoteMessage.data["mensaje"] ?: remoteMessage.notification?.body ?: "Mensaje"
        val clickAction = remoteMessage.data["click_action"]
        val ticketId = remoteMessage.data["ventaId"]?.toLongOrNull()

        val imagenUrl = remoteMessage.data["imagen"]     // <--- FOTO DEL CLIENTE

        Log.d("FCM", "📸 Imagen recibida desde FCM: $imagenUrl")

        // Si viene una imagen → Notificación con BigPicture
        if (!imagenUrl.isNullOrEmpty()) {
            mostrarNotificacionConImagen(
                title = title,
                message = message,
                clickAction = clickAction,
                ticketId = ticketId,
                urlImagen = imagenUrl
            )
        } else {
            // Notificación normal
            showNotification(title, message, clickAction, ticketId)
        }
    }

    /* =====================================================
       NOTIFICACIÓN SIN IMAGEN
    ====================================================== */
    private fun showNotification(
        title: String,
        message: String,
        clickAction: String?,
        ticketId: Long? = null
    ) {
        val channelId = "default_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notificaciones",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = buildIntent(clickAction, ticketId)

        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (pendingIntent != null) builder.setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    /* =====================================================
       NOTIFICACIÓN CON IMAGEN (BIG PICTURE)
    ====================================================== */
    private fun mostrarNotificacionConImagen(
        title: String,
        message: String,
        clickAction: String?,
        ticketId: Long?,
        urlImagen: String
    ) {
        Thread {
            try {
                // Descargar imagen desde URL
                val url = java.net.URL(urlImagen)
                val bitmap = android.graphics.BitmapFactory.decodeStream(url.openConnection().getInputStream())

                val channelId = "default_channel"
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        channelId,
                        "Notificaciones",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                    notificationManager.createNotificationChannel(channel)
                }

                val intent = buildIntent(clickAction, ticketId)

                val pendingIntent = intent?.let {
                    PendingIntent.getActivity(
                        this, 0, it,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }

                val builder = NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon(null as Bitmap?)

                    )
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)

                if (pendingIntent != null) builder.setContentIntent(pendingIntent)

                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())

            } catch (e: Exception) {
                e.printStackTrace()
                showNotification(title, message, clickAction, ticketId)
            }
        }.start()
    }

    /* =====================================================
       ARMADO DEL INTENT SEGÚN click_action
    ====================================================== */
    private fun buildIntent(clickAction: String?, ticketId: Long?): Intent? {
        return when (clickAction) {
            "OPEN_TICKET_DETAIL" -> {
                ticketId?.let {
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("ticketId", it)
                    }
                }
            }
            "OTRO_TIPO" -> {
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("openPantallaInicio", true)
                }
            }
            else -> null
        }
    }

    /* =====================================================
       GUARDADO DEL TOKEN FCM
    ====================================================== */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Nuevo token generado: $token")

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val userRef = FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)

        userRef.update("fcmTokens", FieldValue.arrayUnion(token))
            .addOnSuccessListener { Log.d("FCM", "Token guardado en Firestore") }
            .addOnFailureListener { Log.e("FCM", "Error guardando token", it) }
    }
}
