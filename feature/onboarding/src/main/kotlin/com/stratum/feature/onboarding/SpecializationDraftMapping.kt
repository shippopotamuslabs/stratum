package com.stratum.feature.onboarding

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference

/**
 * Maps the domain [SpecializationPreference] to the UI-shaped [SpecializationDraftUi] and
 * back. Composables only ever see [SpecializationDraftUi] (ARCHITECTURE.md §4.3). Also owns
 * the muscle display labels, the screen section grouping, and the static antagonist-pair
 * advisory hints.
 */
internal fun SpecializationPreference.toSpecializationDraft(): SpecializationDraftUi =
    SpecializationDraftUi(
        selectedMuscleLabels = specializedMuscles.map { it.toDisplayLabel() }.toSet(),
    )

internal fun MuscleGroup.toDisplayLabel(): String = when (this) {
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
}

internal fun muscleGroupFromLabel(label: String): MuscleGroup? =
    MuscleGroup.entries.firstOrNull { it.toDisplayLabel() == label }

/**
 * Builds the specialization preference from the UI selection. Only Specialize selections are
 * emitted; Maintenance is implicit (absence). [Omit] is reserved for OS-6.
 */
internal fun SpecializationDraftUi.toPreference(): SpecializationPreference {
    if (selectedMuscleLabels.isEmpty()) return SpecializationPreference.Empty
    val levels = selectedMuscleLabels.mapNotNullTo(mutableSetOf()) { label ->
        muscleGroupFromLabel(label)
    }.associateWith { SpecializationLevel.Specialize }
    return SpecializationPreference(levels = levels)
}

/** Screen sections, each a heading and the muscles shown under it. */
internal data class SpecializationSection(
    val heading: String,
    val muscles: List<MuscleGroup>,
)

internal val SPECIALIZATION_SECTIONS: List<SpecializationSection> = listOf(
    SpecializationSection("Torso", listOf(MuscleGroup.Chest, MuscleGroup.Back, MuscleGroup.Ab)),
    SpecializationSection(
        "Shoulders",
        listOf(MuscleGroup.LateralDeltoid, MuscleGroup.PosteriorDeltoid, MuscleGroup.AnteriorDeltoid, MuscleGroup.Trap),
    ),
    SpecializationSection("Arms", listOf(MuscleGroup.Bicep, MuscleGroup.Tricep, MuscleGroup.Forearm)),
    SpecializationSection(
        "Legs & Hips",
        listOf(MuscleGroup.Quad, MuscleGroup.Hamstring, MuscleGroup.Glute, MuscleGroup.Calf, MuscleGroup.LowerBack),
    ),
)

/** Antagonist pairs used for advisory coaching (never blocks). */
private val ANTAGONIST_PAIRS: List<Pair<MuscleGroup, MuscleGroup>> = listOf(
    MuscleGroup.Chest to MuscleGroup.Back,
    MuscleGroup.Quad to MuscleGroup.Hamstring,
    MuscleGroup.Bicep to MuscleGroup.Tricep,
    MuscleGroup.AnteriorDeltoid to MuscleGroup.PosteriorDeltoid,
)

/**
 * The first antagonist hint that applies when exactly one side of a pair is selected, or
 * `null`. Advisory only — OS-3 never blocks on push/pull balance (that is an OS-6
 * volume-distribution concern).
 */
internal fun antagonistHintFor(selected: Set<MuscleGroup>): String? {
    for ((a, b) in ANTAGONIST_PAIRS) {
        val hasA = a in selected
        val hasB = b in selected
        if (hasA && !hasB) return "Pairing ${a.toDisplayLabel()} with ${b.toDisplayLabel()} helps keep joints balanced."
        if (hasB && !hasA) return "Pairing ${b.toDisplayLabel()} with ${a.toDisplayLabel()} helps keep joints balanced."
    }
    return null
}
