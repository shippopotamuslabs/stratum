package com.stratum.domain.workout

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import javax.inject.Inject

/**
 * Diffs a baseline template [Workout] against the applied workout (baseline +
 * session overlay, as built by [ApplyOverlayToWorkoutUseCase]) for the end-of-workout
 * change summary (STR-125).
 *
 * Comparing baseline-vs-applied — rather than interpreting overlay fields — keeps the
 * sheet honest: the summary shows exactly what "Update Workout Template" will write,
 * including apply semantics (auto-generated myo minis excluded, added-then-deleted
 * slots dropped, positions renumbered).
 *
 * Only lifts present in both workouts get per-lift rows (sets / rest / supersets /
 * notes); added and removed lifts are covered by [LiftAdded] / [LiftRemoved] alone.
 */
class ComputeTemplateChangeSummaryUseCase @Inject constructor() {
    operator fun invoke(
        baseline: Workout,
        applied: Workout,
        liftNames: Map<LiftId, String>,
    ): TemplateChangeSummary {
        val diff = WorkoutDiff(baseline, applied, liftNames)
        val rows =
            diff.liftRows() +
                diff.orderRows() +
                diff.setRows() +
                diff.restRows() +
                diff.supersetRows() +
                diff.noteRows()
        return TemplateChangeSummary(rows)
    }

