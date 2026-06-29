package com.stratum.domain.program

import com.stratum.domain.importing.VolumeLandmarkPrior
import com.stratum.domain.importing.VolumeLandmarkPriors
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Population-level MEV/MAV/MRV/PV targets derived from [VolumeLandmarkPriors] for program
 * generation when the user has no import history (preset-only onboarding).
 */
data class LiteratureVolumeLandmark(
    val muscle: MuscleGroup,
    val estimatedMev: Double,
    val estimatedMav: Double,
    val estimatedMrv: Double,
    val estimatedPv: Double,
    val mevLow: Double,
    val mevHigh: Double,
    val mrvLow: Double,
    val mrvHigh: Double,
)

object LiteratureVolumeLandmarks {
    fun all(): List<LiteratureVolumeLandmark> =
        MuscleGroup.entries.map { muscle ->
            VolumeLandmarkPriors.forMuscle(muscle).toLiteratureLandmark(muscle)
        }
}

internal fun VolumeLandmarkPrior.toLiteratureLandmark(muscle: MuscleGroup): LiteratureVolumeLandmark =
    LiteratureVolumeLandmark(
        muscle = muscle,
        estimatedMev = mevMid,
        estimatedMav = mavMid,
        estimatedMrv = mrvMid,
        estimatedPv = pvMid,
        mevLow = mevLow,
        mevHigh = mevHigh,
        mrvLow = mrvLow,
        mrvHigh = mrvHigh,
    )
