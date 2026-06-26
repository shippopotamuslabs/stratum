package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.data.db.ExperimentBlockDao
import com.stratum.core.data.db.LiftEffectivenessObservationDao
import com.stratum.core.data.db.LiftWeeklyPerformanceDao
import com.stratum.core.data.db.MuscleDoseResponsePointDao
import com.stratum.core.data.db.MuscleResponseModelDiagnosticDao
import com.stratum.core.data.db.MuscleVolumeLandmarkEstimateDao
import com.stratum.core.data.db.MuscleWeeklyOutcomeIndexDao
import com.stratum.core.data.db.MuscleWeeklyStimulusDao
import com.stratum.core.data.db.ResponseEpochDao
import com.stratum.core.data.db.ResponseProfileMetadataDao
import com.stratum.core.data.db.StratumDatabase
import com.stratum.core.data.db.VolumeResponseObservationDao
import com.stratum.core.data.mapper.toDomain
import com.stratum.core.data.mapper.toEntity
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.ExperimentBlock
import com.stratum.domain.response.ExperimentBlockRepository
import com.stratum.domain.response.ExtractedResponseProfile
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.LiftEffectivenessObservationRepository
import com.stratum.domain.response.MuscleVolumeLandmarkEstimateRepository
import com.stratum.domain.response.ResponseEpochRepository
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.response.ResponseProfileMetadataRepository
import com.stratum.domain.response.ResponseProfilePersistencePort
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.VolumeResponseObservationRepository
import com.stratum.domain.response.modeling.MuscleVolumeLandmarkEstimate
import javax.inject.Inject

class RoomResponseProfilePersistenceRepository @Inject constructor(
    private val database: StratumDatabase,
    private val epochDao: ResponseEpochDao,
    private val volumeDao: VolumeResponseObservationDao,
    private val effectivenessDao: LiftEffectivenessObservationDao,
    private val experimentDao: ExperimentBlockDao,
    private val metadataDao: ResponseProfileMetadataDao,
    private val stimulusDao: MuscleWeeklyStimulusDao,
    private val liftPerformanceDao: LiftWeeklyPerformanceDao,
    private val outcomeIndexDao: MuscleWeeklyOutcomeIndexDao,
    private val dosePointDao: MuscleDoseResponsePointDao,
    private val landmarkDao: MuscleVolumeLandmarkEstimateDao,
    private val diagnosticDao: MuscleResponseModelDiagnosticDao,
) : ResponseProfilePersistencePort {
    override suspend fun replaceExtractedProfile(profile: ExtractedResponseProfile) {
        database.withTransaction {
            epochDao.deleteAll()
            volumeDao.deleteAll()
            effectivenessDao.deleteAll()
            experimentDao.deleteAll()
            stimulusDao.deleteAll()
            liftPerformanceDao.deleteAll()
            outcomeIndexDao.deleteAll()
            dosePointDao.deleteAll()
            landmarkDao.deleteAll()
            diagnosticDao.deleteAll()
            if (profile.epochs.isNotEmpty()) {
                epochDao.insertAll(profile.epochs.map { it.toEntity() })
            }
            if (profile.volumeResponses.isNotEmpty()) {
                volumeDao.insertAll(profile.volumeResponses.map { it.toEntity() })
            }
            if (profile.liftEffectiveness.isNotEmpty()) {
                effectivenessDao.insertAll(profile.liftEffectiveness.map { it.toEntity() })
            }
            if (profile.experimentBlocks.isNotEmpty()) {
                experimentDao.insertAll(profile.experimentBlocks.map { it.toEntity() })
            }
            if (profile.weeklyStimulus.isNotEmpty()) {
                stimulusDao.insertAll(profile.weeklyStimulus.map { it.toEntity() })
            }
            if (profile.liftPerformance.isNotEmpty()) {
                liftPerformanceDao.insertAll(profile.liftPerformance.map { it.toEntity() })
            }
            if (profile.outcomeIndices.isNotEmpty()) {
                outcomeIndexDao.insertAll(profile.outcomeIndices.map { it.toEntity() })
            }
            if (profile.dosePoints.isNotEmpty()) {
                dosePointDao.insertAll(profile.dosePoints.map { it.toEntity() })
            }
            if (profile.landmarkEstimates.isNotEmpty()) {
                landmarkDao.insertAll(profile.landmarkEstimates.map { it.toEntity() })
            }
            if (profile.modelDiagnostics.isNotEmpty()) {
                diagnosticDao.insertAll(profile.modelDiagnostics.map { it.toEntity() })
            }
            metadataDao.upsert(profile.metadata.toEntity())
        }
    }

    override suspend fun clearAll() {
        database.withTransaction {
            epochDao.deleteAll()
            volumeDao.deleteAll()
            effectivenessDao.deleteAll()
            experimentDao.deleteAll()
            stimulusDao.deleteAll()
            liftPerformanceDao.deleteAll()
            outcomeIndexDao.deleteAll()
            dosePointDao.deleteAll()
            landmarkDao.deleteAll()
            diagnosticDao.deleteAll()
            metadataDao.deleteAll()
        }
    }
}

