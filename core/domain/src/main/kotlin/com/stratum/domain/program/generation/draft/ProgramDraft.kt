@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.draft

import com.stratum.domain.program.SetType

data class ProgramDraft(
    var name: String? = null,
    /** Full cycle slots (training + rest). */
    var cycleLength: Int? = null,
    /** Rest slot indices in `[0, cycleLength)`. Null means unset (normalize treats as empty). */
    var restSlotIndices: Set<Int>? = null,
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
