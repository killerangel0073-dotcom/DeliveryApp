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

    suspend fun obtenerTokensPorDestino(destino: String): List<String> {
        return try {
            val tokens = mutableListOf<String>()
            // Limpiamos el destino (ej: "Vendedor Delisa R2" -> "Ruta 2 Delisa" o similar)
            val destinoLimpio = destino.replace("Vendedor ", "").trim()
            
            Log.d("NOTIF_SANGEL", "Buscando tokens para destino: $destinoLimpio")

            // 1. Buscar directamente en la colección de rutas por el nombre
            val rutasSnapshot = firestore.collection("rutas")
                .get()
                .await()
            
            var vendedorRef: com.google.firebase.firestore.DocumentReference? = null

            for (doc in rutasSnapshot.documents) {
                val nombreRuta = doc.getString("nombre") ?: ""
                val almacenRef = doc.getDocumentReference("almacenAsignado")
                
                // Si el nombre coincide o el ID del almacén coincide con el destino seleccionado
                if (nombreRuta.contains(destinoLimpio, true) || (almacenRef != null && almacenRef.id.contains(destinoLimpio, true))) {
                    vendedorRef = doc.getDocumentReference("vendedorAsignado")
                    Log.d("NOTIF_SANGEL", "Ruta detectada: $nombreRuta. Vendedor: ${vendedorRef?.id}")
                    break
                }
            }

            // 2. Si encontramos al vendedor, obtenemos sus tokens
            if (vendedorRef != null) {
                val userDoc = vendedorRef.get().await()
                val fcmTokensRaw = userDoc.get("fcmTokens") as? List<*>
                fcmTokensRaw?.forEach { if (it is String && it.isNotBlank()) tokens.add(it) }
            } else {
                // Fallback por si la relación de ruta falla: Buscar por nombre en usuarios
                val usersSnap = firestore.collection("users").whereEqualTo("activo", true).get().await()
                for (doc in usersSnap.documents) {
                    val nombre = doc.getString("nombre") ?: ""
                    if (nombre.contains(destinoLimpio, true)) {
                        val fcmTokensRaw = doc.get("fcmTokens") as? List<*>
                        fcmTokensRaw?.forEach { if (it is String && it.isNotBlank()) tokens.add(it) }
                    }
                }
            }
            
            val resultado = tokens.distinct()
            Log.d("NOTIF_SANGEL", "Dispositivos encontrados: ${resultado.size}")
            resultado
        } catch (e: Exception) {
            Log.e("NOTIF_SANGEL", "Error", e)
            emptyList()
        }
    }

    suspend fun obtenerListaAlmacenes(): List<String> {
        return try {
            val snapshot = firestore.collection("almacenes").get().await()
            snapshot.documents.map { it.id }.sorted()
        } catch (e: Exception) {
            Log.e("FirebaseDataSource", "Error obteniendo lista de almacenes", e)
            emptyList()
        }
    }

    suspend fun obtenerStockDanado(almacenNombre: String): Map<String, Int> {
        return try {
            val result = firestore.collection("inventarioDanado")
                .whereEqualTo("almacenNombre", almacenNombre)
                .get()
                .await()
            
            val stockMap = mutableMapOf<String, Int>()
            for (doc in result.documents) {
                val productoId = doc.getString("productoId")
                val cantidad = doc.getLong("cantidad")?.toInt() ?: 0
                if (productoId != null) stockMap[productoId] = cantidad
            }
            stockMap
        } catch (e: Exception) {
            Log.e("FirebaseDataSource", "Error obteniendo stock dañado", e)
            emptyMap()
        }
    }

    suspend fun obtenerStockGlobal(): Map<String, Int> {
        return try {
            val result = firestore.collection("inventarioStock").get().await()
            val globalStock = mutableMapOf<String, Int>()
            
            for (doc in result.documents) {
                // 🛡️ Búsqueda robusta del ID del producto
                val productoId = doc.getString("productoId") 
                    ?: doc.getDocumentReference("productoRef")?.id 
                    ?: doc.id.split("_")[0]
                
                val cantidad = doc.getLong("cantidad")?.toInt() ?: 0
                
                if (productoId.isNotEmpty()) {
                    globalStock[productoId] = (globalStock[productoId] ?: 0) + cantidad
                }
            }
            Log.d("FirebaseDataSource", "Stock Global calculado para ${globalStock.size} productos únicos")
            globalStock
        } catch (e: Exception) {
            Log.e("FirebaseDataSource", "Error stock global", e)
            emptyMap()
        }
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
