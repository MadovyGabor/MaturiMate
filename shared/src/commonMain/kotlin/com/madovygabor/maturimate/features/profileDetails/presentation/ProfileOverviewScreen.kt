package com.madovygabor.maturimate.features.profileDetails.presentation

import maturimate.shared.generated.resources.nav_practice
import com.madovygabor.maturimate.features.learning.presentation.practiceResult
import com.madovygabor.maturimate.features.learning.presentation.placeAndDay
import com.madovygabor.maturimate.features.learning.presentation.icon
import com.madovygabor.maturimate.features.learning.presentation.activityTitle
import com.madovygabor.maturimate.features.learning.presentation.activityPlace
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.madovygabor.maturimate.core.presentation.uiComponents.AppActivityCard
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppStatCard
import com.madovygabor.maturimate.core.presentation.uiComponents.AppSubjectProgressCard
import com.madovygabor.maturimate.core.presentation.uiComponents.ProgressLine
import com.madovygabor.maturimate.core.presentation.uiComponents.StatItem
import com.madovygabor.maturimate.core.presentation.uiComponents.TagStyle
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.learning.domain.RecentActivity
import com.madovygabor.maturimate.features.learning.domain.StudyTotals
import com.madovygabor.maturimate.features.learning.domain.model.LessonTestCount
import com.madovygabor.maturimate.features.learning.presentation.LoadError
import com.madovygabor.maturimate.features.profileDetails.domain.ProfileSubject
import com.madovygabor.maturimate.features.profileDetails.domain.ProfileSummary
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_create
import maturimate.shared.generated.resources.profile_guest_text
import maturimate.shared.generated.resources.profile_guest_title
import maturimate.shared.generated.resources.welcome_have_account
import maturimate.shared.generated.resources.home_activity_meta
import maturimate.shared.generated.resources.home_kind_lesson
import maturimate.shared.generated.resources.home_recent_lesson_done
import maturimate.shared.generated.resources.home_when_today
import maturimate.shared.generated.resources.home_when_yesterday
import maturimate.shared.generated.resources.profile_guest
import maturimate.shared.generated.resources.profile_history_all
import maturimate.shared.generated.resources.profile_history_empty
import maturimate.shared.generated.resources.profile_history_title
import maturimate.shared.generated.resources.profile_kind_test
import maturimate.shared.generated.resources.profile_lessons_count
import maturimate.shared.generated.resources.profile_parts_close
import maturimate.shared.generated.resources.profile_parts_open
import maturimate.shared.generated.resources.profile_progress_title
import maturimate.shared.generated.resources.profile_result_passed
import maturimate.shared.generated.resources.profile_result_retry
import maturimate.shared.generated.resources.profile_settings
import maturimate.shared.generated.resources.profile_stat_days
import maturimate.shared.generated.resources.profile_stat_hours
import maturimate.shared.generated.resources.profile_stat_minutes
import maturimate.shared.generated.resources.profile_stat_streak
import maturimate.shared.generated.resources.profile_stat_streak_description
import maturimate.shared.generated.resources.profile_stat_tasks
import maturimate.shared.generated.resources.profile_stat_tasks_description
import maturimate.shared.generated.resources.profile_stat_time
import maturimate.shared.generated.resources.profile_stat_time_description
import maturimate.shared.generated.resources.profile_tab_title
import maturimate.shared.generated.resources.profile_tests_count
import maturimate.shared.generated.resources.subjects_coming_soon
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime

/** The profile tab: where the student stands. [onSettings] opens the settings, [onHistory] the full history. */
@Composable
fun ProfileOverviewScreen(
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: ProfileOverviewViewModel = koinViewModel(),
) {
    // read again whenever the screen comes back, so what was done on another tab shows here
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsState()
    ProfileOverviewContent(state, ProfileActions(onSettings, onHistory, onSignUp, onSignIn, viewModel::toggle, viewModel::refresh))
}

/** What the profile can do; one object keeps the parameters short. */
data class ProfileActions(
    val onSettings: () -> Unit = {},
    val onHistory: () -> Unit = {},
    /** A guest's "Fiók létrehozása" and "Már van fiókom". */
    val onSignUp: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onToggle: (Long) -> Unit = {},
    val onRetry: () -> Unit = {},
)

