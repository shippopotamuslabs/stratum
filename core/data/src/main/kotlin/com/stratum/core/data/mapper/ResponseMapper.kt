package com.stratum.core.data.mapper

import com.stratum.core.data.db.ExperimentBlockEntity
import com.stratum.core.data.db.LiftEffectivenessObservationEntity
import com.stratum.core.data.db.ResponseEpochEntity
import com.stratum.core.data.db.ResponseProfileMetadataEntity
import com.stratum.core.data.db.VolumeResponseObservationEntity
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.ExperimentBlock
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.OutcomeDescriptor
import com.stratum.domain.response.RegressionReliability
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.response.StimulusDescriptor
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.WeeklySetRange
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.response.identity.ExperimentId
import java.time.Instant

internal fun ResponseEpochEntity.toDomain(): Epoch =
    Epoch(
        id = id,
        start = Instant.ofEpochMilli(startEpochMillis),
        end = Instant.ofEpochMilli(endEpochMillis),
        anchorLiftIds = anchorLiftIdsCsv.splitCsvToLiftIds(),
        splitReason = splitReason,
    )

internal fun Epoch.toEntity(): ResponseEpochEntity =
    ResponseEpochEntity(
        id = id,
        startEpochMillis = start.toEpochMilli(),
        endEpochMillis = end.toEpochMilli(),
        anchorLiftIdsCsv = anchorLiftIds.joinToString(",") { it.value },
        splitReason = splitReason,
    )

internal fun VolumeResponseObservationEntity.toDomain(): VolumeResponseObservation =
    VolumeResponseObservation(
        muscle = muscle,
        epochId = EpochId(epochId),
        anchorLiftId = LiftId(anchorLiftId),
        stimulus =
            StimulusDescriptor(
                epochId = stimulusEpochId?.let(::EpochId),
                experimentId = stimulusExperimentId?.let(::ExperimentId),
                muscle = stimulusMuscle,
                pattern = stimulusPattern,
                liftId = stimulusLiftId?.let(::LiftId),
                weeklyHardSets = stimulusWeeklyHardSets,
                sessionsPerWeek = stimulusSessionsPerWeek,
                intensityProxy = stimulusIntensityProxy,
            ),
        outcome =
            OutcomeDescriptor(
                anchorLiftId = LiftId(outcomeAnchorLiftId),
                metric = outcomeMetric,
                value = outcomeValue,
                windowWeeks = outcomeWindowWeeks,
                reliability =
                    RegressionReliability(
                        rSquared = outcomeRSquared,
                        outlierCount = outcomeOutlierCount,
                        completenessScore = outcomeCompletenessScore,
                    ),
            ),
        correlationSlope = correlationSlope,
        confidenceScore = confidenceScore,
        evidence = evidence,
        weeklyPointsUsed = weeklyPointsUsed,
        epochWeeksUsed = epochWeeksUsed,
        observedWeeklySetRange = WeeklySetRange(observedMinWeeklySets, observedMaxWeeklySets),
        bestObservedWeeklySetRange =
            bestObservedMinWeeklySets?.let { min ->
                WeeklySetRange(min, bestObservedMaxWeeklySets ?: min)
            },
    )

internal fun VolumeResponseObservation.toEntity(): VolumeResponseObservationEntity =
    VolumeResponseObservationEntity(
        muscle = muscle,
        epochId = epochId.value,
        anchorLiftId = anchorLiftId.value,
        stimulusEpochId = stimulus.epochId?.value,
        stimulusExperimentId = stimulus.experimentId?.value,
        stimulusMuscle = stimulus.muscle,
        stimulusPattern = stimulus.pattern,
        stimulusLiftId = stimulus.liftId?.value,
        stimulusWeeklyHardSets = stimulus.weeklyHardSets,
        stimulusSessionsPerWeek = stimulus.sessionsPerWeek,
        stimulusIntensityProxy = stimulus.intensityProxy,
        outcomeAnchorLiftId = outcome.anchorLiftId.value,
        outcomeMetric = outcome.metric,
        outcomeValue = outcome.value,
        outcomeWindowWeeks = outcome.windowWeeks,
        outcomeRSquared = outcome.reliability.rSquared,
        outcomeOutlierCount = outcome.reliability.outlierCount,
        outcomeCompletenessScore = outcome.reliability.completenessScore,
        correlationSlope = correlationSlope,
        confidenceScore = confidenceScore,
        evidence = evidence,
        weeklyPointsUsed = weeklyPointsUsed,
        epochWeeksUsed = epochWeeksUsed,
        observedMinWeeklySets = observedWeeklySetRange.min,
        observedMaxWeeklySets = observedWeeklySetRange.max,
        bestObservedMinWeeklySets = bestObservedWeeklySetRange?.min,
        bestObservedMaxWeeklySets = bestObservedWeeklySetRange?.max,
    )

