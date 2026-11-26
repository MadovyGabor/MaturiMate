package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.KeyboardType
import com.madovygabor.maturimate.core.presentation.locale.LocalAppLocale
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppCheckboxRow
import com.madovygabor.maturimate.core.presentation.uiComponents.AppDividerWithText
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppGoogleButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTextField
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.auth.domain.AccountRules
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_browse
import maturimate.shared.generated.resources.account_consent
import maturimate.shared.generated.resources.account_consent_required
import maturimate.shared.generated.resources.account_create
import maturimate.shared.generated.resources.account_email
import maturimate.shared.generated.resources.account_first_name
import maturimate.shared.generated.resources.account_google
import maturimate.shared.generated.resources.account_signed_in_as
import maturimate.shared.generated.resources.account_last_name
import maturimate.shared.generated.resources.account_news
import maturimate.shared.generated.resources.account_or_email
import maturimate.shared.generated.resources.account_password_rule_case
import maturimate.shared.generated.resources.account_password_rule_digit
import maturimate.shared.generated.resources.account_password_rule_length
import maturimate.shared.generated.resources.account_password_repeat
import maturimate.shared.generated.resources.account_title
import maturimate.shared.generated.resources.account_why
import maturimate.shared.generated.resources.code_confirm
import maturimate.shared.generated.resources.signup_app_code_sent
import maturimate.shared.generated.resources.signup_app_code_title
import maturimate.shared.generated.resources.signup_app_title
import maturimate.shared.generated.resources.signup_app_why
import maturimate.shared.generated.resources.code_resend
import maturimate.shared.generated.resources.code_sent
import maturimate.shared.generated.resources.code_title
import maturimate.shared.generated.resources.signin_password
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * The last onboarding step: create the account (e-mail with a 6-digit code, or Google), or
 * "Körülnézek". Creating the account or going on as a guest finishes the onboarding.
 */
@Composable
fun AccountScreen(
    role: UserRole?,
    onBack: () -> Unit,
    /** Opened from the profile by a guest: no steps and no "Körülnézek"; [onDone] runs once the account is set up. */
    inApp: Boolean = false,
    onDone: () -> Unit = {},
    viewModel: AccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    val google = rememberGoogleSignIn(onSignedIn = viewModel::onGoogleSignedIn, onError = viewModel::onGoogleFailed, onClosed = viewModel::onGoogleClosed)
    AccountContent(
        state = state,
        role = role,
        actions = AccountActions(
            onChanged = viewModel::onChanged,
            onConsentChanged = viewModel::onConsentChanged,
            onNewsChanged = viewModel::onNewsChanged,
            onGoogle = { viewModel.onGoogle(google) },
            onCreate = viewModel::onCreateAccount,
            onBrowse = viewModel::onBrowseAsGuest,
            onCodeChanged = viewModel::onCodeChanged,
            onConfirmCode = viewModel::onConfirmCode,
            onResendCode = viewModel::onResendCode,
            onBackToForm = viewModel::onBackToForm,
            onBack = onBack,
        ),
        inApp = inApp,
    )
}

