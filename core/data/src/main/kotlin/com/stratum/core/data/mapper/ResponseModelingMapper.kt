package com.stratum.core.data.mapper

import com.stratum.core.data.db.LiftWeeklyPerformanceEntity
import com.stratum.core.data.db.MuscleDoseResponsePointEntity
import com.stratum.core.data.db.MuscleResponseModelDiagnosticEntity
import com.stratum.core.data.db.MuscleVolumeResponseSnapshotEntity
import com.stratum.core.data.db.MuscleWeeklyOutcomeIndexEntity
import com.stratum.core.data.db.MuscleWeeklyStimulusEntity
import com.stratum.domain.importing.LandmarkConfidenceInterval
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.response.TrainingSignalQualityFlag
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import com.stratum.domain.response.modeling.MuscleDoseResponsePoint
import com.stratum.domain.response.modeling.MuscleResponseModelDiagnostic
import com.stratum.domain.response.modeling.MuscleVolumeResponseSnapshot
import com.stratum.domain.response.modeling.MuscleWeeklyOutcomeIndex
import com.stratum.domain.response.modeling.MuscleWeeklyStimulus
import java.time.Instant

internal fun MuscleWeeklyStimulusEntity.toDomain(): MuscleWeeklyStimulus =
    MuscleWeeklyStimulus(
        muscle = muscle,
        weekStart = Instant.ofEpochMilli(weekStartEpochMillis),
        hardSets = hardSets,
        sessions = sessions,
        meanRir = meanRir,
        intensityProxy = intensityProxy,
        compositionHash = compositionHash,
        deloadFlag = deloadFlag,
        qualityFlags = qualityFlagsCsv.splitCsvToQualityFlags(),
    )

internal fun MuscleWeeklyStimulus.toEntity(): MuscleWeeklyStimulusEntity =
    MuscleWeeklyStimulusEntity(
        muscle = muscle,
        weekStartEpochMillis = weekStart.toEpochMilli(),
        hardSets = hardSets,
        sessions = sessions,
        meanRir = meanRir,
        intensityProxy = intensityProxy,
        compositionHash = compositionHash,
        deloadFlag = deloadFlag,
        qualityFlagsCsv = qualityFlags.joinToCsv(),
    )

internal fun LiftWeeklyPerformanceEntity.toDomain(): LiftWeeklyPerformance =
    LiftWeeklyPerformance(
        liftId = LiftId(liftId),
        weekStart = Instant.ofEpochMilli(weekStartEpochMillis),
        peakE1rm = peakE1rm,
        smoothedE1rm = smoothedE1rm,
        logE1rm = logE1rm,
        hardSets = hardSets,
        sessions = sessions,
        meanRir = meanRir,
        reliability = reliability,
        qualityFlags = qualityFlagsCsv.splitCsvToQualityFlags(),
    )

internal fun LiftWeeklyPerformance.toEntity(): LiftWeeklyPerformanceEntity =
    LiftWeeklyPerformanceEntity(
        liftId = liftId.value,
        weekStartEpochMillis = weekStart.toEpochMilli(),
        peakE1rm = peakE1rm,
        smoothedE1rm = smoothedE1rm,
        logE1rm = logE1rm,
        hardSets = hardSets,
        sessions = sessions,
        meanRir = meanRir,
        reliability = reliability,
        qualityFlagsCsv = qualityFlags.joinToCsv(),
    )

internal fun MuscleWeeklyOutcomeIndexEntity.toDomain(): MuscleWeeklyOutcomeIndex =
    MuscleWeeklyOutcomeIndex(
        muscle = muscle,
        weekStart = Instant.ofEpochMilli(weekStartEpochMillis),
        indexValue = indexValue,
        indexStdError = indexStdError,
        contributingLiftCount = contributingLiftCount,
        effectiveLiftCount = effectiveLiftCount,
        primaryCoverage = primaryCoverage,
        compositionHash = compositionHash,
        compositionDrift = compositionDrift,
        qualityFlags = qualityFlagsCsv.splitCsvToQualityFlags(),
    )

internal fun MuscleWeeklyOutcomeIndex.toEntity(): MuscleWeeklyOutcomeIndexEntity =
    MuscleWeeklyOutcomeIndexEntity(
        muscle = muscle,
        weekStartEpochMillis = weekStart.toEpochMilli(),
        indexValue = indexValue,
        indexStdError = indexStdError,
        contributingLiftCount = contributingLiftCount,
        effectiveLiftCount = effectiveLiftCount,
        primaryCoverage = primaryCoverage,
        compositionHash = compositionHash,
        compositionDrift = compositionDrift,
        qualityFlagsCsv = qualityFlags.joinToCsv(),
    )

