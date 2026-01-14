package com.example.compose
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AppTypography
import com.madovygabor.maturimate.core.theme.backgroundDark
import com.madovygabor.maturimate.core.theme.backgroundDarkHighContrast
import com.madovygabor.maturimate.core.theme.backgroundDarkMediumContrast
import com.madovygabor.maturimate.core.theme.backgroundLight
import com.madovygabor.maturimate.core.theme.backgroundLightHighContrast
import com.madovygabor.maturimate.core.theme.backgroundLightMediumContrast
import com.madovygabor.maturimate.core.theme.errorContainerDark
import com.madovygabor.maturimate.core.theme.errorContainerDarkHighContrast
import com.madovygabor.maturimate.core.theme.errorContainerDarkMediumContrast
import com.madovygabor.maturimate.core.theme.errorContainerLight
import com.madovygabor.maturimate.core.theme.errorContainerLightHighContrast
import com.madovygabor.maturimate.core.theme.errorContainerLightMediumContrast
import com.madovygabor.maturimate.core.theme.errorDark
import com.madovygabor.maturimate.core.theme.errorDarkHighContrast
import com.madovygabor.maturimate.core.theme.errorDarkMediumContrast
import com.madovygabor.maturimate.core.theme.errorLight
import com.madovygabor.maturimate.core.theme.errorLightHighContrast
import com.madovygabor.maturimate.core.theme.errorLightMediumContrast
import com.madovygabor.maturimate.core.theme.inverseOnSurfaceDark
import com.madovygabor.maturimate.core.theme.inverseOnSurfaceDarkHighContrast
import com.madovygabor.maturimate.core.theme.inverseOnSurfaceDarkMediumContrast
import com.madovygabor.maturimate.core.theme.inverseOnSurfaceLight
import com.madovygabor.maturimate.core.theme.inverseOnSurfaceLightHighContrast
import com.madovygabor.maturimate.core.theme.inverseOnSurfaceLightMediumContrast
import com.madovygabor.maturimate.core.theme.inversePrimaryDark
import com.madovygabor.maturimate.core.theme.inversePrimaryDarkHighContrast
import com.madovygabor.maturimate.core.theme.inversePrimaryDarkMediumContrast
import com.madovygabor.maturimate.core.theme.inversePrimaryLight
import com.madovygabor.maturimate.core.theme.inversePrimaryLightHighContrast
import com.madovygabor.maturimate.core.theme.inversePrimaryLightMediumContrast
import com.madovygabor.maturimate.core.theme.inverseSurfaceDark
import com.madovygabor.maturimate.core.theme.inverseSurfaceDarkHighContrast
import com.madovygabor.maturimate.core.theme.inverseSurfaceDarkMediumContrast
import com.madovygabor.maturimate.core.theme.inverseSurfaceLight
import com.madovygabor.maturimate.core.theme.inverseSurfaceLightHighContrast
import com.madovygabor.maturimate.core.theme.inverseSurfaceLightMediumContrast
import com.madovygabor.maturimate.core.theme.onBackgroundDark
import com.madovygabor.maturimate.core.theme.onBackgroundDarkHighContrast
import com.madovygabor.maturimate.core.theme.onBackgroundDarkMediumContrast
import com.madovygabor.maturimate.core.theme.onBackgroundLight
import com.madovygabor.maturimate.core.theme.onBackgroundLightHighContrast
import com.madovygabor.maturimate.core.theme.onBackgroundLightMediumContrast
import com.madovygabor.maturimate.core.theme.onErrorContainerDark
import com.madovygabor.maturimate.core.theme.onErrorContainerDarkHighContrast
import com.madovygabor.maturimate.core.theme.onErrorContainerDarkMediumContrast
import com.madovygabor.maturimate.core.theme.onErrorContainerLight
import com.madovygabor.maturimate.core.theme.onErrorContainerLightHighContrast
import com.madovygabor.maturimate.core.theme.onErrorContainerLightMediumContrast
import com.madovygabor.maturimate.core.theme.onErrorDark
import com.madovygabor.maturimate.core.theme.onErrorDarkHighContrast
import com.madovygabor.maturimate.core.theme.onErrorDarkMediumContrast
import com.madovygabor.maturimate.core.theme.onErrorLight
import com.madovygabor.maturimate.core.theme.onErrorLightHighContrast
import com.madovygabor.maturimate.core.theme.onErrorLightMediumContrast
import com.madovygabor.maturimate.core.theme.onPrimaryContainerDark
import com.madovygabor.maturimate.core.theme.onPrimaryContainerDarkHighContrast
import com.madovygabor.maturimate.core.theme.onPrimaryContainerDarkMediumContrast
import com.madovygabor.maturimate.core.theme.onPrimaryContainerLight
import com.madovygabor.maturimate.core.theme.onPrimaryContainerLightHighContrast
import com.madovygabor.maturimate.core.theme.onPrimaryContainerLightMediumContrast
import com.madovygabor.maturimate.core.theme.onPrimaryDark
import com.madovygabor.maturimate.core.theme.onPrimaryDarkHighContrast
import com.madovygabor.maturimate.core.theme.onPrimaryDarkMediumContrast
import com.madovygabor.maturimate.core.theme.onPrimaryLight
import com.madovygabor.maturimate.core.theme.onPrimaryLightHighContrast
import com.madovygabor.maturimate.core.theme.onPrimaryLightMediumContrast
import com.madovygabor.maturimate.core.theme.onSecondaryContainerDark
import com.madovygabor.maturimate.core.theme.onSecondaryContainerDarkHighContrast
import com.madovygabor.maturimate.core.theme.onSecondaryContainerDarkMediumContrast
import com.madovygabor.maturimate.core.theme.onSecondaryContainerLight
import com.madovygabor.maturimate.core.theme.onSecondaryContainerLightHighContrast
import com.madovygabor.maturimate.core.theme.onSecondaryContainerLightMediumContrast
import com.madovygabor.maturimate.core.theme.onSecondaryDark
import com.madovygabor.maturimate.core.theme.onSecondaryDarkHighContrast
import com.madovygabor.maturimate.core.theme.onSecondaryDarkMediumContrast
import com.madovygabor.maturimate.core.theme.onSecondaryLight
import com.madovygabor.maturimate.core.theme.onSecondaryLightHighContrast
import com.madovygabor.maturimate.core.theme.onSecondaryLightMediumContrast
import com.madovygabor.maturimate.core.theme.onSurfaceDark
import com.madovygabor.maturimate.core.theme.onSurfaceDarkHighContrast
import com.madovygabor.maturimate.core.theme.onSurfaceDarkMediumContrast
import com.madovygabor.maturimate.core.theme.onSurfaceLight
import com.madovygabor.maturimate.core.theme.onSurfaceLightHighContrast
import com.madovygabor.maturimate.core.theme.onSurfaceLightMediumContrast
import com.madovygabor.maturimate.core.theme.onSurfaceVariantDark
import com.madovygabor.maturimate.core.theme.onSurfaceVariantDarkHighContrast
import com.madovygabor.maturimate.core.theme.onSurfaceVariantDarkMediumContrast
import com.madovygabor.maturimate.core.theme.onSurfaceVariantLight
import com.madovygabor.maturimate.core.theme.onSurfaceVariantLightHighContrast
import com.madovygabor.maturimate.core.theme.onSurfaceVariantLightMediumContrast
import com.madovygabor.maturimate.core.theme.onTertiaryContainerDark
import com.madovygabor.maturimate.core.theme.onTertiaryContainerDarkHighContrast
import com.madovygabor.maturimate.core.theme.onTertiaryContainerDarkMediumContrast
import com.madovygabor.maturimate.core.theme.onTertiaryContainerLight
import com.madovygabor.maturimate.core.theme.onTertiaryContainerLightHighContrast
import com.madovygabor.maturimate.core.theme.onTertiaryContainerLightMediumContrast
import com.madovygabor.maturimate.core.theme.onTertiaryDark
import com.madovygabor.maturimate.core.theme.onTertiaryDarkHighContrast
import com.madovygabor.maturimate.core.theme.onTertiaryDarkMediumContrast
import com.madovygabor.maturimate.core.theme.onTertiaryLight
import com.madovygabor.maturimate.core.theme.onTertiaryLightHighContrast
import com.madovygabor.maturimate.core.theme.onTertiaryLightMediumContrast
import com.madovygabor.maturimate.core.theme.outlineDark
import com.madovygabor.maturimate.core.theme.outlineDarkHighContrast
import com.madovygabor.maturimate.core.theme.outlineDarkMediumContrast
import com.madovygabor.maturimate.core.theme.outlineLight
import com.madovygabor.maturimate.core.theme.outlineLightHighContrast
import com.madovygabor.maturimate.core.theme.outlineLightMediumContrast
import com.madovygabor.maturimate.core.theme.outlineVariantDark
import com.madovygabor.maturimate.core.theme.outlineVariantDarkHighContrast
import com.madovygabor.maturimate.core.theme.outlineVariantDarkMediumContrast
import com.madovygabor.maturimate.core.theme.outlineVariantLight
import com.madovygabor.maturimate.core.theme.outlineVariantLightHighContrast
import com.madovygabor.maturimate.core.theme.outlineVariantLightMediumContrast
import com.madovygabor.maturimate.core.theme.primaryContainerDark
import com.madovygabor.maturimate.core.theme.primaryContainerDarkHighContrast
import com.madovygabor.maturimate.core.theme.primaryContainerDarkMediumContrast
import com.madovygabor.maturimate.core.theme.primaryContainerLight
import com.madovygabor.maturimate.core.theme.primaryContainerLightHighContrast
import com.madovygabor.maturimate.core.theme.primaryContainerLightMediumContrast
import com.madovygabor.maturimate.core.theme.primaryDark
import com.madovygabor.maturimate.core.theme.primaryDarkHighContrast
import com.madovygabor.maturimate.core.theme.primaryDarkMediumContrast
import com.madovygabor.maturimate.core.theme.primaryLight
import com.madovygabor.maturimate.core.theme.primaryLightHighContrast
import com.madovygabor.maturimate.core.theme.primaryLightMediumContrast
import com.madovygabor.maturimate.core.theme.scrimDark
import com.madovygabor.maturimate.core.theme.scrimDarkHighContrast
import com.madovygabor.maturimate.core.theme.scrimDarkMediumContrast
import com.madovygabor.maturimate.core.theme.scrimLight
import com.madovygabor.maturimate.core.theme.scrimLightHighContrast
import com.madovygabor.maturimate.core.theme.scrimLightMediumContrast
import com.madovygabor.maturimate.core.theme.secondaryContainerDark
import com.madovygabor.maturimate.core.theme.secondaryContainerDarkHighContrast
import com.madovygabor.maturimate.core.theme.secondaryContainerDarkMediumContrast
import com.madovygabor.maturimate.core.theme.secondaryContainerLight
import com.madovygabor.maturimate.core.theme.secondaryContainerLightHighContrast
import com.madovygabor.maturimate.core.theme.secondaryContainerLightMediumContrast
import com.madovygabor.maturimate.core.theme.secondaryDark
import com.madovygabor.maturimate.core.theme.secondaryDarkHighContrast
import com.madovygabor.maturimate.core.theme.secondaryDarkMediumContrast
import com.madovygabor.maturimate.core.theme.secondaryLight
import com.madovygabor.maturimate.core.theme.secondaryLightHighContrast
import com.madovygabor.maturimate.core.theme.secondaryLightMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceBrightDark
import com.madovygabor.maturimate.core.theme.surfaceBrightDarkHighContrast
import com.madovygabor.maturimate.core.theme.surfaceBrightDarkMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceBrightLight
import com.madovygabor.maturimate.core.theme.surfaceBrightLightHighContrast
import com.madovygabor.maturimate.core.theme.surfaceBrightLightMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerDark
import com.madovygabor.maturimate.core.theme.surfaceContainerDarkHighContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerDarkMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerHighDark
import com.madovygabor.maturimate.core.theme.surfaceContainerHighDarkHighContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerHighDarkMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerHighLight
import com.madovygabor.maturimate.core.theme.surfaceContainerHighLightHighContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerHighLightMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerHighestDark
import com.madovygabor.maturimate.core.theme.surfaceContainerHighestDarkHighContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerHighestDarkMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerHighestLight
import com.madovygabor.maturimate.core.theme.surfaceContainerHighestLightHighContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerHighestLightMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerLight
import com.madovygabor.maturimate.core.theme.surfaceContainerLightHighContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerLightMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerLowDark
import com.madovygabor.maturimate.core.theme.surfaceContainerLowDarkHighContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerLowDarkMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerLowLight
import com.madovygabor.maturimate.core.theme.surfaceContainerLowLightHighContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerLowLightMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerLowestDark
import com.madovygabor.maturimate.core.theme.surfaceContainerLowestDarkHighContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerLowestDarkMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerLowestLight
import com.madovygabor.maturimate.core.theme.surfaceContainerLowestLightHighContrast
import com.madovygabor.maturimate.core.theme.surfaceContainerLowestLightMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceDark
import com.madovygabor.maturimate.core.theme.surfaceDarkHighContrast
import com.madovygabor.maturimate.core.theme.surfaceDarkMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceDimDark
import com.madovygabor.maturimate.core.theme.surfaceDimDarkHighContrast
import com.madovygabor.maturimate.core.theme.surfaceDimDarkMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceDimLight
import com.madovygabor.maturimate.core.theme.surfaceDimLightHighContrast
import com.madovygabor.maturimate.core.theme.surfaceDimLightMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceLight
import com.madovygabor.maturimate.core.theme.surfaceLightHighContrast
import com.madovygabor.maturimate.core.theme.surfaceLightMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceVariantDark
import com.madovygabor.maturimate.core.theme.surfaceVariantDarkHighContrast
import com.madovygabor.maturimate.core.theme.surfaceVariantDarkMediumContrast
import com.madovygabor.maturimate.core.theme.surfaceVariantLight
import com.madovygabor.maturimate.core.theme.surfaceVariantLightHighContrast
import com.madovygabor.maturimate.core.theme.surfaceVariantLightMediumContrast
import com.madovygabor.maturimate.core.theme.tertiaryContainerDark
import com.madovygabor.maturimate.core.theme.tertiaryContainerDarkHighContrast
import com.madovygabor.maturimate.core.theme.tertiaryContainerDarkMediumContrast
import com.madovygabor.maturimate.core.theme.tertiaryContainerLight
import com.madovygabor.maturimate.core.theme.tertiaryContainerLightHighContrast
import com.madovygabor.maturimate.core.theme.tertiaryContainerLightMediumContrast
import com.madovygabor.maturimate.core.theme.tertiaryDark
import com.madovygabor.maturimate.core.theme.tertiaryDarkHighContrast
import com.madovygabor.maturimate.core.theme.tertiaryDarkMediumContrast
import com.madovygabor.maturimate.core.theme.tertiaryLight
import com.madovygabor.maturimate.core.theme.tertiaryLightHighContrast
import com.madovygabor.maturimate.core.theme.tertiaryLightMediumContrast

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

