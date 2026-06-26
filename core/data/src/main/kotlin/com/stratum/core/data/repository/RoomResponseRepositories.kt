package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.ExperimentBlockDao
import com.stratum.core.data.db.LiftEffectivenessObservationDao
import com.stratum.core.data.db.ResponseEpochDao
import com.stratum.core.data.db.ResponseProfileMetadataDao
import com.stratum.core.data.db.VolumeResponseObservationDao
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntity
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
import javax.inject.Inject

class RoomResponseEpochRepository @Inject constructor(
    private val dao: ResponseEpochDao,
) : ResponseEpochRepository {
    override suspend fun replaceAll(epochs: List<Epoch>) {
        dao.deleteAll()
        if (epochs.isNotEmpty()) dao.insertAll(epochs.map { it.toEntity() })
    }

    override suspend fun getAll(): List<Epoch> = dao.getAll().map { it.toDomain() }
}

class RoomVolumeResponseObservationRepository @Inject constructor(
    private val dao: VolumeResponseObservationDao,
) : VolumeResponseObservationRepository {
    override suspend fun replaceAll(observations: List<VolumeResponseObservation>) {
        dao.deleteAll()
        if (observations.isNotEmpty()) dao.insertAll(observations.map { it.toEntity() })
    }

    override suspend fun getAll(): List<VolumeResponseObservation> =
        dao.getAll().map { it.toDomain() }
}

class RoomLiftEffectivenessObservationRepository @Inject constructor(
    private val dao: LiftEffectivenessObservationDao,
) : LiftEffectivenessObservationRepository {
    override suspend fun replaceAll(observations: List<LiftEffectivenessObservation>) {
        dao.deleteAll()
        if (observations.isNotEmpty()) dao.insertAll(observations.map { it.toEntity() })
    }

    override suspend fun getAll(): List<LiftEffectivenessObservation> =
        dao.getAll().map { it.toDomain() }
}

class RoomExperimentBlockRepository @Inject constructor(
    private val dao: ExperimentBlockDao,
) : ExperimentBlockRepository {
    override suspend fun replaceAll(blocks: List<ExperimentBlock>) {
        dao.deleteAll()
        if (blocks.isNotEmpty()) dao.insertAll(blocks.map { it.toEntity() })
    }

    override suspend fun getAll(): List<ExperimentBlock> = dao.getAll().map { it.toDomain() }
}

class RoomResponseProfileMetadataRepository @Inject constructor(
    private val dao: ResponseProfileMetadataDao,
) : ResponseProfileMetadataRepository {
    override suspend fun save(metadata: ResponseProfileMetadata) {
        dao.upsert(metadata.toEntity())
    }

    override suspend fun get(): ResponseProfileMetadata? = dao.get()?.toDomain()

    override suspend fun clear() {
        dao.deleteAll()
    }
}
