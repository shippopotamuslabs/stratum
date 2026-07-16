package com.stratum.domain.workout.progression

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.SlotHistoryEntry
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [buildRecentSessionBlock] — the pure lift-level normalizer that feeds
 * the progression engines a capacity-descending recent-session baseline.
 */
@DisplayName("RecentSessionBlock: capacity-ordered recent-session normalizer")
class RecentSessionBlockTest {

    private val liftId = LiftId("L-1")

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun entry(
        position: Int,
        reps: Int,
        rir: Int?,
        weight: Double,
        sessionId: String,
        startedAt: String,
    ): SlotHistoryEntry =
        SlotHistoryEntry(
            session = TrainingSession(
                id = TrainingSessionId(sessionId),
                programId = ProgramId("p-1"),
                workoutId = WorkoutId("w-1"),
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                origin = TrainingSessionOrigin.IMPORT,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAt = Instant.parse(startedAt),
                endedAt = Instant.parse(startedAt.replace("T10:00", "T11:00")),
                sourceApp = null,
            ),
            loggedSet = LoggedSet(
                id = LoggedSetId("set-$sessionId-$position"),
                sessionId = TrainingSessionId(sessionId),
                exerciseNameRaw = "Squat",
                liftId = liftId,
                setIndex = position,
                weight = lb(weight),
                reps = reps,
                rir = rir,
                completedAt = Instant.parse(startedAt),
                liftContext = LoggedLiftContext(
                    liftName = "Squat",
                    movementPatternsBitmask = 1,
                    primaryMusclesBitmask = 1,
                    secondaryMusclesBitmask = 0,
                    fatigueProfile = FatigueProfile.Low,
                    muscleLengthBias = MuscleLengthBias.MidRange,
                ),
            ),
        )

    @Nested
    @DisplayName("cold start / empty inputs")
    inner class ColdStart {
        @Test
        @DisplayName("RSB-empty-map-returns-null")
        fun `RSM-empty-map-returns-null`() {
            assertNull(buildRecentSessionBlock(emptyMap()))
        }

        @Test
        @DisplayName("RSM-all-empty-histories-returns-null")
        fun `RSM-all-empty-histories-returns-null`() {
            assertNull(
                buildRecentSessionBlock(
                    mapOf(
                        0 to emptyList(),
                        1 to emptyList(),
                    ),
                ),
            )
        }
    }

