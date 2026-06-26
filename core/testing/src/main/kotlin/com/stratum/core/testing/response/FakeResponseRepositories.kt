package com.stratum.core.testing.response

import com.stratum.domain.response.Epoch
import com.stratum.domain.response.ExperimentBlock
import com.stratum.domain.response.ExperimentBlockRepository
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.LiftEffectivenessObservationRepository
import com.stratum.domain.response.ResponseEpochRepository
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.response.ResponseProfileMetadataRepository
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.VolumeResponseObservationRepository

class FakeResponseEpochRepository : ResponseEpochRepository {
    private val epochs = mutableListOf<Epoch>()

    override suspend fun replaceAll(epochs: List<Epoch>) {
        this.epochs.clear()
        this.epochs.addAll(epochs)
    }

    override suspend fun getAll(): List<Epoch> = epochs.toList()

    fun seed(epochs: List<Epoch>) {
        this.epochs.clear()
        this.epochs.addAll(epochs)
    }
}

class FakeVolumeResponseObservationRepository : VolumeResponseObservationRepository {
    private val observations = mutableListOf<VolumeResponseObservation>()

    override suspend fun replaceAll(observations: List<VolumeResponseObservation>) {
        this.observations.clear()
        this.observations.addAll(observations)
    }

    override suspend fun getAll(): List<VolumeResponseObservation> = observations.toList()

    fun seed(observations: List<VolumeResponseObservation>) {
        this.observations.clear()
        this.observations.addAll(observations)
    }
}

class FakeLiftEffectivenessObservationRepository : LiftEffectivenessObservationRepository {
    private val observations = mutableListOf<LiftEffectivenessObservation>()

    override suspend fun replaceAll(observations: List<LiftEffectivenessObservation>) {
        this.observations.clear()
        this.observations.addAll(observations)
    }

    override suspend fun getAll(): List<LiftEffectivenessObservation> = observations.toList()

    fun seed(observations: List<LiftEffectivenessObservation>) {
        this.observations.clear()
        this.observations.addAll(observations)
    }
}

class FakeExperimentBlockRepository : ExperimentBlockRepository {
    private val blocks = mutableListOf<ExperimentBlock>()

    override suspend fun replaceAll(blocks: List<ExperimentBlock>) {
        this.blocks.clear()
        this.blocks.addAll(blocks)
    }

    override suspend fun getAll(): List<ExperimentBlock> = blocks.toList()
}

class FakeResponseProfileMetadataRepository : ResponseProfileMetadataRepository {
    private var metadata: ResponseProfileMetadata? = null

    override suspend fun save(metadata: ResponseProfileMetadata) {
        this.metadata = metadata
    }

    override suspend fun get(): ResponseProfileMetadata? = metadata

    override suspend fun clear() {
        metadata = null
    }
}
