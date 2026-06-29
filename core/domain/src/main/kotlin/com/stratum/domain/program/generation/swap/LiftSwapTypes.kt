@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.swap

/** Max swaps the model may suggest in one workout. */
const val LIFT_SWAP_MAX_PER_WORKOUT = 3

/** Pattern-matched alternatives sent to the reviewer per exercise. */
const val LIFT_SWAP_CANDIDATES_PER_LIFT = 10

const val LIFT_SWAP_PROMPT_VERSION = 2

data class SwapRequest(
    val workoutPosition: Int,
    val liftPosition: Int,
    val newLiftId: String,
)

data class LiftSwapCandidateMap(
    /** workoutPosition -> liftPosition -> candidate liftIds */
    val byWorkout: Map<Int, Map<Int, List<String>>>,
)

data class LiftSwapCandidatePayload(
    val liftId: String,
    val name: String,
    val movementPattern: String,
    val primaryMuscles: List<String>,
)

data class LiftSwapExercisePayload(
    val position: Int,
    val liftId: String,
    val name: String,
    val movementPattern: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val setCount: Int,
    val repMin: Int?,
    val repMax: Int?,
    val candidates: List<LiftSwapCandidatePayload>,
)

data class LiftSwapWorkoutPayload(
    val position: Int,
    val name: String,
    val workoutLifts: List<LiftSwapExercisePayload>,
)

data class LiftSwapProgramIntentPayload(
    val trainingAge: String,
    val sessionLengthMinutes: Int?,
    val trainingDaysPerWeek: Int,
    val cycleLength: Int,
    val specialization: Map<String, String>,
    val specializedMuscles: List<String>,
)

data class LiftSwapReviewPayload(
    val promptVersion: Int,
    val intent: LiftSwapProgramIntentPayload,
    val program: LiftSwapProgramPayload,
)

data class LiftSwapProgramPayload(
    val cycleLength: Int,
    val workouts: List<LiftSwapWorkoutPayload>,
)

data class LiftSwapRequest(
    val reviewPayload: LiftSwapReviewPayload,
    val candidateMap: LiftSwapCandidateMap,
)

data class LiftSwapResult(
    val swaps: List<SwapRequest>,
)
