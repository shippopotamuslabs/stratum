package com.stratum.domain.onboarding

/**
 * Steps of the first-time onboarding wizard.
 *
 * The flow forks at [ChoosePath]: the common setup steps (Welcome → Profile → GymAndLifts) lead to a
 * path choice, then either the **Generate** branch (Schedule → Specialization → MinimumVolume → Review)
 * or the **Import** branch (straight to Review). [Review] is terminal for both paths — the
 * Generate/Import action runs from there.
 *
 * Because the two paths share only some steps, [next] and [previous] take the resolved
 * [OnboardingPath] to route correctly around the fork. Callers that don't yet know the path (e.g.
 * rendering an indicator before ChoosePath) pass `null` and get the common-prefix traversal.
 *
 * GymAndLifts precedes ChoosePath because historical-import insights produced there feed the later
 * Generate-only steps when they exist (suggestions + saturation warnings).
 */
enum class OnboardingStep {
    Welcome,
    Profile,
    GymAndLifts,
    ChoosePath,
    Schedule,
    Specialization,
    MinimumVolume,
    Review,
    ;

    /**
     * The step shown after this one given the chosen [path], or `null` if this is terminal.
     *
     * `path` may be `null` while the user is still in the common prefix (before [ChoosePath]); in
     * that case traversal is the simple ordinal walk within the prefix.
     */
    fun next(path: OnboardingPath? = null): OnboardingStep? = when (this) {
        ChoosePath -> when (path) {
            OnboardingPath.Import -> Review
            OnboardingPath.Generate -> Schedule
            null -> null
        }
        Review -> null
        else -> entries.getOrNull(ordinal + 1)
    }

    /**
     * The step shown before this one given the chosen [path], or `null` if this is the first step.
     *
     * On the Import path, Review backs up to ChoosePath (skipping the Generate-only steps); on the
     * Generate path, it backs up to MinimumVolume.
     */
    fun previous(path: OnboardingPath? = null): OnboardingStep? = when (this) {
        Review -> when (path) {
            OnboardingPath.Import -> ChoosePath
            OnboardingPath.Generate -> MinimumVolume
            null -> ChoosePath
        }
        Schedule -> ChoosePath
        Welcome -> null
        else -> entries.getOrNull(ordinal - 1)
    }

    val isLast: Boolean get() = this == Review
    val isFirst: Boolean get() = this == Welcome

    companion object {
        val First: OnboardingStep = Welcome
    }
}