@OptIn(ExperimentalTime::class)
@Composable
fun ProfileOverviewContent(state: ProfileOverviewUiState, actions: ProfileActions) {
    when (state) {
        ProfileOverviewUiState.Loading -> Unit
        ProfileOverviewUiState.Failed -> Column(Modifier.fillMaxSize().padding(MaterialTheme.spacing.space4)) { LoadError(actions.onRetry) }
        is ProfileOverviewUiState.Ready -> ProfileReady(state, actions)
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun ProfileReady(state: ProfileOverviewUiState.Ready, actions: ProfileActions) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    val summary = state.summary
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // tablet and desktop: the numbers and the subjects on the left, the history on the right
        val wide = maxWidth >= layout.breakpointMedium
        Column(
            modifier = Modifier
                .widthIn(max = layout.contentMaxWidth * 2)
                .verticalScroll(rememberScrollState())
                .padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.space6)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                        Header(summary.firstName, actions.onSettings)
                        if (summary.firstName == null) GuestCard(actions)
                        Stats(summary.streak, summary.totals)
                        Progress(summary.subjects, state.open, actions.onToggle)
                    }
                    Column(Modifier.weight(1f)) { Recent(summary.recent, summary.today, actions.onHistory) }
                }
            } else {
                Header(summary.firstName, actions.onSettings)
                // a guest sees the call to sign up first, right under the name
                if (summary.firstName == null) GuestCard(actions)
                Stats(summary.streak, summary.totals)
                Progress(summary.subjects, state.open, actions.onToggle)
                Recent(summary.recent, summary.today, actions.onHistory)
            }
        }
    }
}

/** A small circle with the initial, the name with "Profil" under it, and the gear to the settings. */
@Composable
private fun Header(firstName: String?, onSettings: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
        Surface(
            Modifier.size(MaterialTheme.layout.badge),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (firstName != null) colors.primaryContainer else colors.surfaceContainerHighest,
            contentColor = if (firstName != null) colors.onPrimaryContainer else colors.onSurfaceVariant,
        ) {
            // the letter in the middle of the circle, without the extra space of the line height
            Box(contentAlignment = Alignment.Center) {
                if (firstName != null) {
                    Text(
                        firstName.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                        ),
                    )
                } else Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(MaterialTheme.layout.iconSmall))
            }
        }
        Column(Modifier.weight(1f).semantics { heading() }) {
            Text(firstName ?: stringResource(Res.string.profile_guest), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(Res.string.profile_tab_title), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(Res.string.profile_settings), tint = colors.onSurfaceVariant)
        }
    }
}

/** The call to a guest: create an account (or sign in to one), so the progress is kept. */
@Composable
private fun GuestCard(actions: ProfileActions) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    // compact: an icon and two short lines, the two buttons side by side
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Column(Modifier.padding(horizontal = spacing.space4, vertical = spacing.space3), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(MaterialTheme.layout.icon))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.profile_guest_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.profile_guest_text), style = MaterialTheme.typography.bodySmall)
                }
            }
            // the buttons only as wide as their text, under the text and after the icon
            Row(
                Modifier.padding(start = MaterialTheme.layout.icon + spacing.space3),
                horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppButton(text = stringResource(Res.string.account_create), onClick = actions.onSignUp, compact = true)
                AppButton(text = stringResource(Res.string.welcome_have_account), onClick = actions.onSignIn, variant = AppButtonVariant.TEXT, compact = true)
            }
        }
    }
}

/** The streak in days, the study time ("12ó 40p", minutes only under an hour) and the solved tasks. */
@Composable
private fun Stats(streak: Int, totals: StudyTotals) {
    val minutesAll = (totals.secondsStudied / 60).toInt()
    val hours = minutesAll / 60
    val minutes = minutesAll % 60
    val hourUnit = stringResource(Res.string.profile_stat_hours)
    val minuteUnit = stringResource(Res.string.profile_stat_minutes)
    AppStatCard(
        listOf(
            StatItem(
                Icons.Rounded.LocalFireDepartment,
                listOf(streak.toString(), pluralStringResource(Res.plurals.profile_stat_days, streak)),
                stringResource(Res.string.profile_stat_streak),
                pluralStringResource(Res.plurals.profile_stat_streak_description, streak, streak),
            ),
            StatItem(
                Icons.Rounded.Schedule,
                if (hours > 0) listOf(hours.toString(), hourUnit, minutes.toString(), minuteUnit) else listOf(minutes.toString(), minuteUnit),
                stringResource(Res.string.profile_stat_time),
                stringResource(Res.string.profile_stat_time_description, hours, minutes),
            ),
            StatItem(
                Icons.Rounded.CheckCircle,
                listOf(totals.tasksAnswered.toString()),
                stringResource(Res.string.profile_stat_tasks),
                pluralStringResource(Res.plurals.profile_stat_tasks_description, totals.tasksAnswered, totals.tasksAnswered),
            ),
        )
    )
}

