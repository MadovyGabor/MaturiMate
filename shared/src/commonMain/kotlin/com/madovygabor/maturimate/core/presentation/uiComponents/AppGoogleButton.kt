package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.pressScale
import com.madovygabor.maturimate.core.presentation.theme.spacing

/**
 * The Google sign-in button (docs/design-system/components/GoogleButton.md): the Google "G" mark and the text on the
 * neutral Google button colors (white with a gray outline in the light theme, near-black in the dark one), as the Google
 * branding guidelines for "Sign in with Google" ask. The look is Google's, so it does not follow the app palette.
 *
 * @param text What the button says, in the app language: "Folytatás Google-lel".
 */
@Composable
fun AppGoogleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val google = MaterialTheme.extendedColors
    val colors = MaterialTheme.colorScheme
    val layout = MaterialTheme.layout
    val spacing = MaterialTheme.spacing
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.pressScale(source).heightIn(min = layout.touchTarget).semantics { role = Role.Button },
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) google.googleButtonContainer else colors.surfaceContainerHighest,
        contentColor = if (enabled) google.googleButtonContent else colors.onSurfaceVariant,
        border = BorderStroke(layout.borderThin, if (enabled) google.googleButtonOutline else colors.outlineVariant),
        interactionSource = source,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3, Alignment.CenterHorizontally),
        ) {
            // the mark is decoration: the text says what the button does
            Image(painter = rememberVectorPainter(rememberGoogleMark()), contentDescription = null, modifier = Modifier.size(layout.icon))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The four-color Google "G" (24 by 24, as published in Google's sign-in assets), in the brand colors of the theme tokens. */
@Composable
private fun rememberGoogleMark(): ImageVector {
    val google = MaterialTheme.extendedColors
    return remember(google) {
        ImageVector.Builder(
            name = "GoogleMark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            mark(BLUE, google.googleBlue)
            mark(GREEN, google.googleGreen)
            mark(YELLOW, google.googleYellow)
            mark(RED, google.googleRed)
        }.build()
    }
}

private fun ImageVector.Builder.mark(path: String, color: Color) {
    addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(color))
}

private const val BLUE =
    "M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
private const val GREEN =
    "M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
private const val YELLOW =
    "M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
private const val RED =
    "M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
