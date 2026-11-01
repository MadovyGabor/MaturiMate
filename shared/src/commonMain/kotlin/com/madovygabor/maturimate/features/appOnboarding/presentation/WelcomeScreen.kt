package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppLogo
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.app_name
import maturimate.shared.generated.resources.welcome_have_account
import maturimate.shared.generated.resources.welcome_start
import maturimate.shared.generated.resources.welcome_tagline
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * First screen of the onboarding: the logo, one sentence on what the app gives,
 * "Kezdjük" to start the questions and "Már van fiókom" to sign in.
 *
 * @param onStart Starts the onboarding questions.
 * @param onHaveAccount Opens the sign-in; the button is hidden while this is null
 *   (until the sign-in screen exists, roadmap step 5.12).
 */
@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    onHaveAccount: (() -> Unit)?,
) {
    val start = OnboardingAction(stringResource(Res.string.welcome_start), onStart)
    val haveAccount = onHaveAccount?.let {
        OnboardingAction(stringResource(Res.string.welcome_have_account), it, AppButtonVariant.TEXT)
    }
    OnboardingScaffold(
        actions = listOfNotNull(start, haveAccount),
        stackedActions = true,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3),
        ) {
            AppLogo(size = MaterialTheme.layout.logoLarge, contentDescription = null)
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.welcome_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
@Preview
private fun WelcomeScreenLightPreview() {
    AppTheme(darkTheme = false) { WelcomeScreen(onStart = {}, onHaveAccount = {}) }
}

@Composable
@Preview
private fun WelcomeScreenDarkPreview() {
    AppTheme(darkTheme = true) { WelcomeScreen(onStart = {}, onHaveAccount = {}) }
}
