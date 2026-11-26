package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import com.madovygabor.maturimate.core.presentation.uiComponents.AppCodeField
import maturimate.shared.generated.resources.code_label
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.error_email_invalid
import maturimate.shared.generated.resources.error_email_taken
import maturimate.shared.generated.resources.error_first_name_empty
import maturimate.shared.generated.resources.error_last_name_empty
import maturimate.shared.generated.resources.error_code_invalid
import maturimate.shared.generated.resources.error_password_weak
import maturimate.shared.generated.resources.error_passwords_differ
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The e-mail code step: the [title] (null when the screen has its own), the sentence saying where the code went, and
 * the code field. Used by the account form, the sign-in confirmation and the password reset.
 */
@Composable
fun ColumnScope.CodeStepContent(
    title: String?,
    sent: String,
    code: String,
    error: String?,
    enabled: Boolean,
    onCodeChanged: (String) -> Unit,
) {
    if (title != null) Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    Text(sent, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    AppCodeField(
        value = code,
        onValueChange = onCodeChanged,
        label = stringResource(Res.string.code_label),
        error = error,
        enabled = enabled,
    )
}

/** The sentence for a field error. */
@Composable
fun FieldError.text(field: AccountField?): String = stringResource(resource(field))

private fun FieldError.resource(field: AccountField?): StringResource = when (this) {
    FieldError.EMPTY -> if (field == AccountField.LAST_NAME) Res.string.error_last_name_empty else Res.string.error_first_name_empty
    FieldError.EMAIL_INVALID -> Res.string.error_email_invalid
    FieldError.PASSWORD_WEAK -> Res.string.error_password_weak
    FieldError.EMAIL_TAKEN -> Res.string.error_email_taken
    FieldError.CODE_INVALID -> Res.string.error_code_invalid
    FieldError.PASSWORDS_DIFFER -> Res.string.error_passwords_differ
}
