package com.stratum.domain.response

import com.stratum.domain.importing.CustomLiftMusclePolicy
import com.stratum.domain.importing.ImportStubLiftGuesser
import com.stratum.domain.lift.Lift
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.toMovementPatternSet
import com.stratum.domain.training.toMuscleGroupSet
import java.time.Instant

/**
 * Effective weekly hard-set credits per muscle. Primary movers count fully; secondary
 * contributors count at [SECONDARY_SET_CREDIT] so landmark and volume-response analytics
 * reflect compound-lift stimulus (e.g. triceps from pressing, biceps from pulling). Up to 4
 * secondary muscles may be credited per set for compound lifts with multiple co-movers.
 */
object MuscleVolumeCredit {
    const val PRIMARY_SET_CREDIT = 1.0
    const val SECONDARY_SET_CREDIT = 0.5

    fun accumulateSetCredits(
        liftContext: LoggedLiftContext,
        weekStart: Instant,
        muscleWeekCredits: MutableMap<MuscleGroup, MutableMap<Instant, Double>>,
    ) {
        if (
            liftContext.movementPatternsBitmask == 0 &&
            liftContext.primaryMusclesBitmask == 0 &&
            liftContext.secondaryMusclesBitmask == 0
        ) {
            return
        }

        val movementPattern =
            liftContext.movementPatternsBitmask
                .toMovementPatternSet()
                .firstOrNull()
                ?: return
        val primaryMuscles = liftContext.primaryMusclesBitmask.toMuscleGroupSet()
        primaryMuscles.forEach { muscle ->
            addCredit(muscle, weekStart, PRIMARY_SET_CREDIT, muscleWeekCredits)
        }
        val secondaryMuscles =
            CustomLiftMusclePolicy.effectiveSecondaryMuscles(
                movementPattern = movementPattern,
                primaryMuscles = primaryMuscles,
                secondaryMuscles = liftContext.secondaryMusclesBitmask.toMuscleGroupSet(),
            )
        (secondaryMuscles - primaryMuscles).forEach { muscle ->
            addCredit(muscle, weekStart, SECONDARY_SET_CREDIT, muscleWeekCredits)
        }
    }

    fun accumulateSetCredits(
        lift: Lift,
        weekStart: Instant,
        muscleWeekCredits: MutableMap<MuscleGroup, MutableMap<Instant, Double>>,
    ) {
        val movementPattern = ImportStubLiftGuesser.effectiveMovementPattern(lift)
        val primaryMuscles =
            ImportStubLiftGuesser.effectivePrimaryMuscles(lift, movementPattern)
        primaryMuscles.forEach { muscle ->
            addCredit(muscle, weekStart, PRIMARY_SET_CREDIT, muscleWeekCredits)
        }
        val secondaryMuscles =
            CustomLiftMusclePolicy.effectiveSecondaryMuscles(
                movementPattern = movementPattern,
                primaryMuscles = primaryMuscles,
                secondaryMuscles = lift.secondaryMuscles,
            )
        (secondaryMuscles - primaryMuscles).forEach { muscle ->
            addCredit(muscle, weekStart, SECONDARY_SET_CREDIT, muscleWeekCredits)
        }
    }

    private fun addCredit(
        muscle: MuscleGroup,
        weekStart: Instant,
        credit: Double,
        muscleWeekCredits: MutableMap<MuscleGroup, MutableMap<Instant, Double>>,
    ) {
        val weeks = muscleWeekCredits.getOrPut(muscle) { mutableMapOf() }
        weeks[weekStart] = weeks.getOrDefault(weekStart, 0.0) + credit
    }
}
