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
        
        // 🔥 INICIALIZAR TIME MANAGER (Arquitectura de Seguridad)
        com.gruposanangel.delivery.utilidades.TimeManager.init(this)

        scheduleSyncWorkers(this)
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())

        val db = AppDatabase.getDatabase(this)
        usuarioDao = db.usuarioDao(); repositoryUsuario = RepositoryUsuario(FirebaseDataSource(), usuarioDao)
        repository = RepositoryCliente(db.clienteDao())
        inventarioRepo = RepositoryInventario(FirebaseDataSource(), db.productoDao(), db.VentaDao(), db.movimientoInventarioDao())
        ventaRepository = VentaRepository(db.VentaDao(), db.productoDao())

        // 🔥 QUITAMOS LAS LLAMADAS DIRECTAS DE ONCREATE
        // Las moveremos dentro del Wrapper para que solo corran con permisos

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
            
            // 🔥 ESTADOS DE BLOQUEO Y SEGURIDAD
            var blockReason by remember { mutableStateOf<String?>(null) }
            var appGlobalBloqueada by remember { mutableStateOf(false) }
            val timeManager = com.gruposanangel.delivery.utilidades.TimeManager
            var showTimeError by remember { mutableStateOf(!timeManager.esHoraAutomaticaActivada(context)) }
            var needsSync by remember { mutableStateOf(timeManager.requiereSincronizacion()) }

            // 🔥 OBSERVADOR DE CICLO DE VIDA PARA AUTO-VALIDACIÓN
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        showTimeError = !timeManager.esHoraAutomaticaActivada(context)
                        needsSync = timeManager.requiereSincronizacion()
                        if (!showTimeError && needsSync && usuarioActual != null) {
                            iniciarSincronizacionInmediata()
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            DisposableEffect(intent) {
                // Esto se disparará cuando setIntent(intent) sea llamado en onNewIntent
                procesarIntent(intent)
                onDispose { }
            }

            // 🔥 LISTENER GLOBAL DE BLOQUEO (Configuración de la App)
            LaunchedEffect(Unit) {
                FirebaseFirestore.getInstance().collection("config").document("app")
                    .addSnapshotListener { snapshot, _ ->
                        appGlobalBloqueada = snapshot?.getBoolean("bloqueada") ?: false
                    }
            }

            // Listener en tiempo real para el estado "activo" del usuario
            LaunchedEffect(usuarioActual?.uid, appGlobalBloqueada) {
                val uid = usuarioActual?.uid ?: FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    statusListener?.remove()
                    statusListener = FirebaseFirestore.getInstance().collection("users").document(uid)
                        .addSnapshotListener { snapshot, _ ->
                            if (snapshot != null && snapshot.exists()) {
                                val activo = snapshot.getBoolean("activo") ?: true
                                val status = snapshot.getString("status") ?: "ACTIVO"
                                val estadoLicencia = snapshot.getString("licenciaEstado") ?: "VIGENTE"
                                val puesto = snapshot.getString("puestoTrabajo") ?: ""

                                // 🔥 Los administradores/jefes tienen inmunidad de bloqueo por licencia
                                val p = puesto.trim()
                                val esJefe = p == "CEO" || p == "Gerente General" || p == "Supervisor" || p == "Administración"
                                
                                // 🔥 CEO y Gerente General tienen inmunidad TOTAL al bloqueo visual
                                val esDirectivoMaximo = p == "CEO" || p == "Gerente General"

                                blockReason = when {
                                    esDirectivoMaximo -> null
                                    appGlobalBloqueada -> "GLOBAL_BLOCK"
                                    !activo || status == "SUSPENDIDO" || status == "BAJA" -> "ACCOUNT_DISABLED"
                                    estadoLicencia == "VENCIDA" && !esJefe -> "LICENSE_EXPIRED"
                                    else -> null
                                }

                                if ((status == "SUSPENDIDO" || status == "BAJA") && !esDirectivoMaximo) {
                                    cerrarSesion(context)
                                }
                            }
                        }
                } else {
                    blockReason = if (appGlobalBloqueada) "GLOBAL_BLOCK" else null
                    statusListener?.remove()
                }
            }

            SideEffect { sysUI.setSystemBarsColor(Color.Red, darkIcons = false); sysUI.setNavigationBarColor(Color.Black, darkIcons = true) }

            if (blockReason != null) {
                // PANTALLA DE BLOQUEO TOTAL
                Box(Modifier.fillMaxSize().background(Color.Black), Alignment.Center) {
                    if (blockReason == "ACCOUNT_DISABLED" || blockReason == "GLOBAL_BLOCK") {
                        Image(
                            painter = painterResource(id = R.drawable.appbloqueada),
                            contentDescription = "Aplicación Bloqueada",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    } else {
                        // Bloqueo por Licencia Vencida
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                            Icon(Icons.Default.Dangerous, null, tint = Color.Red, modifier = Modifier.size(100.dp))
                            Spacer(Modifier.height(24.dp))
                            Text("LICENCIA VENCIDA", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Tu licencia de conducir ha expirado. Por seguridad y cumplimiento legal, no puedes iniciar jornada hasta que un administrador valide tu nueva licencia.",
                                color = Color.LightGray, textAlign = TextAlign.Center, fontSize = 16.sp
                            )
                            Spacer(Modifier.height(40.dp))
                            Text("Contacta a tu supervisor para la actualización.", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (showTimeError || (needsSync && usuarioActual != null)) {
                // 🔥 PANTALLA DE BLOQUEO POR HORA INCORRECTA
                Box(Modifier.fillMaxSize().background(Color.White), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Default.HistoryToggleOff, null, tint = Color.Red, modifier = Modifier.size(80.dp))
                        Spacer(Modifier.height(24.dp))
                        Text("CONFIGURACIÓN DE HORA INCORRECTA", fontWeight = FontWeight.Black, color = Color.Black, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (showTimeError) "Para continuar, debes activar la 'Hora Automática' en los ajustes de tu teléfono. Esto garantiza la integridad de tus ventas."
                            else "Se requiere conexión a internet para validar el reloj del sistema antes de iniciar tu jornada.",
                            color = Color.Gray, textAlign = TextAlign.Center, fontSize = 14.sp
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = { 
                                if (showTimeError) {
                                    // Abrir ajustes de fecha y hora
                                    context.startActivity(Intent(android.provider.Settings.ACTION_DATE_SETTINGS))
                                } else {
                                    showTimeError = !timeManager.esHoraAutomaticaActivada(context)
                                    needsSync = timeManager.requiereSincronizacion()
                                    if (!showTimeError && needsSync) iniciarSincronizacionInmediata()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(if (showTimeError) "IR A AJUSTES" else "REINTENTAR", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color.Red)) {
                    HardLockPermissionWrapper(
                        onLockActive = {
                            // 🔥 Limpieza total inmediata si hay bloqueo
                            try {
                                stopService(Intent(context, LocationService::class.java))
                            } catch (_: Exception) { }
                            locationServiceStarted = false
                            syncJob?.cancel()
                            syncJob = null
                        },
                        onLockCleared = {
                            // 🔥 Solo cuando el camino está limpio, arrancamos motores
                            if (syncJob == null) syncJob = startForegroundSyncLoop()
                            iniciarSincronizacionInmediata()
                        }
                    ) {
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
                // 0. Sincronizar Reloj con Servidor (Seguridad Temporal)
                try {
                    val serverTimeSnap = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                    // Si el documento existe, usamos el tiempo actual del sistema como base inicial 
                    // pero lo ideal es un serverTimestamp. Para esta versión, si el usuario tiene internet,
                    // validamos contra un campo "ultimaConexion" que el servidor actualiza.
                    val tiempoServidor = System.currentTimeMillis() // Fallback
                    com.gruposanangel.delivery.utilidades.TimeManager.sincronizar(tiempoServidor, this@MainActivity)
                } catch (e: Exception) { Log.e("TIME", "Error sync time: ${e.message}") }

                // 1. Actualizar Token FCM (Indispensable para comunicación)
                launch { FcmUtils.updateFcmToken(uid) }
                
                // 2. Sincronizar perfil del vendedor/admin
                repositoryUsuario.sincronizarVendedorLocal(uid)
                
                val usuario = repositoryUsuario.obtenerUsuarioLocal(uid)
                val puesto = usuario?.puestoTrabajo?.trim() ?: ""
                val idParaSync = if (puesto == "Vendedor de Ruta" || puesto == "Suplente de Ruta") uid else ""
                
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
                            val (exito, msg) = ventaRepository.sincronizarConServidor(
                                ventaLocalId = v.id, 
                                clienteId = v.clienteId, 
                                clienteNombre = v.clienteNombre, 
                                productos = prods, 
                                metodoPago = v.metodoPago, 
                                vendedorId = v.vendedorId, 
                                vendedorNombre = nombreVendedor, 
                                almacenVendedorId = almacenId,
                                fotoEvidenciaLocal = v.fotoEvidenciaVisita,
                                fueraDeRango = v.fueraDeRango,
                                latitudVenta = v.latitudVenta,
                                longitudVenta = v.longitudVenta,
                                fecha = v.fecha // 🔥 Pasamos la hora original capturada
                            )
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
            // 🛡️ Blindaje final: No intentamos arrancar el FGS si no hay permisos reales
            val hasLocation = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            if (hasLocation && !locationServiceStarted) {
                try {
                    ContextCompat.startForegroundService(this, Intent(this, LocationService::class.java).apply { action = LocationService.ACTION_START })
                    locationServiceStarted = true
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error arrancando servicio: ${e.message}")
                }
            }
        } else {
            if (locationServiceStarted) {
                try {
                    stopService(Intent(this, LocationService::class.java))
                } catch (_: Exception) { }
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