    @Nested
    @DisplayName("uniform weight — RIR-normalized failure capacity ranking")
    inner class UniformWeight {
        @Test
        @DisplayName("RSM-reps-going-up-reordered-to-descending")
        fun `RSM-reps-going-up-reordered-to-descending`() {
            // The exact edge case from the spec: 240×12 RIR 1, 240×13 RIR 1, 240×15 RIR 0.
            // All same weight → capacity = reps + rir: 13, 14, 15. Descending → 15, 14, 13.
            val perSlot = mapOf(
                0 to listOf(entry(0, reps = 12, rir = 1, weight = 240.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
                1 to listOf(entry(1, reps = 13, rir = 1, weight = 240.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
                2 to listOf(entry(2, reps = 15, rir = 0, weight = 240.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
            )
            val block = buildRecentSessionBlock(perSlot)
            assertNotNull(block)
            val positions = block!!.performances.map { it.slotPosition }
            assertEquals(listOf(2, 1, 0), positions, "highest-capacity set first, lowest last")
            // positionToEntry preserves the positional actuals for UI display
            assertEquals(12, block.positionToEntry.getValue(0).loggedSet.reps)
            assertEquals(13, block.positionToEntry.getValue(1).loggedSet.reps)
            assertEquals(15, block.positionToEntry.getValue(2).loggedSet.reps)
        }

        @Test
        @DisplayName("RSM-already-descending-stays-put")
        fun `RSM-already-descending-stays-put`() {
            // 240×15 RIR 0, 240×13 RIR 1, 240×12 RIR 1 → capacities 15, 14, 13 — already descending.
            val perSlot = mapOf(
                0 to listOf(entry(0, reps = 15, rir = 0, weight = 240.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
                1 to listOf(entry(1, reps = 13, rir = 1, weight = 240.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
                2 to listOf(entry(2, reps = 12, rir = 1, weight = 240.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
            )
            val block = buildRecentSessionBlock(perSlot)
            val positions = block!!.performances.map { it.slotPosition }
            assertEquals(listOf(0, 1, 2), positions, "already-descending order preserved")
        }

        @Test
        @DisplayName("RSM-missing-rir-treated-as-zero-capacity")
        fun `RSM-missing-rir-treated-as-zero-capacity`() {
            // 240×12 RIR null → capacity 12; 240×15 RIR 0 → capacity 15. Descending → 15, 12.
            val perSlot = mapOf(
                0 to listOf(entry(0, reps = 12, rir = null, weight = 240.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
                1 to listOf(entry(1, reps = 15, rir = 0, weight = 240.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
            )
            val block = buildRecentSessionBlock(perSlot)
            val positions = block!!.performances.map { it.slotPosition }
            assertEquals(listOf(1, 0), positions)
        }

        @Test
        @DisplayName("RSM-ties-broken-by-position-ascending")
        fun `RSM-ties-broken-by-position-ascending`() {
            // Two sets with identical capacity (240×10 RIR 2 → 12; 240×12 RIR 0 → 12).
            // Tie → earlier slot position wins the top of the descending order.
            val perSlot = mapOf(
                0 to listOf(entry(0, reps = 10, rir = 2, weight = 240.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
                1 to listOf(entry(1, reps = 12, rir = 0, weight = 240.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
            )
            val block = buildRecentSessionBlock(perSlot)
            val positions = block!!.performances.map { it.slotPosition }
            assertEquals(listOf(0, 1), positions, "ties broken by position ascending")
        }
    }

    @Nested
    @DisplayName("mixed weight — e1RM ranking")
    inner class MixedWeight {
        @Test
        @DisplayName("RSM-mixed-weight-uses-e1rm-for-ranking")
        fun `RSM-mixed-weight-uses-e1rm-for-ranking`() {
            // 300×5 RIR 0 (e1RM ~347) outranks 200×12 RIR 0 (e1RM ~295) — the heavier
            // lower-rep set has a higher e1RM despite fewer reps. Mixed weights → e1RM
            // is the capacity measure, so slot 0 wins.
            val perSlot = mapOf(
                0 to listOf(entry(0, reps = 5, rir = 0, weight = 300.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
                1 to listOf(entry(1, reps = 12, rir = 0, weight = 200.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
            )
            val block = buildRecentSessionBlock(perSlot)
            val positions = block!!.performances.map { it.slotPosition }
            assertEquals(listOf(0, 1), positions, "heavier lower-rep set with higher e1RM outranks lighter higher-rep set")
        }
    }

    @Nested
    @DisplayName("recency — most-recent session wins")
    inner class Recency {
        @Test
        @DisplayName("RSM-five-to-three-to-five-uses-most-recent-three")
        fun `RSM-five-to-three-to-five-uses-most-recent-three`() {
            // Current program has 5 slots. Most-recent session (n-1) logged 3 sets; the
            // older session (n-2) logged 5. The block must use n-1's 3 sets only.
            val recentStartedAt = "2026-07-01T10:00:00Z"
            val olderStartedAt = "2026-06-01T10:00:00Z"
            val perSlot = mapOf(
                0 to listOf(
                    entry(0, reps = 12, rir = 1, weight = 240.0, sessionId = "s-n1", startedAt = recentStartedAt),
                    entry(0, reps = 10, rir = 2, weight = 235.0, sessionId = "s-n2", startedAt = olderStartedAt),
                ),
                1 to listOf(
                    entry(1, reps = 13, rir = 1, weight = 240.0, sessionId = "s-n1", startedAt = recentStartedAt),
                    entry(1, reps = 11, rir = 2, weight = 235.0, sessionId = "s-n2", startedAt = olderStartedAt),
                ),
                2 to listOf(
                    entry(2, reps = 15, rir = 0, weight = 240.0, sessionId = "s-n1", startedAt = recentStartedAt),
                    entry(2, reps = 12, rir = 1, weight = 235.0, sessionId = "s-n2", startedAt = olderStartedAt),
                ),
                3 to listOf(
                    entry(3, reps = 11, rir = 1, weight = 235.0, sessionId = "s-n2", startedAt = olderStartedAt),
                ),
                4 to listOf(
                    entry(4, reps = 10, rir = 2, weight = 235.0, sessionId = "s-n2", startedAt = olderStartedAt),
                ),
            )
            val block = buildRecentSessionBlock(perSlot)
            assertNotNull(block)
            // Only 3 performances (the recent session's 3 sets) — slots 3/4 fall off the block.
            assertEquals(3, block!!.performances.size)
            // All entries come from the recent session (weight 240.0), not the older one (235.0).
            assertTrue(block.performances.all { it.entry.loggedSet.weight.amount == 240.0 })
            // positionToEntry only contains the recent session's positions
            assertEquals(setOf(0, 1, 2), block.positionToEntry.keys)
        }

        @Test
        @DisplayName("RSM-single-set-recent-session-builds-one-entry-block")
        fun `RSM-single-set-recent-session-builds-one-entry-block`() {
            val perSlot = mapOf(
                0 to listOf(entry(0, reps = 10, rir = 2, weight = 100.0, sessionId = "s-n1", startedAt = "2026-07-01T10:00:00Z")),
            )
            val block = buildRecentSessionBlock(perSlot)
            assertEquals(1, block!!.performances.size)
            assertEquals(0, block.performances.first().slotPosition)
        }
    }
}