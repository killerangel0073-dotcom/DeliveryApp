package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gruposanangel.delivery.RepositoryUsuario
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.ui.screens.LoginViewModel
import com.gruposanangel.delivery.ui.screens.LoginViewModelFactory
import kotlinx.coroutines.delay
import com.gruposanangel.delivery.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun PantallaLoginPro(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val firebaseDataSource = FirebaseDataSource()
    val repoUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())

    val viewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(repoUsuario)
    )
    val uiState by viewModel.uiState.collectAsState()

    // Llamamos a la vista pura pasando los datos reales del ViewModel
    PantallaLoginProContent(
        isLoading = uiState.isLoading,
        errorMessage = uiState.errorMessage,
        onLoginClick = { em, pass -> viewModel.login(em, pass) },
        onLoginSuccess = onLoginSuccess,
        loginSuccessSignal = uiState.loginSuccess
    )
}

// 🔹 VISTA PURA (Separa el diseño de la base de datos para que el Preview funcione al 100%)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLoginProContent(
    isLoading: Boolean,
    errorMessage: String?,
    onLoginClick: (String, String) -> Unit,
    onLoginSuccess: () -> Unit,
    loginSuccessSignal: Boolean
) {
    // -------------------- STATE LOCAL (Solo UI) --------------------
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Entrada escalonada
    var showEmail by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }

    // -------------------- EFFECTS --------------------
    LaunchedEffect(Unit) {
        showEmail = true
        delay(150)
        showPassword = true
        delay(150)
        showButton = true
    }

    LaunchedEffect(loginSuccessSignal) {
        if (loginSuccessSignal) {
            delay(200)
            onLoginSuccess()
        }
    }

    // -------------------- ANIMATIONS --------------------
    val enterAnim = fadeIn(tween(400)) + slideInVertically(
        initialOffsetY = { it / 3 },
        animationSpec = tween(400)
    )

    // Animación de ancho del botón: 220dp -> 60dp (Círculo)
    val buttonWidth by animateDpAsState(
        targetValue = if (isLoading) 60.dp else 220.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "width"
    )

    // Animación de redondeo: 14dp -> 100dp (Círculo perfecto)
    val buttonCorner by animateDpAsState(
        targetValue = if (isLoading) 100.dp else 14.dp,
        animationSpec = tween(300),
        label = "corner"
    )

    // -------------------- UI --------------------
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 🔹 FONDO
        Image(
            painter = painterResource(R.drawable.fondo_mapa),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 🔹 Overlay oscuro sutil para legibilidad (Gris/Negro sutil)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        )

        // 🔹 Bloqueo de clics mientras carga (Sin Card invasiva)
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = false) {} 
            )
        }

        // 🔹 Contenido de login
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {

                Image(
                    painter = painterResource(id = R.drawable.logotipo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(180.dp)
                        .padding(bottom = 20.dp),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = "Grupo San Ángel",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // EMAIL
                AnimatedVisibility(visible = showEmail, enter = enterAnim) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Correo del Usuario") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            containerColor = Color.White,
                            focusedBorderColor = Color(0xFFFF0000),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color.White,
                            cursorColor = Color(0xFFFF0000),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )
                }

                // PASSWORD
                AnimatedVisibility(visible = showPassword, enter = enterAnim) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    null
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            containerColor = Color.White,
                            focusedBorderColor = Color(0xFFFF0000),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color.White,
                            cursorColor = Color(0xFFFF0000),
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )
                }

                // ERROR ANIMADO
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFEBEE), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            errorMessage.orEmpty(),
                            color = Color(0xFFD32F2F),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 🚀 BOTÓN DINÁMICO (MORFEO PREMIUM)
                AnimatedVisibility(visible = showButton, enter = enterAnim) {
                    Button(
                        onClick = { onLoginClick(email, password) },
                        modifier = Modifier
                            .height(60.dp) // Un poco más estético
                            .width(buttonWidth),
                        shape = RoundedCornerShape(buttonCorner),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
                        contentPadding = PaddingValues(0.dp) // Evita que el texto mueva el indicator
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            Text(
                                "Iniciar sesión", 
                                color = Color.White, 
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// 🔹 PREVIEWS SEPARADOS PARA VER AMBOS ESTADOS
@Preview(showBackground = true, showSystemUi = true, name = "Login - Estado Normal")
@Composable
fun PreviewLoginNormal() {
    MaterialTheme {
        PantallaLoginProContent(
            isLoading = false,
            errorMessage = null,
            onLoginClick = { _, _ -> },
            onLoginSuccess = {},
            loginSuccessSignal = false
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Login - Estado Cargando")
@Composable
fun PreviewLoginLoading() {
    MaterialTheme {
        PantallaLoginProContent(
            isLoading = true,
            errorMessage = null,
            onLoginClick = { _, _ -> },
            onLoginSuccess = {},
            loginSuccessSignal = false
        )
    }
}
