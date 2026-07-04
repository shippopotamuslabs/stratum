package com.stratum.domain.importing

import com.stratum.domain.importing.identity.HistoricalSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MergeImportedHistoryUseCaseTest {
    private val subject = MergeImportedHistoryUseCase()

    @Test
    fun `single file passes through with namespaced ids`() {
        val session =
            HistoricalSession(
                id = HistoricalSessionId("s1"),
                startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                endedAt = null,
                sourceApp = null,
            )
        val set =
            LoggedSet(
                id = LoggedSetId("set1"),
                sessionId = session.id,
                exerciseNameRaw = "Squat",
                liftId = null,
                setIndex = 0,
                weight = Weight(100.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = null,
                completedAt = Instant.parse("2025-01-01T01:00:00Z"),
            )

        val (sessions, sets) =
            subject(
                listOf(
                    ImportedHistoryChunk(
                        sourceLabel = "export.csv",
                        sessions = listOf(session),
                        sets = listOf(set),
                    ),
                ),
            )

        assertEquals(1, sessions.size)
        assertEquals(HistoricalSessionId("f0::s1"), sessions.single().id)
        assertEquals(LoggedSetId("f0::set1"), sets.single().id)
        assertEquals(HistoricalSessionId("f0::s1"), sets.single().sessionId)
    }

    @Test
    fun `colliding ids across files are namespaced independently`() {
        fun chunk(label: String, exercise: String): ImportedHistoryChunk {
            val session =
                HistoricalSession(
                    id = HistoricalSessionId("1"),
                    startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                    endedAt = null,
                    sourceApp = null,
                )
            return ImportedHistoryChunk(
                sourceLabel = label,
                sessions = listOf(session),
                sets =
                    listOf(
                        LoggedSet(
                            id = LoggedSetId("1"),
                            sessionId = session.id,
                            exerciseNameRaw = exercise,
                            liftId = null,
                            setIndex = 0,
                            weight = Weight(100.0, Weight.WeightUnit.Lb),
                            reps = 5,
                            rir = null,
                            completedAt = Instant.parse("2025-01-01T01:00:00Z"),
                        ),
                    ),
            )
        }

        val (sessions, sets) =
            subject(
                listOf(
                    chunk("a.csv", "Squat"),
                    chunk("b.csv", "Bench"),
                ),
            )

        assertEquals(2, sessions.size)
        assertEquals(2, sets.size)
        assertEquals(setOf("f0::1", "f1::1"), sessions.map { it.id.value }.toSet())
        assertEquals(setOf("f0::1", "f1::1"), sets.map { it.id.value }.toSet())
        assertEquals("Squat", sets.first { it.id.value == "f0::1" }.exerciseNameRaw)
        assertEquals("Bench", sets.first { it.id.value == "f1::1" }.exerciseNameRaw)
    }

    @Test
    fun `skips empty file chunks`() {
        val session =
            HistoricalSession(
                id = HistoricalSessionId("s1"),
                startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                endedAt = null,
                sourceApp = null,
            )
        val set =
            LoggedSet(
                id = LoggedSetId("set1"),
                sessionId = session.id,
                exerciseNameRaw = "Squat",
                liftId = null,
                setIndex = 0,
                weight = Weight(100.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = null,
                completedAt = Instant.parse("2025-01-01T01:00:00Z"),
            )

        val (_, sets) =
            subject(
                listOf(
                    ImportedHistoryChunk("empty.csv", emptyList(), emptyList()),
                    ImportedHistoryChunk("export.csv", listOf(session), listOf(set)),
                ),
            )

        assertEquals(1, sets.size)
    }

    @Test
    fun `fails when all chunks are empty`() {
        assertThrows(IllegalArgumentException::class.java) {
            subject(
                listOf(
                    ImportedHistoryChunk("empty.csv", emptyList(), emptyList()),
                ),
            )
        }
    }

    @Test
    fun `duplicate sets across files are deduped by workout fingerprint`() {
        fun duplicateSet(exercise: String): LoggedSet =
            LoggedSet(
                id = LoggedSetId("1"),
                sessionId = HistoricalSessionId("1"),
                exerciseNameRaw = exercise,
                liftId = null,
                setIndex = 0,
                weight = Weight(100.0, Weight.WeightUnit.Lb),
                reps = 5,
                rir = null,
                completedAt = Instant.parse("2025-01-01T12:00:00Z"),
            )

        val session =
            HistoricalSession(
                id = HistoricalSessionId("1"),
                startedAt = Instant.parse("2025-01-01T12:00:00Z"),
                endedAt = null,
                sourceApp = null,
            )
        val set = duplicateSet("Bench Press")

        val (_, sets) =
            subject(
                listOf(
                    ImportedHistoryChunk("a.csv", listOf(session), listOf(set)),
                    ImportedHistoryChunk(
                        "b.csv",
                        listOf(session.copy(id = HistoricalSessionId("1"))),
                        listOf(set.copy(id = LoggedSetId("1"), setIndex = 2)),
                    ),
                ),
            )

        assertEquals(1, sets.size)
    }
}
