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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.gruposanangel.delivery.R
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerfilDeUsuarioScreen(navController: NavController?) {
    val isPreview = LocalInspectionMode.current

    var displayName by remember { mutableStateOf(if (isPreview) "Lizeth Vanesa Flores Corona" else "Cargando...") }
    var jobTitle by remember { mutableStateOf(if (isPreview) "Supervisor de Ventas" else "") }
    var bossName by remember { mutableStateOf(if (isPreview) "CEO Angel Lara" else "") }
    var routeAssigned by remember { mutableStateOf(if (isPreview) "Ruta 1" else "") }
    var licenseNumber by remember { mutableStateOf(if (isPreview) "x25448216-a25" else "") }
    var voterId by remember { mutableStateOf(if (isPreview) "x25448216-a25" else "") }
    var photoUrl by remember { mutableStateOf("") }

    // Cargar datos desde Firebase
    if (!isPreview) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid
        LaunchedEffect(uid) {
            uid?.let {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .whereEqualTo("uid", it)
                    .get()
                    .addOnSuccessListener { docs ->
                        if (!docs.isEmpty) {
                            val userDoc = docs.documents[0]
                            displayName = userDoc.getString("display_name") ?: ""
                            jobTitle = userDoc.getString("puestoTrabajo") ?: ""
                            bossName = userDoc.getString("jefeDirecto") ?: ""
                            routeAssigned = userDoc.getString("rutaAsignada") ?: ""
                            licenseNumber = userDoc.getString("licenciaConducir") ?: ""
                            voterId = userDoc.getString("credencialElector") ?: ""
                            photoUrl = userDoc.getString("photo_url") ?: ""
                        }
                    }
            }
        }
    }

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
                    IconButton(onClick = { navController?.popBackStack() }) {
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
            // Foto de perfil
            if (isPreview || photoUrl.isEmpty()) {
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

            // Nombre
            Text(displayName, style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.height(24.dp))

            // Campos de información
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

@Preview(showBackground = true)
@Composable
fun PerfilDeUsuarioPreview() {
    // Usamos un NavController simulado para preview
    val navController = rememberNavController()
    PerfilDeUsuarioScreen(navController = navController)
}
