package com.gruposanangel.delivery.SegundoPlano

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// 1. Agregamos la "caja" para envolver el dato con su tiempo
data class VelocidadState(
    val kmh: Float = 0f,
    val timestamp: Long = 0L
)

object LocationState {

    // 2. Cambiamos de MutableStateFlow(0f) a MutableStateFlow(VelocidadState())
    private val _velocidad = MutableStateFlow(VelocidadState())
    val velocidad = _velocidad.asStateFlow()

    // 3. Actualizamos la función para que guarde el tiempo actual automáticamente
    fun updateVelocidad(valor: Float) {
        _velocidad.value = VelocidadState(
            kmh = valor,
            timestamp = System.currentTimeMillis()
        )
    }

    fun reset() {
        _velocidad.value = VelocidadState(kmh = 0f, timestamp = 0L)
    }
}