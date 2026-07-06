@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.schema

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType

data class ProgramNormalizationRequest(
    val trainingAge: String,
    val lastTrained: String?,
    val enabledLiftIds: Set<String>,
    val movementPatternByLiftId: Map<String, MovementPattern?>,
)

data class NormalizedProgram(
    val name: String,
    val cycleLength: Int,
    val workouts: List<NormalizedWorkout>,
)

data class NormalizedWorkout(
    val name: String,
    val weekday: String?,
    val position: Int,
    val workoutLifts: List<NormalizedLift>,
)

data class NormalizedLift(
    val liftId: String,
    val position: Int,
    val progressionScheme: ProgressionScheme,
    val workoutLiftSets: List<NormalizedSet>,
)

data class NormalizedSet(
    val position: Int,
    val setType: SetType,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
    val isTopSet: Boolean,
)
