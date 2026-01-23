package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.sp
import com.madovygabor.maturimate.core.theme.AppTheme
import com.madovygabor.maturimate.core.ui.utils.SurfaceContainer
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.welcome
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

// WelcomeScreen composable that serves as the onboarding welcome screen.
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

        // Define breakpoint for desktop size
        val windowHeight = this.maxHeight
        val isDesktop = maxWidth > isDesktopSize.size

        // Responsive font sizes based on device type
        val surfaceTitleFontSize = if (isDesktop) 26.sp else 18.sp
        val surfaceSubtitleFontSize = if (isDesktop) 22.sp else 16.sp

        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Title Text
            Text(
                text = "Üdvözöllek a MaturiMate-ben!",
                modifier = Modifier
                    .fillMaxWidth(.8f),
                style = TextStyle(
                    fontStyle = MaterialTheme.typography.titleLarge.fontStyle,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                maxLines = 2,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 20.sp,
                    maxFontSize = 55.sp
                )
            )

            // Spacer for vertical spacing
            Spacer(modifier = Modifier.weight(0.3f))

            // Content Surface
            SurfaceContainer(
                spaceBy = 8
            ) {

                Image(
                    painter = painterResource(Res.drawable.welcome),
                    contentDescription = "Üdvözlő kép",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = windowHeight * 0.35f)
                        .padding(vertical = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = "Hódítsd meg az érettségi csúcsát!",
                    style = TextStyle(
                        fontStyle = MaterialTheme.typography.titleMedium.fontStyle,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = Bold,
                    ),
                    fontSize = surfaceTitleFontSize,
                    modifier = Modifier
                        .fillMaxWidth(),
                )

               Text(
                    text = "Tarts velünk, és juss el a célodig lépésről lépésre. A MaturiMate-tel a vizsga nem akadály, hanem egy újabb győzelem.",
                    style = TextStyle(
                        fontStyle = MaterialTheme.typography.bodyMedium.fontStyle,
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                   fontSize = surfaceSubtitleFontSize,
                   modifier = Modifier
                       .fillMaxWidth(),
               )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Start Button
            Button(
                onClick = { onNavigateToProfileSetup() },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(bottom = 16.dp)
                    .height(56.dp)
            ) {
                Text(text = "Kezdjük el!", style = MaterialTheme.typography.titleMedium )
            }
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