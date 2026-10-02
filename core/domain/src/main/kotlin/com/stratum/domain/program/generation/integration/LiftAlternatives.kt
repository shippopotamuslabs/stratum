package com.stratum.domain.program.generation.integration

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.isCompoundLift
import com.stratum.domain.program.generation.selection.LiftCandidate
import com.stratum.domain.program.generation.selection.compareLiftCandidates
import com.stratum.domain.program.generation.selection.liftCatalogQualityAdjustment
import com.stratum.domain.program.generation.selection.scoreLift

/** Pattern-matched alternatives considered per exercise by variety/recovery repair. */
const val LIFT_ALTERNATIVES_PER_LIFT = 10

private fun alternativeScore(lift: LiftContext, context: ProgramGenerationContext): Double =
    scoreLift(lift, context) + liftCatalogQualityAdjustment(lift)

private fun matchesPattern(lift: LiftContext, pattern: MovementPattern?): Boolean =
    lift.movementPattern == pattern || (pattern != null && pattern in lift.movementPatterns)

private fun liftContextToCandidate(lift: LiftContext, context: ProgramGenerationContext): LiftCandidate =
    LiftCandidate(
        liftId = lift.id,
        name = lift.name,
        pattern = lift.movementPattern,
        priority = alternativeScore(lift, context),
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
    val current = context.liftsById[currentLiftId]
    val ranked: List<String> =
        if (current == null) {
            emptyList()
        } else {
            val primaryMuscles = current.primaryMuscles.toSet()
            val pattern = current.movementPattern
            context.liftsById.values
                .asSequence()
                .filter { it.id in context.enabledLiftIds }
                .filter { it.id != currentLiftId }
                .filter { matchesPattern(it, pattern) }
                .filter { lift -> lift.primaryMuscles.any { it in primaryMuscles } }
                .map { it.id }
                .toList()
        }
    return (seedCandidateIds + ranked + currentLiftId)
        .distinct()
        .filter { it in context.enabledLiftIds }
        .mapNotNull { id -> context.liftsById[id]?.let { lift -> id to liftContextToCandidate(lift, context) } }
        .sortedWith { a, b ->
            val scoreDiff = b.second.priority - a.second.priority
            if (scoreDiff != 0.0) scoreDiff.compareTo(0.0)
            else compareLiftCandidates(a.second, b.second)
        }
        .take(LIFT_ALTERNATIVES_PER_LIFT)
        .map { it.first }
}
