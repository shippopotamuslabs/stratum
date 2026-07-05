package com.stratum.domain.training

import com.stratum.domain.lift.Lift
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.LiftSet

interface LoggedLiftContextFactory {
    fun fromLiftCatalog(lift: Lift, exerciseNameRaw: String): LoggedLiftContext

    fun fromLiveTemplate(
        lift: Lift,
        workoutLift: WorkoutLift,
        templateSet: LiftSet?,
    ): LoggedLiftContext
}

class DefaultLoggedLiftContextFactory : LoggedLiftContextFactory {
    override fun fromLiftCatalog(lift: Lift, exerciseNameRaw: String): LoggedLiftContext =
        LoggedLiftContext(
            liftName = lift.name,
            movementPatternsBitmask = lift.movementPatterns.toMovementPatternsBitmask(),
            primaryMusclesBitmask = lift.primaryMuscles.toMusclesBitmask(),
            secondaryMusclesBitmask = lift.secondaryMuscles.toMusclesBitmask(),
            kinematicProfile = lift.kinematicProfile,
            muscleLengthBias = lift.muscleLengthBias,
        )

    override fun fromLiveTemplate(
        lift: Lift,
        workoutLift: WorkoutLift,
        templateSet: LiftSet?,
    ): LoggedLiftContext =
        fromLiftCatalog(lift, lift.name).copy(
            workoutLiftId = workoutLift.id,
            progressionScheme = workoutLift.progressionScheme,
            setType = templateSet?.setType,
            repMin = templateSet?.repMin,
            repMax = templateSet?.repMax,
            rirGoal = templateSet?.rirGoal,
        )
}

/** @deprecated Use [DefaultLoggedLiftContextFactory]. Kept for test references during migration. */
class StubLoggedLiftContextFactory : LoggedLiftContextFactory by DefaultLoggedLiftContextFactory()
