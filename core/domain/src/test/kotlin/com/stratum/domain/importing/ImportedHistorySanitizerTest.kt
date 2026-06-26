package com.stratum.domain.importing

import com.stratum.domain.importing.identity.HistoricalSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImportedHistorySanitizerTest {
    @Test
    fun `drops sets with non-positive reps`() {
        val session =
            HistoricalSession(
                id = HistoricalSessionId("s1"),
                startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                endedAt = null,
                sourceApp = null,
            )
        val sets =
            listOf(
                LoggedSet(
                    id = LoggedSetId("a"),
                    sessionId = session.id,
                    exerciseNameRaw = "Squat",
                    liftId = null,
                    setIndex = 0,
                    weight = Weight(100.0, Weight.WeightUnit.Lb),
                    reps = 0,
                    rir = null,
                    completedAt = Instant.parse("2025-01-01T01:00:00Z"),
                ),
                LoggedSet(
                    id = LoggedSetId("b"),
                    sessionId = session.id,
                    exerciseNameRaw = "Squat",
                    liftId = null,
                    setIndex = 1,
                    weight = Weight(100.0, Weight.WeightUnit.Lb),
                    reps = 5,
                    rir = null,
                    completedAt = Instant.parse("2025-01-01T01:05:00Z"),
                ),
            )

        val (_, sanitized) = ImportedHistorySanitizer.sanitize(listOf(session), sets)

        assertEquals(1, sanitized.size)
        assertEquals(5, sanitized.single().reps)
    }
}
