package com.madovygabor.maturimate.features.subjectsAndExams.presentation.examSelection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madovygabor.maturimate.core.presentation.utils.SubjectStaticData
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.LoadingScreenWrapper
import com.madovygabor.maturimate.core.presentation.utils.Subject
import com.madovygabor.maturimate.features.subjectsAndExams.domain.model.Exam
import com.madovygabor.maturimate.features.subjectsAndExams.presentation.SubjectViewModel
import com.madovygabor.maturimate.features.subjectsAndExams.presentation.subjectSelection.SubjectSelectionUiState
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ExamSelectionScreen(
    subjectId: String,
    viewModel: SubjectViewModel,
    onExamClick: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    // Amikor a képernyőre lépünk, betöltjük az adott tantárgy vizsgáit
    LaunchedEffect(subjectId) {
        viewModel.loadExamsForUiSubject(subjectId)
    }

    ExamSelectionContent(
        subjectId = subjectId,
        state = state,
        onExamClick = onExamClick
    )
}


@Composable
fun ExamSelectionContent(
    subjectId: String,
    state: SubjectSelectionUiState,
    onExamClick: (String) -> Unit
) {
    val subject = SubjectStaticData.getById(subjectId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.background),
    ) {
        // --- FEJLÉC (Subject Header) ---
        ExamSelectionHeader(subject)

        // Tantárgy színe szerinti elválasztó
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = subject.color,
            thickness = 3.dp
        )

        // --- TARTALOM (Loading / List / Empty) ---
        Box(modifier = Modifier.fillMaxSize()) {

            LoadingScreenWrapper(
                isLoading = state.isLoading,
            ){
                if (state.filteredExams.isEmpty()){
                    Text(
                        text = "Még nincsenek elérhető vizsgák.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else{
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        items(state.filteredExams) { exam ->
                            ExamListItem(
                                exam = exam,
                                subjectColor = subject.color,
                                onClick = { onExamClick(exam.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExamSelectionHeader(subject: Subject) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(65.dp)
                .background(subject.color.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = subject.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }

        BasicText(
            text = subject.name,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 12.sp,
                maxFontSize = 25.sp
            )
        )
    }
}

@Composable
fun ExamListItem(
    exam: Exam,
    subjectColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Évszám ikon box
            Box(
                modifier = Modifier
                    .size(55.dp)
                    .background(subjectColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Assignment,
                    contentDescription = null,
                    tint = subjectColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = "${exam.year}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Vizsga típusának (Session) feloldása
                Text(
                    text = when(exam.session) {
                        "MAY" -> "Májusi rendes vizsga"
                        "SEPTEMBER" -> "Szeptemberi pót/javító"
                        else -> "Írásbeli vizsga"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


//@Composable
//fun ExamSelectionScreen(
//    // TODO: Use Koin to inject the ViewModel
//    subjectId: String,
//    modifier: Modifier = Modifier
//) {
//
//    val subject = SubjectStaticData.getById(subjectId)
//
//    Column(
//        modifier = modifier
//            .fillMaxSize()
//            .background(color = MaterialTheme.colorScheme.background),
//    ) {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
//                .padding(16.dp),
//
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.spacedBy(16.dp)
//        ){
//            Box(
//                modifier = Modifier
//                    .size(65.dp)
//                    .background(subject.color.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
//                contentAlignment = Alignment.Center
//            ) {
//                Icon(
//                    imageVector = subject.icon, // Itt bármilyen ikont használhatsz
//                    contentDescription = null,
//                    tint = MaterialTheme.colorScheme.inverseSurface,
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .padding(16.dp),
//                )
//            }
//
//            BasicText(
//                text = subject.name,
//                style = TextStyle(
//                    fontStyle = MaterialTheme.typography.titleMedium.fontStyle,
//                    fontWeight = FontWeight.Bold,
//                    color = MaterialTheme.colorScheme.onSurface,
//                ),
//                maxLines = 1,
//                autoSize = TextAutoSize.StepBased(
//                    minFontSize = 12.sp,
//                    maxFontSize = 25.sp
//                )
//            )
//
//
//        }
//
//        HorizontalDivider(
//            modifier = Modifier
//                .fillMaxWidth(),
//            color = subject.color,
//            thickness = 3.dp
//        )
//
//
//        LazyColumn(
//            modifier = Modifier.fillMaxWidth()
//        ) {
//            for (i in 1..9) {
//                item {
//                    ExamListItem(
//                        examId = "201$i",
//                        subjectColor = subject.color
//                    )
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun ExamListItem(
//    examId: String,
//    subjectColor: Color,
//    modifier: Modifier = Modifier
//) {
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(8.dp,end= 8.dp, top = 8.dp),
//
//        shape = RoundedCornerShape(8.dp),
//        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
//        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
//    ) {
//        Row(
//            modifier = Modifier
//                .fillMaxSize()
//                .size(85.dp)
//                .padding(8.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//
//            Box(
//                modifier = Modifier
//                    .size(55.dp)
//                    .background(subjectColor.copy(alpha = 0.3f),
//                        RoundedCornerShape(8.dp)),
//                contentAlignment = Alignment.Center,
//
//            ) {
//                Icon(
//                    imageVector = Icons.AutoMirrored.Outlined.Assignment, // Itt bármilyen ikont használhatsz
//                    contentDescription = null,
//                    tint = MaterialTheme.colorScheme.inverseSurface,
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .padding(8.dp)
//                )
//            }
//
//            Column(
//                modifier = Modifier
//                    .weight(1f)
//                    .padding(start = 16.dp)
//            ) {
//                BasicText(
//                    text = examId,
//                    style = TextStyle(
//                        fontStyle = MaterialTheme.typography.titleLarge.fontStyle,
//                        fontWeight = FontWeight.Bold,
//                        color = MaterialTheme.colorScheme.onSurface,
//                        fontSize = 20.sp,
//
//                    ),
//                )
//                BasicText(
//                    text = "Temporary description for exam item dsfdddsdssddsdsdsds.",
//                    style = TextStyle(
//                        fontStyle = MaterialTheme.typography.bodyMedium.fontStyle,
//                        fontWeight = FontWeight.Normal,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant,
//                    ),
//                    modifier = Modifier.padding(top = 4.dp),
//                    maxLines = 2,
//                    overflow = TextOverflow.Ellipsis
//                )
//            }
//
//            Icon(
//                imageVector = Icons.Default.ChevronRight, // Itt bármilyen ikont használhatsz
//                contentDescription = null,
//                tint = MaterialTheme.colorScheme.inverseSurface,
//                modifier = Modifier
//                    .padding(8.dp)
//            )
//
//        }
//
//    }
//
//}



@Composable
@Preview(showBackground = true)
private fun ExamSelectionScreenPreview() {
    AppTheme(darkTheme = false) {
        ExamSelectionContent(
            subjectId = "HU_MJL",
            state = SubjectSelectionUiState(
                filteredExams = listOf(
                    Exam(
                        id = "exam1",
                        subjectId = "math_standard",
                        year = 2022,
                        session = "MAY",
                        totalMinutes = 120,
                        vpuMinutes = 150,
                        taskCount = 10
                    )
                )

            ),
            onExamClick = {}
        )
    }
}