package com.gruposanangel.delivery.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.R
import com.gruposanangel.delivery.data.UsuarioDao
import com.gruposanangel.delivery.data.UsuarioEntity
import com.gruposanangel.delivery.ui.theme.DeliveryTheme
import com.gruposanangel.delivery.ui.theme.ThemeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun PerfilDeUsuarioScreen(navController: NavController?, usuarioDao: UsuarioDao) {
    val isPreview = LocalInspectionMode.current
    val uid = if (isPreview) "123" else FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var user by remember { mutableStateOf<UsuarioEntity?>(null) }
    if (!isPreview && uid.isNotEmpty()) { LaunchedEffect(uid) { usuarioDao.obtenerPorIdFlow(uid).collect { user = it } } }
    
    PerfilDeUsuarioContent(
        user = user ?: if (isPreview) UsuarioEntity("123", "Lizeth Vanessa", "Gerente", "Si tiene", "") else null,
        onBack = { navController?.popBackStack() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilDeUsuarioContent(user: UsuarioEntity?, onBack: () -> Unit) {
    val context = LocalContext.current
    // Ahora isDarkState refleja lo que el sensor o el sistema deciden si no hay selección manual
    val isDarkState = ThemeConfig.isDarkTheme.value ?: ThemeConfig.isSensorDark.value
    val isManual = ThemeConfig.isDarkTheme.value != null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("MI PERFIL", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, letterSpacing = 1.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.Red) } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background))).padding(vertical = 24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp, modifier = Modifier.size(134.dp)) {
                            Box(Modifier.padding(4.dp)) {
                                AsyncImage(model = user?.photoUrl, placeholder = painterResource(R.drawable.repartidor), error = painterResource(R.drawable.repartidor), contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                            }
                        }
                        Spacer(Modifier.height(16.dp)); Text(user?.nombre ?: "Cargando...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                        Surface(shape = RoundedCornerShape(8.dp), color = if (user?.activo == true) Color(0xFFE8F5E9).copy(alpha = if (isDarkState) 0.2f else 1f) else Color(0xFFFFEBEE).copy(alpha = if (isDarkState) 0.2f else 1f), modifier = Modifier.padding(top = 8.dp)) {
                            Text(if (user?.activo == true) "ACTIVO" else "INACTIVO", color = if (user?.activo == true) (if (isDarkState) Color(0xFF81C784) else Color(0xFF2E7D32)) else (if (isDarkState) Color(0xFFE57373) else Color(0xFFC62828)), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Datos de Operación", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    InfoFieldCard("Puesto", user?.puestoTrabajo ?: "---", Icons.Default.SupervisorAccount)
                    InfoFieldCard("Ruta", user?.ultimaRutaNombre ?: "Sin ruta", Icons.Default.Route)
                    InfoFieldCard("Almacén", user?.ultimoAlmacenNombre ?: "Sin almacén", Icons.Default.Storefront)
                    Text("Identificación", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                    InfoFieldCard("Licencia", user?.licenciaConducir ?: "No disponible", Icons.Default.Badge)
                    InfoFieldCard("Email", user?.email ?: "---", Icons.Default.Email)

                    Text("Personalización", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(12.dp), color = Color.Red.copy(alpha = 0.08f), modifier = Modifier.size(40.dp)) { Icon(if (isDarkState) Icons.Default.DarkMode else Icons.Default.LightMode, null, tint = Color.Red, modifier = Modifier.padding(8.dp)) }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("MODO OSCURO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                                    Text(if (isDarkState) "Activado" else "Desactivado", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Switch(
                                    checked = isDarkState,
                                    onCheckedChange = { ThemeConfig.saveTheme(context, it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Red)
                                )
                            }
                            
                            if (isManual) {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                TextButton(
                                    onClick = { ThemeConfig.saveTheme(context, null) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.BrightnessAuto, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("VOLVER A MODO AUTOMÁTICO (SENSOR)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoFieldCard(label: String, value: String, icon: ImageVector) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = Color.Red.copy(alpha = 0.08f), modifier = Modifier.size(40.dp)) { Icon(icon, null, tint = Color.Red, modifier = Modifier.padding(8.dp)) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PerfilUsuarioPreview() {
    DeliveryTheme { PerfilDeUsuarioContent(UsuarioEntity("1", "Lizeth Vanessa Flores", "Gerente General", "Si tiene", "", activo = true, ultimaRutaNombre = "Ruta Norte", ultimoAlmacenNombre = "Almacén Central"), {}) }
}
