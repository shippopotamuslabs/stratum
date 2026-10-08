package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.stratum.core.ui.theme.StratumThemeId

/**
 * Screenshot tests for CreateProgramScreen wizard steps (STR-92). Thin `@PreviewTest`
 * wrappers over the shared preview content — fake state lives in the main-source
 * previews so Studio `@Preview`s and goldens can never drift apart.
 */
@PreviewTest
@PreviewLightDark
@Composable
fun createProgramChoosePathTonal() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewChoosePathState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createProgramChoosePathNeo() {
    CreateProgramStepPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewChoosePathState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createProgramSpecializationTonal() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewSpecializationState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createProgramSpecializationNeo() {
    CreateProgramStepPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewSpecializationState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createProgramMinimumVolumeTonal() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewMinimumVolumeState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createProgramMinimumVolumeNeo() {
    CreateProgramStepPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewMinimumVolumeState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createProgramScheduleTonal() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewScheduleState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createProgramScheduleNeo() {
    CreateProgramStepPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewScheduleState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createProgramGenerateErrorTonal() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewGenerateErrorState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createProgramGenerateErrorNeo() {
    CreateProgramStepPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewGenerateErrorState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createProgramEditorTonal() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewEditorState())
}

@PreviewTest
@PreviewLightDark
@Composable
fun createProgramEditorNeo() {
    CreateProgramStepPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewEditorState())
}
