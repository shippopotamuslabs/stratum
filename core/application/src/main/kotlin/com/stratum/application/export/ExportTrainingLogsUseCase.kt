package com.stratum.application.export

import com.stratum.application.diagnostics.OperationalDiagnostic
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
 * [InvalidRange] and [NoLogsInRange] are expected caller outcomes and carry
 * no diagnostics; an operational read/encode failure rides the
 * [PersistenceFailed] branch as an [OperationalDiagnostic] and is drained
 * exactly once at the presentation boundary. Coroutine cancellation is not
 * a branch: it propagates.
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
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : ExportTrainingLogsResult
}

class ExportTrainingLogsUseCase(
    private val trainingLogRepository: TrainingLogRepository,
    private val clock: () -> Instant,
) {
    /** Hilt entry: wall clock. Unit tests use the primary constructor with a fixed clock. */
    @Inject
    constructor(
        trainingLogRepository: TrainingLogRepository,
    ) : this(trainingLogRepository, { Instant.now() })

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
                return ExportTrainingLogsResult.PersistenceFailed(
                    diagnostics = listOf(
                        OperationalDiagnostic(
                            tag = TAG,
                            message = "Failed to read training logs: ${error.message}",
                            cause = error,
                        ),
                    ),
                )
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
            ExportTrainingLogsResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to export training logs: ${error.message}",
                        cause = error,
                    ),
                ),
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
