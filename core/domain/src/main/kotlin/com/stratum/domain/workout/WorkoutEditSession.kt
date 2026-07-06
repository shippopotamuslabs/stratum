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
}

@Singleton
class WorkoutEditSession @Inject constructor() {
    private val _events = MutableSharedFlow<EditSessionEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<EditSessionEvent> = _events.asSharedFlow()

    fun selectLift(workoutId: String, workoutLiftId: String?, liftId: String) {
        _events.tryEmit(EditSessionEvent.LiftSelected(workoutId, workoutLiftId, liftId))
    }
}
