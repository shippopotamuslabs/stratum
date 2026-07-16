package com.stratum.domain.export

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
) {
    /** Hilt entry: wall clock. Unit tests use the primary constructor with a fixed clock. */
    @Inject
    constructor(
        trainingLogRepository: TrainingLogRepository,
    ) : this(trainingLogRepository, { Instant.now() })

    /**
     * Reads sessions/sets in [range], maps to Stratum log envelope with stable ids,
     * serializes pure JSON bytes. Empty selection → failure (no empty file).
     */
    suspend operator fun invoke(range: ExportLogRange): Result<ExportFileResult> =
        runCatching {
            val now = clock()
            val bounds = ExportRangeBounds.resolve(range, now)
            val (sessions, sets) = filterLogs(bounds)
            require(sessions.isNotEmpty() && sets.isNotEmpty()) {
                "No training logs in the selected range."
            }
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
        val FILE_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
