package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.logo
import maturimate.shared.generated.resources.logoTransparent
import org.jetbrains.compose.resources.painterResource

/**
 * The MaturiMate logo, never recolored (docs/design-system/README.md).
 *
 * On the light cream background the transparent logo is shown; on dark surfaces the logo
 * sits on its own cream tile (`logo.png`) with rounded corners.
 *
 * @param contentDescription Null when a text next to the logo already names the app.
 */
@Composable
fun AppLogo(
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tileShape: CornerBasedShape = MaterialTheme.shapes.extraLarge,
) {
    // follows the theme in effect, so a forced light or dark theme shows the right logo
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (darkTheme) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = contentDescription,
            modifier = modifier.size(size).clip(tileShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Image(
            painter = painterResource(Res.drawable.logoTransparent),
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    }
}
