package com.stratum.feature.onboarding

/**
 * Presentation models for historical import progress. Mirrors domain [ImportProgressPhase] and
 * [ImportCatalogProgressStep] without leaking domain types into composables.
 */
enum class ImportProgressPhaseUi {
    Uploading,
    ReadingData,
    CreatingConverter,
    ValidatingSample,
    ConvertingFull,
    EnrichingCustomLifts,
    ExtractingInsights,
    Complete,
    Failed,
}

sealed class ImportCatalogStepUi {
    data object LoadingConvertedData : ImportCatalogStepUi()

    data class MergingImportedFiles(val fileCount: Int) : ImportCatalogStepUi()

    data class MatchingCatalog(val exerciseCount: Int) : ImportCatalogStepUi()

    data class IdentifyingCustomLifts(val exerciseCount: Int) : ImportCatalogStepUi()

    data class InferringEquipment(val exerciseCount: Int) : ImportCatalogStepUi()

    data object EnablingAvailableLifts : ImportCatalogStepUi()

    data object SavingHistory : ImportCatalogStepUi()

    data object AnalyzingPatterns : ImportCatalogStepUi()
}

data class ImportProgressUi(
    val phase: ImportProgressPhaseUi,
    val detail: String? = null,
    val catalogStep: ImportCatalogStepUi? = null,
    val fileIndex: Int? = null,
    val fileCount: Int? = null,
)
