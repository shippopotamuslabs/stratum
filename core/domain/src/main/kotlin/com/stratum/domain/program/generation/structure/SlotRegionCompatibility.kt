@file:Suppress("MagicNumber")

package com.stratum.domain.program.generation.structure

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate
import com.stratum.domain.program.generation.universe.SynthesisSlot
import com.stratum.domain.program.generation.volume.TrainingDomain
import com.stratum.domain.program.generation.volume.muscleTrainingDomain

private val LOWER_PRIMARY_PATTERNS =
    setOf(
        MovementPattern.SquatPattern,
        MovementPattern.HipHinge,
        MovementPattern.HipExtension,
        MovementPattern.KneeExtension,
        MovementPattern.KneeFlexion,
        MovementPattern.PlantarFlexion,
    )

internal fun allowedRegionFiltersForSlot(slot: WorkoutSlotTemplate): Set<SessionRegionFilter> =
    allowedRegionFiltersForPattern(slot.pattern.name, slot.primaryMuscle.name, slot.isCrossDomainTouch)

fun allowedRegionFiltersForPattern(
    pattern: String,
    primaryMuscle: String,
    isCrossDomainTouch: Boolean,
): Set<SessionRegionFilter> {
    if (isCrossDomainTouch) {
        return when (muscleTrainingDomain(primaryMuscle)) {
            TrainingDomain.Lower -> setOf(SessionRegionFilter.UpperOnly, SessionRegionFilter.Unrestricted)
            TrainingDomain.Upper -> setOf(SessionRegionFilter.LowerOnly, SessionRegionFilter.Unrestricted)
        }
    }
    if (runCatching { MovementPattern.valueOf(pattern) }.getOrNull() in LOWER_PRIMARY_PATTERNS ||
        muscleTrainingDomain(primaryMuscle) == TrainingDomain.Lower
    ) {
        return setOf(SessionRegionFilter.LowerOnly, SessionRegionFilter.Unrestricted)
    }
    return setOf(SessionRegionFilter.UpperOnly, SessionRegionFilter.Unrestricted)
}

fun isSlotCompatibleWithRegionFilter(
    slot: SynthesisSlot,
    regionFilter: SessionRegionFilter,
): Boolean = regionFilter in slot.allowedRegionFilters
