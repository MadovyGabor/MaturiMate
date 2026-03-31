package com.madovygabor.maturimate.features.subjectsAndExams.presentation.subjectSelection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.Subject
import com.madovygabor.maturimate.core.presentation.utils.SubjectStaticData
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLanguage
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLevel
import com.madovygabor.maturimate.features.appOnboarding.domain.UserPreferences
import com.madovygabor.maturimate.features.subjectsAndExams.presentation.SubjectViewModel
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SubjectSelectionScreen(
    viewModel: SubjectViewModel,
    onSubjectClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    SubjectSelectionContent(
        state = state,
        onSubjectClick = onSubjectClick
    )
}



@Composable
fun SubjectSelectionContent(
    state: SubjectSelectionUiState,
    onSubjectClick: (String) -> Unit
) {
    val prefs = state.userPreferences

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        Text(
            text = "Tantárgyak",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 16.dp)
        )

        // Column-t használunk LazyColumn helyett a súlyozáshoz (weights)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)

        ) {
            state.subjects.forEach { subject ->
                // Javított logika: Csak akkor aktív, ha a preferenciák alapján van DB kódja
                val isEnabled = state.userPreferences?.let {
                    SubjectStaticData.getDbId(subject.uiId, it) != null
                } ?: false // Amíg tölt, nem kattintható

                val displayLevel = if (subject.uiId == "ENGLISH") {
                    prefs?.maturitaLevel?.name ?: subject.level
                } else subject.level

                // A Box kapja meg a weight(1f)-et, így kitöltik az űrt
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    SubjectCard(
                        subject = subject.copy(level = displayLevel),
                        isEnabled = isEnabled,
                        onClick = { onSubjectClick(subject.uiId) },
                        modifier = Modifier.fillMaxSize() // A kártya töltse ki a Box-ot
                    )
                }
            }
        }
    }
}

@Composable
fun SubjectCard(
    subject: Subject,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Ha a tárgy nem releváns (pl. SK tagozaton a magyar), elhalványítjuk
    val cardAlpha = if (isEnabled) 1f else 0.4f

    Card(
        modifier = modifier // Ide kerül a fillMaxSize() és az alpha is lehet itt
            .alpha(cardAlpha)
            .padding(horizontal = 8.dp)
            .clickable(enabled = isEnabled) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isEnabled) 2.dp else 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Felső színes dekorációs sáv
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.4f)
                    .height(12.dp)
                    .background(
                        color = if (isEnabled) subject.color else Color.Gray,
                        shape = RoundedCornerShape(bottomStart = 8.dp)
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ikon doboza (szürke, ha disabled)
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(70.dp)
                        .background(
                            color = if (isEnabled) subject.color.copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = subject.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }

                // Tantárgy neve auto-size szöveggel
                BasicText(
                    text = subject.name,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    maxLines = 2,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 14.sp,
                        maxFontSize = 20.sp
                    )
                )

                // Szint jelző (B2, B1 stb.), ha van
                subject.level?.let { level ->
                    Surface(
                        color = if (isEnabled) subject.color.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = level,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isEnabled) subject.color else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, widthDp = 1920, heightDp = 1080)
private fun SubjectListPreview() {
    AppTheme(darkTheme = false) {
        SubjectSelectionContent(
            state = SubjectSelectionUiState(
                subjects = SubjectStaticData.getAll(),
                userPreferences = UserPreferences(
                    MaturitaLanguage.HU,
                    MaturitaLevel.B1,
                    maturitaDate = LocalDate(2024, 6, 15),
                    isVpuEnabled = false,
                    isSetupCompleted = true
                )
            ),
            onSubjectClick = {}
        )
    }
}