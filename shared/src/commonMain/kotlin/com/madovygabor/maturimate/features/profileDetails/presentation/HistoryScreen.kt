package com.madovygabor.maturimate.features.profileDetails.presentation

import maturimate.shared.generated.resources.history_filter_practice
import com.madovygabor.maturimate.features.learning.presentation.activityPlace
import com.madovygabor.maturimate.features.learning.domain.ActivityKind
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceChip
import com.madovygabor.maturimate.features.learning.presentation.LoadError
import com.madovygabor.maturimate.features.profileDetails.domain.HistoryGroup
import com.madovygabor.maturimate.features.profileDetails.domain.HistorySection
import kotlinx.datetime.LocalDate
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_back
import maturimate.shared.generated.resources.history_days_ago
import maturimate.shared.generated.resources.history_filter_all
import maturimate.shared.generated.resources.history_filter_empty
import maturimate.shared.generated.resources.history_filter_lessons
import maturimate.shared.generated.resources.history_filter_tests
import maturimate.shared.generated.resources.history_month_year
import maturimate.shared.generated.resources.history_weeks_ago
import maturimate.shared.generated.resources.home_when_today
import maturimate.shared.generated.resources.home_when_yesterday
import maturimate.shared.generated.resources.month_1
import maturimate.shared.generated.resources.month_10
import maturimate.shared.generated.resources.month_11
import maturimate.shared.generated.resources.month_12
import maturimate.shared.generated.resources.month_2
import maturimate.shared.generated.resources.month_3
import maturimate.shared.generated.resources.month_4
import maturimate.shared.generated.resources.month_5
import maturimate.shared.generated.resources.month_6
import maturimate.shared.generated.resources.month_7
import maturimate.shared.generated.resources.month_8
import maturimate.shared.generated.resources.month_9
import maturimate.shared.generated.resources.profile_history_empty
import maturimate.shared.generated.resources.profile_history_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime

/** The full history: every finished lesson and test round under day headings, with a filter. */
@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = koinViewModel()) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsState()
    HistoryContent(state, onBack, onFilter = viewModel::select, onRetry = viewModel::refresh)
}

@OptIn(ExperimentalTime::class)
@Composable
fun HistoryContent(state: HistoryUiState, onBack: () -> Unit, onFilter: (HistoryFilter) -> Unit, onRetry: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = MaterialTheme.layout.contentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.common_back))
                }
                Text(
                    stringResource(Res.string.profile_history_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            when (state) {
                HistoryUiState.Loading -> Unit
                HistoryUiState.Failed -> LoadError(onRetry)
                is HistoryUiState.Ready -> Ready(state, onFilter)
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun Ready(state: HistoryUiState.Ready, onFilter: (HistoryFilter) -> Unit) {
    val spacing = MaterialTheme.spacing
    // four chips; on a narrow phone with the longer Slovak labels the row scrolls sideways
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = spacing.space2), horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
        AppChoiceChip(stringResource(Res.string.history_filter_all), selected = state.filter == HistoryFilter.ALL, onClick = { onFilter(HistoryFilter.ALL) })
        AppChoiceChip(stringResource(Res.string.history_filter_lessons), selected = state.filter == HistoryFilter.LESSONS, onClick = { onFilter(HistoryFilter.LESSONS) })
        AppChoiceChip(stringResource(Res.string.history_filter_tests), selected = state.filter == HistoryFilter.TESTS, onClick = { onFilter(HistoryFilter.TESTS) })
        AppChoiceChip(stringResource(Res.string.history_filter_practice), selected = state.filter == HistoryFilter.PRACTICE, onClick = { onFilter(HistoryFilter.PRACTICE) })
    }
    val sections = state.history.sections.mapNotNull { section ->
        val shown = section.activities.filter {
            when (state.filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.LESSONS -> it.kind == ActivityKind.LESSON
                HistoryFilter.TESTS -> it.kind == ActivityKind.TEST
                HistoryFilter.PRACTICE -> it.kind.isPractice
            }
        }
        if (shown.isEmpty()) null else HistorySection(section.group, shown)
    }
    if (sections.isEmpty()) {
        val empty = state.history.sections.isEmpty()
        EmptyNote(stringResource(if (empty) Res.string.profile_history_empty else Res.string.history_filter_empty))
        return
    }
    sections.forEachIndexed { index, section ->
        Column(
            Modifier.padding(top = if (index == 0) spacing.space1 else spacing.space3),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            SectionTitle(groupLabel(section.group, state.history.today))
            section.activities.forEach { ActivityItem(it, meta = activityPlace(it)) }
        }
    }
}

/** The heading of a group: "Ma", "Tegnap", "3 napja", "1 hete", "Október", or "2025. október" in another year. */
@Composable
private fun groupLabel(group: HistoryGroup, today: LocalDate): String = when (group) {
    HistoryGroup.Today -> stringResource(Res.string.home_when_today)
    HistoryGroup.Yesterday -> stringResource(Res.string.home_when_yesterday)
    is HistoryGroup.DaysAgo -> pluralStringResource(Res.plurals.history_days_ago, group.days, group.days)
    is HistoryGroup.WeeksAgo -> pluralStringResource(Res.plurals.history_weeks_ago, group.weeks, group.weeks)
    is HistoryGroup.InMonth -> {
        val month = stringResource(MONTHS[group.month.ordinal])
        // "2025. október" in Hungarian, "Október 2025" in Slovak: the month in lower case, the heading's first letter in capital
        if (group.year == today.year) month
        else stringResource(Res.string.history_month_year, month.lowercase(), group.year).replaceFirstChar { it.uppercase() }
    }
}

private val MONTHS = listOf(
    Res.string.month_1, Res.string.month_2, Res.string.month_3, Res.string.month_4, Res.string.month_5, Res.string.month_6,
    Res.string.month_7, Res.string.month_8, Res.string.month_9, Res.string.month_10, Res.string.month_11, Res.string.month_12,
)
