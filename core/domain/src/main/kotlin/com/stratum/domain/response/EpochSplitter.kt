package com.stratum.domain.response

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.SortedMap

object EpochSplitter {
    fun split(input: ResponseExtractionInput): List<Epoch> {
        val orderedSessions = input.sessions.sortedBy { it.startedAt }
        if (orderedSessions.isEmpty()) {
            return listOf(
                singleEpoch(
                    index = 0,
                    start = input.analysisWindowStart,
                    end = input.analysisWindowEnd,
                    reason = EpochSplitReason.WindowEnd,
                ),
            )
        }

        val cleanSets = TrainingSignalCleaner.clean(input)
        val splitPoints = mutableListOf<SplitPoint>()
        splitPoints += sessionGapSplits(orderedSessions)
        splitPoints += equipmentShiftSplits(orderedSessions, cleanSets)
        splitPoints += primaryLiftSplits(orderedSessions, cleanSets)
        splitPoints += changePointSplits(input, cleanSets, splitPoints)

        val uniqueStarts =
            splitPoints
                .sortedWith(compareBy<SplitPoint> { it.start }.thenBy { it.priority })
                .distinctBy { it.start }
        return buildEpochs(
            splitPoints = uniqueStarts,
            windowStart = input.analysisWindowStart,
            windowEnd = input.analysisWindowEnd,
        )
    }

    fun markDeloadWeeks(
        epoch: Epoch,
        input: ResponseExtractionInput,
        cleanSets: List<CleanTrainingSet> = TrainingSignalCleaner.clean(input),
    ): Set<Instant> {
        val working = WeeklySignalSupport.workingSetsInEpoch(epoch, cleanSets)
        if (working.isEmpty()) return emptySet()

        val anchorLiftId = working.groupingBy { it.sourceSet.liftId!! }.eachCount().maxByOrNull { it.value }?.key
        val anchorWorking = anchorLiftId?.let { WeeklySignalSupport.workingSetsInEpoch(epoch, cleanSets, it) } ?: working
        val weeklyHardSets =
            WeeklySignalSupport.groupByWeek(anchorWorking).mapValues { (_, weekSets) -> weekSets.size }
        if (weeklyHardSets.isEmpty()) return emptySet()

        val medianWeeklyHardSets = weeklyHardSets.values.sorted()[weeklyHardSets.size / 2].toDouble()
        val weeklyAnchorPeaks =
            anchorLiftId?.let { liftId ->
                OutcomeSeriesBuilder.weeklyAnchorOutcomes(epoch, liftId, cleanSets)
                    .associate { it.weekStart to (it.peakEstimatedOneRm ?: 0.0) }
            } ?: emptyMap()

        val weeklyMeanWeight =
            anchorLiftId?.let { liftId ->
                WeeklySignalSupport.groupByWeek(WeeklySignalSupport.workingSetsInEpoch(epoch, cleanSets, liftId))
                    .mapValues { (_, weekSets) -> weekSets.map { it.normalizedWeight }.average() }
            } ?: emptyMap()

        val deloadWeeks = mutableSetOf<Instant>()
        var peakBeforeWeek = 0.0
        weeklyHardSets.toSortedMap().forEach { (weekStart, hardSets) ->
            val peak = weeklyAnchorPeaks[weekStart] ?: 0.0
            val volumeDeload =
                hardSets.toDouble() < DELOAD_VOLUME_FRACTION * medianWeeklyHardSets &&
                    peak <= peakBeforeWeek
            if (volumeDeload) {
                deloadWeeks += weekStart
                peakBeforeWeek = maxOf(peakBeforeWeek, peak)
                return@forEach
            }

            val meanWeight = weeklyMeanWeight[weekStart] ?: return@forEach
            val priorWeeks =
                weeklyMeanWeight.keys
                    .filter { it.isBefore(weekStart) }
                    .sorted()
                    .takeLast(ROLLING_PRIMARY_WINDOW_WEEKS)
            if (priorWeeks.size < ROLLING_PRIMARY_WINDOW_WEEKS) return@forEach
            val priorMean = priorWeeks.mapNotNull { weeklyMeanWeight[it] }.average()
            if (priorMean <= 0.0) return@forEach
            val weightDrop = (priorMean - meanWeight) / priorMean
            val volumeSteady =
                hardSets.toDouble() in
                    ((1.0 - DELOAD_VOLUME_STEADY_BAND) * medianWeeklyHardSets)..
                        ((1.0 + DELOAD_VOLUME_STEADY_BAND) * medianWeeklyHardSets)
            if (weightDrop > DELOAD_WEIGHT_DROP_FRACTION && volumeSteady) {
                deloadWeeks += weekStart
            }
            peakBeforeWeek = maxOf(peakBeforeWeek, peak)
        }
        return deloadWeeks
    }

