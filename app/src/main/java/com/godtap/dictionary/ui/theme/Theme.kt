package com.godtap.dictionary.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// TapDictionary Brand Colors - Clean Blue Theme (exact match to taptranslate-now)
// Primary: #2196F3 (Material Blue)
private val md_theme_light_primary = Color(0xFF2196F3)
private val md_theme_light_onPrimary = Color(0xFFFFFFFF)
private val md_theme_light_primaryContainer = Color(0xFFBBDEFB) // Light blue
private val md_theme_light_onPrimaryContainer = Color(0xFF0D47A1)
// Secondary: Soft Blue Gray
private val md_theme_light_secondary = Color(0xFFECEFF2)
private val md_theme_light_onSecondary = Color(0xFF2A3845)
private val md_theme_light_secondaryContainer = Color(0xFFF5F7F9)
private val md_theme_light_onSecondaryContainer = Color(0xFF1A2733)
// Accent: Warm Orange #FF9800
private val md_theme_light_tertiary = Color(0xFFFF9800)
private val md_theme_light_onTertiary = Color(0xFFFFFFFF)
private val md_theme_light_tertiaryContainer = Color(0xFFFFE0B2) // Light orange
private val md_theme_light_onTertiaryContainer = Color(0xFFE65100)
private val md_theme_light_error = Color(0xFFDC2626)
private val md_theme_light_errorContainer = Color(0xFFFEE2E2)
private val md_theme_light_onError = Color(0xFFFFFFFF)
private val md_theme_light_onErrorContainer = Color(0xFF7F1D1D)
// Background: Clean light
private val md_theme_light_background = Color(0xFFFAFBFC)
private val md_theme_light_onBackground = Color(0xFF151F28)
// Surface: Pure white
private val md_theme_light_surface = Color(0xFFFFFFFF)
private val md_theme_light_onSurface = Color(0xFF151F28)
// Muted: Light background surfaces
private val md_theme_light_surfaceVariant = Color(0xFFF7F8F9)
private val md_theme_light_onSurfaceVariant = Color(0xFF5A6B7A)
// Border
private val md_theme_light_outline = Color(0xFFDAE0E6)
private val md_theme_light_inverseOnSurface = Color(0xFFFAFBFC)
private val md_theme_light_inverseSurface = Color(0xFF1F2937)
private val md_theme_light_inversePrimary = Color(0xFF64B5F6)

// Dark Theme Colors
private val md_theme_dark_primary = Color(0xFF64B5F6) // Light blue
private val md_theme_dark_onPrimary = Color(0xFF0D47A1)
private val md_theme_dark_primaryContainer = Color(0xFF1976D2)
private val md_theme_dark_onPrimaryContainer = Color(0xFFBBDEFB)
private val md_theme_dark_secondary = Color(0xFF3B4858)
private val md_theme_dark_onSecondary = Color(0xFFD7E3F7)
private val md_theme_dark_secondaryContainer = Color(0xFF2A3845)
private val md_theme_dark_onSecondaryContainer = Color(0xFFECEFF2)
private val md_theme_dark_tertiary = Color(0xFFFFB74D) // Light orange
private val md_theme_dark_onTertiary = Color(0xFFE65100)
private val md_theme_dark_tertiaryContainer = Color(0xFFF57C00)
private val md_theme_dark_onTertiaryContainer = Color(0xFFFFE0B2)
private val md_theme_dark_error = Color(0xFFFCA5A5)
private val md_theme_dark_errorContainer = Color(0xFFB91C1C)
private val md_theme_dark_onError = Color(0xFF7F1D1D)
private val md_theme_dark_onErrorContainer = Color(0xFFFEE2E2)
private val md_theme_dark_background = Color(0xFF151F28)
private val md_theme_dark_onBackground = Color(0xFFF0F4F8)
private val md_theme_dark_surface = Color(0xFF1F2937)
private val md_theme_dark_onSurface = Color(0xFFF0F4F8)
private val md_theme_dark_surfaceVariant = Color(0xFF2A3845)
private val md_theme_dark_onSurfaceVariant = Color(0xFFB0BDC9)
private val md_theme_dark_outline = Color(0xFF4A5A6B)
private val md_theme_dark_inverseOnSurface = Color(0xFF151F28)
private val md_theme_dark_inverseSurface = Color(0xFFF0F4F8)
private val md_theme_dark_inversePrimary = Color(0xFF2196F3)

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
)

@Composable
fun GodTapDictionaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
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

