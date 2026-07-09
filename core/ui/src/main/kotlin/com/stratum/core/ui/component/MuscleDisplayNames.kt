package com.stratum.core.ui.component

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Product display name for a [MuscleGroup].
 *
 * Prefer this (or domain [MuscleGroup.displayLabel]) over [Enum.name] / title-casing the enum —
 * raw names like `LateralDeltoid` and mistaken labels like `PlantarFlexion` for calves must not
 * reach the dashboard or other chrome.
 *
 * Delegates to [MuscleGroup.displayLabel] so domain remains the single mapping table.
 */
fun MuscleGroup.toDisplayName(): String = displayLabel

/**
 * Volume-card / program-summary alias for [toDisplayName]. Kept for existing call sites.
 */
fun MuscleGroup.toVolumeDisplayName(): String = toDisplayName()

/**
 * Program-facing movement pattern label. Calf work is shown as "Calves" rather than the clinical
 * [MovementPattern.PlantarFlexion] name.
 */
fun MovementPattern.toProgramDisplayName(): String =
    when (this) {
        MovementPattern.PlantarFlexion -> "Calves"
        else -> displayName
    }

/** Alias matching [MuscleGroup.toDisplayName] naming for movement patterns. */
fun MovementPattern.toDisplayName(): String = toProgramDisplayName()
