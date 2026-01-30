package com.madovygabor.maturimate.features.profileDetails.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.madovygabor.maturimate.features.profileDetails.presentation.SubjectStat

/**
 * A responsive section displaying progress statistics for different subjects.
 * It switches between a horizontal Row for desktop and a wrapping FlowRow for mobile.
 *
 * @param modifier Custom modifier for external layout adjustments.
 * @param isDesktop Boolean flag to toggle between desktop and mobile layouts.
 * @param stats List of [SubjectStat] objects containing progress and labels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectStatsSection(
    modifier: Modifier = Modifier,
    isDesktop: Boolean = false,
    stats: List<SubjectStat>,
) {
    if (isDesktop) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            stats.forEach { stat ->
                StatItemVertical(
                    stat = stat,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        FlowRow(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            maxItemsInEachRow = 2
        ) {
            stats.forEach { stat ->
                StatItemVertical(
                    stat = stat,
                    modifier = Modifier.fillMaxWidth(0.47f),
                    fontSize = 18.sp,
                    circleSize = 80.dp
                )
            }
        }
    }
}

@Composable
private fun StatItemVertical(
    stat: SubjectStat,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    circleSize: Dp = 100.dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        CircularStat(
            stat = stat,
            size = circleSize,
            fontSize = (fontSize.value * 0.95f).sp // Cleaner scaling
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stat.subject.name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontSize = fontSize,
            lineHeight = 1.1.em,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
fun CircularStat(
    stat: SubjectStat,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
    fontSize: TextUnit = 18.sp
) {
    // Semantic color logic for better readability
    val progressColor = when {
        stat.progress > 0.75f -> Color(0xFF4CAF50) // Green
        stat.progress >= 0.33f -> Color(0xFFFBC02D) // Amber
        else -> Color(0xFFE53935) // Red
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            progress = { stat.progress },
            color = progressColor,
            trackColor = progressColor.copy(alpha = 0.2f),
            strokeWidth = 6.dp,
            strokeCap = StrokeCap.Round
        )
        Text(
            text = stat.scoreText,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        )
    }
}