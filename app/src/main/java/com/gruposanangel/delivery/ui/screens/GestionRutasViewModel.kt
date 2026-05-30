package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gruposanangel.delivery.ClienteOrdenado
import com.gruposanangel.delivery.Itinerario
import com.gruposanangel.delivery.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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
        // Generamos el ID compuesto: Ruta1_Lun_Par
        val itinerarioId = "${state.selectedRutaBase.replace(" ", "")}_${state.selectedDay}_${state.selectedWeek}"
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val itinerario = repositoryRuta.obtenerItinerario(itinerarioId)
            
            val ids = itinerario?.clientesOrdenados?.map { it.clienteId }?.toSet() ?: emptySet()
            
            _uiState.update { it.copy(
                selectedClientIds = ids,
                isLoading = false
            ) }
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
        // ID Compuesto: Ruta1_Lun_Par
        val itinerarioId = "${state.selectedRutaBase.replace(" ", "")}_${state.selectedDay}_${state.selectedWeek}"
        
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            try {
                // Creamos la lista ordenada (por ahora simplemente el orden en que están en el Set)
                val clientesOrdenados = state.selectedClientIds.mapIndexed { index, id ->
                    ClienteOrdenado(clienteId = id, ordenVisita = index + 1)
                }

                val nuevoItinerario = Itinerario(
                    id = itinerarioId,
                    rutaId = state.selectedRutaBase,
                    diaSemana = state.selectedDay,
                    frecuencia = state.selectedWeek,
                    activo = true,
                    clientesOrdenados = clientesOrdenados,
                    lastUpdated = System.currentTimeMillis()
                )
                
                repositoryRuta.guardarItinerario(nuevoItinerario)
                
                _uiState.update { it.copy(
                    isLoading = false, 
                    successMessage = "Itinerario guardado: ${state.selectedRutaBase} (${state.selectedDay} ${state.selectedWeek})"
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = "Error al guardar: ${e.message}"
                ) }
            }
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
