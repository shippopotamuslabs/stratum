@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.volume

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.program.generation.context.MAJOR_MUSCLES
import com.stratum.domain.program.generation.context.MuscleVolumeSnapshotContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.response.modeling.VolumePosture
import kotlin.math.max
import kotlin.math.min

/** Per-muscle weekly fractional set target. See [resolveUnscaledTarget] for semantics. */
data class WeeklyVolumeTarget(
    val min: Double,
    val aim: Double,
    val max: Double,
)

typealias WeeklyVolumeTargets = Map<String, WeeklyVolumeTarget>

/**
 * Per-muscle weekly volume target given specialization and import snapshots.
 *
 * - **MV** ([MuscleVolumeSnapshotContext.retentionFloorMv]): retention floor
 * - **PV** ([MuscleVolumeSnapshotContext.paretoVolumePv]): growth aim for Maintenance
 * - **RC** ([MuscleVolumeSnapshotContext.recoverabilityCeilingRc]): Specialize fill target and ceiling
 */
internal fun resolveUnscaledTarget(
    muscle: String,
    specializationLevel: String,
    context: ProgramGenerationContext,
): WeeklyVolumeTarget {
    val snapshot = context.muscleVolumeSnapshotsByMuscle[muscle]
    val globals = GlobalVolumePriors.forTrainingAgeName(context.trainingAge)
    val mv = snapshot?.retentionFloorMv ?: globals.retentionFloorMv
    val pv = snapshot?.paretoVolumePv ?: globals.paretoVolumePv
    val rc = snapshot?.recoverabilityCeilingRc ?: globals.recoverabilityCeilingRc
    val entry = snapshot?.growthEntryFloor ?: globals.growthEntryFloor
    val maintenanceMax = snapshot?.maintenanceCeiling ?: globals.maintenanceCeiling

    return when (specializationLevel) {
        "Specialize" ->
            WeeklyVolumeTarget(
                min = entry,
                aim = rc,
                max = rc,
            )
        "MinimumVolume" ->
            WeeklyVolumeTarget(
                min = mv,
                aim = mv,
                max = entry,
            )
        else ->
            WeeklyVolumeTarget(
                min = mv,
                aim = pv,
                max = maintenanceMax,
            )
    }
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

internal fun targetableMuscles(context: ProgramGenerationContext): List<String> {
    val muscles = MAJOR_MUSCLES.toMutableSet()
    muscles.addAll(context.muscleVolumeSnapshotsByMuscle.keys)
    muscles.addAll(context.specialization.keys)
    return muscles.filter { muscle ->
        (context.specialization[muscle] ?: "Maintenance") != "Omit"
    }
}

fun computeAllWeeklyTargets(context: ProgramGenerationContext): WeeklyVolumeTargets {
    val muscles = targetableMuscles(context)
    val unscaled = buildUnscaledWeeklyTargets(muscles, context)
    return applyWeeklyTimeBudgetPrecedence(unscaled, context)
}

fun availablePatternsByMuscle(context: ProgramGenerationContext): Map<String, List<String>> {
    val byMuscle = mutableMapOf<String, MutableSet<String>>()
    for (lift in context.liftsById.values) {
        for (muscle in lift.primaryMuscles + lift.secondaryMuscles) {
            byMuscle.getOrPut(muscle) { mutableSetOf() }.add(lift.movementPattern)
        }
    }
    return byMuscle.mapValues { (_, patterns) -> patterns.toList() }
}

/** True when import data shows the muscle is already trained near population recoverability limits. */
fun isSaturatedSpecializeRisk(muscle: String, context: ProgramGenerationContext): Boolean {
    val snapshot = context.muscleVolumeSnapshotsByMuscle[muscle] ?: return false
    if (snapshot.posture == VolumePosture.Saturated.name) return true
    val observedMax = snapshot.observedDoseMax ?: return false
    val observedMedian = snapshot.observedDoseMedian ?: return false
    return observedMax >= GlobalVolumePriors.RECOVERABILITY_CEILING_RC * 0.9 &&
        observedMedian >= GlobalVolumePriors.PARETO_VOLUME_PV * 0.85
}
