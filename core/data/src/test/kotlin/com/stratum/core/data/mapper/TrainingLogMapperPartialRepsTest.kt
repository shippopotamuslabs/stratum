package com.stratum.core.data.mapper

import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.training.identity.TrainingSessionId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TrainingLogMapper partialReps")
class TrainingLogMapperPartialRepsTest {

    @Test
    @DisplayName("entity round-trip preserves partialReps")
    fun roundTripPreservesPartialReps() {
        val domain =
            WorkoutLoggingFixtures.loggedSet(
                id = "set-1",
                sessionId = TrainingSessionId("ts-1"),
            ).copy(partialReps = 2)
        val entity = domain.toEntity(nowMillis = 1_700_000_000_000L)
        assertEquals(2, entity.partialReps)
        assertEquals(2, entity.toTrainingDomain().partialReps)
    }

    @Test
    @DisplayName("default fixtures have no partials")
    fun fixturesDefaultNull() {
        val domain =
            WorkoutLoggingFixtures.loggedSet(
                id = "set-1",
                sessionId = TrainingSessionId("ts-1"),
            )
        assertNull(domain.partialReps)
        assertNull(domain.toEntity(nowMillis = 1L).partialReps)
    }
}
