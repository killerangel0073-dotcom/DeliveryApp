package com.gruposanangel.delivery

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.data.UsuarioDao
import com.gruposanangel.delivery.data.UsuarioEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await


class RepositoryUsuario(private val usuarioDao: UsuarioDao) {

    suspend fun sincronizarVendedorLocal(uid: String) {
        try {
            val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()

            // Campos básicos
            val nombre = doc.getString("nombre") ?: "Desconocido"
            val email = doc.getString("email")
            val photoUrl = doc.getString("photo_url")
            val puestoTrabajo = doc.getString("puestoTrabajo")
            val licencia = doc.getString("licenciaConducir")

            // Campos nuevos desde Firestore
            val activo = doc.getBoolean("activo")
            val createdTime = doc.getTimestamp("created_time")?.toDate()?.time
            val credencialElector = doc.getString("credencialElector")

            // fcmTokens -> List<String>
            val fcmRaw = doc.get("fcmTokens") as? List<*>
            val fcmTokens = fcmRaw?.mapNotNull { it?.toString() } ?: emptyList()


            // jefeDirecto -> guardar id o path y obtener nombre
            val jefeDirectoRef = doc.get("jefeDirecto") as? DocumentReference
            var jefeDirectoId: String? = null
            var jefeDirectoNombre: String? = null

            if (jefeDirectoRef != null) {
                jefeDirectoId = jefeDirectoRef.id
                // Obtener documento del jefe
                val jefeSnap = jefeDirectoRef.get().await()
                jefeDirectoNombre = jefeSnap.getString("nombre") ?: jefeDirectoRef.id
            }



            // Ruta asignada (DocumentReference) -> extraer id y nombre (fallback a id)
            val rutaRef = doc.get("rutaAsignada") as? DocumentReference
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

            // Crear la entidad completa
            val vendedor = UsuarioEntity(
                uid = uid,
                nombre = nombre,
                email = email,
                photoUrl = photoUrl,
                puestoTrabajo = puestoTrabajo,
                licenciaConducir = licencia,
                activo = activo,
                createdTime = createdTime,
                credencialElector = credencialElector,
                jefeDirectoId = jefeDirectoId,
                jefeDirectoNombre = jefeDirectoNombre, // <-- NUEVO
                ultimaRutaId = ultimaRutaId,
                ultimaRutaNombre = ultimaRutaNombre,
                ultimoAlmacenId = ultimoAlmacenId,
                ultimoAlmacenNombre = ultimoAlmacenNombre
            )

            // Guardar en Room (REPLACE)
            usuarioDao.limpiarTabla()
            usuarioDao.insertar(vendedor)

        } catch (e: Exception) {
            Log.e("RepoUsuario", "Error sincronizando vendedor local: ${e.message}", e)
        }
    }

    // --- Lectura local ---
    suspend fun obtenerUsuarioLocal(uid: String): UsuarioEntity? {
        return usuarioDao.obtenerPorId(uid)
    }

    fun obtenerUsuarioLocalFlow(uid: String): Flow<UsuarioEntity?> {
        return usuarioDao.obtenerPorIdFlow(uid)
    }
}