internal fun MuscleDoseResponsePointEntity.toDomain(): MuscleDoseResponsePoint =
    MuscleDoseResponsePoint(
        muscle = muscle,
        weekStart = Instant.ofEpochMilli(weekStartEpochMillis),
        dose = dose,
        response = response,
        lagWeeks = lagWeeks,
        responseWindowWeeks = responseWindowWeeks,
    )

internal fun MuscleDoseResponsePoint.toEntity(): MuscleDoseResponsePointEntity =
    MuscleDoseResponsePointEntity(
        muscle = muscle,
        weekStartEpochMillis = weekStart.toEpochMilli(),
        dose = dose,
        response = response,
        lagWeeks = lagWeeks,
        responseWindowWeeks = responseWindowWeeks,
    )

internal fun MuscleVolumeResponseSnapshotEntity.toDomain(): MuscleVolumeResponseSnapshot =
    MuscleVolumeResponseSnapshot(
        muscle = muscle,
        retentionFloorMv = retentionFloorMv,
        paretoVolumePv = paretoVolumePv,
        recoverabilityCeilingRc = recoverabilityCeilingRc,
        growthEntryFloor = growthEntryFloor,
        observedDoseMin = observedDoseMin,
        observedDoseMax = observedDoseMax,
        observedDoseMedian = observedDoseMedian,
        productiveBandMin = productiveBandMin,
        productiveBandMax = productiveBandMax,
        posture = posture,
        pvInterval = interval(pvIntervalLow, pvIntervalHigh, pvIntervalMethod),
        confidenceTier = confidenceTier,
        outcomeModelType = outcomeModelType,
        diagnosticAnchorLiftId = diagnosticAnchorLiftId?.let(::LiftId),
    )

internal fun MuscleVolumeResponseSnapshot.toEntity(): MuscleVolumeResponseSnapshotEntity =
    MuscleVolumeResponseSnapshotEntity(
        muscle = muscle,
        retentionFloorMv = retentionFloorMv,
        paretoVolumePv = paretoVolumePv,
        recoverabilityCeilingRc = recoverabilityCeilingRc,
        growthEntryFloor = growthEntryFloor,
        observedDoseMin = observedDoseMin,
        observedDoseMax = observedDoseMax,
        observedDoseMedian = observedDoseMedian,
        productiveBandMin = productiveBandMin,
        productiveBandMax = productiveBandMax,
        posture = posture,
        pvIntervalLow = pvInterval.low,
        pvIntervalHigh = pvInterval.high,
        pvIntervalMethod = pvInterval.method,
        confidenceTier = confidenceTier,
        outcomeModelType = outcomeModelType,
        diagnosticAnchorLiftId = diagnosticAnchorLiftId?.value,
    )

internal fun MuscleResponseModelDiagnosticEntity.toDomain(): MuscleResponseModelDiagnostic =
    MuscleResponseModelDiagnostic(
        muscle = muscle,
        selectedLagWeeks = selectedLagWeeks,
        responseWindowWeeks = responseWindowWeeks,
        pairedPointCount = pairedPointCount,
        volumeBinCount = volumeBinCount,
        indexWeekCount = indexWeekCount,
        peakDoseCoverage = peakDoseCoverage,
        compositionStability = compositionStability,
        residualError = residualError,
        predictiveScore = predictiveScore,
        anchorAgreement = anchorAgreement,
        rejectionReasons = rejectionReasonsCsv.split("|").filter { it.isNotBlank() },
        confidenceScore = confidenceScore,
    )

internal fun MuscleResponseModelDiagnostic.toEntity(): MuscleResponseModelDiagnosticEntity =
    MuscleResponseModelDiagnosticEntity(
        muscle = muscle,
        selectedLagWeeks = selectedLagWeeks,
        responseWindowWeeks = responseWindowWeeks,
        pairedPointCount = pairedPointCount,
        volumeBinCount = volumeBinCount,
        indexWeekCount = indexWeekCount,
        peakDoseCoverage = peakDoseCoverage,
        compositionStability = compositionStability,
        residualError = residualError,
        predictiveScore = predictiveScore,
        anchorAgreement = anchorAgreement,
        rejectionReasonsCsv = rejectionReasons.joinToString("|"),
        confidenceScore = confidenceScore,
    )

private fun interval(
    low: Double,
    high: Double,
    method: com.stratum.domain.importing.IntervalMethod,
): LandmarkConfidenceInterval = LandmarkConfidenceInterval(low = low, high = high, method = method)

private fun Set<TrainingSignalQualityFlag>.joinToCsv(): String = joinToString(",") { it.name }

private fun String.splitCsvToQualityFlags(): Set<TrainingSignalQualityFlag> =
    if (isBlank()) {
        emptySet()
    } else {
        split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { TrainingSignalQualityFlag.valueOf(it) }.getOrNull() }
            .toSet()
    }
