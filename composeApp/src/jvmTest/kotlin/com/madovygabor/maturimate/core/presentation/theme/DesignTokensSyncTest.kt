package com.madovygabor.maturimate.core.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Checks that the theme colors match docs/design-system/tokens.json, the source of the design system.
 * When this fails, a color was changed in only one of the two places.
 */
class DesignTokensSyncTest {

    // Token name to its (light, dark) value in the code.
    private val codeColors: Map<String, Pair<Color, Color>> = mapOf(
        "primary" to (primaryLight to primaryDark),
        "on-primary" to (onPrimaryLight to onPrimaryDark),
        "primary-container" to (primaryContainerLight to primaryContainerDark),
        "on-primary-container" to (onPrimaryContainerLight to onPrimaryContainerDark),
        "secondary" to (secondaryLight to secondaryDark),
        "on-secondary" to (onSecondaryLight to onSecondaryDark),
        "secondary-container" to (secondaryContainerLight to secondaryContainerDark),
        "on-secondary-container" to (onSecondaryContainerLight to onSecondaryContainerDark),
        "tertiary" to (tertiaryLight to tertiaryDark),
        "on-tertiary" to (onTertiaryLight to onTertiaryDark),
        "tertiary-container" to (tertiaryContainerLight to tertiaryContainerDark),
        "on-tertiary-container" to (onTertiaryContainerLight to onTertiaryContainerDark),
        "error" to (errorLight to errorDark),
        "on-error" to (onErrorLight to onErrorDark),
        "error-container" to (errorContainerLight to errorContainerDark),
        "on-error-container" to (onErrorContainerLight to onErrorContainerDark),
        "background" to (backgroundLight to backgroundDark),
        "on-background" to (onBackgroundLight to onBackgroundDark),
        "surface-container-lowest" to (surfaceContainerLowestLight to surfaceContainerLowestDark),
        "surface-container-low" to (surfaceContainerLowLight to surfaceContainerLowDark),
        "surface-container" to (surfaceContainerLight to surfaceContainerDark),
        "surface-container-high" to (surfaceContainerHighLight to surfaceContainerHighDark),
        "surface-container-highest" to (surfaceContainerHighestLight to surfaceContainerHighestDark),
        "surface-dim" to (surfaceDimLight to surfaceDimDark),
        "surface-bright" to (surfaceBrightLight to surfaceBrightDark),
        "scrim" to (scrimLight to scrimDark),
        "surface-variant" to (surfaceVariantLight to surfaceVariantDark),
        "on-surface-variant" to (onSurfaceVariantLight to onSurfaceVariantDark),
        "outline" to (outlineLight to outlineDark),
        "outline-variant" to (outlineVariantLight to outlineVariantDark),
        "inverse-surface" to (inverseSurfaceLight to inverseSurfaceDark),
        "inverse-on-surface" to (inverseOnSurfaceLight to inverseOnSurfaceDark),
        "inverse-primary" to (inversePrimaryLight to inversePrimaryDark),
        "brand-gold" to (extendedColorsLight.brandGold to extendedColorsDark.brandGold),
        "on-brand-gold" to (extendedColorsLight.onBrandGold to extendedColorsDark.onBrandGold),
        "success" to (extendedColorsLight.success to extendedColorsDark.success),
        "on-success" to (extendedColorsLight.onSuccess to extendedColorsDark.onSuccess),
        "success-container" to (extendedColorsLight.successContainer to extendedColorsDark.successContainer),
        "on-success-container" to (extendedColorsLight.onSuccessContainer to extendedColorsDark.onSuccessContainer),
        "focus-ring" to (extendedColorsLight.focusRing to extendedColorsDark.focusRing),
    )

    @Test
    fun everyColorTokenMatchesTheCode() {
        val tokensFile = File("../docs/design-system/tokens.json")
        val tokens = Json.parseToJsonElement(tokensFile.readText())
            .jsonObject.getValue("color").jsonObject.getValue("tokens").jsonArray
            .associate { token ->
                val value = token.jsonObject.getValue("value").jsonObject
                token.jsonObject.getValue("name").jsonPrimitive.content to
                    (value.getValue("light").jsonPrimitive.content to value.getValue("dark").jsonPrimitive.content)
            }

        assertEquals(tokens.keys, codeColors.keys, "Token names in tokens.json and in the code differ")
        for ((name, hex) in tokens) {
            val (light, dark) = codeColors.getValue(name)
            assertEquals(hex.first.lowercase(), light.toHex(), "$name (light)")
            assertEquals(hex.second.lowercase(), dark.toHex(), "$name (dark)")
        }
    }

    private fun Color.toHex(): String = "#%06x".format(toArgb() and 0xFFFFFF)
}
