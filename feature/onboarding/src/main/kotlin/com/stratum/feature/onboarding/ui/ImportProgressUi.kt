package com.stratum.feature.onboarding.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stratum.feature.onboarding.ImportCatalogStepUi
import com.stratum.feature.onboarding.ImportProgressPhaseUi
import com.stratum.feature.onboarding.ImportProgressUi
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
internal fun catalogProgressStepStringRes(step: ImportCatalogStepUi): Int =
    when (step) {
        ImportCatalogStepUi.LoadingConvertedData -> FeatureR.string.gym_lifts_import_subtitle_loading_converted
        is ImportCatalogStepUi.MergingImportedFiles -> FeatureR.string.gym_lifts_import_subtitle_merging
        is ImportCatalogStepUi.MatchingCatalog -> FeatureR.string.gym_lifts_import_subtitle_matching
        is ImportCatalogStepUi.IdentifyingCustomLifts -> FeatureR.string.gym_lifts_import_subtitle_custom_lifts
        is ImportCatalogStepUi.InferringEquipment -> FeatureR.string.gym_lifts_import_subtitle_equipment
        ImportCatalogStepUi.EnablingAvailableLifts -> FeatureR.string.gym_lifts_import_subtitle_enabling
        ImportCatalogStepUi.SavingHistory -> FeatureR.string.gym_lifts_import_subtitle_saving
        ImportCatalogStepUi.AnalyzingPatterns -> FeatureR.string.gym_lifts_import_subtitle_analyzing
    }

@Composable
internal fun importProgressSubtitle(progress: ImportProgressUi): String? {
    progress.catalogStep?.let { step ->
        return when (step) {
            ImportCatalogStepUi.LoadingConvertedData,
            ImportCatalogStepUi.EnablingAvailableLifts,
            ImportCatalogStepUi.SavingHistory,
            ImportCatalogStepUi.AnalyzingPatterns,
            ->
                stringResource(catalogProgressStepStringRes(step))
            is ImportCatalogStepUi.MergingImportedFiles ->
                stringResource(
                    catalogProgressStepStringRes(step),
                    step.fileCount,
                )
            is ImportCatalogStepUi.MatchingCatalog ->
                stringResource(
                    catalogProgressStepStringRes(step),
                    step.exerciseCount,
                )
            is ImportCatalogStepUi.IdentifyingCustomLifts ->
                stringResource(
                    catalogProgressStepStringRes(step),
                    step.exerciseCount,
                )
            is ImportCatalogStepUi.InferringEquipment ->
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
