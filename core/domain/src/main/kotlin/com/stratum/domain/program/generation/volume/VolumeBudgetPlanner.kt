@file:Suppress("MagicNumber", "ReturnCount")

package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.VolumeLandmarkContext
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import kotlin.math.max
import kotlin.math.round

internal fun maintenanceMvFloor(landmark: VolumeLandmarkContext?, mev: Double): Double =
    landmark?.estimatedMv ?: max(2.0, mev * 0.45)

internal fun maintenancePvGoal(landmark: VolumeLandmarkContext?, mev: Double): Double =
    landmark?.estimatedPv ?: max(maintenanceMvFloor(landmark, mev), mev * 0.75)

/**
 * Muscle-week aims sum above raw weekly hard-set slots because compounds credit multiple muscles
 * per set. Scale the budget so maintenance is not trimmed to MV when sessions can still deliver PV.
 */
internal const val WEEKLY_MUSCLE_AIM_OVERLAP_FACTOR = 1.35

/**
 * When session time cannot fit every unscaled aim, preserve specialized [WeeklyVolumeTarget.aim]
 * values (MRV) and trim maintenance toward PV before MV.
 */
internal fun applyWeeklyTimeBudgetPrecedence(
    unscaled: Map<String, WeeklyVolumeTarget>,
    context: ProgramGenerationContext,
): WeeklyVolumeTargets {
    val sessionLength = context.sessionLengthMinutes
    if (sessionLength == null || sessionLength <= 0) return unscaled
    val perSessionCap = checkNotNull(sessionHardSetCapForLength(sessionLength))
    val weeklyTimeBudget =
        context.trainingDaysPerWeek * perSessionCap.toDouble() * WEEKLY_MUSCLE_AIM_OVERLAP_FACTOR
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
    repeat(100) {
        if (maintenanceMuscles.sumOf { muscle -> trimmed.getValue(muscle).aim } <= maintenanceBudget + 0.01) {
            return trimmed
        }
        val trimmedTowardPv =
            trimMaintenanceExcess(
                trimmed = trimmed,
                maintenanceMuscles = maintenanceMuscles,
                maintenanceBudget = maintenanceBudget,
                trimFloor = { muscle -> maintenancePvTrimFloor(muscle, trimmed.getValue(muscle), context) },
            )
        if (trimmedTowardPv) return@repeat
        trimMaintenanceExcess(
            trimmed = trimmed,
            maintenanceMuscles = maintenanceMuscles,
            maintenanceBudget = maintenanceBudget,
            trimFloor = { muscle -> trimmed.getValue(muscle).min },
        )
    }
    return trimmed
}

private fun maintenancePvTrimFloor(
    muscle: String,
    target: WeeklyVolumeTarget,
    context: ProgramGenerationContext,
): Double {
    val landmark = context.volumeLandmarksByMuscle[muscle]
    val mev = landmark?.estimatedMev ?: 8.0
    return max(target.min, maintenancePvGoal(landmark, mev))
}

/** Returns true when any maintenance aim was reduced toward [trimFloor]. */
private fun trimMaintenanceExcess(
    trimmed: MutableMap<String, WeeklyVolumeTarget>,
    maintenanceMuscles: List<String>,
    maintenanceBudget: Double,
    trimFloor: (String) -> Double,
): Boolean {
    val maintenanceTotal = maintenanceMuscles.sumOf { muscle -> trimmed.getValue(muscle).aim }
    val reducible =
        maintenanceMuscles.filter { muscle ->
            trimmed.getValue(muscle).aim > trimFloor(muscle)
        }
    if (reducible.isEmpty()) return false
    val excess = maintenanceTotal - maintenanceBudget
    if (excess <= 0.0) return false
    val totalReducible =
        reducible.sumOf { muscle ->
            val target = trimmed.getValue(muscle)
            target.aim - trimFloor(muscle)
        }
    if (totalReducible <= 0.0) return false
    for (muscle in reducible) {
        val target = trimmed.getValue(muscle)
        val floor = trimFloor(muscle)
        val share = (target.aim - floor) / totalReducible * excess
        trimmed[muscle] =
            target.copy(
                aim = max(floor, round((target.aim - share) * 2) / 2.0),
            )
    }
    return true
}

private fun specializationLevel(muscle: String, context: ProgramGenerationContext): String =
    context.specialization[muscle] ?: "Maintenance"

private fun proportionalScaleTargets(
    targets: Map<String, WeeklyVolumeTarget>,
    weeklyTimeBudget: Double,
): WeeklyVolumeTargets {
    val totalAim = targets.values.sumOf { target -> target.aim }
    if (totalAim <= 0.0) return targets
    val scale = weeklyTimeBudget / totalAim
    return targets.mapValues { (_, target) ->
        WeeklyVolumeTarget(
            min = max(2.0, round(target.min * scale * 2) / 2.0),
            aim = max(2.0, round(target.aim * scale * 2) / 2.0),
            max = max(2.0, round(target.max * scale * 2) / 2.0),
        )
    }
}
