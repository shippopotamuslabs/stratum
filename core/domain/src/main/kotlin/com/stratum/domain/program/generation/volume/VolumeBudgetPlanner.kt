@file:Suppress("MagicNumber", "ReturnCount")

package com.stratum.domain.program.generation.volume

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.program.generation.context.MuscleVolumeSnapshotContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.sessionAimHardSetsForLength
import kotlin.math.max
import kotlin.math.round

internal fun maintenanceMvFloor(snapshot: MuscleVolumeSnapshotContext?): Double =
    snapshot?.retentionFloorMv ?: GlobalVolumePriors.forTrainingAge(null).retentionFloorMv

internal fun maintenancePvGoal(snapshot: MuscleVolumeSnapshotContext?): Double =
    snapshot?.paretoVolumePv ?: GlobalVolumePriors.PARETO_VOLUME_PV

/**
 * Muscle-week aims sum above raw weekly hard-set slots because compounds credit multiple muscles
 * per set. Scale the budget so maintenance is not trimmed to MV when sessions can still deliver PV.
 */
internal const val WEEKLY_MUSCLE_AIM_OVERLAP_FACTOR = 1.35

/** Major maintenance muscles trimmed last; mirrors solver antagonist preference (back/ham ahead). */
internal val MAINTENANCE_VOLUME_ANCHORS = setOf("Chest", "Back", "Quad", "Hamstring")

internal fun maintenanceTrimPriority(muscle: String): Int {
    if (muscle !in MAINTENANCE_VOLUME_ANCHORS) {
        return if (muscleTrainingDomain(muscle) == TrainingDomain.Lower) 0 else 1
    }
    return when (muscle) {
        "Quad" -> 2
        "Chest" -> 3
        "Hamstring" -> 4
        "Back" -> 5
        else -> 1
    }
}

/**
 * When session time cannot fit every unscaled aim, preserve specialized [WeeklyVolumeTarget.aim]
 * values (RC) and trim maintenance toward PV before MV.
 */
internal fun applyWeeklyTimeBudgetPrecedence(
    unscaled: Map<String, WeeklyVolumeTarget>,
    context: ProgramGenerationContext,
): WeeklyVolumeTargets {
    val sessionLength = context.sessionLengthMinutes
    if (sessionLength == null || sessionLength <= 0) return unscaled
    // Scale weekly aims from soft session aim (band midpoint), not hard max.
    val perSessionAim = checkNotNull(sessionAimHardSetsForLength(sessionLength))
    val weeklyTimeBudget =
        context.trainingDaysPerWeek * perSessionAim.toDouble() * WEEKLY_MUSCLE_AIM_OVERLAP_FACTOR
    val totalAim = unscaled.values.sumOf { target -> target.aim }
    if (totalAim <= weeklyTimeBudget) return unscaled

    val result = unscaled.mapValues { (_, target) -> target }.toMutableMap()
    val specializedAim =
        result
            .filter { (muscle, _) -> specializationLevel(muscle, context) == "Specialize" }
            .values
            .sumOf { target -> target.aim }
    val maintenanceBudget = weeklyTimeBudget - specializedAim
    if (maintenanceBudget < 0) {
        return proportionalScaleTargets(result, weeklyTimeBudget)
    }

    val maintenanceMuscles =
        result.keys.filter { muscle -> specializationLevel(muscle, context) == "Maintenance" }
    if (maintenanceMuscles.sumOf { muscle -> result.getValue(muscle).aim } <= maintenanceBudget) {
        return result
    }

    val trimmed = result.mapValues { (_, target) -> target.copy() }.toMutableMap()
    trimMaintenanceMusclesToBudget(
        trimmed = trimmed,
        maintenanceMuscles = maintenanceMuscles,
        maintenanceBudget = maintenanceBudget,
        context = context,
    )
    return trimmed
}

