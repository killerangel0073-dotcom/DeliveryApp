package com.gruposanangel.delivery

import android.Manifest
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.RepositoryInventario
import com.gruposanangel.delivery.data.VentaRepository

import com.gruposanangel.delivery.ui.screens.Navegador
import com.gruposanangel.delivery.ui.screens.PantallaLoginPro
import com.gruposanangel.delivery.ui.screens.VistaModeloVenta
import com.gruposanangel.delivery.ui.screens.isNetworkAvailable
import kotlinx.coroutines.*
import com.gruposanangel.delivery.SegundoPlano.LocationService
import com.gruposanangel.delivery.utilidades.FcmUtils
import androidx.work.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.gruposanangel.delivery.SegundoPlano.scheduleSyncWorkers
import com.gruposanangel.delivery.data.UsuarioDao
import com.gruposanangel.delivery.model.Plantilla_Producto
import com.gruposanangel.delivery.ui.screens.PerfilDeUsuarioScreen
import org.json.JSONObject



private val bluetoothPermissions = arrayOf(
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.BLUETOOTH_SCAN
)





fun isLocationEnabled(activity: ComponentActivity): Boolean {
    val locationManager = activity.getSystemService(LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

@Composable
fun LocationRequiredDialog(openLocationSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("UBICACIÓN REQUERIDA", color = Color.Red, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text("La app requiere que la ubicación esté activada todo el tiempo para funcionar.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = openLocationSettings, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.Red, contentColor = Color.White)) {
                    Text("Abrir configuración")
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
fun PermissionRequiredDialog(onRequestPermission: () -> Unit, titleText: String = "PERMISO REQUERIDO", bodyText: String = "La app necesita permiso para funcionar correctamente. Concede el permiso.") {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(titleText, color = Color.Red, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text(bodyText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = onRequestPermission, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.Red, contentColor = Color.White)) {
                    Text("Conceder permiso")
                }
            }
        },
        dismissButton = {}
    )
}

class MainActivity : ComponentActivity() {
    private var locationServiceStarted = false


    private lateinit var usuarioDao: UsuarioDao
    private lateinit var repositoryUsuario: RepositoryUsuario

    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private val hasNotificationPermissionState = mutableStateOf(false)
    private lateinit var inventarioRepo: RepositoryInventario
    private lateinit var ventaRepository: VentaRepository
    private lateinit var vistaModeloVenta: VistaModeloVenta
    private val showLocationDialogState = mutableStateOf(false)
    private val showBluetoothDialogState = mutableStateOf(false)
    private lateinit var fineLocationLauncher: ActivityResultLauncher<String>
    private lateinit var backgroundLocationLauncher: ActivityResultLauncher<String>
    private lateinit var bluetoothLauncher: ActivityResultLauncher<Array<String>>
    private val hasFineLocationState = mutableStateOf(false)
    private val hasBackgroundLocationState = mutableStateOf(false)
    private val hasBluetoothPermissionState = mutableStateOf(false)
    private lateinit var repository: RepositoryCliente


    private fun startForegroundSyncLoop(): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            while (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (FirebaseAuth.getInstance().currentUser != null && isNetworkAvailable(this@MainActivity)) {
                    try {
                        // Sincronizar clientes
                        repository.sincronizarConFirebase()

                        // Sincronizar ventas pendientes
                        val pendientes = ventaRepository.obtenerVentasPendientes()
                        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                        val almacenId = inventarioRepo.getAlmacenVendedor(uid) ?: return@launch

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
                                if (!firestoreId.isNullOrEmpty()) {
                                    ventaRepository.marcarVentaConFirestoreId(venta.id, firestoreId)
                                }
                            }
                        }


                        //vistaModeloVenta.sincronizarVentasPendientes()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                delay(5000)
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // Programar workers periódicos
        scheduleSyncWorkers(this)



        // Launcher para permiso de notificaciones (API 33+)
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasNotificationPermissionState.value = granted
        }


        // Inicializar Firebase
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        // Inicializar Room y Repositorios
        val db = AppDatabase.getDatabase(this)
        val clienteDao = db.clienteDao()



        usuarioDao = db.usuarioDao() // tu DAO para usuarios
        repositoryUsuario = RepositoryUsuario()
        repository = RepositoryCliente(clienteDao)
        inventarioRepo = RepositoryInventario(db.productoDao())
        ventaRepository = VentaRepository(db.VentaDao())




// Forma por nombre (respetando el nombre exacto del constructor)
        vistaModeloVenta = VistaModeloVenta(
            repositoryInventario = inventarioRepo,
            ventaRepository = ventaRepository
        )



        // Configurar ventana para que no ajuste el contenido por las barras del sistema
        WindowCompat.setDecorFitsSystemWindows(window, true)







        // ---- Launchers de permisos ----
        fineLocationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasFineLocationState.value = granted
            if (granted) {
                onLocationPermissionGranted()

            }

        }

        // Launcher para permisos de ubicación en segundo plano (API 29+)
        backgroundLocationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasBackgroundLocationState.value = granted
            if (granted) {
                onLocationPermissionGranted()

            }

        }

        bluetoothLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            hasBluetoothPermissionState.value = permissions.values.all { it }
        }

        // ---- Comprobar permisos inicialmente ----
        hasFineLocationState.value = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasBackgroundLocationState.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else true
        hasBluetoothPermissionState.value = bluetoothPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }






        // Solicitar permisos si no están concedidos
        lifecycle.addObserver(object : DefaultLifecycleObserver {

            private var permissionJob: Job? = null




            // Tarea en segundo plano para sincronizar clientes y ventas

            private var syncJob: Job? = null

            override fun onResume(owner: LifecycleOwner) {




                // Solicitar permiso de notificaciones si es API 33+
                permissionJob = CoroutineScope(Dispatchers.Main).launch {
                    while (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        val providersEnabled = isLocationEnabled(this@MainActivity)
                        showLocationDialogState.value = (hasFineLocationState.value && hasBackgroundLocationState.value && !providersEnabled)
                        showBluetoothDialogState.value = !hasBluetoothPermissionState.value


                        // 🚀 Solicitar permiso de notificaciones si es API 33+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !hasNotificationPermissionState.value) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        // 🚀 Solicitar permisos automáticamente si faltan
                        if (!hasFineLocationState.value) {
                            fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }

                        // 🚀 Solicitar permiso de ubicación en segundo plano si es API 29+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationState.value) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }


                        /*
                         // ✅ Arrancar servicio si todo está bien
                        if (hasFineLocationState.value && hasBackgroundLocationState.value && providersEnabled) {
                        if (!locationServiceStarted) {
                        val intent = Intent(this, LocationService::class.java).apply {
                         action = LocationService.ACTION_START
                          }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                          ContextCompat.startForegroundService(this, intent)
                              } else {
                            startService(intent)
                                }
                               locationServiceStarted = true
                            }
                               }
                                  */



                        delay(1000)
                    }
                }














                // Iniciar tarea de sincronización en segundo plano
                syncJob = startForegroundSyncLoop()



            }


            // Cancelar tareas al pausar
            override fun onPause(owner: LifecycleOwner) {
                permissionJob?.cancel()
                syncJob?.cancel() // <-- cancelar loop
            }
        })

        // ---- FIN Launchers de permisos ----
        setContent {





            // Controlador de UI del sistema para barras de estado y navegación
            val systemUiController = rememberSystemUiController()
            val backgroundColor = Color(0xFFFF0000)






            // Configurar colores de las barras del sistema
            SideEffect {
                systemUiController.setSystemBarsColor(color = backgroundColor, darkIcons = false)
                systemUiController.setNavigationBarColor(color = Color.Black, darkIcons = true)
            }


            // Obtener usuario actual de Firebase Auth
            val currentUser = FirebaseAuth.getInstance().currentUser
            var loggedIn by remember { mutableStateOf(currentUser != null) }





            // Efecto para manejar la lógica de inventario y clientes al iniciar/cerrar sesión
            LaunchedEffect(loggedIn) {
                if (loggedIn) {
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    currentUser?.uid?.let { uid ->

                        // 1️⃣ Sincronizar datos del vendedor local
                        withContext(Dispatchers.IO) {
                            repositoryUsuario.sincronizarVendedorLocal(usuarioDao, uid)

                            val user = usuarioDao.obtenerPorId(uid)
                            Log.d("DEBUG", "Usuario en Room: $user")
                        }

                        // 1️⃣ Descargar inventario y guardarlo en local
                        inventarioRepo.descargarProductosFirebase()

                        // 2️⃣ Iniciar escucha de inventario en tiempo real
                        inventarioRepo.escucharCambiosFirebase(uid)

                        // 3️⃣ Iniciar escucha de clientes en tiempo real (si tu repo también requiere UID)
                        repository.escucharCambiosFirebase()


                        // 🔹 4️⃣ Guardar token FCM en Firestore
                        val prefs = getSharedPreferences("fcm_prefs", MODE_PRIVATE)
                        val savedToken = prefs.getString("fcm_token", null)
                        if (savedToken != null) {
                            FcmUtils.saveTokenToArray(uid, savedToken)
                            Log.d("FCM", "Token guardado en Firestore desde MainActivity: $savedToken")
                        } else {
                            Log.w("FCM", "No había token en SharedPreferences al loguear")
                        }

                        // 🔹 5️⃣ Forzar actualización del token actual de Firebase
                        FcmUtils.updateFcmToken(uid)






                    }
                } else {
                    inventarioRepo.stopEscuchaFirebase()
                    repository.stopEscuchaFirebase()
                }
            }






            // Tema personalizado con Material3
            Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {






                val context = LocalContext.current

                if (loggedIn) {
                    Navegador(
                        repository = repository,
                        onLogout = {
                            // Llama a cerrarSesion y solo actualiza loggedIn cuando termina
                            cerrarSesion(context) {
                                loggedIn = false
                            }
                        }
                    )
                } else {
                    PantallaLoginPro(onLoginSuccess = { loggedIn = true })
                }







                if (showLocationDialogState.value) {
                    LocationRequiredDialog(openLocationSettings = {
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        startActivity(intent)
                    })
                }

                if (showBluetoothDialogState.value) {
                    PermissionRequiredDialog(
                        titleText = "PERMISO DE BLUETOOTH REQUERIDO",
                        bodyText = "La app necesita permiso de Bluetooth para poder imprimir.",
                        onRequestPermission = { bluetoothLauncher.launch(bluetoothPermissions) }
                    )
                }
            }
        }
    }







    private fun startLocationService() {
        if (hasFineLocationState.value && hasBackgroundLocationState.value) {
            val intent = Intent(this, LocationService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    // Guardar flag de permisos concedidos
    private fun onLocationPermissionGranted() {
        getSharedPreferences("settings", MODE_PRIVATE)
            .edit()
            .putBoolean("locationPermissionGranted", true)
            .apply()
        startLocationService()
    }




    // Detener escuchas de Firebase al destruir la actividad

    override fun onDestroy() {
        super.onDestroy()
        if (::repository.isInitialized) {
            repository.stopEscuchaFirebase()
            inventarioRepo.stopEscuchaFirebase()

        }
    }



    fun cerrarSesion(context: Context, onComplete: () -> Unit = {}) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val uid = currentUser.uid
            val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            val savedToken = prefs.getString("fcm_token", null)

            if (savedToken != null) {
                // ⚡ Asegúrate de eliminar el token antes de cerrar sesión
                CoroutineScope(Dispatchers.IO).launch {
                    FcmUtils.removeTokenFromArray(uid, savedToken)
                    Log.d("FCM", "🗑 Token eliminado: $savedToken")

                    // Ahora sí podemos cerrar sesión
                    withContext(Dispatchers.Main) {
                        prefs.edit().remove("fcm_token").apply()
                        auth.signOut()
                        Log.d("FCM", "🔴 Sesión cerrada correctamente")
                        onComplete()
                    }
                }
            } else {
                // No hay token, simplemente cerramos sesión
                prefs.edit().remove("fcm_token").apply()
                auth.signOut()
                Log.d("FCM", "🔴 Sesión cerrada (sin token)")
                onComplete()
            }
        } else {
            Log.w("FCM", "⚠ No hay usuario autenticado; solo ejecutando onComplete")
            onComplete()
        }
    }








}