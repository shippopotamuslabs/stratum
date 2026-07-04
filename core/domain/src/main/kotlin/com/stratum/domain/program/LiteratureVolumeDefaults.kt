package com.stratum.domain.program

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.importing.ResolvedGlobalVolumePriors
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.TrainingAge

/**
 * Population-level MV / PV / RC targets from [GlobalVolumePriors] for program generation when the
 * user has no import history.
 */
data class LiteratureVolumeDefaults(
    val muscle: MuscleGroup,
    val retentionFloorMv: Double,
    val paretoVolumePv: Double,
    val recoverabilityCeilingRc: Double,
    val growthEntryFloor: Double,
    val maintenanceCeiling: Double,
)

object LiteratureVolumeDefaultsProvider {
    fun all(trainingAge: TrainingAge? = null): List<LiteratureVolumeDefaults> {
        val globals = GlobalVolumePriors.forTrainingAge(trainingAge)
        return MuscleGroup.entries.map { muscle -> globals.toLiteratureDefaults(muscle) }
    }
}

internal fun ResolvedGlobalVolumePriors.toLiteratureDefaults(muscle: MuscleGroup): LiteratureVolumeDefaults =
    LiteratureVolumeDefaults(
        muscle = muscle,
        retentionFloorMv = retentionFloorMv,
        paretoVolumePv = paretoVolumePv,
        recoverabilityCeilingRc = recoverabilityCeilingRc,
        growthEntryFloor = growthEntryFloor,
        maintenanceCeiling = maintenanceCeiling,
    )
