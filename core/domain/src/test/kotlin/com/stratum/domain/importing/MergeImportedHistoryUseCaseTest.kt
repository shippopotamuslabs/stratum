package com.stratum.domain.importing

import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MergeImportedHistoryUseCaseTest {
    private val subject = MergeImportedHistoryUseCase()

    @Test
    fun `single file passes through with namespaced ids`() {
        val session = WorkoutLoggingFixtures.importTrainingSession(id = "s1")
        val set =
            WorkoutLoggingFixtures.importLoggedSet(
                id = "set1",
                sessionId = "s1",
                exerciseNameRaw = "Squat",
                liftId = null,
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
        assertEquals(TrainingSessionId("f0::s1"), sessions.single().id)
        assertEquals(LoggedSetId("f0::set1"), sets.single().id)
        assertEquals(TrainingSessionId("f0::s1"), sets.single().sessionId)
    }

    @Test
    fun `colliding ids across files are namespaced independently`() {
        fun chunk(label: String, exercise: String): ImportedHistoryChunk {
            val session = WorkoutLoggingFixtures.importTrainingSession(id = "1")
            return ImportedHistoryChunk(
                sourceLabel = label,
                sessions = listOf(session),
                sets =
                    listOf(
                        WorkoutLoggingFixtures.importLoggedSet(
                            id = "1",
                            sessionId = "1",
                            exerciseNameRaw = exercise,
                            liftId = null,
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
        val session = WorkoutLoggingFixtures.importTrainingSession(id = "s1")
        val set =
            WorkoutLoggingFixtures.importLoggedSet(
                id = "set1",
                sessionId = "s1",
                exerciseNameRaw = "Squat",
                liftId = null,
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
        val completedAt = Instant.parse("2025-01-01T12:00:00Z")
        val session =
            WorkoutLoggingFixtures.importTrainingSession(
                id = "1",
                startedAt = completedAt,
            )
        val set =
            WorkoutLoggingFixtures.importLoggedSet(
                id = "1",
                sessionId = "1",
                exerciseNameRaw = "Bench Press",
                liftId = null,
                completedAt = completedAt,
            )

        val (_, sets) =
            subject(
                listOf(
                    ImportedHistoryChunk("a.csv", listOf(session), listOf(set)),
                    ImportedHistoryChunk(
                        "b.csv",
                        listOf(session.copy(id = TrainingSessionId("1"))),
                        listOf(set.copy(id = LoggedSetId("1"), setIndex = 2)),
                    ),
                ),
            )

        assertEquals(1, sets.size)
    }
}