/** The progress in each of the user's subjects; a tap opens its big parts. */
@Composable
private fun Progress(subjects: List<ProfileSubject>, open: Set<Long>, onToggle: (Long) -> Unit) {
    if (subjects.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2)) {
        SectionTitle(stringResource(Res.string.profile_progress_title))
        subjects.forEach { item ->
            val progress = item.progress
            AppSubjectProgressCard(
                subject = line(stringResource(item.subject.label), progress?.count ?: LessonTestCount.ZERO),
                parts = progress?.parts.orEmpty().map { line(it.topic.title, it.count) },
                expanded = item.subject.id in open,
                onToggle = { onToggle(item.subject.id) },
                openLabel = stringResource(Res.string.profile_parts_open),
                closeLabel = stringResource(Res.string.profile_parts_close),
                comingSoon = if (progress == null) stringResource(Res.string.subjects_coming_soon) else null,
            )
        }
    }
}

@Composable
private fun line(title: String, count: LessonTestCount): ProgressLine {
    val fraction = count.steps.fraction
    return ProgressLine(
        title = title,
        fraction = fraction,
        percent = "${(fraction * 100).roundToInt()}%",
        details = listOf(
            stringResource(Res.string.profile_lessons_count, count.lessons.done, count.lessons.total),
            stringResource(Res.string.profile_tests_count, count.tests.done, count.tests.total),
        ),
    )
}

/** The last three activities, without day headings; "Összes megtekintése" opens the full history. */
@OptIn(ExperimentalTime::class)
@Composable
private fun Recent(recent: List<RecentActivity>, today: LocalDate, onHistory: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionTitle(stringResource(Res.string.profile_history_title)) }
            if (recent.isNotEmpty()) {
                TextButton(onClick = onHistory) {
                    Text(stringResource(Res.string.profile_history_all), style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(MaterialTheme.layout.iconSmall))
                }
            }
        }
        if (recent.isEmpty()) {
            EmptyNote(stringResource(Res.string.profile_history_empty))
        } else {
            recent.forEach { activity ->
                ActivityItem(activity, meta = placeAndDay(activityPlace(activity), dayText(activity, today)))
            }
        }
    }
}

/** "Ma", "Tegnap", or the date, as on the home screen. */
@OptIn(ExperimentalTime::class)
@Composable
private fun dayText(activity: RecentActivity, today: LocalDate): String {
    val day = activity.at.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return when (day) {
        today -> stringResource(Res.string.home_when_today)
        today.minus(DatePeriod(days = 1)) -> stringResource(Res.string.home_when_yesterday)
        else -> "${day.day}. ${day.month.ordinal + 1}."
    }
}

/**
 * A finished lesson, test round or practice round, with a tag for the kind and one for the result (with the points
 * of a round; practice has no passing, only "8 / 10").
 */
@OptIn(ExperimentalTime::class)
@Composable
internal fun ActivityItem(activity: RecentActivity, meta: String) {
    val earned = activity.earnedPoints ?: 0
    val max = activity.maxPoints ?: 0
    val (result, resultStyle) = when {
        activity.kind.isPractice -> practiceResult(activity)
        !activity.isTest -> stringResource(Res.string.home_recent_lesson_done) to TagStyle.SUCCESS
        activity.passed == true -> stringResource(Res.string.profile_result_passed, earned, max) to TagStyle.SUCCESS
        else -> stringResource(Res.string.profile_result_retry, earned, max) to TagStyle.NEUTRAL
    }
    AppActivityCard(
        title = activityTitle(activity),
        meta = meta,
        icon = activity.icon,
        kind = when {
            activity.kind.isPractice -> stringResource(Res.string.nav_practice)
            activity.isTest -> stringResource(Res.string.profile_kind_test)
            else -> stringResource(Res.string.home_kind_lesson)
        },
        result = result,
        resultStyle = resultStyle,
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun EmptyNote(text: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(MaterialTheme.layout.borderThin, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.background,
    ) {
        Text(
            text,
            modifier = Modifier.padding(MaterialTheme.spacing.space4),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
