package com.stratum.domain.response

interface ResponseEpochRepository {
    suspend fun replaceAll(epochs: List<Epoch>)

    suspend fun getAll(): List<Epoch>
}

interface VolumeResponseObservationRepository {
    suspend fun replaceAll(observations: List<VolumeResponseObservation>)

    suspend fun getAll(): List<VolumeResponseObservation>
}

interface LiftEffectivenessObservationRepository {
    suspend fun replaceAll(observations: List<LiftEffectivenessObservation>)

    suspend fun getAll(): List<LiftEffectivenessObservation>
}

interface ExperimentBlockRepository {
    suspend fun replaceAll(blocks: List<ExperimentBlock>)

    suspend fun getAll(): List<ExperimentBlock>
}

interface ResponseProfileMetadataRepository {
    suspend fun save(metadata: ResponseProfileMetadata)

    suspend fun get(): ResponseProfileMetadata?

    suspend fun clear()
}

interface ExperimentRegistry {
    fun active(): List<ExperimentBlock>

    fun record(block: ExperimentBlock)
}
