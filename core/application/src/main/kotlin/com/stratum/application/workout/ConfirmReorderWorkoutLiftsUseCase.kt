package com.stratum.application.workout

import com.stratum.domain.workout.ReorderLiftItem
import com.stratum.domain.workout.WorkoutEditSession
import javax.inject.Inject

/**
 * Confirm/write path for the reorder-lifts screen (STR-292).
 *
 * First use case to inject [WorkoutEditSession]: the session bus itself is a
 * STR-207 exception (a domain `@Singleton` bus, not an outbound port), so no
 * Hilt module is needed — `:core:application` already depends on
 * `:core:domain`. The ViewModel keeps its transient drag order locally and
 * routes both the snapshot read and the confirm write through this use case,
 * so the ViewModel injects only a use case (gospel §12.4).
 */
class ConfirmReorderWorkoutLiftsUseCase @Inject constructor(
    private val workoutEditSession: WorkoutEditSession,
) {
    /** Staged lifts for the reorder screen (empty when nothing was staged). */
    fun loadReorderLifts(): List<ReorderLiftItem> = workoutEditSession.getReorderLifts()

    /** Emits the confirmed order to the editor consumers. */
    operator fun invoke(workoutId: String, orderedWorkoutLiftIds: List<String>) {
        workoutEditSession.confirmReorder(workoutId, orderedWorkoutLiftIds)
    }
}
