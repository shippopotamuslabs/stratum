package com.stratum.feature.program

import com.stratum.core.ui.progress.activeTimelineIndex
import com.stratum.core.ui.progress.toUi
import com.stratum.core.ui.trainingsetup.TrainingSetupValidationConstants
import com.stratum.core.ui.trainingsetup.isReadyForGeneration
import com.stratum.core.ui.trainingsetup.withSixDayCycleLengthSelected
import com.stratum.core.ui.trainingsetup.withSyncedCycleSpace
import com.stratum.core.ui.trainingsetup.withTrainingDayCount
import com.stratum.core.ui.trainingsetup.withTrainingSlotToggled
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.GetUserTrainingProfileUseCase
import com.stratum.domain.onboarding.UpdateSpecializationUseCase
import com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.domain.program.GenerateProgramUseCase
import com.stratum.domain.program.InferLastTrainedBucketUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramGenerationProgress
import com.stratum.domain.program.ProgramGenerationProgressPhase
import com.stratum.domain.logging.LogAnalyticsEventUseCase
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.logging.PARAM_ACTION
import com.stratum.domain.logging.PARAM_SCREEN
import com.stratum.domain.logging.USER_VISIBLE_ERROR_EVENT
import com.stratum.domain.telemetry.SynthesisTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Generate-path orchestration: specialization / minimum-volume / schedule prefs, profile flush,
 * and [GenerateProgramUseCase]. Editor load after success is delegated via [onProgramGenerated].
 */
