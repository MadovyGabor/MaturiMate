package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppCodeField
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTextField
import com.madovygabor.maturimate.features.auth.domain.AccountRules
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.presentation.toUiText
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_password_rule_case
import maturimate.shared.generated.resources.account_password_rule_digit
import maturimate.shared.generated.resources.account_password_rule_length
import maturimate.shared.generated.resources.code_confirm
import maturimate.shared.generated.resources.code_label
import maturimate.shared.generated.resources.code_resend
import maturimate.shared.generated.resources.password_rule_met
import maturimate.shared.generated.resources.password_rule_unmet
import maturimate.shared.generated.resources.settings_delete
import maturimate.shared.generated.resources.password_rule_met
import maturimate.shared.generated.resources.password_rule_unmet
import maturimate.shared.generated.resources.settings_delete_confirm
import maturimate.shared.generated.resources.password_rule_met
import maturimate.shared.generated.resources.password_rule_unmet
import maturimate.shared.generated.resources.settings_delete_desc
import maturimate.shared.generated.resources.password_rule_met
import maturimate.shared.generated.resources.password_rule_unmet
import maturimate.shared.generated.resources.settings_delete_google_confirm
import maturimate.shared.generated.resources.password_rule_met
import maturimate.shared.generated.resources.password_rule_unmet
import maturimate.shared.generated.resources.settings_delete_google_text
import maturimate.shared.generated.resources.password_rule_met
import maturimate.shared.generated.resources.password_rule_unmet
import maturimate.shared.generated.resources.settings_delete_password
import maturimate.shared.generated.resources.password_rule_met
import maturimate.shared.generated.resources.password_rule_unmet
import maturimate.shared.generated.resources.settings_delete_section_confirm
import maturimate.shared.generated.resources.password_rule_met
import maturimate.shared.generated.resources.password_rule_unmet
import maturimate.shared.generated.resources.settings_delete_warning_short
import maturimate.shared.generated.resources.password_rule_met
import maturimate.shared.generated.resources.password_rule_unmet
import maturimate.shared.generated.resources.settings_delete_word
import maturimate.shared.generated.resources.password_rule_met
import maturimate.shared.generated.resources.password_rule_unmet
import maturimate.shared.generated.resources.settings_delete_word_prompt
import maturimate.shared.generated.resources.settings_edit_password_desc
import maturimate.shared.generated.resources.settings_password
import maturimate.shared.generated.resources.settings_password_code_send
import maturimate.shared.generated.resources.settings_password_mismatch
import maturimate.shared.generated.resources.settings_password_new
import maturimate.shared.generated.resources.settings_password_old
import maturimate.shared.generated.resources.settings_password_reauth_need_send
import maturimate.shared.generated.resources.settings_password_reauth_sent
import maturimate.shared.generated.resources.settings_password_reauth_title
import maturimate.shared.generated.resources.settings_password_repeat
import maturimate.shared.generated.resources.settings_password_section_code
import maturimate.shared.generated.resources.settings_password_section_current
import maturimate.shared.generated.resources.settings_password_section_new
import maturimate.shared.generated.resources.settings_password_wrong
import org.jetbrains.compose.resources.stringResource

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = spacing.space1).semantics { heading() },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainerLow,
            border = BorderStroke(MaterialTheme.layout.borderThin, colors.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(spacing.space4),
                verticalArrangement = Arrangement.spacedBy(spacing.space3),
                content = content,
            )
        }
    }
}

