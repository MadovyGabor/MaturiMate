package com.madovygabor.maturimate.core.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.literata_regular
import maturimate.composeapp.generated.resources.literata_semibold
import maturimate.composeapp.generated.resources.manrope_bold
import maturimate.composeapp.generated.resources.manrope_regular
import maturimate.composeapp.generated.resources.manrope_semibold
import org.jetbrains.compose.resources.Font

// Values from docs/design-system/tokens.json (type). Change them there first.

/** Literata: headings and reading. */
@Composable
fun serifFontFamily() = FontFamily(
    Font(Res.font.literata_regular, FontWeight.Normal),
    Font(Res.font.literata_semibold, FontWeight.SemiBold),
)

/** Manrope: the interface. */
@Composable
fun sansFontFamily() = FontFamily(
    Font(Res.font.manrope_regular, FontWeight.Normal),
    Font(Res.font.manrope_semibold, FontWeight.SemiBold),
    Font(Res.font.manrope_bold, FontWeight.Bold),
)

private fun style(family: FontFamily, size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: Double = 0.0) =
    TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        letterSpacing = letterSpacing.sp,
    )

/** The Material 3 type scale of the design system. */
fun appTypography(serif: FontFamily, sans: FontFamily) = Typography(
    displayLarge = style(serif, 40, 48, FontWeight.SemiBold),
    displayMedium = style(serif, 34, 42, FontWeight.SemiBold),
    displaySmall = style(serif, 30, 38, FontWeight.SemiBold),
    headlineLarge = style(serif, 32, 40, FontWeight.SemiBold),
    headlineMedium = style(serif, 28, 36, FontWeight.SemiBold),
    headlineSmall = style(serif, 24, 32, FontWeight.SemiBold),
    titleLarge = style(sans, 20, 28, FontWeight.Bold),
    titleMedium = style(sans, 16, 24, FontWeight.Bold),
    titleSmall = style(sans, 14, 20, FontWeight.SemiBold),
    bodyLarge = style(sans, 16, 24, FontWeight.Normal),
    bodyMedium = style(sans, 14, 20, FontWeight.Normal),
    bodySmall = style(sans, 12, 16, FontWeight.Normal),
    labelLarge = style(sans, 14, 20, FontWeight.SemiBold),
    labelMedium = style(sans, 13, 18, FontWeight.SemiBold),
    labelSmall = style(sans, 12, 16, FontWeight.SemiBold, letterSpacing = 0.4),
)

/** Design system text styles that have no Material 3 role. */
@Immutable
data class ExtendedTypography(
    /** Lesson text and reading passages. */
    val reading: TextStyle,
)

fun appExtendedTypography(serif: FontFamily) = ExtendedTypography(
    reading = style(serif, 18, 30, FontWeight.Normal),
)

val LocalExtendedTypography = staticCompositionLocalOf { appExtendedTypography(FontFamily.Serif) }

/** The extended text styles of the current theme, next to [MaterialTheme.typography]. */
val MaterialTheme.extendedTypography: ExtendedTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedTypography.current
