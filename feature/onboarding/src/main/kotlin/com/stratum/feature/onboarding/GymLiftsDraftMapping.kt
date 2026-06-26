package com.stratum.feature.onboarding

import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.onboarding.MuscleGroup

internal fun GymPreset.toPresetOptionUi(): GymPresetOptionUi = GymPresetOptionUi(
    label = displayName,
    description = toPresetDescription(),
)

internal fun allGymPresetOptions(): List<GymPresetOptionUi> =
    GymPreset.entries.map { it.toPresetOptionUi() }

internal fun GymPreset.toPresetDescription(): String = when (this) {
    GymPreset.CommercialGym -> "Full equipment: barbells, dumbbells, cables, and machines."
    GymPreset.GarageGym -> "Home setup: barbells, dumbbells, and bodyweight. No cables or machines."
}

internal fun List<Lift>.toGymLiftsDraft(
    selectedPreset: GymPreset?,
    usedHistoricalImport: Boolean,
    hasCompletedHistoricalImport: Boolean,
    importProgress: ImportProgress?,
    importError: String?,
    liftsValid: Boolean,
    showErrors: Boolean,
): GymLiftsDraftUi = GymLiftsDraftUi(
    selectedPresetLabel = selectedPreset?.displayName,
    usedHistoricalImport = usedHistoricalImport,
    hasCompletedHistoricalImport = hasCompletedHistoricalImport,
    importProgress = importProgress,
    importError = importError,
    isValid = liftsValid,
    showErrors = showErrors,
)

internal fun Lift.toSummary(): LiftSummaryUi = LiftSummaryUi(
    id = id.value,
    name = name,
    patternLabel = movementPattern.displayName,
    muscleSummary = muscleSummary(primaryMuscles, secondaryMuscles),
    enabled = enabled,
)

internal fun gymPresetFromLabel(label: String?): GymPreset? =
    GymPreset.entries.firstOrNull { it.displayName == label }

private fun muscleSummary(primary: Set<MuscleGroup>, secondary: Set<MuscleGroup>): String {
    val parts = primary.map { it.displayLabel }
    val extras = secondary.map { it.displayLabel }.filter { it !in parts }
    return (parts + extras).joinToString(", ").ifEmpty { "—" }
}
