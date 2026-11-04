package com.madovygabor.maturimate.features.learning.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.extendedTypography
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppActivityCard
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppGoalRing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppInfoChip
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTag
import com.madovygabor.maturimate.core.presentation.uiComponents.AppWeekStrip
import com.madovygabor.maturimate.core.presentation.uiComponents.TagStyle
import com.madovygabor.maturimate.core.presentation.uiComponents.WeekDotState
import com.madovygabor.maturimate.core.presentation.uiComponents.WeekStripDay
import com.madovygabor.maturimate.features.learning.domain.HomeSummary
import com.madovygabor.maturimate.features.learning.domain.MottoKind
import com.madovygabor.maturimate.features.learning.domain.RecentActivity
import com.madovygabor.maturimate.features.learning.domain.WeekDayState
import com.madovygabor.maturimate.features.learning.domain.model.ContinueTarget
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.StepKind
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.home_activity_meta
import maturimate.shared.generated.resources.home_all_done_text
import maturimate.shared.generated.resources.home_all_done_title
import maturimate.shared.generated.resources.home_continue
import maturimate.shared.generated.resources.home_day_1
import maturimate.shared.generated.resources.home_day_2
import maturimate.shared.generated.resources.home_day_3
import maturimate.shared.generated.resources.home_day_4
import maturimate.shared.generated.resources.home_day_5
import maturimate.shared.generated.resources.home_day_6
import maturimate.shared.generated.resources.home_day_7
import maturimate.shared.generated.resources.home_day_done
import maturimate.shared.generated.resources.home_day_later
import maturimate.shared.generated.resources.home_day_missed
import maturimate.shared.generated.resources.home_day_today
import maturimate.shared.generated.resources.home_exam_days
import maturimate.shared.generated.resources.home_goal_caption
import maturimate.shared.generated.resources.home_goal_description
import maturimate.shared.generated.resources.home_goal_none_text
import maturimate.shared.generated.resources.home_goal_none_title
import maturimate.shared.generated.resources.home_goal_reached_text
import maturimate.shared.generated.resources.home_goal_reached_title
import maturimate.shared.generated.resources.home_goal_remaining_text
import maturimate.shared.generated.resources.home_goal_remaining_title
import maturimate.shared.generated.resources.home_greeting
import maturimate.shared.generated.resources.home_greeting_noname
import maturimate.shared.generated.resources.home_kind_final_test
import maturimate.shared.generated.resources.home_kind_lesson
import maturimate.shared.generated.resources.home_kind_mini_test
import maturimate.shared.generated.resources.home_kind_topic_test
import maturimate.shared.generated.resources.home_motto_comeback
import maturimate.shared.generated.resources.home_motto_fresh
import maturimate.shared.generated.resources.home_motto_general
import maturimate.shared.generated.resources.home_motto_reached
import maturimate.shared.generated.resources.home_motto_streak
import maturimate.shared.generated.resources.home_recent_empty
import maturimate.shared.generated.resources.home_recent_lesson_done
import maturimate.shared.generated.resources.home_recent_test_passed
import maturimate.shared.generated.resources.home_recent_test_retry
import maturimate.shared.generated.resources.home_recent_title
import maturimate.shared.generated.resources.home_start
import maturimate.shared.generated.resources.home_streak
import maturimate.shared.generated.resources.home_streak_none
import maturimate.shared.generated.resources.home_streak_risk
import maturimate.shared.generated.resources.home_suggestion_meta
import maturimate.shared.generated.resources.home_suggestion_title
import maturimate.shared.generated.resources.home_when_today
import maturimate.shared.generated.resources.home_when_yesterday
import maturimate.shared.generated.resources.home_minutes
import maturimate.shared.generated.resources.learning_step_tasks_meta
import maturimate.shared.generated.resources.subjects_coming_soon
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime

/** The home tab: what to do today. [onContinue] takes the student to the step in the subjects tab. */
@Composable
fun HomeScreen(
    onContinue: (ContinueTarget) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    // read again whenever the screen comes back, so what was done on another screen shows here
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsState()
    HomeContent(state, onContinue, onRetry = viewModel::refresh)
}

