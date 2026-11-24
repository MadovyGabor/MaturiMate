package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppCodeField
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTextField
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.presentation.toUiText
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_first_name
import maturimate.shared.generated.resources.account_last_name
import maturimate.shared.generated.resources.account_password_hint
import maturimate.shared.generated.resources.code_label
import maturimate.shared.generated.resources.code_resend
import maturimate.shared.generated.resources.code_sent
import maturimate.shared.generated.resources.common_cancel
import maturimate.shared.generated.resources.common_loading
import maturimate.shared.generated.resources.common_save
import maturimate.shared.generated.resources.settings_delete_confirm
import maturimate.shared.generated.resources.settings_delete_google_confirm
import maturimate.shared.generated.resources.settings_delete_google_text
import maturimate.shared.generated.resources.settings_delete_password
import maturimate.shared.generated.resources.settings_delete_text
import maturimate.shared.generated.resources.settings_delete_title
import maturimate.shared.generated.resources.settings_delete_word_prompt
import maturimate.shared.generated.resources.settings_password_old
import maturimate.shared.generated.resources.settings_password_wrong
import maturimate.shared.generated.resources.settings_export
import maturimate.shared.generated.resources.settings_export_hint
import maturimate.shared.generated.resources.settings_export_save
import maturimate.shared.generated.resources.settings_export_share
import maturimate.shared.generated.resources.settings_name_title
import maturimate.shared.generated.resources.settings_password
import maturimate.shared.generated.resources.settings_password_code_send
import maturimate.shared.generated.resources.settings_password_code_text
import maturimate.shared.generated.resources.settings_password_new
import maturimate.shared.generated.resources.settings_password_mismatch
import maturimate.shared.generated.resources.settings_password_repeat
import org.jetbrains.compose.resources.stringResource

