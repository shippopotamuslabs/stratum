@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.swap

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.isCompoundLift
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.selection.LiftCandidate
import com.stratum.domain.program.generation.selection.SelectedSlotLift
import com.stratum.domain.program.generation.selection.compareLiftCandidates
import com.stratum.domain.program.generation.selection.liftCatalogQualityAdjustment
import com.stratum.domain.program.generation.selection.scoreLift

interface LiftSwapPort {
    suspend fun swapLifts(request: LiftSwapRequest): Result<LiftSwapResult>
}

private fun specializedMuscles(context: ProgramGenerationContext): List<String> =
    context.specialization
        .filter { (_, level) -> level == SpecializationLevel.Specialize }
        .keys
        .map { it.name }
        .toList()

private fun liftToCandidatePayload(lift: LiftContext): LiftSwapCandidatePayload =
    LiftSwapCandidatePayload(
        liftId = lift.id,
        name = lift.name.ifEmpty { lift.id },
        movementPattern = lift.movementPattern?.name ?: "Unknown",
        primaryMuscles = lift.primaryMuscles.map { it.name },
    )

private fun swapCandidateScore(lift: LiftContext, context: ProgramGenerationContext): Double =
    scoreLift(lift, context) + liftCatalogQualityAdjustment(lift)

private fun matchesPattern(lift: LiftContext, pattern: MovementPattern?): Boolean =
    lift.movementPattern == pattern || (pattern != null && pattern in lift.movementPatterns)

private fun liftContextToCandidate(lift: LiftContext, context: ProgramGenerationContext): LiftCandidate =
    LiftCandidate(
        liftId = lift.id,
        name = lift.name,
        pattern = lift.movementPattern,
        priority = swapCandidateScore(lift, context),
        priorityTier = if (lift.proven == true) "proven" else "available",
        isCompound = lift.isCompoundLift,
        mainstream = lift.mainstream,
        primaryMuscles = lift.primaryMuscles,
        secondaryMuscles = lift.secondaryMuscles,
    )

/** Enabled lifts that could replace [currentLiftId] in the same slot (pattern + primary overlap). */
fun alternativesForLift(
    context: ProgramGenerationContext,
    currentLiftId: String,
    seedCandidateIds: List<String>,
): List<String> {
    val current = context.liftsById[currentLiftId] ?: return seedCandidateIds.distinct()
    val primaryMuscles = current.primaryMuscles.toSet()
    val pattern = current.movementPattern

    val ranked = context.liftsById.values
        .asSequence()
        .filter { it.id in context.enabledLiftIds }
        .filter { it.id != currentLiftId }
        .filter { matchesPattern(it, pattern) }
        .filter { lift -> lift.primaryMuscles.any { it in primaryMuscles } }
        .sortedWith { a, b ->
            val scoreDiff = swapCandidateScore(b, context) - swapCandidateScore(a, context)
            if (scoreDiff != 0.0) scoreDiff.compareTo(0.0)
            else compareLiftCandidates(liftContextToCandidate(a, context), liftContextToCandidate(b, context))
        }
        .map { it.id }
        .toList()

    val merged = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (id in seedCandidateIds + ranked + currentLiftId) {
        if (id in seen || id !in context.enabledLiftIds) continue
        seen += id
        merged += id
    }
    return merged
        .sortedByDescending { id ->
            context.liftsById[id]?.let { swapCandidateScore(it, context) } ?: 0.0
        }
        .take(LIFT_SWAP_CANDIDATES_PER_LIFT)
}

fun buildSwapCandidateMap(
    program: ProgramDraft,
    context: ProgramGenerationContext,
    selectionsBySession: List<List<SelectedSlotLift>>,
): LiftSwapCandidateMap {
    val byWorkout = mutableMapOf<Int, Map<Int, List<String>>>()
    program.workouts.forEachIndexed { workoutPosition, workout ->
        val selectionSeeds = selectionsBySession.getOrElse(workoutPosition) { emptyList() }
        val liftMap = mutableMapOf<Int, List<String>>()
        workout.workoutLifts.orEmpty().forEachIndexed { liftPosition, lift ->
            val liftId = lift.liftId.orEmpty()
            val seedIds = selectionSeeds.getOrNull(liftPosition)?.candidates?.map { it.id }.orEmpty()
            liftMap[liftPosition] = alternativesForLift(context, liftId, seedIds)
        }
        byWorkout[workoutPosition] = liftMap
    }
    return LiftSwapCandidateMap(byWorkout = byWorkout)
}

