package com.stratum.core.testing.importing

import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.domain.importing.SupersetAffinityRepository
import com.stratum.domain.lift.identity.LiftId

/**
 * In-memory [LiftTrainingInsightsRepository]. `replaceAll` overwrites the whole collection.
 */
class FakeLiftTrainingInsightsRepository(
    initial: List<LiftTrainingInsights> = emptyList(),
) : LiftTrainingInsightsRepository {
    private val insights = initial.toMutableList()

    override suspend fun replaceAll(insights: List<LiftTrainingInsights>) {
        this.insights.clear()
        this.insights.addAll(insights)
    }

    override suspend fun getAll(): List<LiftTrainingInsights> = insights.toList()

    override suspend fun getByLiftId(liftId: LiftId): LiftTrainingInsights? =
        insights.firstOrNull { it.liftId == liftId }
}

/**
 * In-memory [SupersetAffinityRepository]. `replaceAll` overwrites the whole collection.
 */
class FakeSupersetAffinityRepository(
    initial: List<SupersetAffinity> = emptyList(),
) : SupersetAffinityRepository {
    private val affinities = initial.toMutableList()

    override suspend fun replaceAll(affinities: List<SupersetAffinity>) {
        this.affinities.clear()
        this.affinities.addAll(affinities)
    }

    override suspend fun getAll(): List<SupersetAffinity> = affinities.toList()
}

/**
 * In-memory [MuscleVolumeBaselineRepository]. `replaceAll` overwrites the whole collection.
 */
class FakeMuscleVolumeBaselineRepository(
    initial: List<MuscleVolumeBaseline> = emptyList(),
) : MuscleVolumeBaselineRepository {
    private val baselines = initial.toMutableList()

    override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) {
        this.baselines.clear()
        this.baselines.addAll(baselines)
    }

    override suspend fun getAll(): List<MuscleVolumeBaseline> = baselines.toList()
}

/**
 * In-memory [ImportAnalysisMetadataRepository]. Stores a single metadata row (upsert on save,
 * cleared on [clear]).
 */
class FakeImportAnalysisMetadataRepository(
    initial: ImportAnalysisMetadata? = null,
) : ImportAnalysisMetadataRepository {
    private var metadata: ImportAnalysisMetadata? = initial

    override suspend fun save(metadata: ImportAnalysisMetadata) {
        this.metadata = metadata
    }

    override suspend fun get(): ImportAnalysisMetadata? = metadata

    override suspend fun clear() {
        metadata = null
    }
}
