package com.stratum.domain.training

import com.stratum.domain.lift.Lift
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.WorkoutLiftId

interface LoggedLiftContextFactory {
    fun fromLiftCatalog(lift: Lift, exerciseNameRaw: String): LoggedLiftContext

    fun fromLiveTemplate(
        lift: Lift,
        workoutLift: WorkoutLift,
        templateSet: LiftSet?,
    ): LoggedLiftContext

    /**
     * Mid-session / ad-hoc lift that is not (yet) on the program template.
     * Stamps [workoutLiftId] so session amend/unlog and summary grouping still key correctly.
     */
    fun fromLiveSessionLift(
        lift: Lift,
        workoutLiftId: WorkoutLiftId,
        progressionScheme: ProgressionScheme = ProgressionScheme.Double,
        setType: SetType? = null,
        repMin: Int? = null,
        repMax: Int? = null,
        rirGoal: Int? = null,
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

    override fun fromLiveSessionLift(
        lift: Lift,
        workoutLiftId: WorkoutLiftId,
        progressionScheme: ProgressionScheme,
        setType: SetType?,
        repMin: Int?,
        repMax: Int?,
        rirGoal: Int?,
    ): LoggedLiftContext =
        fromLiftCatalog(lift, lift.name).copy(
            workoutLiftId = workoutLiftId,
            progressionScheme = progressionScheme,
            setType = setType,
            repMin = repMin,
            repMax = repMax,
            rirGoal = rirGoal,
        )
}

/** @deprecated Use [DefaultLoggedLiftContextFactory]. Kept for test references during migration. */
class StubLoggedLiftContextFactory : LoggedLiftContextFactory by DefaultLoggedLiftContextFactory()
