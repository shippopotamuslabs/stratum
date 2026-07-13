package com.stratum.domain.response.modeling

import com.stratum.domain.importing.CustomLiftMusclePolicy
import com.stratum.domain.importing.ImportStubLiftGuesser
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.CleanTrainingSet
import com.stratum.domain.response.DayBucketSupport
import com.stratum.domain.response.MuscleVolumeCredit
import com.stratum.domain.response.WeeklySignalSupport
import java.time.Instant

/**
 * Per-muscle hard-set volume bucketed by UTC calendar day. Uses the same primary/secondary credit
 * rules as [MuscleWeeklyStimulusBuilder] so daily totals stay consistent with weekly stimulus when
 * summed over the same hard-set definition.
 */
object MuscleDailyVolumeBuilder {

    data class DailyVolumePoint(
        val dayStart: Instant,
        val hardSets: Double,
    )

    data class MuscleDailySeries(
        val muscle: MuscleGroup,
        val points: List<DailyVolumePoint>,
        val contributingLiftIds: Set<LiftId>,
    )

    /**
     * Aggregates working sets into sparse daily points (days with zero credit are omitted).
     * Only [CleanTrainingSet.isWorkingSet] rows with a resolved lift id are credited.
     */
    fun build(
        cleanSets: List<CleanTrainingSet>,
        lifts: List<Lift>,
    ): List<MuscleDailySeries> {
        val liftById = lifts.associateBy { it.id }
        val working = cleanSets.filter { it.isWorkingSet && it.sourceSet.liftId != null }
        if (working.isEmpty()) return emptyList()

        val byDay = WeeklySignalSupport.groupByDay(working)
        val muscleDayCredits = mutableMapOf<MuscleGroup, MutableMap<Instant, Double>>()
        val muscleLifts = mutableMapOf<MuscleGroup, MutableSet<LiftId>>()

        byDay.forEach { (dayStart, daySets) ->
            daySets.forEach { row ->
                val liftId = row.sourceSet.liftId ?: return@forEach
                val lift = liftById[liftId] ?: return@forEach
                val movementPattern = ImportStubLiftGuesser.effectiveMovementPattern(lift)
                val primaryMuscles =
                    ImportStubLiftGuesser.effectivePrimaryMuscles(lift, movementPattern)
                val secondaryMuscles =
                    CustomLiftMusclePolicy.effectiveSecondaryMuscles(
                        movementPattern = movementPattern,
                        primaryMuscles = primaryMuscles,
                        secondaryMuscles = lift.secondaryMuscles,
                    )
                val credited =
                    buildMap {
                        primaryMuscles.forEach { put(it, MuscleVolumeCredit.PRIMARY_SET_CREDIT) }
                        (secondaryMuscles - primaryMuscles).forEach {
                            put(it, MuscleVolumeCredit.SECONDARY_SET_CREDIT)
                        }
                    }
                credited.forEach { (muscle, credit) ->
                    val days = muscleDayCredits.getOrPut(muscle) { mutableMapOf() }
                    days[dayStart] = days.getOrDefault(dayStart, 0.0) + credit
                    muscleLifts.getOrPut(muscle) { mutableSetOf() }.add(liftId)
                }
            }
        }

        return muscleDayCredits.map { (muscle, dayCounts) ->
            MuscleDailySeries(
                muscle = muscle,
                points = dayCounts
                    .map { (dayStart, hardSets) -> DailyVolumePoint(dayStart, hardSets) }
                    .sortedBy { it.dayStart },
                contributingLiftIds = muscleLifts[muscle].orEmpty(),
            )
        }.sortedBy { it.muscle.name }
    }

    /**
     * Zero-fills [dayCount] consecutive UTC days ending on [asOf]'s calendar day (inclusive).
     * Oldest first. Days with no credit get `0.0`.
     */
    fun zeroFillLastDays(
        series: MuscleDailySeries,
        asOf: Instant,
        dayCount: Int = RUNNING_VOLUME_DAY_COUNT,
    ): List<DailyVolumePoint> {
        require(dayCount > 0) { "dayCount must be positive" }
        return DayBucketSupport.zeroFillDaily(
            points = series.points.map { it.dayStart to it.hardSets },
            asOf = asOf,
            dayCount = dayCount,
        ).map { (day, hardSets) -> DailyVolumePoint(dayStart = day, hardSets = hardSets) }
    }

    const val RUNNING_VOLUME_DAY_COUNT = 7
}
