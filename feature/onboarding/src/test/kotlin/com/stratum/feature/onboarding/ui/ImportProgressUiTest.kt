package com.stratum.feature.onboarding.ui

import com.stratum.domain.importing.ImportCatalogProgressStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.stratum.feature.onboarding.R as FeatureR

class ImportProgressUiTest {
    @Test
    fun catalogProgressStepStringRes_mapsEveryStepToAStringResource() {
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_loading_converted,
            catalogProgressStepStringRes(ImportCatalogProgressStep.LoadingConvertedData),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_merging,
            catalogProgressStepStringRes(ImportCatalogProgressStep.MergingImportedFiles(fileCount = 3)),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_matching,
            catalogProgressStepStringRes(ImportCatalogProgressStep.MatchingCatalog(exerciseCount = 1)),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_custom_lifts,
            catalogProgressStepStringRes(ImportCatalogProgressStep.IdentifyingCustomLifts(exerciseCount = 1)),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_equipment,
            catalogProgressStepStringRes(ImportCatalogProgressStep.InferringEquipment(exerciseCount = 1)),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_enabling,
            catalogProgressStepStringRes(ImportCatalogProgressStep.EnablingAvailableLifts),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_saving,
            catalogProgressStepStringRes(ImportCatalogProgressStep.SavingHistory),
        )
        assertEquals(
            FeatureR.string.gym_lifts_import_subtitle_analyzing,
            catalogProgressStepStringRes(ImportCatalogProgressStep.AnalyzingPatterns),
        )
    }
}
