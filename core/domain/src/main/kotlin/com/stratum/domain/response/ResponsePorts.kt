package com.stratum.domain.response

import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleVolumeResponseSnapshot
import com.stratum.domain.response.modeling.MuscleWeeklyOutcomeIndex
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import java.time.Instant

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

interface MuscleVolumeResponseSnapshotRepository {
    suspend fun replaceAll(snapshots: List<MuscleVolumeResponseSnapshot>)

    suspend fun getAll(): List<MuscleVolumeResponseSnapshot>
}

/**
 * Read access to the per-muscle/week hard-set stimulus series. The dashboard reads a bounded window
 * ([getSince]) for its 3-month charts; the series is refreshed by
 * [com.stratum.domain.training.RefreshWeeklySeriesUseCase] on workout completion.
 */
interface MuscleWeeklyStimulusRepository {
    suspend fun replaceAll(stimulus: List<MuscleWeeklyStimulus>)

    suspend fun getAll(): List<MuscleWeeklyStimulus>

    suspend fun getSince(windowStart: Instant): List<MuscleWeeklyStimulus>
}

/**
 * Read access to the per-lift/week peak-e1RM performance series. The dashboard reads a bounded
 * window ([getSince]) for its 3-month charts; the series is refreshed by
 * [com.stratum.domain.training.RefreshWeeklySeriesUseCase] on workout completion.
 */
interface LiftWeeklyPerformanceRepository {
    suspend fun replaceAll(performance: List<LiftWeeklyPerformance>)

    suspend fun getAll(): List<LiftWeeklyPerformance>

    suspend fun getSince(windowStart: Instant): List<LiftWeeklyPerformance>
}

/**
 * Read access to the per-muscle/week outcome index (log-e1RM deviation, credit-weighted). Written by
 * the full extract pipeline ([ExtractResponseProfileUseCase] / response-modeling worker); the
 * dashboard reads a bounded window ([getSince]) for the muscle progress line.
 */
interface MuscleWeeklyOutcomeIndexRepository {
    suspend fun replaceAll(indices: List<MuscleWeeklyOutcomeIndex>)

    suspend fun getAll(): List<MuscleWeeklyOutcomeIndex>

    suspend fun getSince(windowStart: Instant): List<MuscleWeeklyOutcomeIndex>
}

/**
 * Schedules a background re-run of the dose-response extract pipeline. Defined as a domain port so
 * [com.stratum.domain.training.FinalizeTrainingSessionUseCase] can request work without depending on
 * WorkManager (Android-only; implemented in `:core:data`).
 *
 * Implementations must enqueue non-blockingly (schedule only — never await worker completion).
 */
interface ResponseModelingSchedulerPort {
    fun enqueueRefresh()
}

interface ExperimentRegistry {
    fun active(): List<ExperimentBlock>

    fun record(block: ExperimentBlock)
}
