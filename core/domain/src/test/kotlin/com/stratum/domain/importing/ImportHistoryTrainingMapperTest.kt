package com.stratum.domain.importing

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ImportHistoryTrainingMapper (STR-168 healing)")
class ImportHistoryTrainingMapperTest {

    @Test
    @DisplayName("negative setIndex and RIR are healed instead of aborting the import")
    fun healsNegativeIndexAndRir() {
        val set =
            ImportHistoryTrainingMapper.parseSet(
                id = LoggedSetId("set-1"),
                sessionId = TrainingSessionId("session-1"),
                exerciseNameRaw = "Barbell Squat",
                setIndex = -3,
                weight = Weight(100.0, Weight.WeightUnit.Lb),
                reps = 8,
                rir = -1,
                completedAt = Instant.parse("2025-01-01T10:30:00Z"),
            )

        assertEquals(0, set.setIndex)
        assertEquals(0, set.rir)
    }

    @Test
    @DisplayName("valid imported values pass through unchanged")
    fun validValuesUnchanged() {
        val set =
            ImportHistoryTrainingMapper.parseSet(
                id = LoggedSetId("set-2"),
                sessionId = TrainingSessionId("session-1"),
                exerciseNameRaw = "Barbell Squat",
                setIndex = 2,
                weight = Weight(100.0, Weight.WeightUnit.Lb),
                reps = 8,
                rir = 3,
                completedAt = Instant.parse("2025-01-01T10:30:00Z"),
            )

        assertEquals(2, set.setIndex)
        assertEquals(3, set.rir)
    }
}
