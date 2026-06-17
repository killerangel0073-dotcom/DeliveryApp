package com.gruposanangel.delivery.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.RutaEntity
import com.gruposanangel.delivery.data.UsuarioEntity
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private val RojoDelisa = Color(0xFFE53935)
private val NegroPremium = Color(0xFF1E1E24)
private val GrisFondoPremium = Color(0xFFF6F8FA)
private val GrisTextoSecundario = Color(0xFF757575)

@Composable
fun Pantalla_Usuarios_Admin(navController: NavController) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val vm: UsuariosAdminViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    var imgBitmap by remember { mutableStateOf<Bitmap?>(null) }; var imgFile by remember { mutableStateOf<File?>(null) }
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { scope.launch { val file = createImageFile3(context); context.contentResolver.openInputStream(it)?.use { i -> FileOutputStream(file).use { o -> i.copyTo(o) } }; imgFile = file; imgBitmap = BitmapFactory.decodeFile(file.absolutePath) } } }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> bmp?.let { val file = createImageFile3(context); FileOutputStream(file).use { o -> it.compress(Bitmap.CompressFormat.JPEG, 85, o) }; imgFile = file; imgBitmap = it } }

    PantallaUsuariosAdminContent(
        uiState = uiState, imgBitmap = imgBitmap,
        onBack = { navController.popBackStack() },
        onUserSelect = { vm.seleccionarUsuario(it) },
        onImageSourceSelected = { if (it) cameraLauncher.launch(null) else galleryLauncher.launch("image/*") },
        onValidateLicense = { 
            val user = uiState.usuarioSeleccionado
            val uid = user?.uid ?: "new_user"
            val uNombre = user?.nombre?.ifBlank { "N" } ?: "N"
            navController.navigate("camara_escaneo_licencia/$uid/$uNombre")
        },
        onValidateINE = {
            val user = uiState.usuarioSeleccionado
            val uid = user?.uid ?: "new_user"
            val uNombre = user?.nombre?.ifBlank { "N" } ?: "N"
            navController.navigate("camara_escaneo_ine/$uid/$uNombre")
        },
        onSave = { n, e, p, a, l, c, pass -> vm.guardarUsuario(n, e, p, a, l, c, imgFile, pass) },
        onDelete = { vm.eliminarUsuario(it) },
        onRutaSelect = { vm.proponerRuta(it) },
        onRutaClear = { vm.proponerRuta(null) },
        onConfirmRuta = { vm.confirmarPropuestaRuta() },
        onCancelRuta = { vm.cancelarConfirmacionRuta() },
        onClearNombreIA = { vm.clearNombreExtraido() }
    )

    // Mostrar mensajes de éxito o error
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearMessages()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaUsuariosAdminContent(
    uiState: UsuariosAdminUiState, 
    imgBitmap: Bitmap?, 
    onBack: () -> Unit, 
    onUserSelect: (UsuarioEntity?) -> Unit, 
    onImageSourceSelected: (Boolean) -> Unit, 
    onValidateLicense: () -> Unit, 
    onValidateINE: () -> Unit, 
    onSave: (String, String, String, Boolean, String, String, String) -> Unit, 
    onDelete: (String) -> Unit,
    onRutaSelect: (RutaEntity) -> Unit,
    onRutaClear: () -> Unit,
    onConfirmRuta: () -> Unit,
    onCancelRuta: () -> Unit,
    onClearNombreIA: () -> Unit
) {
    val context = LocalContext.current
    var nombre by remember(uiState.usuarioSeleccionado) { mutableStateOf(uiState.usuarioSeleccionado?.nombre ?: "") }
    var email by remember(uiState.usuarioSeleccionado) { mutableStateOf(uiState.usuarioSeleccionado?.email ?: "") }
    var password by remember(uiState.usuarioSeleccionado) { mutableStateOf("") }
    var puesto by remember(uiState.usuarioSeleccionado) { mutableStateOf(uiState.usuarioSeleccionado?.puestoTrabajo ?: "") }
    var activo by remember(uiState.usuarioSeleccionado) { mutableStateOf(uiState.usuarioSeleccionado?.activo ?: true) }
    var showImgDialog by remember { mutableStateOf(false) }
    var showDelDialog by remember { mutableStateOf(false) }
    var showLicensePhotoDialog by remember { mutableStateOf(false) }
    var showINEPhotoDialog by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    // 🔥 AUTOLLENADO DE NOMBRE DESDE IA
    LaunchedEffect(uiState.nombreExtraidoDeIA) {
        uiState.nombreExtraidoDeIA?.let {
            nombre = it
            onClearNombreIA()
        }
    }

    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("GESTIÓN DE PERSONAL", fontWeight = FontWeight.Black, color = NegroPremium, style = MaterialTheme.typography.labelLarge) }, 
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RojoDelisa) } }, 
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            ) 
        },
        containerColor = GrisFondoPremium
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                item { UserSelectorCard("Nuevo", "Crear cuenta", icon = Icons.Default.PersonAdd, isSelected = uiState.isNewUserMode, onClick = { onUserSelect(null) }) }
                items(uiState.usuarios) { u -> UserSelectorCard(u.nombre, u.puestoTrabajo ?: "Sin puesto", photoUrl = u.photoUrl, isSelected = uiState.usuarioSeleccionado?.uid == u.uid && !uiState.isNewUserMode, onClick = { onUserSelect(u) }) }
            }
            Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(12.dp)) {
                Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { 
                        Text(if (uiState.isNewUserMode) "Nuevo Usuario" else "Editar Perfil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = RojoDelisa)
                        if (!uiState.isNewUserMode) IconButton(onClick = { showDelDialog = true }) { Icon(Icons.Default.DeleteForever, null, tint = GrisTextoSecundario) } 
                    }
                    Box(Modifier.size(110.dp).align(Alignment.CenterHorizontally).clickable { showImgDialog = true }, contentAlignment = Alignment.BottomEnd) {
                        Surface(shape = CircleShape, shadowElevation = 4.dp, modifier = Modifier.fillMaxSize()) {
                            if (imgBitmap != null) Image(bitmap = imgBitmap.asImageBitmap(), null, contentScale = ContentScale.Crop)
                            else AsyncImage(model = uiState.usuarioSeleccionado?.photoUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = null, contentScale = ContentScale.Crop)
                        }
                        Surface(shape = CircleShape, color = RojoDelisa, modifier = Modifier.size(30.dp)) { Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.padding(6.dp)) }
                    }
                    FormTextField(nombre, { nombre = it }, "Nombre Completo", Icons.Default.Badge)
                    FormTextField(email, { email = it }, "Correo Electrónico", Icons.Default.Email)
                    
                    if (uiState.isNewUserMode) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Contraseña de Acceso") },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = RojoDelisa) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = Color.Gray)
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RojoDelisa, focusedLabelColor = RojoDelisa, cursorColor = RojoDelisa),
                            singleLine = true
                        )
                    }

                    FormTextField(puesto, { puesto = it }, "Puesto", Icons.Default.Work)

                    // 🔥 SECCIÓN DE RUTA ASIGNADA
                    val user = uiState.usuarioSeleccionado
                    if (!uiState.isNewUserMode) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Explore, null, tint = RojoDelisa, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("RUTA ASIGNADA", fontWeight = FontWeight.Black, fontSize = 12.sp, color = RojoDelisa)
                                }
                                Spacer(Modifier.height(12.dp))
                                
                                var expandedRutas by remember { mutableStateOf(false) }
                                
                                Box {
                                    OutlinedCard(
                                        onClick = { expandedRutas = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            Modifier.padding(12.dp).fillMaxWidth(),
                                            Arrangement.SpaceBetween,
                                            Alignment.CenterVertically
                                        ) {
                                            // Mostrar el nombre propuesto si existe, sino el original
                                            val rutaAMostrar = uiState.rutaCambioPendienteNombre ?: user?.ultimaRutaNombre ?: "Sin ruta asignada"
                                            val esCambio = uiState.rutaCambioPendienteId != null
                                            
                                            Text(
                                                text = if (esCambio) "$rutaAMostrar (Pendiente)" else rutaAMostrar,
                                                fontWeight = FontWeight.Bold,
                                                color = if (esCambio) RojoDelisa else (if (user?.ultimaRutaId != null) NegroPremium else GrisTextoSecundario)
                                            )
                                            Icon(Icons.Default.ArrowDropDown, null, tint = NegroPremium)
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = expandedRutas,
                                        onDismissRequest = { expandedRutas = false },
                                        modifier = Modifier.fillMaxWidth(0.8f).background(Color.White)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Ninguna (Quitar ruta)", color = GrisTextoSecundario) },
                                            onClick = { onRutaClear(); expandedRutas = false }
                                        )
                                        HorizontalDivider(color = GrisFondoPremium)
                                        uiState.rutas.forEach { ruta ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Column {
                                                        Text(ruta.nombre, fontWeight = FontWeight.Bold, color = NegroPremium)
                                                    }
                                                },
                                                onClick = { onRutaSelect(ruta); expandedRutas = false }
                                            )
                                        }
                                    }
                                }
                                
                                if (uiState.rutaCambioPendienteId != null) {
                                    Text(
                                        "El cambio se aplicará al guardar.", 
                                        fontSize = 11.sp, 
                                        color = RojoDelisa, 
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // 🔥 SECCIONES DE AUDITORÍA (IA) - SIEMPRE VISIBLES
                    val licenciaEstado = user?.licenciaEstado ?: "PENDIENTE"
                    val ineEstado = user?.ineEstado ?: "PENDIENTE"
                    
                    val colorLicencia = when(licenciaEstado) {
                        "VIGENTE" -> Color(0xFF2E7D32)
                        "VENCIDA" -> RojoDelisa
                        else -> GrisTextoSecundario
                    }
                    val colorIne = when(ineEstado) {
                        "VIGENTE" -> Color(0xFF2E7D32)
                        "VENCIDA" -> RojoDelisa
                        else -> GrisTextoSecundario
                    }

                    // --- CARD LICENCIA ---
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = user?.licenciaFotoUrl != null) { showLicensePhotoDialog = true },
                        colors = CardDefaults.cardColors(containerColor = colorLicencia.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colorLicencia.copy(alpha = 0.2f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, null, tint = colorLicencia, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("AUDITORÍA DE LICENCIA", fontWeight = FontWeight.Black, fontSize = 12.sp, color = colorLicencia)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Estado: $licenciaEstado", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NegroPremium)
                            
                            if (user?.licenciaVencimiento != null && user.licenciaVencimiento > 0) {
                                Text("Vencimiento: ${sdf.format(Date(user.licenciaVencimiento))}", fontSize = 13.sp, color = GrisTextoSecundario)
                            }

                            if (uiState.resultadoLicencia != null && !uiState.isLoadingLicencia) {
                                Surface(
                                    color = colorLicencia.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, null, tint = colorLicencia, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Delisa IA: ${uiState.resultadoLicencia}", fontSize = 13.sp, color = colorLicencia, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                            
                            if (uiState.errorLicencia != null) {
                                Text("Delisa IA: ${uiState.errorLicencia}", fontSize = 11.sp, color = RojoDelisa, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                            }

                            Spacer(Modifier.height(12.dp))

                            if (uiState.isLoadingLicencia) {
                                DelisaThinkingAnimation()
                            } else {
                                Button(
                                    onClick = {
                                        onValidateLicense()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = colorLicencia),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(8.dp))
                                    Text("VALIDAR LICENCIA (IA)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // --- CARD INE ---
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = user?.ineFotoUrl != null) { showINEPhotoDialog = true },
                        colors = CardDefaults.cardColors(containerColor = colorIne.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colorIne.copy(alpha = 0.2f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBox, null, tint = colorIne, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("AUDITORÍA DE INE", fontWeight = FontWeight.Black, fontSize = 12.sp, color = colorIne)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Estado: $ineEstado", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NegroPremium)
                            
                            if (user?.ineVencimiento != null && user.ineVencimiento > 0) {
                                Text("Vencimiento: ${sdf.format(Date(user.ineVencimiento))}", fontSize = 13.sp, color = GrisTextoSecundario)
                            }

                            if (uiState.resultadoIne != null && !uiState.isLoadingIne) {
                                Surface(
                                    color = colorIne.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, null, tint = colorIne, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Delisa IA: ${uiState.resultadoIne}", fontSize = 13.sp, color = colorIne, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }

                            if (uiState.errorIne != null) {
                                Text("Delisa IA: ${uiState.errorIne}", fontSize = 11.sp, color = RojoDelisa, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                            }

                            Spacer(Modifier.height(12.dp))

                            if (uiState.isLoadingIne) {
                                DelisaThinkingAnimation()
                            } else {
                                Button(
                                    onClick = {
                                        onValidateINE()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = colorIne),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp))
                                    Text("VALIDAR INE (IA)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { 
                        Text("Cuenta Activa", fontWeight = FontWeight.Bold, color = NegroPremium)
                        Switch(
                            checked = activo, 
                            onCheckedChange = { activo = it }, 
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = RojoDelisa,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.LightGray,
                                checkedBorderColor = RojoDelisa,
                                uncheckedBorderColor = Color.LightGray
                            )
                        ) 
                    }
                    
                    if (uiState.isLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = RojoDelisa)
                    else Button(
                        onClick = { onSave(nombre, email, puesto, activo, user?.licenciaConducir ?: "", user?.credencialElector ?: "", password) }, 
                        modifier = Modifier.fillMaxWidth().height(54.dp), 
                        shape = RoundedCornerShape(16.dp), 
                        colors = ButtonDefaults.buttonColors(containerColor = RojoDelisa)
                    ) { 
                        Icon(if (uiState.isNewUserMode) Icons.Default.PersonAdd else Icons.Default.Save, null); 
                        Spacer(Modifier.width(8.dp)); 
                        Text(if (uiState.isNewUserMode) "CREAR CUENTA" else "ACTUALIZAR DATOS", fontWeight = FontWeight.ExtraBold) 
                    }
                }
            }
        }
    }
    if (showDelDialog) {
        AlertDialog(
            onDismissRequest = { showDelDialog = false },
            title = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("¿Eliminar Usuario?", fontWeight = FontWeight.Black, color = NegroPremium)
                }
            },
            text = {
                Text(
                    "¿Estás seguro que deseas borrar a ${uiState.usuarioSeleccionado?.nombre}?\nEsta acción no se puede deshacer.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { uiState.usuarioSeleccionado?.uid?.let { onDelete(it) }; showDelDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RojoDelisa),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ELIMINAR DEFINITIVAMENTE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelDialog = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("CANCELAR", color = GrisTextoSecundario)
                }
            }
        )
    }

    if (showImgDialog) {
        AlertDialog(
            onDismissRequest = { showImgDialog = false },
            title = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Actualizar Foto", fontWeight = FontWeight.Black, color = NegroPremium)
                }
            },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onImageSourceSelected(true); showImgDialog = false }
                            .padding(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = RojoDelisa.copy(alpha = 0.1f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, null, tint = RojoDelisa, modifier = Modifier.padding(16.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("CÁMARA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NegroPremium)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onImageSourceSelected(false); showImgDialog = false }
                            .padding(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = RojoDelisa.copy(alpha = 0.1f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(Icons.Outlined.Collections, null, tint = RojoDelisa, modifier = Modifier.padding(16.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("GALERÍA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NegroPremium)
                    }
                }
            },
            confirmButton = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { showImgDialog = false }) {
                        Text("CANCELAR", color = GrisTextoSecundario, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }
    
    if (showLicensePhotoDialog && uiState.usuarioSeleccionado?.licenciaFotoUrl != null) {
        AlertDialog(
            onDismissRequest = { showLicensePhotoDialog = false },
            title = { Text("Evidencia de Licencia", fontWeight = FontWeight.Black) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = uiState.usuarioSeleccionado.licenciaFotoUrl,
                        contentDescription = "Foto Licencia",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.58f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                        contentScale = ContentScale.FillBounds,
                        placeholder = painterResource(R.drawable.repartidor),
                        error = painterResource(R.drawable.repartidor)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Evidencia oficial de licencia de conducir.", fontSize = 12.sp, color = GrisTextoSecundario, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            },
            confirmButton = { Button(onClick = { showLicensePhotoDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = RojoDelisa)) { Text("CERRAR") } }
        )
    }

    if (showINEPhotoDialog && uiState.usuarioSeleccionado?.ineFotoUrl != null) {
        AlertDialog(
            onDismissRequest = { showINEPhotoDialog = false },
            title = { Text("Evidencia de INE", fontWeight = FontWeight.Black) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = uiState.usuarioSeleccionado.ineFotoUrl,
                        contentDescription = "Foto INE",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.58f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                        contentScale = ContentScale.FillBounds,
                        placeholder = painterResource(R.drawable.repartidor),
                        error = painterResource(R.drawable.repartidor)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Evidencia oficial de identificación INE/IFE.", fontSize = 12.sp, color = GrisTextoSecundario, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            },
            confirmButton = { Button(onClick = { showINEPhotoDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = RojoDelisa)) { Text("CERRAR") } }
        )
    }

    if (uiState.showRutaConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelRuta,
            title = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Confirmar Cambio de Ruta", fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            },
            text = {
                Text(
                    uiState.rutaConfirmationMessage ?: "",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = onConfirmRuta,
                        colors = ButtonDefaults.buttonColors(containerColor = RojoDelisa)
                    ) {
                        Text("SÍ, CAMBIAR")
                    }
                }
            },
            dismissButton = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onCancelRuta) {
                        Text("CANCELAR", color = GrisTextoSecundario)
                    }
                }
            }
        )
    }
}

@Composable
fun UserSelectorCard(nombre: String, subtitulo: String, photoUrl: String? = null, icon: ImageVector? = null, isSelected: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.size(width = 140.dp, height = 120.dp).clickable { onClick() }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) Color.White else Color(0xFFF1F2F6)), border = if (isSelected) BorderStroke(2.dp, RojoDelisa) else null, elevation = CardDefaults.cardElevation(if (isSelected) 8.dp else 2.dp)) {
        Column(Modifier.fillMaxSize().padding(12.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Box(Modifier.size(45.dp).clip(CircleShape).background(if (isSelected) RojoDelisa.copy(0.1f) else Color.White), Alignment.Center) {
                if (!photoUrl.isNullOrEmpty()) AsyncImage(model = photoUrl, null, contentScale = ContentScale.Crop)
                else Icon(icon ?: Icons.Default.Person, null, tint = if (isSelected) RojoDelisa else GrisTextoSecundario, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(8.dp)); Text(nombre, fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (isSelected) RojoDelisa else NegroPremium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitulo, fontSize = 10.sp, color = GrisTextoSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun FormTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, leadingIcon = { Icon(icon, null, tint = RojoDelisa) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = RojoDelisa, focusedLabelColor = RojoDelisa, cursorColor = RojoDelisa), singleLine = true)
}

@Composable
fun DelisaThinkingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)) {
            CircularProgressIndicator(
                modifier = Modifier.size(54.dp),
                color = RojoDelisa,
                strokeWidth = 3.dp,
                trackColor = RojoDelisa.copy(alpha = 0.1f)
            )
            Icon(Icons.Default.AutoAwesome, null, tint = RojoDelisa, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Delisa está analizando...", 
            style = MaterialTheme.typography.labelMedium,
            color = RojoDelisa,
            fontWeight = FontWeight.Black,
            modifier = Modifier.graphicsLayer(alpha = alpha)
        )
    }
}

fun createImageFile3(context: android.content.Context): File { val dir = File(context.filesDir, "usuarios"); if (!dir.exists()) dir.mkdirs(); return File(dir, "u_${System.currentTimeMillis()}.jpg") }

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun UsuariosAdminPreview() {
    val users = listOf(UsuarioEntity("1", "Lizeth Flores", "Gerente", "Si", ""), UsuarioEntity("2", "Juan Perez", "Vendedor", "Si", ""))
    DeliveryTheme { PantallaUsuariosAdminContent(UsuariosAdminUiState(usuarios = users), null, {}, {}, {}, {}, {}, {_,_,_,_,_,_,_ ->}, {}, {}, {}, {}, {}, {}) }
}
