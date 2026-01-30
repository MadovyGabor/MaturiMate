package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.madovygabor.maturimate.core.presentation.utils.LayoutConstants
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.uiComponents.SurfaceContainer
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.welcome
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The initial landing screen of the application.
 * Introduces the user to MaturiMate and provides a clear call-to-action to begin setup.
 *
 * @param onNavigateToProfileSetup Callback to advance the user to the profile configuration screen.
 */
@Composable
fun WelcomeScreen(
    onNavigateToProfileSetup: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        val windowHeight = this.maxHeight
        val isDesktop = maxWidth > LayoutConstants.DesktopThreshold

        // Responsive font size scales
        val surfaceTitleFontSize = if (isDesktop) 26.sp else 18.sp
        val surfaceSubtitleFontSize = if (isDesktop) 22.sp else 16.sp

        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main App Title with adaptive sizing
            BasicText(
                text = "Üdvözöllek a MaturiMate-ben!",
                modifier = Modifier.fillMaxWidth(0.8f),
                style = MaterialTheme.typography.headlineLarge.copy(
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = Bold,
                    lineHeight = 1.em
                ),
                maxLines = 2,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 24.sp,
                    maxFontSize = 55.sp
                )
            )

            // Flexible spacer to push content towards the center
            Spacer(modifier = Modifier.weight(0.4f))

            // Highlighted Info Section
            SurfaceContainer(
                verticalSpacing = 8.dp,
            ) {
                // Featured Welcome Illustration
                Image(
                    painter = painterResource(Res.drawable.welcome),
                    contentDescription = "Welcome Illustration",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = windowHeight * 0.35f)
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = "Hódítsd meg az érettségi csúcsát!",
                    style = TextStyle(
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = Bold,
                    ),
                    fontSize = surfaceTitleFontSize,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Tarts velünk, és juss el a célodig lépésről lépésre. A MaturiMate-tel a vizsga nem akadály, hanem egy újabb győzelem.",
                    style = TextStyle(
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    fontSize = surfaceSubtitleFontSize,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Pushes the button to the bottom
            Spacer(modifier = Modifier.weight(1f))

            // Primary Call-to-Action
            Button(
                onClick = onNavigateToProfileSetup,
                modifier = Modifier
                    .widthIn(max = 350.dp)
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Kezdjük el!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = Bold
                )
            }

            // Bottom safety margin
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, widthDp = 1920, heightDp = 1080)
private fun OnboardingScreenPreview() {
    AppTheme(darkTheme = false) {
        WelcomeScreen(
            onNavigateToProfileSetup = {}
        )
    }
}