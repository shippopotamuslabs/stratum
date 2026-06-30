@file:Suppress("MagicNumber")

package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.VolumeLandmarkContext
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

internal fun maintenanceMvFloor(landmark: VolumeLandmarkContext?, mev: Double): Double =
    landmark?.estimatedMv ?: max(2.0, mev * 0.45)

/**
 * When session time cannot fit every unscaled aim, preserve specialized [WeeklyVolumeTarget.aim]
 * values (MRV) and trim maintenance muscles toward their MV floor before touching specialize.
 */
internal fun applyWeeklyTimeBudgetPrecedence(
    unscaled: Map<String, WeeklyVolumeTarget>,
    context: ProgramGenerationContext,
): WeeklyVolumeTargets {
    val sessionLength = context.sessionLengthMinutes
    if (sessionLength == null || sessionLength <= 0) return unscaled
    val perSessionCap = checkNotNull(sessionHardSetCapForLength(sessionLength))
    val weeklyTimeBudget = context.trainingDaysPerWeek * perSessionCap.toDouble()
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
            return@repeat
        }
        val reducible =
            maintenanceMuscles.filter { muscle ->
                trimmed.getValue(muscle).aim > trimmed.getValue(muscle).min
            }
        if (reducible.isEmpty()) return@repeat
        val currentTotal = reducible.sumOf { muscle -> trimmed.getValue(muscle).aim }
        val excess = currentTotal - maintenanceBudget
        val totalReducible =
            reducible.sumOf { muscle ->
                trimmed.getValue(muscle).aim - trimmed.getValue(muscle).min
            }
        if (totalReducible <= 0.0) return@repeat
        for (muscle in reducible) {
            val target = trimmed.getValue(muscle)
            val share = (target.aim - target.min) / totalReducible * excess
            trimmed[muscle] =
                target.copy(
                    aim = max(target.min, round((target.aim - share) * 2) / 2.0),
                )
        }
    }
    return trimmed
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
