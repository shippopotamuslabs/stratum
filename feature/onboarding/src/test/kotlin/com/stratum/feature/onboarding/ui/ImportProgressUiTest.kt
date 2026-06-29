package com.stratum.feature.onboarding.ui

import com.stratum.feature.onboarding.ImportCatalogStepUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.stratum.feature.onboarding.R as FeatureR

class ImportProgressUiTest {
    @Test
    fun catalogProgressStepStringRes_mapsEveryStepToAStringResource() {
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_loading_converted,
            catalogProgressStepStringRes(ImportCatalogStepUi.LoadingConvertedData),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_merging,
            catalogProgressStepStringRes(ImportCatalogStepUi.MergingImportedFiles(fileCount = 3)),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_matching,
            catalogProgressStepStringRes(ImportCatalogStepUi.MatchingCatalog(exerciseCount = 1)),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_custom_lifts,
            catalogProgressStepStringRes(ImportCatalogStepUi.IdentifyingCustomLifts(exerciseCount = 1)),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_equipment,
            catalogProgressStepStringRes(ImportCatalogStepUi.InferringEquipment(exerciseCount = 1)),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_enabling,
            catalogProgressStepStringRes(ImportCatalogStepUi.EnablingAvailableLifts),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_saving,
            catalogProgressStepStringRes(ImportCatalogStepUi.SavingHistory),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_analyzing,
            catalogProgressStepStringRes(ImportCatalogStepUi.AnalyzingPatterns),
        )
    }
}
