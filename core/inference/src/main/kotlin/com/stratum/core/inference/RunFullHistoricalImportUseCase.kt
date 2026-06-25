package com.stratum.core.inference

import com.stratum.domain.importing.ExtractTrainingInsightsSummary
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.HistoricalImportPort
import com.stratum.domain.importing.HistoricalImportRunnerPort
import com.stratum.domain.importing.ImportJob
import com.stratum.domain.importing.ImportJobRepository
import com.stratum.domain.importing.ImportCatalogProgressStep
import com.stratum.domain.importing.ImportJobStatus
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
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
) : HistoricalImportRunnerPort {
    override fun run(fileBytes: ByteArray, fileName: String): Flow<HistoricalImportEvent> = flow {
        val job = startHistoricalImport().getOrElse { error ->
            emit(HistoricalImportEvent.Failed(error.message ?: "Could not start import job."))
            return@flow
        }
        try {
            emit(HistoricalImportEvent.Progress(ImportProgress(ImportProgressPhase.Uploading)))
            val refs = historicalImportPort.uploadRawAndSample(job.id, fileBytes, fileName).getOrThrow()
            updateLocalJob(job, ImportJobStatus.AgentRunning)

            historicalImportPort.startImport(job.id, refs).getOrThrow()

            val snapshot = pollUntilTerminal(job.id) { progress ->
                emit(HistoricalImportEvent.Progress(progress))
            }.getOrThrow()

            when (snapshot.status) {
                RemoteImportJobStatus.Failed -> {
                    val message = snapshot.errorMessage ?: "Import failed."
                    updateLocalJob(job, ImportJobStatus.Failed, message)
                    emit(HistoricalImportEvent.Failed(message))
                    return@flow
                }
                RemoteImportJobStatus.Ready -> Unit
                RemoteImportJobStatus.PendingAgent,
                RemoteImportJobStatus.AgentRunning,
                -> error("Import ended in unexpected state: ${snapshot.status}")
            }

            val conversionPath = snapshot.conversionStoragePath
                ?: error("Import ready but conversion path missing.")
            emit(
                HistoricalImportEvent.Progress(
                    ImportProgress(
                        phase = ImportProgressPhase.EnrichingCustomLifts,
                        catalogStep = ImportCatalogProgressStep.LoadingConvertedData,
                    ),
                ),
            )
            updateLocalJob(job, ImportJobStatus.ExtractingInsights)

            val payload = historicalImportPort.downloadConversion(conversionPath).getOrThrow()
            val (sessions, sets) = ImportedHistoryJsonParser.parse(payload)
            val summary =
                persistImportedHistory(sessions, sets) { progress ->
                    emit(HistoricalImportEvent.Progress(progress))
                }.getOrThrow()
            summary.liftResolution?.let(ImportLiftResolutionLogger::log)
            updateLocalJob(job, ImportJobStatus.Completed)
            emit(HistoricalImportEvent.Progress(ImportProgress(ImportProgressPhase.Complete)))
            emit(HistoricalImportEvent.Completed(summary))
        } catch (error: Throwable) {
            rollbackHistoricalImport()
            val message = error.message ?: "Import failed."
            updateLocalJob(job, ImportJobStatus.Failed, message)
            emit(HistoricalImportEvent.Failed(message))
        }
    }

    private suspend fun pollUntilTerminal(
        jobId: com.stratum.domain.importing.identity.ImportJobId,
        onProgress: suspend (ImportProgress) -> Unit,
    ): Result<com.stratum.domain.importing.ImportJobSnapshot> {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var elapsedMs = 0L
        var interval = INITIAL_POLL_INTERVAL_MS
        while (System.currentTimeMillis() < deadline) {
            val snapshot = historicalImportPort.pollJobStatus(jobId).getOrThrow()
            snapshot.progressPhase?.let { phase ->
                onProgress(ImportProgress(phase, snapshot.progressMessage))
            } ?: snapshot.progressMessage?.let { detail ->
                onProgress(ImportProgress(ImportProgressPhase.ReadingData, detail))
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
        private const val INITIAL_POLL_INTERVAL_MS = 1_500L
        private const val SLOW_POLL_INTERVAL_MS = 3_000L
        private const val BACKOFF_AFTER_MS = 30_000L
        private const val POLL_TIMEOUT_MS = 15 * 60 * 1_000L
    }
}
