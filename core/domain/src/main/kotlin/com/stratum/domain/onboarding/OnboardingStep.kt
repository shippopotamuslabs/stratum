package com.stratum.domain.onboarding

/**
 * Ordered steps of the first-time onboarding wizard.
 *
 * Locked for onboarding (see docs/onboarding-vertical-slices.md §1). Later slices fill
 * the screens for each step; the enum ordering is stable so resume-after-kill and navigation
 * can rely on it. GymAndLifts precedes Specialization/MinimumVolume because those steps consume
 * historical-import insights produced on the GymAndLifts step (suggestions + saturation warnings).
 */
enum class OnboardingStep {
    Welcome,
    Profile,
    Schedule,
    GymAndLifts,
    Specialization,
    MinimumVolume,
    Review,
    Generate,
    ;

    /**
     * The step shown after this one, or `null` if this is the terminal step.
     */
    fun next(): OnboardingStep? =
        entries.getOrNull(ordinal + 1)

    /**
     * The step shown before this one, or `null` if this is the first step.
     */
    fun previous(): OnboardingStep? =
        entries.getOrNull(ordinal - 1)

    val isLast: Boolean get() = this == Generate
    val isFirst: Boolean get() = this == Welcome

    companion object {
        val First: OnboardingStep = Welcome
    }
}
