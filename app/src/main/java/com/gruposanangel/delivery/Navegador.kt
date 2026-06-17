package com.gruposanangel.delivery

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.model.Plantila_carga
import com.gruposanangel.delivery.ui.screens.*

@Composable
fun Navegador(
    repository: RepositoryCliente?,
    onLogout: () -> Unit = {},
    autoOpenTicketId: String? = null,
    intentAction: String? = null,
    intentExtras: android.os.Bundle? = null
) {
    val navController = rememberNavController()
    // 🔥 ESTADO DE IMPRESORA GLOBAL
    var impresoraBluetooth by remember { mutableStateOf<BluetoothDevice?>(null) }
    val context = LocalContext.current
    
    val setImpresora: (BluetoothDevice) -> Unit = { device ->
        impresoraBluetooth = device
    }

    NavHost(navController = navController, startDestination = "delivery?screen=Inicio") {
        composable(
            "delivery?screen={screen}",
            arguments = listOf(navArgument("screen") { defaultValue = "Inicio" })
        ) { backStackEntry ->
            val screenArg = backStackEntry.arguments?.getString("screen") ?: "Inicio"
            val db = AppDatabase.getDatabase(context)
            val firebaseDataSource = FirebaseDataSource()
            val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())

            Pantalla_Principal(
                navController = navController,
                startScreen = screenArg,
                repository = repository,
                inventarioRepo = inventarioRepo,
                onLogout = onLogout,
                impresoraBluetooth = impresoraBluetooth,
                onImpresoraSeleccionada = setImpresora
            )
        }

        composable(
            route = "LISTA PRODUCTOS?origen={origen}&destino={destino}&emergency={emergency}",
            arguments = listOf(
                navArgument("origen") { type = NavType.StringType; nullable = true },
                navArgument("destino") { type = NavType.StringType; nullable = true },
                navArgument("emergency") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val origen = backStackEntry.arguments?.getString("origen")
            val destino = backStackEntry.arguments?.getString("destino")
            val isEmergency = backStackEntry.arguments?.getBoolean("emergency") ?: false
            
            MovimientosInventarioScreen(
                navController = navController,
                impresoraBluetooth = impresoraBluetooth,
                onImpresoraSeleccionada = { device -> impresoraBluetooth = device },
                preSelectedOrigen = origen,
                preSelectedDestino = destino,
                isEmergency = isEmergency
            )
        }

        composable("NOTIFICACIONES") {
            PantallaNotificaciones(navController)
        }

        composable("DETALLE_CARGA") {
            val plantilacarga = navController
                .previousBackStackEntry
                ?.savedStateHandle
                ?.get<Plantila_carga>("carga")

            PantallaDetalleCarga(navController, plantilacarga)
        }

        composable("INVENTARIO_VENDEDOR") {
            val db = AppDatabase.getDatabase(context)
            val firebaseDataSource = FirebaseDataSource()
            val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
            PantallaInventario(navController, inventarioRepo)
        }

        composable("perfil_usuario") {
            val usuarioDao = AppDatabase.getDatabase(context).usuarioDao()
            PerfilDeUsuarioScreen(
                navController = navController,
                usuarioDao = usuarioDao
            )
        }

        composable("crear_cliente") {
            CrearClienteScreen(navController, repository!!)
        }

        composable(
            route = "editar_cliente/{clienteId}",
            arguments = listOf(navArgument("clienteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val clienteId = backStackEntry.arguments?.getString("clienteId") ?: ""
            EditarClienteScreen(navController, clienteId, repository)
        }

        composable("CREAR_PRODUCTO") {
            CrearProductoScreen(navController)
        }

        composable("PRODUCTOS") {
            ListaProductosScreen(navController)
        }

        composable(
            "EDITAR_PRODUCTOS/{productoId}",
            arguments = listOf(navArgument("productoId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("productoId")
            EditarProductoScreen(navController, productoId = id)
        }

        composable("MAPA_SCREEN") {
            val mapaViewModel: MapaViewModel = viewModel()
            MapaScreen(navController = navController, viewModel = mapaViewModel)
        }

        composable("ADMIN_USUARIOS") {
            Pantalla_Usuarios_Admin(navController)
        }

        composable(
            route = "camara_escaneo_licencia/{userId}/{userName}",
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("userName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val userName = backStackEntry.arguments?.getString("userName") ?: ""
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("ADMIN_USUARIOS") }
            val viewModel: UsuariosAdminViewModel = viewModel(parentEntry)
            
            PantallaCamaraGenerica(
                navController = navController,
                tituloGuia = "Encuadre la Licencia",
                onPhotoCaptured = { file ->
                    viewModel.validarLicenciaConIA(file, userId, userName)
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "camara_escaneo_ine/{userId}/{userName}",
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("userName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            val userName = backStackEntry.arguments?.getString("userName") ?: ""
            val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("ADMIN_USUARIOS") }
            val viewModel: UsuariosAdminViewModel = viewModel(parentEntry)
            
            PantallaCamaraGenerica(
                navController = navController,
                tituloGuia = "Encuadre el INE (Frente)",
                onPhotoCaptured = { file ->
                    viewModel.validarINEConIA(file, userId, userName)
                    navController.popBackStack()
                }
            )
        }

        composable("ADMIN_RUTAS") {
            Pantalla_Gestion_Rutas(navController)
        }

        composable("HISTORIAL_RUTA") {
            Pantalla_Historial_Ruta(navController)
        }

        composable("VENDEDOR_INFO_VENTAS") {
            VendedorInfoVentasScreen()
        }

        composable("PANTALLA_ARQUEO") {
            PantallaArqueo(navController)
        }

        composable("MI_RENDIMIENTO") {
            val db = AppDatabase.getDatabase(context)
            val firebaseDataSource = FirebaseDataSource()
            val usuarioRepo = RepositoryUsuario(firebaseDataSource, db.usuarioDao())
            val ventaRepo = VentaRepository(db.VentaDao())
            val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            
            val viewModel: DashboardVendedorViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = 
                        DashboardVendedorViewModel(ventaRepo, usuarioRepo, inventarioRepo, uid) as T
                }
            )
            val uiState by viewModel.uiState.collectAsState()

            Pantalla_Mi_Rendimiento(
                navController = navController,
                nombreVendedor = uiState.nombreVendedor,
                ventaDia = uiState.ventaDia,
                clientesDia = uiState.clientesDia
            )
        }

        composable("CIERRE_DIA") {
            val db = AppDatabase.getDatabase(context)
            val firebaseDataSource = FirebaseDataSource()
            val usuarioRepo = RepositoryUsuario(firebaseDataSource, db.usuarioDao())
            val ventaRepo = VentaRepository(db.VentaDao())
            val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

            val viewModel: DashboardVendedorViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = 
                        DashboardVendedorViewModel(ventaRepo, usuarioRepo, inventarioRepo, uid) as T
                }
            )
            val uiState by viewModel.uiState.collectAsState()

            PantallaCierreDia(
                navController = navController,
                uiState = uiState
            )
        }

        composable("ventas_room") {
            val db = AppDatabase.getDatabase(context)
            val ventaRepository = VentaRepository(db.VentaDao())

            VentasRoomScreen(
                context = context,
                navController = navController,
                ventaRepository = ventaRepository
            )
        }

        composable(
            route = "detalle_ticket_completo/{ticketId}",
            arguments = listOf(navArgument("ticketId") { type = NavType.StringType })
        ) { backStackEntry ->
            val ticketId = backStackEntry.arguments?.getString("ticketId") ?: ""
            val db = AppDatabase.getDatabase(context)
            val ventaRepository = VentaRepository(db.VentaDao())

            DetalleTicketScreen(
                navController = navController,
                ticketId = ticketId,
                ventaRepository = ventaRepository,
                impresoraBluetooth = impresoraBluetooth
            )
        }

        composable(
            route = "detalle_venta_admin/{ticketId}",
            arguments = listOf(navArgument("ticketId") { type = NavType.StringType })
        ) { backStackEntry ->
            val ticketId = backStackEntry.arguments?.getString("ticketId") ?: ""
            Pantalla_Detalle_Venta_Admin(navController, ticketId, impresoraBluetooth)
        }

        composable(
            route = "detalle_cliente/{clienteId}?origen={origen}",
            arguments = listOf(
                navArgument("clienteId") { type = NavType.StringType },
                navArgument("origen") { type = NavType.StringType; defaultValue = "Clientes"; nullable = true }
            )
        ) { backStackEntry ->
            val clienteId = backStackEntry.arguments?.getString("clienteId") ?: ""
            val origen = backStackEntry.arguments?.getString("origen") ?: "Clientes"

            DetalleClienteScreen(
                clienteId = clienteId,
                navController = navController,
                repository = repository,
                origen = origen
            )
        }

        composable("ventas_periodo") {
            val db = AppDatabase.getDatabase(context)
            val firebaseDataSource = FirebaseDataSource()
            val viewModel: VentaViewModel = viewModel(
                factory = VentaViewModelFactory(
                    repositoryInventario = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao()),
                    ventaRepository = VentaRepository(db.VentaDao()),
                    repositoryUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())
                )
            )

            PantallaVentaPeriodo(
                navController = navController,
                vistaModelo = viewModel
            )
        }




        composable(
            route = "pantalla_ventas/{clienteId}?origen={origen}",
            arguments = listOf(
                navArgument("clienteId") { type = NavType.StringType },
                navArgument("origen") { type = NavType.StringType; defaultValue = "Clientes"; nullable = true }
            )
        ) { backStackEntry ->
            val clienteId = backStackEntry.arguments?.getString("clienteId") ?: ""
            val origen = backStackEntry.arguments?.getString("origen") ?: "Clientes"
            val db = AppDatabase.getDatabase(context)
            val firebaseDataSource = FirebaseDataSource()
            val inventarioRepo = RepositoryInventario(firebaseDataSource, db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())

            PantallaVentas(
                navController = navController,
                clienteId = clienteId,
                repository = repository,
                inventarioRepo = inventarioRepo,
                impresoraBluetooth = impresoraBluetooth,
                origen = origen
            )
        }
    }

    LaunchedEffect(autoOpenTicketId) {
        autoOpenTicketId?.let { id ->
            navController.navigate("detalle_venta_admin/$id") {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(intentAction, intentExtras) {
        if (intentAction == null) return@LaunchedEffect

        android.util.Log.d("NAV_DEBUG", "Intent recibido: Action=$intentAction, Extras=${intentExtras?.keySet()?.joinToString()}")
        when (intentAction) {
            "OPEN_MAPA" -> {
                navController.navigate("delivery?screen=    Mapa    ") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = false }
                    launchSingleTop = true
                }
            }
            "OPEN_NOTIFICACIONES" -> {
                navController.navigate("NOTIFICACIONES") {
                    popUpTo(navController.graph.startDestinationId) { inclusive = false }
                    launchSingleTop = true
                }
            }
            "OPEN_VENTA_DETALLE" -> {
                val ventaId = intentExtras?.getString("ventaId")
                android.util.Log.d("NAV_DEBUG", "Tratando de abrir ventaId: $ventaId")
                if (!ventaId.isNullOrEmpty()) {
                    navController.navigate("detalle_venta_admin/$ventaId") {
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}
