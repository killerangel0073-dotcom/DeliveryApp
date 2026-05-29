package com.gruposanangel.delivery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.SegundoPlano.LocationService
import com.gruposanangel.delivery.SegundoPlano.scheduleSyncWorkers
import com.gruposanangel.delivery.ui.screens.*
import com.gruposanangel.delivery.utilidades.FcmUtils
import com.gruposanangel.delivery.utilidades.HardLockPermissionWrapper
import com.gruposanangel.delivery.utilidades.hayInternet
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var usuarioDao: UsuarioDao
    private lateinit var repositoryUsuario: RepositoryUsuario
    private lateinit var inventarioRepo: RepositoryInventario
    private lateinit var ventaRepository: VentaRepository
    private lateinit var repository: RepositoryCliente
    private var syncJob: Job? = null
    private var locationServiceStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleSyncWorkers(this)
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())

        val db = AppDatabase.getDatabase(this)
        usuarioDao = db.usuarioDao(); repositoryUsuario = RepositoryUsuario(FirebaseDataSource(), usuarioDao)
        repository = RepositoryCliente(db.clienteDao()); inventarioRepo = RepositoryInventario(FirebaseDataSource(), db.productoDao(), db.VentaDao())
        ventaRepository = VentaRepository(db.VentaDao())

        syncJob = startForegroundSyncLoop()
        iniciarSincronizacionInmediata()

        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            val usuarioActual by repositoryUsuario.getUsuarioActual().collectAsState(initial = null)
            val context = LocalContext.current; val sysUI = rememberSystemUiController()
            SideEffect { sysUI.setSystemBarsColor(Color.Red, darkIcons = false); sysUI.setNavigationBarColor(Color.Black, darkIcons = true) }

            Box(Modifier.fillMaxSize().background(Color.Red)) {
                HardLockPermissionWrapper {
                    if (!locationServiceStarted) startLocationService()
                    if (usuarioActual != null) { Navegador(repository = repository, onLogout = { cerrarSesion(context) }) }
                    else { PantallaLoginPro { iniciarSincronizacionInmediata() } }
                }
            }
        }
    }

    private fun iniciarSincronizacionInmediata() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                FcmUtils.updateFcmToken(uid)
                repositoryUsuario.sincronizarVendedorLocal(uid)
                repository.descargarClientesFirebase(this@MainActivity)
                inventarioRepo.descargarProductosFirebase(uid)
                ventaRepository.descargarVentasDia(uid) // 🔥 Descargamos las ventas del día para evitar lista vacía
                repository.escucharCambiosFirebase(this@MainActivity)
                inventarioRepo.escucharCambiosFirebase(uid)
            } catch (e: Exception) { Log.e("SYNC", "Error inicial", e) }
        }
    }

    private fun startForegroundSyncLoop(): Job = lifecycleScope.launch(Dispatchers.IO) {
        while (isActive) {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null && hayInternet(this@MainActivity)) {
                try {
                    val pendientes = ventaRepository.obtenerVentasPendientes()
                    val uid = user.uid; val almacenId = inventarioRepo.getAlmacenVendedor(uid)
                    if (almacenId != null) {
                        pendientes.forEach { v ->
                            val prods = ventaRepository.obtenerDetallesDeVenta(v.id).map { Plantilla_Producto(it.productoId, it.nombre, it.precio, it.cantidad) }
                            val (exito, msg) = ventaRepository.sincronizarConServidor(v.id, v.clienteId, v.clienteNombre, prods, v.metodoPago, v.vendedorId, almacenId)
                            if (exito) { val fId = try { JSONObject(msg).optString("ventaId") } catch (_: Exception) { null }; if (!fId.isNullOrEmpty()) ventaRepository.marcarVentaConFirestoreId(v.id, fId) }
                        }
                    }
                } catch (e: Exception) { }
            }
            delay(10000)
        }
    }

    private fun startLocationService() { if (!locationServiceStarted) { ContextCompat.startForegroundService(this, Intent(this, LocationService::class.java).apply { action = LocationService.ACTION_START }); locationServiceStarted = true } }
    override fun onResume() { super.onResume(); if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && !LocationService.isRunning) locationServiceStarted = false }
    override fun onDestroy() { super.onDestroy(); repository.stopEscuchaFirebase(); inventarioRepo.stopEscuchaFirebase(); syncJob?.cancel() }

    fun cerrarSesion(context: Context) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (uid != null) {
                    // 1. Obtener el token actual de forma segura
                    val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                    
                    // 2. Eliminación atómica únicamente de este dispositivo
                    FcmUtils.removeTokenFromArray(uid, token)
                }
            } catch (e: Exception) {
                Log.e("LOGOUT", "Error limpiando tokens FCM: ${e.message}")
            } finally {
                // 3. Cierre de sesión seguro (Incluso si falla lo anterior para no bloquear al usuario)
                repositoryUsuario.cerrarSesion()
                withContext(Dispatchers.Main) {
                    prefs.edit().remove("fcm_token").apply()
                    auth.signOut()
                    // El estado usuarioActual se volverá null y disparará la PantallaLoginPro automáticamente
                }
            }
        }
    }
}
