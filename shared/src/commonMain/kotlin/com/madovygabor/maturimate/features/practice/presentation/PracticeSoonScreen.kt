package com.madovygabor.maturimate.features.practice.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTag
import com.madovygabor.maturimate.core.presentation.uiComponents.TagStyle
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.practice_soon_text
import maturimate.shared.generated.resources.practice_soon_title
import maturimate.shared.generated.resources.subjects_coming_soon
import org.jetbrains.compose.resources.stringResource

/** The practice tab until Milestone 9a: what it will hold, and "Hamarosan". */
@Composable
fun PracticeSoonScreen() {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(spacing.space6), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = MaterialTheme.layout.contentMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHighest, contentColor = colors.onSurfaceVariant) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.padding(spacing.space4).size(MaterialTheme.layout.badge))
            }
            Text(stringResource(Res.string.practice_soon_title), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
            AppTag(stringResource(Res.string.subjects_coming_soon), style = TagStyle.HIGHLIGHT)
            Text(
                stringResource(Res.string.practice_soon_text),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