    private data class SplitPoint(
        val start: Instant,
        val reason: EpochSplitReason,
        val priority: Int,
    )

    private fun changePointSplits(
        input: ResponseExtractionInput,
        cleanSets: List<CleanTrainingSet>,
        existingSplits: List<SplitPoint>,
    ): List<SplitPoint> {
        val working = cleanSets.filter { it.isWorkingSet && it.sourceSet.liftId != null }
        if (working.isEmpty()) return emptyList()

        val anchorLiftId =
            working.groupingBy { it.sourceSet.liftId!! }.eachCount().maxByOrNull { it.value }?.key
                ?: return emptyList()
        val scanEpoch =
            Epoch(
                id = EpochId("change-point-scan"),
                start = input.analysisWindowStart,
                end = input.analysisWindowEnd,
                anchorLiftIds = emptySet(),
                splitReason = EpochSplitReason.WindowEnd,
            )
        val deloadWeeks = markDeloadWeeks(scanEpoch, input, cleanSets)
        val signals = OutcomeSeriesBuilder.weeklyAnchorOutcomes(scanEpoch, anchorLiftId, cleanSets)
        return ChangePointDetector.binarySegment(signals).mapNotNull { boundary ->
            if (WeeklySignalSupport.weekStartFor(boundary) in deloadWeeks) return@mapNotNull null
            val duplicatesExistingSplit =
                existingSplits.any { split ->
                    ChronoUnit.DAYS.between(
                        WeeklySignalSupport.weekStartFor(split.start),
                        WeeklySignalSupport.weekStartFor(boundary),
                    ).let { days -> kotlin.math.abs(days) <= ROLLING_PRIMARY_WINDOW_WEEKS * 7L }
                }
            if (duplicatesExistingSplit) return@mapNotNull null
            SplitPoint(
                start = boundary,
                reason = EpochSplitReason.ChangePoint,
                priority = 4,
            )
        }
    }

    private fun sessionGapSplits(sessions: List<TrainingSession>): List<SplitPoint> {
        if (sessions.size < 2) return emptyList()
        val splits = mutableListOf<SplitPoint>()
        for (index in 1 until sessions.size) {
            val gapDays =
                ChronoUnit.DAYS.between(
                    sessions[index - 1].startedAt.atZone(ZoneOffset.UTC).toLocalDate(),
                    sessions[index].startedAt.atZone(ZoneOffset.UTC).toLocalDate(),
                )
            if (gapDays > MAX_EPOCH_GAP_DAYS) {
                splits +=
                    SplitPoint(
                        start = sessions[index].startedAt,
                        reason = EpochSplitReason.SessionGap,
                        priority = 0,
                    )
            }
        }
        return splits
    }

