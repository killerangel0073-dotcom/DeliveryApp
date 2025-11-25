@file:OptIn(ExperimentalMaterial3Api::class)

package com.gruposanangel.delivery.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.RepositoryCliente
import com.gruposanangel.delivery.data.ClienteEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*

@Composable
fun CrearClienteScreen(navController: NavController?, repository: RepositoryCliente? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isInPreview = LocalInspectionMode.current

    var nombreNegocio by remember { mutableStateOf(TextFieldValue("")) }
    var nombreDueno by remember { mutableStateOf(TextFieldValue("")) }
    var telefono by remember { mutableStateOf(TextFieldValue("")) }
    var correo by remember { mutableStateOf(TextFieldValue("")) }
    var ubicacion by remember { mutableStateOf(TextFieldValue("Cargando ubicación...")) }
    var ubicacionValida by remember { mutableStateOf(false) }

    var tipoExibidor by remember { mutableStateOf("Elige una opción") }
    val opcionesExibidor = listOf("No asignado", "Exhibidor de Mesa", "Exhibidor Normal", "Exhibidor Premium")
    var expanded by remember { mutableStateOf(false) }

    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val launcherGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> imageBitmap = BitmapFactory.decodeStream(stream) } }
    }
    val launcherCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { imageBitmap = it }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        if (!isInPreview) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            obtenerUbicacion(scope, context) {
                ubicacion = it
                ubicacionValida = !it.text.contains("Error") && !it.text.contains("No se pudo")
                // Si no se pudo obtener ubicación, usar Chalma como fallback
                if (!ubicacionValida) {
                    ubicacion = TextFieldValue("Chalma, Veracruz")
                    ubicacionValida = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    "Crear Nuevo Cliente",
                    modifier = Modifier.align(Alignment.Center),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp
                )
                IconButton(
                    onClick = {
                        navController?.navigate("delivery?screen=Clientes") {
                            launchSingleTop = true
                            popUpTo(0) { inclusive = true } // Borra toda la pila
                        }
                    }
                    ,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                }
            }
        },
        containerColor = Color.White
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Imagen del cliente
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .clickable { if (!isInPreview) showDialog = true }
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap!!.asImageBitmap(),
                        contentDescription = "Foto del cliente",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.repartidor),
                        contentDescription = "Foto del cliente",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                ModernOutlinedField("Nombre del negocio", nombreNegocio, onValueChange = { nombreNegocio = nombreNegocio.capitalizeWordsWithCursor(it) })
                ModernOutlinedField("Nombre del dueño", nombreDueno, onValueChange = { nombreDueno = nombreDueno.capitalizeWordsWithCursor(it) })
                ModernOutlinedField("Teléfono", telefono, keyboardType = KeyboardType.Number, onValueChange = { if (it.text.all { ch -> ch.isDigit() }) telefono = it })
                ModernOutlinedField("Correo", correo, keyboardType = KeyboardType.Email, onValueChange = { correo = it })

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = tipoExibidor,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de exhibidor", color = if (tipoExibidor != "Elige una opción") Color.Red else Color.Gray) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color.Red,
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color.Red,
                            focusedLabelColor = Color.Red
                        ),
                        textStyle = TextStyle(fontSize = 18.sp),
                        shape = RoundedCornerShape(10.dp)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        opcionesExibidor.forEach { opcion ->
                            DropdownMenuItem(text = { Text(opcion) }, onClick = { tipoExibidor = opcion; expanded = false })
                        }
                    }
                }

                ModernOutlinedField(
                    "Ubicación",
                    ubicacion,
                    maxLines = 2,
                    onValueChange = { },
                    readOnly = true
                )
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    errorMessage?.let { msg ->
                        Text(
                            msg,
                            color = Color.Red,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (isLoading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(20.dp))
            }

            Button(
                onClick = {
                    if (isLoading || isInPreview) return@Button
                    errorMessage = null
                    fun showError(msg: String) {
                        errorMessage = msg
                        scope.launch { delay(1500); errorMessage = null }
                    }

                    if (imageBitmap == null) { showError("Foto requerida"); return@Button }
                    if (nombreNegocio.text.isBlank()) { showError("Nombre del negocio requerido"); return@Button }
                    if (nombreDueno.text.isBlank()) { showError("Nombre del dueño requerido"); return@Button }
                    if (telefono.text.isBlank()) { showError("Teléfono requerido"); return@Button }
                    if (correo.text.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(correo.text).matches()) { showError("Correo inválido"); return@Button }
                    if (tipoExibidor == "Elige una opción") { showError("Debe seleccionar un tipo de exhibidor"); return@Button }

                    if (!ubicacionValida) {
                        showError("Aún no se obtuvo ubicación, intente de nuevo")
                        return@Button
                    }

                    scope.launch {
                        isLoading = true
                        try {
                            val fused = LocationServices.getFusedLocationProviderClient(context)
                            val lastLocation: Location? = try { fused.lastLocation.await() } catch (_: SecurityException) { null }
                            val lat = lastLocation?.latitude ?: 19.4895   // Chalma fallback
                            val lon = lastLocation?.longitude ?: -96.8289 // Chalma fallback

                            val clienteId = UUID.randomUUID().toString()
                            val photosDir = File(context.filesDir, "clientes_photos")
                            if (!photosDir.exists()) photosDir.mkdirs()
                            val destFile = File(photosDir, "$clienteId.jpg")
                            withContext(Dispatchers.IO) {
                                destFile.outputStream().use { stream -> imageBitmap!!.compress(Bitmap.CompressFormat.JPEG, 80, stream) }
                            }

                            val cliente = ClienteEntity(
                                id = clienteId,
                                nombreNegocio = nombreNegocio.text,
                                nombreDueno = nombreDueno.text,
                                telefono = telefono.text,
                                correo = correo.text,
                                tipoExhibidor = tipoExibidor,
                                ubicacionLat = lat,
                                ubicacionLon = lon,
                                fotografiaUrl = destFile.absolutePath,
                                activo = true,
                                medio = "medio",
                                fechaDeCreacion = System.currentTimeMillis(),
                                syncStatus = false
                            )

                            if (!isInPreview && repository != null) {
                                withContext(Dispatchers.IO) { repository.guardarLocal(cliente) }
                                Toast.makeText(context, "Cliente guardado localmente.", Toast.LENGTH_SHORT).show()
                                scope.launch(Dispatchers.IO) { repository.sincronizarConFirebase() }
                                navController?.navigate("delivery?screen=Inventario") { popUpTo("delivery") { inclusive = true } }
                            } else if (!isInPreview) {
                                val network = isNetworkAvailable(context)
                                if (network) {
                                    withContext(Dispatchers.IO) {
                                        val storage = FirebaseStorage.getInstance()
                                        val fileName = "clientes/${UUID.randomUUID()}.jpg"
                                        val ref = storage.reference.child(fileName)
                                        val baos = ByteArrayOutputStream()
                                        imageBitmap!!.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                                        ref.putBytes(baos.toByteArray()).await()
                                        val downloadUrl = ref.downloadUrl.await().toString()
                                        val geoPoint = if (lastLocation != null) GeoPoint(lastLocation.latitude, lastLocation.longitude)
                                        else GeoPoint(19.4895, -96.8289) // Chalma fallback
                                        val fechaActual = Timestamp.now()

                                        val clienteData = hashMapOf(
                                            "FotografiaCliente" to downloadUrl,
                                            "nombreNegocio" to nombreNegocio.text,
                                            "nombreDueno" to nombreDueno.text,
                                            "telefono" to telefono.text,
                                            "correo" to correo.text,
                                            "ubicacion" to geoPoint,
                                            "medio" to "medio",
                                            "activo" to true,
                                            "tipoExhibidor" to tipoExibidor,
                                            "fechaDeCreacion" to fechaActual,
                                            "ownerUid" to com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid,
                                            "lastModified" to System.currentTimeMillis()
                                        )

                                            // 🚀 YA NO USAMOS "add()". Ahora usamos el ID que generaste arriba:
                                        FirebaseFirestore.getInstance()
                                            .collection("clientes")
                                            .document(clienteId) // <-- usa el mismo ID que usarás localmente
                                            .set(clienteData)
                                            .await()



                                    }


                                    Toast.makeText(context, "Cliente creado exitosamente", Toast.LENGTH_SHORT).show()
                                    navController?.navigate("delivery?screen=Inventario") { popUpTo("delivery") { inclusive = true } }
                                } else {
                                    errorMessage = "No se pudo guardar: no hay repositorio local y no hay conexión."
                                }
                            }

                        } catch (e: Exception) {
                            e.printStackTrace()
                            errorMessage = "Error al registrar cliente: ${e.message ?: "desconocido"}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000), contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Guardar Cliente", fontSize = 20.sp)
            }
        }
    }

    if (showDialog && !isInPreview) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Selecciona una opción") },
            confirmButton = {
                Column {
                    Button(
                        onClick = { launcherCamera.launch(null); showDialog = false },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000), contentColor = Color.White)
                    ) { Text("Cámara") }

                    Button(
                        onClick = { launcherGallery.launch("image/*"); showDialog = false },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000), contentColor = Color.White)
                    ) { Text("Galería") }

                    OutlinedButton(
                        onClick = { showDialog = false },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF0000))
                    ) { Text("Cancelar") }
                }
            },
            dismissButton = {}
        )
    }
}

