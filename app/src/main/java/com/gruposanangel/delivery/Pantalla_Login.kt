package com.gruposanangel.delivery

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gruposanangel.delivery.data.AppDatabase
import com.gruposanangel.delivery.data.FirebaseDataSource
import com.gruposanangel.delivery.ui.screens.LoginViewModel
import com.gruposanangel.delivery.ui.screens.LoginViewModelFactory
import kotlinx.coroutines.delay
import com.gruposanangel.delivery.ui.theme.*
import androidx.compose.foundation.isSystemInDarkTheme
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun PantallaLoginPro(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val db = AppDatabase.getDatabase(context)
    val firebaseDataSource = FirebaseDataSource()
    val repoUsuario = RepositoryUsuario(firebaseDataSource, db.usuarioDao())

    val viewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(repoUsuario),
    )
    val uiState by viewModel.uiState.collectAsState()

    val isDark = ThemeConfig.isDarkTheme.value ?: isSystemInDarkTheme()

    DeliveryTheme(darkTheme = isDark) {
        PantallaLoginProContent(
            isLoading = uiState.isLoading,
            errorMessage = uiState.errorMessage,
            onLoginClick = { em, pass -> viewModel.login(em, pass) },
            onLoginSuccess = onLoginSuccess,
            loginSuccessSignal = uiState.loginSuccess,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaLoginProContent(
    isLoading: Boolean,
    errorMessage: String?,
    onLoginClick: (String, String) -> Unit,
    onLoginSuccess: () -> Unit,
    loginSuccessSignal: Boolean,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var showEmail by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }

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

    val enterAnim = fadeIn(tween(400)) + slideInVertically(
        initialOffsetY = { it / 3 },
        animationSpec = tween(400),
    )

    val buttonWidth by animateDpAsState(
        targetValue = if (isLoading) 60.dp else 220.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "width",
    )

    val buttonCorner by animateDpAsState(
        targetValue = if (isLoading) 100.dp else 14.dp,
        animationSpec = tween(300),
        label = "corner",
    )

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Image(
            painter = painterResource(R.drawable.fondo_mapa),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .clickable(enabled = false) {},
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {

                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Logo Delisa",
                    modifier = Modifier
                        .size(160.dp)
                        .padding(bottom = 10.dp),
                    contentScale = ContentScale.Fit,
                )

                Text(
                    text = "DELISA BOTANAS",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )

                Text(
                    text = "SISTEMA DE GESTIÓN LOGÍSTICA",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )

                Spacer(modifier = Modifier.height(10.dp))

                // EMAIL
                AnimatedVisibility(visible = showEmail, enter = enterAnim) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Usuario / Email") },
                        leadingIcon = { Icon(Icons.Default.Email, null, tint = DelisaRed) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                            focusedBorderColor = DelisaRed,
                            unfocusedBorderColor = Color.Transparent,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            cursorColor = DelisaRed,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                        ),
                    )
                }

                // PASSWORD
                AnimatedVisibility(visible = showPassword, enter = enterAnim) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = DelisaRed) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    null,
                                    tint = Color.Gray,
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                            focusedBorderColor = DelisaRed,
                            unfocusedBorderColor = Color.Transparent,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            cursorColor = DelisaRed,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                        ),
                    )
                }

                // ERROR ANIMADO
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) {
                        Text(
                            errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 🚀 BOTÓN MORFEO PREMIUM
                AnimatedVisibility(visible = showButton, enter = enterAnim) {
                    Button(
                        onClick = { onLoginClick(email, password) },
                        modifier = Modifier
                            .height(56.dp)
                            .width(buttonWidth)
                            .shadow(if (isLoading) 0.dp else 10.dp, RoundedCornerShape(buttonCorner), ambientColor = DelisaRed),
                        shape = RoundedCornerShape(buttonCorner),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DelisaRed,
                            disabledContainerColor = DelisaRed.copy(alpha = 0.5f),
                        ),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(28.dp),
                            )
                        } else {
                            Text(
                                "ACCEDER AL SISTEMA", 
                                color = Color.White, 
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
                
                Text(
                    text = "© ${Calendar.getInstance()[Calendar.YEAR]} Delisa Botanas",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Login - Estado Normal")
@Composable
fun PreviewLoginNormal() {
    DeliveryTheme(darkTheme = false) {
        PantallaLoginProContent(
            isLoading = false,
            errorMessage = "Credenciales incorrectas, intente de nuevo.",
            onLoginClick = { _, _ -> },
            onLoginSuccess = {},
            loginSuccessSignal = false,
        )
    }
}
