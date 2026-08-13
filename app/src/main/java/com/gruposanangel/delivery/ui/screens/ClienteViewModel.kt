package com.gruposanangel.delivery.ui.screens

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.Plantilla_Cliente
import com.gruposanangel.delivery.SegundoPlano.LocationState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.UsuarioEntity

data class ClienteUiState(
    val clientes: List<Plantilla_Cliente> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val totalClientes: Int = 0,
    val isAdmin: Boolean = false,
    val rutaAsignada: String? = null,
    
    // 🔥 Guía Histórica (14 días atrás)
    val idsVisitadosCicloAnterior: Set<String> = emptySet(),
    val metaVentaPasada: Double = 0.0,
    val visitasPasadasCount: Int = 0,
    val filtrandoSoloCicloAnterior: Boolean = false,
    val fechaCicloAnterior: Long? = null // 🔥 Nueva: Para mostrar "Martes 15 Feb"
)

class ClienteViewModel(
    private val repository: RepositoryCliente,
    private val usuarioRepo: com.gruposanangel.delivery.RepositoryUsuario? = null,
    private val ventaRepo: com.gruposanangel.delivery.VentaRepository? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isAdminOverride = MutableStateFlow<Boolean?>(null)
    private val _filtrandoSoloCicloAnterior = MutableStateFlow(false)

    fun configurarModo(esAdmin: Boolean) {
        _isAdminOverride.value = esAdmin
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleFiltroCicloAnterior() {
        _filtrandoSoloCicloAnterior.value = !_filtrandoSoloCicloAnterior.value
    }

    private val _clientesBase = repository.obtenerClientesLocal()
    private val _usuarioActual = usuarioRepo?.getUsuarioActual() ?: flowOf(null)
    private val _ubicacionVendedor = LocationState.ultimaUbicacion

    val uiState: StateFlow<ClienteUiState> = combine(
        _clientesBase,
        _searchQuery,
        _ubicacionVendedor,
        _isAdminOverride,
        _usuarioActual,
        _filtrandoSoloCicloAnterior
    ) { flowArray ->
        val clientes = flowArray[0] as List<com.gruposanangel.delivery.data.ClienteEntity>
        val query = flowArray[1] as String
        val miUbicacion = flowArray[2] as Location?
        val overrideAdmin = flowArray[3] as Boolean?
        val usuario = flowArray[4] as UsuarioEntity?
        val filtrandoCiclo = flowArray[5] as Boolean
        
        // 1. DETERMINAR ROL Y MODO
        val puesto = (usuario?.puestoTrabajo ?: "").uppercase()
        val esAdminReal = puesto.contains("CEO") || puesto.contains("GERENTE") || puesto.contains("SUPERVISOR") || puesto.contains("ADMIN")
        val modoVendedorActivo = (esAdminReal && overrideAdmin == false) || (!esAdminReal)
        
        val rutaNombrePerfil = usuario?.ultimaRutaNombre?.trim() ?: ""
        val rutaIdPerfil = usuario?.ultimaRutaId?.trim() ?: ""
        val almacenIdPerfil = usuario?.ultimoAlmacenId?.trim() ?: ""
        val almacenNombrePerfil = usuario?.ultimoAlmacenNombre?.trim() ?: ""
        
        // 🔥 LÓGICA DE RASTREO PASSIVE: Actualizamos ubicación local para que el VM siempre tenga distancias
        // miUbicacion viene directamente de LocationState.ultimaUbicacion (gestionado por el Service)

        // 🔥 2. CÁLCULO DE GUÍA HISTÓRICA (14 DÍAS ATRÁS - BASADO EN RUTA)
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -14)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0)
        val inicioPasado = cal.timeInMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23); cal.set(java.util.Calendar.MINUTE, 59); cal.set(java.util.Calendar.SECOND, 59)
        val finPasado = cal.timeInMillis

        // Consultamos ventas pasadas por UNIDAD (Ruta/Almacén) no por Vendedor
        val ventasPasadas = ventaRepo?.obtenerVentasPorUnidadPeriodo(
            almacenIdPerfil.ifEmpty { almacenNombrePerfil }, 
            rutaNombrePerfil, 
            rutaIdPerfil, 
            inicioPasado, 
            finPasado
        ) ?: emptyList()
        
        val mapaVentasPasadas = ventasPasadas.groupBy { it.clienteId }
            .mapValues { entry -> entry.value.sumOf { it.total } }
        
        val idsVisitadosAnteriormente = mapaVentasPasadas.keys
        val metaVentaPasada = ventasPasadas.sumOf { it.total }
        val visitasPasadasCount = idsVisitadosAnteriormente.size

        // 3. APLICAR FILTRO DE RUTA
        val clientesFiltradosPorRuta = if (modoVendedorActivo && (rutaNombrePerfil.isNotEmpty() || rutaIdPerfil.isNotEmpty())) {
            clientes.filter { cliente ->
                val cRuta = cliente.rutaId?.trim() ?: ""
                cRuta.equals(rutaNombrePerfil, ignoreCase = true) || cRuta.equals(rutaIdPerfil, ignoreCase = true)
            }
        } else {
            clientes 
        }

        // 🔥 4. APLICAR FILTRO DE CICLO ANTERIOR (Si está activo)
        val filtradosPorCiclo = if (filtrandoCiclo && modoVendedorActivo) {
            clientesFiltradosPorRuta.filter { idsVisitadosAnteriormente.contains(it.id) }
        } else {
            clientesFiltradosPorRuta
        }

        // 5. FILTRAR POR BÚSQUEDA
        val filtradosPorBusqueda = if (query.isBlank()) {
            filtradosPorCiclo
        } else {
            filtradosPorCiclo.filter { 
                it.nombreNegocio.contains(query, ignoreCase = true) || 
                it.nombreDueno.contains(query, ignoreCase = true) 
            }
        }

        // 6. MAPEAR A UI (Calcular distancias y Marcar históricos)
        val listaMapeada = filtradosPorBusqueda.map { dbItem ->
            val locCliente = Location("").apply {
                latitude = dbItem.ubicacionLat
                longitude = dbItem.ubicacionLon
            }
            
            val distancia = if (miUbicacion != null) miUbicacion.distanceTo(locCliente) else -1f
            val distTexto = when {
                distancia == -1f -> ""
                distancia < 1000 -> "${distancia.toInt()} m"
                else -> String.format(java.util.Locale.US, "%.1f km", distancia / 1000f)
            }

            Plantilla_Cliente(
                id = dbItem.id,
                nombreNegocio = dbItem.nombreNegocio,
                nombreDueno = dbItem.nombreDueno,
                fotografiaCliente = dbItem.fotografiaUrl ?: "",
                activo = dbItem.activo,
                distanciaMetros = distancia,
                distanciaTexto = distTexto,
                rutaId = dbItem.rutaId,
                visitadoAnteriormente = idsVisitadosAnteriormente.contains(dbItem.id),
                montoCompraPasada = mapaVentasPasadas[dbItem.id] ?: 0.0,
                valor = dbItem.valorCliente
            )
        }

        // 7. ORDENAMIENTO (Prioridad: 1. Ciclo Pasado si no se busca, 2. Distancia)
        val listaFinal = if (query.isBlank()) {
            listaMapeada.sortedWith(
                compareByDescending<Plantilla_Cliente> { it.visitadoAnteriormente }
                    .thenBy { if (it.distanciaMetros == -1f) Float.MAX_VALUE else it.distanciaMetros }
            )
        } else {
            listaMapeada.sortedBy { if (it.distanciaMetros == -1f) Float.MAX_VALUE else it.distanciaMetros }
        }

        ClienteUiState(
            clientes = listaFinal,
            searchQuery = query,
            isLoading = false,
            totalClientes = listaFinal.size,
            isAdmin = esAdminReal && (overrideAdmin != false),
            rutaAsignada = if (modoVendedorActivo) (usuario?.ultimaRutaNombre ?: "Ruta") else "VISTA GLOBAL",
            idsVisitadosCicloAnterior = idsVisitadosAnteriormente,
            metaVentaPasada = metaVentaPasada,
            visitasPasadasCount = visitasPasadasCount,
            filtrandoSoloCicloAnterior = filtrandoCiclo,
            fechaCicloAnterior = inicioPasado
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ClienteUiState()
    )

    fun syncData(context: android.content.Context) {
        viewModelScope.launch {
            repository.descargarClientesFirebase(context)
        }
    }
}
