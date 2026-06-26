package com.stratum.domain.response

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.fixtures.ResponseFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

class AnchorLiftSelectorTest {
    @Test
    @DisplayName("RP-S4-001 insufficient sessions returns null")
    fun rpS4_001_insufficientSessionsReturnsNull() {
        val input = ResponseFixtures.fixS4SparseQuad()
        val epochs =
            listOf(
                ResponseFixtures.epoch(
                    id = "sparse",
                    start = input.analysisWindowStart,
                    end = input.analysisWindowEnd,
                ),
            )
        val result = AnchorLiftSelector.select(MuscleGroup.Quad, epochs, input)

        assertNull(result)
    }

    @Test
    @DisplayName("RP-S4-002 anchor lift selected")
    fun rpS4_002_anchorLiftSelected() {
        val input = ResponseFixtures.fixS4RichChest()
        val epochs =
            listOf(
                ResponseFixtures.epoch(
                    id = "rich",
                    start = input.analysisWindowStart,
                    end = input.analysisWindowEnd,
                ),
            )
        val result = AnchorLiftSelector.select(MuscleGroup.Chest, epochs, input)

        assertEquals(ResponseFixtures.liftBench, result)
        assertNotEquals(ResponseFixtures.liftFly, result)
    }

    @Test
    @DisplayName("RP-S4-003 specialize breaks session-count tie")
    fun rpS4_003_specializeBreaksTie() {
        val tieInput = buildChestTieInput(provenLiftIds = setOf(ResponseFixtures.liftBench))
        val epochs =
            listOf(
                ResponseFixtures.epoch(
                    id = "tie",
                    start = tieInput.analysisWindowStart,
                    end = tieInput.analysisWindowEnd,
                ),
            )
        val result = AnchorLiftSelector.select(MuscleGroup.Chest, epochs, tieInput)

        assertEquals(ResponseFixtures.liftBench, result)
    }

    private fun buildChestTieInput(provenLiftIds: Set<com.stratum.domain.lift.identity.LiftId>): ResponseExtractionInput {
        val benchSessions =
            (0 until 8).map { index ->
                ResponseFixtures.fixS4RichChest().sessions.first().copy(
                    id = com.stratum.domain.importing.identity.HistoricalSessionId("tie-bench-$index"),
                    startedAt = ResponseFixtures.weekStart(2025, 1, 6).plus(index * 7L, ChronoUnit.DAYS),
                )
            }
        val flySessions =
            (0 until 8).map { index ->
                ResponseFixtures.fixS4RichChest().sessions.first().copy(
                    id = com.stratum.domain.importing.identity.HistoricalSessionId("tie-fly-$index"),
                    startedAt = ResponseFixtures.weekStart(2025, 1, 6).plus(index * 7L, ChronoUnit.DAYS),
                )
            }
        val sessions = benchSessions + flySessions
        val benchSets =
            benchSessions.map { session ->
                ResponseFixtures.fixS1Working().sets.single().copy(
                    id = com.stratum.domain.importing.identity.LoggedSetId("tie-b-${session.id.value}"),
                    sessionId = session.id,
                    liftId = ResponseFixtures.liftBench,
                    completedAt = session.startedAt,
                )
            }
        val flySets =
            flySessions.map { session ->
                ResponseFixtures.fixS1Working().sets.single().copy(
                    id = com.stratum.domain.importing.identity.LoggedSetId("tie-f-${session.id.value}"),
                    sessionId = session.id,
                    liftId = ResponseFixtures.liftFly,
                    completedAt = session.startedAt,
                )
            }
        return ResponseFixtures.input(
            sessions = sessions,
            sets = benchSets + flySets,
            lifts = listOf(ResponseFixtures.benchPressLift(), ResponseFixtures.dumbbellFlyLift()),
            provenLiftIds = provenLiftIds,
            specializeMuscles = setOf(MuscleGroup.Chest),
        )
    }
}
