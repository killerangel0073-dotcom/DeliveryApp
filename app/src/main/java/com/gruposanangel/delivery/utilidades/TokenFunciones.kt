package com.gruposanangel.delivery.utilidades

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// --------------------------
// Objeto FcmUtils
// --------------------------
object FcmUtils {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Guarda el token en un array para manejar múltiples dispositivos por usuario.
     * FieldValue.arrayUnion evita duplicados automáticamente.
     */
    fun saveTokenToArray(uid: String, token: String) {
        db.collection("users").document(uid)
            .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
            .addOnSuccessListener {
                Log.d("FCM", "✅ Token [$token] agregado al array correctamente para usuario [$uid]")
            }
            .addOnFailureListener { e ->
                Log.e("FCM", "❌ Error agregando token [$token] al array para usuario [$uid]", e)
            }
    }


    fun removeTokenFromArray(uid: String, token: String) {
        db.collection("users").document(uid)
            .set(mapOf("fcmTokens" to FieldValue.arrayRemove(token)), SetOptions.merge())
            .addOnSuccessListener { Log.d("FCM", "✅ Token [$token] eliminado del array para usuario [$uid]") }
            .addOnFailureListener { e -> Log.e("FCM", "❌ Error eliminando token [$token] del array para usuario [$uid]", e) }

    }


    /**
     * Obtiene el token FCM actual del dispositivo y lo guarda en Firestore.
     */
    fun updateFcmToken(uid: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                saveTokenToArray(uid, token)
            } else {
                Log.e("FCM", "❌ No se pudo obtener el token")
            }
        }
    }
}

// --------------------------
// Servicio TokenFunciones
// --------------------------
class TokenFunciones : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "🔄 Nuevo token generado: $token")

        // Guardar token en SharedPreferences para usarlo después del login
        val prefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()

        // Si ya hay un usuario logueado, guardar directamente en Firestore
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        uid?.let {
            FcmUtils.saveTokenToArray(it, token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "📩 Mensaje recibido: ${remoteMessage.data}")

        remoteMessage.notification?.let {
            Log.d("FCM", "🔔 Notificación: ${it.title} - ${it.body}")
        }
    }
}
