package com.stratum.domain.training

import com.stratum.domain.program.Program

/**
 * Resolves a human-readable **workout** title (e.g. "Upper A"), never a lift name as the
 * primary product label for calendar day chips.
 *
 * Order:
 * 1. Non-blank [TrainingSession.workoutNameSnapshot] (preferred — set at session create)
 * 2. Live program workout name when [TrainingSession.workoutId] still resolves
 * 3. Optional [liftOrExerciseFallback] — **only for detail/recap when no workout identity exists**
 *    (imports without a snapshot). Calendar must not pass a lift fallback.
 * 4. [DEFAULT]
 */
object TrainingSessionDisplayName {
    const val DEFAULT = "Workout"

    /**
     * Calendar-safe title: snapshot or program workout name only (no lift/exercise names).
     */
    fun resolveForCalendar(session: TrainingSession, program: Program? = null): String =
        resolve(session = session, program = program, liftOrExerciseFallback = null)

    fun resolve(
        session: TrainingSession,
        program: Program? = null,
        liftOrExerciseFallback: String? = null,
    ): String {
        session.workoutNameSnapshot?.takeIf { it.isNotBlank() }?.let { return it }
        session.workoutId?.let { workoutId ->
            program?.workouts?.firstOrNull { it.id == workoutId }?.name
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        liftOrExerciseFallback?.takeIf { it.isNotBlank() }?.let { return it }
        return DEFAULT
    }
}
