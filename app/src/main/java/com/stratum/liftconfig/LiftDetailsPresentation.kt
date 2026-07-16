package com.stratum.liftconfig

import com.stratum.core.ui.component.StratumMuscleFilterCardState
import com.stratum.core.ui.component.toVolumeDisplayName
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.SeedEquipmentIds
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.onboarding.MuscleGroup

/** Display label for length-bias chips and detail rows. */
fun MuscleLengthBias.toDetailLabel(): String =
    when (this) {
        MuscleLengthBias.Lengthened -> "Lengthened"
        MuscleLengthBias.MidRange -> "Mid-range"
        MuscleLengthBias.Shortened -> "Shortened"
    }

/** Display label for fatigue profile chips and detail rows. */
fun FatigueProfile.toDetailLabel(): String =
    when (this) {
        FatigueProfile.High -> "High"
        FatigueProfile.Medium -> "Medium"
        FatigueProfile.Low -> "Low"
    }

/**
 * Display label for load-type chips and detail rows.
 *
 * Distinct from [equipmentDetailLabel]: load type is how resistance is logged
 * (weight column visibility / sign), not which implement supplies increment defaults.
 */
fun LoadType.toDetailLabel(): String =
    when (this) {
        LoadType.Weighted -> "Weighted"
        LoadType.Bodyweight -> "Bodyweight"
        LoadType.Assisted -> "Assisted"
    }

/**
 * Equipment implement label for detail rows.
 *
 * Seed bodyweight uses "Bodyweight only" so it is not confused with [LoadType.Bodyweight]
 * ("Bodyweight" = no external load logged).
 */
fun equipmentDetailLabel(
    equipmentId: EquipmentId?,
    equipmentName: String?,
): String? =
    when {
        equipmentId == SeedEquipmentIds.Bodyweight -> "Bodyweight only"
        else -> equipmentName
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

fun fatigueOptions(selected: FatigueProfile): List<LiftDetailOptionUi> =
    FatigueProfile.entries.map { profile ->
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
