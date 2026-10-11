package com.mohdshayan.rainfold.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * The whole radius scale, and the rule for using it:
 *   RadiusSm  chips, fields
 *   RadiusMd  buttons, rows
 *   RadiusLg  sheet tops, the Fold
 * The round Play button is the one documented exception (a full circle).
 */
val RadiusSm = 6.dp
val RadiusMd = 14.dp
val RadiusLg = 28.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(RadiusSm),
    small = RoundedCornerShape(RadiusSm),
    medium = RoundedCornerShape(RadiusMd),
    large = RoundedCornerShape(RadiusLg),
    extraLarge = RoundedCornerShape(RadiusLg),
)
