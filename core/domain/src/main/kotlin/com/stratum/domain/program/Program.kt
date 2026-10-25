package com.stratum.domain.program

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import com.stratum.domain.program.schedule.CycleProjection
import com.stratum.domain.program.schedule.requireCycleGeometry
import com.stratum.domain.training.CycleAdvancementEffects
import com.stratum.domain.training.CyclePointerUpdate
import com.stratum.domain.training.CycleProgressResolver
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.identity.ProgramChangeEventId
import com.stratum.domain.workout.myorep.MyoRepKind
import java.time.Instant
import java.util.UUID

/** Default inter-set rest (seconds) for generated and legacy persisted sets. */
const val DEFAULT_LIFT_SET_REST_SECONDS = 120

/**
 * Program hierarchy (vertical-slices §2.4): `Program → Workout → WorkoutLift → LiftSet`.
 *
 * This is the **template** hierarchy produced by generation (OS-7) — distinct from the logged-set
 * hierarchy in OS-6. All working sets are straight sets (single load per lift); `LiftSet` carries
 * no weight/load field (D2). `progressionScheme` on [WorkoutLift] is server-assigned from
 * `TrainingAge` during generation (D2); the model never authors it.
 */
data class Program(
    val id: ProgramId,
    val name: String,
    val createdAt: Instant,
    /**
     * Number of slots in one full cycle (training workouts **plus** rest slots).
     * Equals `workouts.size + restSlotIndices.size`. Not “training days only.”
     */
    val cycleLength: Int,
    /**
     * Denormalized projection cache of training weekdays when known. **Not** schedule identity —
     * slot index + [restSlotIndices] are authoritative. Prefer empty for rotating / unbound.
     */
    val trainingWeekdays: Set<Weekday> = emptySet(),
    val workouts: List<Workout>,
    val currentCycleNumber: Int = 1,
    /**
     * Next unconsumed slot index in `[0, cycleLength)`, derived from the session ledger
     * (cache). Completing a slot advances; full cycle resets to 0 and bumps [currentCycleNumber].
     */
    val cyclePosition: Int = 0,
    /**
     * Whether this is the user's active training program. At most one user program is active;
     * library copies and the import-history FK parent stay inactive. Multi-program `isActive`
     * authorized for create-program / library (AGENTS.md §4.5 — v1 reinstall, no migration).
     */
    val isActive: Boolean = true,
    /** Indices in `[0, cycleLength)` that are rest slots (not training). */
    val restSlotIndices: Set<Int> = emptySet(),
    /**
     * Epoch day of slot 0 for the current cycle (optional projection anchor for history /
     * skip weekday snapshots). Programs strip UI is order-only and does not display dates
     * derived from this field.
     */
    val cycleStartEpochDay: Long? = null,
) {
    init {
        // STR-167: cycle geometry is the aggregate's own rule (shared with CycleSpace /
        // MaterializedCycleSpace / GenerationCycleHints via requireCycleGeometry).
        requireCycleGeometry(cycleLength, restSlotIndices)
        require(currentCycleNumber >= 1) {
            "currentCycleNumber must be at least 1, was $currentCycleNumber"
        }
        require(cyclePosition in 0 until cycleLength) {
            "cyclePosition $cyclePosition must lie in [0, $cycleLength)"
        }
        // Children are validated by the aggregate root: workout positions are unique
        // (they are the training order) and every workout belongs to this program.
        // Positions are NOT required to be dense 0..T-1: pruning a workout
        // (ProgramLiftSanitizer) legitimately leaves a gap until a reorder /
        // materialize pass re-densifies, and that state must stay constructible.
        require(workouts.all { workout -> workout.programId == id }) {
            "every workout must belong to program ${id.value}"
        }
        require(workouts.map { workout -> workout.position }.distinct().size == workouts.size) {
            "workout positions must be unique within the program"
        }
    }

    /** Makes this the active program. Cross-program demotion stays repository-enforced. */
    fun activate(): Program = copy(isActive = true)

    /** Demotes this program to inactive library inventory. */
    fun deactivate(): Program = copy(isActive = false)

    /**
     * Appends [lift] to the workout it belongs to ([WorkoutLift.workoutId]).
     * The use case decides when a lift is added and constructs the lift (catalog rest
     * lookup, set shape); the aggregate owns where it lands and that the workout exists.
     */
    fun addWorkoutLift(lift: WorkoutLift): Program {
        var found = false
        val updated =
            workouts.map { workout ->
                if (workout.id == lift.workoutId) {
                    found = true
                    workout.copy(workoutLifts = workout.workoutLifts + lift)
                } else {
                    workout
                }
            }
        require(found) { "Workout with ID ${lift.workoutId.value} not found in program" }
        return copy(workouts = updated)
    }

    /**
     * Replaces the stored [WorkoutLift] that shares [updated]'s id, in the workout
     * [updated] belongs to. Set-count adjustment, lift swaps, and check-in mutations
     * all funnel through here instead of re-mapping the hierarchy in each use case.
     */
    fun replaceWorkoutLift(updated: WorkoutLift): Program {
        var found = false
        val updatedWorkouts =
            workouts.map { workout ->
                if (workout.id != updated.workoutId) {
                    workout
                } else {
                    workout.copy(
                        workoutLifts =
                            workout.workoutLifts.map { lift ->
                                if (lift.id == updated.id) {
                                    found = true
                                    updated
                                } else {
                                    lift
                                }
                            },
                    )
                }
            }
        require(found) { "Workout lift with ID ${updated.id.value} not found in program" }
        return copy(workouts = updatedWorkouts)
    }

    /**
     * Cycle advancement (STR-167, absorbed from the former `computeCycleAdvancement`
     * free function): reconciles the session ledger against this program's cycle
     * space and returns the pointer update plus the optional cycle-completed event
     * that `:core:data` applies transactionally. The pointer-advance decision is the
     * model's; [CycleProgressResolver] stays the ledger-reading service that reports
     * slot progress, and this method decides what the pointer becomes.
     */
    fun advanceCycle(sessions: List<TrainingSession>): CycleAdvancementEffects {
        val progress = CycleProgressResolver.reconcileSlotProgress(this, sessions)
        val cycleComplete = progress.isComplete
        val completedCycleNumber = currentCycleNumber
        val nextCycleNumber = if (cycleComplete) completedCycleNumber + 1 else completedCycleNumber
        val nextCyclePosition = if (cycleComplete) 0 else progress.cyclePosition
        val nextCycleStart =
            if (cycleComplete && cycleStartEpochDay != null) {
                CycleProjection.nextCycleStartEpochDay(cycleStartEpochDay, cycleLength)
            } else {
                null
            }
        return CycleAdvancementEffects(
            pointerUpdate =
                CyclePointerUpdate(
                    programId = id,
                    currentCycleNumber = nextCycleNumber,
                    cyclePosition = nextCyclePosition,
                    cycleStartEpochDay = nextCycleStart,
                ),
            cycleCompletedEvent =
                if (cycleComplete) {
                    ProgramChangeEvent(
                        id = ProgramChangeEventId(UUID.randomUUID().toString()),
                        programId = id,
                        eventType = ProgramChangeEventType.CYCLE_COMPLETED,
                        occurredAt = Instant.now(),
                        payload = ProgramChangeEventPayload.CycleCompleted(completedCycleNumber),
                    )
                } else {
                    null
                },
        )
    }
}

