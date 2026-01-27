package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import com.madovygabor.maturimate.core.presentation.LayoutConstants
import com.madovygabor.maturimate.core.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.DatePickerField
import com.madovygabor.maturimate.core.presentation.utils.SurfaceContainer
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingViewModel
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
        onVpuToggled = { viewModel.onVpuToggled(it) },
        onLevelSelected = { viewModel.onLevelSelected(it) },
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
    onLanguageSelected: (String) -> Unit,
    onLevelSelected: (String) -> Unit,
    onDateSelected: (Long?) -> Unit,
    onVpuToggled: (Boolean) -> Unit,
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
        val surfaceSubtitleFontSize = if (isDesktop) 16.sp else 14.sp


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
                            .padding(bottom = 12.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ){

                        LanguageCard(
                            name = "Magyar",
                            flag = "🇭🇺", //TODO: Use image not emoji
                            isSelected = uiState.selectedLanguage == "HU",
                            onClick = {
                                onLanguageSelected("HU")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )

                        LanguageCard(
                            name = "Szlovák",
                            flag = "🇸🇰", //TODO: Use image not emoji
                            isSelected = uiState.selectedLanguage == "SK",
                            onClick = { onLanguageSelected("SK") },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        )
                    }
                }
            )

            //English Exam Level Selection section
            SurfaceContainer(
                modifier = Modifier
                    .fillMaxWidth(),
                content = {
                    Text(
                        text = "Milyen szintű angol vizsgára készülsz?",
                        style = TextStyle(
                            fontStyle = MaterialTheme.typography.titleMedium.fontStyle,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = Bold,
                        ),
                        fontSize = surfaceTitleFontSize,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )


                    val options = listOf("B1", "B2")

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        options.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = options.size
                                ),
                                selected = uiState.examLevel == label,
                                onClick = {
                                    onLevelSelected(label)
                                },
                                label = {
                                    Text(label)
                                }
                            )
                        }
                    }
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
                            .padding(bottom = 12.dp),
                    )

                    DatePickerField(
                        label = "Érettségi dátuma",
                        placeholder = "Dátum kiválasztása",
                        selectedDateMillis = uiState.examDate,
                        onDateSelected = { onDateSelected(it) }
                    )
                }
            )

            // VPUS Support Section
            SurfaceContainer(
                modifier = Modifier
                    .fillMaxWidth(),
                content = {
                    Text(
                        text = "Igényelsz VPUS támogatást?",
                        style = TextStyle(
                            fontStyle = MaterialTheme.typography.titleMedium.fontStyle,
                            textAlign = TextAlign.Start,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = Bold,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        fontSize = surfaceTitleFontSize,
                    )

                    Row{
                        Text(
                            text = "Kapcsold be a személyre szabott módszertant és a több időt. Ideális segítség az SNI/VPUS diákok számára.",
                            style = TextStyle(
                                fontStyle = MaterialTheme.typography.bodyMedium.fontStyle,
                                textAlign = TextAlign.Start,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.8f),
                            fontSize = surfaceSubtitleFontSize,
                        )

                        Switch(
                            modifier = Modifier.weight(0.2f).padding(start = 8.dp),
                            checked = uiState.isVpuEnabled,
                            onCheckedChange = { onVpuToggled(it) }
                        )

                    }

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



// Language selection card
@Composable
fun LanguageCard(
    name: String,
    flag: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        // Content of the card
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = flag, fontSize = 32.sp) // Emoji vagy Image
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
            // Show check icon if selected
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
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
            onLevelSelected = {},
            onLanguageSelected = {},
            onVpuToggled = {},
            onDateSelected = {},
            onProfileSetupComplete = {}

        )
    }

}