@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.context.MAJOR_MUSCLES
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import kotlin.math.max
import kotlin.math.min

/** Per-muscle weekly hard-set credit target. `min` is MV for maintenance, max(mev, mav−tolerance) for Specialize;
 * `aim` is MAV (maintenance) or MRV (specialize); `max` is MRV ceiling. */
data class WeeklyVolumeTarget(
    val min: Double,
    val aim: Double,
    val max: Double,
)

typealias WeeklyVolumeTargets = Map<String, WeeklyVolumeTarget>

/** Hard-set credit tolerance applied to MEV/MAV/MRV bounds (matches the validators). */
private const val VOLUME_TOLERANCE_SETS = 2

/**
 * Per-muscle weekly volume target for [muscle] given specialization, return-to-training
 * conservatism, and volume-response evidence. Extracted verbatim from the former private
 * `resolveVolumeTarget` in `programValidation.ts` — do not change behavior without
 * updating the matching validator tests.
 */
internal fun resolveUnscaledTarget(
    muscle: String,
    specializationLevel: String,
    context: ProgramGenerationContext,
): WeeklyVolumeTarget {
    val landmark = context.volumeLandmarksByMuscle[muscle]
    val mev = landmark?.estimatedMev ?: 8.0
    val mav = landmark?.estimatedMav ?: 14.0
    val mrv = landmark?.estimatedMrv ?: 20.0
    val pv = landmark?.estimatedPv ?: 11.0

    val lastTrained = context.lastTrained
    val conservative =
        lastTrained == "SixToTwelveMonthsAgo" ||
            lastTrained == "MoreThanAYearAgo" ||
            lastTrained == "Never"

    if (specializationLevel == "Specialize") {
        return WeeklyVolumeTarget(
            min = max(mev, mav - VOLUME_TOLERANCE_SETS),
            aim = mrv,
            max = mrv,
        )
    }

    if (specializationLevel == "MinimumVolume") {
        val mv = maintenanceMvFloor(landmark, mev)
        return WeeklyVolumeTarget(min = mv, aim = mv, max = mev)
    }

    val mv = maintenanceMvFloor(landmark, mev)
    if (conservative) {
        return WeeklyVolumeTarget(min = mv, aim = pv, max = mav)
    }
    return WeeklyVolumeTarget(min = mv, aim = mav, max = mrv)
}

internal fun trainableMuscles(context: ProgramGenerationContext): List<String> {
    val patternsByMuscle = availablePatternsByMuscle(context)
    return targetableMuscles(context).filter { muscle ->
        (patternsByMuscle[muscle]?.size ?: 0) > 0
    }
}

fun resolveMuscleWeeklyTarget(
    muscle: String,
    specializationLevel: String,
    context: ProgramGenerationContext,
): WeeklyVolumeTarget {
    val batch = computeAllWeeklyTargets(context)
    return batch[muscle] ?: resolveUnscaledTarget(muscle, specializationLevel, context)
}

internal fun buildUnscaledWeeklyTargets(
    muscles: List<String>,
    context: ProgramGenerationContext,
): WeeklyVolumeTargets =
    muscles.associateWith { muscle ->
        val level = context.specialization[muscle] ?: "Maintenance"
        resolveUnscaledTarget(muscle, level, context)
    }

/**
 * The muscles Phase 0 must plan volume for: every major muscle, any muscle with a
 * personal/literature landmark, and any muscle the user explicitly specialized or omitted.
 * Omitted muscles are excluded from targeting (they receive zero volume).
 */
internal fun targetableMuscles(context: ProgramGenerationContext): List<String> {
    val muscles = MAJOR_MUSCLES.toMutableSet()
    muscles.addAll(context.volumeLandmarksByMuscle.keys)
    muscles.addAll(context.specialization.keys)
    return muscles.filter { muscle ->
        (context.specialization[muscle] ?: "Maintenance") != "Omit"
    }
}

/**
 * Computes the per-muscle weekly volume target for every non-Omitted muscle, keyed by muscle.
 * Consumed by the split allocator (Phase 0) and the LLM split planner (Phase 1, 5–6 days).
 */
fun computeAllWeeklyTargets(context: ProgramGenerationContext): WeeklyVolumeTargets {
    val muscles = targetableMuscles(context)
    val unscaled = buildUnscaledWeeklyTargets(muscles, context)
    return applyWeeklyTimeBudgetPrecedence(unscaled, context)
}

/**
 * For each muscle, the movement patterns the user has at least one enabled lift for. Used by
 * the split allocator's feasibility guard (never plan a slot you can't fill) and the LLM split
 * planner (knows a "Chest touch" day is possible). Patterns are drawn from the payload's
 * proven/available lift pool — both tiers are already filtered to enabled lifts on the client.
 */
fun availablePatternsByMuscle(context: ProgramGenerationContext): Map<String, List<String>> {
    val byMuscle = mutableMapOf<String, MutableSet<String>>()
    for (lift in context.liftsById.values) {
        for (muscle in lift.primaryMuscles + lift.secondaryMuscles) {
            byMuscle.getOrPut(muscle) { mutableSetOf() }.add(lift.movementPattern)
        }
    }
    return byMuscle.mapValues { (_, patterns) -> patterns.toList() }
}
