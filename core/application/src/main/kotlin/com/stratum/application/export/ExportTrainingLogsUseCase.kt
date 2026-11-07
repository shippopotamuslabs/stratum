package com.stratum.application.export

import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.training.LoggedSet
import com.stratum.application.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.export.ExportFileResult
import com.stratum.domain.export.ExportLogRange
import com.stratum.domain.export.ExportRangeBounds
import com.stratum.domain.export.ExportRangeBoundsResult
import com.stratum.domain.export.StratumExportJson

/**
 * Outcome of [ExportTrainingLogsUseCase] (STR-243).
 *
 * [InvalidRange] and [NoLogsInRange] are expected caller outcomes and are not
 * logged; an operational read/encode failure is logged once via
 * [LoggingPort.error] at the use-case boundary and surfaces as
 * [PersistenceFailed]. Coroutine cancellation is not a branch: it propagates.
 */
sealed interface ExportTrainingLogsResult {
    data class Success(val file: ExportFileResult) : ExportTrainingLogsResult

    /** The custom range's start date falls after its end date. */
    data class InvalidRange(
        val startDate: LocalDate,
        val endDateInclusive: LocalDate,
    ) : ExportTrainingLogsResult

    /** The selected range contains no training logs; no file is produced. */
    data object NoLogsInRange : ExportTrainingLogsResult

    /** The export failed (repository / store I/O). */
    data object PersistenceFailed : ExportTrainingLogsResult
}

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
     * serializes pure JSON bytes. Empty selection → [ExportTrainingLogsResult.NoLogsInRange]
     * (no empty file).
     */
    suspend operator fun invoke(range: ExportLogRange): ExportTrainingLogsResult {
        val now = clock()
        val bounds =
            when (val resolution = ExportRangeBounds.resolve(range, now)) {
                is ExportRangeBoundsResult.StartAfterEnd ->
                    return ExportTrainingLogsResult.InvalidRange(
                        resolution.startDate,
                        resolution.endDateInclusive,
                    )
                is ExportRangeBoundsResult.Resolved -> resolution.bounds
            }
        val (sessions, sets) =
            try {
                filterLogs(bounds)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                loggingPort.error(TAG, "Failed to read training logs: ${error.message}", error)
                return ExportTrainingLogsResult.PersistenceFailed
            }
        if (sessions.isEmpty() || sets.isEmpty()) {
            return ExportTrainingLogsResult.NoLogsInRange
        }
        return try {
            val json =
                StratumExportJson.encodeLogsEnvelope(
                    exportedAt = now,
                    range = range,
                    rangeBounds = bounds,
                    sessions = sessions,
                    sets = sets,
                )
            ExportTrainingLogsResult.Success(
                ExportFileResult(
                    bytes = json.encodeToByteArray(),
                    fileName = exportFileName(now),
                    mimeType = MIME_JSON,
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Failed to export training logs: ${error.message}", error)
            ExportTrainingLogsResult.PersistenceFailed
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
