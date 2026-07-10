package com.stratum.core.inference

import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.HistoricalImportPort
import com.stratum.domain.importing.HistoricalImportRunnerPort
import com.stratum.domain.importing.ImportCatalogProgressStep
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.ImportJob
import com.stratum.domain.importing.ImportJobRepository
import com.stratum.domain.importing.ImportJobStatus
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.importing.HistoricalImportResumeState
import com.stratum.domain.importing.ImportedHistoryChunk
import com.stratum.domain.importing.MergeImportedHistoryUseCase
import com.stratum.domain.importing.PersistImportedHistoryUseCase
import com.stratum.domain.importing.RollbackHistoricalImportUseCase
import com.stratum.domain.importing.ImportFailureKind
import com.stratum.domain.importing.RemoteImportJobStatus
import com.stratum.domain.importing.StartHistoricalImportUseCase
import com.stratum.domain.importing.UnsupportedImportFileException
import com.stratum.domain.di.DefaultDispatcher
import com.stratum.domain.logging.LoggingPort
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class RunFullHistoricalImportUseCase @Inject constructor(
    private val startHistoricalImport: StartHistoricalImportUseCase,
    private val historicalImportPort: HistoricalImportPort,
    private val persistImportedHistory: PersistImportedHistoryUseCase,
    private val rollbackHistoricalImport: RollbackHistoricalImportUseCase,
    private val importJobRepository: ImportJobRepository,
    private val mergeImportedHistory: MergeImportedHistoryUseCase,
    private val importLiftResolutionLogger: ImportLiftResolutionLogger,
    private val loggingPort: LoggingPort,
    // Import work (network upload/poll/download, JSON parsing, lift resolution) historically ran
    // on Dispatchers.Main and caused ANRs. Defaulted so JVM tests can construct without Hilt.
    @DefaultDispatcher private val importDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : HistoricalImportRunnerPort {
    override fun run(
        files: List<ImportFileInput>,
        resume: HistoricalImportResumeState?,
        // emit() is called both directly here and from nested onProgress callbacks inside
        // convertSingleFile / persistImportedHistory (which run on importDispatcher via flowOn).
        // Wrapping those calls in withContext would make emit() fire on a different dispatcher than
        // the collector, throwing the Flow context-preservation invariant. flowOn() shifts the whole
        // upstream (body + all emissions) onto importDispatcher and bridges to the collector's
        // context with a channel — the correct tool for a flow that emits from nested callbacks.
        // Historically this body ran on Dispatchers.Main (the collector's context) and caused ANRs.
    ): Flow<HistoricalImportEvent> = flow {
        val allFiles = resume?.files ?: files
        if (allFiles.isEmpty()) {
            emit(HistoricalImportEvent.Failed("No files selected."))
            return@flow
        }
        val totalFiles = allFiles.size
        val chunks = (resume?.completedChunks ?: emptyList()).toMutableList()
        val pendingFiles = allFiles.drop(chunks.size)
        val startIndex = chunks.size
        try {
            pendingFiles.forEachIndexed { offset, file ->
                val fileIndex = startIndex + offset + 1
                convertSingleFile(
                    file = file,
                    fileIndex = fileIndex,
                    fileCount = totalFiles,
                    onProgress = { progress ->
                        emit(HistoricalImportEvent.Progress(progress))
                    },
                )?.let { chunk ->
                    chunks += chunk
                }
            }
            if (totalFiles > 1) {
                emit(
                    HistoricalImportEvent.Progress(
                        ImportProgress(
                            phase = ImportProgressPhase.EnrichingCustomLifts,
                            catalogStep = ImportCatalogProgressStep.MergingImportedFiles(totalFiles),
                        ),
                    ),
                )
            }
            val (sessions, sets) = mergeImportedHistory(chunks)
            emit(
                HistoricalImportEvent.Progress(
                    ImportProgress(
                        phase = ImportProgressPhase.EnrichingCustomLifts,
                        catalogStep = ImportCatalogProgressStep.LoadingConvertedData,
                    ),
                ),
            )
            val summary =
                persistImportedHistory(sessions, sets) { progress ->
                    emit(HistoricalImportEvent.Progress(progress))
                }.getOrThrow()
            summary.liftResolution?.let(importLiftResolutionLogger::log)
            emit(HistoricalImportEvent.Progress(ImportProgress(ImportProgressPhase.Complete)))
            emit(HistoricalImportEvent.Completed(summary))
        } catch (error: Throwable) {
            rollbackHistoricalImport()
            val message = error.message ?: "Import failed."
            val kind =
                if (error is UnsupportedImportFileException) {
                    ImportFailureKind.UnsupportedFile
                } else {
                    ImportFailureKind.Generic
                }
            loggingPort.error(TAG, "Historical import failed: $message", error)
            val resumeState =
                HistoricalImportResumeState(
                    files = allFiles,
                    completedChunks = chunks.toList(),
                ).takeIf { it.isResumable() }
            emit(
                HistoricalImportEvent.Failed(
                    message = message,
                    resume = resumeState,
                    kind = kind,
                ),
            )
        }
    }.flowOn(importDispatcher)

    private suspend fun convertSingleFile(
        file: ImportFileInput,
        fileIndex: Int,
        fileCount: Int,
        onProgress: suspend (ImportProgress) -> Unit,
    ): ImportedHistoryChunk? {
        val job = startHistoricalImport().getOrElse { error ->
            error("Could not start import job: ${error.message}")
        }
        try {
            onProgress(
                ImportProgress(
                    phase = ImportProgressPhase.Uploading,
                    fileIndex = fileIndex,
                    fileCount = fileCount,
                ),
            )
            val refs =
                historicalImportPort.uploadRawAndSample(job.id, file.bytes, file.fileName).getOrThrow()
            updateLocalJob(job, ImportJobStatus.AgentRunning)

            historicalImportPort.startImport(job.id, refs).getOrThrow()

            val snapshot =
                pollUntilTerminal(
                    jobId = job.id,
                    fileIndex = fileIndex,
                    fileCount = fileCount,
                    onProgress = onProgress,
                ).getOrThrow()

            when (snapshot.status) {
                RemoteImportJobStatus.Failed -> {
                    val message = snapshot.errorMessage ?: "Import failed."
                    updateLocalJob(job, ImportJobStatus.Failed, message)
                    error(message)
                }
                RemoteImportJobStatus.Rejected -> {
                    val message =
                        snapshot.errorMessage
                            ?: "This file does not look like a training log."
                    updateLocalJob(job, ImportJobStatus.Failed, message)
                    throw UnsupportedImportFileException(message)
                }
                RemoteImportJobStatus.Ready -> Unit
                RemoteImportJobStatus.PendingAgent,
                RemoteImportJobStatus.AgentRunning,
                -> error("Import ended in unexpected state: ${snapshot.status}")
            }

            val conversionPath =
                snapshot.conversionStoragePath
                    ?: error("Import ready but conversion path missing.")

            val payload = historicalImportPort.downloadConversion(conversionPath).getOrThrow()
            val (sessions, sets) = ImportedHistoryJsonParser.parse(payload)
            updateLocalJob(job, ImportJobStatus.Completed)
            return ImportedHistoryChunk(
                sourceLabel = file.fileName,
                sessions = sessions,
                sets = sets,
            )
        } catch (error: Throwable) {
            updateLocalJob(
                job,
                ImportJobStatus.Failed,
                error.message ?: "Import failed.",
            )
            throw error
        }
    }

    private suspend fun pollUntilTerminal(
        jobId: com.stratum.domain.importing.identity.ImportJobId,
        fileIndex: Int,
        fileCount: Int,
        onProgress: suspend (ImportProgress) -> Unit,
    ): Result<com.stratum.domain.importing.ImportJobSnapshot> {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var elapsedMs = 0L
        var interval = INITIAL_POLL_INTERVAL_MS
        while (System.currentTimeMillis() < deadline) {
            val snapshot = historicalImportPort.pollJobStatus(jobId).getOrThrow()
            snapshot.progressPhase?.let { phase ->
                onProgress(
                    ImportProgress(
                        phase = phase,
                        detail = snapshot.progressMessage,
                        fileIndex = fileIndex,
                        fileCount = fileCount,
                    ),
                )
            } ?: snapshot.progressMessage?.let { detail ->
                onProgress(
                    ImportProgress(
                        phase = ImportProgressPhase.ReadingData,
                        detail = detail,
                        fileIndex = fileIndex,
                        fileCount = fileCount,
                    ),
                )
            }
            when (snapshot.status) {
                RemoteImportJobStatus.Ready,
                RemoteImportJobStatus.Failed,
                RemoteImportJobStatus.Rejected,
                -> return Result.success(snapshot)
                RemoteImportJobStatus.PendingAgent,
                RemoteImportJobStatus.AgentRunning,
                -> {
                    delay(interval.milliseconds)
                    elapsedMs += interval
                    if (elapsedMs > BACKOFF_AFTER_MS) {
                        interval = SLOW_POLL_INTERVAL_MS
                    }
                }
            }
        }
        return Result.failure(IllegalStateException("Import timed out."))
    }

    private suspend fun updateLocalJob(
        job: ImportJob,
        status: ImportJobStatus,
        errorMessage: String? = null,
    ) {
        importJobRepository.updateJob(
            job.copy(
                status = status,
                errorMessage = errorMessage,
                updatedAt = Instant.now(),
            ),
        )
    }

    companion object {
        private const val TAG = "StratumImport"
        private const val INITIAL_POLL_INTERVAL_MS = 1_500L
        private const val SLOW_POLL_INTERVAL_MS = 3_000L
        private const val BACKOFF_AFTER_MS = 30_000L
        private const val POLL_TIMEOUT_MS = 15 * 60 * 1_000L
    }
}
