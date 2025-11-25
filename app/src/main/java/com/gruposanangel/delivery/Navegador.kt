package com.gruposanangel.delivery.ui.screens

import TicketVentaCompleto
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaDao
import com.gruposanangel.delivery.data.VentaRepository
import com.gruposanangel.delivery.model.Plantila_carga

// ---------------------------
// NAV HOST
// ---------------------------
@Composable
fun Navegador(repository: RepositoryCliente?, onLogout: () -> Unit = {}) {
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





    }
}
