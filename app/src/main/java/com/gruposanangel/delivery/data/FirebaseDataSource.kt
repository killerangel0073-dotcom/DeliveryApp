package com.gruposanangel.delivery.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Fuente de datos centralizada para Firebase en Delisa Botanas.
 * Maneja la autenticación y la descarga de perfiles desde Firestore.
 */
class FirebaseDataSource {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun login(email: String, password: String): String {
        val result = auth.signInWithEmailAndPassword(email.trim(), password.trim()).await()
        return result.user?.uid ?: throw Exception("El inicio de sesión falló: UID nulo")
    }

    suspend fun descargarPerfilUsuario(uid: String): Map<String, Any>? {
        val document = firestore.collection("users").document(uid).get().await()
        return document.data
    }

    fun getUidActual(): String? = auth.currentUser?.uid

    fun cerrarSesion() {
        auth.signOut()
    }

    suspend fun obtenerProductosCatalog(): List<Map<String, Any>> {
        val snapshot = firestore.collection("producto").get().await()
        return snapshot.documents.mapNotNull { it.data?.plus("id" to it.id) }
    }

    suspend fun obtenerStockAlmacen(almacenNombre: String): Map<String, Int> {
        val result = firestore.collection("inventarioStock")
            .whereEqualTo("almacenNombre", almacenNombre)
            .get()
            .await()
        
        val stockMap = mutableMapOf<String, Int>()
        for (doc in result.documents) {
            val productoRef = doc.getDocumentReference("productoRef")
            val cantidad = doc.getLong("cantidad")?.toInt() ?: 0
            productoRef?.id?.let { stockMap[it] = cantidad }
        }
        return stockMap
    }

    suspend fun crearOrdenTransferencia(ordenData: Map<String, Any>): String {
        val docRef = firestore.collection("ordenesTransferencia").document()
        docRef.set(ordenData).await()
        return docRef.id
    }

    suspend fun obtenerTokensDirectivos(): List<String> {
        return try {
            val puestos = listOf("CEO1.1", "Gerente General")
            val tokens = mutableListOf<String>()
            
            for (puesto in puestos) {
                val snapshot = firestore.collection("users")
                    .whereEqualTo("puestoTrabajo", puesto)
                    .whereEqualTo("activo", true)
                    .get()
                    .await()
                
                snapshot.documents.forEach { doc ->
                    val fcmTokensRaw = doc.get("fcmTokens") as? List<*>
                    fcmTokensRaw?.forEach { item ->
                        when (item) {
                            is String -> if (item.isNotBlank()) tokens.add(item)
                            is Map<*, *> -> {
                                val t = item["token"] as? String
                                if (!t.isNullOrBlank()) tokens.add(t)
                            }
                        }
                    }
                }
            }
            tokens.distinct()
        } catch (e: Exception) {
            Log.e("FirebaseDataSource", "Error obteniendo tokens", e)
            emptyList()
        }
    }
}
