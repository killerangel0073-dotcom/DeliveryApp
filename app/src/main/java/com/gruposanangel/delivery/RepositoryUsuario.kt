package com.gruposanangel.delivery

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.data.UsuarioDao
import com.gruposanangel.delivery.data.UsuarioEntity
import kotlinx.coroutines.flow.Flow
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

            // Ruta y Almacen asignado
            val rutaRef = userData["rutaAsignada"] as? DocumentReference
            var ultimaRutaId: String? = null
            var ultimaRutaNombre: String? = null
            var ultimoAlmacenId: String? = null
            var ultimoAlmacenNombre: String? = null

            if (rutaRef != null) {
                val rutaSnap = rutaRef.get().await()
                ultimaRutaId = rutaSnap.id
                ultimaRutaNombre = rutaSnap.getString("nombre") ?: rutaRef.id

                val almacenRef = rutaSnap.get("almacenAsignado") as? DocumentReference
                if (almacenRef != null) {
                    val almacenSnap = almacenRef.get().await()
                    ultimoAlmacenId = almacenSnap.id
                    ultimoAlmacenNombre = almacenSnap.getString("nombre") ?: almacenRef.id
                }
            }

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
                credencialElector = credencialElector,
                jefeDirectoId = jefeDirectoId,
                jefeDirectoNombre = jefeDirectoNombre,
                ultimaRutaId = ultimaRutaId,
                ultimaRutaNombre = ultimaRutaNombre,
                ultimoAlmacenId = ultimoAlmacenId,
                ultimoAlmacenNombre = ultimoAlmacenNombre
            )

            // Guardar en Room (reemplazando cualquier sesión anterior)
            usuarioDao.limpiarTabla()
            usuarioDao.insertar(entity)
            Log.d("RepositoryUsuario", "Usuario sincronizado y guardado en local: $uid")

        } catch (e: Exception) {
            Log.e("RepositoryUsuario", "Error al sincronizar usuario: ${e.message}", e)
        }
    }

    /**
     * Devuelve un Flow del usuario actual directamente desde Room para reactividad en la UI.
     */
    fun getUsuarioActual(): Flow<UsuarioEntity?> {
        return usuarioDao.obtenerUsuarioActualFlow()
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
        return usuarioDao.obtenerUsuarioActual()
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