@OptIn(ExperimentalTime::class)
@Composable
fun HomeContent(state: HomeUiState, onContinue: (ContinueTarget) -> Unit, onRetry: () -> Unit) {
    when (state) {
        HomeUiState.Loading -> Unit
        HomeUiState.Failed -> Column(Modifier.fillMaxSize().padding(MaterialTheme.spacing.space4)) { LoadError(onRetry) }
        is HomeUiState.Ready -> HomeReady(state.summary, onContinue)
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun HomeReady(summary: HomeSummary, onContinue: (ContinueTarget) -> Unit) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= layout.breakpointMedium
        Column(
            modifier = Modifier
                .widthIn(max = layout.contentMaxWidth * 2)
                .verticalScroll(rememberScrollState())
                .padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            Greeting(summary)
            if (wide) {
                // the two cards side by side, as tall as the taller one
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(spacing.space4)) {
                    GoalCard(summary, showWeek = true, Modifier.weight(1f).fillMaxHeight())
                    ContinueCard(summary, onContinue, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                GoalCard(summary, showWeek = false, Modifier.fillMaxWidth())
                ContinueCard(summary, onContinue, Modifier.fillMaxWidth())
            }
            // nothing to repeat for a student who has not done anything yet
            if (summary.recent.isNotEmpty()) SuggestionCard()
            RecentActivities(summary)
        }
    }
}

@Composable
private fun Greeting(summary: HomeSummary) {
    val spacing = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
        Text(
            if (summary.firstName != null) stringResource(Res.string.home_greeting, summary.firstName) else stringResource(Res.string.home_greeting_noname),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            when (summary.motto) {
                MottoKind.REACHED -> stringResource(Res.string.home_motto_reached)
                MottoKind.STREAK -> stringResource(Res.string.home_motto_streak, summary.streak)
                MottoKind.COMEBACK -> stringResource(Res.string.home_motto_comeback)
                MottoKind.FRESH -> stringResource(Res.string.home_motto_fresh)
                MottoKind.GENERAL -> stringResource(Res.string.home_motto_general)
            },
            style = MaterialTheme.extendedTypography.reading,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(top = spacing.space2), horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
            AppInfoChip(
                text = if (summary.streak > 0) stringResource(Res.string.home_streak, summary.streak) else stringResource(Res.string.home_streak_none),
                icon = Icons.Rounded.LocalFireDepartment,
                highlighted = summary.streak > 0,
            )
            summary.daysToExam?.let { days ->
                AppInfoChip(pluralStringResource(Res.plurals.home_exam_days, days, days), Icons.Rounded.CalendarMonth)
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun GoalCard(summary: HomeSummary, showWeek: Boolean, modifier: Modifier) {
    val spacing = MaterialTheme.spacing
    val goal = summary.goal
    val goalMinutes = goal.goalSeconds / 60
    val minutes = goal.secondsStudied / 60
    // the streak is in danger all day, until today's goal is reached
    val risk = if (summary.streak > 0 && !goal.reached) stringResource(Res.string.home_streak_risk) else null
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(spacing.space4), verticalArrangement = Arrangement.spacedBy(spacing.space4, Alignment.CenterVertically)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space4)) {
                AppGoalRing(
                    fraction = goal.fraction,
                    reached = goal.reached,
                    value = minutes.toString(),
                    caption = stringResource(Res.string.home_goal_caption, goalMinutes),
                    description = stringResource(Res.string.home_goal_description, minutes, goalMinutes),
                )
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
                    Text(
                        when {
                            goal.reached -> stringResource(Res.string.home_goal_reached_title)
                            minutes == 0 -> stringResource(Res.string.home_goal_none_title)
                            else -> stringResource(Res.string.home_goal_remaining_title, goalMinutes - minutes)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    val detail = when {
                        goal.reached -> stringResource(Res.string.home_goal_reached_text, minutes)
                        minutes == 0 -> stringResource(Res.string.home_goal_none_text, goalMinutes)
                        else -> stringResource(Res.string.home_goal_remaining_text, minutes, goalMinutes)
                    }
                    Text(
                        if (risk != null) "$risk $detail" else detail,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showWeek) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AppWeekStrip(weekDays(summary))
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun weekDays(summary: HomeSummary): List<WeekStripDay> {
    val labels = listOf(
        Res.string.home_day_1, Res.string.home_day_2, Res.string.home_day_3, Res.string.home_day_4,
        Res.string.home_day_5, Res.string.home_day_6, Res.string.home_day_7,
    )
    return summary.week.mapIndexed { index, day ->
        val label = stringResource(labels[index])
        val (state, template) = when (day.state) {
            WeekDayState.DONE -> WeekDotState.DONE to Res.string.home_day_done
            WeekDayState.MISSED -> WeekDotState.MISSED to Res.string.home_day_missed
            WeekDayState.TODAY -> WeekDotState.TODAY to Res.string.home_day_today
            WeekDayState.LATER -> WeekDotState.LATER to Res.string.home_day_later
        }
        WeekStripDay(label, state, stringResource(template, label))
    }
}

@Composable
private fun ContinueCard(summary: HomeSummary, onContinue: (ContinueTarget) -> Unit, modifier: Modifier) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val next = summary.next
    Surface(modifier, shape = MaterialTheme.shapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Column(Modifier.padding(spacing.space4), verticalArrangement = Arrangement.spacedBy(spacing.space2, Alignment.CenterVertically)) {
            if (next == null) {
                Text(stringResource(Res.string.home_all_done_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.home_all_done_text), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "${stepKindLabel(next.kind)} · ${next.topicTitle}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(next.unit.title, style = MaterialTheme.typography.headlineSmall)
                val unit = next.unit
                Text(
                    if (unit is LearningUnit.Practice) stringResource(Res.string.learning_step_tasks_meta, unit.roundSize)
                    else stringResource(Res.string.home_minutes, unit.estimatedMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AppButton(
                    text = if (summary.motto == MottoKind.FRESH) stringResource(Res.string.home_start) else stringResource(Res.string.home_continue),
                    onClick = { onContinue(next) },
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.space2),
                )
            }
        }
    }
}

@Composable
private fun stepKindLabel(kind: StepKind): String = when (kind) {
    StepKind.LESSON -> stringResource(Res.string.home_kind_lesson)
    StepKind.MINI_TEST -> stringResource(Res.string.home_kind_mini_test)
    StepKind.TOPIC_TEST -> stringResource(Res.string.home_kind_topic_test)
    StepKind.FINAL_TEST -> stringResource(Res.string.home_kind_final_test)
}

/** The mistake practice comes with the practice tab (milestone 9a); until then the card is there but greyed. */
@Composable
private fun SuggestionCard() {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
        Row(
            Modifier.padding(horizontal = spacing.space4, vertical = spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLowest, modifier = Modifier.size(MaterialTheme.layout.touchTarget)) {
                Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.padding(spacing.space3))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.home_suggestion_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.home_suggestion_meta), style = MaterialTheme.typography.bodyMedium)
            }
            AppTag(stringResource(Res.string.subjects_coming_soon))
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun RecentActivities(summary: HomeSummary) {
    val spacing = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
        Text(
            stringResource(Res.string.home_recent_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.recent.isEmpty()) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(MaterialTheme.layout.borderThin, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.background,
            ) {
                Text(
                    stringResource(Res.string.home_recent_empty),
                    modifier = Modifier.padding(spacing.space4),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            summary.recent.forEach { ActivityRow(it, today = summary.goal.date) }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun ActivityRow(activity: RecentActivity, today: kotlinx.datetime.LocalDate) {
    val day = activity.at.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val whenText = when (day) {
        today -> stringResource(Res.string.home_when_today)
        today.minus(DatePeriod(days = 1)) -> stringResource(Res.string.home_when_yesterday)
        else -> "${day.day}. ${day.month.ordinal + 1}."
    }
    AppActivityCard(
        title = activity.title,
        meta = stringResource(Res.string.home_activity_meta, activity.topicTitle, whenText),
        isTest = activity.isTest,
        result = when {
            !activity.isTest -> stringResource(Res.string.home_recent_lesson_done)
            activity.passed == true -> stringResource(Res.string.home_recent_test_passed)
            else -> stringResource(Res.string.home_recent_test_retry)
        },
        resultStyle = if (!activity.isTest || activity.passed == true) TagStyle.SUCCESS else TagStyle.NEUTRAL,
    )
}
