package com.gruposanangel.delivery.ui.screens


import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.*

import androidx.compose.ui.platform.LocalContext

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.gruposanangel.delivery.MainActivity
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.RepositoryInventario

import com.gruposanangel.delivery.data.VentaRepository
import com.gruposanangel.delivery.model.Plantila_carga

// ---------------------------
// NAV HOST
// ---------------------------
@Composable
fun Navegador(
    repository: RepositoryCliente?,
    onLogout: () -> Unit = {},
    autoOpenTicketId: Long? = null // ⚡ Nuevo parámetro
) {




    val navController = rememberNavController()
    var impresoraBluetooth by remember { mutableStateOf<BluetoothDevice?>(null) }
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val ventaRepository = VentaRepository(db.VentaDao())


    NavHost(navController = navController, startDestination = "delivery?screen=Inicio") {


        // Pantalla principal con navegación inferior
        composable(
            "delivery?screen={screen}",
            arguments = listOf(navArgument("screen") { defaultValue = "Inicio" })
        ) { backStackEntry ->
            val screenArg = backStackEntry.arguments?.getString("screen") ?: "Inicio"
            val context = LocalContext.current
            val db = AppDatabase.getDatabase(context)
            val inventarioRepo = RepositoryInventario(db.productoDao())

            Pantalla_Principal(
                navController = navController,
                startScreen = screenArg,
                repository = repository,
                inventarioRepo = inventarioRepo,

                onLogout = onLogout,
                impresoraBluetooth = impresoraBluetooth,
                onImpresoraSeleccionada = { device -> impresoraBluetooth = device }
            )
        }

        // Lista de productos
        composable("LISTA PRODUCTOS") {
            MovimientosInventarioScreen(
                navController = navController,
                impresoraBluetooth = impresoraBluetooth,
                onImpresoraSeleccionada = { device -> impresoraBluetooth = device }
            )
        }

        // Notificaciones
        composable("NOTIFICACIONES") {
            PantallaNotificaciones(navController)



        }





        // Detalle de carga
        composable("DETALLE_CARGA") {
            val plantilacarga = navController
                .previousBackStackEntry
                ?.savedStateHandle
                ?.get<Plantila_carga>("carga")

            PantallaDetalleCarga(navController, plantilacarga)
        }





        // Inventario del vendedor
        composable("INVENTARIO VENDEDROR") {
            val context = LocalContext.current
            val db = AppDatabase.getDatabase(context)
            val inventarioRepo = RepositoryInventario(db.productoDao())
            PantallaInventario(navController, inventarioRepo)
        }





        // Perfil de usuario
        composable("perfil_usuario") {
            val context = LocalContext.current
            val usuarioDao = AppDatabase.getDatabase(context).usuarioDao() // <-- Obtenemos el DAO aquí

            PerfilDeUsuarioScreen(
                navController = navController,
                usuarioDao = usuarioDao
            )
        }




        // Crear cliente
        composable("crear_cliente") {
            CrearClienteScreen(navController, repository!!)
        }








        // Mapa
        composable("MAPA_SCREEN") {
            MapaScreen(navController = navController)
        }






        // Lista de clientes
        composable(
            route = "detalle_ticket_completo/{ticketId}",
            arguments = listOf(navArgument("ticketId") { type = NavType.LongType })
        ) { backStackEntry ->
            val ticketId = backStackEntry.arguments?.getLong("ticketId") ?: 0L



            DetalleTicketScreen(
                navController = navController,
                ticketId = ticketId,
                ventaRepository = ventaRepository, // 🔹 pasar tu repositorio aquí
                impresoraBluetooth = impresoraBluetooth // 🔹 PASAR LA IMPRESORA AQUÍ
            )
        }



        // 🔍 DETALLE DE CLIENTE
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
                origen = origen // 🔹 pasamos el origen
            )
        }






        // Ventas por periodo
        composable("ventas_periodo") {
            val context = LocalContext.current

            // 1️⃣ Obtener la base de datos y los DAOs
            val db = AppDatabase.getDatabase(context)
            val ventaDao = db.VentaDao()
            val productoDao = db.productoDao()

            // 2️⃣ Crear repositorios
            val ventaRepository = VentaRepository(ventaDao)
            val inventarioRepo = RepositoryInventario(productoDao)

            // 3️⃣ Crear ViewModel usando la factory con ambos repositorios
            val viewModel: VistaModeloVenta = viewModel(
                factory = VistaModeloVentaFactory(
                    repositoryInventario = inventarioRepo,
                    ventaRepository = ventaRepository
                )
            )

            // 4️⃣ Llamar a la pantalla
            PantallaVentaPeriodo(
                navController = navController,
                vistaModelo = viewModel
            )
        }






        // Pantalla de venta de cliente (usa id y repo local)
        composable(
            route = "pantalla_venta/{idCliente}",
            arguments = listOf(navArgument("idCliente") { type = NavType.StringType })
        ) { backStackEntry ->
            val idCliente = backStackEntry.arguments?.getString("idCliente") ?: ""
            val context = LocalContext.current
            val db = AppDatabase.getDatabase(context)
            val inventarioRepo = RepositoryInventario(db.productoDao()) // <-- crear repo local

            PantallaVenta(
                navController = navController,
                clienteId = idCliente,
                repository = repository,
                inventarioRepo = inventarioRepo,           // <-- PASAR inventarioRepo
                impresoraBluetooth = impresoraBluetooth,
                productosPreview = null
            )
        }

        composable(
            route = "detalle_ticket_supervisor/{firestoreId}"
        ) { backStackEntry ->

            val firestoreId = backStackEntry.arguments?.getString("firestoreId") ?: ""

            DetalleTicketSupervisorScreen(
                firestoreId = firestoreId,
                navController = navController,
                impresoraBluetooth = null // si el supervisor imprime
            )
        }



        // PantallaVentas2 (Nueva pantalla)
        composable(
            route = "pantalla_ventas2/{clienteId}",
            arguments = listOf(navArgument("clienteId") { type = NavType.StringType })
        ) { backStackEntry ->

            val clienteId = backStackEntry.arguments?.getString("clienteId") ?: ""
            val context = LocalContext.current
            val db = AppDatabase.getDatabase(context)

            val inventarioRepo = RepositoryInventario(db.productoDao())
            val ventaRepository = VentaRepository(db.VentaDao())

            PantallaVentas2(
                navController = navController,
                clienteId = clienteId,
                repository = repository,            // 🔹 SE LO PASAMOS DIRECTO (lo recibes en Navegador)
                inventarioRepo = inventarioRepo,    // 🔹 El repo local del inventario
                impresoraBluetooth = impresoraBluetooth,
                productosPreview = null
            )
        }






    }

    LaunchedEffect(autoOpenTicketId) {
        autoOpenTicketId?.let { id ->

            // Guardamos el ID en savedStateHandle para que la pantalla lo lea si lo necesita
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set("ticketId", id)

            navController.navigate("detalle_ticket_completo/$id") {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }
    }



    val openScreen = (context as? MainActivity)?.intent?.action

    LaunchedEffect(openScreen) {
        if (openScreen == "OPEN_MAPA") {
            navController.navigate("MAPA_SCREEN") {
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                launchSingleTop = true
            }
        }
    }



}
