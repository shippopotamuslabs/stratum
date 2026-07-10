package com.stratum.domain.training

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.MuscleVolumeSummary
import com.stratum.domain.response.MuscleVolumeCredit
import javax.inject.Inject

/**
 * Fractional hard-set volume per muscle for a single completed session, derived from logged-set
 * [LoggedLiftContext] muscle bitmasks (primary/secondary credits from [MuscleVolumeCredit]).
 *
 * Uses tags stored on the set at log time so history stays accurate after catalog edits.
 */
class ComputeLoggedSessionMuscleVolumeUseCase @Inject constructor() {
    operator fun invoke(sets: List<LoggedSet>): List<MuscleVolumeSummary> {
        if (sets.isEmpty()) return emptyList()

        val volumeTotals = mutableMapOf<MuscleGroup, Double>()
        val exercisesByMuscle = mutableMapOf<MuscleGroup, MutableSet<String>>()

        for (set in sets) {
            val exerciseKey =
                set.liftId?.value
                    ?: set.liftContext.liftName.takeIf { it.isNotBlank() }
                    ?: set.exerciseNameRaw
            val primary = set.liftContext.primaryMusclesBitmask.toMuscleGroupSet()
            val secondary =
                set.liftContext.secondaryMusclesBitmask.toMuscleGroupSet() - primary

            creditMuscles(
                muscles = primary,
                credit = MuscleVolumeCredit.PRIMARY_SET_CREDIT,
                exerciseKey = exerciseKey,
                volumeTotals = volumeTotals,
                exercisesByMuscle = exercisesByMuscle,
            )
            creditMuscles(
                muscles = secondary,
                credit = MuscleVolumeCredit.SECONDARY_SET_CREDIT,
                exerciseKey = exerciseKey,
                volumeTotals = volumeTotals,
                exercisesByMuscle = exercisesByMuscle,
            )
        }

        return volumeTotals
            .map { (muscle, setCount) ->
                MuscleVolumeSummary(
                    muscle = muscle,
                    exerciseCount = exercisesByMuscle[muscle]?.size ?: 0,
                    setCount = setCount,
                )
            }
            .sortedByDescending { summary -> summary.setCount }
    }

    private fun creditMuscles(
        muscles: Set<MuscleGroup>,
        credit: Double,
        exerciseKey: String,
        volumeTotals: MutableMap<MuscleGroup, Double>,
        exercisesByMuscle: MutableMap<MuscleGroup, MutableSet<String>>,
    ) {
        for (muscle in muscles) {
            volumeTotals[muscle] = (volumeTotals[muscle] ?: 0.0) + credit
            exercisesByMuscle.getOrPut(muscle) { mutableSetOf() }.add(exerciseKey)
        }
    }
}
