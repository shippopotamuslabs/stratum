package com.stratum.feature.onboarding.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stratum.domain.importing.ImportCatalogProgressStep
import com.stratum.domain.importing.ImportProgress
import com.stratum.feature.onboarding.R as FeatureR

private val TECHNICAL_IMPORT_DETAIL_PATTERNS =
    listOf(
        Regex("(?i)traceback"),
        Regex("(?i)KeyError"),
        Regex("(?i)AttributeError"),
        Regex("(?i)TypeError"),
        Regex("(?i)ValueError"),
        Regex("sessionId,"),
        Regex("exerciseNameRaw"),
        Regex("(?i)line \\d+"),
        Regex("(?i)file \""),
        Regex("(?i)must be a JSON object"),
    )

/** Hides agent/tool stderr and schema validation text from onboarding progress subtitles. */
internal fun userFacingImportDetail(detail: String): String? =
    detail.takeIf { text -> TECHNICAL_IMPORT_DETAIL_PATTERNS.none { it.containsMatchIn(text) } }

@StringRes
internal fun catalogProgressStepStringRes(step: ImportCatalogProgressStep): Int =
    when (step) {
        ImportCatalogProgressStep.LoadingConvertedData -> FeatureR.string.gym_lifts_import_subtitle_loading_converted
        is ImportCatalogProgressStep.MergingImportedFiles -> FeatureR.string.gym_lifts_import_subtitle_merging
        is ImportCatalogProgressStep.MatchingCatalog -> FeatureR.string.gym_lifts_import_subtitle_matching
        is ImportCatalogProgressStep.IdentifyingCustomLifts -> FeatureR.string.gym_lifts_import_subtitle_custom_lifts
        is ImportCatalogProgressStep.InferringEquipment -> FeatureR.string.gym_lifts_import_subtitle_equipment
        ImportCatalogProgressStep.EnablingAvailableLifts -> FeatureR.string.gym_lifts_import_subtitle_enabling
        ImportCatalogProgressStep.SavingHistory -> FeatureR.string.gym_lifts_import_subtitle_saving
        ImportCatalogProgressStep.AnalyzingPatterns -> FeatureR.string.gym_lifts_import_subtitle_analyzing
    }

@Composable
internal fun importProgressSubtitle(progress: ImportProgress): String? {
    progress.catalogStep?.let { step ->
        return when (step) {
            ImportCatalogProgressStep.LoadingConvertedData,
            ImportCatalogProgressStep.EnablingAvailableLifts,
            ImportCatalogProgressStep.SavingHistory,
            ImportCatalogProgressStep.AnalyzingPatterns,
            ->
                stringResource(catalogProgressStepStringRes(step))
            is ImportCatalogProgressStep.MergingImportedFiles ->
                stringResource(
                    catalogProgressStepStringRes(step),
                    step.fileCount,
                )
            is ImportCatalogProgressStep.MatchingCatalog ->
                stringResource(
                    catalogProgressStepStringRes(step),
                    step.exerciseCount,
                )
            is ImportCatalogProgressStep.IdentifyingCustomLifts ->
                stringResource(
                    catalogProgressStepStringRes(step),
                    step.exerciseCount,
                )
            is ImportCatalogProgressStep.InferringEquipment ->
                stringResource(
                    catalogProgressStepStringRes(step),
                    step.exerciseCount,
                )
        }
    }
    val fileIndex = progress.fileIndex
    val fileCount = progress.fileCount
    val filePrefix =
        if (fileIndex != null && fileCount != null && fileCount > 1) {
            stringResource(
                FeatureR.string.gym_lifts_import_subtitle_file_of,
                fileIndex,
                fileCount,
            )
        } else {
            null
        }
    val detail = progress.detail?.let(::userFacingImportDetail)
    return when {
        filePrefix != null && detail != null -> "$filePrefix: $detail"
        filePrefix != null -> filePrefix
        else -> detail
    }
}
