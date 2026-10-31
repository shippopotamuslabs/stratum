package com.stratum.domain.program

/**
 * A built weekly schedule plus the non-fatal [ScheduleDiagnostic]s recorded while
 * building it (STR-258).
 *
 * Diagnostics are values, not logs: `:core:domain` never logs (CODE_STYLE_RULES.md §1),
 * so [buildWeeklyScheduleOrFallback] returns them alongside the items and the caller at
 * the boundary drains each one exactly once through its logging wiring.
 * Diagnostics never reach the UI.
 */
data class WeeklyScheduleOutcome(
    val items: List<ScheduleDayItem>,
    val diagnostics: List<ScheduleDiagnostic> = emptyList(),
)

/**
 * A non-fatal schedule-building event recorded when the weekday-bound schedule could
 * not be built and the positional fallback was used instead (STR-258).
 *
 * Diagnostics are values, not logs: `:core:domain` never logs, so the outcome carries
 * them and the boundary drains each one exactly once. Diagnostics never reach the UI.
 */
sealed interface ScheduleDiagnostic {
    /**
     * Workouts exist but none carries a weekday binding and no training-weekday
     * metadata remains, so the strict builder would silently produce seven rest
     * days; the positional fallback shows the workouts instead.
     */
    data object FallbackNoWeekdayBoundWorkouts : ScheduleDiagnostic

    /**
     * The strict weekday-bound builder threw; the positional fallback shows the
     * workouts instead. [cause] records the strict builder's failure.
     */
    data class FallbackStrictBuildFailed(
        val cause: Throwable,
    ) : ScheduleDiagnostic
}
