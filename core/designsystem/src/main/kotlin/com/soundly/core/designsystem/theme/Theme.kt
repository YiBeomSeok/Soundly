package com.soundly.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LightDefaultColorScheme = lightColorScheme(
    primary = Color(0xFF325ADC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E8FF),
    onPrimaryContainer = Color(0xFF152B72),
    secondary = Color(0xFF42665D),
    secondaryContainer = Color(0xFFD6EEE5),
    onSecondaryContainer = Color(0xFF173B31),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFF6F7FB),
    surfaceVariant = Color(0xFFE7EAF2),
    onSurface = Color(0xFF1A2032),
    onSurfaceVariant = Color(0xFF596174),
    outline = Color(0xFF7D8596),
    outlineVariant = Color(0xFFDCE0EB),
)

val DarkDefaultColorScheme = darkColorScheme(
    primary = Color(0xFFAFC3FF),
    onPrimary = Color(0xFF172F78),
    primaryContainer = Color(0xFF283E79),
    onPrimaryContainer = Color(0xFFE1E8FF),
    secondary = Color(0xFFA7D5C6),
    secondaryContainer = Color(0xFF284D42),
    onSecondaryContainer = Color(0xFFD6EEE5),
    background = Color(0xFF10141F),
    surface = Color(0xFF10141F),
    surfaceVariant = Color(0xFF252C3D),
    onSurface = Color(0xFFE8ECF6),
    onSurfaceVariant = Color(0xFFB4BDCF),
    outline = Color(0xFF8993A8),
    outlineVariant = Color(0xFF343E52),
)

@Composable
fun SoundlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    androidTheme: Boolean = false,
    disableDynamicTheming: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = if (!disableDynamicTheming && !androidTheme && supportsDynamicTheming()) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) DarkDefaultColorScheme else LightDefaultColorScheme

    val view = LocalView.current
    if (!LocalInspectionMode.current) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalBackgroundTheme provides BackgroundTheme(color = colors.background),
        LocalGradientColors provides GradientColors(container = colors.surface),
        LocalTintTheme provides TintTheme(),
    ) {
        MaterialTheme(colorScheme = colors, typography = SoundlyTypography, content = content)
    }
}

@androidx.annotation.ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicTheming(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
