package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.component.platecalculator.PlateDiagramPreviewContent
import com.stratum.core.ui.component.platecalculator.PlateMiniDrawerPreviewContent
import com.stratum.core.ui.component.platecalculator.PlateSheetPreviewContent
import com.stratum.core.ui.theme.StratumTheme
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
    StratumTheme(themeId = StratumThemeId.Tonal) {
        PreviewMultiLine()
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartMultiLineNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        PreviewMultiLine()
    }
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
    StratumTheme(themeId = StratumThemeId.Tonal) {
        PreviewDonut()
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun chartDonutNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        PreviewDonut()
    }
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
    PlateDiagramPreviewContent(family = StratumThemeId.Tonal, targetWeight = 225.0)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateDiagramLoadedNeo() {
    PlateDiagramPreviewContent(family = StratumThemeId.NeoBrutalist, targetWeight = 225.0)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateDiagramEmptyTonal() {
    PlateDiagramPreviewContent(family = StratumThemeId.Tonal, targetWeight = null)
}

@PreviewTest
@PreviewLightDark
@Composable
fun plateDiagramEmptyNeo() {
    PlateDiagramPreviewContent(family = StratumThemeId.NeoBrutalist, targetWeight = null)
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
    ProgramLibraryCardPreviewContent(family = StratumThemeId.Tonal, expanded = true)
}

@PreviewTest
@PreviewLightDark
@Composable
fun programLibraryCardExpandedNeo() {
    ProgramLibraryCardPreviewContent(family = StratumThemeId.NeoBrutalist, expanded = true)
}

@PreviewTest
@PreviewLightDark
@Composable
fun programLibraryCardCollapsedTonal() {
    ProgramLibraryCardPreviewContent(family = StratumThemeId.Tonal, expanded = false)
}

@PreviewTest
@PreviewLightDark
@Composable
fun programLibraryCardCollapsedNeo() {
    ProgramLibraryCardPreviewContent(family = StratumThemeId.NeoBrutalist, expanded = false)
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
    StratumTheme(themeId = StratumThemeId.Tonal) {
        TimeFieldPreviewColumn()
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun timeFieldNeo() {
    StratumTheme(themeId = StratumThemeId.NeoBrutalist) {
        TimeFieldPreviewColumn()
    }
}
