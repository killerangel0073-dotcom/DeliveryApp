package com.gruposanangel.delivery.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- GESTIÓN DE TEMA GLOBAL ---
object ThemeConfig {
    var isDarkTheme = mutableStateOf<Boolean?>(null) // null = automático (sensor o sistema)
    var isSensorDark = mutableStateOf(false) // Estado controlado por el sensor de luz

    fun loadTheme(context: Context) {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        if (prefs.contains("is_dark")) {
            isDarkTheme.value = prefs.getBoolean("is_dark", false)
        }
    }

    fun saveTheme(context: Context, dark: Boolean?) {
        isDarkTheme.value = dark
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        if (dark == null) {
            prefs.edit().remove("is_dark").apply()
        } else {
            prefs.edit().putBoolean("is_dark", dark!!).apply()
        }
    }
}

val DarkColorScheme = darkColorScheme(
    primary = DelisaRed,
    onPrimary = Color.White,
    primaryContainer = DelisaRedDark,
    onPrimaryContainer = Color.White,
    
    secondary = DelisaBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF003366), // Azul muy oscuro para contraste
    
    tertiary = DelisaGreen,
    onTertiary = Color.White,
    
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkVariant,
    onSurfaceVariant = DarkOnBackground,
    
    error = ErrorRed,
    onError = Color.White,
    
    // Eliminación de morados residuales de M3
    outline = Color.Gray,
    surfaceTint = Color.Transparent, // Evita el tinte morado en superficies
    inversePrimary = DelisaRedLight
)

val LightColorScheme = lightColorScheme(
    primary = DelisaRed,
    onPrimary = Color.White,
    primaryContainer = DelisaRedLight,
    onPrimaryContainer = DelisaRedDark,
    
    secondary = DelisaBlue,
    onSecondary = Color.White,
    secondaryContainer = DelisaBlueLight,
    
    tertiary = DelisaGreen,
    onTertiary = Color.White,
    
    background = NeutralBackground,
    onBackground = NeutralOnBackground,
    surface = NeutralSurface,
    onSurface = NeutralOnSurface,
    surfaceVariant = NeutralVariant,
    onSurfaceVariant = Color.Gray,
    
    error = ErrorRed,
    onError = Color.White,

    // Eliminación de morados residuales de M3
    outline = Color.LightGray,
    surfaceTint = Color.Transparent, // Evita el tinte morado en superficies
    inversePrimary = DelisaRedLight
)

@Composable
fun DeliveryTheme(
    // Si isDarkTheme es null (modo auto), usamos el sensor. 
    // Si el sensor no ha detectado nada, podemos usar isSystemInDarkTheme() como respaldo.
    darkTheme: Boolean = ThemeConfig.isDarkTheme.value ?: ThemeConfig.isSensorDark.value,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