/** A failure said in words, in the error color; read out when it appears. */
@Composable
private fun ErrorText(error: AuthError) {
    Text(
        error.toUiText().asString(),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * "Are you sure?" before a red action: the title, what happens, and the red button. While [busy] the button says so and waits.
 * A failure of the action is said in [error] and the dialog stays open. With a [word] (and the [wordLabel] that asks for it)
 * the button works only after the user typed that word (case does not matter, spaces around it do not count): a guard
 * against a slip for what cannot be undone.
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
    word: String? = null,
    wordLabel: String? = null,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val matches = word == null || typed.trim().equals(word, ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (word != null) AppTextField(value = typed, onValueChange = { typed = it }, label = wordLabel, enabled = !busy)
                error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy && matches,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(if (busy) stringResource(Res.string.common_loading) else confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** The family name and the first name; both are needed. [error] is the last failure of a save. */
@Composable
internal fun NameDialog(lastName: String, firstName: String, busy: Boolean, error: AuthError?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var last by rememberSaveable { mutableStateOf(lastName) }
    var first by rememberSaveable { mutableStateOf(firstName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
                AppTextField(value = last, onValueChange = { last = it }, label = stringResource(Res.string.account_last_name))
                AppTextField(value = first, onValueChange = { first = it }, label = stringResource(Res.string.account_first_name))
                error?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(first, last) }, enabled = !busy && first.isNotBlank() && last.isNotBlank()) {
                Text(if (busy) stringResource(Res.string.common_loading) else stringResource(Res.string.common_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/**
 * The password change, proved by BOTH the current password and a code from the e-mail (Supabase reauthentication: GoTrue
 * checks the code only on sessions older than 24 hours, so the current password, checked by the server, covers a fresh
 * one). Two steps: the code is sent to [email]; then the code, the current password and the new one twice (a typo is
 * caught). The step and the error live in the view model ([step], [error]); what is typed is kept over a rotation. Each
 * error is said at its field.
 */
@Composable
internal fun PasswordDialog(
    email: String,
    step: PasswordStep,
    busy: Boolean,
    error: AuthError?,
    onSendCode: () -> Unit,
    onClearError: () -> Unit,
    /** The current password, the code and the new password. */
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    // the current password is never written to the saved state (plain remember): a rotation asks for it again
    var old by remember { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    // said only once the second one is as long as the first, not at every letter
    val mismatch = repeat.length >= new.length && repeat.isNotEmpty() && repeat != new
    val ready = !busy && code.isNotBlank() && old.isNotEmpty() && new.isNotEmpty() && repeat == new
    val save = { if (ready) onSave(old, code, new) }
    val typed: (String) -> Unit = { if (error != null) onClearError() }
    val codeError = error == AuthError.INVALID_CODE
    val oldError = error == AuthError.INVALID_CREDENTIALS
    val newError = error == AuthError.WEAK_PASSWORD || error == AuthError.SAME_PASSWORD
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_password)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3),
            ) {
                when (step) {
                    PasswordStep.SEND -> Text(stringResource(Res.string.settings_password_code_text, email), style = MaterialTheme.typography.bodyMedium)
                    PasswordStep.FORM -> {
                        Text(stringResource(Res.string.code_sent, email), style = MaterialTheme.typography.bodyMedium)
                        AppCodeField(
                            value = code,
                            onValueChange = { code = it; typed(it) },
                            label = stringResource(Res.string.code_label),
                            imeAction = ImeAction.Next,
                            error = if (codeError) error?.toUiText()?.asString() else null,
                        )
                        TextButton(onClick = onSendCode, enabled = !busy) { Text(stringResource(Res.string.code_resend)) }
                        AppTextField(
                            value = old,
                            onValueChange = { old = it; typed(it) },
                            label = stringResource(Res.string.settings_password_old),
                            password = true,
                            contentType = ContentType.Password,
                            imeAction = ImeAction.Next,
                            // the settings' own words: "wrong e-mail or password" would not fit here
                            error = if (oldError) stringResource(Res.string.settings_password_wrong) else null,
                        )
                        AppTextField(
                            value = new,
                            onValueChange = { new = it; typed(it) },
                            label = stringResource(Res.string.settings_password_new),
                            helper = stringResource(Res.string.account_password_hint),
                            password = true,
                            contentType = ContentType.NewPassword,
                            imeAction = ImeAction.Next,
                            error = if (newError) error?.toUiText()?.asString() else null,
                        )
                        AppTextField(
                            value = repeat,
                            onValueChange = { repeat = it },
                            label = stringResource(Res.string.settings_password_repeat),
                            imeAction = ImeAction.Done,
                            onImeAction = save,
                            password = true,
                            error = if (mismatch) stringResource(Res.string.settings_password_mismatch) else null,
                        )
                    }
                }
                // an error that is not a field's own (network, expired session, too many tries)
                error?.takeIf { !codeError && !oldError && !newError }?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            val loading = stringResource(Res.string.common_loading)
            when (step) {
                PasswordStep.SEND -> TextButton(onClick = onSendCode, enabled = !busy) {
                    Text(if (busy) loading else stringResource(Res.string.settings_password_code_send))
                }
                PasswordStep.FORM -> TextButton(onClick = save, enabled = ready) {
                    Text(if (busy) loading else stringResource(Res.string.common_save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/**
 * "Are you sure?" before the account deletion, with the proof that the user is the owner: the typed [word] guards against a
 * slip, and an account [withPassword] types its password (checked by the server); an account made with Google signs in
 * with Google again instead ([onGoogle]). While [busy] the button says so and waits; a failure is said in [error] (a wrong
 * password at its field) and the dialog stays open.
 */
@Composable
internal fun DeleteDialog(
    withPassword: Boolean,
    word: String,
    busy: Boolean,
    error: AuthError?,
    onDelete: (String) -> Unit,
    onGoogle: () -> Unit,
    onClearError: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    // the password is never written to the saved state (plain remember): a rotation asks for it again
    var password by remember { mutableStateOf("") }
    val matches = typed.trim().equals(word, ignoreCase = true)
    val ready = !busy && matches && (!withPassword || password.isNotEmpty())
    val passwordError = withPassword && error == AuthError.INVALID_CREDENTIALS
    val confirm = { if (ready) { if (withPassword) onDelete(password) else onGoogle() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_delete_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3),
            ) {
                Text(stringResource(Res.string.settings_delete_text), style = MaterialTheme.typography.bodyMedium)
                if (!withPassword) Text(stringResource(Res.string.settings_delete_google_text), style = MaterialTheme.typography.bodyMedium)
                AppTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = stringResource(Res.string.settings_delete_word_prompt, word),
                    enabled = !busy,
                    imeAction = if (withPassword) ImeAction.Next else ImeAction.Done,
                )
                if (withPassword) {
                    AppTextField(
                        value = password,
                        onValueChange = { password = it; if (error != null) onClearError() },
                        label = stringResource(Res.string.settings_delete_password),
                        password = true,
                        contentType = ContentType.Password,
                        enabled = !busy,
                        imeAction = ImeAction.Done,
                        onImeAction = confirm,
                        error = if (passwordError) stringResource(Res.string.settings_password_wrong) else null,
                    )
                }
                error?.takeIf { !passwordError }?.let { ErrorText(it) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = confirm,
                enabled = ready,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    when {
                        busy -> stringResource(Res.string.common_loading)
                        withPassword -> stringResource(Res.string.settings_delete_confirm)
                        else -> stringResource(Res.string.settings_delete_google_confirm)
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** Where the copy of the data goes, on a phone that can share: into a file, or to another app. */
@Composable
internal fun ExportDialog(onSave: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_export)) },
        text = {
            Column {
                Text(stringResource(Res.string.settings_export_hint), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onSave) { Text(stringResource(Res.string.settings_export_save)) }
                TextButton(onClick = onShare) { Text(stringResource(Res.string.settings_export_share)) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}
