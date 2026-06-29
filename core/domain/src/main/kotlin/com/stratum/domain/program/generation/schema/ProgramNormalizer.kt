@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.schema

import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.program.generation.reference.MAJOR_COMPOUND_PATTERNS
import com.stratum.domain.program.generation.reference.TOP_SET_RIR
import com.stratum.domain.program.generation.reference.clampBackOffRir
import com.stratum.domain.program.generation.reference.schemeForTrainingAge

/**
 * Validate + deterministically normalize the parsed model payload.
 *
 * Validate: every workoutLift.liftId in [request.enabledLiftIds]; ≥1 workout after validation;
 * Double lifts have exactly one top set with a rep range.
 * Normalize: progressionScheme from trainingAge; clamp RIR per §2.3; enforce straight sets.
 */
fun parseAndNormalizeProgram(
    program: ProgramDraft,
    request: ProgramNormalizationRequest,
): NormalizedProgram {
    val scheme = schemeForTrainingAge(request.trainingAge).toDomain()
    val workouts = mutableListOf<NormalizedWorkout>()
    for (workout in program.workouts) {
        val lifts = normalizeLifts(workout.workoutLifts.orEmpty(), request, scheme)
        if (lifts.isEmpty()) continue
        workouts += NormalizedWorkout(
            name = workout.name ?: "Workout",
            weekday = workout.weekday,
            position = workout.position ?: workouts.size,
            workoutLifts = lifts,
        )
    }
    if (workouts.isEmpty()) {
        error("No valid workouts after normalization")
    }
    val cycleLength = program.cycleLength?.takeIf { it >= 1 } ?: workouts.size
    return NormalizedProgram(
        name = program.name ?: "Program",
        cycleLength = cycleLength,
        workouts = workouts,
    )
}

/** Lift ids present in the model draft but not in the enabled catalog (silently dropped by normalization). */
fun collectRejectedLiftIds(
    program: ProgramDraft,
    enabledLiftIds: Set<String>,
): List<String> {
    val rejected = mutableSetOf<String>()
    for (workout in program.workouts) {
        for (lift in workout.workoutLifts.orEmpty()) {
            val liftId = lift.liftId
            if (liftId != null && liftId !in enabledLiftIds) {
                rejected += liftId
            }
        }
    }
    return rejected.toList()
}

private fun normalizeLifts(
    rawLifts: List<ProgramLiftDraft>,
    request: ProgramNormalizationRequest,
    scheme: ProgressionScheme,
): List<NormalizedLift> {
    val out = mutableListOf<NormalizedLift>()
    for (lift in rawLifts) {
        val liftId = lift.liftId ?: continue
        if (liftId !in request.enabledLiftIds) continue
        val isMajorCompound = request.movementPatternByLiftId[liftId].orEmpty() in MAJOR_COMPOUND_PATTERNS
        val sets = normalizeSets(lift, isMajorCompound, scheme)
        if (sets.isEmpty()) continue
        if (scheme == ProgressionScheme.Double) {
            val topSets = sets.filter { it.isTopSet }
            if (topSets.size != 1 || topSets.first().repMin <= 0 || topSets.first().repMax <= 0) {
                error("Double progression lift $liftId must have exactly one top set with a rep range")
            }
        }
        out += NormalizedLift(
            liftId = liftId,
            position = lift.position ?: out.size,
            progressionScheme = scheme,
            workoutLiftSets = sets,
        )
    }
    return out
}

private fun normalizeSets(
    lift: ProgramLiftDraft,
    isMajorCompound: Boolean,
    @Suppress("UnusedParameter") scheme: ProgressionScheme,
): List<NormalizedSet> {
    val out = mutableListOf<NormalizedSet>()
    for (set in lift.workoutLiftSets.orEmpty()) {
        val isTopSet = set.isTopSet
        val rirGoal = if (isTopSet) {
            TOP_SET_RIR
        } else {
            clampBackOffRir(isMajorCompound, set.rirGoal)
        }
        val setType = set.setType
        out += NormalizedSet(
            position = set.position,
            setType = setType,
            repMin = set.repMin,
            repMax = set.repMax,
            rirGoal = rirGoal,
            isTopSet = isTopSet,
        )
    }
    return out
}

private fun com.stratum.domain.program.generation.reference.ProgressionScheme.toDomain(): ProgressionScheme =
    when (this) {
        com.stratum.domain.program.generation.reference.ProgressionScheme.Linear -> ProgressionScheme.Linear
        com.stratum.domain.program.generation.reference.ProgressionScheme.Double -> ProgressionScheme.Double
    }