/*
 * Template-mutation inventory (STR-167): which raw `copy(workouts = ...)` mutations
 * moved onto the aggregate above, and which stay off it, with reasons.
 *
 * Moved: activation (activate/deactivate, every former copy(isActive = ...) site),
 * AddWorkoutLiftUseCase (addWorkoutLift), AdjustWorkoutLiftSetCountUseCase /
 * ApplyCheckInRecommendationsUseCase / SwapWorkoutLiftUseCase /
 * ApplyRepRangeShiftUseCase / GenerateProgressionSnapshotUseCase's scheme change
 * (replaceWorkoutLift, with WorkoutLiftSetAdjuster still owning set-shape math),
 * cycle advancement (advanceCycle).
 *
 * Staying in use cases / services:
 * - SaveWorkoutUseCase's whole-workout replacement — already the model's own
 *   extension API (mergeWorkout in ProgramWorkoutMerge.kt).
 * - Generation pipeline transforms (ProgramIntegration, CalendarLiftVariety,
 *   HeuristicProgramNaming, withMaterializedCycleSpace): class C pipeline services
 *   reshaping a not-yet-persisted draft program end to end; the draft is their
 *   working type, not a persisted aggregate being edited in place.
 * - ReorderProgramCycleDays: a schedule service that recomputes rest slots and the
 *   cycle pointer together — cycle geometry spanning the whole aggregate, decided
 *   by the reorder algorithm, not a single-workout edit.
 */

