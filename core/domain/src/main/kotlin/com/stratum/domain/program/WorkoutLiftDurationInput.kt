package com.stratum.domain.program

/**
 * One prescribed set for [EstimateWorkoutDurationMinutesUseCase].
 * [repMin]/[repMax] are raw band values (Double back-offs may be `0/0`).
 */
data class DurationSetInput(
    val restSeconds: Int,
    val repMin: Int = 0,
    val repMax: Int = 0,
)

/**
 * Per-lift duration input for [EstimateWorkoutDurationMinutesUseCase].
 * When [supersetGroupId] is set, [position] orders lifts within the group chain.
 *
 * [useRestTimerBetweenLifts] affects **estimation only for inter-lift gaps** in a multi-lift
 * superset: when true, the estimate includes this lift's configured rest after each non-terminal
 * set in the chain; when false, a short 30s transition is assumed. Live session rest-timer
 * behavior is owned separately by workout logging and is not driven by this DTO.
 */
data class WorkoutLiftDurationInput(
    val sets: List<DurationSetInput>,
    val position: Int = 0,
    val supersetGroupId: String? = null,
    val useRestTimerBetweenLifts: Boolean = false,
)
