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
import com.google.firebase.auth.*
import kotlinx.coroutines.delay
import com.gruposanangel.delivery.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun PantallaLoginPro(onLoginSuccess: () -> Unit) {

    // -------------------- STATE --------------------
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loginSuccess by remember { mutableStateOf(false) }

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

    LaunchedEffect(loginSuccess) {
        if (loginSuccess) {
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
        targetValue = if (loading) 52.dp else 200.dp,
        animationSpec = tween(300)
    )

    val buttonCorner by animateDpAsState(
        targetValue = if (loading) 50.dp else 14.dp,
        animationSpec = tween(300)
    )

    // -------------------- UI --------------------
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 🔹 Fondo
        Image(
            painter = painterResource(R.drawable.fondologin),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

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

                // BUTTON MORPH → LOADING
                AnimatedVisibility(visible = showButton, enter = enterAnim) {
                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Por favor, llena todos los campos"
                                return@Button
                            }

                            loading = true
                            errorMessage = null

                            FirebaseAuth.getInstance()
                                .signInWithEmailAndPassword(email.trim(), password.trim())
                                .addOnCompleteListener { task ->
                                    loading = false
                                    if (task.isSuccessful) {
                                        loginSuccess = true
                                    } else {
                                        errorMessage = when (task.exception) {
                                            is FirebaseAuthInvalidUserException,
                                            is FirebaseAuthInvalidCredentialsException ->
                                                "Usuario o contraseña incorrectos"
                                            else -> "Error al iniciar sesión"
                                        }
                                    }
                                }
                        },
                        modifier = Modifier
                            .height(52.dp)
                            .width(buttonWidth),
                        shape = RoundedCornerShape(buttonCorner),
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text("Iniciar sesión", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PantallaLoginProPreview() {
    PantallaLoginPro {}
}
