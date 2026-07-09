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

interface ResponseProfilePersistencePort {
    suspend fun replaceExtractedProfile(profile: ExtractedResponseProfile)

    /**
     * Replaces **only** the weekly series tables ([ExtractedResponseProfile.weeklyStimulus] and
     * [ExtractedResponseProfile.liftPerformance]) in a single transaction, leaving all other
     * response tables (epochs, dose-response, outcome indices, …) untouched. Used by
     * [com.stratum.domain.training.RefreshWeeklySeriesUseCase] for the cheap, deterministic
     * post-workout refresh that keeps the dashboard charts fresh without re-running the heavy
     * dose-response modeling pipeline.
     */
    suspend fun replaceWeeklySeries(profile: ExtractedResponseProfile)

    suspend fun clearAll()
}
