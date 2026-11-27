package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/**
 * Shared dialog of the design system (docs/design-system/components/Dialog.md), for short confirmations,
 * one-line choices, and name editing. Built from theme values only (`surfaceContainerHigh`, `radius-xl`, `dialogMaxWidth`).
 */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    dismissText: String? = null,
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    Dialog(
        onDismissRequest = { if (!confirmLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier.padding(horizontal = spacing.space4).widthIn(max = layout.dialogMaxWidth).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerHigh,
            border = BorderStroke(layout.borderThin, colors.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(spacing.space6),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                if (text != null || content != null) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.space3),
                    ) {
                        if (text != null) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (content != null) content()
                    }
                }
                if (dismissText != null || (confirmText != null && onConfirm != null)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.space2, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissText != null) {
                            AppButton(
                                text = dismissText,
                                onClick = onDismiss,
                                variant = AppButtonVariant.TEXT,
                                enabled = !confirmLoading,
                            )
                        }
                        if (confirmText != null && onConfirm != null) {
                            AppButton(
                                text = confirmText,
                                onClick = onConfirm,
                                variant = if (destructive) AppButtonVariant.DESTRUCTIVE else AppButtonVariant.FILLED,
                                enabled = confirmEnabled && !confirmLoading,
                                loading = confirmLoading,
                            )
                        }
                    }
                }
            }
        }
    }
}
