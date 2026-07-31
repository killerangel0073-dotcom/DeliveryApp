package com.gruposanangel.delivery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.data.*
import com.gruposanangel.delivery.VentaRepository
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.SegundoPlano.LocationService
import com.gruposanangel.delivery.SegundoPlano.scheduleSyncWorkers
import com.gruposanangel.delivery.ui.screens.*
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import com.gruposanangel.delivery.ui.theme.ThemeConfig
import com.gruposanangel.delivery.utilidades.FcmUtils
import com.gruposanangel.delivery.utilidades.HardLockPermissionWrapper
import com.gruposanangel.delivery.utilidades.hayInternet
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var usuarioDao: UsuarioDao
    private lateinit var repositoryUsuario: RepositoryUsuario
    private lateinit var inventarioRepo: RepositoryInventario
    private lateinit var ventaRepository: VentaRepository
    private lateinit var repository: RepositoryCliente
    private var syncJob: Job? = null
    private var locationServiceStarted = false
    private var statusListener: com.google.firebase.firestore.ListenerRegistration? = null

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        
        ThemeConfig.loadTheme(this)
        com.gruposanangel.delivery.utilidades.TimeManager.init(this)
        scheduleSyncWorkers(this)
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())

        val db = AppDatabase.getDatabase(this)
        usuarioDao = db.usuarioDao(); repositoryUsuario = RepositoryUsuario(FirebaseDataSource(), usuarioDao)
        repository = RepositoryCliente(db.clienteDao())
        inventarioRepo = RepositoryInventario(FirebaseDataSource(), db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
        ventaRepository = VentaRepository(db.VentaDao(), db.productoDao())

        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent {
            DeliveryTheme {
                val usuarioActual by repositoryUsuario.getUsuarioActual().collectAsState(initial = null)
                val context = LocalContext.current; val sysUI = rememberSystemUiController()
                var navAction by remember { mutableStateOf<String?>(null) }
                var navExtras by remember { mutableStateOf<Bundle?>(null) }

                val procesarIntent: (Intent?) -> Unit = { i ->
                    val extras = i?.extras
                    val tipo = extras?.getString("tipo")
                    val ventaId = extras?.getString("ventaId")
                    if (tipo != null || ventaId != null || (i?.action != null && i.action != Intent.ACTION_MAIN)) {
                        navAction = when {
                            tipo == "JORNADA" -> "OPEN_DASHBOARD_ADMIN"
                            tipo == "VENTA_NUEVA" || (!ventaId.isNullOrEmpty() && ventaId != "0") -> "OPEN_VENTA_DETALLE"
                            tipo == "CARGA_NUEVA" -> "OPEN_NOTIFICACIONES"
                            else -> i?.action
                        }
                        navExtras = extras
                    }
                }

                LaunchedEffect(Unit) { procesarIntent(intent) }
                
                var blockReason by remember { mutableStateOf<String?>(null) }
                var appGlobalBloqueada by remember { mutableStateOf(false) }
                val timeManager = com.gruposanangel.delivery.utilidades.TimeManager
                var showTimeError by remember { mutableStateOf(!timeManager.esHoraAutomaticaActivada(context)) }
                var needsSync by remember { mutableStateOf(timeManager.requiereSincronizacion()) }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            showTimeError = !timeManager.esHoraAutomaticaActivada(context)
                            needsSync = timeManager.requiereSincronizacion()
                            if (!showTimeError && needsSync && usuarioActual != null) { iniciarSincronizacionInmediata() }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                DisposableEffect(intent) { procesarIntent(intent); onDispose { } }

                LaunchedEffect(Unit) {
                    FirebaseFirestore.getInstance().collection("config").document("app")
                        .addSnapshotListener { snapshot, _ -> appGlobalBloqueada = snapshot?.getBoolean("bloqueada") ?: false }
                }

                LaunchedEffect(usuarioActual?.uid, appGlobalBloqueada) {
                    val uid = usuarioActual?.uid ?: FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) {
                        statusListener?.remove()
                        statusListener = FirebaseFirestore.getInstance().collection("users").document(uid)
                            .addSnapshotListener { snapshot, _ ->
                                if (snapshot != null && snapshot.exists()) {
                                    // 1. Sincronización proactiva del perfil en Room
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        try { repositoryUsuario.syncUsuario(uid) } catch (_: Exception) {}
                                    }

                                    // 2. Lógica de seguridad y bloqueos
                                    val activo = snapshot.getBoolean("activo") ?: true
                                    val status = snapshot.getString("status") ?: "ACTIVO"
                                    val estadoLicencia = snapshot.getString("licenciaEstado") ?: "VIGENTE"
                                    val puesto = snapshot.getString("puestoTrabajo") ?: ""
                                    val esJefe = puesto == "CEO" || puesto == "Gerente General" || puesto == "Supervisor" || puesto == "Administración"
                                    val esDirectivoMaximo = puesto == "CEO" || puesto == "Gerente General"
                                    blockReason = when {
                                        esDirectivoMaximo -> null
                                        appGlobalBloqueada -> "GLOBAL_BLOCK"
                                        !activo || status == "SUSPENDIDO" || status == "BAJA" -> "ACCOUNT_DISABLED"
                                        estadoLicencia == "VENCIDA" && !esJefe -> "LICENSE_EXPIRED"
                                        else -> null
                                    }
                                    if ((status == "SUSPENDIDO" || status == "BAJA") && !esDirectivoMaximo) { cerrarSesion(context) }
                                }
                            }
                    } else {
                        blockReason = if (appGlobalBloqueada) "GLOBAL_BLOCK" else null
                        statusListener?.remove()
                    }
                }

                SideEffect { 
                    sysUI.setSystemBarsColor(Color.Red, darkIcons = false)
                    sysUI.setNavigationBarColor(Color.Black, darkIcons = true) 
                }

                if (blockReason != null) {
                    Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
                        if (blockReason == "ACCOUNT_DISABLED" || blockReason == "GLOBAL_BLOCK") {
                            Image(painter = painterResource(id = R.drawable.appbloqueada), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                                Icon(Icons.Default.Dangerous, null, tint = Color.Red, modifier = Modifier.size(100.dp))
                                Spacer(Modifier.height(24.dp))
                                Text("LICENCIA VENCIDA", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                                Spacer(Modifier.height(16.dp))
                                Text("Tu licencia de conducir ha expirado. Por seguridad y cumplimiento legal, no puedes iniciar jornada hasta que un administrador valide tu nueva licencia.", color = Color.LightGray, textAlign = TextAlign.Center, fontSize = 16.sp)
                                Spacer(Modifier.height(40.dp))
                                Text("Contacta a tu supervisor para la actualización.", color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (showTimeError || (needsSync && usuarioActual != null)) {
                    Box(Modifier.fillMaxSize().background(Color.White), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            Icon(Icons.Default.HistoryToggleOff, null, tint = Color.Red, modifier = Modifier.size(80.dp))
                            Spacer(Modifier.height(24.dp))
                            Text("CONFIGURACIÓN DE HORA INCORRECTA", fontWeight = FontWeight.Black, color = Color.Black, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Text(if (showTimeError) "Para continuar, debes activar la 'Hora Automática' en los ajustes de tu teléfono." else "Se requiere conexión a internet para validar el reloj del sistema.", color = Color.Gray, textAlign = TextAlign.Center, fontSize = 14.sp)
                            Spacer(Modifier.height(32.dp))
                            Button(onClick = { 
                                if (showTimeError) { context.startActivity(Intent(android.provider.Settings.ACTION_DATE_SETTINGS)) } 
                                else { showTimeError = !timeManager.esHoraAutomaticaActivada(context); needsSync = timeManager.requiereSincronizacion(); if (!showTimeError && needsSync) iniciarSincronizacionInmediata() }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), shape = RoundedCornerShape(12.dp)) { Text(if (showTimeError) "IR A AJUSTES" else "REINTENTAR", fontWeight = FontWeight.Bold) }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize().background(Color.Red)) {
                        HardLockPermissionWrapper(
                            onLockActive = {
                                try { stopService(Intent(context, LocationService::class.java)) } catch (_: Exception) { }
                                locationServiceStarted = false
                                syncJob?.cancel(); syncJob = null
                            },
                            onLockCleared = { if (syncJob == null) syncJob = startForegroundSyncLoop(); iniciarSincronizacionInmediata() }
                        ) {
                            startLocationService(usuarioActual?.puestoTrabajo)
                            if (usuarioActual != null) { 
                                Navegador(repository = repository, onLogout = { cerrarSesion(context) }, intentAction = navAction, intentExtras = navExtras)
                                if (navAction != null) { navAction = null; navExtras = null }
                            } else { PantallaLoginPro { iniciarSincronizacionInmediata() } }
                        }
                    }
                }
            }
        }
    }

    private fun iniciarSincronizacionInmediata() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                try {
                    val serverTimeSnap = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                    com.gruposanangel.delivery.utilidades.TimeManager.sincronizar(System.currentTimeMillis(), this@MainActivity)
                } catch (e: Exception) { }
                launch { FcmUtils.updateFcmToken(uid) }
                repositoryUsuario.sincronizarVendedorLocal(uid)
                val usuario = repositoryUsuario.obtenerUsuarioLocal(uid)
                val puesto = usuario?.puestoTrabajo?.trim() ?: ""
                val idParaSync = if (puesto == "Vendedor de Ruta" || puesto == "Suplente de Ruta") uid else ""
                repository.escucharCambiosFirebase(this@MainActivity)
                inventarioRepo.escucharCambiosFirebase(uid)
                launch { repository.descargarClientesFirebase(this@MainActivity) }
                launch { inventarioRepo.descargarProductosFirebase(uid) }
                launch { ventaRepository.descargarVentasDia(idParaSync) }
            } catch (e: Exception) { Log.e("SYNC", "Error: ${e.message}") }
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
                            val (exito, msg) = ventaRepository.sincronizarConServidor(v.id, v.clienteId, v.clienteNombre, prods, v.metodoPago, v.vendedorId, nombreVendedor, almacenId, v.fotoEvidenciaVisita, v.fueraDeRango, v.latitudVenta, v.longitudVenta, v.fecha, v.motivoVisita)
                            if (exito) { val fId = try { JSONObject(msg).optString("ventaId") } catch (_: Exception) { null }; if (!fId.isNullOrEmpty()) ventaRepository.marcarVentaConFirestoreId(v.id, fId) }
                        }
                    }
                } catch (e: Exception) { }
            }
            delay(10000)
        }
    }

    private fun startLocationService(puesto: String?) {
        val p = puesto?.trim() ?: ""
        if (p == "Vendedor de Ruta" || p == "Suplente de Ruta") {
            val hasLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasLocation && !locationServiceStarted) {
                try { ContextCompat.startForegroundService(this, Intent(this, LocationService::class.java).apply { action = LocationService.ACTION_START }); locationServiceStarted = true } catch (e: Exception) { }
            }
        } else {
            if (locationServiceStarted) { try { stopService(Intent(this, LocationService::class.java)) } catch (_: Exception) { }; locationServiceStarted = false }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lux = event.values[0]
            if (lux < 10f) {
                ThemeConfig.isSensorDark.value = true
            } else if (lux > 20f) {
                ThemeConfig.isSensorDark.value = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() { 
        super.onResume()
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && !LocationService.isRunning) locationServiceStarted = false 
    }
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    override fun onDestroy() { super.onDestroy(); repository.stopEscuchaFirebase(); inventarioRepo.stopEscuchaFirebase(); syncJob?.cancel(); statusListener?.remove() }

    fun cerrarSesion(context: Context) {
        val auth = FirebaseAuth.getInstance(); val uid = auth.currentUser?.uid; val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE); val db = AppDatabase.getDatabase(context)
        lifecycleScope.launch(Dispatchers.IO) {
            try { if (uid != null) { val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await(); FcmUtils.removeTokenFromArray(uid, token) } } catch (e: Exception) { }
            try { db.clearAllTables(); prefs.edit().clear().apply() } catch (e: Exception) { }
            withContext(Dispatchers.Main) { stopService(Intent(this@MainActivity, LocationService::class.java)); locationServiceStarted = false; repositoryUsuario.cerrarSesion(); auth.signOut() }
        }
    }
}