private val mediumContrastLightColorScheme = lightColorScheme(
    primary = primaryLightMediumContrast,
    onPrimary = onPrimaryLightMediumContrast,
    primaryContainer = primaryContainerLightMediumContrast,
    onPrimaryContainer = onPrimaryContainerLightMediumContrast,
    secondary = secondaryLightMediumContrast,
    onSecondary = onSecondaryLightMediumContrast,
    secondaryContainer = secondaryContainerLightMediumContrast,
    onSecondaryContainer = onSecondaryContainerLightMediumContrast,
    tertiary = tertiaryLightMediumContrast,
    onTertiary = onTertiaryLightMediumContrast,
    tertiaryContainer = tertiaryContainerLightMediumContrast,
    onTertiaryContainer = onTertiaryContainerLightMediumContrast,
    error = errorLightMediumContrast,
    onError = onErrorLightMediumContrast,
    errorContainer = errorContainerLightMediumContrast,
    onErrorContainer = onErrorContainerLightMediumContrast,
    background = backgroundLightMediumContrast,
    onBackground = onBackgroundLightMediumContrast,
    surface = surfaceLightMediumContrast,
    onSurface = onSurfaceLightMediumContrast,
    surfaceVariant = surfaceVariantLightMediumContrast,
    onSurfaceVariant = onSurfaceVariantLightMediumContrast,
    outline = outlineLightMediumContrast,
    outlineVariant = outlineVariantLightMediumContrast,
    scrim = scrimLightMediumContrast,
    inverseSurface = inverseSurfaceLightMediumContrast,
    inverseOnSurface = inverseOnSurfaceLightMediumContrast,
    inversePrimary = inversePrimaryLightMediumContrast,
    surfaceDim = surfaceDimLightMediumContrast,
    surfaceBright = surfaceBrightLightMediumContrast,
    surfaceContainerLowest = surfaceContainerLowestLightMediumContrast,
    surfaceContainerLow = surfaceContainerLowLightMediumContrast,
    surfaceContainer = surfaceContainerLightMediumContrast,
    surfaceContainerHigh = surfaceContainerHighLightMediumContrast,
    surfaceContainerHighest = surfaceContainerHighestLightMediumContrast,
)

