package com.stratum.domain.importing

import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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

        val merged =
            subject(
                listOf(
                    ImportedHistoryChunk(
                        sourceLabel = "export.csv",
                        sessions = listOf(session),
                        sets = listOf(set),
                    ),
                ),
            )

        assertEquals(1, merged.sessions.size)
        assertEquals(TrainingSessionId("f0::s1"), merged.sessions.single().id)
        assertEquals(LoggedSetId("f0::set1"), merged.sets.single().id)
        assertEquals(TrainingSessionId("f0::s1"), merged.sets.single().sessionId)
        assertTrue(merged.stableIdSessionIds.isEmpty())
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

        val merged =
            subject(
                listOf(
                    chunk("a.csv", "Squat"),
                    chunk("b.csv", "Bench"),
                ),
            )

        assertEquals(2, merged.sessions.size)
        assertEquals(2, merged.sets.size)
        assertEquals(setOf("f0::1", "f1::1"), merged.sessions.map { it.id.value }.toSet())
        assertEquals(setOf("f0::1", "f1::1"), merged.sets.map { it.id.value }.toSet())
        assertEquals("Squat", merged.sets.first { it.id.value == "f0::1" }.exerciseNameRaw)
        assertEquals("Bench", merged.sets.first { it.id.value == "f1::1" }.exerciseNameRaw)
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

        val merged =
            subject(
                listOf(
                    ImportedHistoryChunk("empty.csv", emptyList(), emptyList()),
                    ImportedHistoryChunk("export.csv", listOf(session), listOf(set)),
                ),
            )

        assertEquals(1, merged.sets.size)
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

        val merged =
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

        assertEquals(1, merged.sets.size)
    }

    @Test
    fun `preserveStableIds sessions are reported in stableIdSessionIds`() {
        val stratumSession = WorkoutLoggingFixtures.importTrainingSession(id = "stratum-1")
        val stratumSet =
            WorkoutLoggingFixtures.importLoggedSet(
                id = "stratum-set-1",
                sessionId = "stratum-1",
                exerciseNameRaw = "Squat",
                liftId = null,
            )
        val thirdSession = WorkoutLoggingFixtures.importTrainingSession(id = "tp-1")
        val thirdSet =
            WorkoutLoggingFixtures.importLoggedSet(
                id = "tp-set-1",
                sessionId = "tp-1",
                exerciseNameRaw = "Bench",
                liftId = null,
            )

        val merged =
            subject(
                listOf(
                    ImportedHistoryChunk(
                        sourceLabel = "stratum.json",
                        sessions = listOf(stratumSession),
                        sets = listOf(stratumSet),
                        preserveStableIds = true,
                    ),
                    ImportedHistoryChunk(
                        sourceLabel = "hevy.csv",
                        sessions = listOf(thirdSession),
                        sets = listOf(thirdSet),
                        preserveStableIds = false,
                    ),
                ),
            )

        assertEquals(setOf("stratum-1"), merged.stableIdSessionIds)
        assertEquals(setOf("stratum-1", "f1::tp-1"), merged.sessions.map { it.id.value }.toSet())
    }
}
