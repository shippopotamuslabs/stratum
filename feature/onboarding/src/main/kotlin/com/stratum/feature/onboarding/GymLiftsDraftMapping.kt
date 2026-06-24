package com.stratum.feature.onboarding

import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Maps the domain lift catalog + preset choice to the UI-shaped [GymLiftsDraftUi] and back.
 * Composables only ever see [GymLiftsDraftUi] / [LiftSummaryUi] (ARCHITECTURE.md §4.3). Also
 * owns the preset display labels and the per-lift muscle/pattern summary strings.
 *
 * OS-4b simplified the Gym & Lifts step to preset-only selection — [toGymLiftsDraft] no longer
 * carries the lift list, search, or custom-lift form. The [Lift.toSummary] / [LiftSummaryUi]
 * helpers and [MuscleGroup.toMuscleLabel] remain for the future post-onboarding catalog
 * management screen.
 */
internal fun List<Lift>.toGymLiftsDraft(
    selectedPreset: GymPreset?,
    showErrors: Boolean,
): GymLiftsDraftUi = GymLiftsDraftUi(
    selectedPresetLabel = selectedPreset?.toDisplayLabel(),
    showErrors = showErrors,
)

internal fun Lift.toSummary(): LiftSummaryUi = LiftSummaryUi(
    id = id.value,
    name = name,
    patternLabel = movementPattern.displayName,
    muscleSummary = muscleSummary(primaryMuscles, secondaryMuscles),
    enabled = enabled,
)

internal fun GymPreset.toDisplayLabel(): String = displayName

internal fun gymPresetFromLabel(label: String?): GymPreset? =
    GymPreset.entries.firstOrNull { it.displayName == label }

/**
 * Renders a lift's muscle set as a compact "Quads, Glutes" style summary. Primary muscles are
 * listed first; secondary are folded in if there's room and they add information. The strings
 * are display labels (matching the Specialization step) so the two steps read consistently.
 */
private fun muscleSummary(primary: Set<MuscleGroup>, secondary: Set<MuscleGroup>): String {
    val parts = primary.map { it.toMuscleLabel() }
    val extras = secondary.map { it.toMuscleLabel() }.filter { it !in parts }
    return (parts + extras).joinToString(", ").ifEmpty { "—" }
}

private fun MuscleGroup.toMuscleLabel(): String = when (this) {
    MuscleGroup.Chest -> "Chest"
    MuscleGroup.Back -> "Back"
    MuscleGroup.Quad -> "Quads"
    MuscleGroup.Hamstring -> "Hamstrings"
    MuscleGroup.Glute -> "Glutes"
    MuscleGroup.PosteriorDeltoid -> "Rear Delts"
    MuscleGroup.LateralDeltoid -> "Side Delts"
    MuscleGroup.AnteriorDeltoid -> "Front Delts"
    MuscleGroup.Tricep -> "Triceps"
    MuscleGroup.Bicep -> "Biceps"
    MuscleGroup.Trap -> "Traps"
    MuscleGroup.Calf -> "Calves"
    MuscleGroup.Forearm -> "Forearms"
    MuscleGroup.Ab -> "Abs"
    MuscleGroup.LowerBack -> "Lower Back"
    MuscleGroup.Abductor -> "Abductors"
    MuscleGroup.Adductor -> "Adductors"
}
