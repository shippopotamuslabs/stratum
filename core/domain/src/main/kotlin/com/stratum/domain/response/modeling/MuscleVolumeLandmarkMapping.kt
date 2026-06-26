package com.stratum.domain.response.modeling

import com.stratum.domain.importing.LandmarkConfidenceInterval
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.importing.MuscleVolumeHighlight

fun MuscleVolumeLandmarkEstimate.toHighlight(): MuscleVolumeHighlight =
    MuscleVolumeHighlight(
        muscle = muscle,
        estimatedMev = estimatedMev,
        estimatedMav = estimatedMav,
        estimatedMrv = estimatedMrv,
        estimatedPv = estimatedPv,
        mevInterval = mevInterval,
        mavInterval = mavInterval,
        mrvInterval = mrvInterval,
        pvInterval = pvInterval,
        confidenceTier = confidenceTier,
    )

fun List<MuscleVolumeLandmarkEstimate>.toHighlights(): List<MuscleVolumeHighlight> =
    map { it.toHighlight() }