private fun trimMaintenanceMusclesToBudget(
    trimmed: MutableMap<String, WeeklyVolumeTarget>,
    maintenanceMuscles: List<String>,
    maintenanceBudget: Double,
    context: ProgramGenerationContext,
) {
    val ordered = maintenanceMuscles.sortedBy(::maintenanceTrimPriority)
    repeat(100) {
        if (maintenanceMuscles.sumOf { muscle -> trimmed.getValue(muscle).aim } <= maintenanceBudget + 0.01) {
            return
        }
        val trimmedTowardPv =
            trimMaintenanceExcessPrioritized(
                trimmed = trimmed,
                orderedMuscles = ordered,
                maintenanceBudget = maintenanceBudget,
                trimFloor = { muscle -> maintenanceTrimFloor(muscle, trimmed, context, towardPv = true) },
            )
        if (trimmedTowardPv) return@repeat
        trimMaintenanceExcessPrioritized(
            trimmed = trimmed,
            orderedMuscles = ordered,
            maintenanceBudget = maintenanceBudget,
            trimFloor = { muscle -> maintenanceTrimFloor(muscle, trimmed, context, towardPv = false) },
        )
    }
}

private fun roundToHalfSets(value: Double): Double = round(value * 2) / 2.0

private fun maintenancePvTrimFloor(
    muscle: String,
    target: WeeklyVolumeTarget,
    context: ProgramGenerationContext,
): Double {
    val snapshot = context.muscleVolumeSnapshotsByMuscle[muscle]
    return max(target.min, maintenancePvGoal(snapshot))
}

private fun maintenanceTrimFloor(
    muscle: String,
    trimmed: Map<String, WeeklyVolumeTarget>,
    context: ProgramGenerationContext,
    towardPv: Boolean,
): Double {
    val target = trimmed.getValue(muscle)
    val base =
        if (towardPv) {
            maintenancePvTrimFloor(muscle, target, context)
        } else {
            target.min
        }
    val antagonistPartner =
        when (muscle) {
            "Back" -> "Chest"
            "Hamstring" -> "Quad"
            else -> null
        }
    val partnerAim = antagonistPartner?.let { partner -> trimmed[partner]?.aim }
    return if (partnerAim != null) max(base, partnerAim) else base
}

private fun trimMaintenanceExcessPrioritized(
    trimmed: MutableMap<String, WeeklyVolumeTarget>,
    orderedMuscles: List<String>,
    maintenanceBudget: Double,
    trimFloor: (String) -> Double,
): Boolean {
    val maintenanceTotal = orderedMuscles.sumOf { muscle -> trimmed.getValue(muscle).aim }
    val excess = maintenanceTotal - maintenanceBudget
    if (excess <= 0.0) return false

    var remaining = excess
    var progress = false
    for (muscle in orderedMuscles) {
        if (remaining > 1e-9) {
            val target = trimmed.getValue(muscle)
            val floor = trimFloor(muscle)
            val reducible = target.aim - floor
            if (reducible > 0.0) {
                val cut = minOf(reducible, remaining)
                val newAim = roundToHalfSets(target.aim - cut).coerceIn(floor, target.aim)
                val actualCut = target.aim - newAim
                if (actualCut > 0.0) {
                    trimmed[muscle] = target.copy(aim = newAim)
                    remaining -= actualCut
                    progress = true
                }
            }
        }
    }
    return progress
}

private fun specializationLevel(muscle: String, context: ProgramGenerationContext): String =
    specializationLevelForMuscle(muscle, context)

private fun proportionalScaleTargets(
    targets: Map<String, WeeklyVolumeTarget>,
    weeklyTimeBudget: Double,
): WeeklyVolumeTargets {
    val totalAim = targets.values.sumOf { target -> target.aim }
    if (totalAim <= 0.0) return targets
    val scale = weeklyTimeBudget / totalAim
    return targets.mapValues { (_, target) ->
        WeeklyVolumeTarget(
            min = max(2.0, roundToHalfSets(target.min * scale)),
            aim = max(2.0, roundToHalfSets(target.aim * scale)),
            max = max(2.0, roundToHalfSets(target.max * scale)),
        )
    }
}
