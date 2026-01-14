package com.example.compose
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AppTypography
import com.madovygabor.maturimate.ui.theme.backgroundDark
import com.madovygabor.maturimate.ui.theme.backgroundDarkHighContrast
import com.madovygabor.maturimate.ui.theme.backgroundDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.backgroundLight
import com.madovygabor.maturimate.ui.theme.backgroundLightHighContrast
import com.madovygabor.maturimate.ui.theme.backgroundLightMediumContrast
import com.madovygabor.maturimate.ui.theme.errorContainerDark
import com.madovygabor.maturimate.ui.theme.errorContainerDarkHighContrast
import com.madovygabor.maturimate.ui.theme.errorContainerDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.errorContainerLight
import com.madovygabor.maturimate.ui.theme.errorContainerLightHighContrast
import com.madovygabor.maturimate.ui.theme.errorContainerLightMediumContrast
import com.madovygabor.maturimate.ui.theme.errorDark
import com.madovygabor.maturimate.ui.theme.errorDarkHighContrast
import com.madovygabor.maturimate.ui.theme.errorDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.errorLight
import com.madovygabor.maturimate.ui.theme.errorLightHighContrast
import com.madovygabor.maturimate.ui.theme.errorLightMediumContrast
import com.madovygabor.maturimate.ui.theme.inverseOnSurfaceDark
import com.madovygabor.maturimate.ui.theme.inverseOnSurfaceDarkHighContrast
import com.madovygabor.maturimate.ui.theme.inverseOnSurfaceDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.inverseOnSurfaceLight
import com.madovygabor.maturimate.ui.theme.inverseOnSurfaceLightHighContrast
import com.madovygabor.maturimate.ui.theme.inverseOnSurfaceLightMediumContrast
import com.madovygabor.maturimate.ui.theme.inversePrimaryDark
import com.madovygabor.maturimate.ui.theme.inversePrimaryDarkHighContrast
import com.madovygabor.maturimate.ui.theme.inversePrimaryDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.inversePrimaryLight
import com.madovygabor.maturimate.ui.theme.inversePrimaryLightHighContrast
import com.madovygabor.maturimate.ui.theme.inversePrimaryLightMediumContrast
import com.madovygabor.maturimate.ui.theme.inverseSurfaceDark
import com.madovygabor.maturimate.ui.theme.inverseSurfaceDarkHighContrast
import com.madovygabor.maturimate.ui.theme.inverseSurfaceDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.inverseSurfaceLight
import com.madovygabor.maturimate.ui.theme.inverseSurfaceLightHighContrast
import com.madovygabor.maturimate.ui.theme.inverseSurfaceLightMediumContrast
import com.madovygabor.maturimate.ui.theme.onBackgroundDark
import com.madovygabor.maturimate.ui.theme.onBackgroundDarkHighContrast
import com.madovygabor.maturimate.ui.theme.onBackgroundDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.onBackgroundLight
import com.madovygabor.maturimate.ui.theme.onBackgroundLightHighContrast
import com.madovygabor.maturimate.ui.theme.onBackgroundLightMediumContrast
import com.madovygabor.maturimate.ui.theme.onErrorContainerDark
import com.madovygabor.maturimate.ui.theme.onErrorContainerDarkHighContrast
import com.madovygabor.maturimate.ui.theme.onErrorContainerDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.onErrorContainerLight
import com.madovygabor.maturimate.ui.theme.onErrorContainerLightHighContrast
import com.madovygabor.maturimate.ui.theme.onErrorContainerLightMediumContrast
import com.madovygabor.maturimate.ui.theme.onErrorDark
import com.madovygabor.maturimate.ui.theme.onErrorDarkHighContrast
import com.madovygabor.maturimate.ui.theme.onErrorDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.onErrorLight
import com.madovygabor.maturimate.ui.theme.onErrorLightHighContrast
import com.madovygabor.maturimate.ui.theme.onErrorLightMediumContrast
import com.madovygabor.maturimate.ui.theme.onPrimaryContainerDark
import com.madovygabor.maturimate.ui.theme.onPrimaryContainerDarkHighContrast
import com.madovygabor.maturimate.ui.theme.onPrimaryContainerDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.onPrimaryContainerLight
import com.madovygabor.maturimate.ui.theme.onPrimaryContainerLightHighContrast
import com.madovygabor.maturimate.ui.theme.onPrimaryContainerLightMediumContrast
import com.madovygabor.maturimate.ui.theme.onPrimaryDark
import com.madovygabor.maturimate.ui.theme.onPrimaryDarkHighContrast
import com.madovygabor.maturimate.ui.theme.onPrimaryDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.onPrimaryLight
import com.madovygabor.maturimate.ui.theme.onPrimaryLightHighContrast
import com.madovygabor.maturimate.ui.theme.onPrimaryLightMediumContrast
import com.madovygabor.maturimate.ui.theme.onSecondaryContainerDark
import com.madovygabor.maturimate.ui.theme.onSecondaryContainerDarkHighContrast
import com.madovygabor.maturimate.ui.theme.onSecondaryContainerDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.onSecondaryContainerLight
import com.madovygabor.maturimate.ui.theme.onSecondaryContainerLightHighContrast
import com.madovygabor.maturimate.ui.theme.onSecondaryContainerLightMediumContrast
import com.madovygabor.maturimate.ui.theme.onSecondaryDark
import com.madovygabor.maturimate.ui.theme.onSecondaryDarkHighContrast
import com.madovygabor.maturimate.ui.theme.onSecondaryDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.onSecondaryLight
import com.madovygabor.maturimate.ui.theme.onSecondaryLightHighContrast
import com.madovygabor.maturimate.ui.theme.onSecondaryLightMediumContrast
import com.madovygabor.maturimate.ui.theme.onSurfaceDark
import com.madovygabor.maturimate.ui.theme.onSurfaceDarkHighContrast
import com.madovygabor.maturimate.ui.theme.onSurfaceDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.onSurfaceLight
import com.madovygabor.maturimate.ui.theme.onSurfaceLightHighContrast
import com.madovygabor.maturimate.ui.theme.onSurfaceLightMediumContrast
import com.madovygabor.maturimate.ui.theme.onSurfaceVariantDark
import com.madovygabor.maturimate.ui.theme.onSurfaceVariantDarkHighContrast
import com.madovygabor.maturimate.ui.theme.onSurfaceVariantDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.onSurfaceVariantLight
import com.madovygabor.maturimate.ui.theme.onSurfaceVariantLightHighContrast
import com.madovygabor.maturimate.ui.theme.onSurfaceVariantLightMediumContrast
import com.madovygabor.maturimate.ui.theme.onTertiaryContainerDark
import com.madovygabor.maturimate.ui.theme.onTertiaryContainerDarkHighContrast
import com.madovygabor.maturimate.ui.theme.onTertiaryContainerDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.onTertiaryContainerLight
import com.madovygabor.maturimate.ui.theme.onTertiaryContainerLightHighContrast
import com.madovygabor.maturimate.ui.theme.onTertiaryContainerLightMediumContrast
import com.madovygabor.maturimate.ui.theme.onTertiaryDark
import com.madovygabor.maturimate.ui.theme.onTertiaryDarkHighContrast
import com.madovygabor.maturimate.ui.theme.onTertiaryDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.onTertiaryLight
import com.madovygabor.maturimate.ui.theme.onTertiaryLightHighContrast
import com.madovygabor.maturimate.ui.theme.onTertiaryLightMediumContrast
import com.madovygabor.maturimate.ui.theme.outlineDark
import com.madovygabor.maturimate.ui.theme.outlineDarkHighContrast
import com.madovygabor.maturimate.ui.theme.outlineDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.outlineLight
import com.madovygabor.maturimate.ui.theme.outlineLightHighContrast
import com.madovygabor.maturimate.ui.theme.outlineLightMediumContrast
import com.madovygabor.maturimate.ui.theme.outlineVariantDark
import com.madovygabor.maturimate.ui.theme.outlineVariantDarkHighContrast
import com.madovygabor.maturimate.ui.theme.outlineVariantDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.outlineVariantLight
import com.madovygabor.maturimate.ui.theme.outlineVariantLightHighContrast
import com.madovygabor.maturimate.ui.theme.outlineVariantLightMediumContrast
import com.madovygabor.maturimate.ui.theme.primaryContainerDark
import com.madovygabor.maturimate.ui.theme.primaryContainerDarkHighContrast
import com.madovygabor.maturimate.ui.theme.primaryContainerDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.primaryContainerLight
import com.madovygabor.maturimate.ui.theme.primaryContainerLightHighContrast
import com.madovygabor.maturimate.ui.theme.primaryContainerLightMediumContrast
import com.madovygabor.maturimate.ui.theme.primaryDark
import com.madovygabor.maturimate.ui.theme.primaryDarkHighContrast
import com.madovygabor.maturimate.ui.theme.primaryDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.primaryLight
import com.madovygabor.maturimate.ui.theme.primaryLightHighContrast
import com.madovygabor.maturimate.ui.theme.primaryLightMediumContrast
import com.madovygabor.maturimate.ui.theme.scrimDark
import com.madovygabor.maturimate.ui.theme.scrimDarkHighContrast
import com.madovygabor.maturimate.ui.theme.scrimDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.scrimLight
import com.madovygabor.maturimate.ui.theme.scrimLightHighContrast
import com.madovygabor.maturimate.ui.theme.scrimLightMediumContrast
import com.madovygabor.maturimate.ui.theme.secondaryContainerDark
import com.madovygabor.maturimate.ui.theme.secondaryContainerDarkHighContrast
import com.madovygabor.maturimate.ui.theme.secondaryContainerDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.secondaryContainerLight
import com.madovygabor.maturimate.ui.theme.secondaryContainerLightHighContrast
import com.madovygabor.maturimate.ui.theme.secondaryContainerLightMediumContrast
import com.madovygabor.maturimate.ui.theme.secondaryDark
import com.madovygabor.maturimate.ui.theme.secondaryDarkHighContrast
import com.madovygabor.maturimate.ui.theme.secondaryDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.secondaryLight
import com.madovygabor.maturimate.ui.theme.secondaryLightHighContrast
import com.madovygabor.maturimate.ui.theme.secondaryLightMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceBrightDark
import com.madovygabor.maturimate.ui.theme.surfaceBrightDarkHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceBrightDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceBrightLight
import com.madovygabor.maturimate.ui.theme.surfaceBrightLightHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceBrightLightMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerDark
import com.madovygabor.maturimate.ui.theme.surfaceContainerDarkHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerHighDark
import com.madovygabor.maturimate.ui.theme.surfaceContainerHighDarkHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerHighDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerHighLight
import com.madovygabor.maturimate.ui.theme.surfaceContainerHighLightHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerHighLightMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerHighestDark
import com.madovygabor.maturimate.ui.theme.surfaceContainerHighestDarkHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerHighestDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerHighestLight
import com.madovygabor.maturimate.ui.theme.surfaceContainerHighestLightHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerHighestLightMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerLight
import com.madovygabor.maturimate.ui.theme.surfaceContainerLightHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerLightMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerLowDark
import com.madovygabor.maturimate.ui.theme.surfaceContainerLowDarkHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerLowDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerLowLight
import com.madovygabor.maturimate.ui.theme.surfaceContainerLowLightHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerLowLightMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerLowestDark
import com.madovygabor.maturimate.ui.theme.surfaceContainerLowestDarkHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerLowestDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerLowestLight
import com.madovygabor.maturimate.ui.theme.surfaceContainerLowestLightHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceContainerLowestLightMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceDark
import com.madovygabor.maturimate.ui.theme.surfaceDarkHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceDimDark
import com.madovygabor.maturimate.ui.theme.surfaceDimDarkHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceDimDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceDimLight
import com.madovygabor.maturimate.ui.theme.surfaceDimLightHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceDimLightMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceLight
import com.madovygabor.maturimate.ui.theme.surfaceLightHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceLightMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceVariantDark
import com.madovygabor.maturimate.ui.theme.surfaceVariantDarkHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceVariantDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.surfaceVariantLight
import com.madovygabor.maturimate.ui.theme.surfaceVariantLightHighContrast
import com.madovygabor.maturimate.ui.theme.surfaceVariantLightMediumContrast
import com.madovygabor.maturimate.ui.theme.tertiaryContainerDark
import com.madovygabor.maturimate.ui.theme.tertiaryContainerDarkHighContrast
import com.madovygabor.maturimate.ui.theme.tertiaryContainerDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.tertiaryContainerLight
import com.madovygabor.maturimate.ui.theme.tertiaryContainerLightHighContrast
import com.madovygabor.maturimate.ui.theme.tertiaryContainerLightMediumContrast
import com.madovygabor.maturimate.ui.theme.tertiaryDark
import com.madovygabor.maturimate.ui.theme.tertiaryDarkHighContrast
import com.madovygabor.maturimate.ui.theme.tertiaryDarkMediumContrast
import com.madovygabor.maturimate.ui.theme.tertiaryLight
import com.madovygabor.maturimate.ui.theme.tertiaryLightHighContrast
import com.madovygabor.maturimate.ui.theme.tertiaryLightMediumContrast

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

