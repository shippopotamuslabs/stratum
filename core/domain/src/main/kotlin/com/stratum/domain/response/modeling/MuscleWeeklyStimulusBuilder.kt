package com.stratum.domain.response.modeling

import com.stratum.domain.lift.Lift
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.CleanTrainingSet
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.IntensityProxyCalculator
import com.stratum.domain.response.MuscleVolumeCredit
import com.stratum.domain.response.TrainingSignalQualityFlag
import com.stratum.domain.response.WeeklySignalSupport
import java.time.Instant
import kotlin.math.roundToInt
import com.stratum.domain.importing.CustomLiftMusclePolicy
import com.stratum.domain.importing.ImportStubLiftGuesser

object MuscleWeeklyStimulusBuilder {
    fun build(
        epoch: Epoch,
        cleanSets: List<CleanTrainingSet>,
        lifts: List<Lift>,
        deloadWeeks: Set<Instant> = emptySet(),
    ): List<MuscleWeeklyStimulus> {
        val liftById = lifts.associateBy { it.id }
        val working = WeeklySignalSupport.workingSetsInEpoch(epoch, cleanSets)
        val byWeek = WeeklySignalSupport.groupByWeek(working)
        val muscleWeekCredits = mutableMapOf<MuscleGroup, MutableMap<Instant, Double>>()
        val muscleWeekSessions = mutableMapOf<MuscleGroup, MutableMap<Instant, MutableSet<String>>>()
        val muscleWeekRir = mutableMapOf<MuscleGroup, MutableMap<Instant, MutableList<Double>>>()
        val muscleWeekIntensity = mutableMapOf<MuscleGroup, MutableMap<Instant, MutableList<Double>>>()
        val muscleWeekLiftCredits =
            mutableMapOf<MuscleGroup, MutableMap<Instant, MutableMap<String, Double>>>()
        val muscleWeekFlags = mutableMapOf<MuscleGroup, MutableMap<Instant, MutableSet<TrainingSignalQualityFlag>>>()

        byWeek.forEach { (weekStart, weekSets) ->
            weekSets.forEach { row ->
                val lift = liftById[row.sourceSet.liftId] ?: return@forEach
                val movementPattern = com.stratum.domain.importing.ImportStubLiftGuesser.effectiveMovementPattern(lift)
                val primaryMuscles =
                    com.stratum.domain.importing.ImportStubLiftGuesser.effectivePrimaryMuscles(lift, movementPattern)
                val secondaryMuscles =
                    com.stratum.domain.importing.CustomLiftMusclePolicy.effectiveSecondaryMuscles(
                        movementPattern = movementPattern,
                        primaryMuscles = primaryMuscles,
                        secondaryMuscles = lift.secondaryMuscles,
                    )
                val creditedMuscles =
                    buildMap {
                        primaryMuscles.forEach { put(it, MuscleVolumeCredit.PRIMARY_SET_CREDIT) }
                        (secondaryMuscles - primaryMuscles).forEach { put(it, MuscleVolumeCredit.SECONDARY_SET_CREDIT) }
                    }
                val intensity = IntensityProxyCalculator.forWeek(weekSets.filter { it.sourceSet.liftId == lift.id }, lift.id)
                creditedMuscles.forEach { (muscle, credit) ->
                    val weeks = muscleWeekCredits.getOrPut(muscle) { mutableMapOf() }
                    weeks[weekStart] = weeks.getOrDefault(weekStart, 0.0) + credit
                    muscleWeekSessions.getOrPut(muscle) { mutableMapOf() }
                        .getOrPut(weekStart) { mutableSetOf() }
                        .add(row.sourceSet.sessionId.value)
                    row.sourceSet.rir?.let { rir ->
                        muscleWeekRir.getOrPut(muscle) { mutableMapOf() }
                            .getOrPut(weekStart) { mutableListOf() }
                            .add(rir.toDouble())
                    }
                    intensity?.let { proxy ->
                        muscleWeekIntensity.getOrPut(muscle) { mutableMapOf() }
                            .getOrPut(weekStart) { mutableListOf() }
                            .add(proxy * credit)
                    }
                    val liftCredits =
                        muscleWeekLiftCredits.getOrPut(muscle) { mutableMapOf() }
                            .getOrPut(weekStart) { mutableMapOf() }
                    liftCredits[lift.id.value] = liftCredits.getOrDefault(lift.id.value, 0.0) + credit
                    val flags = muscleWeekFlags.getOrPut(muscle) { mutableMapOf() }
                        .getOrPut(weekStart) { mutableSetOf() }
                    flags.addAll(row.qualityFlags)
                }
            }
        }

        return muscleWeekCredits.flatMap { (muscle, weekCounts) ->
            weekCounts.map { (weekStart, hardSets) ->
                val sessions = muscleWeekSessions[muscle]?.get(weekStart)?.size ?: 0
                val meanRir = muscleWeekRir[muscle]?.get(weekStart)?.average()
                val intensityValues = muscleWeekIntensity[muscle]?.get(weekStart).orEmpty()
                val intensityProxy =
                    if (intensityValues.isEmpty()) {
                        null
                    } else {
                        intensityValues.average()
                    }
                val compositionHash =
                    muscleWeekLiftCredits[muscle]?.get(weekStart)
                        ?.entries
                        ?.sortedBy { it.key }
                        ?.joinToString("|") { (liftId, credit) ->
                            "$liftId:${(credit * 10).roundToInt()}"
                        }
                        .orEmpty()
                MuscleWeeklyStimulus(
                    muscle = muscle,
                    weekStart = weekStart,
                    hardSets = hardSets,
                    sessions = sessions,
                    meanRir = meanRir,
                    intensityProxy = intensityProxy,
                    compositionHash = compositionHash,
                    deloadFlag = weekStart in deloadWeeks,
                    qualityFlags = muscleWeekFlags[muscle]?.get(weekStart).orEmpty(),
                )
            }
        }.sortedWith(compareBy({ it.muscle }, { it.weekStart }))
    }
}