internal fun LiftEffectivenessObservationEntity.toDomain(): LiftEffectivenessObservation =
    LiftEffectivenessObservation(
        pattern = pattern,
        liftId = LiftId(liftId),
        epochId = EpochId(epochId),
        effectivenessScore = effectivenessScore,
        peerLiftCount = peerLiftCount,
        rank = rank,
        evidence = evidence,
        confidenceScore = confidenceScore,
    )

internal fun LiftEffectivenessObservation.toEntity(): LiftEffectivenessObservationEntity =
    LiftEffectivenessObservationEntity(
        pattern = pattern,
        liftId = liftId.value,
        epochId = epochId.value,
        effectivenessScore = effectivenessScore,
        peerLiftCount = peerLiftCount,
        rank = rank,
        evidence = evidence,
        confidenceScore = confidenceScore,
    )

internal fun ExperimentBlockEntity.toDomain(): ExperimentBlock =
    ExperimentBlock(
        id = id,
        prescribed =
            StimulusDescriptor(
                epochId = prescribedEpochId?.let(::EpochId),
                experimentId = prescribedExperimentId?.let(::ExperimentId),
                muscle = prescribedMuscle,
                pattern = prescribedPattern,
                liftId = prescribedLiftId?.let(::LiftId),
                weeklyHardSets = prescribedWeeklyHardSets,
                sessionsPerWeek = prescribedSessionsPerWeek,
                intensityProxy = prescribedIntensityProxy,
            ),
        outcomeWindowStart = Instant.ofEpochMilli(outcomeWindowStartEpochMillis),
        outcomeWindowEnd = Instant.ofEpochMilli(outcomeWindowEndEpochMillis),
        status = status,
    )

internal fun ExperimentBlock.toEntity(): ExperimentBlockEntity =
    ExperimentBlockEntity(
        id = id,
        prescribedEpochId = prescribed.epochId?.value,
        prescribedExperimentId = prescribed.experimentId?.value,
        prescribedMuscle = prescribed.muscle,
        prescribedPattern = prescribed.pattern,
        prescribedLiftId = prescribed.liftId?.value,
        prescribedWeeklyHardSets = prescribed.weeklyHardSets,
        prescribedSessionsPerWeek = prescribed.sessionsPerWeek,
        prescribedIntensityProxy = prescribed.intensityProxy,
        outcomeWindowStartEpochMillis = outcomeWindowStart.toEpochMilli(),
        outcomeWindowEndEpochMillis = outcomeWindowEnd.toEpochMilli(),
        status = status,
    )

internal fun ResponseProfileMetadataEntity.toDomain(): ResponseProfileMetadata =
    ResponseProfileMetadata(
        epochsAnalyzed = epochsAnalyzed,
        analysisWindowStart = Instant.ofEpochMilli(analysisWindowStartEpochMillis),
        analysisWindowEnd = Instant.ofEpochMilli(analysisWindowEndEpochMillis),
        lastExtractedAt = Instant.ofEpochMilli(lastExtractedAtEpochMillis),
        pipelineVersion = pipelineVersion,
    )

internal fun ResponseProfileMetadata.toEntity(): ResponseProfileMetadataEntity =
    ResponseProfileMetadataEntity(
        epochsAnalyzed = epochsAnalyzed,
        analysisWindowStartEpochMillis = analysisWindowStart.toEpochMilli(),
        analysisWindowEndEpochMillis = analysisWindowEnd.toEpochMilli(),
        lastExtractedAtEpochMillis = lastExtractedAt.toEpochMilli(),
        pipelineVersion = pipelineVersion,
    )

private fun String.splitCsvToLiftIds(): Set<LiftId> =
    if (isBlank()) {
        emptySet()
    } else {
        split(",").filter { it.isNotBlank() }.map(::LiftId).toSet()
    }
