package com.stratum.domain.onboarding

/**
 * The two onboarding paths a user can take after the common setup steps.
 *
 * - [Generate]: the user walks through Schedule → Specialization → Minimum Volume, then reviews and
 *   generates a program.
 * - [Import]: the user skips the schedule/specialization/minimum-volume steps and imports an existing
 *   program file (CSV/XLSX) directly from Review.
 *
 * Resolved dynamically by [OnboardingWizardState.onboardingPath].
 */
enum class OnboardingPath {
    Generate,
    Import,
}
