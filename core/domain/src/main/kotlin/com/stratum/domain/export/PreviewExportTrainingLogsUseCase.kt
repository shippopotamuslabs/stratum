package com.stratum.domain.export

import com.stratum.domain.training.TrainingLogRepository
import java.time.Instant
import javax.inject.Inject

class PreviewExportTrainingLogsUseCase(
    private val trainingLogRepository: TrainingLogRepository,
    private val clock: () -> Instant,
) {
    /** Hilt entry: wall clock. Unit tests use the primary constructor with a fixed clock. */
    @Inject
    constructor(
        trainingLogRepository: TrainingLogRepository,
    ) : this(trainingLogRepository, { Instant.now() })

    suspend operator fun invoke(range: ExportLogRange): ExportLogPreview {
        val now = clock()
        // Invalid custom range: treat as empty preview rather than throwing for UI.
        val bounds =
            try {
                ExportRangeBounds.resolve(range, now)
            } catch (_: IllegalArgumentException) {
                return ExportLogPreview(sessionCount = 0, setCount = 0)
            }
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
        val setCount = allSets.count { it.sessionId in sessionIds }
        return ExportLogPreview(
            sessionCount = sessions.size,
            setCount = setCount,
        )
    }
}
