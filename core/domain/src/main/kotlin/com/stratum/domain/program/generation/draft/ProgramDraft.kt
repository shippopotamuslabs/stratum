@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.draft

import com.stratum.domain.program.SetType

data class ProgramDraft(
    var name: String? = null,
    var cycleLength: Int? = null,
    val workouts: List<ProgramWorkoutDraft>,
)

data class ProgramWorkoutDraft(
    var name: String? = null,
    var weekday: String? = null,
    var position: Int? = null,
    var workoutLifts: MutableList<ProgramLiftDraft>? = null,
)

data class ProgramLiftDraft(
    var liftId: String? = null,
    var position: Int? = null,
    var workoutLiftSets: MutableList<LiftSetDraft>? = null,
)

data class LiftSetDraft(
    var position: Int,
    var setType: SetType = SetType.Standard,
    var repMin: Int,
    var repMax: Int,
    var rirGoal: Int,
    var isTopSet: Boolean = false,
)
