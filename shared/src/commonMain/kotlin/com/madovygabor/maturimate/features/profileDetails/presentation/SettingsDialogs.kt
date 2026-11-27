package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceCard
import com.madovygabor.maturimate.core.presentation.uiComponents.AppDialog
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTextField
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.presentation.hungarianOrder
import com.madovygabor.maturimate.features.auth.presentation.toUiText
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_first_name
import maturimate.shared.generated.resources.account_last_name
import maturimate.shared.generated.resources.common_cancel
import maturimate.shared.generated.resources.common_save
import maturimate.shared.generated.resources.language_hu
import maturimate.shared.generated.resources.language_sk
import maturimate.shared.generated.resources.settings_choose_language
import maturimate.shared.generated.resources.settings_edit_name_desc
import maturimate.shared.generated.resources.settings_edit_name_title
import maturimate.shared.generated.resources.settings_export
import maturimate.shared.generated.resources.settings_export_hint
import maturimate.shared.generated.resources.settings_export_save
import maturimate.shared.generated.resources.settings_export_share
import maturimate.shared.generated.resources.settings_reset_confirm
import maturimate.shared.generated.resources.settings_reset_confirm_title
import maturimate.shared.generated.resources.settings_reset_word
import maturimate.shared.generated.resources.settings_reset_word_label
import maturimate.shared.generated.resources.settings_reset_word_prompt
import org.jetbrains.compose.resources.stringResource

/**
 * Short confirmation dialog backed by [AppDialog]. Used for reset progress, sign out, and replace-answers.
 */
@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    error: AuthError? = null,
    destructive: Boolean = true,
) {
    AppDialog(
        title = title,
        text = text,
        confirmText = confirm,
        onConfirm = onConfirm,
        confirmEnabled = true,
        confirmLoading = busy,
        dismissText = stringResource(Res.string.common_cancel),
        onDismiss = onDismiss,
        destructive = destructive,
        content = error?.let {
            {
                Text(
                    it.toUiText().asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        },
    )
}

/** One-line choice dialog for app language. */
@Composable
internal fun AppLanguageDialog(
    current: TeachingLanguage?,
    onChoose: (TeachingLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = stringResource(Res.string.settings_choose_language),
        dismissText = stringResource(Res.string.common_cancel),
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2)) {
            TeachingLanguage.entries.forEach { language ->
                AppChoiceCard(
                    badge = language.name,
                    title = stringResource(if (language == TeachingLanguage.HU) Res.string.language_hu else Res.string.language_sk),
                    description = "",
                    selected = language == current,
                    onClick = { onChoose(language) },
                )
            }
        }
    }
}

/** Name edit dialog backed by [AppDialog]. */
@Composable
internal fun NameDialog(
    initialFirstName: String,
    initialLastName: String,
    busy: Boolean,
    error: AuthError?,
    onClearError: () -> Unit,
    onSave: (firstName: String, lastName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var first by rememberSaveable(initialFirstName) { mutableStateOf(initialFirstName) }
    var last by rememberSaveable(initialLastName) { mutableStateOf(initialLastName) }
    val trimmedFirst = first.trim()
    val trimmedLast = last.trim()
    val changed = trimmedFirst != initialFirstName.trim() || trimmedLast != initialLastName.trim()
    val canSave = trimmedFirst.isNotEmpty() && trimmedLast.isNotEmpty() && changed

    AppDialog(
        title = stringResource(Res.string.settings_edit_name_title),
        text = stringResource(Res.string.settings_edit_name_desc),
        confirmText = stringResource(Res.string.common_save),
        onConfirm = { onSave(trimmedFirst, trimmedLast) },
        confirmEnabled = canSave,
        confirmLoading = busy,
        dismissText = stringResource(Res.string.common_cancel),
        onDismiss = onDismiss,
    ) {
        val lastField: @Composable () -> Unit = {
            AppTextField(
                value = last,
                onValueChange = { last = it; if (error != null) onClearError() },
                label = stringResource(Res.string.account_last_name),
                enabled = !busy,
                contentType = ContentType.PersonLastName,
                imeAction = ImeAction.Next,
            )
        }
        val firstField: @Composable () -> Unit = {
            AppTextField(
                value = first,
                onValueChange = { first = it; if (error != null) onClearError() },
                label = stringResource(Res.string.account_first_name),
                enabled = !busy,
                contentType = ContentType.PersonFirstName,
                imeAction = ImeAction.Done,
                onImeAction = { if (canSave && !busy) onSave(trimmedFirst, trimmedLast) },
            )
        }
        if (hungarianOrder()) {
            lastField()
            firstField()
        } else {
            firstField()
            lastField()
        }
        error?.let {
            Text(
                it.toUiText().asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Second-step verification dialog for resetting progress: requires typing the confirmation word. */
@Composable
internal fun ResetConfirmDialog(
    busy: Boolean,
    error: AuthError?,
    onClearError: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val word = stringResource(Res.string.settings_reset_word)
    var typed by rememberSaveable { mutableStateOf("") }
    val matches = typed.trim().equals(word, ignoreCase = true)

    AppDialog(
        title = stringResource(Res.string.settings_reset_confirm_title),
        text = stringResource(Res.string.settings_reset_word_prompt, word),
        confirmText = stringResource(Res.string.settings_reset_confirm),
        onConfirm = onConfirm,
        confirmEnabled = matches,
        confirmLoading = busy,
        dismissText = stringResource(Res.string.common_cancel),
        onDismiss = onDismiss,
        destructive = true,
    ) {
        AppTextField(
            value = typed,
            onValueChange = { typed = it; if (error != null) onClearError() },
            label = stringResource(Res.string.settings_reset_word_label),
            enabled = !busy,
            imeAction = ImeAction.Done,
            onImeAction = { if (matches && !busy) onConfirm() },
        )
        error?.let {
            Text(
                it.toUiText().asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** One-line choice dialog for data export (save to file or share). */
@Composable
internal fun ExportDialog(onSave: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        title = stringResource(Res.string.settings_export),
        text = stringResource(Res.string.settings_export_hint),
        dismissText = stringResource(Res.string.common_cancel),
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2)) {
            AppButton(
                text = stringResource(Res.string.settings_export_save),
                onClick = onSave,
                variant = AppButtonVariant.TONAL,
                modifier = Modifier.fillMaxWidth(),
            )
            AppButton(
                text = stringResource(Res.string.settings_export_share),
                onClick = onShare,
                variant = AppButtonVariant.OUTLINED,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
