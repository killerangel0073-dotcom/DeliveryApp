package com.gruposanangel.delivery

import android.Manifest
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
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
import com.gruposanangel.delivery.utilidades.PermisosManager
import kotlinx.coroutines.*
import org.json.JSONObject

private val bluetoothPermissions = arrayOf(
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_SCAN
)

private val ALL_REQUIRED_PERMISSIONS: Array<String> =
    (PermisosManager.PERMISOS_REQUERIDOS + bluetoothPermissions).toSet().toTypedArray()

class MainActivity : ComponentActivity() {

    private lateinit var usuarioDao: UsuarioDao
    private lateinit var repositoryUsuario: RepositoryUsuario
    private lateinit var inventarioRepo: RepositoryInventario
    private lateinit var ventaRepository: VentaRepository
    private lateinit var vistaModeloVenta: VistaModeloVenta
    private lateinit var repository: RepositoryCliente

    private var syncJob: Job? = null
    private var locationServiceStarted = false
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>

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

        repositoryUsuario = RepositoryUsuario()
        repository = RepositoryCliente(clienteDao)
        inventarioRepo = RepositoryInventario(db.productoDao())
        ventaRepository = VentaRepository(db.VentaDao())
        vistaModeloVenta = VistaModeloVenta(inventarioRepo, ventaRepository)

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

        syncJob?.cancel()
        syncJob = startForegroundSyncLoop()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                repositoryUsuario.sincronizarVendedorLocal(usuarioDao, uid)
                repository.escucharCambiosFirebase()
                inventarioRepo.escucharCambiosFirebase(uid)
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            val currentUser = FirebaseAuth.getInstance().currentUser
            var loggedIn by remember { mutableStateOf(currentUser != null) }
            val context = LocalContext.current
            val navController = rememberNavController()
            var batterySettingsHandled by remember { mutableStateOf(false) }

            val systemUiController = rememberSystemUiController()
            SideEffect {
                systemUiController.setSystemBarsColor(color = Color.Red, darkIcons = false)
                systemUiController.setNavigationBarColor(color = Color.Black, darkIcons = true)
            }

            Box(modifier = Modifier.fillMaxSize().background(Color.Red)) {
                PermissionGate(
                    permissionLauncher = requestPermissionLauncher,
                    permissionsToRequest = ALL_REQUIRED_PERMISSIONS,
                    onAllRequiredChecksPassed = {
                        if (!locationServiceStarted) startLocationService()
                        if (!batterySettingsHandled) {
                            openManufacturerBatterySettings()
                            batterySettingsHandled = true
                        }

                        if (loggedIn) {
                            Navegador(
                                repository = repository,
                                onLogout = { cerrarSesion(context) { loggedIn = false } },
                                autoOpenTicketId = ventaIdToOpenMapaState.value
                            )
                        } else {
                            PantallaLoginPro(onLoginSuccess = { loggedIn = true })
                        }
                    }
                )
            }
        }
    }

    private fun startForegroundSyncLoop(): Job = lifecycleScope.launch(Dispatchers.IO) {
        Log.d("SYNC", "Loop de sincronización iniciado")
        while (isActive) {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null && isNetworkAvailable(this@MainActivity)) {
                try {
                    repository.sincronizarConFirebase()

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

    private fun openManufacturerBatterySettings() {
        when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> openXiaomiBatterySettings()
            "samsung" -> openSamsungBatterySettings()
            "huawei", "honor" -> openHuaweiBatterySettings()
            else -> requestIgnoreBatteryOptimizations()
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }
    }

    private fun openXiaomiBatterySettings() {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", packageName)
                putExtra("package_label", getString(R.string.app_name))
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST")
                startActivity(intent)
            } catch (ex: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    private fun openSamsungBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openHuaweiBatterySettings() {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
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
