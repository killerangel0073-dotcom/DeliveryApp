package com.gruposanangel.delivery.SegundoPlano

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// 1️⃣ Estado que representa lo que la UI necesita
data class BatteryUiState(
    val level: Int = 100,
    val isCharging: Boolean = false,
    val isPlugged: Boolean = false
)


// 2️⃣ Fuente única de verdad
object BatteryState {

    private val _state = MutableStateFlow(BatteryUiState())
    val state = _state.asStateFlow()

    fun update(level: Int, isCharging: Boolean, isPlugged: Boolean) {
        Log.d("BATTERY_STATE", "update -> level=$level charging=$isCharging plugged=$isPlugged")
        _state.value = BatteryUiState(level, isCharging, isPlugged)
    }


}
