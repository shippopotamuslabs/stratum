package com.stratum.domain.checkin

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import java.time.Duration
import java.time.Instant
import com.stratum.domain.workout.LiftSwapped

/**
 * Weeks the current variation has been in rotation for [workoutLiftId].
 *
 * Prefers the most recent [ProgramChangeEventType.LIFT_SWAPPED] that introduced [currentLiftId]
 * on that slot; falls back to [programCreatedAt] when no swap history exists.
 */
fun liftRotationTenureWeeks(
    workoutLiftId: WorkoutLiftId,
    currentLiftId: LiftId,
    programCreatedAt: Instant,
    events: List<ProgramChangeEvent>,
    now: Instant = Instant.now(),
): Int {
    val lastSwapOntoCurrent =
        events
            .asSequence()
            .filter { it.eventType == ProgramChangeEventType.LIFT_SWAPPED }
            .mapNotNull { event ->
                val payload = event.payload as? ProgramChangeEventPayload.LiftSwapped ?: return@mapNotNull null
                if (payload.workoutLiftId == workoutLiftId && payload.newLiftId == currentLiftId) {
                    event.occurredAt
                } else {
                    null
                }
            }
            .maxOrNull()

    val start = lastSwapOntoCurrent ?: programCreatedAt
    return weeksBetween(start, now)
}

private fun weeksBetween(start: Instant, end: Instant): Int =
    (Duration.between(start, end).toDays() / 7).toInt().coerceAtLeast(0)