// --- Campos y helpers ---
@Composable
fun ModernOutlinedField(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    maxLines: Int = 1,
    readOnly: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = if (value.text.isNotBlank()) Color.Red else Color.Gray) },
        singleLine = maxLines == 1,
        maxLines = maxLines,
        readOnly = readOnly,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = Color.Red,
            unfocusedBorderColor = Color.Gray,
            cursorColor = Color.Red,
            focusedLabelColor = Color.Red
        ),
        textStyle = TextStyle(fontSize = 18.sp),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
}

fun TextFieldValue.capitalizeWordsWithCursor(newValue: TextFieldValue): TextFieldValue {
    val words = newValue.text.split(" ").map { word ->
        if (word.isNotEmpty()) word.replaceFirstChar { it.uppercaseChar() } else word
    }.joinToString(" ")
    val cursor = newValue.selection.start.coerceIn(0, words.length)
    return TextFieldValue(words, selection = androidx.compose.ui.text.TextRange(cursor))
}

@SuppressLint("MissingPermission")
fun obtenerUbicacion(scope: CoroutineScope, context: android.content.Context, onResult: (TextFieldValue) -> Unit) {
    scope.launch(Dispatchers.IO) {
        try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val location = try {
                withTimeoutOrNull(10000) {
                    fused.getCurrentLocation(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                        com.google.android.gms.tasks.CancellationTokenSource().token
                    ).await()
                }
            } catch (e: Exception) {
                null
            }
            val direccion = if (location != null) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val dir = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                dir?.firstOrNull()?.getAddressLine(0) ?: "${location.latitude}, ${location.longitude}"
            } else {
                "No se pudo obtener ubicación"
            }
            onResult(TextFieldValue(direccion))
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(TextFieldValue("Error al obtener ubicación"))
        }
    }
}

fun isNetworkAvailable(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val nw = cm.activeNetwork ?: return false
        val actNw = cm.getNetworkCapabilities(nw) ?: return false
        return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } else {
        @Suppress("DEPRECATION")
        val netInfo = cm.activeNetworkInfo ?: return false
        @Suppress("DEPRECATION")
        return netInfo.isConnected
    }
}

@Preview(showBackground = true)
@Composable
fun CrearClientePreview() {
    CrearClienteScreen(rememberNavController(), repository = null)
}
