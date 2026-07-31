package com.gruposanangel.delivery

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.data.UsuarioDao
import com.gruposanangel.delivery.data.UsuarioEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/**
 * Repositorio de Usuarios refactorizado para Delisa Botanas.
 * Centraliza la lógica de negocio del perfil con un enfoque Offline-First.
 */
class RepositoryUsuario(
    private val firebaseDataSource: FirebaseDataSource,
    private val usuarioDao: UsuarioDao
) {

    /**
     * Realiza el login en Firebase.
     */
    suspend fun login(email: String, password: String): String {
        return firebaseDataSource.login(email, password)
    }

    /**
     * Sincroniza los datos del usuario desde Firebase a la base de datos local (Room).
     */
    suspend fun syncUsuario(uid: String) {
        try {
            val userData = firebaseDataSource.descargarPerfilUsuario(uid) ?: return

            val nombre = userData["nombre"] as? String ?: "Desconocido"
            val email = userData["email"] as? String
            val photoUrl = userData["photo_url"] as? String
            val puestoTrabajo = userData["puestoTrabajo"] as? String
            val licencia = userData["licenciaConducir"] as? String
            val activo = userData["activo"] as? Boolean
            val status = userData["status"] as? String ?: "ACTIVO"
            val fechaBaja = (userData["fechaBaja"] as? com.google.firebase.Timestamp)?.toDate()?.time
            val motivoBaja = userData["motivoBaja"] as? String

            val createdTime = (userData["created_time"] as? com.google.firebase.Timestamp)?.toDate()?.time
            val credencialElector = userData["credencialElector"] as? String

            // jefeDirecto
            val jefeDirectoRef = userData["jefeDirecto"] as? DocumentReference
            var jefeDirectoId: String? = null
            var jefeDirectoNombre: String? = null
            if (jefeDirectoRef != null) {
                jefeDirectoId = jefeDirectoRef.id
                val jefeSnap = jefeDirectoRef.get().await()
                jefeDirectoNombre = jefeSnap.getString("nombre") ?: jefeDirectoId
            }

            // Ruta y Almacen asignado (Base)
            val rutaRef = userData["rutaAsignada"] as? DocumentReference
            var ultimaRutaId: String? = null
            var ultimaRutaNombre: String? = null
            var ultimoAlmacenId: String? = null
            var ultimoAlmacenNombre: String? = null

            if (rutaRef != null) {
                val rutaSnap = rutaRef.get().await()
                ultimaRutaId = rutaSnap.id
                ultimaRutaNombre = rutaSnap.getString("nombre") ?: rutaSnap.id

                val almacenRef = rutaSnap.get("almacenAsignado") as? DocumentReference
                if (almacenRef != null) {
                    val almacenSnap = almacenRef.get().await()
                    ultimoAlmacenId = almacenSnap.id
                    ultimoAlmacenNombre = almacenSnap.getString("nombre") ?: almacenRef.id
                }
            }

            // --- NUEVA LÓGICA DE COBERTURA Y MULTI-ALMACÉN ---
            val cobertura = userData["coberturaActiva"] as? Map<String, Any>
            val expiracion = (cobertura?.get("expiracion") as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
            val esCoberturaValida = cobertura != null && System.currentTimeMillis() < expiracion

            val listaAlmacenesFinal = mutableListOf<String>()

            if (esCoberturaValida) {
                Log.d("RepositoryUsuario", "🚨 Cobertura de emergencia detectada y válida para $uid")
                
                // 1. Sobrescribir Ruta (Para ver los clientes de la ruta que se cubre)
                val rutaCoberturaRef = cobertura!!["rutaId"] as? DocumentReference
                if (rutaCoberturaRef != null) {
                    val rutaSnap = rutaCoberturaRef.get().await()
                    ultimaRutaId = rutaSnap.id
                    ultimaRutaNombre = rutaSnap.getString("nombre") ?: rutaSnap.id
                }

                // 2. Determinar Almacenes Permitidos
                val modo = cobertura["modo"] as? String ?: "RELEVO"
                val almacenesCobertura = cobertura["almacenes"] as? List<String> ?: emptyList()
                
                listaAlmacenesFinal.addAll(almacenesCobertura)

                // Si es modo RESPALDO, el vendedor usa su propio stock para cubrir otra ruta
                if (modo == "RESPALDO" && ultimoAlmacenNombre != null) {
                    if (!listaAlmacenesFinal.contains(ultimoAlmacenNombre)) {
                        listaAlmacenesFinal.add(ultimoAlmacenNombre)
                    }
                }
            } else {
                // Modo Normal: Solo su almacén asignado si existe
                if (ultimoAlmacenNombre != null) {
                    listaAlmacenesFinal.add(ultimoAlmacenNombre)
                }
                
                // 🔥 SOPORTE MULTI-LÍNEA (Delisa + Frituras) incluso en modo normal
                val almacenesExtra = userData["almacenesAdicionales"] as? List<String>
                almacenesExtra?.forEach { if (!listaAlmacenesFinal.contains(it)) listaAlmacenesFinal.add(it) }
            }

            // --- CONFIGURACIÓN DE VENTA DINÁMICA ---
            val perfilesRaw = userData["perfilesVenta"] as? List<Map<String, Any>>
            val perfilesJson = if (perfilesRaw != null) {
                org.json.JSONArray(perfilesRaw).toString()
            } else null

            val entity = UsuarioEntity(
                uid = uid,
                nombre = nombre,
                email = email,
                photoUrl = photoUrl,
                puestoTrabajo = puestoTrabajo,
                licenciaConducir = licencia,
                activo = activo,
                status = status,
                fechaBaja = fechaBaja,
                motivoBaja = motivoBaja,
                createdTime = createdTime,
                credencialElector = createdTime?.let { "" } ?: credencialElector, // Ajuste menor de compatibilidad
                jefeDirectoId = jefeDirectoId,
                jefeDirectoNombre = jefeDirectoNombre,
                ultimaRutaId = ultimaRutaId,
                ultimaRutaNombre = ultimaRutaNombre,
                ultimoAlmacenId = ultimoAlmacenId,
                ultimoAlmacenNombre = ultimoAlmacenNombre,
                almacenesConfig = listaAlmacenesFinal.joinToString(","),
                enCobertura = esCoberturaValida,
                expiracionCobertura = if (esCoberturaValida) expiracion else null,
                perfilesVentaJson = perfilesJson
            )

            // Guardar en Room (Se usa REPLACE en el DAO por lo que no es necesario limpiar la tabla)
            usuarioDao.insertar(entity)
            Log.d("RepositoryUsuario", "Usuario sincronizado y guardado en local: $uid")

        } catch (e: Exception) {
            Log.e("RepositoryUsuario", "Error al sincronizar usuario: ${e.message}", e)
        }
    }

    /**
     * Devuelve un Flow del usuario logueado actualmente directamente desde Room.
     * Se mantiene reactivo tanto a cambios en la sesión de Firebase como a actualizaciones en Room.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getUsuarioActual(): Flow<UsuarioEntity?> {
        return firebaseDataSource.authStateFlow().flatMapLatest { firebaseUser ->
            if (firebaseUser != null) {
                usuarioDao.obtenerPorIdFlow(firebaseUser.uid)
            } else {
                flowOf(null)
            }
        }
    }

    /**
     * Obtiene el UID actual (si existe sesión activa en Firebase).
     */
    fun getUidActual(): String? = firebaseDataSource.getUidActual()

    /**
     * Cierra la sesión en Firebase y limpia los datos locales en Room.
     */
    suspend fun cerrarSesion() {
        firebaseDataSource.cerrarSesion()
        usuarioDao.limpiarTabla()
    }

    suspend fun obtenerTokensDirectivos(): List<String> {
        return firebaseDataSource.obtenerTokensDirectivos()
    }

    suspend fun obtenerTokenSupervisor(): String? {
        return obtenerTokensDirectivos().firstOrNull()
    }

    suspend fun obtenerTokensPorDestino(destino: String): List<String> {
        return firebaseDataSource.obtenerTokensPorDestino(destino)
    }

    // --- MÉTODOS DE COMPATIBILIDAD ---

    suspend fun obtenerUsuarioActual(): UsuarioEntity? {
        val uid = getUidActual() ?: return null
        return usuarioDao.obtenerPorId(uid)
    }

    suspend fun sincronizarVendedorLocal(uid: String) {
        syncUsuario(uid)
    }

    suspend fun obtenerUsuarioLocal(uid: String): UsuarioEntity? {
        return usuarioDao.obtenerPorId(uid)
    }

    /**
     * Da de baja lógica a un vendedor.
     */
    suspend fun desactivarVendedor(uid: String, motivo: String, status: String = "BAJA") {
        firebaseDataSource.desactivarUsuario(uid, motivo, status)
        // Opcional: Podrías actualizar localmente si el UID coincide con el usuario actual
    }

    /**
     * Obtiene la lista de vendedores activos desde Firestore (Operacional).
     */
    suspend fun obtenerVendedoresActivos(): List<UsuarioEntity> {
        // Esta lógica podría moverse a FirebaseDataSource para mantener el patrón
        // Por ahora, usaremos una consulta filtrada por status
        return firebaseDataSource.obtenerUsuariosPorStatus("ACTIVO")
    }
}