internal class CreateProgramGenerateController(
    private val state: MutableStateFlow<CreateProgramUiState>,
    private val scope: CoroutineScope,
    private val generateProgram: GenerateProgramUseCase,
    private val updateUserTrainingProfile: UpdateUserTrainingProfileUseCase,
    private val updateSpecialization: UpdateSpecializationUseCase,
    private val getUserTrainingProfile: GetUserTrainingProfileUseCase,
    private val inferLastTrained: InferLastTrainedBucketUseCase,
    private val onProgramGenerated: (Program) -> Unit,
    private val logError: LogErrorUseCase,
    private val logAnalytics: LogAnalyticsEventUseCase,
    private val synthesisTelemetry: SynthesisTelemetry,
) {
    private var generateJob: Job? = null

    fun selectGeneratePath() {
        state.update {
            it.copy(
                path = CreateProgramPath.Generate,
                step = CreateProgramStep.Specialization,
            )
        }
    }

    fun onMuscleToggled(label: String) {
        state.update { current ->
            val selected = current.specializationDraft.selectedMuscleLabels.toMutableSet()
            if (label in selected) {
                selected.remove(label)
            } else if (selected.size < TrainingSetupValidationConstants.MAX_SPECIALIZE) {
                selected.add(label)
            }
            current.copy(
                specializationDraft =
                    current.specializationDraft.copy(selectedMuscleLabels = selected),
            )
        }
    }

    fun onMinimumVolumeMuscleToggled(label: String) {
        state.update { current ->
            val selected = current.minimumVolumeDraft.selectedMuscleLabels.toMutableSet()
            if (label in selected) {
                selected.remove(label)
            } else if (selected.size < TrainingSetupValidationConstants.MAX_MINIMUM_VOLUME) {
                selected.add(label)
            }
            current.copy(
                minimumVolumeDraft =
                    current.minimumVolumeDraft.copy(selectedMuscleLabels = selected),
            )
        }
    }

    fun onTrainingDayCountChanged(count: Int) {
        state.update { current ->
            current.copy(scheduleDraft = current.scheduleDraft.withTrainingDayCount(count))
        }
    }

    fun onSessionPresetSelected(key: String?) {
        state.update { current ->
            current.copy(scheduleDraft = current.scheduleDraft.copy(sessionLengthSelection = key))
        }
    }

    fun onCustomSessionMinutesChange(minutes: Int) {
        state.update { current ->
            current.copy(scheduleDraft = current.scheduleDraft.copy(customSessionMinutes = minutes))
        }
    }

    fun onSixDayCycleLengthSelected(cycleLength: Int) {
        state.update { current ->
            current.copy(
                scheduleDraft = current.scheduleDraft.withSixDayCycleLengthSelected(cycleLength),
            )
        }
    }

    fun onTrainingSlotToggled(slotIndex: Int) {
        state.update { current ->
            current.copy(scheduleDraft = current.scheduleDraft.withTrainingSlotToggled(slotIndex))
        }
    }

    fun nextFromPreferences() {
        when (state.value.step) {
            CreateProgramStep.Specialization ->
                state.update { it.copy(step = CreateProgramStep.MinimumVolume) }
            CreateProgramStep.MinimumVolume ->
                state.update { it.copy(step = CreateProgramStep.Schedule) }
            CreateProgramStep.Schedule -> generate()
            else -> Unit
        }
    }

    fun backFromPreferences() {
        when (state.value.step) {
            CreateProgramStep.Specialization ->
                state.update { it.copy(step = CreateProgramStep.ChoosePath, path = null) }
            CreateProgramStep.MinimumVolume ->
                state.update { it.copy(step = CreateProgramStep.Specialization) }
            CreateProgramStep.Schedule ->
                state.update { it.copy(step = CreateProgramStep.MinimumVolume) }
            else -> Unit
        }
    }

    fun generate() {
        if (state.value.generateState == CreateProgramGenerateState.Loading) return
        val schedule = state.value.scheduleDraft.withSyncedCycleSpace()
        if (!schedule.isReadyForGeneration()) {
            state.update {
                it.copy(scheduleDraft = schedule.copy(showErrors = true))
            }
            return
        }
        state.update {
            it.copy(
                step = CreateProgramStep.Generating,
                generateState = CreateProgramGenerateState.Loading,
                generateErrorMessage = null,
                generateProgress =
                    ProgramGenerationProgress(ProgramGenerationProgressPhase.PreparingPayload).toUi(),
                generateMaxTimelineIndex = 0,
                scheduleDraft = schedule,
            )
        }
        generateJob?.cancel()
        generateJob =
            scope.launch {
                flushPreferencesForGeneration()
                // STR-19 trace: per-stage client timings for the post-ready window.
                // On-device generation has no server jobId here, so stages are
                // correlated by this client session ID (same convention as
                // synthesisRendered below).
                val sessionId = java.util.UUID.randomUUID().toString()
                val generateStartMs = System.currentTimeMillis()
                val result =
                    generateProgram(
                        restSlotIndices = schedule.restSlotIndices,
                        cycleLength = schedule.cycleLength,
                    ) { progress ->
                        withContext(Dispatchers.Main.immediate) {
                            val ui = progress.toUi()
                            val index = ui.phase.activeTimelineIndex()
                            state.update { current ->
                                current.copy(
                                    generateProgress = ui,
                                    generateMaxTimelineIndex =
                                        maxOf(current.generateMaxTimelineIndex, index),
                                )
                            }
                        }
                    }
                synthesisTelemetry.synthesisClientStage(
                    sessionId,
                    SynthesisTelemetry.STAGE_GENERATE_PROGRAM,
                    generateStartMs,
                )
                val program =
                    result.getOrElse { error ->
                        publishGenerateFailure(error)
                        return@launch
                    }
                val editorStartMs = System.currentTimeMillis()
                onProgramGenerated(program)
                synthesisTelemetry.synthesisClientStage(
                    sessionId,
                    SynthesisTelemetry.STAGE_LOAD_PROGRAM_INTO_EDITOR,
                    editorStartMs,
                )
                // STR-13 trace: program handed to the editor for rendering.
                synthesisTelemetry.synthesisRendered(sessionId)
            }
    }

    private fun publishGenerateFailure(error: Throwable) {
        logError(GENERATE_LOG_TAG, "program generation failed: ${error.message}", error)
        logAnalytics(
            USER_VISIBLE_ERROR_EVENT,
            mapOf(PARAM_SCREEN to "create_program", PARAM_ACTION to "generate_program"),
        )
        state.update {
            it.copy(
                generateState = CreateProgramGenerateState.Error,
                generateErrorMessage = error.message ?: "Generation failed",
                step = CreateProgramStep.Generating,
            )
        }
    }

    private suspend fun flushPreferencesForGeneration() {
        val current = state.value
        val existing = getUserTrainingProfile()
        val lastTrained = inferLastTrained(fallback = existing.lastTrained)
        // Schedule identity is rest+cycleLength — clear legacy weekdays; persist cycle-space.
        val draft = current.scheduleDraft
        val minutes = draft.resolvedSessionMinutes
        updateUserTrainingProfile(
            existing.copy(
                lastTrained = lastTrained,
                weekdays = emptySet(),
                sessionLengthMinutes = minutes,
                generationTrainingDayCount = draft.trainingDayCount,
                generationCycleLength = draft.cycleLength,
                generationRestSlotIndices = draft.restSlotIndices,
            ),
        )
        val specialized =
            current.specializationDraft.selectedMuscleLabels
                .mapNotNull { label -> muscleFromLabel(label) }
        val minimum =
            current.minimumVolumeDraft.selectedMuscleLabels
                .mapNotNull { label -> muscleFromLabel(label) }
        val levels =
            buildMap {
                specialized.forEach { muscle -> put(muscle, SpecializationLevel.Specialize) }
                minimum.forEach { muscle -> put(muscle, SpecializationLevel.MinimumVolume) }
            }
        updateSpecialization(SpecializationPreference(levels = levels))
    }

    companion object {
        private const val GENERATE_LOG_TAG = "CreateProgramGenerate"

        fun muscleFromLabel(label: String): MuscleGroup? =
            MuscleGroup.entries.firstOrNull { muscle -> muscle.displayLabel == label }
    }
}
