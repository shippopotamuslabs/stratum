package com.stratum.domain.workout

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface EditSessionEvent {
    data class LiftSelected(
        val workoutId: String,
        val workoutLiftId: String?, // null for Add mode
        val liftId: String,
    ) : EditSessionEvent

    /**
     * Multi-add from the lift picker. [liftIds] are catalog lift IDs in selection order.
     * When [asSuperset] is true and there are at least two lifts, consumers should assign
     * the newly appended rows a shared [com.stratum.domain.program.identity.WorkoutSupersetGroupId].
     */
    data class LiftsSelected(
        val workoutId: String,
        val liftIds: List<String>,
        val asSuperset: Boolean,
    ) : EditSessionEvent

    data class LiftsReordered(
        val workoutId: String,
        val orderedWorkoutLiftIds: List<String>,
    ) : EditSessionEvent
}

data class ReorderLiftItem(
    val workoutLiftId: String,
    val name: String,
)

@Singleton
class WorkoutEditSession @Inject constructor() {
    private val _events = MutableSharedFlow<EditSessionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<EditSessionEvent> = _events.asSharedFlow()

    private var _reorderLifts = emptyList<ReorderLiftItem>()

    fun selectLift(workoutId: String, workoutLiftId: String?, liftId: String) {
        _events.tryEmit(EditSessionEvent.LiftSelected(workoutId, workoutLiftId, liftId))
    }

    fun selectLifts(workoutId: String, liftIds: List<String>, asSuperset: Boolean) {
        if (liftIds.isEmpty()) return
        _events.tryEmit(EditSessionEvent.LiftsSelected(workoutId, liftIds, asSuperset))
    }

    fun setReorderLifts(lifts: List<ReorderLiftItem>) {
        _reorderLifts = lifts
    }

    fun getReorderLifts(): List<ReorderLiftItem> = _reorderLifts

    fun confirmReorder(workoutId: String, orderedWorkoutLiftIds: List<String>) {
        _events.tryEmit(EditSessionEvent.LiftsReordered(workoutId, orderedWorkoutLiftIds))
    }
}

