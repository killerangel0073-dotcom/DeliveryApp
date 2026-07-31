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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.text.style.TextAlign
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
import com.gruposanangel.delivery.data.PerfilVenta
import com.gruposanangel.delivery.data.FiltroPerfil
import com.gruposanangel.delivery.ui.theme.*
import com.gruposanangel.delivery.utilidades.DialogoSeleccionImagen
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

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
        onDelete = { uid, motivo -> vm.eliminarUsuario(uid, motivo) },
        onRutaSelect = { vm.proponerRuta(it) },
        onRutaClear = { vm.proponerRuta(null) },
        onConfirmRuta = { vm.confirmarPropuestaRuta() },
        onCancelRuta = { vm.cancelarConfirmacionRuta() },
        onClearNombreIA = { vm.clearNombreExtraido() },
        onAgregarPerfil = { vm.agregarPerfil(it) },
        onEliminarPerfil = { vm.eliminarPerfil(it) },
        onActualizarPerfil = { vm.actualizarPerfil(it) }
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
    onDelete: (String, String) -> Unit,
    onRutaSelect: (RutaEntity) -> Unit,
    onRutaClear: () -> Unit,
    onConfirmRuta: () -> Unit,
    onCancelRuta: () -> Unit,
    onClearNombreIA: () -> Unit,
    onAgregarPerfil: (PerfilVenta) -> Unit,
    onEliminarPerfil: (String) -> Unit,
    onActualizarPerfil: (PerfilVenta) -> Unit
) {
    val context = LocalContext.current
    var nombre by remember(uiState.usuarioSeleccionado) { mutableStateOf(uiState.usuarioSeleccionado?.nombre ?: "") }
    var email by remember(uiState.usuarioSeleccionado) { mutableStateOf(uiState.usuarioSeleccionado?.email ?: "") }
    var password by remember(uiState.usuarioSeleccionado) { mutableStateOf("") }
    var puesto by remember(uiState.usuarioSeleccionado) { mutableStateOf(uiState.usuarioSeleccionado?.puestoTrabajo ?: "") }
    var activo by remember(uiState.usuarioSeleccionado) { mutableStateOf(uiState.usuarioSeleccionado?.activo ?: true) }
    var showImgDialog by remember { mutableStateOf(false) }
    var showDelDialog by remember { mutableStateOf(false) }
    var motivoBaja by remember { mutableStateOf("") }
    var showStatusConfirmDialog by remember { mutableStateOf(false) }
    var showPuestoConfirmDialog by remember { mutableStateOf(false) }
    var pendingPuestoChange by remember { mutableStateOf("") }
    var pendingStatusChange by remember { mutableStateOf(true) }
    var showLicensePhotoDialog by remember { mutableStateOf(false) }
    var showINEPhotoDialog by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    // 🔥 ESTADOS PARA PERFILES DE VENTA
    var showPerfilDialog by remember { mutableStateOf(false) }
    var perfilAEditar by remember { mutableStateOf<PerfilVenta?>(null) }

    // 🎭 Animación para la Foto de Perfil Principal
    val profileInteractionSource = remember { MutableInteractionSource() }
    val isProfilePressed by profileInteractionSource.collectIsPressedAsState()
    val profileScale by animateFloatAsState(
        targetValue = if (isProfilePressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "profileScale"
    )

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
                title = { Text("GESTIÓN DE PERSONAL", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge) }, 
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = DelisaRed) } }, 
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            ) 
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                item { UserSelectorCard("Nuevo", "Crear cuenta", icon = Icons.Default.PersonAdd, isSelected = uiState.isNewUserMode, onClick = { onUserSelect(null) }) }
                items(uiState.usuarios) { u -> UserSelectorCard(u.nombre, u.puestoTrabajo ?: "Sin puesto", photoUrl = u.photoUrl, isSelected = uiState.usuarioSeleccionado?.uid == u.uid && !uiState.isNewUserMode, onClick = { onUserSelect(u) }) }
            }
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f), 
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp) // Eliminamos elevación para evitar grises
            ) {
                Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { 
                        Text(if (uiState.isNewUserMode) "Nuevo Usuario" else "Editar Perfil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = DelisaRed)
                        if (!uiState.isNewUserMode) IconButton(onClick = { showDelDialog = true }) { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } 
                    }
                    Box(
                        Modifier
                            .size(110.dp)
                            .align(Alignment.CenterHorizontally)
                            .graphicsLayer {
                                scaleX = profileScale
                                scaleY = profileScale
                            }
                            .clickable(
                                interactionSource = profileInteractionSource,
                                indication = null
                            ) { showImgDialog = true }, 
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Surface(
                            shape = CircleShape, 
                            shadowElevation = 4.dp, 
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            if (imgBitmap != null) Image(bitmap = imgBitmap.asImageBitmap(), null, contentScale = ContentScale.Crop)
                            else AsyncImage(model = uiState.usuarioSeleccionado?.photoUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = null, contentScale = ContentScale.Crop)
                        }
                        Surface(shape = CircleShape, color = DelisaRed, modifier = Modifier.size(30.dp)) { Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.padding(6.dp)) }
                    }
                    FormTextField(nombre, { nombre = it }, "Nombre Completo", Icons.Default.Badge)
                    FormTextField(email, { email = it }, "Correo Electrónico", Icons.Default.Email)
                    
                    // 🔥 SELECTOR DE PUESTO (DROPDOWN)
                    var expandedPuestos by remember { mutableStateOf(false) }
                    Box {
                        OutlinedTextField(
                            value = puesto,
                            onValueChange = { },
                            readOnly = true,
                            label = { Text("Puesto / Cargo") },
                            leadingIcon = { Icon(Icons.Default.Work, null, tint = DelisaRed) },
                            trailingIcon = { 
                                IconButton(onClick = { expandedPuestos = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable { expandedPuestos = true },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DelisaRed, 
                                focusedLabelColor = DelisaRed, 
                                cursorColor = DelisaRed,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            enabled = true
                        )
                        DropdownMenu(
                            expanded = expandedPuestos,
                            onDismissRequest = { expandedPuestos = false },
                            modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surface)
                        ) {
                            uiState.puestosDisponibles.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = { 
                                        if (p != puesto && !uiState.isNewUserMode) {
                                            pendingPuestoChange = p
                                            showPuestoConfirmDialog = true
                                        } else {
                                            puesto = p
                                        }
                                        expandedPuestos = false 
                                    }
                                )
                            }
                        }
                    }
                    
                    if (uiState.isNewUserMode) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Contraseña de Acceso") },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = DelisaRed) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DelisaRed, 
                                focusedLabelColor = DelisaRed, 
                                cursorColor = DelisaRed,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true
                        )
                    }

                    // 🔥 SECCIÓN DE RUTA ASIGNADA
                    val user = uiState.usuarioSeleccionado
                    if (!uiState.isNewUserMode) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Explore, null, tint = DelisaRed, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("RUTA ASIGNADA", fontWeight = FontWeight.Black, fontSize = 12.sp, color = DelisaRed)
                                }
                                Spacer(Modifier.height(12.dp))
                                
                                var expandedRutas by remember { mutableStateOf(false) }
                                
                                Box {
                                    OutlinedCard(
                                        onClick = { expandedRutas = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    ) {
                                        Row(
                                            Modifier.padding(12.dp).fillMaxWidth(),
                                            Arrangement.SpaceBetween,
                                            Alignment.CenterVertically
                                        ) {
                                            val rutaAMostrar = uiState.rutaCambioPendienteNombre ?: user?.ultimaRutaNombre ?: "Sin ruta asignada"
                                            val esCambio = uiState.rutaCambioPendienteId != null
                                            
                                            Text(
                                                text = if (esCambio) "$rutaAMostrar (Pendiente)" else rutaAMostrar,
                                                fontWeight = FontWeight.Bold,
                                                color = if (esCambio) DelisaRed else (if (user?.ultimaRutaId != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                            Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = expandedRutas,
                                        onDismissRequest = { expandedRutas = false },
                                        modifier = Modifier.fillMaxWidth(0.8f).background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Ninguna (Quitar ruta)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                            onClick = { onRutaClear(); expandedRutas = false }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                                        uiState.rutas.forEach { ruta ->
                                            DropdownMenuItem(
                                                text = { 
                                                    Column {
                                                        Text(ruta.nombre, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                                        color = DelisaRed, 
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
                        "VIGENTE" -> DelisaGreen
                        "VENCIDA" -> DelisaRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val colorIne = when(ineEstado) {
                        "VIGENTE" -> DelisaGreen
                        "VENCIDA" -> DelisaRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                            Text("Estado: $licenciaEstado", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            
                            if (user?.licenciaVencimiento != null && user.licenciaVencimiento > 0) {
                                Text("Vencimiento: ${sdf.format(Date(user.licenciaVencimiento))}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text("Delisa IA: ${uiState.errorLicencia}", fontSize = 11.sp, color = DelisaRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
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
                                    Icon(Icons.Default.PhotoCamera, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                                    Text("VALIDAR LICENCIA (IA)", fontWeight = FontWeight.Bold, color = Color.White)
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
                            Text("Estado: $ineEstado", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            
                            if (user?.ineVencimiento != null && user.ineVencimiento > 0) {
                                Text("Vencimiento: ${sdf.format(Date(user.ineVencimiento))}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text("Delisa IA: ${uiState.errorIne}", fontSize = 11.sp, color = DelisaRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
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
                                    Icon(Icons.Default.Fingerprint, null, tint = Color.White); Spacer(Modifier.width(8.dp))
                                    Text("VALIDAR INE (IA)", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 🔥 SECCIÓN: PERFILES DE VENTA (Configuración Multimarca)
                    if (!uiState.isNewUserMode) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("PERFILES DE VENTA", fontWeight = FontWeight.Black, fontSize = 12.sp, color = DelisaRed)
                            TextButton(onClick = { perfilAEditar = null; showPerfilDialog = true }) {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("AGREGAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        
                        if (uiState.perfilesVentaEdit.isEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Sin perfiles. Se venderá todo Delisa por defecto.",
                                    modifier = Modifier.padding(16.dp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        uiState.perfilesVentaEdit.forEach { perfil ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(perfil.nombre, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        perfil.filtros.forEach { f ->
                                            Text(
                                                "${f.marca}: ${if (f.categorias.isEmpty()) "Toda la marca" else f.categorias.joinToString(", ")}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(onClick = { perfilAEditar = perfil; showPerfilDialog = true }) {
                                        Icon(Icons.Default.Edit, null, tint = DelisaGreen, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { onEliminarPerfil(perfil.id) }) {
                                        Icon(Icons.Default.Delete, null, tint = DelisaRed, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { 
                        Text("Acceso a la App (Suspendido/Activo)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Switch(
                            checked = activo, 
                            onCheckedChange = { 
                                pendingStatusChange = it
                                showStatusConfirmDialog = true
                            }, 
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DelisaRed,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                checkedBorderColor = DelisaRed,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        ) 
                    }
                    
                    if (uiState.isLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = DelisaRed)
                    else Button(
                        onClick = { onSave(nombre, email, puesto, activo, user?.licenciaConducir ?: "", user?.credencialElector ?: "", password) }, 
                        modifier = Modifier.fillMaxWidth().height(54.dp), 
                        shape = RoundedCornerShape(16.dp), 
                        colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
                    ) { 
                        Icon(if (uiState.isNewUserMode) Icons.Default.PersonAdd else Icons.Default.Save, null, tint = Color.White); 
                        Spacer(Modifier.width(8.dp)); 
                        Text(if (uiState.isNewUserMode) "CREAR CUENTA" else "ACTUALIZAR DATOS", fontWeight = FontWeight.ExtraBold, color = Color.White) 
                    }
                }
            }
        }
    }
    if (showDelDialog) {
        AlertDialog(
            onDismissRequest = { showDelDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "BAJA DE PERSONAL", 
                    fontWeight = FontWeight.Black, 
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val tieneRuta = uiState.usuarioSeleccionado?.ultimaRutaId != null
                    Text(
                        if (tieneRuta) 
                            "¿Deseas dar de baja definitiva a ${uiState.usuarioSeleccionado?.nombre}?\nSe liberará su ruta (${uiState.usuarioSeleccionado?.ultimaRutaNombre}) y perderá acceso."
                        else 
                            "¿Deseas dar de baja definitiva a ${uiState.usuarioSeleccionado?.nombre}?\nPerderá acceso inmediato a la plataforma.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = motivoBaja,
                        onValueChange = { motivoBaja = it },
                        label = { Text("Motivo de la baja") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DelisaRed,
                            focusedLabelColor = DelisaRed,
                            cursorColor = DelisaRed,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        if (motivoBaja.isNotBlank()) {
                            uiState.usuarioSeleccionado?.uid?.let { onDelete(it, motivoBaja) }
                            showDelDialog = false 
                        } else {
                            Toast.makeText(context, "Por favor escribe un motivo", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CONFIRMAR BAJA DEFINITIVA", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelDialog = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showImgDialog) {
        DialogoSeleccionImagen(
            onDismiss = { showImgDialog = false },
            onCameraSelected = { onImageSourceSelected(true); showImgDialog = false },
            onGallerySelected = { onImageSourceSelected(false); showImgDialog = false }
        )
    }
    
    if (showLicensePhotoDialog && uiState.usuarioSeleccionado?.licenciaFotoUrl != null) {
        AlertDialog(
            onDismissRequest = { showLicensePhotoDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Evidencia de Licencia", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = uiState.usuarioSeleccionado.licenciaFotoUrl,
                        contentDescription = "Foto Licencia",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.58f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.background),
                        contentScale = ContentScale.FillBounds,
                        placeholder = painterResource(R.drawable.repartidor),
                        error = painterResource(R.drawable.repartidor)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Evidencia oficial de licencia de conducir.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            },
            confirmButton = { Button(onClick = { showLicensePhotoDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)) { Text("CERRAR", color = Color.White) } }
        )
    }

    if (showINEPhotoDialog && uiState.usuarioSeleccionado?.ineFotoUrl != null) {
        AlertDialog(
            onDismissRequest = { showINEPhotoDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Evidencia de INE", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = uiState.usuarioSeleccionado.ineFotoUrl,
                        contentDescription = "Foto INE",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.58f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.background),
                        contentScale = ContentScale.FillBounds,
                        placeholder = painterResource(R.drawable.repartidor),
                        error = painterResource(R.drawable.repartidor)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Evidencia oficial de identificación INE/IFE.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            },
            confirmButton = { Button(onClick = { showINEPhotoDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)) { Text("CERRAR", color = Color.White) } }
        )
    }

    if (uiState.showRutaConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelRuta,
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Confirmar Cambio de Ruta", fontWeight = FontWeight.Black, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            text = {
                Text(
                    uiState.rutaConfirmationMessage ?: "",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = onConfirmRuta,
                        colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
                    ) {
                        Text("SÍ, CAMBIAR", color = Color.White)
                    }
                }
            },
            dismissButton = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = onCancelRuta) {
                        Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        )
    }

    if (showStatusConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStatusConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    if (pendingStatusChange) "Activar Cuenta" else "Desactivar Cuenta", 
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    if (pendingStatusChange) 
                        "¿Deseas reactivar el acceso para ${uiState.usuarioSeleccionado?.nombre}?"
                    else 
                        "Al desactivar la cuenta, el usuario perderá acceso inmediato a la aplicación. ¿Deseas continuar?",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = { 
                        activo = pendingStatusChange
                        showStatusConfirmDialog = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CONFIRMAR", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStatusConfirmDialog = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showPuestoConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPuestoConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "Confirmar Cambio de Puesto", 
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                val tieneRuta = uiState.usuarioSeleccionado?.ultimaRutaId != null
                val esVendedor = uiState.usuarioSeleccionado?.puestoTrabajo?.contains("Vendedor") == true || uiState.usuarioSeleccionado?.puestoTrabajo?.contains("Suplente") == true
                val nuevoEsVendedor = pendingPuestoChange.contains("Vendedor") || pendingPuestoChange.contains("Suplente")

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "¿Estás seguro de cambiar el puesto de ${uiState.usuarioSeleccionado?.nombre} a '$pendingPuestoChange'?",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    if (esVendedor && !nuevoEsVendedor && tieneRuta) {
                        Surface(
                            color = DelisaRed.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                "⚠️ ADVERTENCIA: Al dejar de ser vendedor, la ruta '${uiState.usuarioSeleccionado?.ultimaRutaNombre}' se liberará automáticamente.",
                                color = DelisaRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        puesto = pendingPuestoChange
                        showPuestoConfirmDialog = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DelisaRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CONFIRMAR CAMBIO", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPuestoConfirmDialog = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    if (showPerfilDialog) {
        DialogPerfilVenta(
            perfilInicial = perfilAEditar,
            marcas = uiState.marcasDisponibles,
            categoriasMap = uiState.categoriasDisponibles,
            onDismiss = { showPerfilDialog = false },
            onSave = {
                if (perfilAEditar == null) onAgregarPerfil(it)
                else onActualizarPerfil(it)
                showPerfilDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogPerfilVenta(
    perfilInicial: PerfilVenta?,
    marcas: List<String>,
    categoriasMap: Map<String, List<String>>,
    onDismiss: () -> Unit,
    onSave: (PerfilVenta) -> Unit
) {
    var nombre by remember { mutableStateOf(perfilInicial?.nombre ?: "") }
    var marcaSeleccionada by remember { mutableStateOf(perfilInicial?.filtros?.firstOrNull()?.marca ?: marcas.firstOrNull() ?: "Delisa") }
    val categoriasIniciales = perfilInicial?.filtros?.firstOrNull()?.categorias ?: emptyList()
    val categoriasSeleccionadas = remember { mutableStateListOf<String>().apply { addAll(categoriasIniciales) } }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (perfilInicial == null) "Nuevo Perfil" else "Editar Perfil", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre (Ej: Botanas Delisa)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Marca Principal", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DelisaRed)
                
                var expandedMarcas by remember { mutableStateOf(false) }
                Box {
                    OutlinedCard(onClick = { expandedMarcas = true }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(marcaSeleccionada)
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    }
                    DropdownMenu(expanded = expandedMarcas, onDismissRequest = { expandedMarcas = false }) {
                        marcas.forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = { marcaSeleccionada = m; expandedMarcas = false; categoriasSeleccionadas.clear() })
                        }
                    }
                }

                Text("Categorías (Si no eliges, entra toda la marca)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DelisaRed)
                
                val categoriasDisponibles = categoriasMap[marcaSeleccionada] ?: emptyList()
                
                if (categoriasDisponibles.isEmpty()) {
                    Text("No hay categorías registradas para esta marca.", fontSize = 11.sp, color = Color.Gray)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        mainAxisSpacing = 8.dp,
                        crossAxisSpacing = 4.dp
                    ) {
                        categoriasDisponibles.forEach { cat ->
                            val isSelected = categoriasSeleccionadas.contains(cat)
                            FilterChip(
                                selected = isSelected,
                                onClick = { if (isSelected) categoriasSeleccionadas.remove(cat) else categoriasSeleccionadas.add(cat) },
                                label = { Text(cat, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DelisaRed,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nombre.isNotBlank()) {
                        val filtro = FiltroPerfil(marcaSeleccionada, categoriasSeleccionadas.toList())
                        onSave(PerfilVenta(perfilInicial?.id ?: "", nombre, listOf(filtro)))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DelisaRed)
            ) { Text("GUARDAR") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCELAR") } }
    )
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeholders = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val rows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var rowWidth = 0
        
        placeholders.forEach { placeable ->
            if (rowWidth + placeable.width + mainAxisSpacing.roundToPx() > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                rowWidth = 0
            }
            currentRow.add(placeable)
            rowWidth += placeable.width + mainAxisSpacing.roundToPx()
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)
        
        val totalHeight = rows.sumOf { row -> row.maxOf { it.height } } + (rows.size - 1) * crossAxisSpacing.roundToPx()
        
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOf { it.height }
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + mainAxisSpacing.roundToPx()
                }
                y += rowHeight + crossAxisSpacing.roundToPx()
            }
        }
    }
}

@Composable
fun UserSelectorCard(nombre: String, subtitulo: String, photoUrl: String? = null, icon: ImageVector? = null, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // 🎭 Animación de Escala (Efecto de presión "Tesla Style")
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else if (isSelected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "cardScale"
    )

    // 🎨 Animación de Colores Suaves
    val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface, label = "bgColor")
    val contentColor by animateColorAsState(if (isSelected) DelisaRed else MaterialTheme.colorScheme.onSurface, label = "contentColor")
    val elevation by animateDpAsState(if (isSelected) 10.dp else 2.dp, label = "elevation")

    Card(
        modifier = Modifier
            .size(width = 145.dp, height = 125.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(24.dp),
                spotColor = if (isSelected) DelisaRed.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.2f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple(bounded = true, color = DelisaRed.copy(alpha = 0.1f)),
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = if (isSelected) BorderStroke(2.dp, DelisaRed) else BorderStroke(1.dp, Color.Transparent)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp), 
            Arrangement.Center, 
            Alignment.CenterHorizontally
        ) {
            // Contenedor de Imagen con Efecto de Profundidad
            Box(
                Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) DelisaRed.copy(0.08f) else MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (isSelected) 1.dp else 0.dp,
                        color = if (isSelected) DelisaRed.copy(0.2f) else Color.Transparent,
                        shape = CircleShape
                    ), 
                Alignment.Center
            ) {
                if (!photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = photoUrl, 
                        contentDescription = null, 
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = icon ?: Icons.Default.Person, 
                        contentDescription = null, 
                        tint = if (isSelected) DelisaRed else MaterialTheme.colorScheme.onSurfaceVariant, 
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(10.dp))
            
            Text(
                text = nombre, 
                fontSize = 13.sp, 
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, 
                color = contentColor, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = subtitulo, 
                fontSize = 10.sp, 
                color = if (isSelected) contentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun FormTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector) {
    OutlinedTextField(
        value = value, 
        onValueChange = onValueChange, 
        label = { Text(label) }, 
        leadingIcon = { Icon(icon, null, tint = DelisaRed) }, 
        modifier = Modifier.fillMaxWidth(), 
        shape = RoundedCornerShape(16.dp), 
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DelisaRed, 
            focusedLabelColor = DelisaRed, 
            cursorColor = DelisaRed,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ), 
        singleLine = true
    )
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
                color = DelisaRed,
                strokeWidth = 3.dp,
                trackColor = DelisaRed.copy(alpha = 0.1f)
            )
            Icon(Icons.Default.AutoAwesome, null, tint = DelisaRed, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Delisa está analizando...", 
            style = MaterialTheme.typography.labelMedium,
            color = DelisaRed,
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
    DeliveryTheme { PantallaUsuariosAdminContent(UsuariosAdminUiState(usuarios = users), null, {}, {}, {}, {}, {}, {_,_,_,_,_,_,_ ->}, {_,_ ->}, {}, {}, {}, {}, {}, {}, {}, {}) }
}
