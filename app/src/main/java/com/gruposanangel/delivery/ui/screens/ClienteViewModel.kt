package com.gruposanangel.delivery.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.Plantilla_Cliente
import kotlinx.coroutines.flow.*

data class ClienteUiState(
    val clientes: List<Plantilla_Cliente> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true
)

class ClienteViewModel(
    private val repository: RepositoryCliente
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Observamos los clientes locales del repositorio
    private val _clientesBase = repository.obtenerClientesLocal()

    // Combinamos la lista base con la consulta de búsqueda de forma eficiente
    val uiState: StateFlow<ClienteUiState> = combine(
        _clientesBase,
        _searchQuery
    ) { clientes, query ->
        val filtrados = if (query.isBlank()) {
            clientes
        } else {
            clientes.filter { 
                it.nombreNegocio.contains(query, ignoreCase = true) || 
                it.nombreDueno.contains(query, ignoreCase = true) 
            }
        }
        
        ClienteUiState(
            clientes = filtrados.map { dbItem ->
                Plantilla_Cliente(
                    id = dbItem.id,
                    nombreNegocio = dbItem.nombreNegocio,
                    nombreDueno = dbItem.nombreDueno,
                    fotografiaCliente = dbItem.fotografiaUrl ?: "",
                    activo = dbItem.activo
                )
            },
            searchQuery = query,
            // Solo quitamos el loader si ya hay clientes o si Room ya emitió
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly, // Cambiado a Eagerly para que empiece a escuchar de inmediato
        initialValue = ClienteUiState(isLoading = true)
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }
}
