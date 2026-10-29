package com.stratum.application.response.modeling

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.CleanTrainingSet
import com.stratum.domain.response.MuscleVolumeCredit
import com.stratum.domain.response.TrainingSignalQualityFlag
import com.stratum.domain.response.WeeklySignalSupport
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT
import com.stratum.domain.response.modeling.MuscleDailyVolumeBuilder

class MuscleDailyVolumeBuilderTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val newYork: ZoneId = ZoneId.of("America/New_York")

    @Test
    @DisplayName("primary muscle gets full set credit; secondary half")
    fun primaryAndSecondaryCredit() {
        val day = Instant.parse("2025-06-10T15:00:00Z")
        val clean = listOf(
            cleanSet(
                liftId = BENCH,
                completedAt = day,
                isWorkingSet = true,
            ),
        )
        val series = MuscleDailyVolumeBuilder.build(clean, listOf(benchLift()), zone = utc)
        val chest = series.single { it.muscle == MuscleGroup.Chest }
        val tricep = series.single { it.muscle == MuscleGroup.Tricep }

        assertEquals(1.0, chest.points.single().hardSets, 0.001)
        assertEquals(MuscleVolumeCredit.SECONDARY_SET_CREDIT, tricep.points.single().hardSets, 0.001)
        assertEquals(setOf(BENCH), chest.contributingLiftIds)
    }

    @Test
    @DisplayName("warmup (non-working) sets are excluded")
    fun warmupExcluded() {
        val day = Instant.parse("2025-06-10T15:00:00Z")
        val clean = listOf(
            cleanSet(liftId = BENCH, completedAt = day, isWorkingSet = false),
            cleanSet(liftId = BENCH, completedAt = day, isWorkingSet = true, setId = "hard"),
        )
        val series = MuscleDailyVolumeBuilder.build(clean, listOf(benchLift()), zone = utc)
        val chest = series.single { it.muscle == MuscleGroup.Chest }
        assertEquals(1.0, chest.points.single().hardSets, 0.001)
    }

    @Test
    @DisplayName("sets on different UTC days land in different buckets")
    fun dayBucketsUtc() {
        val day0 = Instant.parse("2025-06-10T23:00:00Z")
        val day1 = Instant.parse("2025-06-11T01:00:00Z")
        val clean = listOf(
            cleanSet(liftId = BENCH, completedAt = day0, setId = "s0"),
            cleanSet(liftId = BENCH, completedAt = day1, setId = "s1"),
        )
        val series = MuscleDailyVolumeBuilder.build(clean, listOf(benchLift()), zone = utc)
        val chest = series.single { it.muscle == MuscleGroup.Chest }
        assertEquals(2, chest.points.size)
        assertEquals(
            WeeklySignalSupport.dayStartFor(day0, utc),
            chest.points[0].dayStart,
        )
        assertEquals(
            WeeklySignalSupport.dayStartFor(day1, utc),
            chest.points[1].dayStart,
        )
        assertEquals(1.0, chest.points[0].hardSets, 0.001)
        assertEquals(1.0, chest.points[1].hardSets, 0.001)
    }

    @Test
    @DisplayName("sets around UTC midnight stay on the same local day in America/New_York")
    fun dayBucketsLocalNewYork() {
        // Wed 2026-07-22 20:00 EDT = Thu 00:00 UTC — still Wednesday locally.
        val wedEvening = Instant.parse("2026-07-23T00:00:00Z")
        // Thu 2026-07-23 01:00 EDT = Thu 05:00 UTC — Thursday locally.
        val thuMorning = Instant.parse("2026-07-23T05:00:00Z")
        val clean = listOf(
            cleanSet(liftId = BENCH, completedAt = wedEvening, setId = "s0"),
            cleanSet(liftId = BENCH, completedAt = thuMorning, setId = "s1"),
        )
        val series = MuscleDailyVolumeBuilder.build(clean, listOf(benchLift()), zone = newYork)
        val chest = series.single { it.muscle == MuscleGroup.Chest }
        assertEquals(2, chest.points.size)
        assertEquals(
            WeeklySignalSupport.dayStartFor(wedEvening, newYork),
            chest.points[0].dayStart,
        )
        assertEquals(
            WeeklySignalSupport.dayStartFor(thuMorning, newYork),
            chest.points[1].dayStart,
        )
        assertEquals(
            java.time.LocalDate.of(2026, 7, 22),
            chest.points[0].dayStart.atZone(newYork).toLocalDate(),
        )
        assertEquals(
            java.time.LocalDate.of(2026, 7, 23),
            chest.points[1].dayStart.atZone(newYork).toLocalDate(),
        )
    }

    @Test
    @DisplayName("zeroFillLastDays emits 7 points with rest days at zero")
    fun zeroFill() {
        val asOf = Instant.parse("2025-06-12T18:00:00Z")
        val activeDay = Instant.parse("2025-06-11T12:00:00Z")
        val sparse = MuscleDailyVolumeBuilder.MuscleDailySeries(
            muscle = MuscleGroup.Chest,
            points = listOf(
                MuscleDailyVolumeBuilder.DailyVolumePoint(
                    dayStart = WeeklySignalSupport.dayStartFor(activeDay, utc),
                    hardSets = 3.0,
                ),
            ),
            contributingLiftIds = setOf(BENCH),
        )
        val filled = MuscleDailyVolumeBuilder.zeroFillLastDays(
            sparse,
            asOf,
            dayCount = 7,
            zone = utc,
        )
        assertEquals(7, filled.size)
        assertEquals(3.0, filled[5].hardSets, 0.001, "day-before-asOf is index 5 when asOf is day 6")
        assertEquals(0.0, filled[0].hardSets, 0.001)
        assertEquals(0.0, filled[6].hardSets, 0.001, "asOf day has no credit")
        assertTrue(filled.zipWithNext().all { (a, b) -> a.dayStart.isBefore(b.dayStart) })
    }

    @Test
    @DisplayName("zeroFillLastDays ends on local calendar day not UTC day after evening UTC rollover")
    fun zeroFillUsesLocalDayInNewYork() {
        // Wed 2026-07-22 20:30 EDT — already Thursday in UTC.
        val asOf = Instant.parse("2026-07-23T00:30:00Z")
        val sparse = MuscleDailyVolumeBuilder.MuscleDailySeries(
            muscle = MuscleGroup.Chest,
            points = emptyList(),
            contributingLiftIds = emptySet(),
        )
        val filled = MuscleDailyVolumeBuilder.zeroFillLastDays(
            sparse,
            asOf,
            dayCount = 7,
            zone = newYork,
        )
        assertEquals(7, filled.size)
        assertEquals(
            java.time.LocalDate.of(2026, 7, 22),
            filled.last().dayStart.atZone(newYork).toLocalDate(),
            "last bar must still be Wednesday locally",
        )
        assertEquals(
            java.time.LocalDate.of(2026, 7, 16),
            filled.first().dayStart.atZone(newYork).toLocalDate(),
        )
    }

    private fun cleanSet(
        liftId: LiftId,
        completedAt: Instant,
        isWorkingSet: Boolean = true,
        setId: String = "set-1",
    ): CleanTrainingSet =
        CleanTrainingSet(
            sourceSet = LoggedSet(
                id = LoggedSetId(setId),
                sessionId = TrainingSessionId("sess-1"),
                exerciseNameRaw = "Bench",
                liftId = liftId,
                setIndex = 0,
                weight = Weight(100.0, Weight.WeightUnit.Kg),
                reps = 8,
                rir = 2,
                completedAt = completedAt,
                liftContext = LoggedLiftContext(liftId.value),
            ),
            normalizedWeight = 100.0,
            estimatedOneRm = 120.0,
            rirAdjustedEstimatedOneRm = 130.0,
            isWorkingSet = isWorkingSet,
            qualityFlags = emptySet<TrainingSignalQualityFlag>(),
        )

    private fun benchLift(): Lift = Lift(
        id = BENCH,
        name = "Bench Press",
        movementPatterns = setOf(MovementPattern.HorizontalPress),
        primaryMuscles = setOf(MuscleGroup.Chest),
        secondaryMuscles = setOf(MuscleGroup.Tricep),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )

    private companion object {
        val BENCH = LiftId("bench")
    }
}
