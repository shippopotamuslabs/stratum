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
        val muscleWeekCounts = mutableMapOf<MuscleGroup, MutableMap<java.time.Instant, Int>>()

        byWeek.forEach { (weekStart, weekSets) ->
            weekSets.forEach { row ->
                val lift = liftById[row.sourceSet.liftId] ?: return@forEach
                lift.primaryMuscles.forEach { muscle ->
                    val weeks = muscleWeekCounts.getOrPut(muscle) { mutableMapOf() }
                    weeks[weekStart] = weeks.getOrDefault(weekStart, 0) + 1
                }
            }
        }

        return muscleWeekCounts.mapValues { (_, weekCounts) ->
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
                hardSets = weekSets.size,
                sessions = weekSets.map { it.sourceSet.sessionId }.distinct().size,
                meanRir = weekSets.mapNotNull { it.sourceSet.rir }.average().takeIf { weekSets.any { s -> s.sourceSet.rir != null } },
                intensityProxy = IntensityProxyCalculator.forWeek(weekSets, liftId),
                peakEstimatedOneRm = weekSets.maxOf { it.estimatedOneRm },
                smoothedEstimatedOneRm = weekSets.maxOf { it.estimatedOneRm },
                qualityFlags = weekSets.flatMap { it.qualityFlags }.toSet(),
            )
        }.sortedBy { it.weekStart }
    }
}
