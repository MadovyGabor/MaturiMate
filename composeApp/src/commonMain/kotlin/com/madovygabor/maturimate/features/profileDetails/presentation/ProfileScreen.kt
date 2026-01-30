package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madovygabor.maturimate.core.presentation.MainFrame
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.uiComponents.DatePickerField
import com.madovygabor.maturimate.core.presentation.uiComponents.EditDialog
import com.madovygabor.maturimate.core.presentation.utils.SubjectStaticData
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLanguage
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLevel
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.components.LanguageSelector
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.components.MaturitaLevelSelector
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.toUiText
import com.madovygabor.maturimate.features.profileDetails.presentation.components.ProfileDetailItem
import com.madovygabor.maturimate.features.profileDetails.presentation.components.ProfileIcon
import com.madovygabor.maturimate.features.profileDetails.presentation.components.SubjectStatsSection
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.ui.tooling.preview.Preview
import toFormattedDateString

/**
 * The main entry point for the Profile screen.
 * It manages the lifecycle of the UI state and handles the display of
 * edit dialogs based on the active [EditType].
 *
 * @param viewModel The [ProfileViewModel] providing state and business logic.
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel
){
    val uiState by viewModel.profileUiState.collectAsState()

    ProfileScreenContent(
        uiState = uiState,
        onMauritaDateChangeRequested = { viewModel.onEditing(EditType.DATE) },
        onMaturitaLanguageChangeRequested = { viewModel.onEditing(EditType.LANGUAGE) },
        onMaturitaLevelChangeRequested = { viewModel.onEditing(EditType.LEVEL) }
    )

    // Dialog management based on EditType state
    when (uiState.editType) {
        EditType.DATE -> {
            EditDialog(
                title = "Érettségi dátuma",
                onDismiss = { viewModel.dismissDialog() },
                onConfirm = { viewModel.saveChanges() }
            ) {
                DatePickerField(
                    label = "Válaszd ki a napot",
                    selectedDate = uiState.draftMaturitaDate,
                    onDateSelected = { viewModel.onDraftDateChanged(it) }
                )
            }
        }

        EditType.LANGUAGE -> {
            EditDialog(
                title = "Tagozat módosítása",
                onDismiss = { viewModel.dismissDialog() },
                onConfirm = { viewModel.saveChanges() }
            ) {
                // Egy egyszerű választó sor (pl. Segmented Button vagy Row)
                LanguageSelector(
                    selectedLanguage = uiState.draftMaturitaLanguage,
                    onLanguageSelected = { viewModel.onDraftLanguageChanged(it) }
                )
            }
        }

        EditType.LEVEL -> {
            EditDialog(
                title = "Angol szint módosítása",
                onDismiss = { viewModel.dismissDialog() },
                onConfirm = { viewModel.saveChanges() }
            ) {
                MaturitaLevelSelector(
                    selectedLevel = uiState.draftMaturitaLevel,
                    onLevelSelected = { viewModel.onDraftLevelChanged(it) }
                )
            }
        }

        EditType.NONE -> { /* No dialog to show*/ }
    }

}

/**
 * A stateless UI component that renders the profile content.
 * Features a responsive layout that adapts between mobile and desktop widths.
 */
@Composable
fun ProfileScreenContent(
    uiState: ProfileUiState,
    onMauritaDateChangeRequested: () -> Unit = {},
    onMaturitaLanguageChangeRequested: () -> Unit = {},
    onMaturitaLevelChangeRequested: () -> Unit = {}
){
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {

        // Breakpoints for adaptive UI
        val isDesktop = 600.dp < this.maxWidth

        Column(
            modifier = Modifier
                .widthIn(max = 850.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Adaptive Header section
            if (isDesktop) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Info items with consistent styling
                    ProfileHeader()

                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "${uiState.daysLeft}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 40.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )

                        Text(
                            text = "nap van hátra",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            } else {
                ProfileHeader()
            }

            // Info items with consistent styling
            ProfileDetailItem(
                label = "Az érettségi dátuma",
                value = uiState.maturitaDate?.toFormattedDateString() ?: "Nincs beállítva",
                icon = Icons.Default.Event,
                onClick = { onMauritaDateChangeRequested() }
            )

            //
            ProfileDetailItem(
                label = "Választott tagozat",
                value = uiState.maturitaLanguage.toUiText().asString(),
                icon = Icons.Default.Translate,
                onClick = { onMaturitaLanguageChangeRequested() }
            )


            ProfileDetailItem(
                label = "Választott angol szint",
                value = uiState.maturitaLevel.toUiText().asString(),
                icon = Icons.Default.School,
                onClick = { onMaturitaLevelChangeRequested() }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                thickness = 1.dp,
            )

            Text(
                text = "Tantargyak statisztikái",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )

            SubjectStatsSection(
                isDesktop = isDesktop,
                stats = uiState.subjectStats
            )

        }
    }
}


@Composable
fun ProfileHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {

        ProfileIcon()

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "Profilom",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground // Warm Grey
        )
    }
}

@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, widthDp = 1920, heightDp = 1080)
private fun ProfileScreenPreview() {
    AppTheme(darkTheme = false) {

        MainFrame(
            selectedKey = Route.Main.Profile,
            onSelectedKey = { /*TODO*/ },
        ){
            ProfileScreenContent(
                uiState = ProfileUiState(
                    maturitaDate = LocalDate(2024, 6, 15),
                    daysLeft = 120,
                    editType = EditType.DATE,
                    maturitaLevel = MaturitaLevel.B2,
                    maturitaLanguage = MaturitaLanguage.HU,
                    subjectStats = listOf(
                        SubjectStat(
                            subject = SubjectStaticData.getById("ENGLISH"),
                            progress = 0.75f,
                            scoreText = "85%"
                        ),
                        SubjectStat(
                            subject = SubjectStaticData.getById("MATH"),
                            progress = 0.50f,
                            scoreText = "60%"
                        ),
                        SubjectStat(
                            subject = SubjectStaticData.getById("HUNGARIAN"),
                            progress = 0.90f,
                            scoreText = "92%"
                        ),
                        SubjectStat(
                            subject = SubjectStaticData.getById("SLOVAK"),
                            progress = 0.30f,
                            scoreText = "45%"
                        )
                    )
                )
            )
        }

    }
}
