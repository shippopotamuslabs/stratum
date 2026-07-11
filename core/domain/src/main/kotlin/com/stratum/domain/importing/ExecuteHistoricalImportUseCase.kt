package com.stratum.domain.importing

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface HistoricalImportOrchestrationEvent {
    data class ValidationFailed(val reason: ImportFileValidationResult.Invalid) : HistoricalImportOrchestrationEvent

    data class Progress(val progress: ImportProgress) : HistoricalImportOrchestrationEvent

    data class Completed(val refresh: RefreshPersistedImportStateResult) : HistoricalImportOrchestrationEvent

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
    private val rollbackHistoricalImport: RollbackHistoricalImportUseCase,
    private val refreshPersistedImportState: RefreshPersistedImportStateUseCase,
) {
    operator fun invoke(
        files: List<ImportFileInput>,
        resume: HistoricalImportResumeState?,
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
        runHistoricalImport(files, resume).collect { event ->
            when (event) {
                is HistoricalImportEvent.Progress ->
                    emit(HistoricalImportOrchestrationEvent.Progress(event.progress))
                is HistoricalImportEvent.Completed -> {
                    val refresh = refreshPersistedImportState()
                    emit(HistoricalImportOrchestrationEvent.Completed(refresh))
                }
                is HistoricalImportEvent.Failed -> {
                    rollbackHistoricalImport()
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
