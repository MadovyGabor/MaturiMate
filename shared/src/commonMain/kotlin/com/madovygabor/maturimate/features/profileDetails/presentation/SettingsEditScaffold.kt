package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_back
import maturimate.shared.generated.resources.common_save
import org.jetbrains.compose.resources.stringResource

/**
 * Shared frame for every settings edit page (inside the settings navigation, without the onboarding progress bar):
 * top bar with Back and title, short 1-sentence description, scrollable content, and a single Save button at the bottom.
 */
@Composable
fun SettingsEditScaffold(
    title: String,
    description: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
    saving: Boolean = false,
    saveLabel: String = stringResource(Res.string.common_save),
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val keyboardOpen by remember(density, ime) { derivedStateOf { ime.getBottom(density) > 0 } }
    val scroll = rememberScrollState()
    val showBottomDivider by remember(scroll, keyboardOpen) {
        derivedStateOf { !keyboardOpen && scroll.canScrollForward }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val wide = maxWidth >= layout.breakpointMedium
        val columnModifier = Modifier.widthIn(max = layout.contentMaxWidth).fillMaxWidth()
        val sideMargin = if (wide) spacing.space6 else spacing.space4
        val buttons: @Composable (Modifier) -> Unit = { modifier ->
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.End,
            ) {
                AppButton(
                    text = saveLabel,
                    onClick = onSave,
                    variant = if (destructive) AppButtonVariant.DESTRUCTIVE else AppButtonVariant.FILLED,
                    enabled = canSave && !saving,
                    loading = saving,
                    modifier = if (wide) Modifier.widthIn(min = layout.buttonMinWidth) else Modifier.fillMaxWidth(),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = columnModifier.padding(start = spacing.space2, end = sideMargin, top = spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, enabled = !saving) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.common_back))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .then(columnModifier)
                    .padding(horizontal = sideMargin, vertical = spacing.space4),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
                content()
                if (keyboardOpen) {
                    buttons(Modifier.fillMaxWidth().padding(top = spacing.space2))
                }
            }
            if (showBottomDivider) {
                HorizontalDivider(color = colors.outlineVariant)
            }
            if (!keyboardOpen) {
                buttons(columnModifier.padding(horizontal = sideMargin, vertical = spacing.space3))
            }
        }
    }
}
