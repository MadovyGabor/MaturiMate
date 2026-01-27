package com.madovygabor.maturimate.features.subjects.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madovygabor.maturimate.core.presentation.SubjectStaticData
import com.madovygabor.maturimate.core.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ExamSelectionScreen(
    // TODO: Use Koin to inject the ViewModel
    subjectId: String,
    modifier: Modifier = Modifier
) {

    val subject = SubjectStaticData.getById(subjectId)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),

            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ){
            Box(
                modifier = Modifier
                    .size(65.dp)
                    .background(subject.color.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = subject.icon, // Itt bármilyen ikont használhatsz
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                )
            }

            BasicText(
                text = subject.name,
                style = TextStyle(
                    fontStyle = MaterialTheme.typography.titleMedium.fontStyle,
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

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth(),
            color = subject.color,
            thickness = 3.dp
        )


        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            for (i in 1..9) {
                item {
                    ExamListItem(
                        examId = "201$i",
                        subjectColor = subject.color
                    )
                }
            }
        }
    }
}

@Composable
fun ExamListItem(
    examId: String,
    subjectColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp,end= 8.dp, top = 8.dp),

        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .size(85.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(55.dp)
                    .background(subjectColor.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,

            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Assignment, // Itt bármilyen ikont használhatsz
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                BasicText(
                    text = examId,
                    style = TextStyle(
                        fontStyle = MaterialTheme.typography.titleLarge.fontStyle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,

                    ),
                )
                BasicText(
                    text = "Temporary description for exam item dsfdddsdssddsdsdsds.",
                    style = TextStyle(
                        fontStyle = MaterialTheme.typography.bodyMedium.fontStyle,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight, // Itt bármilyen ikont használhatsz
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier
                    .padding(8.dp)
            )

        }

    }

}

@Composable
@Preview(showBackground = true)
private fun ExamSelectionScreenPreview() {
    AppTheme(darkTheme = false) {
        ExamSelectionScreen(
            subjectId = "MATH"
        )
    }

}