private val highContrastLightColorScheme = lightColorScheme(
    primary = primaryLightHighContrast,
    onPrimary = onPrimaryLightHighContrast,
    primaryContainer = primaryContainerLightHighContrast,
    onPrimaryContainer = onPrimaryContainerLightHighContrast,
    secondary = secondaryLightHighContrast,
    onSecondary = onSecondaryLightHighContrast,
    secondaryContainer = secondaryContainerLightHighContrast,
    onSecondaryContainer = onSecondaryContainerLightHighContrast,
    tertiary = tertiaryLightHighContrast,
    onTertiary = onTertiaryLightHighContrast,
    tertiaryContainer = tertiaryContainerLightHighContrast,
    onTertiaryContainer = onTertiaryContainerLightHighContrast,
    error = errorLightHighContrast,
    onError = onErrorLightHighContrast,
    errorContainer = errorContainerLightHighContrast,
    onErrorContainer = onErrorContainerLightHighContrast,
    background = backgroundLightHighContrast,
    onBackground = onBackgroundLightHighContrast,
    surface = surfaceLightHighContrast,
    onSurface = onSurfaceLightHighContrast,
    surfaceVariant = surfaceVariantLightHighContrast,
    onSurfaceVariant = onSurfaceVariantLightHighContrast,
    outline = outlineLightHighContrast,
    outlineVariant = outlineVariantLightHighContrast,
    scrim = scrimLightHighContrast,
    inverseSurface = inverseSurfaceLightHighContrast,
    inverseOnSurface = inverseOnSurfaceLightHighContrast,
    inversePrimary = inversePrimaryLightHighContrast,
    surfaceDim = surfaceDimLightHighContrast,
    surfaceBright = surfaceBrightLightHighContrast,
    surfaceContainerLowest = surfaceContainerLowestLightHighContrast,
    surfaceContainerLow = surfaceContainerLowLightHighContrast,
    surfaceContainer = surfaceContainerLightHighContrast,
    surfaceContainerHigh = surfaceContainerHighLightHighContrast,
    surfaceContainerHighest = surfaceContainerHighestLightHighContrast,
)