    private class WorkoutDiff(
        private val baseline: Workout,
        private val applied: Workout,
        private val liftNames: Map<LiftId, String>,
    ) {
        private val baselineById = baseline.workoutLifts.associateBy { it.id }
        private val appliedById = applied.workoutLifts.associateBy { it.id }

        /** Lifts surviving in both workouts, in applied position order. */
        private val commonLifts: List<Pair<WorkoutLift, WorkoutLift>> =
            applied.workoutLifts.mapNotNull { appliedLift ->
                baselineById[appliedLift.id]?.let { baselineLift -> baselineLift to appliedLift }
            }

        private fun nameOf(id: LiftId): String = liftNames[id] ?: UNKNOWN_LIFT_DISPLAY_NAME

        fun liftRows(): List<TemplateChangeRow> {
            val removed =
                baseline.workoutLifts
                    .filterNot { it.id in appliedById }
                    .map { LiftRemoved(nameOf(it.liftId), it.sets.size) }
            val addedOrSwapped =
                applied.workoutLifts.mapNotNull { appliedLift ->
                    val baselineLift = baselineById[appliedLift.id]
                    when {
                        baselineLift == null ->
                            LiftAdded(nameOf(appliedLift.liftId), appliedLift.sets.size)
                        baselineLift.liftId != appliedLift.liftId ->
                            LiftSwapped(nameOf(baselineLift.liftId), nameOf(appliedLift.liftId))
                        else -> null
                    }
                }
            return removed + addedOrSwapped
        }

        /**
         * Order rows fire on rank change among surviving lifts, so add/remove side
         * effects (siblings shifting down) do not read as user reorders. Displayed
         * positions are absolute template positions.
         */
        fun orderRows(): List<TemplateChangeRow> {
            val baselineRank =
                baseline.workoutLifts
                    .filter { it.id in appliedById }
                    .mapIndexed { index, lift -> lift.id to index }
                    .toMap()
            val appliedRank =
                applied.workoutLifts
                    .filter { it.id in baselineById }
                    .mapIndexed { index, lift -> lift.id to index }
                    .toMap()
            return commonLifts.mapNotNull { (baselineLift, appliedLift) ->
                if (baselineRank[appliedLift.id] == appliedRank[appliedLift.id]) {
                    null
                } else {
                    LiftMoved(
                        liftName = nameOf(appliedLift.liftId),
                        oldPosition = baselineLift.position,
                        newPosition = appliedLift.position,
                    )
                }
            }
        }

        fun setRows(): List<TemplateChangeRow> =
            commonLifts.flatMap { (baselineLift, appliedLift) ->
                val name = nameOf(appliedLift.liftId)
                val baselineSetsById = baselineLift.sets.associateBy { it.id }
                val appliedSetsById = appliedLift.sets.associateBy { it.id }
                val removedCount = baselineSetsById.keys.count { it !in appliedSetsById }
                val addedCount = appliedSetsById.keys.count { it !in baselineSetsById }
                buildList {
                    if (removedCount > 0 || addedCount > 0) {
                        add(
                            SetCountChanged(
                                liftName = name,
                                oldCount = baselineLift.sets.size,
                                newCount = appliedLift.sets.size,
                                replacedCount = minOf(removedCount, addedCount),
                            ),
                        )
                    }
                    appliedLift.sets.mapNotNullTo(this) { appliedSet ->
                        val baselineSet = baselineSetsById[appliedSet.id] ?: return@mapNotNullTo null
                        val oldShape = baselineSet.toShape()
                        val newShape = appliedSet.toShape()
                        if (oldShape == newShape) {
                            null
                        } else {
                            SetEdited(name, appliedSet.position, oldShape, newShape)
                        }
                    }
                }
            }

        fun restRows(): List<TemplateChangeRow> =
            commonLifts.flatMap { (baselineLift, appliedLift) ->
                val name = nameOf(appliedLift.liftId)
                val baselineSetsById = baselineLift.sets.associateBy { it.id }
                appliedLift.sets.mapNotNull { appliedSet ->
                    val baselineSet = baselineSetsById[appliedSet.id] ?: return@mapNotNull null
                    if (baselineSet.restSeconds == appliedSet.restSeconds) {
                        null
                    } else {
                        RestEdited(name, appliedSet.position, baselineSet.restSeconds, appliedSet.restSeconds)
                    }
                }
            }

        fun supersetRows(): List<TemplateChangeRow> =
            commonLifts.flatMap { (baselineLift, appliedLift) ->
                val name = nameOf(appliedLift.liftId)
                buildList {
                    if (baselineLift.supersetGroupId != appliedLift.supersetGroupId) {
                        add(
                            SupersetGroupingChanged(
                                liftName = name,
                                oldPartners = groupPartners(baseline, baselineLift),
                                newPartners = groupPartners(applied, appliedLift),
                            ),
                        )
                    }
                    if (baselineLift.useRestTimerBetweenLifts != appliedLift.useRestTimerBetweenLifts) {
                        add(
                            RestBetweenLiftsChanged(
                                liftName = name,
                                oldEnabled = baselineLift.useRestTimerBetweenLifts,
                                newEnabled = appliedLift.useRestTimerBetweenLifts,
                            ),
                        )
                    }
                }
            }

        fun noteRows(): List<TemplateChangeRow> =
            commonLifts.mapNotNull { (baselineLift, appliedLift) ->
                val oldNotes = baselineLift.effectiveNotes()
                val newNotes = appliedLift.effectiveNotes()
                if (oldNotes == newNotes) {
                    null
                } else {
                    PinnedNotesChanged(nameOf(appliedLift.liftId), oldNotes, newNotes)
                }
            }

        /** Names of the other lifts sharing [lift]'s superset group (empty = ungrouped). */
        private fun groupPartners(workout: Workout, lift: WorkoutLift): List<String> {
            val groupId = lift.supersetGroupId ?: return emptyList()
            return workout.workoutLifts
                .filter { it.id != lift.id && it.supersetGroupId == groupId }
                .map { nameOf(it.liftId) }
        }
    }

}

private fun LiftSet.toShape(): TemplateSetShape =
    TemplateSetShape(
        setType = setType,
        myoRepKind = myoRepKind,
        repMin = repMin,
        repMax = repMax,
        rirGoal = rirGoal,
        isTopSet = isTopSet,
    )

/**
 * Effective pinned notes: the list form wins, else the scalar. Storage reads leave
 * the list empty (scalar-authoritative), while template saves carry the list —
 * comparing effective content avoids phantom rows when both hold the same text.
 */
private fun WorkoutLift.effectiveNotes(): List<String> =
    pinnedNotes.ifEmpty { listOfNotNull(pinnedNote) }
