package com.gruposanangel.delivery

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Representa la hoja de ruta para un día y ciclo específico.
 * Document ID sugerido: "Ruta1_Lun_Par"
 */
@Parcelize
data class Itinerario(
    val id: String = "",           // ID único (ej: Ruta1_Lun_Par)
    val rutaId: String = "",       // Referencia a la colección 'rutas'
    val diaSemana: String = "",    // Lun, Mar, Mie, Jue, Vie, Sab
    val frecuencia: String = "",   // Par, Non, Todas
    val activo: Boolean = true,
    val clientesOrdenados: List<ClienteOrdenado> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
) : Parcelable

/**
 * Puntero ligero hacia un cliente.
 */
@Parcelize
data class ClienteOrdenado(
    val clienteId: String = "",    // ID del documento en la colección 'clientes'
    val ordenVisita: Int = 0       // Posición en la que debe ser visitado
) : Parcelable
