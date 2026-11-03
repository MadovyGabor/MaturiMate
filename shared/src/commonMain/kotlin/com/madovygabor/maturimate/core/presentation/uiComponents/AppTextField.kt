package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout

/**
 * Outlined text field with a label and a helper or error text
 * (docs/design-system/components/TextField.md).
 *
 * @param error What is wrong and what to do; shown instead of [helper] and marks the field.
 * @param password Hides the characters.
 * @param correct For an answer field after checking: true is shown green, false red (with [readOnly]); null is a normal field.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    correct: Boolean? = null,
) {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val support = error ?: helper
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = MaterialTheme.layout.choiceCardMinHeight),
        label = { Text(label) },
        supportingText = support?.let { { Text(it) } },
        isError = error != null || correct == false,
        singleLine = true,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerLowest,
            unfocusedContainerColor = if (correct == true) extended.successContainer else colors.surfaceContainerLowest,
            errorContainerColor = if (correct == false) colors.errorContainer else colors.surfaceContainerLowest,
            unfocusedBorderColor = if (correct == true) extended.success else colors.outline,
        ),
    )
}
