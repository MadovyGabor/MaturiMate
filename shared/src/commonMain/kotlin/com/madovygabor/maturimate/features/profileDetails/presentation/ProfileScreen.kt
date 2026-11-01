package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.utils.longDate
import com.madovygabor.maturimate.features.appOnboarding.presentation.LocalEditingAnswer
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.date.DateScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.GoalScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.language.LanguageScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.ReminderScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.hoursAndMinutes
import com.madovygabor.maturimate.features.appOnboarding.presentation.role.RoleScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.SubjectsScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.SubjectLevel
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.goal_minutes
import maturimate.shared.generated.resources.language_hu
import maturimate.shared.generated.resources.language_sk
import maturimate.shared.generated.resources.plan_days
import maturimate.shared.generated.resources.plan_until_exam
import maturimate.shared.generated.resources.profile_not_set
import maturimate.shared.generated.resources.profile_reminder_off
import maturimate.shared.generated.resources.profile_title
import maturimate.shared.generated.resources.profile_why
import maturimate.shared.generated.resources.role_learner
import maturimate.shared.generated.resources.role_student
import maturimate.shared.generated.resources.role_teacher
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime

/** A page of the profile tab: the answers, or one question opened to change its answer. */
private sealed interface ProfilePage {
    data object Answers : ProfilePage
    data class Edit(val step: OnboardingStep) : ProfilePage
}

/**
 * The profile tab: every onboarding answer, each opening its onboarding question to change it.
 * A new role asks for the subjects again, then for an exam date or a daily goal when the new
 * role needs one that is missing.
 */
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val backStack = remember { mutableStateListOf<ProfilePage>(ProfilePage.Answers) }
    val done: () -> Unit = { if (backStack.size > 1) backStack.removeLast() }
    // the first of the questions still missing replaces the one just answered, so Back returns
    // to the answers; each question then asks for what is still missing after it
    val continueWith: (List<OnboardingStep>) -> Unit = { missing ->
        backStack.removeLast()
        missing.firstOrNull()?.let { backStack.add(ProfilePage.Edit(it)) }
    }

    NavDisplay(
        backStack = backStack,
        onBack = done,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // every opened question loads the saved answer again
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<ProfilePage.Answers> {
                ProfileContent(state, onEdit = { backStack.add(ProfilePage.Edit(it)) })
            }
            entry<ProfilePage.Edit> { page ->
                val role = state?.profile?.role
                CompositionLocalProvider(LocalEditingAnswer provides true) {
                    when (page.step) {
                        // a new language rebuilds the app in it, which shows the answers again
                        OnboardingStep.LANGUAGE -> LanguageScreen(onBack = done, onNext = { done() })
                        OnboardingStep.ROLE -> RoleScreen(onBack = done, onNext = { continueWith(listOf(OnboardingStep.SUBJECTS)) })
                        OnboardingStep.SUBJECTS -> SubjectsScreen(onBack = done, onNext = { newRole -> continueWith(viewModel.missingAfterRoleChange(newRole)) })
                        OnboardingStep.DATE -> DateScreen(onBack = done, onNext = { continueWith(viewModel.missingAfterRoleChange(role) - OnboardingStep.DATE) })
                        OnboardingStep.GOAL -> GoalScreen(role = role, onBack = done, onNext = done)
                        OnboardingStep.REMINDER -> ReminderScreen(role = role, onBack = done, onNext = done)
                        OnboardingStep.PLAN, OnboardingStep.ACCOUNT -> Unit
                    }
                }
            }
        },
    )
}

@Composable
fun ProfileContent(state: ProfileUiState?, onEdit: (OnboardingStep) -> Unit) {
    val spacing = MaterialTheme.spacing
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = MaterialTheme.layout.contentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.space4, vertical = spacing.space6),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            Text(stringResource(Res.string.profile_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            if (state == null) return@Column
            state.daysLeft?.let { days ->
                Countdown(stringResource(Res.string.plan_until_exam), pluralStringResource(Res.plurals.plan_days, days, days))
            }
            Text(stringResource(Res.string.profile_why), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                state.steps.forEach { step ->
                    AnswerRow(stringResource(step.label), answerText(step, state.profile), onClick = { onEdit(step) })
                }
            }
        }
    }
}

/** The saved answer of a step in words. */
@Composable
private fun answerText(step: OnboardingStep, profile: UserProfile): String {
    val notSet = stringResource(Res.string.profile_not_set)
    return when (step) {
        OnboardingStep.LANGUAGE -> when (profile.teachingLanguage) {
            TeachingLanguage.HU -> stringResource(Res.string.language_hu)
            TeachingLanguage.SK -> stringResource(Res.string.language_sk)
            null -> notSet
        }
        OnboardingStep.ROLE -> when (profile.role) {
            UserRole.STUDENT -> stringResource(Res.string.role_student)
            UserRole.LEARNER -> stringResource(Res.string.role_learner)
            UserRole.TEACHER -> stringResource(Res.string.role_teacher)
            null -> notSet
        }
        OnboardingStep.SUBJECTS -> profile.subjects
            .mapNotNull { saved -> CatalogSubject.byId(saved.subjectId) }
            .map { stringResource(it.label) }
            .joinToString(", ")
            .ifEmpty { notSet }
        OnboardingStep.DATE -> profile.graduationDate?.let { longDate(it) } ?: notSet
        OnboardingStep.GOAL -> profile.dailyGoalMinutes?.let { pluralStringResource(Res.plurals.goal_minutes, it, it) } ?: notSet
        OnboardingStep.REMINDER -> profile.reminderTime?.takeIf { profile.reminderEnabled }?.hoursAndMinutes()
            ?: stringResource(Res.string.profile_reminder_off)
        OnboardingStep.PLAN, OnboardingStep.ACCOUNT -> ""
    }
}

/** Days until the exam, as on the plan step. */
@Composable
private fun Countdown(label: String, value: String) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(spacing.space4), verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/** One answer: the step's name, the answer, and an arrow that opens the question. */
@Composable
private fun AnswerRow(label: String, value: String, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = spacing.space4, vertical = spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
@Preview
private fun ProfileContentPreview() {
    AppTheme(darkTheme = false) {
        ProfileContent(
            ProfileUiState(
                profile = UserProfile(
                    teachingLanguage = TeachingLanguage.HU, role = UserRole.STUDENT, graduationDate = LocalDate(2027, 5, 3),
                    uiLanguage = TeachingLanguage.HU, dailyGoalMinutes = 10, timezone = "Europe/Bratislava", onboardingCompletedAt = null,
                    subjects = listOf(UserSubject(1, true), UserSubject(3, true, SubjectLevel.B2)),
                    reminderEnabled = true, reminderTime = LocalTime(18, 0),
                ),
                daysLeft = 183,
            ),
            onEdit = {},
        )
    }
}
