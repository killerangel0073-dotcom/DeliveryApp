package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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

    LaunchedEffect(uiState.loginSuccess) {
        if (uiState.loginSuccess) {
            delay(200)
            onLoginSuccess()
        }
    }

    // -------------------- ANIMATIONS --------------------
    val enterAnim = fadeIn(tween(400)) + slideInVertically(
        initialOffsetY = { it / 3 },
        animationSpec = tween(400)
    )

    val buttonWidth by animateDpAsState(
        targetValue = if (uiState.isLoading) 52.dp else 200.dp,
        animationSpec = tween(300)
    )

    val buttonCorner by animateDpAsState(
        targetValue = if (uiState.isLoading) 50.dp else 14.dp,
        animationSpec = tween(300)
    )

    // -------------------- UI --------------------
    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            // 🔹 IMAGEN ARRIBA
            Image(
                painter = painterResource(R.drawable.fondo_mapa),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // ocupa casi toda la pantalla
                contentScale = ContentScale.Crop
            )


        }





        // 🔹 Overlay oscuro para contraste
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.0f))
        )

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
                    fontSize = 30.sp
                )

                // 🔹 TEXTO ABAJO
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {

                }
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
                            containerColor = Color(0xFFF5F5F5),
                            focusedBorderColor = Color(0xFFFF0000),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFFFF0000),
                            cursorColor = Color(0xFFFF0000)
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
                            containerColor = Color(0xFFF5F5F5),
                            focusedBorderColor = Color(0xFFFF0000),
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color(0xFFFF0000),
                            cursorColor = Color(0xFFFF0000)
                        )
                    )
                }

                // ERROR ANIMADO
                AnimatedVisibility(
                    visible = uiState.errorMessage != null,
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
                            uiState.errorMessage.orEmpty(),
                            color = Color(0xFFD32F2F),
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // BUTTON MORPH → LOADING
                AnimatedVisibility(visible = showButton, enter = enterAnim) {
                    Button(
                        onClick = { viewModel.login(email, password) },
                        modifier = Modifier
                            .height(70.dp)
                            .width(buttonWidth),
                        shape = RoundedCornerShape(buttonCorner),
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text("Iniciar sesión", color = Color.White, fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewLogin() {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F2))
    ) {

        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {

            Image(
                painter = painterResource(id = R.drawable.logotipo),
                contentDescription = null,
                modifier = Modifier.size(140.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Login")
            }
        }
    }
}