private val mediumContrastDarkColorScheme = darkColorScheme(
    primary = primaryDarkMediumContrast,
    onPrimary = onPrimaryDarkMediumContrast,
    primaryContainer = primaryContainerDarkMediumContrast,
    onPrimaryContainer = onPrimaryContainerDarkMediumContrast,
    secondary = secondaryDarkMediumContrast,
    onSecondary = onSecondaryDarkMediumContrast,
    secondaryContainer = secondaryContainerDarkMediumContrast,
    onSecondaryContainer = onSecondaryContainerDarkMediumContrast,
    tertiary = tertiaryDarkMediumContrast,
    onTertiary = onTertiaryDarkMediumContrast,
    tertiaryContainer = tertiaryContainerDarkMediumContrast,
    onTertiaryContainer = onTertiaryContainerDarkMediumContrast,
    error = errorDarkMediumContrast,
    onError = onErrorDarkMediumContrast,
    errorContainer = errorContainerDarkMediumContrast,
    onErrorContainer = onErrorContainerDarkMediumContrast,
    background = backgroundDarkMediumContrast,
    onBackground = onBackgroundDarkMediumContrast,
    surface = surfaceDarkMediumContrast,
    onSurface = onSurfaceDarkMediumContrast,
    surfaceVariant = surfaceVariantDarkMediumContrast,
    onSurfaceVariant = onSurfaceVariantDarkMediumContrast,
    outline = outlineDarkMediumContrast,
    outlineVariant = outlineVariantDarkMediumContrast,
    scrim = scrimDarkMediumContrast,
    inverseSurface = inverseSurfaceDarkMediumContrast,
    inverseOnSurface = inverseOnSurfaceDarkMediumContrast,
    inversePrimary = inversePrimaryDarkMediumContrast,
    surfaceDim = surfaceDimDarkMediumContrast,
    surfaceBright = surfaceBrightDarkMediumContrast,
    surfaceContainerLowest = surfaceContainerLowestDarkMediumContrast,
    surfaceContainerLow = surfaceContainerLowDarkMediumContrast,
    surfaceContainer = surfaceContainerDarkMediumContrast,
    surfaceContainerHigh = surfaceContainerHighDarkMediumContrast,
    surfaceContainerHighest = surfaceContainerHighestDarkMediumContrast,
)

