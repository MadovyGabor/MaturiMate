package com.madovygabor.maturimate.core.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design system colors that have no Material 3 role.
 * Values from docs/design-system/tokens.json. Change them there first.
 */
@Immutable
data class ExtendedColors(
    val brandGold: Color,
    val onBrandGold: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val focusRing: Color,
    /** The Google sign-in button, as the Google branding guidelines give it. */
    val googleButtonContainer: Color,
    val googleButtonContent: Color,
    val googleButtonOutline: Color,
    /** The four colors of the Google "G" mark. */
    val googleBlue: Color,
    val googleGreen: Color,
    val googleYellow: Color,
    val googleRed: Color,
)

val extendedColorsLight = ExtendedColors(
    brandGold = Color(0xFFF2B630),
    onBrandGold = Color(0xFF2B1F00),
    success = Color(0xFF2F6B3A),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFCDEBC9),
    onSuccessContainer = Color(0xFF0F2A14),
    focusRing = Color(0xFF7B5800),
    googleButtonContainer = Color(0xFFFFFFFF),
    googleButtonContent = Color(0xFF1F1F1F),
    googleButtonOutline = Color(0xFF747775),
    googleBlue = Color(0xFF4285F4),
    googleGreen = Color(0xFF34A853),
    googleYellow = Color(0xFFFBBC05),
    googleRed = Color(0xFFEA4335),
)

val extendedColorsDark = ExtendedColors(
    brandGold = Color(0xFFF2B630),
    onBrandGold = Color(0xFF2B1F00),
    success = Color(0xFF9FD49A),
    onSuccess = Color(0xFF0A3914),
    successContainer = Color(0xFF1F5128),
    onSuccessContainer = Color(0xFFCDEBC9),
    focusRing = Color(0xFFF4C04E),
    googleButtonContainer = Color(0xFF131314),
    googleButtonContent = Color(0xFFE3E3E3),
    googleButtonOutline = Color(0xFF8E918F),
    googleBlue = Color(0xFF4285F4),
    googleGreen = Color(0xFF34A853),
    googleYellow = Color(0xFFFBBC05),
    googleRed = Color(0xFFEA4335),
)

val LocalExtendedColors = staticCompositionLocalOf { extendedColorsLight }

/** The extended colors of the current theme, next to [MaterialTheme.colorScheme]. */
val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
