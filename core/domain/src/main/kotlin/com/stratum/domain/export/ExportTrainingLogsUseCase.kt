package com.stratum.domain.export

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSession
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ExportTrainingLogsUseCase(
    private val trainingLogRepository: TrainingLogRepository,
    private val clock: () -> Instant,
    private val loggingPort: LoggingPort,
) {
    /** Hilt entry: wall clock. Unit tests use the primary constructor with a fixed clock. */
    @Inject
    constructor(
        trainingLogRepository: TrainingLogRepository,
        loggingPort: LoggingPort,
    ) : this(trainingLogRepository, { Instant.now() }, loggingPort)

    /**
     * Reads sessions/sets in [range], maps to Stratum log envelope with stable ids,
     * serializes pure JSON bytes. Empty selection → failure (no empty file).
     */
    suspend operator fun invoke(range: ExportLogRange): Result<ExportFileResult> {
        val now = clock()
        val bounds =
            ExportRangeBounds.resolve(range, now)
                .getOrElse { return Result.failure(it) }
        val (sessions, sets) =
            runCatchingLogged(loggingPort, TAG, "Failed to read training logs") {
                filterLogs(bounds)
            }.getOrElse { return Result.failure(it) }
        if (sessions.isEmpty() || sets.isEmpty()) {
            return Result.failure(IllegalArgumentException("No training logs in the selected range."))
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to export training logs") {
            val json =
                StratumExportJson.encodeLogsEnvelope(
                    exportedAt = now,
                    range = range,
                    rangeBounds = bounds,
                    sessions = sessions,
                    sets = sets,
                )
            ExportFileResult(
                bytes = json.encodeToByteArray(),
                fileName = exportFileName(now),
                mimeType = MIME_JSON,
            )
        }
    }

    private suspend fun filterLogs(
        bounds: Pair<Instant, Instant>?,
    ): Pair<List<TrainingSession>, List<LoggedSet>> {
        val allSessions = trainingLogRepository.getAllSessions()
        val allSets = trainingLogRepository.getAllSets()
        val sessions =
            if (bounds == null) {
                allSessions
            } else {
                val (start, endExclusive) = bounds
                allSessions.filter { session ->
                    !session.startedAt.isBefore(start) && session.startedAt.isBefore(endExclusive)
                }
            }
        val sessionIds = sessions.map { it.id }.toSet()
        val sets = allSets.filter { it.sessionId in sessionIds }
        return sessions to sets
    }

    private fun exportFileName(now: Instant): String {
        val stamp = FILE_STAMP.format(now.atZone(ZoneOffset.UTC))
        return "stratum-logs-$stamp.json"
    }

    private companion object {
        const val MIME_JSON = "application/json"
        const val TAG = "ExportTrainingLogs"
        val FILE_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