private val highContrastDarkColorScheme = darkColorScheme(
    primary = primaryDarkHighContrast,
    onPrimary = onPrimaryDarkHighContrast,
    primaryContainer = primaryContainerDarkHighContrast,
    onPrimaryContainer = onPrimaryContainerDarkHighContrast,
    secondary = secondaryDarkHighContrast,
    onSecondary = onSecondaryDarkHighContrast,
    secondaryContainer = secondaryContainerDarkHighContrast,
    onSecondaryContainer = onSecondaryContainerDarkHighContrast,
    tertiary = tertiaryDarkHighContrast,
    onTertiary = onTertiaryDarkHighContrast,
    tertiaryContainer = tertiaryContainerDarkHighContrast,
    onTertiaryContainer = onTertiaryContainerDarkHighContrast,
    error = errorDarkHighContrast,
    onError = onErrorDarkHighContrast,
    errorContainer = errorContainerDarkHighContrast,
    onErrorContainer = onErrorContainerDarkHighContrast,
    background = backgroundDarkHighContrast,
    onBackground = onBackgroundDarkHighContrast,
    surface = surfaceDarkHighContrast,
    onSurface = onSurfaceDarkHighContrast,
    surfaceVariant = surfaceVariantDarkHighContrast,
    onSurfaceVariant = onSurfaceVariantDarkHighContrast,
    outline = outlineDarkHighContrast,
    outlineVariant = outlineVariantDarkHighContrast,
    scrim = scrimDarkHighContrast,
    inverseSurface = inverseSurfaceDarkHighContrast,
    inverseOnSurface = inverseOnSurfaceDarkHighContrast,
    inversePrimary = inversePrimaryDarkHighContrast,
    surfaceDim = surfaceDimDarkHighContrast,
    surfaceBright = surfaceBrightDarkHighContrast,
    surfaceContainerLowest = surfaceContainerLowestDarkHighContrast,
    surfaceContainerLow = surfaceContainerLowDarkHighContrast,
    surfaceContainer = surfaceContainerDarkHighContrast,
    surfaceContainerHigh = surfaceContainerHighDarkHighContrast,
    surfaceContainerHighest = surfaceContainerHighestDarkHighContrast,
)

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color
)

val unspecified_scheme = ColorFamily(
    Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable() () -> Unit
) {
  val colorScheme = when {
      darkTheme -> darkScheme
      else -> lightScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    content = content
  )
}

