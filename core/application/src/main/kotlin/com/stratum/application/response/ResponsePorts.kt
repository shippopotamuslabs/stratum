package com.stratum.application.response

import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleVolumeResponseSnapshot
import com.stratum.domain.response.modeling.MuscleWeeklyOutcomeIndex
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import java.time.Instant
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.ExperimentBlock
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.application.training.FinalizeTrainingSessionUseCase
import com.stratum.application.training.RebuildLocalDerivedTrainingDataUseCase
import com.stratum.application.training.RefreshWeeklySeriesUseCase
import com.stratum.domain.dashboard.windowStart
import com.stratum.domain.response.RE_EXTRACT_MIN_COMPLETED_WORKOUTS

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
 * [com.stratum.application.training.RefreshWeeklySeriesUseCase] on workout completion and after cloud
 * pull ([com.stratum.application.training.RebuildLocalDerivedTrainingDataUseCase]). Not cloud-synced.
 */
interface MuscleWeeklyStimulusRepository {
    suspend fun replaceAll(stimulus: List<MuscleWeeklyStimulus>)

    suspend fun getAll(): List<MuscleWeeklyStimulus>

    suspend fun getSince(windowStart: Instant): List<MuscleWeeklyStimulus>
}

/**
 * Read access to the per-lift/week peak-e1RM performance series. The dashboard reads a bounded
 * window ([getSince]) for its 3-month charts; the series is refreshed by
 * [com.stratum.application.training.RefreshWeeklySeriesUseCase] on workout completion and after cloud
 * pull ([com.stratum.application.training.RebuildLocalDerivedTrainingDataUseCase]). Not cloud-synced.
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
 * [com.stratum.application.training.FinalizeTrainingSessionUseCase] can request work without depending on
 * WorkManager (Android-only; implemented in `:core:data`).
 *
 * Implementations must enqueue non-blockingly (schedule only — never await worker completion).
 */
interface ResponseModelingSchedulerPort {
    /**
     * Normal path after finalize: worker applies the
     * [RE_EXTRACT_MIN_COMPLETED_WORKOUTS] gate before extracting.
     */
    fun enqueueRefresh()

    /**
     * Force a full dose-response extract, skipping the completed-workout gate. Does not open a
     * coach check-in.
     *
     * Callers:
     * - History edit: a session already included in the last modeling run was amended
     *   ([replaceExisting] = true so corrected data always re-runs).
     * - Settings recovery: user rebuilds progress after import / empty outcome indices
     *   ([replaceExisting] = false so re-taps do not cancel an in-flight force job).
     *
     * @param replaceExisting when true, REPLACE any pending/running unique work; when false, KEEP
     *   an already-scheduled force extract.
     */
    fun enqueueForceExtract(replaceExisting: Boolean = true)
}

interface ExperimentRegistry {
    fun active(): List<ExperimentBlock>

    fun record(block: ExperimentBlock)
}
