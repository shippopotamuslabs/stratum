package com.stratum.core.testing.response

import com.stratum.domain.response.Epoch
import com.stratum.domain.response.ExperimentBlock
import com.stratum.domain.response.ExperimentBlockRepository
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.LiftEffectivenessObservationRepository
import com.stratum.domain.response.LiftWeeklyPerformanceRepository
import com.stratum.domain.response.MuscleWeeklyOutcomeIndexRepository
import com.stratum.domain.response.MuscleWeeklyStimulusRepository
import com.stratum.domain.response.ResponseEpochRepository
import com.stratum.domain.response.ResponseModelingSchedulerPort
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.response.ResponseProfileMetadataRepository
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.VolumeResponseObservationRepository
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleWeeklyOutcomeIndex
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import java.time.Instant

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

class FakeMuscleWeeklyStimulusRepository(
    initial: List<MuscleWeeklyStimulus> = emptyList(),
) : MuscleWeeklyStimulusRepository {
    private val stimulus = initial.toMutableList()

    override suspend fun replaceAll(stimulus: List<MuscleWeeklyStimulus>) {
        this.stimulus.clear()
        this.stimulus.addAll(stimulus)
    }

    override suspend fun getAll(): List<MuscleWeeklyStimulus> = stimulus.toList()

    override suspend fun getSince(windowStart: Instant): List<MuscleWeeklyStimulus> {
        getSinceError?.let { throw it }
        return stimulus.filter { !it.weekStart.isBefore(windowStart) }
    }

    var getSinceError: Throwable? = null

    fun seed(stimulus: List<MuscleWeeklyStimulus>) {
        this.stimulus.clear()
        this.stimulus.addAll(stimulus)
    }
}

class FakeLiftWeeklyPerformanceRepository(
    initial: List<LiftWeeklyPerformance> = emptyList(),
) : LiftWeeklyPerformanceRepository {
    private val performance = initial.toMutableList()

    override suspend fun replaceAll(performance: List<LiftWeeklyPerformance>) {
        this.performance.clear()
        this.performance.addAll(performance)
    }

    var getAllError: Throwable? = null

    override suspend fun getAll(): List<LiftWeeklyPerformance> {
        getAllError?.let { throw it }
        return performance.toList()
    }

    override suspend fun getSince(windowStart: Instant): List<LiftWeeklyPerformance> =
        performance.filter { !it.weekStart.isBefore(windowStart) }

    fun seed(performance: List<LiftWeeklyPerformance>) {
        this.performance.clear()
        this.performance.addAll(performance)
    }
}

class FakeMuscleWeeklyOutcomeIndexRepository(
    initial: List<MuscleWeeklyOutcomeIndex> = emptyList(),
) : MuscleWeeklyOutcomeIndexRepository {
    private val indices = initial.toMutableList()

    override suspend fun replaceAll(indices: List<MuscleWeeklyOutcomeIndex>) {
        this.indices.clear()
        this.indices.addAll(indices)
    }

    override suspend fun getAll(): List<MuscleWeeklyOutcomeIndex> = indices.toList()

    override suspend fun getSince(windowStart: Instant): List<MuscleWeeklyOutcomeIndex> =
        indices.filter { !it.weekStart.isBefore(windowStart) }

    fun seed(indices: List<MuscleWeeklyOutcomeIndex>) {
        this.indices.clear()
        this.indices.addAll(indices)
    }
}

/** Records enqueue calls for finalize / scheduler unit tests. */
class FakeResponseModelingSchedulerPort : ResponseModelingSchedulerPort {
    var enqueueCount: Int = 0
        private set

    var forceExtractCount: Int = 0
        private set

    /** Last [replaceExisting] passed to [enqueueForceExtract], or null if never called. */
    var lastForceReplaceExisting: Boolean? = null
        private set

    /** Alias for older tests that asserted history-invalidation force path. */
    val historyInvalidationCount: Int get() = forceExtractCount

    override fun enqueueRefresh() {
        enqueueCount++
    }

    override fun enqueueForceExtract(replaceExisting: Boolean) {
        forceExtractCount++
        lastForceReplaceExisting = replaceExisting
    }
}
