package com.stratum.feature.program

import com.stratum.core.ui.trainingsetup.MinimumVolumeDraftUi
import com.stratum.core.ui.trainingsetup.ScheduleDraftUi
import com.stratum.core.ui.trainingsetup.SpecializationDraftUi
import com.stratum.feature.program.ui.EditWorkoutUiState
import com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME

enum class CreateProgramStep {
    ChoosePath,
    Specialization,
    MinimumVolume,
    Schedule,
    Generating,
    Editor,
}

enum class CreateProgramPath {
    Generate,
    Scratch,
}

enum class CreateProgramGenerateState {
    Idle,
    Loading,
    Error,
}

data class CreateProgramDayUi(
    val id: String,
    val name: String,
    val position: Int,
    val isRest: Boolean,
    val editState: EditWorkoutUiState.Loaded?,
)

data class CreateProgramUiState(
    val step: CreateProgramStep = CreateProgramStep.ChoosePath,
    val path: CreateProgramPath? = null,
    val specializationDraft: SpecializationDraftUi = SpecializationDraftUi.Empty,
    val minimumVolumeDraft: MinimumVolumeDraftUi = MinimumVolumeDraftUi.Empty,
    val scheduleDraft: ScheduleDraftUi = ScheduleDraftUi.Empty,
    val generateState: CreateProgramGenerateState = CreateProgramGenerateState.Idle,
    val generatePhaseLabel: String? = null,
    val generateDetail: String? = null,
    val generateErrorMessage: String? = null,
    val programId: String = "",
    val programName: String = "",
    val days: List<CreateProgramDayUi> = emptyList(),
    val selectedDayId: String? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val saveErrorMessage: String? = null,
    val showDiscardConfirm: Boolean = false,
    val showRenameProgram: Boolean = false,
    val showRenameWorkout: Boolean = false,
) {
    val selectedDay: CreateProgramDayUi?
        get() = days.firstOrNull { day -> day.id == selectedDayId }

    val canSave: Boolean
        get() =
            days.any { day ->
                !day.isRest &&
                    day.editState != null &&
                    day.editState.lifts.isNotEmpty() &&
                    day.editState.lifts.none { lift -> lift.name == PLACEHOLDER_LIFT_NAME }
            }
}