class RoomResponseEpochRepository @Inject constructor(
    private val database: StratumDatabase,
    private val dao: ResponseEpochDao,
) : ResponseEpochRepository {
    override suspend fun replaceAll(epochs: List<Epoch>) {
        database.withTransaction {
            dao.deleteAll()
            if (epochs.isNotEmpty()) dao.insertAll(epochs.map { it.toEntity() })
        }
    }

    override suspend fun getAll(): List<Epoch> = dao.getAll().map { it.toDomain() }
}

class RoomVolumeResponseObservationRepository @Inject constructor(
    private val database: StratumDatabase,
    private val dao: VolumeResponseObservationDao,
) : VolumeResponseObservationRepository {
    override suspend fun replaceAll(observations: List<VolumeResponseObservation>) {
        database.withTransaction {
            dao.deleteAll()
            if (observations.isNotEmpty()) dao.insertAll(observations.map { it.toEntity() })
        }
    }

    override suspend fun getAll(): List<VolumeResponseObservation> =
        dao.getAll().map { it.toDomain() }
}

class RoomLiftEffectivenessObservationRepository @Inject constructor(
    private val database: StratumDatabase,
    private val dao: LiftEffectivenessObservationDao,
) : LiftEffectivenessObservationRepository {
    override suspend fun replaceAll(observations: List<LiftEffectivenessObservation>) {
        database.withTransaction {
            dao.deleteAll()
            if (observations.isNotEmpty()) dao.insertAll(observations.map { it.toEntity() })
        }
    }

    override suspend fun getAll(): List<LiftEffectivenessObservation> =
        dao.getAll().map { it.toDomain() }
}

class RoomExperimentBlockRepository @Inject constructor(
    private val database: StratumDatabase,
    private val dao: ExperimentBlockDao,
) : ExperimentBlockRepository {
    override suspend fun replaceAll(blocks: List<ExperimentBlock>) {
        database.withTransaction {
            dao.deleteAll()
            if (blocks.isNotEmpty()) dao.insertAll(blocks.map { it.toEntity() })
        }
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

class RoomMuscleVolumeLandmarkEstimateRepository @Inject constructor(
    private val database: StratumDatabase,
    private val dao: MuscleVolumeLandmarkEstimateDao,
) : MuscleVolumeLandmarkEstimateRepository {
    override suspend fun replaceAll(estimates: List<MuscleVolumeLandmarkEstimate>) {
        database.withTransaction {
            dao.deleteAll()
            if (estimates.isNotEmpty()) dao.insertAll(estimates.map { it.toEntity() })
        }
    }

    override suspend fun getAll(): List<MuscleVolumeLandmarkEstimate> =
        dao.getAll().map { it.toDomain() }
}
