package com.stratum.feature.onboarding

import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GymLiftsDraftUiTest {
    @Test
    @DisplayName("Given orphan history flag without completed analysis when progress is null then import is not complete")
    fun orphanHistoryWithoutMetadataIsNotComplete() {
        val draft =
            GymLiftsDraftUi(
                usedHistoricalImport = true,
                hasCompletedHistoricalImport = false,
            )

        assertFalse(draft.importComplete)
    }

    @Test
    @DisplayName("Given failed import progress then import is not complete even with completed analysis flag")
    fun failedProgressIsNotComplete() {
        val draft =
            GymLiftsDraftUi(
                usedHistoricalImport = true,
                hasCompletedHistoricalImport = true,
                importProgress = ImportProgress(ImportProgressPhase.Failed),
            )

        assertFalse(draft.importComplete)
    }

    @Test
    @DisplayName("Given completed analysis when progress is null then import is complete")
    fun completedAnalysisIsComplete() {
        val draft =
            GymLiftsDraftUi(
                usedHistoricalImport = true,
                hasCompletedHistoricalImport = true,
            )

        assertTrue(draft.importComplete)
    }
}
