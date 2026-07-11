package com.stratum.feature.onboarding

/**
 * Coarse Gym-card timeline for historical training-log import.
 * Maps noisy domain phases onto four visible steps (upload → convert → catalog → analyze).
 */
internal enum class HistoricalImportTimelineStep {
    Upload,
    Convert,
    Catalog,
    Analyze,
}

internal val historicalImportTimelineSteps: List<HistoricalImportTimelineStep> =
    HistoricalImportTimelineStep.entries

/** Index into [historicalImportTimelineSteps]; [ImportProgressPhaseUi.Complete] marks all done. */
internal fun ImportProgressPhaseUi?.historicalImportTimelineIndex(): Int =
    when (this) {
        null -> 0
        ImportProgressPhaseUi.Uploading -> 0
        ImportProgressPhaseUi.ReadingData,
        ImportProgressPhaseUi.CreatingConverter,
        ImportProgressPhaseUi.ValidatingSample,
        ImportProgressPhaseUi.ConvertingFull,
        -> 1
        ImportProgressPhaseUi.EnrichingCustomLifts -> 2
        ImportProgressPhaseUi.ExtractingInsights -> 3
        ImportProgressPhaseUi.Complete -> historicalImportTimelineSteps.size
        ImportProgressPhaseUi.Failed -> 0
    }
