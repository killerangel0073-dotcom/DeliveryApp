package com.gruposanangel.delivery.utilidades

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log

/**
 * TimeManager - Arquitectura de Seguridad Temporal para DeliveryApp.
 * Garantiza que la hora de las ventas sea real aunque el usuario cambie el reloj del sistema.
 */
object TimeManager {
    private const val PREFS_NAME = "TimeManagerPrefs"
    private const val KEY_OFFSET = "offset_relativo"
    private const val KEY_LAST_REALTIME = "ultimo_realtime"
    private const val DISCREPANCIA_MAXIMA = 300_000 // 5 minutos en ms

    private var offsetRelativo: Long = 0
    private var necesitaResincronizacion: Boolean = true

    /**
     * Inicializa el Manager desde almacenamiento local.
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        offsetRelativo = prefs.getLong(KEY_OFFSET, 0)
        val ultimoRealtime = prefs.getLong(KEY_LAST_REALTIME, 0)
        
        val currentRealtime = SystemClock.elapsedRealtime()
        
        // Si el equipo se reinició, el contador de hardware vuelve a cero.
        // Si currentRealtime < ultimoRealtime, hubo un reinicio de hardware.
        if (currentRealtime < ultimoRealtime || offsetRelativo == 0L) {
            necesitaResincronizacion = true
            Log.w("TimeManager", "⚠️ Resincronización requerida: Reinicio detectado o sin datos.")
        } else {
            necesitaResincronizacion = false
            Log.i("TimeManager", "✅ Estado temporal persistido cargado correctamente.")
        }
    }

    /**
     * Establece la hora base real obtenida de un servidor confiable (Firebase).
     */
    fun sincronizar(serverTime: Long, context: Context) {
        val currentRealtime = SystemClock.elapsedRealtime()
        // Offset = Hora Atómica - Milisegundos de Hardware
        offsetRelativo = serverTime - currentRealtime
        necesitaResincronizacion = false

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putLong(KEY_OFFSET, offsetRelativo)
            putLong(KEY_LAST_REALTIME, currentRealtime)
            apply()
        }
        Log.i("TimeManager", "📡 Sincronizado con Servidor. Offset: $offsetRelativo")
    }

    /**
     * Retorna la hora real calculada: Tiempo de Hardware + Offset de Servidor.
     * Es inmune a cambios manuales del reloj del sistema.
     */
    fun getHoraReal(): Long {
        return SystemClock.elapsedRealtime() + offsetRelativo
    }

    /**
     * Indica si la aplicación debe bloquearse hasta obtener la hora del servidor.
     */
    fun requiereSincronizacion(): Boolean = necesitaResincronizacion

    /**
     * Verifica si el vendedor tiene activada la 'Hora Automática' en los ajustes de Android.
     */
    fun esHoraAutomaticaActivada(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME) == 1
        } catch (e: Exception) {
            true 
        }
    }
}
