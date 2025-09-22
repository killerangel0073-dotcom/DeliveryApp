package com.gruposanangel.delivery

import android.Manifest
import android.content.Context.LOCATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.ClienteRepository
import com.gruposanangel.delivery.ui.screens.DeliveryAppNav
import com.gruposanangel.delivery.ui.screens.PantallaLoginPro
import kotlinx.coroutines.*
import android.provider.Settings
import androidx.compose.ui.text.style.TextAlign

fun isLocationEnabled(activity: ComponentActivity): Boolean {
    val locationManager = activity.getSystemService(LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

@Composable
fun LocationRequiredDialog(openLocationSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                "UBICACIÓN REQUERIDA",
                color = Color.Red,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                "La app requiere que la ubicación esté activada todo el tiempo para funcionar.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = openLocationSettings,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.Red,
                        contentColor = Color.White
                    )
                ) {
                    Text("Abrir configuración")
                }
            }
        },
        dismissButton = {}
    )
}

@Composable
fun PermissionRequiredDialog(onRequestPermission: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                "PERMISO DE UBICACIÓN REQUERIDO",
                color = Color.Red,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                "La app necesita permiso de ubicación para funcionar correctamente. Concede el permiso.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color.Red,
                        contentColor = Color.White
                    )
                ) {
                    Text("Conceder permiso")
                }
            }
        },
        dismissButton = {}
    )
}

class MainActivity : ComponentActivity() {

    // Mantener el estado de los dialogs
    private val showLocationDialogState = mutableStateOf(false)
    private val showPermissionDialogState = mutableStateOf(false)

    // Launchers de permisos
    private lateinit var fineLocationLauncher: ActivityResultLauncher<String>
    private lateinit var backgroundLocationLauncher: ActivityResultLauncher<String>

    private val hasFineLocationState = mutableStateOf(false)
    private val hasBackgroundLocationState = mutableStateOf(false)

    // Repositorio como propiedad para detener escucha en onDestroy y logout
    private lateinit var repository: ClienteRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // Inicializar DB y repo (sigues con el mismo comportamiento)
        val database = AppDatabase.getDatabase(this)
        val clienteDao = database.clienteDao()
        repository = ClienteRepository(clienteDao)

        // NOTA: **No** arrancamos escucharCambiosFirebase() aquí con CoroutineScope.
        // El listener se iniciará/detendrá desde Compose según el estado de sesión (loggedIn).

        // Launchers de permisos
        fineLocationLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                hasFineLocationState.value = granted
                showPermissionDialogState.value = !granted
            }

        backgroundLocationLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                hasBackgroundLocationState.value = granted
            }

        hasFineLocationState.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        hasBackgroundLocationState.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasFineLocationState.value || !hasBackgroundLocationState.value) {
            showPermissionDialogState.value = true
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var job: Job? = null

            override fun onResume(owner: LifecycleOwner) {
                hasFineLocationState.value = ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                hasBackgroundLocationState.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                } else true

                showPermissionDialogState.value = !hasFineLocationState.value || !hasBackgroundLocationState.value

                job = CoroutineScope(Dispatchers.Main).launch {
                    while (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        val fineGranted = hasFineLocationState.value
                        val backgroundGranted = hasBackgroundLocationState.value
                        val providersEnabled = isLocationEnabled(this@MainActivity)

                        showPermissionDialogState.value = !fineGranted || !backgroundGranted
                        showLocationDialogState.value = fineGranted && backgroundGranted && !providersEnabled

                        delay(1000)
                    }
                }
            }

            override fun onPause(owner: LifecycleOwner) {
                job?.cancel()
                showLocationDialogState.value = false
            }
        })

        setContent {
            val systemUiController = rememberSystemUiController()
            val backgroundColor = Color(0xFFFF0000)

            SideEffect {
                systemUiController.setSystemBarsColor(color = backgroundColor, darkIcons = false)
                systemUiController.setNavigationBarColor(color = Color.Black, darkIcons = true)
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            var loggedIn by remember { mutableStateOf(currentUser != null) }

            // Iniciar/detener la escucha de Firestore según el estado de sesión
            LaunchedEffect(loggedIn) {
                if (loggedIn) {
                    repository.escucharCambiosFirebase()
                } else {
                    repository.stopEscuchaFirebase()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
            ) {
                if (loggedIn) {
                    DeliveryAppNav(
                        repository = repository,
                        onLogout = {
                            // Detener la escucha y cerrar sesión
                            repository.stopEscuchaFirebase()
                            FirebaseAuth.getInstance().signOut()
                            loggedIn = false
                        }
                    )
                } else {
                    PantallaLoginPro(onLoginSuccess = { loggedIn = true })
                }

                if (showPermissionDialogState.value) {
                    PermissionRequiredDialog(onRequestPermission = {
                        val finePermission = Manifest.permission.ACCESS_FINE_LOCATION
                        val backgroundPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION

                        val fineGranted = ContextCompat.checkSelfPermission(
                            this@MainActivity, finePermission
                        ) == PackageManager.PERMISSION_GRANTED
                        val backgroundGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                            ContextCompat.checkSelfPermission(this@MainActivity, backgroundPermission) == PackageManager.PERMISSION_GRANTED
                        else true

                        if (!fineGranted || !backgroundGranted) {
                            // Abrir ajustes de la app directamente
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            this@MainActivity.startActivity(intent)
                        } else {
                            // Si aún se pueden pedir permisos
                            fineLocationLauncher.launch(finePermission)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                backgroundLocationLauncher.launch(backgroundPermission)
                            }
                        }
                    })
                }

                if (showLocationDialogState.value && !showPermissionDialogState.value) {
                    LocationRequiredDialog(openLocationSettings = {
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        this@MainActivity.startActivity(intent)
                    })
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Asegurarnos de detener la escucha si la Activity se destruye
        if (::repository.isInitialized) {
            repository.stopEscuchaFirebase()
        }
    }
}
