package com.madovygabor.maturimate.features.appOnboarding.presentation

import maturimate.composeapp.generated.resources.Res
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
 * The steps the user goes through. Until the role is chosen (step 5.6) this is the
 * student's path, the longest one, as in the prototype.
 */
fun onboardingSteps(): List<OnboardingStep> = OnboardingStep.entries

/** Where the user is in the onboarding, for the step header and the step list. */
data class OnboardingProgress(
    val current: OnboardingStep,
    val steps: List<OnboardingStep> = onboardingSteps(),
    val onBack: () -> Unit,
) {
    /** 1-based number of the current step. */
    val number: Int get() = steps.indexOf(current) + 1
}
