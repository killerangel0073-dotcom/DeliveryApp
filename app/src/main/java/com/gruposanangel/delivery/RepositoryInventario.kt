package com.gruposanangel.delivery.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/**
 * Repositorio de Inventario refactorizado para Delisa Botanas.
 */
class RepositoryInventario(
    private val firebaseDataSource: FirebaseDataSource,
    private val productoDao: ProductoDao,
    private val ventaDao: VentaDao
) {

    private val firestore = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null
    private val TAG = "InventarioRepository"
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun obtenerProductosLocal(): Flow<List<ProductoEntity>> = productoDao.getAllProductosFlow()

    suspend fun descargarCatalogoProductos() {
        try {
            val data = firebaseDataSource.obtenerProductosCatalog()
            val entities = data.map { map ->
                ProductoEntity(
                    id = map["id"] as String,
                    productoId = map["id"] as String,
                    nombre = map["nombre"] as? String ?: "",
                    precio = map["precio"] as? Double ?: 0.0,
                    cantidadDisponible = 0,
                    imagenUrl = map["imagenUrl"] as? String ?: "",
                    syncStatus = true
                )
            }
            productoDao.insertAll(entities)
        } catch (e: Exception) { Log.e(TAG, "Error catálogo", e) }
    }

    suspend fun obtenerStockAlmacen(almacen: String): Map<String, Int> = firebaseDataSource.obtenerStockAlmacen(almacen)

    suspend fun crearOrdenTransferencia(ordenData: Map<String, Any>): String = firebaseDataSource.crearOrdenTransferencia(ordenData)

    suspend fun actualizarCantidadProducto(productoId: String, cantidadVendida: Int) {
        val producto = productoDao.getProductoById(productoId) ?: return
        productoDao.updateCantidadDisponible(producto.id, (producto.cantidadDisponible - cantidadVendida).coerceAtLeast(0))
    }

    suspend fun aplicarCargaLocal(productosCarga: List<Plantilla_Producto>, almacenNombre: String? = null) {
        for (p in productosCarga) {
            var idABuscar = if (almacenNombre != null) "${p.id}_$almacenNombre" else p.id
            var productoLocal = productoDao.getProductoById(idABuscar)
            if (productoLocal == null) {
                productoLocal = productoDao.getProductoById(p.id)
                if (productoLocal != null) idABuscar = p.id
            }
            if (productoLocal != null) {
                productoDao.updateCantidadDisponible(idABuscar, productoLocal.cantidadDisponible + p.cantidad)
            }
        }
    }

    private suspend fun obtenerMapaVentasPendientes(): Map<String, Int> {
        val pendientes = ventaDao.obtenerVentasPendientes(); val mapa = mutableMapOf<String, Int>()
        for (v in pendientes) {
            ventaDao.obtenerDetallesPorVenta(v.id).forEach { d -> mapa[d.productoId] = (mapa[d.productoId] ?: 0) + d.cantidad }
        }
        return mapa
    }

    suspend fun descargarProductosFirebase(uid: String) {
        try {
            descargarCatalogoProductos()
            var userDoc = firestore.collection("users").document(uid).get().await()
            if (!userDoc.exists()) userDoc = firestore.collection("users").whereEqualTo("uid", uid).get().await().documents.firstOrNull() ?: return
            val rutaRef = userDoc.getDocumentReference("rutaAsignada"); val rutaDoc = rutaRef?.get()?.await()
            val nombreAlmacen = rutaDoc?.getString("almacenNombre") ?: rutaDoc?.getDocumentReference("almacenAsignado")?.id ?: userDoc.getString("ultimoAlmacenNombre") ?: return
            val snapshot = firestore.collection("inventarioStock").whereEqualTo("almacenNombre", nombreAlmacen).get().await()
            val ventasPendientes = obtenerMapaVentasPendientes()
            val entities = snapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val baseId = id.split("_")[0]
                val cat = productoDao.getProductoById(baseId)

                val nombreNube = doc.getString("productoNombre")
                val precioNube = (doc.get("precioUnitario") as? Number)?.toDouble() ?: 0.0

                val nombreFinal = if (!nombreNube.isNullOrEmpty()) nombreNube else (cat?.nombre ?: "Producto")
                val precioFinal = if (precioNube > 0) precioNube else (cat?.precio ?: 0.0)

                val cantFinal = ((doc.getLong("cantidad") ?: 0L).toInt() - (ventasPendientes[id] ?: 0)).coerceAtLeast(0)
                
                ProductoEntity(id, baseId, nombreFinal, precioFinal, cantFinal, cat?.imagenUrl ?: doc.getString("imagenUrl") ?: "", true)
            }
            if (entities.isNotEmpty()) productoDao.insertAll(entities)
        } catch (e: Exception) { Log.e(TAG, "Error descargar stock", e) }
    }

    fun escucharCambiosFirebase(uid: String) {
        listenerRegistration?.remove()
        repositoryScope.launch {
            try {
                var userDoc = firestore.collection("users").document(uid).get().await()
                if (!userDoc.exists()) userDoc = firestore.collection("users").whereEqualTo("uid", uid).get().await().documents.firstOrNull() ?: return@launch
                val nombreAlmacen = userDoc.getString("ultimoAlmacenNombre") ?: return@launch
                listenerRegistration = firestore.collection("inventarioStock").whereEqualTo("almacenNombre", nombreAlmacen).addSnapshotListener { snap, _ ->
                    if (snap == null) return@addSnapshotListener
                    repositoryScope.launch {
                        val pendientes = obtenerMapaVentasPendientes()
                        val entities = snap.documents.mapNotNull { d ->
                            val id = d.id
                            val baseId = id.split("_")[0]
                            val cant = ((d.getLong("cantidad") ?: 0L).toInt() - (pendientes[id] ?: 0)).coerceAtLeast(0)
                            val cat = productoDao.getProductoById(baseId)
                            val prev = productoDao.getProductoById(id)

                            val nombreNube = d.getString("productoNombre")
                            val precioNube = (d.get("precioUnitario") as? Number)?.toDouble() ?: 0.0

                            val nombreFinal = if (!nombreNube.isNullOrEmpty()) nombreNube else (cat?.nombre ?: prev?.nombre ?: "Producto")
                            val precioFinal = if (precioNube > 0) precioNube else (cat?.precio ?: prev?.precio ?: 0.0)

                            ProductoEntity(id, baseId, nombreFinal, precioFinal, cant, cat?.imagenUrl ?: prev?.imagenUrl ?: "", true)
                        }
                        if (entities.isNotEmpty()) productoDao.insertAll(entities)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    suspend fun getAlmacenVendedor(uid: String): String? {
        val userQuery = firestore.collection("users").whereEqualTo("uid", uid).get().await()
        val userDoc = userQuery.documents.firstOrNull() ?: return null
        val rutaRef = userDoc.getDocumentReference("rutaAsignada") ?: return userDoc.getString("ultimoAlmacenNombre")
        val rutaSnap = rutaRef.get().await()
        return rutaSnap.getString("almacenNombre") ?: rutaSnap.getDocumentReference("almacenAsignado")?.id
    }

    fun stopEscuchaFirebase() { listenerRegistration?.remove(); listenerRegistration = null }
}
