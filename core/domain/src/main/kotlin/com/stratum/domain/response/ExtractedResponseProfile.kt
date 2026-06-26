package com.stratum.domain.response

data class ExtractedResponseProfile(
    val epochs: List<Epoch>,
    val volumeResponses: List<VolumeResponseObservation>,
    val liftEffectiveness: List<LiftEffectivenessObservation>,
    val experimentBlocks: List<ExperimentBlock>,
    val metadata: ResponseProfileMetadata,
)

interface ResponseProfilePersistencePort {
    suspend fun replaceExtractedProfile(profile: ExtractedResponseProfile)

    suspend fun clearAll()
}
