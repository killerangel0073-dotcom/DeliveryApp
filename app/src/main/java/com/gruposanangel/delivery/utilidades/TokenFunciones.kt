package com.gruposanangel.delivery.utilidades

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

// ----------------------------------------------------------
//  FcmUtils: UTILIDADES PARA TOKENS (NO ES UN SERVICIO FCM)
// ----------------------------------------------------------
object FcmUtils {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Guarda el token en Firestore dentro de un array para soportar
     * múltiples dispositivos por usuario.
     */
    fun saveTokenToArray(uid: String, token: String) {
        db.collection("users").document(uid)
            .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
            .addOnSuccessListener {
                Log.d("FCM", "✅ Token [$token] agregado al array del usuario [$uid]")
            }
            .addOnFailureListener { e ->
                Log.e("FCM", "❌ Error agregando token [$token] al usuario [$uid]", e)
            }
    }

    /**
     * Elimina el token del array (por ejemplo al cerrar sesión).
     */
    fun removeTokenFromArray(uid: String, token: String) {
        db.collection("users").document(uid)
            .set(mapOf("fcmTokens" to FieldValue.arrayRemove(token)), SetOptions.merge())
            .addOnSuccessListener {
                Log.d("FCM", "🗑 Token [$token] eliminado del usuario [$uid]")
            }
            .addOnFailureListener { e ->
                Log.e("FCM", "❌ Error eliminando token [$token] del usuario [$uid]", e)
            }
    }

    /**
     * Obtiene el token actual del dispositivo y lo sube a Firestore.
     */
    fun updateFcmToken(uid: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e("FCM", "❌ No se pudo obtener el token")
                return@addOnCompleteListener
            }

            val token = task.result
            saveTokenToArray(uid, token)
        }
    }
}
