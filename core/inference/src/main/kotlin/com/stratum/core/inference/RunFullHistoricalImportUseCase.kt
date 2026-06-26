package com.stratum.core.inference

import android.util.Log
import com.stratum.domain.importing.ExtractTrainingInsightsSummary
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
import com.stratum.domain.importing.ImportedHistoryChunk
import com.stratum.domain.importing.MergeImportedHistoryUseCase
import com.stratum.domain.importing.PersistImportedHistoryUseCase
import com.stratum.domain.importing.RollbackHistoricalImportUseCase
import com.stratum.domain.importing.RemoteImportJobStatus
import com.stratum.domain.importing.StartHistoricalImportUseCase
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class RunFullHistoricalImportUseCase @Inject constructor(
    private val startHistoricalImport: StartHistoricalImportUseCase,
    private val historicalImportPort: HistoricalImportPort,
    private val persistImportedHistory: PersistImportedHistoryUseCase,
    private val rollbackHistoricalImport: RollbackHistoricalImportUseCase,
    private val importJobRepository: ImportJobRepository,
    private val mergeImportedHistory: MergeImportedHistoryUseCase,
) : HistoricalImportRunnerPort {
    override fun run(files: List<ImportFileInput>): Flow<HistoricalImportEvent> = flow {
        if (files.isEmpty()) {
            emit(HistoricalImportEvent.Failed("No files selected."))
            return@flow
        }
        val totalFiles = files.size
        val chunks = mutableListOf<ImportedHistoryChunk>()
        try {
            files.forEachIndexed { index, file ->
                val fileIndex = index + 1
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
            summary.liftResolution?.let(ImportLiftResolutionLogger::log)
            emit(HistoricalImportEvent.Progress(ImportProgress(ImportProgressPhase.Complete)))
            emit(HistoricalImportEvent.Completed(summary))
        } catch (error: Throwable) {
            rollbackHistoricalImport()
            val message = error.message ?: "Import failed."
            runCatching { Log.e(TAG, "Historical import failed: $message", error) }
            emit(HistoricalImportEvent.Failed(message))
        }
    }

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
