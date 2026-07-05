package com.stratum.domain.response

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup

object StimulusSeriesBuilder {
    fun weeklyHardSetsByMuscle(
        epoch: Epoch,
        cleanSets: List<CleanTrainingSet>,
        lifts: List<Lift>,
    ): Map<MuscleGroup, List<WeeklyTrainingSignal>> {
        val liftById = lifts.associateBy { it.id }
        val working = WeeklySignalSupport.workingSetsInEpoch(epoch, cleanSets)
        val byWeek = WeeklySignalSupport.groupByWeek(working)
        val muscleWeekCredits = mutableMapOf<MuscleGroup, MutableMap<java.time.Instant, Double>>()

        byWeek.forEach { (weekStart, weekSets) ->
            weekSets.forEach { row ->
                val lift = liftById[row.sourceSet.liftId] ?: return@forEach
                MuscleVolumeCredit.accumulateSetCredits(lift, weekStart, muscleWeekCredits)
            }
        }

        return muscleWeekCredits.mapValues { (_, weekCounts) ->
            weekCounts.map { (weekStart, hardSets) ->
                WeeklyTrainingSignal(
                    weekStart = weekStart,
                    liftId = null,
                    muscle = null,
                    hardSets = hardSets,
                    sessions = 0,
                    meanRir = null,
                    intensityProxy = null,
                    peakEstimatedOneRm = null,
                    smoothedEstimatedOneRm = null,
                    qualityFlags = emptySet(),
                )
            }.sortedBy { it.weekStart }
        }
    }

    fun weeklyForLift(
        epoch: Epoch,
        liftId: LiftId,
        cleanSets: List<CleanTrainingSet>,
    ): List<WeeklyTrainingSignal> {
        val working = WeeklySignalSupport.workingSetsInEpoch(epoch, cleanSets, liftId)
        return WeeklySignalSupport.groupByWeek(working).map { (weekStart, weekSets) ->
            WeeklyTrainingSignal(
                weekStart = weekStart,
                liftId = liftId,
                muscle = null,
                hardSets = weekSets.size.toDouble(),
                sessions = weekSets.map { it.sourceSet.sessionId }.distinct().size,
                meanRir = weekSets.mapNotNull { it.sourceSet.rir }.average().takeIf { weekSets.any { s -> s.sourceSet.rir != null } },
                intensityProxy = IntensityProxyCalculator.forWeek(weekSets, liftId),
                peakEstimatedOneRm = weekSets.maxOf { it.estimatedOneRm },
                smoothedEstimatedOneRm = weekSets.maxOf { it.estimatedOneRm },
                qualityFlags = weekSets.flatMap { it.qualityFlags }.toSet(),
            )
        }.sortedBy { it.weekStart }
    }

    fun weeklyHardSetsFromSnapshot(
        epoch: Epoch,
        sets: List<com.stratum.domain.training.LoggedSet>,
    ): Map<MuscleGroup, Double> {
        val muscleWeekCredits = mutableMapOf<MuscleGroup, MutableMap<java.time.Instant, Double>>()
        val windowSets = sets.filter { it.completedAt in epoch.start..epoch.end }
        windowSets.forEach { set ->
            if (
                set.liftContext.movementPatternsBitmask == 0 &&
                set.liftContext.primaryMusclesBitmask == 0 &&
                set.liftContext.secondaryMusclesBitmask == 0
            ) {
                return@forEach
            }
            val weekStart = WeeklySignalSupport.weekStartFor(set.completedAt)
            MuscleVolumeCredit.accumulateSetCredits(set.liftContext, weekStart, muscleWeekCredits)
        }
        return muscleWeekCredits.mapValues { (_, weekCounts) -> weekCounts.values.sum() }
    }
}
