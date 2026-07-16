@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.schema

import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.program.ProgressionRepRangePolicy
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.lift.MAJOR_COMPOUND_PATTERNS
import com.stratum.domain.program.generation.reference.TOP_SET_RIR
import com.stratum.domain.program.generation.reference.clampBackOffRir
import com.stratum.domain.program.generation.reference.schemeForTrainingAge

/**
 * Validate + deterministically normalize the parsed model payload.
 *
 * Validate: every workoutLift.liftId in [request.enabledLiftIds]; ≥1 workout after validation;
 * Double lifts must arrive with at least one positive rep range (pre-heal) so we never invent
 * a prescription band from nothing.
 * Normalize: progressionScheme from trainingAge; clamp RIR per §2.3; [ProgressionRepRangePolicy]
 * for top-set + rep-range shape (Double RIR-only backoffs, Linear shared band).
 */
fun parseAndNormalizeProgram(
    program: ProgramDraft,
    request: ProgramNormalizationRequest,
): NormalizedProgram {
    val workouts = mutableListOf<NormalizedWorkout>()
    for (workout in program.workouts) {
        val lifts = normalizeLifts(workout.workoutLifts.orEmpty(), request)
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
    val restSlotIndices = program.restSlotIndices.orEmpty()
    val cycleLength =
        program.cycleLength?.takeIf { it >= 1 }
            ?: (workouts.size + restSlotIndices.size).coerceAtLeast(1)
    return NormalizedProgram(
        name = program.name ?: "Program",
        cycleLength = cycleLength,
        restSlotIndices = restSlotIndices,
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
): List<NormalizedLift> {
    val trainingAge = parseTrainingAge(request.trainingAge)
    val lastTrained = parseLastTrained(request.lastTrained)
    val out = mutableListOf<NormalizedLift>()
    for (lift in rawLifts) {
        val liftId = lift.liftId ?: continue
        if (liftId !in request.enabledLiftIds) continue
        val liftPattern = request.movementPatternByLiftId[liftId]
        val isMajorCompound = liftPattern != null && liftPattern in MAJOR_COMPOUND_PATTERNS
        val scheme = schemeForTrainingAge(trainingAge, lastTrained, isMajorCompound)
        // Fail closed before heal: Double needs a real band from the draft/model.
        // Shape normalize will place it on the sole top set and zero backoffs.
        if (scheme == ProgressionScheme.Double) {
            val drafts = lift.workoutLiftSets.orEmpty()
            val hasBand = drafts.any { set -> set.repMin > 0 || set.repMax > 0 }
            if (drafts.isNotEmpty() && !hasBand) {
                error("Double progression lift $liftId must have a rep range on at least one set")
            }
        }
        val sets = normalizeSets(lift, isMajorCompound, scheme)
        if (sets.isEmpty()) continue
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
    scheme: ProgressionScheme,
): List<NormalizedSet> {
    val drafts = lift.workoutLiftSets.orEmpty().sortedBy { it.position }
    if (drafts.isEmpty()) return emptyList()

    // Canonical top-set + rep-range shape (Double RIR-only backoffs, Linear shared band).
    val repSpecs =
        ProgressionRepRangePolicy.normalize(
            scheme = scheme,
            orderedSets =
                drafts.map { draft ->
                    ProgressionRepRangePolicy.WorkingSetRepSpec(
                        repMin = draft.repMin,
                        repMax = draft.repMax,
                        isTopSet = draft.isTopSet,
                    )
                },
        )

    return drafts.zip(repSpecs).map { (draft, spec) ->
        val rirGoal =
            if (spec.isTopSet) {
                TOP_SET_RIR
            } else {
                clampBackOffRir(isMajorCompound, draft.rirGoal)
            }
        NormalizedSet(
            position = draft.position,
            setType = draft.setType,
            repMin = spec.repMin,
            repMax = spec.repMax,
            rirGoal = rirGoal,
            isTopSet = spec.isTopSet,
        )
    }
}

private fun parseTrainingAge(trainingAge: String): TrainingAge? =
    runCatching { TrainingAge.valueOf(trainingAge) }.getOrNull()

private fun parseLastTrained(lastTrained: String?): LastTrainedBucket? =
    lastTrained?.let { runCatching { LastTrainedBucket.valueOf(it) }.getOrNull() }