data class Workout(
    val id: WorkoutId,
    val programId: ProgramId,
    val name: String,
    /**
     * Optional projection cache only — **not** schedule identity. Prefer null.
     * Programs strip is order-only (workout name / Rest Day).
     */
    val weekday: Weekday?,
    /** Order among training sessions in the cycle (0..T-1), not calendar slot index. */
    val position: Int,
    val workoutLifts: List<WorkoutLift>,
) {
    init {
        require(position >= 0) { "workout position must be non-negative, was $position" }
        require(workoutLifts.all { lift -> lift.workoutId == id }) {
            "every workout lift must belong to workout ${id.value}"
        }
        require(workoutLifts.map { lift -> lift.position }.distinct().size == workoutLifts.size) {
            "workout lift positions must be unique within the workout"
        }
    }
}

data class WorkoutLift(
    val id: WorkoutLiftId,
    val workoutId: WorkoutId,
    val liftId: LiftId,
    val progressionScheme: ProgressionScheme,
    val position: Int,
    val sets: List<LiftSet>,
    val supersetGroupId: WorkoutSupersetGroupId? = null,
    val useRestTimerBetweenLifts: Boolean = false,
    /** User-authored note pinned to this template slot across sessions of this workout. */
    val pinnedNote: String? = null,
    /**
     * Ordered workout-note bodies carried through a template save. Write-carry
     * only: storage reads leave this empty (use
     * [ProgramRepository.getWorkoutLiftPinnedNotes] for stored lists); template
     * saves with a non-empty list replace the stored rows, while an empty list
     * keeps the existing scalar-authoritative restore behavior.
     */
    val pinnedNotes: List<String> = emptyList(),
    /**
     * Equipment assigned in the template. Null means "inherit the lift's equipment".
     * A mid-session swap overrides this ephemerally via
     * [com.stratum.domain.workout.WorkoutOverlay.swappedEquipment]; "update template"
     * persists the swap back here.
     */
    val equipmentId: EquipmentId? = null,
) {
    init {
        require(position >= 0) { "workout lift position must be non-negative, was $position" }
        require(sets.all { set -> set.workoutLiftId == id }) {
            "every set must belong to workout lift ${id.value}"
        }
        require(sets.map { set -> set.position }.distinct().size == sets.size) {
            "set positions must be unique within the workout lift"
        }
    }

    /**
     * Effective pinned notes: the list form wins, else the scalar. Storage reads leave
     * the list empty (scalar-authoritative), while template saves carry the list —
     * comparing effective content avoids phantom rows when both hold the same text.
     */
    fun effectiveNotes(): List<String> = pinnedNotes.ifEmpty { listOfNotNull(pinnedNote) }
}

/**
 * A single set in a [WorkoutLift]. Named `LiftSet` (not `Set`) to avoid the
 * `kotlin.collections.Set` collision in-package.
 *
 * Rep-range / top-set shape is scheme-dependent — see [ProgressionRepRangePolicy] (canonical):
 * Double: exactly one top set owns [repMin]/[repMax]; back-offs are `0/0` (RIR-only).
 * Linear: every working set stores the shared band; first set is the progression anchor.
 * [rirGoal] is clamped by the generation post-pass (D2).
 */
data class LiftSet(
    val id: LiftSetId,
    val workoutLiftId: WorkoutLiftId,
    val position: Int,
    val setType: SetType,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
    val isTopSet: Boolean,
    val restSeconds: Int = DEFAULT_LIFT_SET_REST_SECONDS,
    /**
     * Myo-rep sub-type when [setType] is [SetType.MyoRep]; null for Standard/Drop.
     * Persisted on the program template (Room v2).
     */
    val myoRepKind: MyoRepKind? = null,
) {
    init {
        // Position only (STR-167 aggregate membership). Rep-band / RIR / rest rules are
        // scheme-dependent and land with STR-168, not here.
        require(position >= 0) { "set position must be non-negative, was $position" }
    }
}
