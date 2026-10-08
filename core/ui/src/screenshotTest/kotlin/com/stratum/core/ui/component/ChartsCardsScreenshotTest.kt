package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.component.platecalculator.PlateDiagramEmptyPreviewContent
import com.stratum.core.ui.component.platecalculator.PlateDiagramLoadedPreviewContent
import com.stratum.core.ui.component.platecalculator.PlateMiniDrawerPreviewContent
import com.stratum.core.ui.component.platecalculator.PlateSheetPreviewContent
import com.stratum.core.ui.theme.StratumThemeId
import java.time.LocalTime

/**
 * Screenshot tests for charts, cards, and pickers (STR-94). Thin `@PreviewTest` wrappers over
 * the shared preview content.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun chartMultiLineTonal() {
    MultiLineChartPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartMultiLineNeo() {
    MultiLineChartPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartDetailSingleTonal() {
    DetailChartPreviewContent(
        family = StratumThemeId.Tonal,
        primarySeries = sampleStrengthSeries(),
        contentDescription = "Sample e1RM chart",
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartDetailSingleNeo() {
    DetailChartPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        primarySeries = sampleStrengthSeries(),
        contentDescription = "Sample e1RM chart",
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartDetailDualTonal() {
    DetailChartPreviewContent(
        family = StratumThemeId.Tonal,
        primarySeries = sampleStrengthSeries(),
        secondarySeries = sampleVolumeSeries(),
        contentDescription = "Sample dual chart",
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartDetailDualNeo() {
    DetailChartPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        primarySeries = sampleStrengthSeries(),
        secondarySeries = sampleVolumeSeries(),
        contentDescription = "Sample dual chart",
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartDetailProgressBarsTonal() {
    DetailChartPreviewContent(
        family = StratumThemeId.Tonal,
        primarySeries = sampleProgressSeries(),
        primaryYScale = DetailYScale.Progress,
        contentDescription = "Sample progress bars",
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartDetailProgressBarsNeo() {
    DetailChartPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        primarySeries = sampleProgressSeries(),
        primaryYScale = DetailYScale.Progress,
        contentDescription = "Sample progress bars",
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartDetailProgressVolumeTonal() {
    DetailChartPreviewContent(
        family = StratumThemeId.Tonal,
        primarySeries = sampleProgressSeries(),
        secondarySeries = sampleVolumeSeries(),
        primaryYScale = DetailYScale.Progress,
        secondaryYScale = DetailYScale.Volume,
        contentDescription = "Sample progress bars + volume",
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartDetailProgressVolumeNeo() {
    DetailChartPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        primarySeries = sampleProgressSeries(),
        secondarySeries = sampleVolumeSeries(),
        primaryYScale = DetailYScale.Progress,
        secondaryYScale = DetailYScale.Volume,
        contentDescription = "Sample progress bars + volume",
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartDonutTonal() {
    DonutChartPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartDonutNeo() {
    DonutChartPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun confidenceBarsTonal() {
    ConfidenceBarsPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun confidenceBarsNeo() {
    ConfidenceBarsPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun musclePickerTonal() {
    MusclePickerPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun musclePickerNeo() {
    MusclePickerPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateDiagramLoadedTonal() {
    PlateDiagramLoadedPreviewContent(family = StratumThemeId.Tonal, targetWeight = 225.0)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateDiagramLoadedNeo() {
    PlateDiagramLoadedPreviewContent(family = StratumThemeId.NeoBrutalist, targetWeight = 225.0)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateDiagramEmptyTonal() {
    PlateDiagramEmptyPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateDiagramEmptyNeo() {
    PlateDiagramEmptyPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateMiniDrawerTonal() {
    PlateMiniDrawerPreviewContent(family = StratumThemeId.Tonal, targetWeight = 225.0)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateMiniDrawerNeo() {
    PlateMiniDrawerPreviewContent(family = StratumThemeId.NeoBrutalist, targetWeight = 225.0)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateMiniDrawerMismatchTonal() {
    PlateMiniDrawerPreviewContent(family = StratumThemeId.Tonal, targetWeight = 153.0)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateMiniDrawerMismatchNeo() {
    PlateMiniDrawerPreviewContent(family = StratumThemeId.NeoBrutalist, targetWeight = 153.0)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateSheetTonal() {
    PlateSheetPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateSheetNeo() {
    PlateSheetPreviewContent(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun programLibraryCardExpandedTonal() {
    ProgramLibraryCardPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewProgramLibraryCardActiveState,
        expanded = true,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun programLibraryCardExpandedNeo() {
    ProgramLibraryCardPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewProgramLibraryCardActiveState,
        expanded = true,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun programLibraryCardCollapsedTonal() {
    ProgramLibraryCardPreviewContent(
        family = StratumThemeId.Tonal,
        state = previewProgramLibraryCardInactiveState,
        expanded = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun programLibraryCardCollapsedNeo() {
    ProgramLibraryCardPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        state = previewProgramLibraryCardInactiveState,
        expanded = false,
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun timePickerDialogTonal() {
    TimePickerDialogPreviewContent(
        family = StratumThemeId.Tonal,
        initialTime = LocalTime.of(14, 30),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun timePickerDialogNeo() {
    TimePickerDialogPreviewContent(
        family = StratumThemeId.NeoBrutalist,
        initialTime = LocalTime.of(9, 15),
    )
}

@PreviewTest
@PreviewLightDark
@Composable
fun durationStepperTonal() {
    DurationStepperPreviewGrid(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun durationStepperNeo() {
    DurationStepperPreviewGrid(family = StratumThemeId.NeoBrutalist)
}

@PreviewTest
@PreviewLightDark
@Composable
fun timeFieldTonal() {
    TimeFieldPreviewContent(family = StratumThemeId.Tonal)
}

@PreviewTest
@PreviewLightDark
@Composable
fun timeFieldNeo() {
    TimeFieldPreviewContent(family = StratumThemeId.NeoBrutalist)
}
