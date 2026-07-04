package com.gruposanangel.delivery.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.gruposanangel.delivery.model.Plantilla_Producto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

/**
 * Repositorio de Inventario refactorizado para Delisa Botanas.
 */
class RepositoryInventario(
    private val firebaseDataSource: FirebaseDataSource,
    private val productoDao: ProductoDao,
    private val ventaDao: VentaDao,
    private val movimientoInventarioDao: MovimientoInventarioDao? = null
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

    private suspend fun obtenerImpactoInventarioPendiente(): Map<String, Int> {
        val mapaImpacto = mutableMapOf<String, Int>()
        
        // 1. Impacto de Ventas Pendientes (Siempre restan)
        // 🔥 Solo consideramos ventas pagadas (No canceladas)
        val ventasPendientes = ventaDao.obtenerVentasPendientes().filter { it.estado != "CANCELADA" }
        for (v in ventasPendientes) {
            ventaDao.obtenerDetallesPorVenta(v.id).forEach { d ->
                val idPK = d.productoId
                mapaImpacto[idPK] = (mapaImpacto[idPK] ?: 0) - d.cantidad
            }
        }

        // 2. Impacto de Ajustes (BLINDAJE CONTRA DUPLICIDAD)
        // 🔥 CRÍTICO: Solo incluimos los movimientos que aún NO se han sincronizado con Firebase.
        // Si ya se sincronizaron, confiamos en que el snapshot de Firestore eventualmente los traerá.
        // Incluir sincronizados "recientes" causaba que se sumaran dos veces (Nube + Local).
        val movimientosAuditoria = movimientoInventarioDao?.obtenerMovimientosPendientes() ?: emptyList()
        
        for (a in movimientosAuditoria) {
            val almacen = a.almacenNombre ?: ""
            val idPK = if (almacen.isNotEmpty() && !a.productoId.contains("_")) 
                "${a.productoId}_$almacen" 
            else a.productoId

            when (a.tipo) {
                "ENTRADA_CAMBIO_BUENO", "CARGA_INVENTARIO" -> {
                    mapaImpacto[idPK] = (mapaImpacto[idPK] ?: 0) + a.cantidad
                }
                "SALIDA_CAMBIO_BUENO", "SALIDA_REPOSICION_BUENO" -> {
                    mapaImpacto[idPK] = (mapaImpacto[idPK] ?: 0) - a.cantidad
                }
            }
        }
        return mapaImpacto
    }

    suspend fun descargarProductosFirebase(uid: String) {
        try {
            descargarCatalogoProductos()
            var userDoc = firestore.collection("users").document(uid).get().await()
            if (!userDoc.exists()) userDoc = firestore.collection("users").whereEqualTo("uid", uid).get().await().documents.firstOrNull() ?: return
            val rutaRef = userDoc.getDocumentReference("rutaAsignada"); val rutaDoc = rutaRef?.get()?.await()
            val nombreAlmacen = rutaDoc?.getString("almacenNombre") ?: rutaDoc?.getDocumentReference("almacenAsignado")?.id ?: userDoc.getString("ultimoAlmacenNombre") ?: return
            val snapshot = firestore.collection("inventarioStock").whereEqualTo("almacenNombre", nombreAlmacen).get().await()
            
            val impactoPendiente = obtenerImpactoInventarioPendiente()
            
            val entities = snapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val baseId = id.split("_")[0]
                val cat = productoDao.getProductoById(baseId)

                val nombreNube = doc.getString("productoNombre")
                val precioNube = (doc.get("precioUnitario") as? Number)?.toDouble() ?: 0.0

                val nombreFinal = if (!nombreNube.isNullOrEmpty()) nombreNube else (cat?.nombre ?: "Producto")
                val precioFinal = if (precioNube > 0) precioNube else (cat?.precio ?: 0.0)

                // Ajustamos el stock de la nube con lo que el vendedor ya hizo localmente
                val cantNube = (doc.getLong("cantidad") ?: 0L).toInt()
                val cantFinal = (cantNube + (impactoPendiente[id] ?: 0)).coerceAtLeast(0)
                
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
                if (!userDoc.exists()) {
                    userDoc = firestore.collection("users").whereEqualTo("uid", uid).get().await().documents.firstOrNull() ?: return@launch
                }
                
                val nombreAlmacen = userDoc.getString("ultimoAlmacenNombre") ?: return@launch
                Log.d(TAG, "Escuchando inventario para almacén: $nombreAlmacen")

                listenerRegistration = firestore.collection("inventarioStock")
                    .whereEqualTo("almacenNombre", nombreAlmacen)
                    .addSnapshotListener { snap, error ->
                        if (error != null || snap == null) return@addSnapshotListener

                        repositoryScope.launch {
                            try {
                                val impactoPendiente = obtenerImpactoInventarioPendiente()
                                val entities = snap.documents.mapNotNull { d ->
                                    val id = d.id
                                    val baseId = id.split("_")[0]
                                    val cantNube = (d.getLong("cantidad") ?: 0L).toInt()
                                    val cantFinal = (cantNube + (impactoPendiente[id] ?: 0)).coerceAtLeast(0)
                                    
                                    val cat = productoDao.getProductoById(baseId)
                                    val prev = productoDao.getProductoById(id)

                                    ProductoEntity(
                                        id = id,
                                        productoId = baseId,
                                        nombre = d.getString("productoNombre") ?: cat?.nombre ?: prev?.nombre ?: "Producto",
                                        precio = (d.get("precioUnitario") as? Number)?.toDouble() ?: cat?.precio ?: prev?.precio ?: 0.0,
                                        cantidadDisponible = cantFinal,
                                        imagenUrl = cat?.imagenUrl ?: prev?.imagenUrl ?: d.getString("imagenUrl") ?: "",
                                        syncStatus = true
                                    )
                                }
                                
                                if (entities.isNotEmpty()) {
                                    productoDao.insertAll(entities)
                                }
                            } catch (e: Exception) { Log.e(TAG, "Error sync reactivo", e) }
                        }
                    }
            } catch (e: Exception) { Log.e(TAG, "Error listener", e) }
        }
    }

    suspend fun getAlmacenVendedor(uid: String): String? {
        val userQuery = firestore.collection("users").whereEqualTo("uid", uid).get().await()
        val userDoc = userQuery.documents.firstOrNull() ?: return null
        val rutaRef = userDoc.getDocumentReference("rutaAsignada") ?: return userDoc.getString("ultimoAlmacenNombre")
        val rutaSnap = rutaRef.get().await()
        return rutaSnap.getString("almacenNombre") ?: rutaSnap.getDocumentReference("almacenAsignado")?.id
    }

    // --- MÉTODOS PARA VISTA ADMINISTRATIVA ---

    suspend fun obtenerListaAlmacenes(): List<String> = firebaseDataSource.obtenerListaAlmacenes()

    suspend fun obtenerStockDanado(almacen: String): Map<String, Int> {
        val stockNube = firebaseDataSource.obtenerStockDanado(almacen)
        val impactoLocal = obtenerImpactoDanadoPendiente(almacen)
        
        val stockFinal = stockNube.toMutableMap()
        impactoLocal.forEach { (prodId, cant) ->
            stockFinal[prodId] = (stockFinal[prodId] ?: 0) + cant
        }
        return stockFinal
    }

    private suspend fun obtenerImpactoDanadoPendiente(almacen: String): Map<String, Int> {
        val mapaImpacto = mutableMapOf<String, Int>()
        // Tomamos movimientos de hoy para asegurar que se vean las devoluciones recién hechas
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val inicioHoy = cal.timeInMillis

        val movimientos = movimientoInventarioDao?.obtenerTodosRecientes(inicioHoy) ?: emptyList()
        
        for (m in movimientos) {
            if (m.almacenNombre == almacen && (m.tipo == "ENTRADA_MALO_DEVOLUCION" || m.tipo == "DEVOLUCION_DANIADO")) {
                val baseId = m.productoId
                mapaImpacto[baseId] = (mapaImpacto[baseId] ?: 0) + m.cantidad
            }
        }
        return mapaImpacto
    }

    suspend fun obtenerStockGlobal(): Map<String, Int> = firebaseDataSource.obtenerStockGlobal()

    fun stopEscuchaFirebase() { listenerRegistration?.remove(); listenerRegistration = null }

    // --- LÓGICA DE AJUSTES Y ARQUEO (CORREGIDA: SIEMPRE ACTUALIZAR ALMACÉN DEL VENDEDOR) ---

    /**
     * Registra un movimiento de carga y lo sincroniza con Firebase (usado para Cargas de Emergencia/Directas)
     */
    suspend fun registrarMovimientoCarga(movimiento: MovimientoInventarioEntity, plantilla: Plantilla_Producto) {
        withContext(Dispatchers.IO) {
            // 1. Guardar el registro del movimiento para auditoría (Arqueo)
            movimientoInventarioDao?.insertarMovimiento(movimiento)
            
            // 2. Actualizar el stock disponible localmente de inmediato
            val idReal = if (!movimiento.almacenNombre.isNullOrEmpty() && !movimiento.productoId.contains("_")) 
                "${movimiento.productoId}_${movimiento.almacenNombre}" 
            else 
                movimiento.productoId

            sumarStockLocal(idReal, movimiento.cantidad, plantilla, movimiento.productoId)
            
            // 3. Intentar sincronizar (Si falla, el Worker lo hará después)
            if (!movimiento.sincronizado) {
                try {
                    sincronizarMovimiento(movimiento)
                } catch (e: Exception) {
                    Log.w(TAG, "Carga guardada localmente, sincronización pendiente.")
                }
            }
        }
    }

    /**
     * Solo actualiza el stock local sin disparar sincronización extra (usado al aceptar transferencias oficiales)
     */
    suspend fun registrarMovimientoCargaLocal(movimiento: MovimientoInventarioEntity, plantilla: Plantilla_Producto) {
        withContext(Dispatchers.IO) {
            movimientoInventarioDao?.insertarMovimiento(movimiento)
            val idReal = if (!movimiento.almacenNombre.isNullOrEmpty() && !movimiento.productoId.contains("_")) 
                "${movimiento.productoId}_${movimiento.almacenNombre}" 
            else 
                movimiento.productoId
            sumarStockLocal(idReal, movimiento.cantidad, plantilla, movimiento.productoId)
        }
    }

    suspend fun registrarDobleMovimiento(
        tipoOperacion: String,
        productoEntra: Plantilla_Producto,
        productoSale: Plantilla_Producto,
        cantidad: Int,
        vendedorId: String,
        almacenNombre: String, // Recibido desde ViewModel para mayor rapidez
        clienteId: String?,
        ticketId: String?,
        motivo: String?
    ) {
        withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            
            // Garantizar que usemos los IDs del almacén del vendedor (ej: prod123_Vendedor R2)
            val baseIdEntra = if (productoEntra.id.contains("_")) productoEntra.id.split("_")[0] else productoEntra.id
            val baseIdSale = if (productoSale.id.contains("_")) productoSale.id.split("_")[0] else productoSale.id
            
            val idRealEntra = if (almacenNombre.isNotEmpty()) "${baseIdEntra}_$almacenNombre" else baseIdEntra
            val idRealSale = if (almacenNombre.isNotEmpty()) "${baseIdSale}_$almacenNombre" else baseIdSale

            // 1. Registro de lo que ENTRA
            val movEntrada = MovimientoInventarioEntity(
                id = UUID.randomUUID().toString(),
                productoId = baseIdEntra,
                nombreProducto = productoEntra.nombre,
                cantidad = cantidad,
                tipo = if (tipoOperacion == "CAMBIO") "ENTRADA_CAMBIO_BUENO" else "ENTRADA_MALO_DEVOLUCION",
                motivo = motivo,
                vendedorId = vendedorId,
                almacenNombre = almacenNombre,
                clienteId = clienteId,
                referenciaId = ticketId,
                timestamp = timestamp
            )

            // 2. Registro de lo que SALE
            val movSalida = MovimientoInventarioEntity(
                id = UUID.randomUUID().toString(),
                productoId = baseIdSale,
                nombreProducto = productoSale.nombre,
                cantidad = cantidad,
                tipo = if (tipoOperacion == "CAMBIO") "SALIDA_CAMBIO_BUENO" else "SALIDA_REPOSICION_BUENO",
                motivo = motivo,
                vendedorId = vendedorId,
                almacenNombre = almacenNombre,
                clienteId = clienteId,
                referenciaId = ticketId,
                timestamp = timestamp + 1
            )

            // 3. Persistencia y Actualización de Stock (SOBRE EL ALMACÉN REAL)
            movimientoInventarioDao?.insertarMovimiento(movEntrada)
            movimientoInventarioDao?.insertarMovimiento(movSalida)

            if (tipoOperacion == "CAMBIO") {
                sumarStockLocal(idRealEntra, cantidad, productoEntra, baseIdEntra)
            } 

            actualizarCantidadProducto(idRealSale, cantidad)

            // 4. Sincronizar
            sincronizarMovimiento(movEntrada)
            sincronizarMovimiento(movSalida)
        }
    }

    private suspend fun sumarStockLocal(idPK: String, cantidad: Int, plantilla: Plantilla_Producto, baseId: String) {
        var producto = productoDao.getProductoById(idPK)
        
        if (producto == null) {
            // Si el producto no existe en este almacén, lo creamos
            producto = ProductoEntity(
                id = idPK,
                productoId = baseId,
                nombre = plantilla.nombre,
                precio = plantilla.precio,
                cantidadDisponible = cantidad,
                imagenUrl = plantilla.imagenUrl,
                syncStatus = false
            )
            productoDao.insertAll(listOf(producto))
        } else {
            productoDao.updateCantidadDisponible(idPK, producto.cantidadDisponible + cantidad)
        }
    }

    suspend fun sincronizarMovimiento(movimiento: MovimientoInventarioEntity) {
        try {
            val data = mutableMapOf(
                "productoId" to movimiento.productoId,
                "nombreProducto" to movimiento.nombreProducto,
                "cantidad" to movimiento.cantidad,
                "tipo" to movimiento.tipo,
                "motivo" to movimiento.motivo,
                "vendedorId" to movimiento.vendedorId,
                "almacenNombre" to movimiento.almacenNombre,
                "clienteId" to movimiento.clienteId,
                "timestamp" to movimiento.timestamp,
                "referenciaId" to movimiento.referenciaId
            )

            // Añadir campos de auditoría si existen
            movimiento.cantidadFisica?.let { data["cantidadFisica"] = it }
            movimiento.cantidadTeorica?.let { data["cantidadTeorica"] = it }

            firestore.collection("ajustes_inventario").document(movimiento.id).set(data).await()
            movimientoInventarioDao?.marcarComoSincronizado(movimiento.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo ajuste: ${e.message}")
            throw e
        }
    }

    fun obtenerMovimientosDesdeFlow(vendedorId: String, inicio: Long): Flow<List<MovimientoInventarioEntity>> {
        return movimientoInventarioDao?.obtenerMovimientosDesdeFlow(vendedorId, inicio) ?: flowOf(emptyList())
    }

    suspend fun obtenerMovimientosDesde(vendedorId: String, inicio: Long): List<MovimientoInventarioEntity> {
        return movimientoInventarioDao?.obtenerMovimientosDesde(vendedorId, inicio) ?: emptyList()
    }

    suspend fun calcularSaldoTeorico(vendedorId: String, fechaInicio: Long): Map<String, Int> {
        // Implementación simplificada para el Arqueo
        val inventario = productoDao.getAllProductosFlow().first()
        val saldo = mutableMapOf<String, Int>()
        
        inventario.forEach { p ->
            // Saldo Teorico = Inventario Actual (que ya considera Ventas y Cargas)
            saldo[p.nombre] = p.cantidadDisponible
        }
        return saldo
    }
}
