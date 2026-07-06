package com.stratum.domain.importing

import com.stratum.core.testing.training.WorkoutLoggingFixtures
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImportedHistorySanitizerTest {
    @Test
    fun `drops sets with non-positive reps`() {
        val session = WorkoutLoggingFixtures.importTrainingSession(id = "s1")
        val sets =
            listOf(
                WorkoutLoggingFixtures.importLoggedSet(
                    id = "a",
                    sessionId = "s1",
                    exerciseNameRaw = "Squat",
                    liftId = null,
                ).copy(reps = 0),
                WorkoutLoggingFixtures.importLoggedSet(
                    id = "b",
                    sessionId = "s1",
                    exerciseNameRaw = "Squat",
                    liftId = null,
                ).copy(reps = 5),
            )

        val (_, sanitized) = ImportedHistorySanitizer.sanitize(listOf(session), sets)

        assertEquals(1, sanitized.size)
        assertEquals(5, sanitized.single().reps)
    }
}
