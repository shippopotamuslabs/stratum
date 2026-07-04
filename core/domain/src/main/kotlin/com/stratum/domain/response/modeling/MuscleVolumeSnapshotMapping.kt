package com.stratum.domain.response.modeling

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.importing.MuscleVolumeHighlight

import com.stratum.domain.response.modeling.OutcomeModelType

fun MuscleVolumeResponseSnapshot.toHighlight(): MuscleVolumeHighlight =
    MuscleVolumeHighlight(
        muscle = muscle,
        retentionFloorMv = retentionFloorMv,
        paretoVolumePv = paretoVolumePv,
        recoverabilityCeilingRc = recoverabilityCeilingRc,
        observedDoseMin = observedDoseMin,
        observedDoseMax = observedDoseMax,
        observedDoseMedian = observedDoseMedian,
        posture = posture,
        pvInterval = pvInterval,
        confidenceTier = confidenceTier,
        personalizedFromLogs =
            outcomeModelType == OutcomeModelType.MuscleIndex ||
                recoverabilityCeilingRc > GlobalVolumePriors.RECOVERABILITY_CEILING_RC + 0.25 ||
                kotlin.math.abs(paretoVolumePv - GlobalVolumePriors.PARETO_VOLUME_PV) > 0.25,
    )

fun List<MuscleVolumeResponseSnapshot>.toHighlights(): List<MuscleVolumeHighlight> =
    map { it.toHighlight() }
