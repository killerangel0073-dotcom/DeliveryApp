package com.gruposanangel.delivery

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.data.UsuarioDao
import com.gruposanangel.delivery.data.UsuarioEntity
import kotlinx.coroutines.tasks.await

class RepositoryUsuario {

    suspend fun sincronizarVendedorLocal(vendedorDao: UsuarioDao, uid: String) {
        try {
            val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
            val vendedor = UsuarioEntity(
                uid = uid,
                nombre = doc.getString("nombre") ?: "Desconocido",
                email = doc.getString("email"),
                photoUrl = doc.getString("photo_url"),
                puestoTrabajo = doc.getString("puestoTrabajo"),
                licenciaConducir = doc.getString("licenciaConducir")
            )
            vendedorDao.limpiarTabla()
            vendedorDao.insertar(vendedor)
        } catch (e: Exception) {
            Log.e("RepoUsuario", "Error sincronizando vendedor local: ${e.message}")
        }
    }


}