package com.stratum.domain.export

import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.export.ExportTestFixtures.CLOCK_INSTANT
import com.stratum.domain.export.ExportTestFixtures.ZONE_NY
import com.stratum.domain.export.ExportTestFixtures.sessionA
import com.stratum.domain.export.ExportTestFixtures.sessionOld
import com.stratum.domain.export.ExportTestFixtures.setA1
import com.stratum.domain.export.ExportTestFixtures.setOld1
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PreviewExportTrainingLogsUseCaseTest {

    @Test
    @DisplayName("IE-S1-010 Preview counts match range contents")
    fun ieS1_010_previewCounts() = runTest {
        val repo = FakeTrainingLogRepository(
            initialSessions = listOf(sessionA, sessionOld),
            initialSets = listOf(setA1, setOld1),
        )
        val useCase =
            PreviewExportTrainingLogsUseCase(
                trainingLogRepository = repo,
                clock = { CLOCK_INSTANT },
            )
        val preview = useCase(ExportLogRange.LastDays(30, ZONE_NY))
        assertEquals(1, preview.sessionCount)
        assertEquals(1, preview.setCount)
    }
}
