package com.stratum.domain.lift

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Quantity of a specific weight plate denomination to load per side of the bar.
 */
data class PlateCount(
    val weight: Double,
    val count: Int,
)

/**
 * Named bar weight option. Labels are presentation-owned; this is identity + mass only.
 */
enum class BarPresetKind {
    Olympic,
    Womens,
    Squat,
    Ez,
    Technique,
    Trap,
    None,
}

data class BarPreset(
    val kind: BarPresetKind,
    val weight: Double,
)

/**
 * Possible validation or matching issues when calculating required plates.
 */
enum class PlateCalculationIssue {
    /** Target weight was successfully met with available plates. */
    None,

    /** Target weight is below the minimum bar weight. */
    BelowBarWeight,

    /** Target weight cannot be evenly composed with the currently enabled plates. */
    CannotMatchWithAvailablePlates,
}

/**
 * Result of a plate calculation.
 */
data class PlateCalculationResult(
    val targetWeight: Double,
    val barWeight: Double,
    val isDoubleSided: Boolean,
    val platesPerSide: List<PlateCount>,
    val totalWeight: Double,
    val remainder: Double,
    val issue: PlateCalculationIssue,
) {
    val isValid: Boolean get() = issue == PlateCalculationIssue.None
    val totalPlateWeightPerSide: Double get() = platesPerSide.sumOf { it.weight * it.count }
    val totalPlateWeight: Double get() = totalPlateWeightPerSide * (if (isDoubleSided) 2.0 else 1.0)
}

/**
 * Deterministic domain calculator for weight plates on a barbell or plate-loaded sleeve.
 * Pure math over doubles.
 */
object PlateCalculator {

    private const val EPSILON = 1e-4

    const val OLYMPIC_BAR_LB: Double = 45.0
    const val WOMENS_BAR_LB: Double = 35.0
    const val SQUAT_BAR_LB: Double = 55.0
    const val EZ_BAR_LB: Double = 25.0
    const val TECHNIQUE_BAR_LB: Double = 15.0
    const val TRAP_BAR_LB: Double = 60.0

    const val OLYMPIC_BAR_KG: Double = 20.0
    const val WOMENS_BAR_KG: Double = 15.0
    const val SQUAT_BAR_KG: Double = 25.0
    const val EZ_BAR_KG: Double = 10.0
    const val TECHNIQUE_BAR_KG: Double = 7.5
    const val TRAP_BAR_KG: Double = 25.0

