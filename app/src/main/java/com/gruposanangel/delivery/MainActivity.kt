package com.gruposanangel.delivery


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.SegundoPlano.LocationService
import com.gruposanangel.delivery.SegundoPlano.scheduleSyncWorkers
import com.gruposanangel.delivery.ui.screens.*
import com.gruposanangel.delivery.utilidades.FcmUtils

import kotlinx.coroutines.*
import org.json.JSONObject


class MainActivity : ComponentActivity() {






    private lateinit var usuarioDao: UsuarioDao
    private lateinit var repositoryUsuario: RepositoryUsuario
    private lateinit var inventarioRepo: RepositoryInventario
    private lateinit var ventaRepository: VentaRepository
    private lateinit var vistaModeloVenta: VistaModeloVenta
    private lateinit var repository: RepositoryCliente

    private var syncJob: Job? = null
    private var locationServiceStarted = false


    private val ventaIdToOpenMapaState = mutableStateOf<Long?>(null)
    private val openMapaState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)
        scheduleSyncWorkers(this)

        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance()
            .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())

        val db = AppDatabase.getDatabase(this)
        val clienteDao = db.clienteDao()
        usuarioDao = db.usuarioDao()

        repositoryUsuario = RepositoryUsuario(usuarioDao)  // <-- pasamos dao aquí
        repository = RepositoryCliente(clienteDao)
        inventarioRepo = RepositoryInventario(db.productoDao())
        ventaRepository = VentaRepository(db.VentaDao())
        vistaModeloVenta = VistaModeloVenta(inventarioRepo, ventaRepository)







        syncJob?.cancel()
        syncJob = startForegroundSyncLoop()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                repositoryUsuario.sincronizarVendedorLocal(uid)  // <-- solo uid
                lifecycleScope.launch(Dispatchers.IO) {
                    repository.escucharCambiosFirebase(this@MainActivity) // ✅ esto funciona
                }


                inventarioRepo.escucharCambiosFirebase(uid)
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            val currentUser = FirebaseAuth.getInstance().currentUser
            var loggedIn by remember { mutableStateOf(currentUser != null) }
            val context = LocalContext.current
            val navController = rememberNavController()


            val systemUiController = rememberSystemUiController()
            SideEffect {
                systemUiController.setSystemBarsColor(color = Color.Red, darkIcons = false)
                systemUiController.setNavigationBarColor(color = Color.Black, darkIcons = true)
            }

            Box(modifier = Modifier.fillMaxSize().background(Color.Red)) {


                PermissionGate(
                    onAllRequiredChecksPassed = {

                        if (!locationServiceStarted) {
                            startLocationService()
                        }


                        if (loggedIn) {
                            Navegador(
                                repository = repository,
                                onLogout = { cerrarSesion(context) { loggedIn = false } },
                                autoOpenTicketId = ventaIdToOpenMapaState.value
                            )
                        } else {
                            PantallaLoginPro(
                                onLoginSuccess = { loggedIn = true }
                            )
                        }
                    }
                )



            }
        }
    }


    override fun onResume() {
        super.onResume()

        if (
            LocationService.isRunning.not() &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationServiceStarted = false
        }
    }



    private fun startForegroundSyncLoop(): Job = lifecycleScope.launch(Dispatchers.IO) {
        Log.d("SYNC", "Loop de sincronización iniciado")
        while (isActive) {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null && isNetworkAvailable(this@MainActivity)) {
                try {
                    lifecycleScope.launch(Dispatchers.IO) {
                        //repository.escucharCambiosFirebase(this@MainActivity) // ✅ esto funciona
                    }



                    val pendientes = ventaRepository.obtenerVentasPendientes()
                    val uid = user.uid
                    val almacenId = inventarioRepo.getAlmacenVendedor(uid)
                    if (almacenId == null) {
                        Log.w("SYNC", "almacenId null — esperando siguiente intento")
                        delay(5000)
                        continue
                    }

                    pendientes.forEach { venta ->
                        val productos = ventaRepository.obtenerDetallesDeVenta(venta.id).map {
                            Plantilla_Producto(it.productoId, it.nombre, it.precio, it.cantidad)
                        }

                        val (exito, mensaje) = vistaModeloVenta.guardarVentaEnServidorSuspend(
                            venta.id,
                            venta.clienteId,
                            venta.clienteNombre,
                            productos,
                            venta.metodoPago,
                            venta.vendedorId,
                            almacenId
                        )
                        if (exito) {
                            val firestoreId = try { JSONObject(mensaje).optString("ventaId") } catch (e: Exception) { null }
                            if (!firestoreId.isNullOrEmpty())
                                ventaRepository.marcarVentaConFirestoreId(venta.id, firestoreId)
                        }
                    }

                } catch (e: Exception) {
                    Log.e("SYNC", "Error en sincronización", e)
                }
            }
            delay(5000)
        }
    }

    private fun startLocationService() {
        if (locationServiceStarted) return
        val intent = Intent(this, LocationService::class.java).apply { action = LocationService.ACTION_START }
        ContextCompat.startForegroundService(this, intent)
        locationServiceStarted = true
    }




    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "OPEN_MAPA") {
            openMapaState.value = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::repository.isInitialized) {
            repository.stopEscuchaFirebase()
            inventarioRepo.stopEscuchaFirebase()
        }
        syncJob?.cancel()
    }

    fun cerrarSesion(context: Context, onComplete: () -> Unit = {}) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)

        val savedToken = currentUser?.uid?.let { prefs.getString("fcm_token", null) }
        if (savedToken != null) {
            CoroutineScope(Dispatchers.IO).launch {
                FcmUtils.removeTokenFromArray(currentUser.uid, savedToken)
                withContext(Dispatchers.Main) {
                    prefs.edit().remove("fcm_token").apply()
                    auth.signOut()
                    onComplete()
                }
            }
        } else {
            prefs.edit().remove("fcm_token").apply()
            auth.signOut()
            onComplete()
        }
    }
}
