package com.stratum.feature.workout

import com.stratum.domain.lift.Weight
import com.stratum.feature.workout.ui.WorkoutUiEvent
import com.stratum.feature.workout.ui.WorkoutUiState

/**
 * Sink from [LiveWorkoutSessionActor] back to the screen ViewModel (StateFlows, events,
 * process-scoped surfaces). Keeps the actor free of a hard ViewModel reference cycle.
 *
 * Primary role is **output** (UI publish, events, surfaces, display unit). Complete/abandon
 * exit-transition flags are also here so the actor can suppress empty-session flashes without
 * holding a ViewModel reference — domain abandon delete stays process-scoped on the VM.
 */
internal interface SessionEffects {
    fun publishUi(state: WorkoutUiState)

    fun publishUserMessage(message: String?)

    suspend fun emitEvent(event: WorkoutUiEvent)

    fun clearLiveSurfaces()

    fun displayUnit(): Weight.WeightUnit

    fun displayUnitEpoch(): Int

    /**
     * When true, actor must not publish [WorkoutUiState.NoActiveSession] (exit transition
     * retains the last loaded frame).
     */
    fun shouldRetainLoadedUi(): Boolean

    /** True while a complete attempt is in flight (suppresses empty-session flash). */
    fun isCompleting(): Boolean

    /**
     * Arms / disarms exit-transition retention of the last [WorkoutUiState.Loaded] frame.
     * Set true before finalize/abandon domain work; false on failure so empty can surface.
     */
    fun setRetainLoadedUiForExit(retain: Boolean)

    /** Clears the ViewModel complete-attempt gate after [SessionCommand.CompleteWorkout] finishes. */
    fun markCompleteAttemptFinished()
}
