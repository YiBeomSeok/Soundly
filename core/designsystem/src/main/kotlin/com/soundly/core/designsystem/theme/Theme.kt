package com.soundly.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Light default theme color scheme
 */
@VisibleForTesting
val LightDefaultColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = DarkGreen40,
    onSecondary = Color.White,
    secondaryContainer = DarkGreen90,
    onSecondaryContainer = DarkGreen10,
    tertiary = Teal40,
    onTertiary = Color.White,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Teal10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = DarkGreenGray99,
    onBackground = DarkGreenGray10,
    surface = DarkGreenGray99,
    onSurface = DarkGreenGray10,
    surfaceVariant = GreenGray90,
    onSurfaceVariant = GreenGray30,
    inverseSurface = DarkGreenGray20,
    inverseOnSurface = DarkGreenGray95,
    outline = GreenGray50,
)

/**
 * Dark default theme color scheme
 */
@VisibleForTesting
val DarkDefaultColorScheme = darkColorScheme(
    primary = SoundlyBlue,
    onPrimary = SoundlyWhite,
    primaryContainer = SoundlyBlue,
    onPrimaryContainer = SoundlyWhite,
    secondary = SoundlyGrey2,
    onSecondary = SoundlyWhite,
    secondaryContainer = SoundlyGrey1,
    onSecondaryContainer = SoundlyWhite,
    tertiary = SoundlyBlue,
    onTertiary = SoundlyWhite,
    tertiaryContainer = SoundlyGrey1,
    onTertiaryContainer = SoundlyWhite,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = SoundlyBlack,
    onBackground = SoundlyWhite,
    surface = SoundlyBlack,
    onSurface = SoundlyWhite,
    surfaceVariant = SoundlyGrey1.copy(alpha = 0.5f),
    onSurfaceVariant = SoundlyGrey4,
    inverseSurface = SoundlyWhite,
    inverseOnSurface = SoundlyBlack,
    outline = SoundlyGrey2,
)

@VisibleForTesting
val LightAndroidColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = DarkGreen40,
    onSecondary = Color.White,
    secondaryContainer = DarkGreen90,
    onSecondaryContainer = DarkGreen10,
    tertiary = Teal40,
    onTertiary = Color.White,
    tertiaryContainer = Teal90,
    onTertiaryContainer = Teal10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = DarkGreenGray99,
    onBackground = DarkGreenGray10,
    surface = DarkGreenGray99,
    onSurface = DarkGreenGray10,
    surfaceVariant = GreenGray90,
    onSurfaceVariant = GreenGray30,
    inverseSurface = DarkGreenGray20,
    inverseOnSurface = DarkGreenGray95,
    outline = GreenGray50,
)

/**
 * Dark Android theme color scheme
 */
@VisibleForTesting
val DarkAndroidColorScheme = darkColorScheme(
    primary = SoundlyBlue,
    onPrimary = SoundlyWhite,
    primaryContainer = SoundlyBlue,
    onPrimaryContainer = SoundlyWhite,
    secondary = SoundlyGrey2,
    onSecondary = SoundlyWhite,
    secondaryContainer = SoundlyGrey1,
    onSecondaryContainer = SoundlyWhite,
    tertiary = SoundlyBlue,
    onTertiary = SoundlyWhite,
    tertiaryContainer = SoundlyGrey1,
    onTertiaryContainer = SoundlyWhite,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = SoundlyBlack,
    onBackground = SoundlyWhite,
    surface = SoundlyBlack,
    onSurface = SoundlyWhite,
    surfaceVariant = SoundlyGrey1.copy(alpha = 0.5f),
    onSurfaceVariant = SoundlyGrey4,
    inverseSurface = SoundlyWhite,
    inverseOnSurface = SoundlyBlack,
    outline = SoundlyGrey2,
)

/**
 * Light Android gradient colors
 */
val LightAndroidGradientColors = GradientColors(container = DarkGreenGray95)

/**
 * Dark Android gradient colors
 */
val DarkAndroidGradientColors = GradientColors(container = Color.Black)

/**
 * Light Android background theme
 */
val LightAndroidBackgroundTheme = BackgroundTheme(color = DarkGreenGray95)

/**
 * Dark Android background theme
 */
val DarkAndroidBackgroundTheme = BackgroundTheme(color = Color.Black)

/**
 * Soundly theme.
 *
 * @param darkTheme Whether the theme should use a dark color scheme (follows system by default).
 * @param androidTheme Whether the theme should use the Android theme color scheme instead of the
 *        default theme.
 * @param disableDynamicTheming If `true`, disables the use of dynamic theming, even when it is
 *        supported. This parameter has no effect if [androidTheme] is `true`.
 */
@Composable
fun SoundlyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    androidTheme: Boolean = false,
    disableDynamicTheming: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Color scheme
    val colorScheme = when {
        androidTheme -> if (darkTheme) DarkAndroidColorScheme else LightAndroidColorScheme
        !disableDynamicTheming && supportsDynamicTheming() -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> if (darkTheme) DarkDefaultColorScheme else LightDefaultColorScheme
    }
    // Gradient colors
    val emptyGradientColors = GradientColors(container = colorScheme.surfaceColorAtElevation(2.dp))
    val defaultGradientColors = GradientColors(
        top = colorScheme.inverseOnSurface,
        bottom = colorScheme.primaryContainer,
        container = colorScheme.surface,
    )
    val gradientColors = when {
        androidTheme -> if (darkTheme) DarkAndroidGradientColors else LightAndroidGradientColors
        !disableDynamicTheming && supportsDynamicTheming() -> emptyGradientColors
        else -> defaultGradientColors
    }
    // Background theme
    val defaultBackgroundTheme = BackgroundTheme(
        color = colorScheme.surface,
        tonalElevation = 2.dp,
    )
    val backgroundTheme = when {
        androidTheme -> if (darkTheme) DarkAndroidBackgroundTheme else LightAndroidBackgroundTheme
        else -> defaultBackgroundTheme
    }
    val tintTheme = when {
        androidTheme -> TintTheme()
        !disableDynamicTheming && supportsDynamicTheming() -> TintTheme(colorScheme.primary)
        else -> TintTheme()
    }

    if (!LocalInspectionMode.current) {
        val view = LocalView.current
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
                !darkTheme
        }
    }

    // Composition locals
    CompositionLocalProvider(
        LocalGradientColors provides gradientColors,
        LocalBackgroundTheme provides backgroundTheme,
        LocalTintTheme provides tintTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SoundlyTypography,
            content = content,
        )
    }
}

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicTheming() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
