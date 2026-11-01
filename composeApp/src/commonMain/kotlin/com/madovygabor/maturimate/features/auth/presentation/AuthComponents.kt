package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.error_email_invalid
import maturimate.composeapp.generated.resources.error_email_taken
import maturimate.composeapp.generated.resources.error_first_name_empty
import maturimate.composeapp.generated.resources.error_last_name_empty
import maturimate.composeapp.generated.resources.error_code_invalid
import maturimate.composeapp.generated.resources.error_password_weak
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A check box with its text; the whole row toggles it. */
@Composable
fun CheckRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, error: String? = null) {
    Column {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.layout.touchTarget)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.outline),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
    }
    if (error != null) Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

/** A line with a short text in the middle, for example "vagy e-maillel". */
@Composable
fun DividerWithText(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
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
}
