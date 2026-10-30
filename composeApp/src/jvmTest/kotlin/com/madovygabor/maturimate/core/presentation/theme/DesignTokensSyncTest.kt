package com.madovygabor.maturimate.core.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Checks that the theme colors and type styles match docs/design-system/tokens.json, the source of the
 * design system. When this fails, a value was changed in only one of the two places.
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
        val tokens = tokens().getValue("color").jsonObject.getValue("tokens").jsonArray
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

    @Test
    fun everyTypeStyleMatchesTheCode() {
        val serif = FontFamily.Serif
        val sans = FontFamily.SansSerif
        val families = mapOf("serif" to serif, "sans" to sans)
        val typography = appTypography(serif, sans)
        val codeStyles: Map<String, TextStyle> = mapOf(
            "display-large" to typography.displayLarge,
            "display-medium" to typography.displayMedium,
            "display-small" to typography.displaySmall,
            "headline-large" to typography.headlineLarge,
            "headline-medium" to typography.headlineMedium,
            "headline-small" to typography.headlineSmall,
            "title-large" to typography.titleLarge,
            "title-medium" to typography.titleMedium,
            "title-small" to typography.titleSmall,
            "body-large" to typography.bodyLarge,
            "body-medium" to typography.bodyMedium,
            "body-small" to typography.bodySmall,
            "label-large" to typography.labelLarge,
            "label-medium" to typography.labelMedium,
            "label-small" to typography.labelSmall,
            "reading" to appExtendedTypography(serif).reading,
        )

        val groups = tokens().getValue("type").jsonObject.getValue("groups").jsonArray
        val tokenStyles = groups.flatMap { group ->
            val groupFamily = group.jsonObject.getValue("family").jsonPrimitive.content
            group.jsonObject.getValue("styles").jsonArray.map { it.jsonObject to groupFamily }
        }.associateBy { (style, _) -> style.getValue("name").jsonPrimitive.content }

        assertEquals(tokenStyles.keys, codeStyles.keys, "Type style names in tokens.json and in the code differ")
        for ((name, entry) in tokenStyles) {
            val (token, groupFamily) = entry
            val code = codeStyles.getValue(name)
            val family = token["family"]?.jsonPrimitive?.content ?: groupFamily
            assertEquals(families.getValue(family), code.fontFamily, "$name font family")
            assertEquals(token.px("fontSize"), code.fontSize.value, "$name font size")
            assertEquals(token.px("lineHeight"), code.lineHeight.value, "$name line height")
            assertEquals(token.getValue("fontWeight").jsonPrimitive.int, code.fontWeight?.weight, "$name font weight")
            assertEquals(token["letterSpacing"]?.let { token.px("letterSpacing") } ?: 0f, code.letterSpacing.value, "$name letter spacing")
        }
    }

    private fun tokens() = Json.parseToJsonElement(File("../docs/design-system/tokens.json").readText()).jsonObject

    private fun JsonObject.px(key: String): Float = getValue(key).jsonPrimitive.content.removeSuffix("px").toFloat()

    private fun Color.toHex(): String = "#%06x".format(toArgb() and 0xFFFFFF)
}
