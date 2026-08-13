package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gruposanangel.delivery.ClienteOrdenado
import com.gruposanangel.delivery.Itinerario
import com.gruposanangel.delivery.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GestionRutasUiState(
    val isLoading: Boolean = false,
    val rutas: List<RutaEntity> = emptyList(),
    val clientesDisponibles: List<ClienteEntity> = emptyList(),
    val selectedRutaBase: String? = null,
    val selectedDay: String? = null,
    val selectedWeek: String? = null,
    val selectedClientIds: Set<String> = emptySet(),
    val itinerariosResumen: List<Itinerario> = emptyList(),
    val esRutaExistente: Boolean = false,
    val searchQuery: String = "",
    val error: String? = null,
    val successMessage: String? = null
)

class GestionRutasViewModel(
    private val repositoryRuta: RepositoryRuta,
    private val repositoryCliente: RepositoryCliente
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _uiState = MutableStateFlow(GestionRutasUiState())
    val uiState: StateFlow<GestionRutasUiState> = _uiState.asStateFlow()

    init {
        cargarDatos()
        cargarResumenItinerarios()
    }

    private fun cargarDatos() {
        _uiState.update { it.copy(isLoading = true) }
        
        // 🚀 Lanzamos la sincronización en paralelo sin bloquear el flujo local
        viewModelScope.launch {
            repositoryRuta.descargarRutasDesdeFirebase()
        }

        viewModelScope.launch {
            combine(
                repositoryRuta.obtenerRutasLocal(),
                repositoryCliente.obtenerClientesLocal(),
                _searchQuery
            ) { rutas, clientes, query ->
                // 🚀 Procesamiento en hilo de computación (Default) para no trabar la UI
                withContext(Dispatchers.Default) {
                    val filtrados = if (query.isBlank()) {
                        clientes
                    } else {
                        clientes.filter { 
                            it.nombreNegocio.contains(query, ignoreCase = true) || 
                            it.nombreDueno.contains(query, ignoreCase = true) 
                        }
                    }
                    
                    Triple(rutas, filtrados, query)
                }
            }.collect { (rutas, filtrados, query) ->
                _uiState.update { it.copy(
                    rutas = rutas,
                    clientesDisponibles = filtrados,
                    searchQuery = query,
                    isLoading = false
                ) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun cargarResumenItinerarios() {
        viewModelScope.launch {
            val resumen = repositoryRuta.obtenerTodosLosItinerarios()
            _uiState.update { it.copy(itinerariosResumen = resumen) }
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

    /**
     * Mantiene los clientes actualmente seleccionados pero cambia los parámetros de la ruta.
     * Útil para copiar una ruta de un día a otro.
     */
    fun mantenerClientesCambiarParametros(nuevaRuta: String?, nuevoDia: String?, nuevaSemana: String?) {
        _uiState.update { state ->
            state.copy(
                selectedRutaBase = nuevaRuta ?: state.selectedRutaBase,
                selectedDay = nuevoDia ?: state.selectedDay,
                selectedWeek = nuevaSemana ?: state.selectedWeek,
                esRutaExistente = false // Al cambiar parámetros para copiar, se asume que es una configuración nueva en ese destino
            )
        }
    }

    private fun actualizarClientesEnRuta() {
        val state = _uiState.value
        val rb = state.selectedRutaBase ?: return
        val sd = state.selectedDay ?: return
        val sw = state.selectedWeek ?: return
        
        // Generamos el ID compuesto: Ruta1_Lun_Par
        val itinerarioId = "${rb.replace(" ", "")}_${sd}_${sw}"
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val itinerario = repositoryRuta.obtenerItinerario(itinerarioId)
            
            val ids = itinerario?.clientesOrdenados?.map { it.clienteId }?.toSet() ?: emptySet()
            
            _uiState.update { it.copy(
                selectedClientIds = ids,
                esRutaExistente = itinerario != null,
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
        
        // VALIDACIÓN OBLIGATORIA
        if (state.selectedRutaBase == null || state.selectedDay == null || state.selectedWeek == null) {
            _uiState.update { it.copy(error = "Por favor selecciona Ruta, Día y Ciclo") }
            return
        }

        val rb = state.selectedRutaBase
        val sd = state.selectedDay
        val sw = state.selectedWeek
        
        // ID Compuesto: Ruta1_Lun_Par
        val itinerarioId = "${rb.replace(" ", "")}_${sd}_${sw}"
        
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            try {
                // Creamos la lista ordenada (por ahora simplemente el orden en que están en el Set)
                val clientesOrdenados = state.selectedClientIds.mapIndexed { index, id ->
                    ClienteOrdenado(clienteId = id, ordenVisita = index + 1)
                }

                val nuevoItinerario = Itinerario(
                    id = itinerarioId,
                    rutaId = rb,
                    diaSemana = sd,
                    frecuencia = sw,
                    activo = true,
                    clientesOrdenados = clientesOrdenados,
                    lastUpdated = System.currentTimeMillis()
                )
                
                repositoryRuta.guardarItinerario(nuevoItinerario)
                cargarResumenItinerarios() // Actualizar resumen después de guardar
                
                _uiState.update { it.copy(
                    isLoading = false, 
                    successMessage = "Itinerario guardado: $rb ($sd $sw)"
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
