package com.stratum.domain.importing

import com.stratum.domain.importing.ImportHistoryTrainingMapper
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImportWindowPolicyTest {
    @Test
    fun `recentSetsForLiftEnablement keeps last six months from latest set`() {
        val old =
            loggedSet(
                id = "old",
                completedAt = Instant.parse("2020-01-01T00:00:00Z"),
            )
        val recent =
            loggedSet(
                id = "recent",
                completedAt = Instant.parse("2025-06-01T00:00:00Z"),
            )

        val recentSets = ImportWindowPolicy.recentSetsForLiftEnablement(listOf(old, recent))

        assertEquals(listOf(recent), recentSets)
    }

    @Test
    fun `liftEnablementCutoff is null for empty history`() {
        assertEquals(null, ImportWindowPolicy.liftEnablementCutoff(emptyList()))
    }

    private fun loggedSet(id: String, completedAt: Instant): LoggedSet =
        ImportHistoryTrainingMapper.parseSet(
            id = LoggedSetId(id),
            sessionId = TrainingSessionId("s1"),
            exerciseNameRaw = "Squat",
            setIndex = 0,
            weight = Weight(100.0, Weight.WeightUnit.Lb),
            reps = 5,
            rir = null,
            completedAt = completedAt,
        )
}