@Composable
private fun PageErrorText(error: AuthError) {
    Text(
        error.toUiText().asString(),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
fun SettingsPasswordPage(
    email: String,
    pageState: PasswordPageState,
    busy: Boolean,
    onClearError: () -> Unit,
    onSendCode: () -> Unit,
    onBack: () -> Unit,
    onSave: (oldPassword: String, newPassword: String, code: String?) -> Unit,
    onBackToForm: () -> Unit = onBack,
) {
    // Passwords and one-time codes are kept in memory only (plain remember), never written to Android SavedState.
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newShown by rememberSaveable { mutableStateOf(false) }

    // If rotation cleared in-memory passwords while on the CODE step, return to FORM instead of disabling Confirm forever.
    LaunchedEffect(pageState.step, old) {
        if (pageState.step == PasswordStep.CODE && old.isEmpty()) onBackToForm()
    }

    val error = pageState.error
    val oldError = error == AuthError.INVALID_CREDENTIALS
    val newError = error == AuthError.WEAK_PASSWORD || error == AuthError.SAME_PASSWORD
    val codeError = error == AuthError.INVALID_CODE
    val mismatch = repeat.length >= new.length && repeat.isNotEmpty() && repeat != new
    val formReady = old.isNotEmpty() && AccountRules.isStrongPassword(new) && repeat == new
    val codeReady = AccountRules.isCode(code) && formReady

    when (pageState.step) {
        PasswordStep.FORM -> {
            SettingsEditScaffold(
                title = stringResource(Res.string.settings_password),
                description = stringResource(Res.string.settings_edit_password_desc),
                onBack = onBack,
                onSave = { onSave(old, new, null) },
                canSave = formReady,
                saving = busy,
            ) {
                CurrentPasswordCard(
                    old = old,
                    busy = busy,
                    oldError = oldError,
                    onOldChange = { old = it; if (error != null) onClearError() },
                )
                NewPasswordCard(
                    new = new,
                    repeat = repeat,
                    newShown = newShown,
                    busy = busy,
                    newErrorText = if (newError) error.toUiText().asString() else null,
                    mismatch = mismatch,
                    formReady = formReady,
                    onNewChange = { new = it; if (error != null) onClearError() },
                    onRepeatChange = { repeat = it; if (error != null) onClearError() },
                    onShownChange = { newShown = it },
                    onSubmit = { if (formReady && !busy) onSave(old, new, null) },
                )
                error?.takeIf { !oldError && !newError }?.let { PageErrorText(it) }
            }
        }
        PasswordStep.CODE -> {
            SettingsEditScaffold(
                title = stringResource(Res.string.settings_password_reauth_title),
                description = if (pageState.codeSent) stringResource(Res.string.settings_password_reauth_sent, email)
                else stringResource(Res.string.settings_password_reauth_need_send),
                onBack = onBackToForm,
                onSave = { onSave(old, new, code) },
                canSave = codeReady,
                saving = busy,
                saveLabel = stringResource(Res.string.code_confirm),
            ) {
                SettingsSectionCard(stringResource(Res.string.settings_password_section_code)) {
                    AppCodeField(
                        value = code,
                        onValueChange = { code = it; if (error != null) onClearError() },
                        label = stringResource(Res.string.code_label),
                        enabled = !busy,
                        imeAction = ImeAction.Done,
                        onImeAction = { if (codeReady && !busy) onSave(old, new, code) },
                        error = if (codeError) error.toUiText().asString() else null,
                    )
                    AppButton(
                        text = stringResource(if (pageState.codeSent) Res.string.code_resend else Res.string.settings_password_code_send),
                        onClick = { code = ""; onSendCode() },
                        variant = AppButtonVariant.TEXT,
                        enabled = !busy,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                error?.takeIf { !codeError }?.let { PageErrorText(it) }
            }
        }
    }
}

@Composable
private fun CurrentPasswordCard(
    old: String,
    busy: Boolean,
    oldError: Boolean,
    onOldChange: (String) -> Unit,
) {
    SettingsSectionCard(stringResource(Res.string.settings_password_section_current)) {
        AppTextField(
            value = old,
            onValueChange = onOldChange,
            label = stringResource(Res.string.settings_password_old),
            password = true,
            contentType = ContentType.Password,
            enabled = !busy,
            imeAction = ImeAction.Next,
            error = if (oldError) stringResource(Res.string.settings_password_wrong) else null,
        )
    }
}

@Composable
private fun NewPasswordCard(
    new: String,
    repeat: String,
    newShown: Boolean,
    busy: Boolean,
    newErrorText: String?,
    mismatch: Boolean,
    formReady: Boolean,
    onNewChange: (String) -> Unit,
    onRepeatChange: (String) -> Unit,
    onShownChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    SettingsSectionCard(stringResource(Res.string.settings_password_section_new)) {
        AppTextField(
            value = new,
            onValueChange = onNewChange,
            label = stringResource(Res.string.settings_password_new),
            password = true,
            passwordShown = newShown,
            onPasswordShownChange = onShownChange,
            contentType = ContentType.NewPassword,
            enabled = !busy,
            imeAction = ImeAction.Next,
            error = newErrorText,
        )
        AppTextField(
            value = repeat,
            onValueChange = onRepeatChange,
            label = stringResource(Res.string.settings_password_repeat),
            password = true,
            passwordShown = newShown,
            onPasswordShownChange = onShownChange,
            contentType = ContentType.NewPassword,
            enabled = !busy,
            imeAction = ImeAction.Done,
            onImeAction = { if (formReady && !busy) onSubmit() },
            error = if (mismatch) stringResource(Res.string.settings_password_mismatch) else null,
        )
        SettingsPasswordRules(new)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPasswordRules(password: String) {
    val metText = stringResource(Res.string.password_rule_met)
    val unmetText = stringResource(Res.string.password_rule_unmet)
    val rules = listOf(
        stringResource(Res.string.account_password_rule_length) to AccountRules.passwordHasLength(password),
        stringResource(Res.string.account_password_rule_case) to AccountRules.passwordHasBothCases(password),
        stringResource(Res.string.account_password_rule_digit) to AccountRules.passwordHasDigit(password),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space4),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space1),
    ) {
        for ((text, met) in rules) {
            val color = if (met) MaterialTheme.extendedColors.success else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space1),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    stateDescription = if (met) metText else unmetText
                },
            ) {
                Icon(
                    imageVector = if (met) Icons.Rounded.Check else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(MaterialTheme.layout.iconSmall),
                )
                Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
    }
}

@Composable
fun SettingsDeletePage(
    withPassword: Boolean,
    busy: Boolean,
    error: AuthError?,
    onClearError: () -> Unit,
    onDeleteWithPassword: (String) -> Unit,
    onDeleteWithGoogle: () -> Unit,
    onBack: () -> Unit,
) {
    val word = stringResource(Res.string.settings_delete_word)
    var typed by rememberSaveable { mutableStateOf("") }
    // Password is never written to saved state (plain remember).
    var password by remember { mutableStateOf("") }
    val matches = typed.trim().equals(word, ignoreCase = true)
    val ready = matches && (!withPassword || password.isNotEmpty())
    val passwordError = withPassword && error == AuthError.INVALID_CREDENTIALS
    val confirm = {
        if (ready) {
            if (withPassword) onDeleteWithPassword(password)
            else onDeleteWithGoogle()
        }
    }

    SettingsEditScaffold(
        title = stringResource(Res.string.settings_delete),
        description = stringResource(Res.string.settings_delete_desc),
        onBack = onBack,
        onSave = confirm,
        canSave = ready,
        saving = busy,
        saveLabel = stringResource(if (withPassword) Res.string.settings_delete_confirm else Res.string.settings_delete_google_confirm),
        destructive = true,
    ) {
        val colors = MaterialTheme.colorScheme
        val spacing = MaterialTheme.spacing
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = colors.errorContainer,
            contentColor = colors.onErrorContainer,
            border = BorderStroke(MaterialTheme.layout.borderThin, colors.outlineVariant),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space3),
            ) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(MaterialTheme.layout.icon))
                Text(
                    text = stringResource(if (withPassword) Res.string.settings_delete_warning_short else Res.string.settings_delete_google_text),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        SettingsSectionCard(stringResource(Res.string.settings_delete_section_confirm)) {
            AppTextField(
                value = typed,
                onValueChange = { typed = it; if (error != null) onClearError() },
                label = stringResource(Res.string.settings_delete_word_prompt, word),
                enabled = !busy,
                imeAction = if (withPassword) ImeAction.Next else ImeAction.Done,
                onImeAction = if (withPassword) null else confirm,
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
        }
        error?.takeIf { !passwordError }?.let { PageErrorText(it) }
    }
}