/** What the account screen can do; one object keeps the screen's parameters short. */
data class AccountActions(
    val onChanged: (AccountField, String) -> Unit = { _, _ -> },
    val onConsentChanged: (Boolean) -> Unit = {},
    val onNewsChanged: (Boolean) -> Unit = {},
    val onGoogle: () -> Unit = {},
    val onCreate: () -> Unit = {},
    val onBrowse: () -> Unit = {},
    val onCodeChanged: (String) -> Unit = {},
    val onConfirmCode: () -> Unit = {},
    val onResendCode: () -> Unit = {},
    val onBackToForm: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun AccountContent(state: AccountUiState, role: UserRole?, actions: AccountActions, inApp: Boolean = false) {
    val email = state.codeSentTo
    if (email != null) CodeStep(state, email, role, actions, inApp) else FormStep(state, role, actions, inApp)
}

@Composable
private fun FormStep(state: AccountUiState, role: UserRole?, actions: AccountActions, inApp: Boolean) {
    val create = OnboardingAction(stringResource(Res.string.account_create), actions.onCreate, enabled = !state.busy, loading = state.busy)
    val idle = !state.busy
    OnboardingScaffold(
        // in the app the guest has looked around already: no "Körülnézek", and a plain back arrow instead of the steps.
        // A signed-in account (Google) is on the way to its sign-up: "Körülnézek" would sign it out again, so it is not offered.
        actions = if (inApp || state.signedInEmail != null) listOf(create)
        else listOf(create, OnboardingAction(stringResource(Res.string.account_browse), actions.onBrowse, AppButtonVariant.TEXT, enabled = !state.busy)),
        stackedActions = true,
        progress = if (inApp) null else OnboardingProgress(OnboardingStep.ACCOUNT, onboardingSteps(role), actions.onBack),
        onBack = if (inApp) actions.onBack else null,
    ) {
        Text(stringResource(if (inApp) Res.string.signup_app_title else Res.string.account_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(if (inApp) Res.string.signup_app_why else Res.string.account_why), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.signedInEmail != null) {
            Text(stringResource(Res.string.account_signed_in_as, state.signedInEmail), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        } else {
            // Google comes first; the name and the consent follow on this screen
            AppGoogleButton(
                text = stringResource(Res.string.account_google),
                onClick = actions.onGoogle,
                modifier = Modifier.fillMaxWidth(),
                enabled = idle,
            )
            AppDividerWithText(stringResource(Res.string.account_or_email))
        }
        // the family name comes first in Hungarian, the first name in Slovak
        val nameFields = listOf(AccountField.LAST_NAME, AccountField.FIRST_NAME).let { if (hungarianOrder()) it else it.reversed() }
        val nameField: @Composable (AccountField, Modifier) -> Unit = { field, modifier ->
            val last = field == AccountField.LAST_NAME
            AppTextField(
                value = if (last) state.lastName else state.firstName,
                onValueChange = { actions.onChanged(field, it) },
                label = stringResource(if (last) Res.string.account_last_name else Res.string.account_first_name),
                error = state.errors[field]?.text(field),
                enabled = idle,
                contentType = if (last) ContentType.PersonLastName else ContentType.PersonFirstName,
                modifier = modifier,
            )
        }
        if (state.signedInEmail != null) {
            // a signed-in account (Google) has only the name and the consents left: there is room, so one field under the other
            for (field in nameFields) nameField(field, Modifier)
        } else {
            // the two share a row, which keeps the e-mail form short enough that the consent below it is in view on opening
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
                for (field in nameFields) nameField(field, Modifier.weight(1f))
            }
        }
        if (state.signedInEmail == null) {
            AppTextField(
                value = state.email,
                onValueChange = { actions.onChanged(AccountField.EMAIL, it) },
                label = stringResource(Res.string.account_email),
                error = state.errors[AccountField.EMAIL]?.text(AccountField.EMAIL),
                keyboardType = KeyboardType.Email,
                enabled = idle,
                contentType = ContentType.EmailAddress,
            )
            // the two password fields sit together under one eye button, with the rule marks under both
            var passwordShown by rememberSaveable { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2)) {
                AppTextField(
                    value = state.password,
                    onValueChange = { actions.onChanged(AccountField.PASSWORD, it) },
                    label = stringResource(Res.string.signin_password),
                    error = state.errors[AccountField.PASSWORD]?.text(AccountField.PASSWORD),
                    password = true,
                    passwordShown = passwordShown,
                    onPasswordShownChange = { passwordShown = it },
                    enabled = idle,
                    contentType = ContentType.NewPassword,
                )
                AppTextField(
                    value = state.passwordRepeat,
                    onValueChange = { actions.onChanged(AccountField.PASSWORD_REPEAT, it) },
                    label = stringResource(Res.string.account_password_repeat),
                    error = state.errors[AccountField.PASSWORD_REPEAT]?.text(AccountField.PASSWORD_REPEAT),
                    password = true,
                    passwordShown = passwordShown,
                    onPasswordShownChange = { passwordShown = it },
                    enabled = idle,
                    contentType = ContentType.NewPassword,
                )
                PasswordRules(state.password)
            }
        }
        AppCheckboxRow(
            stringResource(Res.string.account_consent), state.consent, actions.onConsentChanged, enabled = idle,
            error = stringResource(Res.string.account_consent_required).takeIf { state.consentMissing },
        )
        AppCheckboxRow(stringResource(Res.string.account_news), state.news, actions.onNewsChanged, enabled = idle)
    }
}

/**
 * The three parts of the password rule under the password field: each is checked off as the typed password meets it, so
 * the user sees what is still missing before pressing the button.
 */
@Composable
private fun PasswordRules(password: String) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space1)) {
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

/** The 6-digit code from the sign-up e-mail. */
@Composable
private fun CodeStep(state: AccountUiState, email: String, role: UserRole?, actions: AccountActions, inApp: Boolean) {
    OnboardingScaffold(
        actions = listOf(
            OnboardingAction(stringResource(Res.string.code_confirm), actions.onConfirmCode, enabled = !state.busy, loading = state.busy),
            OnboardingAction(stringResource(Res.string.code_resend), actions.onResendCode, AppButtonVariant.TEXT, enabled = !state.busy),
        ),
        stackedActions = true,
        progress = if (inApp) null else OnboardingProgress(OnboardingStep.ACCOUNT, onboardingSteps(role), actions.onBackToForm),
        onBack = if (inApp) actions.onBackToForm else null,
    ) {
        CodeStepContent(
            title = stringResource(if (inApp) Res.string.signup_app_code_title else Res.string.code_title),
            sent = stringResource(if (inApp) Res.string.signup_app_code_sent else Res.string.code_sent, email),
            code = state.code,
            error = state.codeError?.text(null),
            enabled = !state.busy,
            onCodeChanged = actions.onCodeChanged,
        )
    }
}

/** True when the app language puts the family name first (Hungarian); Slovak puts the first name first. */
@Composable
internal fun hungarianOrder(): Boolean = LocalAppLocale.current.startsWith("hu", ignoreCase = true)

@Composable
@Preview
private fun AccountContentPreview() {
    AppTheme(darkTheme = false) { AccountContent(AccountUiState(consent = true), UserRole.STUDENT, AccountActions()) }
}
