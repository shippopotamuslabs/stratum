package com.stratum.core.ui.component.platecalculator

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.stratum.domain.lift.PlateCalculationIssue
import com.stratum.domain.lift.PlateCalculator

/**
 * Visual/presentation representation of an individual plate loaded on a barbell sleeve.
 */
@Immutable
data class PlateItemUi(
    val weight: Double,
    val count: Int,
    val color: Color,
    val onColor: Color,
    val label: String,
    val heightFraction: Float,
    val widthDp: Dp,
)

/**
 * Status of the plate load calculation for presentation.
 */
enum class PlateLoadStatusUi {
    ExactMatch,
    BelowBarWeight,
    CannotMatchWithAvailablePlates,
}

/**
 * Pure presentation model for barbell weight plate calculations.
 * Contains all state required by the mini drawer and expanded calculator sheet.
 */
@Immutable
data class PlateLoadUi(
    val targetWeight: Double,
    val barWeight: Double,
    val unitLabel: String,
    val isDoubleSided: Boolean = true,
    val platesPerSide: List<PlateItemUi> = emptyList(),
    val totalWeight: Double = 0.0,
    val remainder: Double = 0.0,
    val status: PlateLoadStatusUi = PlateLoadStatusUi.ExactMatch,
) {
    val isValid: Boolean get() = status == PlateLoadStatusUi.ExactMatch
    val totalPlateWeightPerSide: Double get() = platesPerSide.sumOf { it.weight * it.count }
    val totalPlateWeight: Double get() = totalPlateWeightPerSide * (if (isDoubleSided) 2.0 else 1.0)
}

/**
 * Parses an in-progress weight field. Trailing decimals (`"135."`) keep the integer prefix
 * so the plate diagram does not jump to the recommendation mid-keystroke.
 */
fun parsePlateTargetWeight(text: String, fallback: Double): Double =
    text.trimEnd('.').toDoubleOrNull()
        ?: fallback.takeIf { it > 0.0 }
        ?: 0.0

/**
 * Ephemeral bar + plate-denomination selection for the set-log calculator.
 * Lives on the keyboard host for the current screen — not persisted to equipment.
 */
@Immutable
data class PlateInventoryUi(
    val barWeight: Double,
    val enabledPlates: Set<Double>,
    val unitLabel: String,
)

fun defaultPlateInventory(unitLabel: String): PlateInventoryUi {
    val isKg = unitLabel.equals("kg", ignoreCase = true)
    return PlateInventoryUi(
        barWeight = PlateCalculator.defaultBarWeight(isKg),
        enabledPlates = PlateCalculator.defaultAvailablePlates(isKg).toSet(),
        unitLabel = unitLabel,
    )
}

/**
 * Pure presentation mapper: computes a [PlateLoadUi] given target weight, bar weight,
 * available plates, and unit label.
 */
fun computePlateLoadUi(
    targetWeight: Double,
    barWeight: Double,
    availablePlates: List<Double>,
    unitLabel: String,
    isDoubleSided: Boolean = true,
): PlateLoadUi {
    val isKg = unitLabel.equals("kg", ignoreCase = true)
    val domainResult = PlateCalculator.calculatePlates(
        targetWeight = targetWeight,
        barWeight = barWeight,
        availablePlates = availablePlates,
        isDoubleSided = isDoubleSided,
    )

    val platesUi = domainResult.platesPerSide.map { plate ->
        PlateItemUi(
            weight = plate.weight,
            count = plate.count,
            color = PlateColors.colorFor(plate.weight, isKg = isKg),
            onColor = PlateColors.onColorFor(plate.weight, isKg = isKg),
            label = PlateColors.formatPlateWeight(plate.weight),
            heightFraction = PlateColors.heightFractionFor(plate.weight, isKg = isKg),
            widthDp = PlateColors.widthDpFor(plate.weight, isMini = false, isKg = isKg),
        )
    }

    val statusUi = when (domainResult.issue) {
        PlateCalculationIssue.None -> PlateLoadStatusUi.ExactMatch
        PlateCalculationIssue.BelowBarWeight -> PlateLoadStatusUi.BelowBarWeight
        PlateCalculationIssue.CannotMatchWithAvailablePlates ->
            PlateLoadStatusUi.CannotMatchWithAvailablePlates
    }

    return PlateLoadUi(
        targetWeight = domainResult.targetWeight,
        barWeight = domainResult.barWeight,
        unitLabel = unitLabel,
        isDoubleSided = isDoubleSided,
        platesPerSide = platesUi,
        totalWeight = domainResult.totalWeight,
        remainder = domainResult.remainder,
        status = statusUi,
    )
}
