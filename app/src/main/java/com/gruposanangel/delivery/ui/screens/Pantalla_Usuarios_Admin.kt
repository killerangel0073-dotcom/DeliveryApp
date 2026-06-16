package com.gruposanangel.delivery.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.UsuarioEntity
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
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
            if (user != null) {
                navController.navigate("camara_escaneo_licencia/${user.uid}/${user.nombre}")
            }
        },
        onValidateINE = {
            val user = uiState.usuarioSeleccionado
            if (user != null) {
                navController.navigate("camara_escaneo_ine/${user.uid}/${user.nombre}")
            }
        },
        onSave = { n, e, p, a, l, c -> vm.guardarUsuario(n, e, p, a, l, c, imgFile) },
        onDelete = { vm.eliminarUsuario(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaUsuariosAdminContent(uiState: UsuariosAdminUiState, imgBitmap: Bitmap?, onBack: () -> Unit, onUserSelect: (UsuarioEntity?) -> Unit, onImageSourceSelected: (Boolean) -> Unit, onValidateLicense: () -> Unit, onValidateINE: () -> Unit, onSave: (String, String, String, Boolean, String, String) -> Unit, onDelete: (String) -> Unit) {
    val context = LocalContext.current
    var nombre by remember(uiState.usuarioSeleccionado) { mutableStateOf(uiState.usuarioSeleccionado?.nombre ?: "") }
    var email by remember(uiState.usuarioSeleccionado) { mutableStateOf(uiState.usuarioSeleccionado?.email ?: "") }
    var puesto by remember(uiState.usuarioSeleccionado) { mutableStateOf(uiState.usuarioSeleccionado?.puestoTrabajo ?: "") }
    var activo by remember(uiState.usuarioSeleccionado) { mutableStateOf(uiState.usuarioSeleccionado?.activo ?: true) }
    var showImgDialog by remember { mutableStateOf(false) }
    var showDelDialog by remember { mutableStateOf(false) }
    var showLicensePhotoDialog by remember { mutableStateOf(false) }
    var showINEPhotoDialog by remember { mutableStateOf(false) }

    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    Scaffold(
        topBar = { TopAppBar(title = { Text("GESTIÓN DE PERSONAL", fontWeight = FontWeight.Black, color = Color.DarkGray, style = MaterialTheme.typography.labelLarge) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Red) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) },
        containerColor = Color(0xFFF8F9FA)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                item { UserSelectorCard("Nuevo", "Crear cuenta", icon = Icons.Default.PersonAdd, isSelected = uiState.isNewUserMode, onClick = { onUserSelect(null) }) }
                items(uiState.usuarios) { u -> UserSelectorCard(u.nombre, u.puestoTrabajo ?: "Sin puesto", photoUrl = u.photoUrl, isSelected = uiState.usuarioSeleccionado?.uid == u.uid && !uiState.isNewUserMode, onClick = { onUserSelect(u) }) }
            }
            Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(12.dp)) {
                Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(if (uiState.isNewUserMode) "Nuevo Usuario" else "Editar Perfil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.Red); if (!uiState.isNewUserMode) IconButton(onClick = { showDelDialog = true }) { Icon(Icons.Default.DeleteForever, null, tint = Color.Gray) } }
                    Box(Modifier.size(110.dp).align(Alignment.CenterHorizontally).clickable { showImgDialog = true }, contentAlignment = Alignment.BottomEnd) {
                        Surface(shape = CircleShape, shadowElevation = 4.dp, modifier = Modifier.fillMaxSize()) {
                            if (imgBitmap != null) Image(bitmap = imgBitmap.asImageBitmap(), null, contentScale = ContentScale.Crop)
                            else AsyncImage(model = uiState.usuarioSeleccionado?.photoUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = null, contentScale = ContentScale.Crop)
                        }
                        Surface(shape = CircleShape, color = Color.Red, modifier = Modifier.size(30.dp)) { Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.padding(6.dp)) }
                    }
                    FormTextField(nombre, { nombre = it }, "Nombre Completo", Icons.Default.Badge)
                    FormTextField(email, { email = it }, "Correo", Icons.Default.Email)
                    FormTextField(puesto, { puesto = it }, "Puesto", Icons.Default.Work)
                    
                    // 🔥 SECCIONES DE AUDITORÍA (IA) - SIEMPRE VISIBLES
                    val user = uiState.usuarioSeleccionado
                    val licenciaEstado = user?.licenciaEstado ?: "PENDIENTE"
                    val ineEstado = user?.ineEstado ?: "PENDIENTE"
                    
                    val colorLicencia = when(licenciaEstado) {
                        "VIGENTE" -> Color(0xFF2E7D32)
                        "VENCIDA" -> Color.Red
                        else -> Color.Gray
                    }
                    val colorIne = when(ineEstado) {
                        "VIGENTE" -> Color(0xFF2E7D32)
                        "VENCIDA" -> Color.Red
                        else -> Color.Gray
                    }

                    // --- CARD LICENCIA ---
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = user?.licenciaFotoUrl != null) { showLicensePhotoDialog = true },
                        colors = CardDefaults.cardColors(containerColor = colorLicencia.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorLicencia.copy(alpha = 0.2f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VerifiedUser, null, tint = colorLicencia, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("AUDITORÍA DE LICENCIA", fontWeight = FontWeight.Black, fontSize = 12.sp, color = colorLicencia)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Estado: $licenciaEstado", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            
                            if (user?.licenciaVencimiento != null && user.licenciaVencimiento > 0) {
                                Text("Vencimiento: ${sdf.format(Date(user.licenciaVencimiento))}", fontSize = 13.sp, color = Color.DarkGray)
                            }

                            if (uiState.resultadoIA?.contains("VIGENTE") == true && uiState.isLoadingIA.not()) {
                                Text("Delisa IA: ${uiState.resultadoIA}", fontSize = 11.sp, color = Color.Gray)
                            }

                            Spacer(Modifier.height(12.dp))

                            if (uiState.isLoadingIA) {
                                // Animación de escaneo (reutilizada)
                                Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                                    LinearProgressIndicator(color = Color.Red, modifier = Modifier.fillMaxWidth())
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (user == null) {
                                            android.widget.Toast.makeText(context, "Primero cree la cuenta", android.widget.Toast.LENGTH_SHORT).show()
                                        } else onValidateLicense()
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorIne.copy(alpha = 0.2f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBox, null, tint = colorIne, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("AUDITORÍA DE INE", fontWeight = FontWeight.Black, fontSize = 12.sp, color = colorIne)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Estado: $ineEstado", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            
                            if (user?.ineVencimiento != null && user.ineVencimiento > 0) {
                                Text("Vencimiento: ${sdf.format(Date(user.ineVencimiento))}", fontSize = 13.sp, color = Color.DarkGray)
                            }

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    if (user == null) {
                                        android.widget.Toast.makeText(context, "Primero cree la cuenta", android.widget.Toast.LENGTH_SHORT).show()
                                    } else onValidateINE()
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

                    Spacer(Modifier.height(16.dp))

                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { 
                        Text("Cuenta Activa", fontWeight = FontWeight.Bold)
                        Switch(
                            checked = activo, 
                            onCheckedChange = { activo = it }, 
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color.Red,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.LightGray,
                                checkedBorderColor = Color.Red,
                                uncheckedBorderColor = Color.LightGray
                            )
                        ) 
                    }
                    
                    if (uiState.isLoading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = Color.Red)
                    else Button(
                        onClick = { onSave(nombre, email, puesto, activo, user?.licenciaConducir ?: "", user?.credencialElector ?: "") }, 
                        modifier = Modifier.fillMaxWidth().height(54.dp), 
                        shape = RoundedCornerShape(16.dp), 
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { 
                        Icon(if (uiState.isNewUserMode) Icons.Default.PersonAdd else Icons.Default.Save, null); 
                        Spacer(Modifier.width(8.dp)); 
                        Text(if (uiState.isNewUserMode) "CREAR CUENTA" else "ACTUALIZAR DATOS", fontWeight = FontWeight.ExtraBold) 
                    }
                }
            }
        }
    }
    if (showDelDialog) { AlertDialog(onDismissRequest = { showDelDialog = false }, title = { Text("¿Eliminar?") }, text = { Text("Borrar a ${uiState.usuarioSeleccionado?.nombre}?") }, confirmButton = { Button(onClick = { uiState.usuarioSeleccionado?.uid?.let { onDelete(it) }; showDelDialog = false }, colors = ButtonDefaults.buttonColors(RojoDelisa)) { Text("ELIMINAR") } }, dismissButton = { TextButton(onClick = { showDelDialog = false }) { Text("CANCELAR") } }) }
    if (showImgDialog) { AlertDialog(onDismissRequest = { showImgDialog = false }, title = { Text("Foto") }, confirmButton = { Row { TextButton(onClick = { onImageSourceSelected(true); showImgDialog = false }) { Text("CÁMARA", color = Color.Red) }; TextButton(onClick = { onImageSourceSelected(false); showImgDialog = false }) { Text("GALERÍA", color = Color.Red) } } }) }
    
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
                    Text("Evidencia oficial de licencia de conducir.", fontSize = 12.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            },
            confirmButton = { Button(onClick = { showLicensePhotoDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("CERRAR") } }
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
                    Text("Evidencia oficial de identificación INE/IFE.", fontSize = 12.sp, color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            },
            confirmButton = { Button(onClick = { showINEPhotoDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("CERRAR") } }
        )
    }
}

@Composable
fun UserSelectorCard(nombre: String, subtitulo: String, photoUrl: String? = null, icon: ImageVector? = null, isSelected: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.size(width = 140.dp, height = 120.dp).clickable { onClick() }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) Color.White else Color(0xFFF1F2F6)), border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color.Red) else null, elevation = CardDefaults.cardElevation(if (isSelected) 8.dp else 2.dp)) {
        Column(Modifier.fillMaxSize().padding(12.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Box(Modifier.size(45.dp).clip(CircleShape).background(if (isSelected) Color.Red.copy(0.1f) else Color.White), Alignment.Center) {
                if (!photoUrl.isNullOrEmpty()) AsyncImage(model = photoUrl, null, contentScale = ContentScale.Crop)
                else Icon(icon ?: Icons.Default.Person, null, tint = if (isSelected) Color.Red else Color.Gray, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(8.dp)); Text(nombre, fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (isSelected) Color.Red else Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitulo, fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun FormTextField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, leadingIcon = { Icon(icon, null, tint = Color.Red) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Red, focusedLabelColor = Color.Red, cursorColor = Color.Red), singleLine = true)
}

private val RojoDelisa = Color(0xFFE53935)
fun createImageFile3(context: android.content.Context): File { val dir = File(context.filesDir, "usuarios"); if (!dir.exists()) dir.mkdirs(); return File(dir, "u_${System.currentTimeMillis()}.jpg") }

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun UsuariosAdminPreview() {
    val users = listOf(UsuarioEntity("1", "Lizeth Flores", "Gerente", "Si", ""), UsuarioEntity("2", "Juan Perez", "Vendedor", "Si", ""))
    DeliveryTheme { PantallaUsuariosAdminContent(UsuariosAdminUiState(usuarios = users), null, {}, {}, {}, {}, {}, {_,_,_,_,_,_ ->}, {}) }
}
