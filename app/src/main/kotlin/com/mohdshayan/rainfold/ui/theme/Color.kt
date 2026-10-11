package com.mohdshayan.rainfold.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Colour tokens from BLUEPRINT.md section 7: rain cyan and storm petrol. Rain Cyan is the one accent.
 * There is no red: an error is Ink plus an icon. Screens read these through MaterialTheme.colorScheme
 * or the named Fold tokens below, never as raw hex.
 */

// Light mode
val LightBackground = Color(0xFFE9F1F2) // Mist
val LightSurface = Color(0xFFF6FAFA) // Pane
val LightOnBackground = Color(0xFF12262C) // Ink
val LightOnSurfaceVariant = Color(0xFF4A6066) // Drizzle
val LightAccent = Color(0xFF1A7486) // Rain Cyan
val LightOnAccent = Color(0xFFF6FAFA) // Pane
val LightOutlineVariant = Color(0xFFC6D5D8) // Rule

// Dark mode
val DarkBackground = Color(0xFF0E1E23) // Mist
val DarkSurface = Color(0xFF16292F) // Pane
val DarkOnBackground = Color(0xFFE1EDEE) // Ink
val DarkOnSurfaceVariant = Color(0xFF93ABB0) // Drizzle
val DarkAccent = Color(0xFF6FC2D3) // Rain Cyan
val DarkOnAccent = Color(0xFF0E1E23) // Mist
val DarkOutlineVariant = Color(0xFF27393F) // Rule
