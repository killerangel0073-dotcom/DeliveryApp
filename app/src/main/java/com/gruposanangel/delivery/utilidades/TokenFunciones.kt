package com.gruposanangel.delivery.utilidades

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

// ----------------------------------------------------------
//  FcmUtils: UTILIDADES PARA TOKENS (NO ES UN SERVICIO FCM)
// ----------------------------------------------------------
object FcmUtils {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Guarda el token en Firestore dentro de un array para soportar
     * múltiples dispositivos por usuario.
     */
    suspend fun saveTokenToArray(uid: String, token: String) {
        try {
            db.collection("users").document(uid)
                .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
                .await()
            Log.d("FCM", "✅ Token [$token] agregado al array del usuario [$uid]")
        } catch (e: Exception) {
            Log.e("FCM", "❌ Error agregando token [$token] al usuario [$uid]", e)
        }
    }

    /**
     * Elimina el token del array (por ejemplo al cerrar sesión).
     */
    suspend fun removeTokenFromArray(uid: String, token: String) {
        try {
            db.collection("users").document(uid)
                .update("fcmTokens", FieldValue.arrayRemove(token))
                .await()
            Log.d("FCM", "🗑 Token [$token] eliminado del usuario [$uid]")
        } catch (e: Exception) {
            Log.e("FCM", "❌ Error eliminando token [$token] del usuario [$uid]", e)
        }
    }

    /**
     * Obtiene el token actual del dispositivo y lo sube a Firestore.
     */
    fun updateFcmToken(uid: String) {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e("FCM", "❌ No se pudo obtener el token")
                    return@addOnCompleteListener
                }

                val token = task.result
                val db = FirebaseFirestore.getInstance()
                db.collection("users").document(uid)
                    .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d("FCM", "✅ Token actualizado al iniciar sesión para el usuario [$uid]")
                    }
            }
        } catch (e: Exception) {
            Log.e("FCM", "⚠️ Error bypass: El dispositivo no soporta Firebase Messaging (Probable Huawei sin GMS)")
        }
    }
}
