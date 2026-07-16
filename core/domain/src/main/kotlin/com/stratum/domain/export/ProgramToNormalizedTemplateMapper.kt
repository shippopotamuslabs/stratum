package com.stratum.domain.export

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.importing.NormalizedProgramTemplate
import com.stratum.domain.program.importing.NormalizedProgramTemplateLift
import com.stratum.domain.program.importing.NormalizedProgramTemplateSet
import com.stratum.domain.program.importing.NormalizedProgramTemplateWorkout

object ProgramToNormalizedTemplateMapper {
    fun map(
        program: Program,
        liftNames: Map<LiftId, String> = emptyMap(),
    ): NormalizedProgramTemplate {
        val workoutsBySlot = mutableMapOf<Int, NormalizedProgramTemplateWorkout>()
        // Rest slots become rest-day workouts with name "Rest".
        for (restIndex in program.restSlotIndices) {
            workoutsBySlot[restIndex] =
                NormalizedProgramTemplateWorkout(
                    name = "Rest",
                    weekday = null,
                    position = restIndex,
                    lifts = emptyList(),
                    restDay = true,
                )
        }
        // Training workouts: position is training order; map into cycle slot space.
        val trainingSorted = program.workouts.sortedBy { it.position }
        var trainingCursor = 0
        for (slot in 0 until program.cycleLength) {
            if (slot in program.restSlotIndices) continue
            val workout = trainingSorted.getOrNull(trainingCursor)
            if (workout != null) {
                workoutsBySlot[slot] = mapTrainingWorkout(workout, slot, liftNames)
                trainingCursor++
            }
        }
        // Fallback: if cycleLength/rest slots do not cover all workouts, append remaining.
        while (trainingCursor < trainingSorted.size) {
            val workout = trainingSorted[trainingCursor]
            val slot = workoutsBySlot.keys.maxOrNull()?.plus(1) ?: workout.position
            workoutsBySlot[slot] = mapTrainingWorkout(workout, slot, liftNames)
            trainingCursor++
        }
        val ordered = workoutsBySlot.entries.sortedBy { it.key }.map { it.value }
        return NormalizedProgramTemplate(
            programName = program.name,
            cycleLength = program.cycleLength,
            workouts = ordered,
        )
    }

    private fun mapTrainingWorkout(
        workout: Workout,
        slotPosition: Int,
        liftNames: Map<LiftId, String>,
    ): NormalizedProgramTemplateWorkout {
        val lifts =
            workout.workoutLifts
                .sortedBy { it.position }
                .map { wl ->
                    NormalizedProgramTemplateLift(
                        name = liftNames[wl.liftId] ?: wl.liftId.value,
                        position = wl.position,
                        sets =
                            wl.sets
                                .sortedBy { it.position }
                                .map { set ->
                                    NormalizedProgramTemplateSet(
                                        position = set.position,
                                        setTypeLabel = set.setType.name,
                                        repMin = set.repMin,
                                        repMax = set.repMax,
                                        rirGoal = set.rirGoal,
                                        restSeconds = set.restSeconds,
                                        isTopSet = set.isTopSet,
                                    )
                                },
                        supersetGroupKey = wl.supersetGroupId?.value,
                    )
                }
        return NormalizedProgramTemplateWorkout(
            name = workout.name,
            weekday = workout.weekday?.name,
            position = slotPosition,
            lifts = lifts,
            restDay = false,
        )
    }
}
