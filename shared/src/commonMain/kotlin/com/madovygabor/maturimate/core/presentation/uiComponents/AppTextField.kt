package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.extendedTypography
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.password_hide
import maturimate.shared.generated.resources.password_show
import org.jetbrains.compose.resources.stringResource

/**
 * Outlined text field with a label and a helper or error text
 * (docs/design-system/components/TextField.md).
 *
 * @param label The label on the border; null for a field whose label sits above it.
 * @param error What is wrong and what to do; shown instead of [helper] and marks the field.
 * @param password Hides the characters; a trailing button shows or hides them ([passwordShown] and
 *   [onPasswordShownChange] let two fields share that state).
 * @param contentType What the field holds, for autofill and the password manager (e-mail, password, new password).
 * @param code A one-time code: number keyboard, the monospaced `code` style, and the one-time-code content type.
 * @param correct For an answer field after checking: true is shown green, false red (with [readOnly]); null is a normal field.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    correct: Boolean? = null,
    /** Grey text inside the empty field ("Írd ide…"); a field with no [label] relies on it. */
    placeholder: String? = null,
    contentType: ContentType? = null,
    code: Boolean = false,
    /** The keyboard's action key: [ImeAction.Next] moves to the next field, [ImeAction.Done] submits via [onImeAction]. */
    imeAction: ImeAction = ImeAction.Default,
    /** Runs when the action key is pressed; null keeps the keyboard's own behaviour (Next moves the focus). */
    onImeAction: (() -> Unit)? = null,
    /**
     * Whether the password is shown, when the caller holds that state so that two fields (a password and its repeat) share
     * one eye button; null: the field keeps it itself.
     */
    passwordShown: Boolean? = null,
    onPasswordShownChange: ((Boolean) -> Unit)? = null,
) {
    var ownShown by rememberSaveable { mutableStateOf(false) }
    val shown = passwordShown ?: ownShown
    val toggleShown = { if (onPasswordShownChange != null) onPasswordShownChange(!shown) else ownShown = !shown }
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val support = error ?: helper
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().let { base ->
            val type = contentType ?: if (code) ContentType.SmsOtpCode else null
            if (type != null) base.semantics { this.contentType = type } else base
        },
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = support?.let { { Text(it) } },
        isError = error != null || correct == false,
        singleLine = true,
        enabled = enabled,
        readOnly = readOnly,
        trailingIcon = if (password) {
            {
                IconButton(onClick = toggleShown, enabled = enabled) {
                    Icon(
                        imageVector = if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(if (shown) Res.string.password_hide else Res.string.password_show),
                    )
                }
            }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else if (code) KeyboardType.Number else keyboardType, imeAction = imeAction),
        keyboardActions = if (onImeAction != null) KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }, onGo = { onImeAction() }) else KeyboardActions.Default,
        visualTransformation = if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None,
        shape = MaterialTheme.shapes.medium,
        textStyle = if (code) MaterialTheme.extendedTypography.code else MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerLowest,
            unfocusedContainerColor = if (correct == true) extended.successContainer else colors.surfaceContainerLowest,
            errorContainerColor = if (correct == false) colors.errorContainer else colors.surfaceContainerLowest,
            unfocusedBorderColor = if (correct == true) extended.success else colors.outline,
        ),
    )
}
