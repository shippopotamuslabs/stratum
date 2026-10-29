package com.stratum.domain.response

import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleDoseResponsePoint
import com.stratum.domain.response.modeling.MuscleResponseModelDiagnostic
import com.stratum.domain.response.modeling.MuscleVolumeResponseSnapshot
import com.stratum.domain.response.modeling.MuscleWeeklyOutcomeIndex
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import com.stratum.domain.response.modeling.ResponseModelingConstants

data class ExtractedResponseProfile(
    val version: Int = ResponseModelingConstants.EXTRACTED_PROFILE_VERSION,
    val epochs: List<Epoch>,
    val volumeResponses: List<VolumeResponseObservation>,
    val liftEffectiveness: List<LiftEffectivenessObservation>,
    val experimentBlocks: List<ExperimentBlock>,
    val metadata: ResponseProfileMetadata,
    val weeklyStimulus: List<MuscleWeeklyStimulus> = emptyList(),
    val liftPerformance: List<LiftWeeklyPerformance> = emptyList(),
    val outcomeIndices: List<MuscleWeeklyOutcomeIndex> = emptyList(),
    val volumeSnapshots: List<MuscleVolumeResponseSnapshot> = emptyList(),
    val modelDiagnostics: List<MuscleResponseModelDiagnostic> = emptyList(),
    val dosePoints: List<MuscleDoseResponsePoint> = emptyList(),
)
