package com.stratum.core.data.repository

import androidx.room.withTransaction
import com.stratum.core.infrastructure.db.ExperimentBlockDao
import com.stratum.core.infrastructure.db.LiftEffectivenessObservationDao
import com.stratum.core.infrastructure.db.LiftWeeklyPerformanceDao
import com.stratum.core.infrastructure.db.MuscleDoseResponsePointDao
import com.stratum.core.infrastructure.db.MuscleResponseModelDiagnosticDao
import com.stratum.core.infrastructure.db.MuscleVolumeResponseSnapshotDao
import com.stratum.core.infrastructure.db.MuscleWeeklyOutcomeIndexDao
import com.stratum.core.infrastructure.db.MuscleWeeklyStimulusDao
import com.stratum.core.infrastructure.db.ResponseEpochDao
import com.stratum.core.infrastructure.db.ResponseProfileMetadataDao
import com.stratum.core.infrastructure.db.StratumDatabase
import com.stratum.core.infrastructure.db.VolumeResponseObservationDao
import com.stratum.core.infrastructure.mapper.toDomain
import com.stratum.core.infrastructure.mapper.toEntity
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.ExperimentBlock
import com.stratum.application.response.ExperimentBlockRepository
import com.stratum.domain.response.ExtractedResponseProfile
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.application.response.LiftEffectivenessObservationRepository
import com.stratum.application.response.LiftWeeklyPerformanceRepository
import com.stratum.application.response.MuscleVolumeResponseSnapshotRepository
import com.stratum.application.response.MuscleWeeklyOutcomeIndexRepository
import com.stratum.application.response.MuscleWeeklyStimulusRepository
import com.stratum.application.response.ResponseEpochRepository
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.application.response.ResponseProfileMetadataRepository
import com.stratum.application.response.ResponseProfilePersistencePort
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.application.response.VolumeResponseObservationRepository
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleVolumeResponseSnapshot
import com.stratum.domain.response.modeling.MuscleWeeklyOutcomeIndex
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import java.time.Instant
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
    private val snapshotDao: MuscleVolumeResponseSnapshotDao,
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
            snapshotDao.deleteAll()
            diagnosticDao.deleteAll()
            if (profile.epochs.isNotEmpty()) {
                epochDao.upsertAll(profile.epochs.map { it.toEntity() })
            }
            if (profile.volumeResponses.isNotEmpty()) {
                volumeDao.upsertAll(profile.volumeResponses.map { it.toEntity() })
            }
            if (profile.liftEffectiveness.isNotEmpty()) {
                effectivenessDao.upsertAll(profile.liftEffectiveness.map { it.toEntity() })
            }
            if (profile.experimentBlocks.isNotEmpty()) {
                experimentDao.upsertAll(profile.experimentBlocks.map { it.toEntity() })
            }
            if (profile.weeklyStimulus.isNotEmpty()) {
                stimulusDao.upsertAll(profile.weeklyStimulus.map { it.toEntity() })
            }
            if (profile.liftPerformance.isNotEmpty()) {
                liftPerformanceDao.upsertAll(profile.liftPerformance.map { it.toEntity() })
            }
            if (profile.outcomeIndices.isNotEmpty()) {
                outcomeIndexDao.upsertAll(profile.outcomeIndices.map { it.toEntity() })
            }
            if (profile.dosePoints.isNotEmpty()) {
                dosePointDao.upsertAll(profile.dosePoints.map { it.toEntity() })
            }
            if (profile.volumeSnapshots.isNotEmpty()) {
                snapshotDao.upsertAll(profile.volumeSnapshots.map { it.toEntity() })
            }
            if (profile.modelDiagnostics.isNotEmpty()) {
                diagnosticDao.upsertAll(profile.modelDiagnostics.map { it.toEntity() })
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
            snapshotDao.deleteAll()
            diagnosticDao.deleteAll()
            metadataDao.deleteAll()
        }
    }

    override suspend fun replaceWeeklySeries(profile: ExtractedResponseProfile) {
        database.withTransaction {
            stimulusDao.deleteAll()
            liftPerformanceDao.deleteAll()
            if (profile.weeklyStimulus.isNotEmpty()) {
                stimulusDao.upsertAll(profile.weeklyStimulus.map { it.toEntity() })
            }
            if (profile.liftPerformance.isNotEmpty()) {
                liftPerformanceDao.upsertAll(profile.liftPerformance.map { it.toEntity() })
            }
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
            if (epochs.isNotEmpty()) dao.upsertAll(epochs.map { it.toEntity() })
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
            if (observations.isNotEmpty()) dao.upsertAll(observations.map { it.toEntity() })
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
            if (observations.isNotEmpty()) dao.upsertAll(observations.map { it.toEntity() })
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
            if (blocks.isNotEmpty()) dao.upsertAll(blocks.map { it.toEntity() })
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

class RoomMuscleVolumeResponseSnapshotRepository @Inject constructor(
    private val database: StratumDatabase,
    private val dao: MuscleVolumeResponseSnapshotDao,
) : MuscleVolumeResponseSnapshotRepository {
    override suspend fun replaceAll(snapshots: List<MuscleVolumeResponseSnapshot>) {
        database.withTransaction {
            dao.deleteAll()
            if (snapshots.isNotEmpty()) dao.upsertAll(snapshots.map { it.toEntity() })
        }
    }

    override suspend fun getAll(): List<MuscleVolumeResponseSnapshot> =
        dao.getAll().map { it.toDomain() }
}

class RoomMuscleWeeklyStimulusRepository @Inject constructor(
    private val database: StratumDatabase,
    private val dao: MuscleWeeklyStimulusDao,
) : MuscleWeeklyStimulusRepository {
    override suspend fun replaceAll(stimulus: List<MuscleWeeklyStimulus>) {
        database.withTransaction {
            dao.deleteAll()
            if (stimulus.isNotEmpty()) dao.upsertAll(stimulus.map { it.toEntity() })
        }
    }

    override suspend fun getAll(): List<MuscleWeeklyStimulus> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getSince(windowStart: Instant): List<MuscleWeeklyStimulus> =
        dao.getSince(windowStart.toEpochMilli()).map { it.toDomain() }
}

class RoomLiftWeeklyPerformanceRepository @Inject constructor(
    private val database: StratumDatabase,
    private val dao: LiftWeeklyPerformanceDao,
) : LiftWeeklyPerformanceRepository {
    override suspend fun replaceAll(performance: List<LiftWeeklyPerformance>) {
        database.withTransaction {
            dao.deleteAll()
            if (performance.isNotEmpty()) dao.upsertAll(performance.map { it.toEntity() })
        }
    }

    override suspend fun getAll(): List<LiftWeeklyPerformance> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getSince(windowStart: Instant): List<LiftWeeklyPerformance> =
        dao.getSince(windowStart.toEpochMilli()).map { it.toDomain() }
}

class RoomMuscleWeeklyOutcomeIndexRepository @Inject constructor(
    private val database: StratumDatabase,
    private val dao: MuscleWeeklyOutcomeIndexDao,
) : MuscleWeeklyOutcomeIndexRepository {
    override suspend fun replaceAll(indices: List<MuscleWeeklyOutcomeIndex>) {
        database.withTransaction {
            dao.deleteAll()
            if (indices.isNotEmpty()) dao.upsertAll(indices.map { it.toEntity() })
        }
    }

    override suspend fun getAll(): List<MuscleWeeklyOutcomeIndex> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getSince(windowStart: Instant): List<MuscleWeeklyOutcomeIndex> =
        dao.getSince(windowStart.toEpochMilli()).map { it.toDomain() }
}
