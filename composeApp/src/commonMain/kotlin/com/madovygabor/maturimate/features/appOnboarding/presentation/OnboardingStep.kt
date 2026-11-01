package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.compose.runtime.staticCompositionLocalOf
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.common_next
import maturimate.composeapp.generated.resources.common_save
import maturimate.composeapp.generated.resources.onboarding_step_account
import maturimate.composeapp.generated.resources.onboarding_step_date
import maturimate.composeapp.generated.resources.onboarding_step_goal
import maturimate.composeapp.generated.resources.onboarding_step_language
import maturimate.composeapp.generated.resources.onboarding_step_plan
import maturimate.composeapp.generated.resources.onboarding_step_reminder
import maturimate.composeapp.generated.resources.onboarding_step_role
import maturimate.composeapp.generated.resources.onboarding_step_subjects
import org.jetbrains.compose.resources.StringResource

/** The question screens of the onboarding, in order (docs/onboarding/README.md, section 5). */
enum class OnboardingStep(val label: StringResource) {
    LANGUAGE(Res.string.onboarding_step_language),
    ROLE(Res.string.onboarding_step_role),
    SUBJECTS(Res.string.onboarding_step_subjects),
    DATE(Res.string.onboarding_step_date),
    GOAL(Res.string.onboarding_step_goal),
    REMINDER(Res.string.onboarding_step_reminder),
    PLAN(Res.string.onboarding_step_plan),
    ACCOUNT(Res.string.onboarding_step_account),
}

/**
 * The steps the user goes through (docs/onboarding/README.md, section 5): learners have no
 * exam date, teachers neither an exam date nor a daily goal. Until the role is chosen this is
 * the student's path, the longest one, as in the prototype.
 */
fun onboardingSteps(role: UserRole? = null): List<OnboardingStep> = when (role) {
    UserRole.STUDENT, null -> OnboardingStep.entries
    UserRole.LEARNER -> OnboardingStep.entries - OnboardingStep.DATE
    UserRole.TEACHER -> OnboardingStep.entries - OnboardingStep.DATE - OnboardingStep.GOAL
}

/** The screen of a step. */
val OnboardingStep.route: Route
    get() = when (this) {
        OnboardingStep.LANGUAGE -> Route.AppOnboarding.Language
        OnboardingStep.ROLE -> Route.AppOnboarding.Role
        OnboardingStep.SUBJECTS -> Route.AppOnboarding.Subjects
        OnboardingStep.DATE -> Route.AppOnboarding.Date
        OnboardingStep.GOAL -> Route.AppOnboarding.Goal
        OnboardingStep.REMINDER -> Route.AppOnboarding.Reminder
        OnboardingStep.PLAN -> Route.AppOnboarding.Plan
        OnboardingStep.ACCOUNT -> Route.AppOnboarding.Account
    }

/** The screen after [step] for this role; null after the last step. */
fun nextRoute(step: OnboardingStep, role: UserRole?): Route? {
    val steps = onboardingSteps(role)
    return steps.getOrNull(steps.indexOf(step) + 1)?.route
}

/**
 * Whether the saved profile already answers the step. Steps without a stored answer
 * (reminder, plan, account) count as unanswered, so a resumed onboarding shows them again.
 */
fun OnboardingStep.isAnswered(profile: UserProfile): Boolean = when (this) {
    OnboardingStep.LANGUAGE -> profile.teachingLanguage != null
    OnboardingStep.ROLE -> profile.role != null
    OnboardingStep.SUBJECTS -> profile.subjects.isNotEmpty()
    OnboardingStep.DATE -> profile.graduationDate != null
    OnboardingStep.GOAL -> profile.dailyGoalMinutes != null
    OnboardingStep.REMINDER, OnboardingStep.PLAN, OnboardingStep.ACCOUNT -> false
}

/** Where the user is in the onboarding, for the step header and the step list. */
data class OnboardingProgress(
    val current: OnboardingStep,
    val steps: List<OnboardingStep> = onboardingSteps(),
    val onBack: () -> Unit,
) {
    /** 1-based number of the current step. */
    val number: Int get() = steps.indexOf(current) + 1
}

/**
 * True when a question screen is opened from the profile to change its answer: the screen has
 * a plain back button instead of the step header, and "Mentés" instead of "Tovább".
 */
val LocalEditingAnswer = staticCompositionLocalOf { false }

/** The text of a question screen's main button. */
fun nextLabel(editing: Boolean): StringResource = if (editing) Res.string.common_save else Res.string.common_next
