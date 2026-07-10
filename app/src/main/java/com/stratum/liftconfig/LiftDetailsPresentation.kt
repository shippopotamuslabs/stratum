package com.stratum.liftconfig

import com.stratum.core.ui.component.StratumMuscleFilterCardState
import com.stratum.core.ui.component.toVolumeDisplayName
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.onboarding.MuscleGroup

/** Display label for length-bias chips and detail rows. */
fun MuscleLengthBias.toDetailLabel(): String =
    when (this) {
        MuscleLengthBias.Lengthened -> "Lengthened"
        MuscleLengthBias.MidRange -> "Mid-range"
        MuscleLengthBias.Shortened -> "Shortened"
    }

/** Display label for kinematic profile chips and detail rows. */
fun KinematicProfile.toDetailLabel(): String =
    when (this) {
        KinematicProfile.AxialCompound -> "Axial Compound"
        KinematicProfile.NonAxialCompound -> "Non-Axial Compound"
        KinematicProfile.Isolation -> "Isolation"
    }

/** Display label for load-type chips and detail rows. */
fun LoadType.toDetailLabel(): String =
    when (this) {
        LoadType.Weighted -> "Weighted"
        LoadType.Bodyweight -> "Bodyweight"
        LoadType.Assisted -> "Assisted"
    }

/** Title-cases freeform equipment so seed values like "cable" match other detail labels. */
fun String.toTitleCaseWords(): String =
    trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { ch -> ch.titlecase() }
        }

fun Set<MuscleGroup>.toMuscleCards(): List<StratumMuscleFilterCardState> =
    sortedBy { it.name }.map { StratumMuscleFilterCardState(it, it.toVolumeDisplayName()) }

fun lengthBiasOptions(selected: MuscleLengthBias): List<LiftDetailOptionUi> =
    MuscleLengthBias.entries.map { bias ->
        LiftDetailOptionUi(
            key = bias.name,
            label = bias.toDetailLabel(),
            selected = bias == selected,
        )
    }

fun kinematicOptions(selected: KinematicProfile): List<LiftDetailOptionUi> =
    KinematicProfile.entries.map { profile ->
        LiftDetailOptionUi(
            key = profile.name,
            label = profile.toDetailLabel(),
            selected = profile == selected,
        )
    }

fun loadTypeOptions(selected: LoadType): List<LiftDetailOptionUi> =
    LoadType.entries.map { type ->
        LiftDetailOptionUi(
            key = type.name,
            label = type.toDetailLabel(),
            selected = type == selected,
        )
    }

fun movementPatternOptions(selected: Set<MovementPattern>): List<LiftDetailOptionUi> =
    MovementPattern.entries.map { pattern ->
        LiftDetailOptionUi(
            key = pattern.name,
            label = pattern.displayName,
            selected = pattern in selected,
        )
    }

internal fun formatRestDisplay(restSeconds: Int): String {
    val total = restSeconds.coerceAtLeast(0)
    val minutes = total / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return "%d:%02d".format(minutes, seconds)
}

private const val SECONDS_PER_MINUTE = 60
