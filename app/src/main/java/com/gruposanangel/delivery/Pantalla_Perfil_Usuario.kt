package com.gruposanangel.delivery.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gruposanangel.delivery.R
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.gruposanangel.delivery.data.UsuarioDao
import com.gruposanangel.delivery.data.UsuarioEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// ======================== PERFIL DE USUARIO ========================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilDeUsuarioScreen(
    navController: NavController?,
    usuarioDao: UsuarioDao
) {
    val isPreview = LocalInspectionMode.current
    val currentUserUid = if (isPreview) "123" else FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // Estado local del usuario
    var usuario by remember { mutableStateOf<UsuarioEntity?>(null) }

    // Observa cambios desde Room
    if (!isPreview && currentUserUid.isNotEmpty()) {
        LaunchedEffect(currentUserUid) {
            usuarioDao.obtenerPorIdFlow(currentUserUid).collect { u ->
                usuario = u
            }
        }
    }

    // Datos a mostrar en la UI
    val displayName = usuario?.nombre ?: if (isPreview) "Lizeth Vanessa Flores Corona" else "Cargando..."
    val jobTitle = usuario?.puestoTrabajo ?: if (isPreview) "Gerente General" else "Cargando..."
    val licenseNumber = usuario?.licenciaConducir ?: if (isPreview) "Si tiene" else "Cargando..."
    val photoUrl = usuario?.photoUrl ?: ""

    val bossName = "No disponible"
    val routeAssigned = "No asignada"
    val voterId = "No disponible"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Perfil General",
                            modifier = Modifier.align(Alignment.Center),
                            textAlign = TextAlign.Center
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            navController?.navigate("delivery?screen=Inicio") {
                                launchSingleTop = true
                                popUpTo(0) { inclusive = true }
                            }
                        }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                    }
                },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (photoUrl.isEmpty()) {
                Image(
                    painter = painterResource(R.drawable.repartidor),
                    contentDescription = "Foto de perfil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                )
            } else {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Foto de perfil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(displayName, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoField("Nombre Completo", displayName, Icons.Default.Person)
                InfoField("Puesto de Trabajo", jobTitle, Icons.Default.SupervisorAccount)
                InfoField("Jefe Directo", bossName, Icons.Default.SupervisorAccount)
                InfoField("Ruta Asignada", routeAssigned, Icons.Default.Person)
                InfoField("Licencia de Conducir", licenseNumber, Icons.Default.CreditCard)
                InfoField("Credencial de elector", voterId, Icons.Default.CreditCard)
            }
        }
    }
}

// ======================== CAMPO DE INFO ========================
@Composable
fun InfoField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    extraInfo: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 8.dp)
                .border(width = 1.dp, color = Color.Red, shape = MaterialTheme.shapes.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.Red, modifier = Modifier.padding(start = 4.dp, end = 8.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            extraInfo?.let {
                Box(
                    modifier = Modifier
                        .background(Color.LightGray, shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ======================== PREVIEW ========================
@Preview(showBackground = true)
@Composable
fun PerfilDeUsuarioPreview() {
    val navController = rememberNavController()
    PerfilDeUsuarioScreen(navController = navController, usuarioDao = FakeUsuarioDao())
}

// ======================== DAO FALSO PARA PREVIEW ========================
class FakeUsuarioDao : UsuarioDao {
    override suspend fun insertar(vendedor: UsuarioEntity) {}
    override suspend fun obtenerPorId(uid: String): UsuarioEntity? = null
    override suspend fun limpiarTabla() {}
    override fun obtenerPorIdFlow(uid: String): Flow<UsuarioEntity?> =
        flowOf(
            UsuarioEntity(
                uid = "123",
                nombre = "Lizeth Vanessa Flores Corona",
                puestoTrabajo = "Gerente General",
                licenciaConducir = "Si tiene",
                photoUrl = ""
            )
        )
}
