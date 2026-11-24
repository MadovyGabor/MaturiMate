package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.entrance
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.utils.snackbar.AppSnackbarVisuals
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarKind
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.snackbar_error
import maturimate.shared.generated.resources.snackbar_success
import org.jetbrains.compose.resources.stringResource

/**
 * The message of the app (docs/design-system/components/Snackbar.md), for the host in `MainScaffoldWrapper`.
 * A [SnackbarKind.SUCCESS] message is green with a check, a [SnackbarKind.ERROR] one is red with an error icon, any
 * other is the quiet dark one; the kind is never told by color alone. It comes in with the soft entrance of the
 * `motion` tokens (a fade and a small slide up).
 */
@Composable
fun AppSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val spacing = MaterialTheme.spacing
    val kind = (data.visuals as? AppSnackbarVisuals)?.kind ?: SnackbarKind.INFO
    val container = when (kind) {
        SnackbarKind.SUCCESS -> extended.successContainer
        SnackbarKind.ERROR -> colors.errorContainer
        SnackbarKind.INFO -> colors.inverseSurface
    }
    val content = when (kind) {
        SnackbarKind.SUCCESS -> extended.onSuccessContainer
        SnackbarKind.ERROR -> colors.onErrorContainer
        SnackbarKind.INFO -> colors.inverseOnSurface
    }
    Snackbar(
        // the margin around the message (the Snackbar of a SnackbarData has it built in, this content form has not)
        modifier = modifier.padding(spacing.space3).entrance(replay = true),
        action = data.visuals.actionLabel?.let { label ->
            { TextButton(onClick = { data.performAction() }) { Text(label) } }
        },
        shape = MaterialTheme.shapes.extraSmall,
        containerColor = container,
        contentColor = content,
        actionContentColor = if (kind == SnackbarKind.INFO) colors.inversePrimary else content,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            when (kind) {
                SnackbarKind.SUCCESS -> Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(Res.string.snackbar_success),
                    tint = extended.success,
                    modifier = Modifier.size(MaterialTheme.layout.icon),
                )
                SnackbarKind.ERROR -> Icon(
                    Icons.Rounded.Error,
                    contentDescription = stringResource(Res.string.snackbar_error),
                    tint = colors.error,
                    modifier = Modifier.size(MaterialTheme.layout.icon),
                )
                SnackbarKind.INFO -> Unit
            }
            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
