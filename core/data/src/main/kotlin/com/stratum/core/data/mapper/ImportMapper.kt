package com.stratum.core.data.mapper

import com.stratum.core.data.db.ImportAnalysisMetadataEntity
import com.stratum.core.data.db.ImportJobEntity
import com.stratum.core.data.db.LiftTrainingInsightsEntity
import com.stratum.core.data.db.MuscleVolumeBaselineEntity
import com.stratum.core.data.db.SupersetAffinityEntity
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportJob
import com.stratum.domain.importing.ImportJobStatus
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.ProgressTrend
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import java.time.Instant

internal fun ImportJobEntity.toDomain(): ImportJob = ImportJob(
    id = id,
    status = status,
    sampleRef = sampleRef,
    fullRef = fullRef,
    scriptRef = scriptRef,
    errorMessage = errorMessage,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
)

internal fun ImportJob.toEntity(): ImportJobEntity = ImportJobEntity(
    id = id,
    status = status,
    sampleRef = sampleRef,
    fullRef = fullRef,
    scriptRef = scriptRef,
    errorMessage = errorMessage,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
)

internal fun LiftTrainingInsightsEntity.toDomain(): LiftTrainingInsights = LiftTrainingInsights(
    liftId = liftId,
    estimatedOneRm = estimatedOneRm?.let { amount ->
        Weight(amount, weightUnit?.let(Weight.WeightUnit::parse) ?: Weight.WeightUnit.Lb)
    },
    fatigueDecaySlope = fatigueDecaySlope,
    fatigueDecayIntercept = fatigueDecayIntercept,
    sessionCount = sessionCount,
    progressTrend = progressTrend?.let(ProgressTrend::valueOf),
    lastPerformedAt = lastPerformedAtEpochMillis?.let(Instant::ofEpochMilli),
)

internal fun LiftTrainingInsights.toEntity(): LiftTrainingInsightsEntity = LiftTrainingInsightsEntity(
    liftId = liftId,
    estimatedOneRm = estimatedOneRm?.amount,
    weightUnit = estimatedOneRm?.unit?.name,
    fatigueDecaySlope = fatigueDecaySlope,
    fatigueDecayIntercept = fatigueDecayIntercept,
    sessionCount = sessionCount,
    progressTrend = progressTrend?.name,
    lastPerformedAtEpochMillis = lastPerformedAt?.toEpochMilli(),
)

internal fun SupersetAffinityEntity.toDomain(): SupersetAffinity = SupersetAffinity(
    liftA = LiftId(liftA),
    liftB = LiftId(liftB),
    medianRestSeconds = medianRestSeconds,
    coOccurrenceCount = coOccurrenceCount,
    confidence = confidence,
)

internal fun SupersetAffinity.toEntity(): SupersetAffinityEntity {
    // Canonical pair ordering (liftA < liftB lexicographically) so the unique index on (liftA, liftB) is stable.
    val (first, second) = if (liftA.value <= liftB.value) liftA to liftB else liftB to liftA
    return SupersetAffinityEntity(
        liftA = first.value,
        liftB = second.value,
        medianRestSeconds = medianRestSeconds,
        coOccurrenceCount = coOccurrenceCount,
        confidence = confidence,
    )
}

internal fun MuscleVolumeBaselineEntity.toDomain(): MuscleVolumeBaseline = MuscleVolumeBaseline(
    muscle = muscle,
    meanWeeklyHardSets = meanWeeklyHardSets,
    p90WeeklyHardSets = p90WeeklyHardSets,
    trendSlope = trendSlope,
)

internal fun MuscleVolumeBaseline.toEntity(): MuscleVolumeBaselineEntity = MuscleVolumeBaselineEntity(
    muscle = muscle,
    meanWeeklyHardSets = meanWeeklyHardSets,
    p90WeeklyHardSets = p90WeeklyHardSets,
    trendSlope = trendSlope,
)

internal fun ImportAnalysisMetadataEntity.toDomain(): ImportAnalysisMetadata = ImportAnalysisMetadata(
    windowStart = Instant.ofEpochMilli(windowStartEpochMillis),
    windowEnd = Instant.ofEpochMilli(windowEndEpochMillis),
    provenLiftIds = provenLiftIdsCsv.toLiftIdList(),
    unmatchedExerciseNames = if (unmatchedExerciseNamesCsv.isBlank()) {
        emptyList()
    } else {
        unmatchedExerciseNamesCsv.split(',')
    },
    totalSessions = totalSessions,
    totalSets = totalSets,
    extractedAt = Instant.ofEpochMilli(extractedAtEpochMillis),
    pipelineVersion = pipelineVersion,
)

internal fun ImportAnalysisMetadata.toEntity(): ImportAnalysisMetadataEntity = ImportAnalysisMetadataEntity(
    windowStartEpochMillis = windowStart.toEpochMilli(),
    windowEndEpochMillis = windowEnd.toEpochMilli(),
    provenLiftIdsCsv = provenLiftIds.joinToString(",") { it.value },
    unmatchedExerciseNamesCsv = unmatchedExerciseNames.joinToString(","),
    totalSessions = totalSessions,
    totalSets = totalSets,
    extractedAtEpochMillis = extractedAt.toEpochMilli(),
    pipelineVersion = pipelineVersion,
)

private fun String.toLiftIdList(): List<LiftId> =
    if (isBlank()) {
        emptyList()
    } else {
        split(',').map(::LiftId)
    }
