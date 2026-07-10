package com.stratum.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HistoricalImportTimelineStepTest {

    @Test
    @DisplayName("Maps domain phases onto coarse timeline indices")
    fun mapsPhasesToIndices() {
        assertEquals(0, ImportProgressPhaseUi.Uploading.historicalImportTimelineIndex())
        assertEquals(1, ImportProgressPhaseUi.ReadingData.historicalImportTimelineIndex())
        assertEquals(1, ImportProgressPhaseUi.CreatingConverter.historicalImportTimelineIndex())
        assertEquals(1, ImportProgressPhaseUi.ValidatingSample.historicalImportTimelineIndex())
        assertEquals(1, ImportProgressPhaseUi.ConvertingFull.historicalImportTimelineIndex())
        assertEquals(2, ImportProgressPhaseUi.EnrichingCustomLifts.historicalImportTimelineIndex())
        assertEquals(3, ImportProgressPhaseUi.ExtractingInsights.historicalImportTimelineIndex())
        assertEquals(
            historicalImportTimelineSteps.size,
            ImportProgressPhaseUi.Complete.historicalImportTimelineIndex(),
        )
    }
}
