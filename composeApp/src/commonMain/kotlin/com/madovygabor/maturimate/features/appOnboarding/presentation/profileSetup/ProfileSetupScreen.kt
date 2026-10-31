package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madovygabor.maturimate.core.presentation.utils.LayoutConstants
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.uiComponents.DatePickerField
import com.madovygabor.maturimate.core.presentation.uiComponents.SurfaceContainer
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingViewModel
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.components.LanguageSelector
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.ui.tooling.preview.Preview

// The main Profile Setup Screen Composable
@Composable
fun ProfileSetupScreen(
    viewModel: OnboardingViewModel,
    onProfileSetupComplete: () -> Unit,
) {
   // Obtain the UI state from the ViewModel
    val uiState by viewModel.profileSetupUiState.collectAsState()

   // Pass the UI state and event handlers to the content composable
    ProfileSetupContent(
        uiState = uiState,
        onLanguageSelected = { viewModel.onLanguageSelected(it) },
        onDateSelected = { viewModel.onDateSelected(it) },
        onProfileSetupComplete = {
            viewModel.completeOnboarding(
                onSuccess = (onProfileSetupComplete)
            )
        }
    )
}


// The main content composable for the Profile Setup Screen
@Composable
fun ProfileSetupContent(
    uiState: ProfileSetupUiState,
    onLanguageSelected: (TeachingLanguage) -> Unit,
    onDateSelected: (LocalDate?) -> Unit,
    onProfileSetupComplete: () -> Unit,
){
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),

        contentAlignment = Alignment.TopCenter
    ) {
        // Determine if the device is a desktop based on maxHeight
        val isDesktop = maxWidth > LayoutConstants.DesktopThreshold

        // Adjust text styles based on device type
        val titleFontSize = if (isDesktop) 38.sp else 30.sp

        // Surface text sizes
        val surfaceTitleFontSize = if (isDesktop) 20.sp else 16.sp


        // Main Content
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)

        ) {
            Text(
                text = "Szabd magadra a felkészülést!",
                modifier = Modifier
                    .fillMaxWidth(.8f)
                    .padding(bottom = if (isDesktop) 16.dp else 0.dp),
                style = TextStyle(
                    fontStyle = MaterialTheme.typography.titleLarge.fontStyle,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                fontSize = titleFontSize,
            )

            // Language Selection section
            SurfaceContainer(
                modifier = Modifier
                    .fillMaxWidth(),
                content = {
                    Text(
                        text = "Milyen tagozaton érettségizel?",
                        style = TextStyle(
                            fontStyle = MaterialTheme.typography.titleMedium.fontStyle,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = Bold,
                        ),
                        fontSize = surfaceTitleFontSize,
                        modifier = Modifier
                            .fillMaxWidth()
                    )

                    LanguageSelector(
                        selectedLanguage = uiState.selectedLanguage,
                        onLanguageSelected = { onLanguageSelected(it) }
                    )
                }
            )

            // Exam Date Selection section
            SurfaceContainer(
                modifier = Modifier
                    .fillMaxWidth(),
                content = {
                    Text(
                        text = "Mikor érettségizel?",
                        style = TextStyle(
                            fontStyle = MaterialTheme.typography.titleMedium.fontStyle,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = Bold,
                        ),
                        fontSize = surfaceTitleFontSize,
                        modifier = Modifier
                            .fillMaxWidth()
                    )

                    DatePickerField(
                        label = "Érettségi dátuma",
                        placeholder = "Dátum kiválasztása",
                        selectedDate = uiState.examDate,
                        onDateSelected = { onDateSelected(it) }
                    )
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button - Now part of the column for better scrolling and sizing
            Button(
                onClick = onProfileSetupComplete,
                modifier = Modifier
                    .widthIn(max = 400.dp) // Limits button width on Desktop
                    .fillMaxWidth(0.75f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Tovább a feladatokhoz!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = Bold
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, widthDp = 1920, heightDp = 1080)
private fun OnboardingScreenPreview() {
    AppTheme(darkTheme = true) {
        ProfileSetupContent(
            uiState = ProfileSetupUiState(),
            onLanguageSelected = {},
            onDateSelected = {},
            onProfileSetupComplete = {}

        )
    }

}