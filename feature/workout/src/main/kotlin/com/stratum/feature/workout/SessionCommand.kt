package com.stratum.feature.workout

import com.stratum.core.ui.component.SupersetLinkDirection
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.workout.SetSlotId

/**
 * Commands processed FIFO by [LiveWorkoutSessionActor]. Public ViewModel API only enqueues;
 * the actor is the sole writer of live session memory (overlay / structure / snapshot).
 */
internal sealed interface SessionCommand {
    /**
     * Bootstrap a live observation for [routeWorkoutId]: reset session memory and load fatigue.
     * Sent before the cycle/catalog collector begins enqueuing [ApplyCycleSnapshot].
     */
    data class StartSession(val routeWorkoutId: WorkoutId) : SessionCommand

    /** Cycle-progress + catalog emission: structure rebuild, restore, adopt, render. */
    data class ApplyCycleSnapshot(
        val routeWorkoutId: WorkoutId,
        val program: Program?,
        val session: TrainingSession?,
        val workout: Workout?,
        val catalog: List<Lift>,
    ) : SessionCommand

    /** Re-render with current display unit (prefs flip). */
    data object RenderOnly : SessionCommand

    data class LogSet(
        val slotKey: String,
        val weight: Weight,
        val reps: Int,
        val rir: Int?,
    ) : SessionCommand

    data class UnlogSet(val slotKey: String) : SessionCommand

    data class AddSet(val afterSlotId: SetSlotId) : SessionCommand

    data class AddSetForLift(val workoutLiftId: String) : SessionCommand

    data class DeleteSet(val slotId: SetSlotId) : SessionCommand

    data class DeleteSetByKey(val slotKey: String) : SessionCommand

    data class DeleteSetWithCascade(val slotId: SetSlotId) : SessionCommand

    data object Undo : SessionCommand

    data class GenerateMyoMini(val activationSlotId: SetSlotId) : SessionCommand

    data class AddDropSet(val afterSlotId: SetSlotId) : SessionCommand

    data class SetSetKind(val slotKey: String, val kind: LiveSetKind) : SessionCommand

    data class SwapLift(
        val workoutLiftId: WorkoutLiftId,
        val newLiftId: LiftId,
    ) : SessionCommand

    data class AddLifts(
        val liftIds: List<LiftId>,
        val asSuperset: Boolean,
    ) : SessionCommand

    data class RemoveLift(val workoutLiftId: WorkoutLiftId) : SessionCommand

    data class ReorderLifts(val orderedWorkoutLiftIds: List<String>) : SessionCommand

    data class LinkSuperset(
        val workoutLiftId: WorkoutLiftId,
        val direction: SupersetLinkDirection,
    ) : SessionCommand

    data class RemoveFromSuperset(val workoutLiftId: WorkoutLiftId) : SessionCommand

    data class SetUseRestTimerBetweenLifts(
        val workoutLiftId: WorkoutLiftId,
        val enabled: Boolean,
    ) : SessionCommand

    data class SetRestSecondsForSlots(val updates: Map<String, Int>) : SessionCommand

    data class CompleteWorkout(val saveTemplate: Boolean) : SessionCommand

    /**
     * Freeze mutators while domain abandon runs on the process scope.
     * Does **not** delete the training session — that stays process-scoped so nav cancel
     * cannot abort the Room delete. Memory clear is [FinalizeAbandonLocal] after success.
     */
    data object BeginAbandon : SessionCommand

    /** Domain abandon failed — unfreeze mutators so the lifter can continue the session. */
    data object CancelAbandon : SessionCommand

    /**
     * After domain abandon succeeds (or no session id). Clears local session memory via effects.
     * Domain delete is coordinated by the ViewModel (process-scoped).
     */
    data object FinalizeAbandonLocal : SessionCommand
}
