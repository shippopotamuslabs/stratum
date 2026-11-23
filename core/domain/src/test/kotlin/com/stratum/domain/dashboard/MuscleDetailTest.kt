package com.stratum.domain.dashboard

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.CleanTrainingSet
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MuscleDetailTest {
    private val bench = lift("bench", "Bench Press", MovementPattern.HorizontalPress, setOf(MuscleGroup.Chest))
    private val pushdown = lift("pushdown", "Pushdown", MovementPattern.ElbowExtension, setOf(MuscleGroup.Tricep))
    private val squat = lift("squat", "Squat", MovementPattern.SquatPattern, setOf(MuscleGroup.Quad))
    private val lifts = listOf(bench, pushdown, squat)

    @Test
    @DisplayName("lift rows credit primary and secondary sets over trained weeks")
    fun liftRowsCreditSetsPerWeek() {
        val sets =
            // Week 1: 3 bench + 2 pushdown. Week 2: 3 bench + 4 pushdown.
            sets("s1", bench, weekOffset = 1, count = 3, kg = 100.0) +
                sets("s1", pushdown, weekOffset = 1, count = 2, kg = 30.0) +
                sets("s2", bench, weekOffset = 0, count = 3, kg = 105.0) +
                sets("s2", pushdown, weekOffset = 0, count = 4, kg = 32.0) +
                sets("s2", squat, weekOffset = 0, count = 5, kg = 140.0)

        val detail = buildMuscleDetail(MuscleGroup.Tricep, sets, lifts, NOW)

        assertEquals(listOf("Pushdown", "Bench Press"), detail.lifts.map { it.name })
        val pushdownRow = detail.lifts.first()
        val benchRow = detail.lifts.last()
        assertEquals(3.0, pushdownRow.setsPerWeek, EPS)
        assertTrue(pushdownRow.isPrimary)
        // Bench credits triceps at half a set: 6 sets × 0.5 over 2 weeks.
        assertEquals(1.5, benchRow.setsPerWeek, EPS)
        assertTrue(!benchRow.isPrimary)
    }

    @Test
    @DisplayName("recent sessions are newest first with one top set per lift")
    fun recentSessionsNewestFirst() {
        val sets =
            sets("old", bench, weekOffset = 2, count = 2, kg = 90.0) +
                sets("mid", bench, weekOffset = 1, count = 2, kg = 95.0) +
                sets("new", bench, weekOffset = 0, count = 3, kg = 100.0, rir = 1)

        val detail = buildMuscleDetail(MuscleGroup.Chest, sets, lifts, NOW)

        assertEquals(listOf("new", "mid", "old"), detail.recentSessions.map { it.sessionId.value })
        val newest = detail.recentSessions.first()
        assertEquals(1, newest.topSets.size)
        assertEquals(100.0, newest.topSets.single().weight.amount, EPS)
        assertEquals(1, newest.topSets.single().rir)
    }

    @Test
    @DisplayName("a rising weekly e1RM marks the lift as progressing")
    fun risingLiftIsUp() {
        val sets = (0 until 6).flatMap { week ->
            sets("w$week", bench, weekOffset = 5 - week, count = 1, kg = 80.0 + week * 5.0)
        }

        val detail = buildMuscleDetail(MuscleGroup.Chest, sets, lifts, NOW)

        assertEquals(SeriesDirection.Up, detail.lifts.single().direction)
    }

    @Test
    @DisplayName("sets outside the window or for other muscles are ignored")
    fun windowAndMuscleFilter() {
        val sets =
            sets("ancient", bench, weekOffset = 9, count = 3, kg = 100.0) +
                sets("legs", squat, weekOffset = 0, count = 3, kg = 140.0)

        val detail = buildMuscleDetail(MuscleGroup.Chest, sets, lifts, NOW)

        assertTrue(detail.lifts.isEmpty())
        assertTrue(detail.recentSessions.isEmpty())
    }

    @Test
    @DisplayName("recent sessions cap at the pane limit")
    fun recentSessionsCapped() {
        val sets = (0 until 6).flatMap { index ->
            sets("s$index", bench, weekOffset = index, count = 1, kg = 100.0)
        }

        val detail = buildMuscleDetail(MuscleGroup.Chest, sets, lifts, NOW)

        assertEquals(MUSCLE_DETAIL_RECENT_SESSIONS, detail.recentSessions.size)
    }

    private fun sets(
        sessionId: String,
        lift: Lift,
        weekOffset: Int,
        count: Int,
        kg: Double,
        rir: Int? = 2,
    ): List<CleanTrainingSet> {
        val at = NOW.minus(Duration.ofDays(7L * weekOffset)).minus(Duration.ofHours(1))
        return (0 until count).map { index ->
            val set =
                LoggedSet(
                    id = LoggedSetId("$sessionId-${lift.id.value}-$index"),
                    sessionId = TrainingSessionId(sessionId),
                    exerciseNameRaw = lift.name,
                    liftId = lift.id,
                    setIndex = index,
                    weight = Weight(kg, Weight.WeightUnit.Kg),
                    reps = REPS,
                    rir = rir,
                    completedAt = at.plusSeconds(index.toLong()),
                    liftContext = LoggedLiftContext(liftName = lift.name),
                )
            CleanTrainingSet(
                sourceSet = set,
                normalizedWeight = kg,
                estimatedOneRm = EstimatedOneRm.calculate(kg, REPS),
                rirAdjustedEstimatedOneRm = null,
                isWorkingSet = true,
                qualityFlags = emptySet(),
            )
        }
    }

    private fun lift(
        id: String,
        name: String,
        pattern: MovementPattern,
        primary: Set<MuscleGroup>,
    ): Lift =
        Lift(
            id = LiftId(id),
            name = name,
            movementPatterns = setOf(pattern),
            primaryMuscles = primary,
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )

    private companion object {
        // Wednesday, so "now" sits mid-week and the window has a full current week behind it.
        val NOW: Instant = Instant.parse("2026-11-18T18:00:00Z")
        const val REPS = 8
        const val EPS = 1e-9
    }
}
