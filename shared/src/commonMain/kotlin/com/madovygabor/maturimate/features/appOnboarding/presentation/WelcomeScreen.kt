package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppLogo
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.welcome_have_account
import maturimate.shared.generated.resources.welcome_headline
import maturimate.shared.generated.resources.welcome_start
import maturimate.shared.generated.resources.welcome_tagline
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

/**
 * First screen of the onboarding: a medium logo, one headline and one line about what the app does, calm and centered.
 * "Kezdjük" is the one main action; "Már van fiókom"
 * is the quiet one under it.
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
        centered = true,
    ) {
        val gold = MaterialTheme.extendedColors.brandGold
        Column(
            // a little bottom padding lifts the group to the optical center, a bit above the middle
            modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.space8, bottom = MaterialTheme.spacing.space8 * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space4),
        ) {
            // a soft gold glow behind the logo; it is only painted, so it takes no room
            AppLogo(
                size = MaterialTheme.layout.logoLarge,
                contentDescription = null,
                modifier = Modifier.drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(gold.copy(alpha = 0.22f), Color.Transparent), radius = size.minDimension * 0.75f),
                        radius = size.minDimension * 0.75f,
                    )
                },
            )
            Text(
                text = stringResource(Res.string.welcome_headline),
                style = MaterialTheme.typography.displaySmall,
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