fun buildLiftSwapReviewPayload(
    program: ProgramDraft,
    context: ProgramGenerationContext,
    candidateMap: LiftSwapCandidateMap,
): LiftSwapReviewPayload {
    val cycleLength = program.cycleLength ?: program.workouts.size
    return LiftSwapReviewPayload(
        promptVersion = LIFT_SWAP_PROMPT_VERSION,
        intent = LiftSwapProgramIntentPayload(
            trainingAge = context.trainingAge,
            sessionLengthMinutes = context.sessionLengthMinutes,
            trainingDaysPerWeek = context.trainingDaysPerWeek,
            cycleLength = cycleLength,
            specialization = context.specialization.mapKeys { it.key.name }.mapValues { it.value.name },
            specializedMuscles = specializedMuscles(context),
        ),
        program = LiftSwapProgramPayload(
            cycleLength = cycleLength,
            workouts = program.workouts.mapIndexed { workoutPosition, workout ->
                LiftSwapWorkoutPayload(
                    position = workout.position ?: workoutPosition,
                    name = workout.name ?: "Workout ${workoutPosition + 1}",
                    workoutLifts = workout.workoutLifts.orEmpty().mapIndexed { liftPosition, lift ->
                        val liftId = lift.liftId.orEmpty()
                        val meta = context.liftsById[liftId]
                        val sets = lift.workoutLiftSets.orEmpty()
                        val repMins = sets.map { it.repMin }
                        val repMaxs = sets.map { it.repMax }
                        val candidateIds = candidateMap.byWorkout[workoutPosition]?.get(liftPosition).orEmpty()
                        LiftSwapExercisePayload(
                            position = liftPosition,
                            liftId = liftId,
                            name = meta?.name ?: liftId,
                            movementPattern = meta?.movementPattern?.name ?: "Unknown",
                            primaryMuscles = meta?.primaryMuscles.orEmpty().map { it.name },
                            secondaryMuscles = meta?.secondaryMuscles.orEmpty().map { it.name },
                            setCount = sets.size,
                            repMin = repMins.minOrNull(),
                            repMax = repMaxs.maxOrNull(),
                            candidates = candidateIds
                                .mapNotNull { id -> context.liftsById[id] }
                                .map(::liftToCandidatePayload),
                        )
                    },
                )
            },
        ),
    )
}

fun validateSwaps(
    swaps: List<SwapRequest>,
    program: ProgramDraft,
    candidateMap: LiftSwapCandidateMap,
): List<SwapRequest> {
    val valid = mutableListOf<SwapRequest>()
    val perWorkout = mutableMapOf<Int, Int>()
    for (swap in swaps) {
        if (swap.workoutPosition < 0 || swap.workoutPosition >= program.workouts.size) continue
        val workout = program.workouts[swap.workoutPosition]
        val lifts = workout.workoutLifts.orEmpty()
        if (swap.liftPosition < 0 || swap.liftPosition >= lifts.size) continue
        val currentLiftId = lifts[swap.liftPosition].liftId
        if (swap.newLiftId == currentLiftId) continue
        val candidates = candidateMap.byWorkout[swap.workoutPosition]?.get(swap.liftPosition)
        if (candidates == null || swap.newLiftId !in candidates) continue
        val count = perWorkout[swap.workoutPosition] ?: 0
        if (count >= LIFT_SWAP_MAX_PER_WORKOUT) continue
        perWorkout[swap.workoutPosition] = count + 1
        valid += swap
    }
    return valid
}

fun applySwaps(program: ProgramDraft, swaps: List<SwapRequest>): ProgramDraft {
    if (swaps.isEmpty()) return program
    val workouts = program.workouts.toMutableList()
    for (swap in swaps) {
        val workout = workouts.getOrNull(swap.workoutPosition) ?: continue
        val lifts = workout.workoutLifts.orEmpty().toMutableList()
        val lift = lifts.getOrNull(swap.liftPosition) ?: continue
        lifts[swap.liftPosition] = lift.copy(liftId = swap.newLiftId)
        workouts[swap.workoutPosition] = workout.copy(workoutLifts = lifts)
    }
    return program.copy(workouts = workouts)
}

fun buildLiftSwapRequest(
    program: ProgramDraft,
    context: ProgramGenerationContext,
    selectionsBySession: List<List<SelectedSlotLift>>,
): LiftSwapRequest {
    val candidateMap = buildSwapCandidateMap(program, context, selectionsBySession)
    val reviewPayload = buildLiftSwapReviewPayload(program, context, candidateMap)
    return LiftSwapRequest(reviewPayload = reviewPayload, candidateMap = candidateMap)
}
