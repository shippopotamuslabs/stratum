package com.stratum.core.testing.response

import com.stratum.domain.response.ExtractedResponseProfile
import com.stratum.domain.response.ResponseProfilePersistencePort
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus

class FakeResponseProfilePersistencePort(
    val epochRepository: FakeResponseEpochRepository = FakeResponseEpochRepository(),
    val volumeRepository: FakeVolumeResponseObservationRepository = FakeVolumeResponseObservationRepository(),
    val effectivenessRepository: FakeLiftEffectivenessObservationRepository = FakeLiftEffectivenessObservationRepository(),
    val experimentRepository: FakeExperimentBlockRepository = FakeExperimentBlockRepository(),
    val metadataRepository: FakeResponseProfileMetadataRepository = FakeResponseProfileMetadataRepository(),
    val snapshotRepository: FakeMuscleVolumeResponseSnapshotRepository = FakeMuscleVolumeResponseSnapshotRepository(),
    val weeklyStimulusRepository: FakeMuscleWeeklyStimulusRepository = FakeMuscleWeeklyStimulusRepository(),
    val liftPerformanceRepository: FakeLiftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
    val outcomeIndexRepository: FakeMuscleWeeklyOutcomeIndexRepository = FakeMuscleWeeklyOutcomeIndexRepository(),
) : ResponseProfilePersistencePort {
    override suspend fun replaceExtractedProfile(profile: ExtractedResponseProfile) {
        epochRepository.replaceAll(profile.epochs)
        volumeRepository.replaceAll(profile.volumeResponses)
        effectivenessRepository.replaceAll(profile.liftEffectiveness)
        experimentRepository.replaceAll(profile.experimentBlocks)
        metadataRepository.save(profile.metadata)
        snapshotRepository.replaceAll(profile.volumeSnapshots)
        weeklyStimulusRepository.replaceAll(profile.weeklyStimulus)
        liftPerformanceRepository.replaceAll(profile.liftPerformance)
        outcomeIndexRepository.replaceAll(profile.outcomeIndices)
    }

    override suspend fun replaceWeeklySeries(profile: ExtractedResponseProfile) {
        weeklyStimulusRepository.replaceAll(profile.weeklyStimulus)
        liftPerformanceRepository.replaceAll(profile.liftPerformance)
    }

    override suspend fun clearAll() {
        epochRepository.replaceAll(emptyList())
        volumeRepository.replaceAll(emptyList())
        effectivenessRepository.replaceAll(emptyList())
        experimentRepository.replaceAll(emptyList())
        metadataRepository.clear()
        snapshotRepository.replaceAll(emptyList())
        weeklyStimulusRepository.replaceAll(emptyList())
        liftPerformanceRepository.replaceAll(emptyList())
        outcomeIndexRepository.replaceAll(emptyList())
    }
}

/**
 * Simulates a failure on the second persistence step (volume observations).
 */
class FailOnVolumeResponseProfilePersistencePort(
    private val delegate: FakeResponseProfilePersistencePort = FakeResponseProfilePersistencePort(),
) : ResponseProfilePersistencePort {
    val epochRepository: FakeResponseEpochRepository
        get() = delegate.epochRepository

    val volumeRepository: FakeVolumeResponseObservationRepository
        get() = delegate.volumeRepository

    val metadataRepository: FakeResponseProfileMetadataRepository
        get() = delegate.metadataRepository

    val weeklyStimulusRepository: FakeMuscleWeeklyStimulusRepository
        get() = delegate.weeklyStimulusRepository

    val liftPerformanceRepository: FakeLiftWeeklyPerformanceRepository
        get() = delegate.liftPerformanceRepository

    override suspend fun replaceExtractedProfile(profile: ExtractedResponseProfile) {
        delegate.epochRepository.replaceAll(profile.epochs)
        error("volume persistence failed")
    }

    override suspend fun replaceWeeklySeries(profile: ExtractedResponseProfile) {
        delegate.replaceWeeklySeries(profile)
    }

    override suspend fun clearAll() {
        delegate.clearAll()
    }
}