    private fun equipmentShiftSplits(
        sessions: List<TrainingSession>,
        cleanSets: List<CleanTrainingSet>,
    ): List<SplitPoint> {
        if (sessions.size < 2) return emptyList()
        val mixedUnitLifts =
            cleanSets.filter { TrainingSignalQualityFlag.MixedUnits in it.qualityFlags }
                .map { it.sourceSet.liftId!! }
                .toSet()
        if (mixedUnitLifts.isEmpty()) return emptyList()

        val splits = mutableListOf<SplitPoint>()
        for (index in 1 until sessions.size) {
            val previousSession = sessions[index - 1]
            val currentSession = sessions[index]
            val boundaryLift =
                mixedUnitLifts.firstOrNull { liftId ->
                    val previousUnits =
                        cleanSets.filter { it.sourceSet.sessionId == previousSession.id && it.sourceSet.liftId == liftId }
                            .map { it.sourceSet.weight.unit }
                            .toSet()
                    val currentUnits =
                        cleanSets.filter { it.sourceSet.sessionId == currentSession.id && it.sourceSet.liftId == liftId }
                            .map { it.sourceSet.weight.unit }
                            .toSet()
                    previousUnits.isNotEmpty() && currentUnits.isNotEmpty() && previousUnits != currentUnits
                }
            if (boundaryLift != null) {
                splits +=
                    SplitPoint(
                        start = currentSession.startedAt,
                        reason = EpochSplitReason.EquipmentShift,
                        priority = 1,
                    )
            }
        }
        return splits
    }

    private fun primaryLiftSplits(
        sessions: List<TrainingSession>,
        cleanSets: List<CleanTrainingSet>,
    ): List<SplitPoint> {
        val sessionById = sessions.associateBy { it.id }
        val weeklyPrimaryLifts =
            WeeklySignalSupport.groupByWeek(
                cleanSets.filter { it.isWorkingSet && it.sourceSet.liftId != null },
            ).mapValues { (_, weekSets) ->
                weekSets.map { it.sourceSet.liftId!! }.toSet()
            }.toSortedMap()

        if (weeklyPrimaryLifts.size < ROLLING_PRIMARY_WINDOW_WEEKS) return emptyList()

        val weekStarts = weeklyPrimaryLifts.keys.toList()
        val splits = mutableListOf<SplitPoint>()

        splits +=
            (0..weekStarts.size - ROLLING_PRIMARY_WINDOW_WEEKS step ROLLING_PRIMARY_WINDOW_WEEKS)
                .mapNotNull { blockStart ->
                    primaryLiftSplitPoint(
                        blockStart = blockStart,
                        weekStarts = weekStarts,
                        weeklyPrimaryLifts = weeklyPrimaryLifts,
                        cleanSets = cleanSets,
                        sessionById = sessionById,
                    )
                }

        splits +=
            (ROLLING_PRIMARY_WINDOW_WEEKS until weekStarts.size).mapNotNull { index ->
                liftChurnSplitPoint(
                    index = index,
                    weekStarts = weekStarts,
                    weeklyPrimaryLifts = weeklyPrimaryLifts,
                    cleanSets = cleanSets,
                )
            }

        return splits
    }

    private fun primaryLiftSplitPoint(
        blockStart: Int,
        weekStarts: List<Instant>,
        weeklyPrimaryLifts: SortedMap<Instant, Set<LiftId>>,
        cleanSets: List<CleanTrainingSet>,
        sessionById: Map<TrainingSessionId, TrainingSession>,
    ): SplitPoint? {
        val blockEnd = blockStart + ROLLING_PRIMARY_WINDOW_WEEKS
        if (blockEnd >= weekStarts.size) return null
        val leftWeeks = weekStarts.subList(blockStart, blockEnd)
        val rightWeeks =
            weekStarts.subList(blockEnd, minOf(blockEnd + ROLLING_PRIMARY_WINDOW_WEEKS, weekStarts.size))
        if (rightWeeks.size < ROLLING_PRIMARY_WINDOW_WEEKS) return null

        val leftLifts = leftWeeks.flatMap { weeklyPrimaryLifts[it].orEmpty() }.toSet()
        val rightLifts = rightWeeks.flatMap { weeklyPrimaryLifts[it].orEmpty() }.toSet()
        if (jaccardChange(leftLifts, rightLifts) <= PRIMARY_JACCARD_CHANGE_THRESHOLD) return null

        val reason =
            if (leftLifts.size >= 2 || rightLifts.size >= 2) {
                EpochSplitReason.LiftChurn
            } else {
                EpochSplitReason.PrimaryLiftChange
            }
        val boundaryWeek = weekStarts[blockEnd]
        val boundaryInstant =
            cleanSets.filter { WeeklySignalSupport.weekStartFor(it.sourceSet.completedAt) == boundaryWeek }
                .minOfOrNull { it.sourceSet.completedAt }
                ?: sessionById.values.minByOrNull { absInstantDistance(it.startedAt, boundaryWeek) }?.startedAt
                ?: boundaryWeek
        return SplitPoint(start = boundaryInstant, reason = reason, priority = 2)
    }

