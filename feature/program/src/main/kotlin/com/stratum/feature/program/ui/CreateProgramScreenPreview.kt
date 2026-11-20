package com.stratum.feature.program.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.stratum.core.ui.theme.StratumTheme
import com.stratum.core.ui.theme.StratumThemeId
import com.stratum.feature.program.CreateProgramDayUi
import com.stratum.feature.program.CreateProgramGenerateState
import com.stratum.feature.program.CreateProgramStep
import com.stratum.feature.program.CreateProgramUiState
import com.stratum.feature.program.ProgramEditorSurface

/**
 * Screenshot-test previews for [CreateProgramScreen] wizard steps (STR-92). Covers the
 * static steps; the Generating loading slice animates and is intentionally skipped (the
 * error slice is covered). `darkTheme` is left null so `@PreviewLightDark` genuinely
 * varies light/dark.
 */
@PreviewLightDark
@Composable
private fun CreateProgramChoosePathTonalPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewChoosePathState())
}

@PreviewLightDark
@Composable
private fun CreateProgramChoosePathNeoPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewChoosePathState())
}

@PreviewLightDark
@Composable
private fun CreateProgramSpecializationTonalPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewSpecializationState())
}

@PreviewLightDark
@Composable
private fun CreateProgramSpecializationNeoPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewSpecializationState())
}

@PreviewLightDark
@Composable
private fun CreateProgramMinimumVolumeTonalPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewMinimumVolumeState())
}

@PreviewLightDark
@Composable
private fun CreateProgramMinimumVolumeNeoPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewMinimumVolumeState())
}

@PreviewLightDark
@Composable
private fun CreateProgramScheduleTonalPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewScheduleState())
}

@PreviewLightDark
@Composable
private fun CreateProgramScheduleNeoPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewScheduleState())
}

@PreviewLightDark
@Composable
private fun CreateProgramGenerateErrorTonalPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewGenerateErrorState())
}

@PreviewLightDark
@Composable
private fun CreateProgramGenerateErrorNeoPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewGenerateErrorState())
}

@PreviewLightDark
@Composable
private fun CreateProgramEditorTonalPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewEditorState())
}

@PreviewLightDark
@Composable
private fun CreateProgramEditorNeoPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.NeoBrutalist, state = previewEditorState())
}

@PreviewLightDark
@Composable
private fun CreateProgramSheetTonalPreview() {
    CreateProgramStepPreviewContent(family = StratumThemeId.Tonal, state = previewProgramSheetState())
}

@Composable
internal fun CreateProgramStepPreviewContent(family: StratumThemeId, state: CreateProgramUiState) {
    StratumTheme(themeId = family) {
        CreateProgramScreen(
            state = state,
            callbacks = CreateProgramScreenCallbacks(),
        )
    }
}

internal fun previewChoosePathState(): CreateProgramUiState = CreateProgramUiState()

internal fun previewSpecializationState(): CreateProgramUiState =
    CreateProgramUiState(step = CreateProgramStep.Specialization)

internal fun previewMinimumVolumeState(): CreateProgramUiState =
    CreateProgramUiState(step = CreateProgramStep.MinimumVolume)

internal fun previewScheduleState(): CreateProgramUiState =
    CreateProgramUiState(step = CreateProgramStep.Schedule)

internal fun previewGenerateErrorState(): CreateProgramUiState =
    CreateProgramUiState(
        step = CreateProgramStep.Generating,
        generateState = CreateProgramGenerateState.Error,
    )

internal fun previewEditorState(): CreateProgramUiState =
    CreateProgramUiState(
        step = CreateProgramStep.Editor,
        programName = "My 3 Day",
        days =
            listOf(
                CreateProgramDayUi(
                    id = "day-1",
                    name = "Push Day",
                    position = 0,
                    isRest = false,
                    editState = previewEditWorkoutLoadedState(),
                ),
                CreateProgramDayUi(
                    id = "day-2",
                    name = "Rest",
                    position = 1,
                    isRest = true,
                    editState = null,
                ),
            ),
        selectedDayId = "day-1",
    )

internal fun previewProgramSheetState(): CreateProgramUiState =
    CreateProgramUiState(
        step = CreateProgramStep.Editor,
        editorSurface = ProgramEditorSurface.Sheet,
        programName = "My 3 Day",
        days =
            listOf(
                CreateProgramDayUi(
                    id = "day-push",
                    name = "Push",
                    position = 0,
                    isRest = false,
                    editState =
                        EditWorkoutUiState.Loaded(
                            workoutId = "day-push",
                            workoutName = "Push",
                            estimatedDurationMinutes = 36,
                            lifts =
                                listOf(
                                    previewSheetLift(
                                        workoutLiftId = "wl-bench",
                                        name = "Bench Press",
                                        setCount = 3,
                                        repMin = 5,
                                        repMax = 8,
                                        restSeconds = 180,
                                        rirGoal = 2,
                                    ),
                                    previewSheetLift(
                                        workoutLiftId = "wl-press",
                                        name = "Overhead Press",
                                        setCount = 2,
                                        repMin = 8,
                                        repMax = 12,
                                        restSeconds = 90,
                                        rirGoal = 1,
                                    ),
                                ),
                        ),
                ),
                CreateProgramDayUi(
                    id = "day-rest",
                    name = "Rest",
                    position = 1,
                    isRest = true,
                    editState = null,
                ),
                CreateProgramDayUi(
                    id = "day-pull",
                    name = "Pull",
                    position = 2,
                    isRest = false,
                    editState =
                        EditWorkoutUiState.Loaded(
                            workoutId = "day-pull",
                            workoutName = "Pull",
                            estimatedDurationMinutes = 24,
                            lifts =
                                listOf(
                                    previewSheetLift(
                                        workoutLiftId = "wl-row",
                                        name = "Barbell Row",
                                        setCount = 3,
                                        repMin = 6,
                                        repMax = 10,
                                        restSeconds = 120,
                                        rirGoal = 2,
                                    ),
                                ),
                        ),
                ),
            ),
        selectedDayId = "day-push",
    )

private fun previewSheetLift(
    workoutLiftId: String,
    name: String,
    setCount: Int,
    repMin: Int,
    repMax: Int,
    restSeconds: Int,
    rirGoal: Int,
): EditLiftState =
    EditLiftState(
        workoutLiftId = workoutLiftId,
        liftId = workoutLiftId,
        name = name,
        position = 0,
        sets =
            (1..setCount).map { index ->
                EditSetState(
                    setId = "$workoutLiftId-$index",
                    setNumber = index,
                    repMin = repMin,
                    repMax = repMax,
                    restSeconds = restSeconds,
                    rirGoal = rirGoal,
                )
            },
    )
