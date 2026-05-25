package com.gruposanangel.delivery.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Repositorio de Inventario refactorizado para Delisa Botanas.
 * Maneja el stock local y los movimientos de inventario (Cargas).
 */
class RepositoryInventario(
    private val firebaseDataSource: FirebaseDataSource,
    private val productoDao: ProductoDao,
    private val ventaDao: VentaDao
) {

    private val firestore = FirebaseFirestore.getInstance() // Se mantiene para compatibilidad de listeners complejos por ahora
    private var listenerRegistration: ListenerRegistration? = null
    private val TAG = "InventarioRepository"
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Obtiene los productos locales como Flow.
     */
    fun obtenerProductosLocal(): Flow<List<ProductoEntity>> = productoDao.getAllProductosFlow()

    /**
     * Descarga el catálogo de productos y lo guarda en Room.
     */
    suspend fun descargarCatalogoProductos() {
        try {
            val data = firebaseDataSource.obtenerProductosCatalog()
            val entities = data.map { map ->
                ProductoEntity(
                    id = map["id"] as String,
                    productoId = map["id"] as String,
                    nombre = map["nombre"] as? String ?: "",
                    precio = map["precio"] as? Double ?: 0.0,
                    cantidadDisponible = 0, // El catálogo no tiene stock por sí mismo
                    imagenUrl = map["imagenUrl"] as? String ?: "",
                    syncStatus = true
                )
            }
            // No limpiamos toda la tabla para no borrar el stock actual si lo hubiera
            productoDao.insertAll(entities)
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando catálogo", e)
        }
    }

    /**
     * Obtiene el stock de un almacén específico desde la nube.
     */
    suspend fun obtenerStockAlmacen(almacen: String): Map<String, Int> {
        return firebaseDataSource.obtenerStockAlmacen(almacen)
    }

    /**
     * Crea una orden de transferencia en la nube.
     */
    suspend fun crearOrdenTransferencia(ordenData: Map<String, Any>): String {
        return firebaseDataSource.crearOrdenTransferencia(ordenData)
    }

    /**
     * Actualiza el stock local después de una venta.
     */
    suspend fun actualizarCantidadProducto(productoId: String, cantidadVendida: Int) {
        val producto = productoDao.getProductoById(productoId) ?: return
        val nuevaCantidad = (producto.cantidadDisponible - cantidadVendida).coerceAtLeast(0)
        productoDao.updateCantidadDisponible(producto.id, nuevaCantidad)
    }

    /**
     * Suma una carga autorizada directamente al stock local de Room.
     * Se usa cuando el chofer acepta la carga para que el stock suba de inmediato sin esperar a la nube.
     */
    suspend fun aplicarCargaLocal(productosCarga: List<Plantilla_Producto>) {
        for (p in productosCarga) {
            val productoLocal = productoDao.getProductoById(p.id)
            if (productoLocal != null) {
                val nuevaCantidad = productoLocal.cantidadDisponible + p.cantidad
                productoDao.updateCantidadDisponible(p.id, nuevaCantidad)
                Log.d(TAG, "Carga aplicada localmente: ${p.nombre} +${p.cantidad}. Nuevo total: $nuevaCantidad")
            }
        }
    }

    /**
     * Función de conciliación interna.
     * Obtiene un mapa de productoId -> cantidadVendidaPendiente.
     */
    private suspend fun obtenerMapaVentasPendientes(): Map<String, Int> {
        val ventasPendientes = ventaDao.obtenerVentasPendientes()
        val mapa = mutableMapOf<String, Int>()
        for (venta in ventasPendientes) {
            val detalles = ventaDao.obtenerDetallesPorVenta(venta.id)
            for (detalle in detalles) {
                val actual = mapa[detalle.productoId] ?: 0
                mapa[detalle.productoId] = actual + detalle.cantidad
            }
        }
        return mapa
    }

    // --- MÉTODOS DE LEGADO Y SINCRONIZACIÓN ---

    suspend fun descargarProductosFirebase(uid: String) {
        try {
            Log.d(TAG, "Iniciando descarga integral de inventario para: $uid")
            
            // 🔥 PASO 1: Descargar Catálogo General primero para asegurar metadatos (imágenes)
            descargarCatalogoProductos()

            // 1. Obtener la información del usuario para saber su almacén
            var userDoc = firestore.collection("users").document(uid).get().await()
            if (!userDoc.exists()) {
                userDoc = firestore.collection("users").whereEqualTo("uid", uid).get().await().documents.firstOrNull() 
                    ?: throw Exception("Usuario no encontrado en Firestore")
            }

            // Intentamos obtener el nombre del almacén directamente del usuario o su ruta
            val rutaRef = userDoc.getDocumentReference("rutaAsignada")
            val rutaDoc = rutaRef?.get()?.await()
            val almacenRef = rutaDoc?.getDocumentReference("almacenAsignado")
            val nombreAlmacen = rutaDoc?.getString("almacenNombre") // Si existe el campo plano
                ?: almacenRef?.id // El ID suele ser el nombre según tu ejemplo
                ?: userDoc.getString("ultimoAlmacenNombre")

            if (nombreAlmacen == null) {
                Log.e(TAG, "No se pudo determinar el nombre del almacén para el usuario $uid")
                return
            }

            Log.d(TAG, "Consultando inventarioStock para el almacén: $nombreAlmacen")

            // 2. Consultar por nombre de almacén (más robusto que por referencia)
            val snapshot = firestore.collection("inventarioStock")
                .whereEqualTo("almacenNombre", nombreAlmacen)
                .get()
                .await()

            Log.d(TAG, "Documentos encontrados en inventarioStock: ${snapshot.size()}")

            val ventasPendientes = obtenerMapaVentasPendientes()

            val productos = coroutineScope {
                snapshot.documents.mapNotNull { invDoc ->
                    async {
                        runCatching {
                            val longId = invDoc.id
                            // 📄 Usamos EXACTAMENTE los campos que me mostraste
                            val nombre = invDoc.getString("productoNombre") ?: "Sin nombre"
                            val precio = (invDoc.get("precioUnitario") as? Number)?.toDouble() ?: 0.0
                            val cantidadNube = (invDoc.getLong("cantidad") ?: 0L).toInt()

                            val vendidoLocalmente = ventasPendientes[longId] ?: 0
                            val cantidadFinal = (cantidadNube - vendidoLocalmente).coerceAtLeast(0)

                            // ID de producto base (antes del _)
                            val baseId = longId.split("_").firstOrNull() ?: ""

                            // 🖼️ GESTIÓN DE IMAGEN ROBUSTA
                            var imgUrl = ""
                            
                            // Intento A: Buscar en el catálogo que acabamos de descargar en Room
                            val catProduct = productoDao.getProductoById(baseId)
                            imgUrl = catProduct?.imagenUrl ?: ""

                            // Intento B: Si sigue vacío, intentar buscar en el stock local previo
                            if (imgUrl.isEmpty()) {
                                val localPrevio = productoDao.getProductoById(longId)
                                imgUrl = localPrevio?.imagenUrl ?: ""
                            }

                            // Intento C: Si sigue vacía, intentar buscar en el documento de inventario (campo aplanado)
                            if (imgUrl.isEmpty()) {
                                imgUrl = invDoc.getString("imagenUrl") ?: ""
                            }

                            // Intento D: Último recurso, consultar la nube (colección productos)
                            if (imgUrl.isEmpty()) {
                                try {
                                    val pRef = invDoc.getDocumentReference("productoRef")
                                    val pSnap = withTimeoutOrNull(3000) { pRef?.get()?.await() }
                                    imgUrl = pSnap?.getString("imagenUrl") ?: ""
                                } catch (e: Exception) { }
                            }

                            ProductoEntity(
                                id = longId,
                                productoId = baseId,
                                nombre = nombre,
                                precio = precio,
                                cantidadDisponible = cantidadFinal,
                                imagenUrl = imgUrl,
                                syncStatus = true
                            )
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }

            if (productos.isNotEmpty()) {
                productoDao.insertAll(productos)
                Log.d(TAG, "Inventario actualizado en Room: ${productos.size} productos")
            } else {
                Log.w(TAG, "No se procesaron productos (lista vacía)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en descargarProductosFirebase: ${e.message}", e)
        }
    }

    fun escucharCambiosFirebase(uid: String) {
        listenerRegistration?.remove()
        repositoryScope.launch {
            try {
                // Buscamos el almacén una vez para configurar el listener
                val userDoc = firestore.collection("users").document(uid).get().await()
                val rutaRef = userDoc.getDocumentReference("rutaAsignada")
                val rutaDoc = rutaRef?.get()?.await()
                val nombreAlmacen = rutaDoc?.getDocumentReference("almacenAsignado")?.id 
                    ?: userDoc.getString("ultimoAlmacenNombre") ?: return@launch

                Log.d(TAG, "Configurando listener en tiempo real para: $nombreAlmacen")

                listenerRegistration = firestore.collection("inventarioStock")
                    .whereEqualTo("almacenNombre", nombreAlmacen)
                    .addSnapshotListener { snapshot, e ->
                        if (e != null) {
                            Log.e(TAG, "Error en SnapshotListener", e)
                            return@addSnapshotListener
                        }
                        if (snapshot == null) return@addSnapshotListener

                        repositoryScope.launch {
                            val ventasPendientes = obtenerMapaVentasPendientes()
                            val remotos = snapshot.documents.mapNotNull { doc ->
                                runCatching {
                                    val longId = doc.id
                                    val cantFinal = ((doc.getLong("cantidad") ?: 0L).toInt() - (ventasPendientes[longId] ?: 0)).coerceAtLeast(0)
                                    
                                    // Preservar imagen local si existe para no dejarla en blanco en el listener
                                    val previo = productoDao.getProductoById(longId)
                                    val imgFinal = previo?.imagenUrl ?: ""
                                    
                                    ProductoEntity(
                                        id = longId,
                                        productoId = longId.split("_").firstOrNull() ?: "",
                                        nombre = doc.getString("productoNombre") ?: "Producto",
                                        precio = (doc.get("precioUnitario") as? Number)?.toDouble() ?: 0.0,
                                        cantidadDisponible = cantFinal,
                                        imagenUrl = imgFinal,
                                        syncStatus = true
                                    )
                                }.getOrNull()
                            }
                            if (remotos.isNotEmpty()) {
                                productoDao.insertAll(remotos)
                            }
                        }
                    }
            } catch (e: Exception) { Log.e(TAG, "Error iniciando listener", e) }
        }
    }

    suspend fun getAlmacenVendedor(uid: String): String? {
        return obtenerAlmacenAsignado(uid)?.id
    }

    private suspend fun obtenerAlmacenAsignado(uid: String): com.google.firebase.firestore.DocumentReference? {
        return try {
            // Intento 1: Buscar por ID de documento (estándar)
            var userDoc = firestore.collection("users").document(uid).get().await()
            
            // Intento 2: Si no existe, buscar por campo "uid" (compatibilidad)
            if (!userDoc.exists()) {
                val query = firestore.collection("users").whereEqualTo("uid", uid).get().await()
                val snapshot = query.documents.firstOrNull()
                if (snapshot != null) {
                    userDoc = snapshot
                } else {
                    Log.e(TAG, "No se encontró el usuario $uid en Firestore")
                    return null
                }
            }

            val rutaRef = userDoc.getDocumentReference("rutaAsignada") ?: return null
            val rutaDocSnap = rutaRef.get().await()
            rutaDocSnap.getDocumentReference("almacenAsignado")
        } catch (e: Exception) { 
            Log.e(TAG, "Error obteniendo almacén asignado para $uid", e)
            null 
        }
    }

    fun stopEscuchaFirebase() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }
}
