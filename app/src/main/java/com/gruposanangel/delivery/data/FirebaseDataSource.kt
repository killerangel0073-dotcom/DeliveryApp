package com.gruposanangel.delivery.data

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

    suspend fun obtenerTokenSupervisor(): String? {
        val query = firestore.collection("users")
            .whereEqualTo("puestoTrabajo", "CEO1.1")
            .whereEqualTo("activo", true).get().await()
        val tokens = query.documents.firstOrNull()?.get("fcmTokens") as? List<*>
        return tokens?.firstOrNull() as? String
    }
}
