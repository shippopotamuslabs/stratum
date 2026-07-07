package com.stratum.domain.training

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.WorkoutLiftId

data class LoggedLiftContext(
    val liftName: String,
    val movementPatternsBitmask: Int = 0,
    val primaryMusclesBitmask: Int = 0,
    val secondaryMusclesBitmask: Int = 0,
    val kinematicProfile: KinematicProfile = KinematicProfile.NonAxialCompound,
    val muscleLengthBias: MuscleLengthBias = MuscleLengthBias.MidRange,
    val workoutLiftId: WorkoutLiftId? = null,
    val progressionScheme: ProgressionScheme? = null,
    val setType: SetType? = null,
    val repMin: Int? = null,
    val repMax: Int? = null,
    val rirGoal: Int? = null,
)