    val DEFAULT_PLATES_LB: List<Double> = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
    val ALL_PLATES_LB: List<Double> = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25)

    val DEFAULT_PLATES_KG: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    val ALL_PLATES_KG: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)

    fun defaultBarWeight(isKg: Boolean): Double =
        if (isKg) OLYMPIC_BAR_KG else OLYMPIC_BAR_LB

    fun defaultAvailablePlates(isKg: Boolean): List<Double> =
        if (isKg) DEFAULT_PLATES_KG else DEFAULT_PLATES_LB

    fun allAvailablePlates(isKg: Boolean): List<Double> =
        if (isKg) ALL_PLATES_KG else ALL_PLATES_LB

    fun barPresets(isKg: Boolean): List<BarPreset> =
        if (isKg) {
            listOf(
                BarPreset(BarPresetKind.Olympic, OLYMPIC_BAR_KG),
                BarPreset(BarPresetKind.Womens, WOMENS_BAR_KG),
                BarPreset(BarPresetKind.Squat, SQUAT_BAR_KG),
                BarPreset(BarPresetKind.Ez, EZ_BAR_KG),
                BarPreset(BarPresetKind.Technique, TECHNIQUE_BAR_KG),
                BarPreset(BarPresetKind.Trap, TRAP_BAR_KG),
                BarPreset(BarPresetKind.None, 0.0),
            )
        } else {
            listOf(
                BarPreset(BarPresetKind.Olympic, OLYMPIC_BAR_LB),
                BarPreset(BarPresetKind.Womens, WOMENS_BAR_LB),
                BarPreset(BarPresetKind.Squat, SQUAT_BAR_LB),
                BarPreset(BarPresetKind.Ez, EZ_BAR_LB),
                BarPreset(BarPresetKind.Technique, TECHNIQUE_BAR_LB),
                BarPreset(BarPresetKind.Trap, TRAP_BAR_LB),
                BarPreset(BarPresetKind.None, 0.0),
            )
        }

    /**
     * Calculates the plates to load per side given target weight, bar weight, and available plates.
     */
    fun calculatePlates(
        targetWeight: Double,
        barWeight: Double,
        availablePlates: List<Double>,
        isDoubleSided: Boolean = true,
    ): PlateCalculationResult {
        if (targetWeight <= 0.0) {
            return PlateCalculationResult(
                targetWeight = 0.0,
                barWeight = barWeight,
                isDoubleSided = isDoubleSided,
                platesPerSide = emptyList(),
                totalWeight = 0.0,
                remainder = 0.0,
                issue = PlateCalculationIssue.None,
            )
        }

        val effectiveBarWeight = barWeight.coerceAtLeast(0.0)
        if (targetWeight < effectiveBarWeight - EPSILON) {
            return PlateCalculationResult(
                targetWeight = targetWeight,
                barWeight = effectiveBarWeight,
                isDoubleSided = isDoubleSided,
                platesPerSide = emptyList(),
                totalWeight = effectiveBarWeight,
                remainder = targetWeight - effectiveBarWeight,
                issue = PlateCalculationIssue.BelowBarWeight,
            )
        }

        return solvePlates(
            targetWeight = targetWeight,
            effectiveBarWeight = effectiveBarWeight,
            availablePlates = availablePlates,
            isDoubleSided = isDoubleSided,
        )
    }

    private fun solvePlates(
        targetWeight: Double,
        effectiveBarWeight: Double,
        availablePlates: List<Double>,
        isDoubleSided: Boolean,
    ): PlateCalculationResult {
        val neededTotalPlateWeight = targetWeight - effectiveBarWeight
        if (abs(neededTotalPlateWeight) < EPSILON) {
            return PlateCalculationResult(
                targetWeight = targetWeight,
                barWeight = effectiveBarWeight,
                isDoubleSided = isDoubleSided,
                platesPerSide = emptyList(),
                totalWeight = effectiveBarWeight,
                remainder = 0.0,
                issue = PlateCalculationIssue.None,
            )
        }

        val sideMultiplier = if (isDoubleSided) 2.0 else 1.0
        val targetPerSide = neededTotalPlateWeight / sideMultiplier
        val sortedPlates = availablePlates.filter { it > EPSILON }.distinct().sortedDescending()

        val greedy = greedyPlateCounts(targetPerSide, sortedPlates)
        val greedyLoaded = greedy.sumOf { it.weight * it.count }
        val exactMatch =
            if (abs(greedyLoaded - targetPerSide) < EPSILON) {
                greedy
            } else {
                computeExactPlates(targetPerSide, sortedPlates)
            }

        return if (exactMatch != null) {
            val totalPlateWeight = exactMatch.sumOf { it.weight * it.count } * sideMultiplier
            PlateCalculationResult(
                targetWeight = targetWeight,
                barWeight = effectiveBarWeight,
                isDoubleSided = isDoubleSided,
                platesPerSide = exactMatch,
                totalWeight = effectiveBarWeight + totalPlateWeight,
                remainder = 0.0,
                issue = PlateCalculationIssue.None,
            )
        } else {
            val loadedPlateWeight = greedyLoaded * sideMultiplier
            val totalWeight = effectiveBarWeight + loadedPlateWeight
            PlateCalculationResult(
                targetWeight = targetWeight,
                barWeight = effectiveBarWeight,
                isDoubleSided = isDoubleSided,
                platesPerSide = greedy,
                totalWeight = totalWeight,
                remainder = targetWeight - totalWeight,
                issue = PlateCalculationIssue.CannotMatchWithAvailablePlates,
            )
        }
    }

    private fun greedyPlateCounts(
        targetPerSide: Double,
        sortedPlates: List<Double>,
    ): List<PlateCount> {
        var remaining = targetPerSide
        val result = mutableListOf<PlateCount>()
        for (plate in sortedPlates) {
            if (remaining < plate - EPSILON) continue
            val count = floor((remaining + EPSILON) / plate).toInt()
            if (count > 0) {
                result.add(PlateCount(plate, count))
                remaining -= count * plate
            }
        }
        return result
    }

    private fun computeExactPlates(
        targetPerSide: Double,
        sortedPlates: List<Double>,
    ): List<PlateCount>? {
        val scale = 100L
        val targetScaled = (targetPerSide * scale).roundToLong()
        val plateScaled = sortedPlates.map { (it * scale).roundToLong() }

        var bestCombination: List<PlateCount>? = null
        var bestPlateCount = Int.MAX_VALUE

        fun search(
            index: Int,
            currentRemaining: Long,
            currentResult: MutableList<PlateCount>,
            totalPlatesSoFar: Int,
        ) {
            if (currentRemaining == 0L) {
                if (totalPlatesSoFar < bestPlateCount) {
                    bestPlateCount = totalPlatesSoFar
                    bestCombination = currentResult.toList()
                }
                return
            }
            if (index >= plateScaled.size || totalPlatesSoFar >= bestPlateCount) return

            val pScaled = plateScaled[index]
            val maxCount = (currentRemaining / pScaled).toInt()
            for (c in maxCount downTo 0) {
                if (c > 0) {
                    currentResult.add(PlateCount(sortedPlates[index], c))
                }
                search(
                    index = index + 1,
                    currentRemaining = currentRemaining - c * pScaled,
                    currentResult = currentResult,
                    totalPlatesSoFar = totalPlatesSoFar + c,
                )
                if (c > 0) {
                    currentResult.removeAt(currentResult.lastIndex)
                }
            }
        }

        search(0, targetScaled, mutableListOf(), 0)
        return bestCombination
    }
}
