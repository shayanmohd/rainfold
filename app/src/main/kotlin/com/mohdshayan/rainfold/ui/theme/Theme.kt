package com.mohdshayan.rainfold.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Every Material role is mapped to one of the six blueprint tokens (or a tint of the accent over Pane),
// so no baseline Material colour can leak into a component.
private fun scheme(
    dark: Boolean,
    mist: Color,
    pane: Color,
    ink: Color,
    drizzle: Color,
    cyan: Color,
    onCyan: Color,
    rule: Color,
    inverseCyan: Color,
): ColorScheme {
    val tint = cyan.copy(alpha = if (dark) 0.22f else 0.14f).compositeOver(pane)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = cyan,
        onPrimary = onCyan,
        primaryContainer = tint,
        onPrimaryContainer = ink,
        // Snackbar actions sit on inverseSurface (Ink), so they take the other mode's cyan to stay readable.
        inversePrimary = inverseCyan,
        secondary = cyan,
        onSecondary = onCyan,
        secondaryContainer = tint,
        onSecondaryContainer = ink,
        tertiary = cyan,
        onTertiary = onCyan,
        tertiaryContainer = tint,
        onTertiaryContainer = ink,
        background = mist,
        onBackground = ink,
        surface = mist,
        onSurface = ink,
        surfaceVariant = pane,
        onSurfaceVariant = drizzle,
        surfaceTint = cyan,
        inverseSurface = ink,
        inverseOnSurface = mist,
        error = ink,
        onError = mist,
        errorContainer = pane,
        onErrorContainer = ink,
        outline = drizzle,
        outlineVariant = rule,
        scrim = ink,
        surfaceBright = pane,
        surfaceDim = mist,
        surfaceContainerLowest = mist,
        surfaceContainerLow = pane,
        surfaceContainer = pane,
        surfaceContainerHigh = pane,
        surfaceContainerHighest = pane,
    )
}

private val LightColors = scheme(
    dark = false,
    mist = LightBackground,
    pane = LightSurface,
    ink = LightOnBackground,
    drizzle = LightOnSurfaceVariant,
    cyan = LightAccent,
    onCyan = LightOnAccent,
    rule = LightOutlineVariant,
    inverseCyan = DarkAccent,
)

private val DarkColors = scheme(
    dark = true,
    mist = DarkBackground,
    pane = DarkSurface,
    ink = DarkOnBackground,
    drizzle = DarkOnSurfaceVariant,
    cyan = DarkAccent,
    onCyan = DarkOnAccent,
    rule = DarkOutlineVariant,
    inverseCyan = LightAccent,
)

/**
 * The app theme. Dynamic colour is deliberately off: the accent is part of the
 * app's identity and must look the same on every device. System bar icons follow
 * the mode, and LocalReducedMotion is provided for every animation to consult.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
