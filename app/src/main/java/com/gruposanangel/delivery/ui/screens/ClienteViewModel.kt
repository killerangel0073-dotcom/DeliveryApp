package com.gruposanangel.delivery.ui.screens

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.Plantilla_Cliente
import com.gruposanangel.delivery.SegundoPlano.LocationState
import kotlinx.coroutines.launch
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

    // 🔥 OFFLINE-FIRST: Escuchamos la ubicación actual del vendedor
    private val _ubicacionVendedor = LocationState.ultimaUbicacion

    // Combinamos la lista base con la consulta de búsqueda y la ubicación
    val uiState: StateFlow<ClienteUiState> = combine(
        _clientesBase,
        _searchQuery,
        _ubicacionVendedor
    ) { clientes, query, miUbicacion ->
        
        // 1. Filtrar por búsqueda
        val filtrados = if (query.isBlank()) {
            clientes
        } else {
            clientes.filter { 
                it.nombreNegocio.contains(query, ignoreCase = true) || 
                it.nombreDueno.contains(query, ignoreCase = true) 
            }
        }

        // 2. Mapear y Calcular Distancias
        val listaConDistancia = filtrados.map { dbItem ->
            val distanciaM = if (miUbicacion != null && dbItem.ubicacionLat != 0.0) {
                val locCliente = Location("").apply {
                    latitude = dbItem.ubicacionLat
                    longitude = dbItem.ubicacionLon
                }
                miUbicacion.distanceTo(locCliente)
            } else -1f

            val distTexto = when {
                distanciaM < 0 -> "Sin ubicación"
                distanciaM < 1000 -> "${distanciaM.toInt()}m"
                else -> String.format("%.1f km", distanciaM / 1000f)
            }

            Plantilla_Cliente(
                id = dbItem.id,
                nombreNegocio = dbItem.nombreNegocio,
                nombreDueno = dbItem.nombreDueno,
                fotografiaCliente = dbItem.fotografiaUrl ?: "",
                activo = dbItem.activo,
                // Asumimos que agregaremos estos campos a Plantilla_Cliente para la UI
                distanciaMetros = distanciaM,
                distanciaTexto = distTexto
            )
        }

        // 3. ORDENAR POR PROXIMIDAD (Solo si hay distancia válida)
        val listaOrdenada = listaConDistancia.sortedWith(compareBy<Plantilla_Cliente> { it.distanciaMetros < 0 }.thenBy { it.distanciaMetros })
        
        ClienteUiState(
            clientes = listaOrdenada,
            searchQuery = query,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ClienteUiState(isLoading = true)
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun syncData(context: android.content.Context) {
        viewModelScope.launch {
            repository.descargarClientesFirebase(context)
        }
    }
}
