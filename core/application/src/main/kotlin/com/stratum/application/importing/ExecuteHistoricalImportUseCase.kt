package com.stratum.application.importing

import com.stratum.domain.lift.Weight
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.HistoricalImportResumeState
import com.stratum.domain.importing.ImportFailureKind
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.ImportFileValidationResult
import com.stratum.domain.importing.ImportHistoryPersistPolicy
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.MergeImportStats

sealed interface HistoricalImportOrchestrationEvent {
    data class ValidationFailed(val reason: ImportFileValidationResult.Invalid) : HistoricalImportOrchestrationEvent

    data class Progress(val progress: ImportProgress) : HistoricalImportOrchestrationEvent

    data class Completed(
        val refresh: RefreshPersistedImportStateResult,
        val mergeStats: MergeImportStats? = null,
    ) : HistoricalImportOrchestrationEvent

    data class Failed(
        val message: String,
        val resume: HistoricalImportResumeState?,
        val refresh: RefreshPersistedImportStateResult,
        val kind: ImportFailureKind = ImportFailureKind.Generic,
    ) : HistoricalImportOrchestrationEvent
}

class ExecuteHistoricalImportUseCase @Inject constructor(
    private val validateImportFiles: ValidateImportFilesUseCase,
    private val runHistoricalImport: RunHistoricalImportUseCase,
    private val refreshPersistedImportState: RefreshPersistedImportStateUseCase,
) {
    operator fun invoke(
        files: List<ImportFileInput>,
        resume: HistoricalImportResumeState?,
        persistPolicy: ImportHistoryPersistPolicy,
        /**
         * Applied when converted sets omit weight unit. Resume carries its own value;
         * when [resume] is non-null, [resume.fallbackWeightUnit] wins over this argument.
         */
        fallbackWeightUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
    ): Flow<HistoricalImportOrchestrationEvent> = flow {
        if (resume == null) {
            when (val validation = validateImportFiles(files)) {
                is ImportFileValidationResult.Valid -> Unit
                is ImportFileValidationResult.Invalid -> {
                    emit(HistoricalImportOrchestrationEvent.ValidationFailed(validation))
                    return@flow
                }
            }
        }
        val effectiveFallback = resume?.fallbackWeightUnit ?: fallbackWeightUnit
        runHistoricalImport(
            files = files,
            resume = resume,
            persistPolicy = persistPolicy,
            fallbackWeightUnit = effectiveFallback,
        ).collect { event ->
            when (event) {
                is HistoricalImportEvent.Progress ->
                    emit(HistoricalImportOrchestrationEvent.Progress(event.progress))
                is HistoricalImportEvent.Completed -> {
                    val refresh = refreshPersistedImportState()
                    emit(
                        HistoricalImportOrchestrationEvent.Completed(
                            refresh = refresh,
                            mergeStats = event.mergeStats,
                        ),
                    )
                }
                is HistoricalImportEvent.Failed -> {
                    // Replace-on-failure rollback is owned solely by HistoricalImportRunnerPort
                    // (RunFullHistoricalImportUseCase.buildImportFailureEvent) — not here.
                    val refresh = refreshPersistedImportState()
                    emit(
                        HistoricalImportOrchestrationEvent.Failed(
                            message = event.message,
                            resume = event.resume,
                            refresh = refresh,
                            kind = event.kind,
                        ),
                    )
                }
            }
        }
    }
}
