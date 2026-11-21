package com.stratum.feature.program

import com.stratum.core.ui.progress.ProgramGenerationProgressUi
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

/**
 * Why the editor is asking to discard the in-progress training session before continuing.
 * Distinct from [CreateProgramUiState.showDiscardConfirm] (unsaved draft navigation).
 */
enum class ActiveSessionDiscardReason {
    /** Removing the cycle day that is currently being trained. */
    RemoveSelectedDay,
    /** Saving template changes that drop or unlink the active workout. */
    SaveChanges,
    /** Activating this draft while another session is live. */
    Activate,
    /** Demoting/saving in a way that leaves the live session without a template. */
    SaveToLibrary,
}

/**
 * A single cycle slot in the create/edit program editor.
 *
 * Tabs are ordered cycle slots (train or rest) — not Mon–Sun weekday identity.
 * Slot order on save becomes [Program.cycleLength] + [Program.restSlotIndices].
 */
data class CreateProgramDayUi(
    val id: String,
    val name: String,
    val position: Int,
    val isRest: Boolean,
    val editState: EditWorkoutUiState.Loaded?,
    /**
     * Optional projection cache when loaded from a weekday-bound program.
     * Not schedule identity — save ignores this and writes cycle slots.
     */
    val weekday: com.stratum.domain.onboarding.Weekday? = null,
)

data class CreateProgramUiState(
    val step: CreateProgramStep = CreateProgramStep.ChoosePath,
    val path: CreateProgramPath? = null,
    val specializationDraft: SpecializationDraftUi = SpecializationDraftUi.Empty,
    val minimumVolumeDraft: MinimumVolumeDraftUi = MinimumVolumeDraftUi.Empty,
    val scheduleDraft: ScheduleDraftUi = ScheduleDraftUi.Empty,
    val generateState: CreateProgramGenerateState = CreateProgramGenerateState.Idle,
    val generateProgress: ProgramGenerationProgressUi? = null,
    val generateMaxTimelineIndex: Int = 0,
    val generateErrorMessage: String? = null,
    val programId: String = "",
    val programName: String = "",
    val days: List<CreateProgramDayUi> = emptyList(),
    val selectedDayId: String? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val saveErrorMessage: String? = null,
    val showDiscardConfirm: Boolean = false,
    /**
     * When non-null, show confirm-before-discard for the in-progress training session.
     * Cleared on dismiss or after successful abandon + action.
     */
    val pendingActiveSessionDiscard: ActiveSessionDiscardReason? = null,
    val showRenameProgram: Boolean = false,
    val showRenameWorkout: Boolean = false,
    /** True when opened from library with an existing program id. */
    val isEditingExisting: Boolean = false,
    /** Active flag of the loaded program (edit mode only). */
    val originalIsActive: Boolean = false,
    /** Load failure when [isEditingExisting] entry cannot resolve the program. */
    val loadErrorMessage: String? = null,
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

    /**
     * Always true — create/edit cycle slots are uncapped (not a fixed Mon–Sun week).
     * Rest and train tabs are first-class slots; length is free-form.
     */
    val canAddDay: Boolean
        get() = true

    /** Secondary library save is only for new drafts or inactive library edits. */
    val showSaveToLibraryAction: Boolean
        get() = !isEditingExisting || !originalIsActive

    /** Activate is for new drafts and inactive library programs. */
    val showActivateAction: Boolean
        get() = !isEditingExisting || !originalIsActive

    /** Primary save that preserves active state when editing the active program. */
    val showSaveChangesAction: Boolean
        get() = isEditingExisting && originalIsActive
}