    private fun liftChurnSplitPoint(
        index: Int,
        weekStarts: List<Instant>,
        weeklyPrimaryLifts: SortedMap<Instant, Set<LiftId>>,
        cleanSets: List<CleanTrainingSet>,
    ): SplitPoint? {
        val previousWeeks = weekStarts.subList(index - ROLLING_PRIMARY_WINDOW_WEEKS, index)
        val currentWeeks = weekStarts.subList(index - ROLLING_PRIMARY_WINDOW_WEEKS + 1, index + 1)
        val previousLifts = previousWeeks.flatMap { weeklyPrimaryLifts[it].orEmpty() }.toSet()
        val currentLifts = currentWeeks.flatMap { weeklyPrimaryLifts[it].orEmpty() }.toSet()
        if (jaccardChange(previousLifts, currentLifts) <= PRIMARY_JACCARD_CHANGE_THRESHOLD) return null
        if (previousLifts.size < 2 && currentLifts.size < 2) return null

        val boundaryWeek = weekStarts[index]
        val boundaryInstant =
            cleanSets.filter { WeeklySignalSupport.weekStartFor(it.sourceSet.completedAt) == boundaryWeek }
                .minOfOrNull { it.sourceSet.completedAt }
                ?: boundaryWeek
        return SplitPoint(
            start = boundaryInstant,
            reason = EpochSplitReason.LiftChurn,
            priority = 3,
        )
    }

    private fun buildEpochs(
        splitPoints: List<SplitPoint>,
        windowStart: Instant,
        windowEnd: Instant,
    ): List<Epoch> {
        if (splitPoints.isEmpty()) {
            return listOf(
                singleEpoch(
                    index = 0,
                    start = windowStart,
                    end = windowEnd,
                    reason = EpochSplitReason.WindowEnd,
                ),
            )
        }

        val epochs = mutableListOf<Epoch>()
        var epochStart = windowStart
        splitPoints.forEachIndexed { index, split ->
            val epochEnd = split.start.minusMillis(1)
            if (!epochStart.isAfter(epochEnd)) {
                epochs +=
                    Epoch(
                        id = EpochId("epoch-$index"),
                        start = epochStart,
                        end = epochEnd,
                        anchorLiftIds = emptySet(),
                        splitReason = if (index == 0) EpochSplitReason.WindowEnd else splitPoints[index - 1].reason,
                    )
            }
            epochStart = split.start
        }
        epochs +=
            Epoch(
                id = EpochId("epoch-${splitPoints.size}"),
                start = epochStart,
                end = windowEnd,
                anchorLiftIds = emptySet(),
                splitReason = splitPoints.last().reason,
            )
        return epochs
    }

    private fun singleEpoch(
        index: Int,
        start: Instant,
        end: Instant,
        reason: EpochSplitReason,
    ): Epoch =
        Epoch(
            id = EpochId("epoch-$index"),
            start = start,
            end = end,
            anchorLiftIds = emptySet(),
            splitReason = reason,
        )

    private fun jaccardChange(left: Set<LiftId>, right: Set<LiftId>): Double {
        if (left.isEmpty() && right.isEmpty()) return 0.0
        val intersection = left.intersect(right).size.toDouble()
        val union = left.union(right).size.toDouble()
        return 1.0 - intersection / union
    }

    private fun absInstantDistance(first: Instant, second: Instant): Long =
        kotlin.math.abs(first.epochSecond - second.epochSecond)
}
