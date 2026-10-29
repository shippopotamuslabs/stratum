package com.stratum.feature.onboarding

import com.stratum.domain.importing.HistoricalImportResumeState
import com.stratum.domain.importing.ImportCatalogProgressStep
import com.stratum.domain.importing.ImportFileValidationResult
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.application.importing.RefreshPersistedImportStateResult

internal fun ImportProgress.toUi(): ImportProgressUi = ImportProgressUi(
    phase = phase.toUi(),
    detail = detail,
    catalogStep = catalogStep?.toUi(),
    fileIndex = fileIndex,
    fileCount = fileCount,
)

private fun ImportProgressPhase.toUi(): ImportProgressPhaseUi = when (this) {
    ImportProgressPhase.Uploading -> ImportProgressPhaseUi.Uploading
    ImportProgressPhase.ReadingData -> ImportProgressPhaseUi.ReadingData
    ImportProgressPhase.CreatingConverter -> ImportProgressPhaseUi.CreatingConverter
    ImportProgressPhase.ValidatingSample -> ImportProgressPhaseUi.ValidatingSample
    ImportProgressPhase.ConvertingFull -> ImportProgressPhaseUi.ConvertingFull
    ImportProgressPhase.EnrichingCustomLifts -> ImportProgressPhaseUi.EnrichingCustomLifts
    ImportProgressPhase.ExtractingInsights -> ImportProgressPhaseUi.ExtractingInsights
    ImportProgressPhase.Complete -> ImportProgressPhaseUi.Complete
    ImportProgressPhase.Failed -> ImportProgressPhaseUi.Failed
}

private fun ImportCatalogProgressStep.toUi(): ImportCatalogStepUi = when (this) {
    ImportCatalogProgressStep.LoadingConvertedData -> ImportCatalogStepUi.LoadingConvertedData
    is ImportCatalogProgressStep.MergingImportedFiles ->
        ImportCatalogStepUi.MergingImportedFiles(fileCount)
    is ImportCatalogProgressStep.MatchingCatalog ->
        ImportCatalogStepUi.MatchingCatalog(exerciseCount)
    is ImportCatalogProgressStep.IdentifyingCustomLifts ->
        ImportCatalogStepUi.IdentifyingCustomLifts(exerciseCount)
    is ImportCatalogProgressStep.InferringEquipment ->
        ImportCatalogStepUi.InferringEquipment(exerciseCount)
    ImportCatalogProgressStep.EnablingAvailableLifts -> ImportCatalogStepUi.EnablingAvailableLifts
    ImportCatalogProgressStep.SavingHistory -> ImportCatalogStepUi.SavingHistory
    ImportCatalogProgressStep.AnalyzingPatterns -> ImportCatalogStepUi.AnalyzingPatterns
}

internal fun ImportFileValidationResult.Invalid.toUserMessage(): String = when (this) {
    ImportFileValidationResult.Invalid.NoFiles -> "No files selected."
    is ImportFileValidationResult.Invalid.TooManyFiles ->
        "Select at most $max files."
    is ImportFileValidationResult.Invalid.FileTooLarge ->
        "$fileName exceeds the $maxMb MB limit."
}

internal data class ImportResumeScalars(
    val fileIndex: Int?,
    val fileCount: Int?,
    val failedFileName: String?,
)

internal fun HistoricalImportResumeState?.toResumeScalars(): ImportResumeScalars = ImportResumeScalars(
    fileIndex = this?.nextFileIndex?.plus(1),
    fileCount = this?.totalFiles,
    failedFileName = this?.failedFileName,
)

internal fun RefreshPersistedImportStateResult.toEphemeralImportFields(): EphemeralImportRefresh =
    EphemeralImportRefresh(
        hasCompletedHistoricalImport = hasCompletedHistoricalImport,
        importInsightsSummary = importInsightsSummary?.toImportInsightsSummaryUi(),
    )

internal data class EphemeralImportRefresh(
    val hasCompletedHistoricalImport: Boolean,
    val importInsightsSummary: ImportInsightsSummaryUi?,
)
