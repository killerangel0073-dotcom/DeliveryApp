package com.gruposanangel.delivery.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.auth.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun PantallaLoginPro(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var buttonPressed by remember { mutableStateOf(false) }

    // Animación de botón
    val buttonScale by animateFloatAsState(
        targetValue = if (buttonPressed) 0.95f else 1f,
        label = "buttonScale"
    )

    // Animación de shake para error (menos vibración)
    val offsetX by animateFloatAsState(
        targetValue = if (errorMessage != null) 5f else 0f,
        animationSpec = if (errorMessage != null) repeatable(
            iterations = 2,
            animation = tween(50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ) else tween(0)
    )

    // Animación de fade-in de la pantalla
    var screenAlpha by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        screenAlpha = 1f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .alpha(screenAlpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Título
            Text(
                text = "🚚 Grupo San Angel Delivery",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
                color = Color(0xFFFF0000)
            )

            val enterAnimation = remember {
                fadeIn(animationSpec = tween(500)) +
                        slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(500))
            }

            AnimatedVisibility(visible = true, enter = enterAnimation) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo del Usuario") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFFFF0000),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFFFF0000),
                        cursorColor = Color(0xFFFF0000)
                    )
                )
            }

            AnimatedVisibility(visible = true, enter = enterAnimation) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            Icon(icon, contentDescription = null)
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFFFF0000),
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFFFF0000),
                        cursorColor = Color(0xFFFF0000)
                    )
                )
            }

            // Mensaje de error
            errorMessage?.let {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = it,
                        color = Color.Red,
                        fontSize = 14.sp
                    )
                }
            }

            // Botón animado y shake al error
            AnimatedVisibility(visible = true, enter = enterAnimation) {
                Button(
                    onClick = {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            buttonPressed = true
                            loading = true
                            errorMessage = null
                            FirebaseAuth.getInstance()
                                .signInWithEmailAndPassword(email.trim(), password.trim())
                                .addOnCompleteListener { task ->
                                    loading = false
                                    buttonPressed = false
                                    if (task.isSuccessful) {
                                        onLoginSuccess()
                                    } else {
                                        val exception = task.exception
                                        errorMessage = when (exception) {
                                            is FirebaseAuthInvalidUserException,
                                            is FirebaseAuthInvalidCredentialsException -> "Verifique el usuario o contraseña incorrecta"
                                            else -> "Error al iniciar sesión"
                                        }
                                    }
                                }
                        } else {
                            errorMessage = "Por favor, llena todos los campos"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .scale(buttonScale)
                        .offset(x = offsetX.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                    enabled = !loading
                ) {
                    Text("Iniciar sesión", color = Color.White, fontSize = 16.sp)
                }
            }
        }

        // Círculo de carga simple (sin overlay)
        if (loading) {
            CircularProgressIndicator(
                color = Color(0xFFB71C1C),
                strokeWidth = 2.dp,
                modifier = Modifier.size(50.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PantallaLoginProPreview() {
    PantallaLoginPro(onLoginSuccess = {})
}
