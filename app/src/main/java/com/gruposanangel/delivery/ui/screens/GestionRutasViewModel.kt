package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gruposanangel.delivery.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class GestionRutasUiState(
    val isLoading: Boolean = false,
    val rutas: List<RutaEntity> = emptyList(),
    val clientesDisponibles: List<ClienteEntity> = emptyList(),
    val selectedRutaBase: String = "Ruta 1",
    val selectedDay: String = "Lun",
    val selectedWeek: String = "Par",
    val selectedClientIds: Set<String> = emptySet(),
    val error: String? = null,
    val successMessage: String? = null
)

class GestionRutasViewModel(
    private val repositoryRuta: RepositoryRuta,
    private val repositoryCliente: RepositoryCliente
) : ViewModel() {

    private val _uiState = MutableStateFlow(GestionRutasUiState())
    val uiState: StateFlow<GestionRutasUiState> = _uiState.asStateFlow()

    init {
        cargarDatos()
    }

    private fun cargarDatos() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            repositoryRuta.descargarRutasDesdeFirebase()
            
            combine(
                repositoryRuta.obtenerRutasLocal(),
                repositoryCliente.obtenerClientesLocal()
            ) { rutas, clientes ->
                val currentBase = _uiState.value.selectedRutaBase
                val nextBase = if (currentBase == "Ruta 1" && rutas.isNotEmpty()) rutas.first().id else currentBase
                
                _uiState.update { it.copy(
                    rutas = rutas,
                    clientesDisponibles = clientes,
                    selectedRutaBase = nextBase,
                    isLoading = false
                ) }
                actualizarClientesEnRuta()
            }.collect()
        }
    }

    fun setRutaBase(ruta: String) {
        _uiState.update { it.copy(selectedRutaBase = ruta) }
        actualizarClientesEnRuta()
    }

    fun setDay(day: String) {
        _uiState.update { it.copy(selectedDay = day) }
        actualizarClientesEnRuta()
    }

    fun setWeek(week: String) {
        _uiState.update { it.copy(selectedWeek = week) }
        actualizarClientesEnRuta()
    }

    private fun actualizarClientesEnRuta() {
        val state = _uiState.value
        val nombreRutaBuscada = "${state.selectedRutaBase} ${state.selectedDay} ${state.selectedWeek}"
        
        // Buscamos si existe una ruta con ese nombre
        val rutaExistente = state.rutas.find { it.nombre.equals(nombreRutaBuscada, ignoreCase = true) }
        
        if (rutaExistente != null) {
            val ids = state.clientesDisponibles
                .filter { it.rutaId == rutaExistente.id }
                .map { it.id }
                .toSet()
            _uiState.update { it.copy(selectedClientIds = ids) }
        } else {
            _uiState.update { it.copy(selectedClientIds = emptySet()) }
        }
    }

    fun toggleSeleccionCliente(clienteId: String) {
        _uiState.update { state ->
            val actuales = state.selectedClientIds.toMutableSet()
            if (actuales.contains(clienteId)) actuales.remove(clienteId)
            else actuales.add(clienteId)
            state.copy(selectedClientIds = actuales)
        }
    }

    fun guardarConfiguracionRuta() {
        val state = _uiState.value
        val nombreRuta = "${state.selectedRutaBase} ${state.selectedDay} ${state.selectedWeek}"
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            // Buscamos o creamos ID
            val rutaExistente = state.rutas.find { it.nombre.equals(nombreRuta, ignoreCase = true) }
            val id = rutaExistente?.id ?: UUID.randomUUID().toString()
            
            val nuevaRuta = RutaEntity(
                id = id,
                nombre = nombreRuta,
                diasVisita = state.selectedDay,
                frecuencia = if (state.selectedWeek == "Par") "Quincenal Par" else "Quincenal Non"
            )
            
            repositoryRuta.guardarRuta(nuevaRuta)
            repositoryRuta.asignarClientesARuta(id, state.selectedClientIds)
            
            _uiState.update { it.copy(
                isLoading = false, 
                successMessage = "Logística guardada: $nombreRuta"
            ) }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}

class GestionRutasViewModelFactory(
    private val repositoryRuta: RepositoryRuta,
    private val repositoryCliente: RepositoryCliente
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GestionRutasViewModel(repositoryRuta, repositoryCliente) as T
    }
}
