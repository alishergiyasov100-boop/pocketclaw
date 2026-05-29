package com.musornibak.pocketclaw.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode { DARK, LIGHT, SYSTEM }

private val PqDarkColors = darkColorScheme(
    primary = PqPrimary,
    onPrimary = PqOnPrimary,
    primaryContainer = PqPrimaryContainer,
    onPrimaryContainer = PqOnPrimaryContainer,
    secondary = PqSecondary,
    onSecondary = PqOnSecondary,
    secondaryContainer = PqSecondaryContainer,
    onSecondaryContainer = PqOnSecondaryContainer,
    tertiary = PqTertiary,
    onTertiary = PqOnTertiary,
    tertiaryContainer = PqTertiaryContainer,
    onTertiaryContainer = PqOnTertiaryContainer,
    background = PqBackground,
    onBackground = PqOnBackground,
    surface = PqSurface,
    onSurface = PqOnSurface,
    surfaceVariant = PqSurfaceVariant,
    onSurfaceVariant = PqOnSurfaceVariant,
    surfaceContainer = PqSurfaceContainer,
    surfaceContainerHigh = PqSurfaceContainerHigh,
    surfaceContainerHighest = PqSurfaceContainerHighest,
    outline = PqOutline,
    outlineVariant = PqOutlineVariant,
    error = PqError,
    onError = PqOnError,
    errorContainer = PqErrorContainer,
    onErrorContainer = PqOnErrorContainer
)

private val PqLightColors = lightColorScheme(
    primary = PqLightPrimary,
    onPrimary = PqLightOnPrimary,
    primaryContainer = PqLightPrimaryContainer,
    onPrimaryContainer = PqLightOnPrimaryContainer,
    secondary = PqLightSecondary,
    onSecondary = PqLightOnSecondary,
    secondaryContainer = PqLightSecondaryContainer,
    onSecondaryContainer = PqLightOnSecondaryContainer,
    tertiary = PqLightTertiary,
    onTertiary = PqLightOnTertiary,
    tertiaryContainer = PqLightTertiaryContainer,
    onTertiaryContainer = PqLightOnTertiaryContainer,
    background = PqLightBackground,
    onBackground = PqLightOnBackground,
    surface = PqLightSurface,
    onSurface = PqLightOnSurface,
    surfaceVariant = PqLightSurfaceVariant,
    onSurfaceVariant = PqLightOnSurfaceVariant,
    surfaceContainer = PqLightSurfaceContainer,
    surfaceContainerHigh = PqLightSurfaceContainerHigh,
    surfaceContainerHighest = PqLightSurfaceContainerHighest,
    outline = PqLightOutline,
    outlineVariant = PqLightOutlineVariant
)

@Composable
fun PocketClawTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (isDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        isDark -> PqDarkColors
        else -> PqLightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
