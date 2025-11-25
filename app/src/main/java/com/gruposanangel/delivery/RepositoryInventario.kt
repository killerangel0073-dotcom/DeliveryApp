package com.gruposanangel.delivery.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class RepositoryInventario(private val productoDao: ProductoDao) {

    private val firestore = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null
    private val TAG = "InventarioRepository"

    // Scope único para todas las operaciones de Firebase
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Guardar productos localmente en Room.
     */
    suspend fun guardarLocal(productos: List<ProductoEntity>) {
        try {
            productoDao.insertAll(productos)
            Log.d(TAG, "guardarLocal: ${productos.size} productos guardados localmente")
        } catch (e: Exception) {
            Log.e(TAG, "guardarLocal: error guardando productos localmente", e)
        }
    }

    /**
     * Descargar productos desde Firebase y guardarlos localmente.
     * Esto reemplaza los productos locales con los de Firebase.
     */
    suspend fun descargarProductosFirebase() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid

        try {
            val almacenRef = obtenerAlmacenAsignado(uid) ?: return

            // 1️⃣ Consultar inventarioStock filtrando por almacenRef
            val inventarioSnap = firestore.collection("inventarioStock")
                .whereEqualTo("almacenRef", almacenRef)
                .get()
                .await()

            // 2️⃣ Mapear todos los productos en paralelo
            val productos = coroutineScope {
                inventarioSnap.documents.mapNotNull { invDoc ->
                    async {
                        runCatching {
                            val productoRef = invDoc.getDocumentReference("productoRef") ?: return@async null
                            val productoSnap = productoRef.get().await()
                            val productoData = productoSnap.data ?: return@async null

                            ProductoEntity(
                                id = invDoc.id,  // 🔹 ID de inventarioStock (clave primaria)
                                productoId = productoSnap.id, // 🔹 ID del producto referenciado
                                nombre = productoData["nombre"] as? String ?: "",
                                precio = invDoc.getDouble("precioUnitario") ?: 0.0,
                                cantidadDisponible = (invDoc.getLong("cantidad") ?: 0L).toInt(),
                                imagenUrl = productoData["imagenUrl"] as? String ?: "",
                                syncStatus = true
                            )

                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }


            // 3️⃣ Limpiar tabla antes de guardar los nuevos productos
            productoDao.clearAll()
            guardarLocal(productos)


        } catch (e: Exception) {
            Log.e(TAG, "descargarProductosFirebase: error descargando productos", e)
        }
    }

    /**
     * Escucha cambios de Firebase y mantiene la base local actualizada.
     */
    fun escucharCambiosFirebase(uid: String) {
        // Detener escucha previa si existe
        listenerRegistration?.remove()

        repositoryScope.launch {
            try {
                val almacenRef = obtenerAlmacenAsignado(uid) ?: return@launch

                listenerRegistration = firestore.collection("inventarioStock")
                    .whereEqualTo("almacenRef", almacenRef)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "escucharCambiosFirebase: error escuchando firestore", error)
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener

                        repositoryScope.launch {
                            try {
                                // 1️⃣ Obtener todos los productos locales
                                val localProductos: Map<String, ProductoEntity> =
                                    productoDao.getAllProductosFlow().first().associateBy { it.id }

                                // 2️⃣ Mapear documentos de Firebase a ProductoEntity
                                val productosRemotos = snapshot.documents.mapNotNull { doc ->
                                    runCatching {
                                        val productoRef = doc.getDocumentReference("productoRef") ?: return@mapNotNull null
                                        val productoSnap = productoRef.get().await()
                                        val productoData = productoSnap.data ?: return@mapNotNull null

                                        ProductoEntity(
                                            id = doc.id,  // 🔹 usar el ID del inventarioStock
                                            productoId = productoSnap.id,
                                            nombre = productoData["nombre"] as? String ?: "",
                                            precio = doc.getDouble("precioUnitario") ?: 0.0,
                                            cantidadDisponible = (doc.getLong("cantidad") ?: 0L).toInt(),
                                            imagenUrl = productoData["imagenUrl"] as? String ?: "",
                                            syncStatus = true
                                        )

                                    }.getOrNull()
                                }

                                // 3️⃣ Guardar o actualizar todos los productos remotos
                                productoDao.insertAll(productosRemotos)

                                // 4️⃣ Eliminar productos locales que ya no están en Firebase
                                val idsRemotos = productosRemotos.map { it.id }.toSet()
                                val idsLocalesParaBorrar = localProductos.keys - idsRemotos
                                if (idsLocalesParaBorrar.isNotEmpty()) {
                                    productoDao.deleteByIds(idsLocalesParaBorrar.toList())
                                    Log.d(TAG, "Productos eliminados localmente: $idsLocalesParaBorrar")
                                }

                            } catch (e: Exception) {
                                Log.e(TAG, "escucharCambiosFirebase: error procesando snapshot", e)
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "escucharCambiosFirebase: error inicializando listener", e)
            }
        }
    }

    suspend fun actualizarCantidadProducto(productoId: String, cantidadVendida: Int) {
        val producto = productoDao.getProductoById(productoId) ?: return
        val nuevaCantidad = (producto.cantidadDisponible - cantidadVendida).coerceAtLeast(0)
        productoDao.updateCantidadDisponible(producto.id, nuevaCantidad)
    }



    /**
     * Detener escucha de Firebase (evitar fugas de memoria)
     */
    fun stopEscuchaFirebase() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    /**
     * Obtener productos locales como Flow para ser observados en Compose
     */
    fun obtenerProductosLocal(): Flow<List<ProductoEntity>> =
        productoDao.getAllProductosFlow()

    /**
     * Función privada para obtener la referencia de almacen asignado del usuario
     */
    private suspend fun obtenerAlmacenAsignado(uid: String) : com.google.firebase.firestore.DocumentReference? {
        return try {
            val userDocs = firestore.collection("users")
                .whereEqualTo("uid", uid)
                .get()
                .await()
            val userDocSnap = userDocs.documents.firstOrNull() ?: return null
            val rutaRef = userDocSnap.getDocumentReference("rutaAsignada") ?: return null
            val rutaDocSnap = rutaRef.get().await()
            rutaDocSnap.getDocumentReference("almacenAsignado")
        } catch (e: Exception) {
            Log.e(TAG, "obtenerAlmacenAsignado: error obteniendo almacen", e)
            null
        }
    }


    // RepositoryInventario.kt
    suspend fun getAlmacenVendedor(uid: String): String? {
        val almacenRef = obtenerAlmacenAsignado(uid) ?: return null
        return almacenRef.id
    }

}
