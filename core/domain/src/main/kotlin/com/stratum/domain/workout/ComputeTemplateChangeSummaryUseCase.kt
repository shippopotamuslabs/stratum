package com.stratum.domain.workout

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
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
        val rows = WorkoutDiff(baseline, applied, liftNames).rows()
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

        /**
         * Rank among surviving lifts, so add/remove side effects (siblings shifting
         * down) do not read as user reorders. Displayed positions stay absolute
         * template positions.
         */
        private val baselineRank = rankById(baseline, appliedById)
        private val appliedRank = rankById(applied, baselineById)

        /** Superset partners (other member names) per lift id; empty = ungrouped. */
        private val baselinePartners = partnersByLiftId(baseline)
        private val appliedPartners = partnersByLiftId(applied)

        private fun nameOf(id: LiftId): String = liftNames[id] ?: UNKNOWN_LIFT_DISPLAY_NAME

        /**
         * Single traversal over the surviving lifts: per-lift set maps are built once
         * and each aspect appends to its own category bucket, so the returned order
         * stays category-major (lifts, order, sets, rest, supersets, notes).
         */
        fun rows(): List<TemplateChangeRow> {
            val equipmentBucket = mutableListOf<TemplateChangeRow>()
            val orderBucket = mutableListOf<TemplateChangeRow>()
            val setBucket = mutableListOf<TemplateChangeRow>()
            val restBucket = mutableListOf<TemplateChangeRow>()
            val supersetBucket = mutableListOf<TemplateChangeRow>()
            val noteBucket = mutableListOf<TemplateChangeRow>()
            for ((baselineLift, appliedLift) in commonLifts) {
                val name = nameOf(appliedLift.liftId)
                val baselineSetsById = baselineLift.sets.associateBy { it.id }
                val appliedSetsById = appliedLift.sets.associateBy { it.id }
                equipmentRow(baselineLift, appliedLift, name)?.let(equipmentBucket::add)
                orderRow(baselineLift, appliedLift, name)?.let(orderBucket::add)
                emitSetRows(name, baselineLift, appliedLift, baselineSetsById, appliedSetsById, setBucket)
                emitRestRows(name, appliedLift, baselineSetsById, restBucket)
                emitSupersetRows(name, baselineLift, appliedLift, supersetBucket)
                noteRow(name, baselineLift, appliedLift)?.let(noteBucket::add)
            }
            return liftRows() + equipmentBucket + orderBucket + setBucket + restBucket +
                supersetBucket + noteBucket
        }

        private fun equipmentRow(
            baselineLift: WorkoutLift,
            appliedLift: WorkoutLift,
            name: String,
        ): EquipmentSwapped? {
            val baselineEquipment = baselineLift.equipmentId
            val appliedEquipment = appliedLift.equipmentId
            if (baselineEquipment == appliedEquipment) return null
            // Equipment can only be assigned by a swap (never cleared to null).
            if (appliedEquipment == null) return null
            return EquipmentSwapped(
                liftName = name,
                oldId = baselineEquipment,
                newId = appliedEquipment,
            )
        }

        private fun liftRows(): List<TemplateChangeRow> {
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

        private fun orderRow(
            baselineLift: WorkoutLift,
            appliedLift: WorkoutLift,
            name: String,
        ): LiftMoved? {
            if (baselineRank[appliedLift.id] == appliedRank[appliedLift.id]) return null
            return LiftMoved(
                liftName = name,
                oldPosition = baselineLift.position,
                newPosition = appliedLift.position,
            )
        }

        private fun emitSetRows(
            name: String,
            baselineLift: WorkoutLift,
            appliedLift: WorkoutLift,
            baselineSetsById: Map<LiftSetId, LiftSet>,
            appliedSetsById: Map<LiftSetId, LiftSet>,
            out: MutableList<TemplateChangeRow>,
        ) {
            val removedCount = baselineSetsById.keys.count { it !in appliedSetsById }
            val addedCount = appliedSetsById.keys.count { it !in baselineSetsById }
            if (removedCount > 0 || addedCount > 0) {
                out.add(
                    SetCountChanged(
                        liftName = name,
                        oldCount = baselineLift.sets.size,
                        newCount = appliedLift.sets.size,
                        replacedCount = minOf(removedCount, addedCount),
                    ),
                )
            }
            appliedLift.sets.mapNotNullTo(out) { appliedSet ->
                val baselineSet = baselineSetsById[appliedSet.id] ?: return@mapNotNullTo null
                val oldShape = TemplateSetShape.from(baselineSet)
                val newShape = TemplateSetShape.from(appliedSet)
                if (oldShape == newShape) {
                    null
                } else {
                    SetEdited(name, appliedSet.position, oldShape, newShape)
                }
            }
        }

        private fun emitRestRows(
            name: String,
            appliedLift: WorkoutLift,
            baselineSetsById: Map<LiftSetId, LiftSet>,
            out: MutableList<TemplateChangeRow>,
        ) {
            appliedLift.sets.mapNotNullTo(out) { appliedSet ->
                val baselineSet = baselineSetsById[appliedSet.id] ?: return@mapNotNullTo null
                if (baselineSet.restSeconds == appliedSet.restSeconds) {
                    null
                } else {
                    RestEdited(name, appliedSet.position, baselineSet.restSeconds, appliedSet.restSeconds)
                }
            }
        }

        private fun emitSupersetRows(
            name: String,
            baselineLift: WorkoutLift,
            appliedLift: WorkoutLift,
            out: MutableList<TemplateChangeRow>,
        ) {
            if (baselineLift.supersetGroupId != appliedLift.supersetGroupId) {
                out.add(
                    SupersetGroupingChanged(
                        liftName = name,
                        oldPartners = baselinePartners.getValue(baselineLift.id),
                        newPartners = appliedPartners.getValue(appliedLift.id),
                    ),
                )
            }
            if (baselineLift.useRestTimerBetweenLifts != appliedLift.useRestTimerBetweenLifts) {
                out.add(
                    RestBetweenLiftsChanged(
                        liftName = name,
                        oldEnabled = baselineLift.useRestTimerBetweenLifts,
                        newEnabled = appliedLift.useRestTimerBetweenLifts,
                    ),
                )
            }
        }

        private fun noteRow(
            name: String,
            baselineLift: WorkoutLift,
            appliedLift: WorkoutLift,
        ): PinnedNotesChanged? {
            val oldNotes = baselineLift.effectiveNotes()
            val newNotes = appliedLift.effectiveNotes()
            if (oldNotes == newNotes) return null
            return PinnedNotesChanged(name, oldNotes, newNotes)
        }

        /** Rank among surviving lifts per lift id (add/remove shifts excluded). */
        private fun rankById(
            workout: Workout,
            otherById: Map<WorkoutLiftId, WorkoutLift>,
        ): Map<WorkoutLiftId, Int> =
            workout.workoutLifts
                .filter { it.id in otherById }
                .mapIndexed { index, lift -> lift.id to index }
                .toMap()

        /** Other member names per lift id, in workout order (empty = ungrouped). */
        private fun partnersByLiftId(workout: Workout): Map<WorkoutLiftId, List<String>> {
            val byGroup = workout.workoutLifts.filter { it.supersetGroupId != null }.groupBy { it.supersetGroupId }
            return workout.workoutLifts.associate { lift ->
                val groupId = lift.supersetGroupId
                val partners =
                    if (groupId == null) {
                        emptyList()
                    } else {
                        byGroup.getValue(groupId)
                            .filter { it.id != lift.id }
                            .map { nameOf(it.liftId) }
                    }
                lift.id to partners
            }
        }
    }
}
