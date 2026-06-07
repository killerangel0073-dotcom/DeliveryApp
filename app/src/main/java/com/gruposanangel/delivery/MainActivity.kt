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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.google.firebase.firestore.FirebaseFirestore
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
    private var statusListener: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleSyncWorkers(this)
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())

        val db = AppDatabase.getDatabase(this)
        usuarioDao = db.usuarioDao(); repositoryUsuario = RepositoryUsuario(FirebaseDataSource(), usuarioDao)
        repository = RepositoryCliente(db.clienteDao())
        inventarioRepo = RepositoryInventario(FirebaseDataSource(), db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
        ventaRepository = VentaRepository(db.VentaDao())

        syncJob = startForegroundSyncLoop()
        iniciarSincronizacionInmediata()

        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            val usuarioActual by repositoryUsuario.getUsuarioActual().collectAsState(initial = null)
            val context = LocalContext.current; val sysUI = rememberSystemUiController()
            
            // 🚀 ESTADO PARA MANEJO DE NOTIFICACIONES Y NAVEGACIÓN
            var navAction by remember { mutableStateOf<String?>(null) }
            var navExtras by remember { mutableStateOf<Bundle?>(null) }

            // Función para procesar el Intent actual (SOPORTA BACKGROUND Y FOREGROUND)
            val procesarIntent: (Intent?) -> Unit = { i ->
                val extras = i?.extras
                val tipo = extras?.getString("tipo")
                val ventaId = extras?.getString("ventaId")

                Log.d("NAV_DEBUG", "Analizando Intent: Action=${i?.action}, Tipo=$tipo, VentaId=$ventaId")

                if (tipo != null || ventaId != null || (i?.action != null && i.action != Intent.ACTION_MAIN)) {
                    // Si el Intent trae datos de navegación, los priorizamos sobre la acción
                    navAction = when {
                        tipo == "VENTA_NUEVA" || ventaId != null -> "OPEN_VENTA_DETALLE"
                        tipo == "CARGA_NUEVA" -> "OPEN_NOTIFICACIONES"
                        else -> i?.action
                    }
                    navExtras = extras
                    Log.d("NAV_DEBUG", "Navegación detectada: $navAction")
                }
            }

            // Procesar el intent inicial y los cambios
            LaunchedEffect(Unit) { procesarIntent(intent) }
            
            // Estado de bloqueo de cuenta
            var isAccountBlocked by remember { mutableStateOf(false) }

            DisposableEffect(intent) {
                // Esto se disparará cuando setIntent(intent) sea llamado en onNewIntent
                procesarIntent(intent)
                onDispose { }
            }

            // Listener en tiempo real para el estado "activo" del usuario
            LaunchedEffect(usuarioActual?.uid) {
                val uid = usuarioActual?.uid ?: FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    statusListener?.remove()
                    statusListener = FirebaseFirestore.getInstance().collection("users").document(uid)
                        .addSnapshotListener { snapshot, _ ->
                            if (snapshot != null && snapshot.exists()) {
                                val activo = snapshot.getBoolean("activo") ?: true
                                isAccountBlocked = !activo
                            }
                        }
                } else {
                    isAccountBlocked = false
                    statusListener?.remove()
                }
            }

            SideEffect { sysUI.setSystemBarsColor(Color.Red, darkIcons = false); sysUI.setNavigationBarColor(Color.Black, darkIcons = true) }

            if (isAccountBlocked) {
                // PANTALLA DE BLOQUEO TOTAL
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Image(
                        painter = painterResource(id = R.drawable.appbloqueada),
                        contentDescription = "Aplicación Bloqueada",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color.Red)) {
                    HardLockPermissionWrapper {
                        startLocationService(usuarioActual?.puestoTrabajo)
                        if (usuarioActual != null) { 
                            Navegador(
                                repository = repository, 
                                onLogout = { cerrarSesion(context) },
                                intentAction = navAction,
                                intentExtras = navExtras
                            )
                            // Limpiamos los estados de navegación después de pasarlos para que no se repitan
                            if (navAction != null) {
                                navAction = null
                                navExtras = null
                            }
                        }
                        else { PantallaLoginPro { iniciarSincronizacionInmediata() } }
                    }
                }
            }
        }
    }

    private fun iniciarSincronizacionInmediata() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Actualizar Token FCM (Indispensable para comunicación)
                launch { FcmUtils.updateFcmToken(uid) }
                
                // 2. Sincronizar perfil del vendedor/admin
                repositoryUsuario.sincronizarVendedorLocal(uid)
                
                val usuario = repositoryUsuario.obtenerUsuarioLocal(uid)
                val idParaSync = if (usuario?.puestoTrabajo == "Vendedor de Ruta") uid else ""
                
                // 3. Iniciar escucha en tiempo real (SnapshotListeners)
                repository.escucharCambiosFirebase(this@MainActivity)
                inventarioRepo.escucharCambiosFirebase(uid)
                
                // 4. Descargas masivas de respaldo
                launch { repository.descargarClientesFirebase(this@MainActivity) }
                launch { inventarioRepo.descargarProductosFirebase(uid) }
                launch { ventaRepository.descargarVentasDia(idParaSync) }

            } catch (e: Exception) { 
                Log.e("SYNC", "Error crítico en sincronización inicial: ${e.message}") 
            }
        }
    }

    private fun startForegroundSyncLoop(): Job = lifecycleScope.launch(Dispatchers.IO) {
        while (isActive) {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null && hayInternet(this@MainActivity)) {
                try {
                    val pendientes = ventaRepository.obtenerVentasPendientes()
                    val uid = user.uid; val almacenId = inventarioRepo.getAlmacenVendedor(uid)
                    val nombreVendedor = repositoryUsuario.obtenerUsuarioActual()?.nombre ?: user.displayName ?: "Vendedor"
                    
                    if (almacenId != null) {
                        pendientes.forEach { v ->
                            val prods = ventaRepository.obtenerDetallesDeVenta(v.id).map { Plantilla_Producto(it.productoId, it.nombre, it.precio, it.cantidad) }
                            val (exito, msg) = ventaRepository.sincronizarConServidor(v.id, v.clienteId, v.clienteNombre, prods, v.metodoPago, v.vendedorId, nombreVendedor, almacenId)
                            if (exito) { val fId = try { JSONObject(msg).optString("ventaId") } catch (_: Exception) { null }; if (!fId.isNullOrEmpty()) ventaRepository.marcarVentaConFirestoreId(v.id, fId) }
                        }
                    }
                } catch (e: Exception) { }
            }
            delay(10000)
        }
    }

    private fun startLocationService(puesto: String?) {
        if (puesto == "Vendedor de Ruta") {
            if (!locationServiceStarted) {
                ContextCompat.startForegroundService(this, Intent(this, LocationService::class.java).apply { action = LocationService.ACTION_START })
                locationServiceStarted = true
            }
        } else {
            if (locationServiceStarted) {
                stopService(Intent(this, LocationService::class.java))
                locationServiceStarted = false
            }
        }
    }
    override fun onResume() { super.onResume(); if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && !LocationService.isRunning) locationServiceStarted = false }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() { 
        super.onDestroy()
        repository.stopEscuchaFirebase()
        inventarioRepo.stopEscuchaFirebase()
        syncJob?.cancel() 
        statusListener?.remove()
    }

    fun cerrarSesion(context: Context) {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid
        val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        val db = AppDatabase.getDatabase(context)

        lifecycleScope.launch(Dispatchers.IO) {
            // 1. LIMPIEZA REMOTA (FCM)
            try {
                if (uid != null) {
                    val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                    FcmUtils.removeTokenFromArray(uid, token)
                }
            } catch (e: Exception) {
                Log.e("LOGOUT", "Error limpiando tokens FCM: ${e.message}")
            }

            // 2. LIMPIEZA LOCAL (Room + Prefs)
            try {
                db.clearAllTables()
                prefs.edit().clear().apply()
                Log.d("LOGOUT", "Limpieza local completada exitosamente")
            } catch (e: Exception) {
                Log.e("LOGOUT", "Error limpiando persistencia local: ${e.message}")
            }

            // 3. SALIDA FINAL
            // Ejecutamos en el hilo principal para disparar la reacción de la UI
            withContext(Dispatchers.Main) {
                stopService(Intent(this@MainActivity, LocationService::class.java))
                locationServiceStarted = false
                repositoryUsuario.cerrarSesion() // Limpia tu repositorio en memoria
                auth.signOut() // Dispara el cambio de estado de Auth
            }
        }
    }
}
