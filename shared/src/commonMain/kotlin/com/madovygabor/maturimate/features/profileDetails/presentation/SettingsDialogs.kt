package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTextField
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.presentation.toUiText
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_first_name
import maturimate.shared.generated.resources.account_last_name
import maturimate.shared.generated.resources.account_password_hint
import maturimate.shared.generated.resources.common_cancel
import maturimate.shared.generated.resources.common_ok
import maturimate.shared.generated.resources.common_save
import maturimate.shared.generated.resources.settings_name_title
import maturimate.shared.generated.resources.settings_password
import maturimate.shared.generated.resources.settings_password_new
import maturimate.shared.generated.resources.settings_password_mismatch
import maturimate.shared.generated.resources.settings_password_old
import maturimate.shared.generated.resources.settings_password_repeat
import maturimate.shared.generated.resources.settings_password_wrong
import org.jetbrains.compose.resources.stringResource

/** "Are you sure?" before a red action: the title, what happens, and the red button. */
@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** The family name and the first name; both are needed. [error] is the last failure of a save. */
@Composable
internal fun NameDialog(lastName: String, firstName: String, busy: Boolean, error: AuthError?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var last by remember { mutableStateOf(lastName) }
    var first by remember { mutableStateOf(firstName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
                AppTextField(value = last, onValueChange = { last = it }, label = stringResource(Res.string.account_last_name))
                AppTextField(value = first, onValueChange = { first = it }, label = stringResource(Res.string.account_first_name))
                error?.let { Text(it.toUiText().asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(first, last) }, enabled = !busy && first.isNotBlank() && last.isNotBlank()) {
                Text(stringResource(Res.string.common_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** The old password and the new one twice, so a typo is caught. A wrong old password is said so at its field. */
@Composable
internal fun PasswordDialog(busy: Boolean, error: AuthError?, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    // said only once the second one is as long as the first, not at every letter
    val mismatch = repeat.length >= new.length && repeat.isNotEmpty() && repeat != new
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
                AppTextField(
                    value = old,
                    onValueChange = { old = it },
                    label = stringResource(Res.string.settings_password_old),
                    password = true,
                    error = if (error == AuthError.INVALID_CREDENTIALS) stringResource(Res.string.settings_password_wrong) else null,
                )
                AppTextField(
                    value = new,
                    onValueChange = { new = it },
                    label = stringResource(Res.string.settings_password_new),
                    helper = stringResource(Res.string.account_password_hint),
                    password = true,
                    error = error?.takeIf { it == AuthError.WEAK_PASSWORD || it == AuthError.SAME_PASSWORD }?.toUiText()?.asString(),
                )
                AppTextField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = stringResource(Res.string.settings_password_repeat),
                    password = true,
                    error = if (mismatch) stringResource(Res.string.settings_password_mismatch) else null,
                )
                error?.takeIf { it == AuthError.NETWORK || it == AuthError.UNKNOWN || it == AuthError.TOO_MANY_REQUESTS }?.let {
                    Text(it.toUiText().asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(old, new) }, enabled = !busy && old.isNotEmpty() && new.isNotEmpty() && repeat == new) {
                Text(stringResource(Res.string.common_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}

/** A message with one button that closes it. */
@Composable
internal fun InfoDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_ok)) } },
    )
}
