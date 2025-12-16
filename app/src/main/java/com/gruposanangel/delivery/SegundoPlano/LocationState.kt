package com.gruposanangel.delivery.SegundoPlano



import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocationState {

    private val _velocidad = MutableStateFlow(0f)
    val velocidad = _velocidad.asStateFlow()

    fun updateVelocidad(valor: Float) {
        _velocidad.value = valor
    }

    fun reset() {
        _velocidad.value = 0f
    }
}


