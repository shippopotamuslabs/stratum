package com.stratum.application.training

import com.stratum.domain.di.ApplicationScope
import com.stratum.application.healthconnect.HealthConnectSchedulerPort
import com.stratum.domain.logging.LoggingPort
import com.stratum.application.program.ProgramRepository
import com.stratum.application.response.ResponseModelingSchedulerPort
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.sessionsWithMaterializedAutoRests
import com.stratum.domain.response.RE_EXTRACT_MIN_COMPLETED_WORKOUTS

/**
 * Outcome of finalizing a training session (STR-243).
 *
 * The guard branches carry the typed facts the former constructed exceptions
 * carried ([SessionNotFound] / [SessionNotInProgress] with the session id and
 * actual status). The originating throwable of a failed finalize goes once to
 * the diagnostics channel ([LoggingPort.error]) at the use-case boundary; it
 * never crosses as a payload. Coroutine cancellation is not a branch: it
 * propagates.
 */
sealed interface FinalizeTrainingSessionResult {
    data class Success(val session: TrainingSession) : FinalizeTrainingSessionResult

    data class SessionNotFound(val sessionId: TrainingSessionId) : FinalizeTrainingSessionResult

    data class SessionNotInProgress(
        val sessionId: TrainingSessionId,
        val status: TrainingSessionStatus,
    ) : FinalizeTrainingSessionResult

    /** The finalize write failed (repository / store I/O). */
    data object PersistenceFailed : FinalizeTrainingSessionResult
}

class FinalizeTrainingSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val computePersonalRecords: ComputePersonalRecordsUseCase,
    private val localDerivedStages: LocalDerivedTrainingStages,
    private val loggingPort: LoggingPort,
    private val responseModelingScheduler: ResponseModelingSchedulerPort =
        NoOpResponseModelingScheduler,
    private val healthConnectScheduler: HealthConnectSchedulerPort =
        NoOpHealthConnectScheduler,
    // Process-lifetime scope (SupervisorJob) so the post-completion derived-data refresh outlives
    // the ViewModel that triggered completion. Defaulted to a Main.immediate scope so JVM tests
    // can construct the use case without Hilt. Stage work hops to Default inside localDerivedStages.
    @ApplicationScope private val refreshScope: CoroutineScope =
        CoroutineScope(Dispatchers.Main.immediate),
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): FinalizeTrainingSessionResult {
        // The session load sits inside the boundary try (C3 precedent): a throwing
        // load is logged once and returned as PersistenceFailed instead of escaping.
        return try {
            val session =
                trainingLogRepository.getSession(sessionId)
                    ?: return FinalizeTrainingSessionResult.SessionNotFound(sessionId)
            if (session.status != TrainingSessionStatus.IN_PROGRESS) {
                return FinalizeTrainingSessionResult.SessionNotInProgress(
                    session.id,
                    session.status,
                )
            }

            val completed = session.complete(at = Instant.now())

            val sessionSets = trainingLogRepository.getSetsForSession(sessionId)
            val computation = computePersonalRecords(sessionSets).getOrThrow()

            // Room unique index on (liftId, equipmentId, isPersonalRecord): only one
            // true-PR per (lift, equipment) pair. Demote previous PRs *before* promoting
            // session sets so the unique constraint is free.
            if (computation.oldPrSetsToUpdate.isNotEmpty()) {
                trainingLogRepository.updateSets(computation.oldPrSetsToUpdate)
            }
            trainingLogRepository.updateSets(computation.updatedIncomingSets)

            val sessionsToPersist = mutableListOf(completed)
            val cycleEffects =
                if (completed.kind == TrainingSessionKind.PROGRAM_WORKOUT) {
                    val program = programRepository.getProgram()
                    // Orphan / soft-unlinked sessions (program deleted) complete as ledger-only —
                    // do not advance the current active program's cycle pointer.
                    if (
                        program == null ||
                        completed.programId == null ||
                        completed.programId != program.id
                    ) {
                        null
                    } else {
                        val baseSessions =
                            trainingLogRepository.getAllSessions()
                                .filter { it.id != session.id } + completed
                        val (ledger, autoRests) =
                            sessionsWithMaterializedAutoRests(
                                program = program,
                                sessions = baseSessions,
                                now = completed.completedAt,
                            )
                        sessionsToPersist += autoRests
                        program.advanceCycle(ledger)
                    }
                } else {
                    null
                }

            trainingLogRepository.persistSessionsWithCycleEffects(
                sessions = sessionsToPersist,
                cyclePointerUpdate = cycleEffects?.pointerUpdate,
                cycleCompletedEvent = cycleEffects?.cycleCompletedEvent,
            )

            // The session + cycle effects are durable above. The derived-data refresh is
            // fire-and-forget on the application scope so completion returns (and the UI
            // navigates) immediately; the dashboard tables refresh behind the user. Failures are
            // swallowed inside LocalDerivedTrainingStages (LogAndContinue).
            refreshScope.launch {
                localDerivedStages.run(
                    includeInsights = true,
                    failPolicy = LocalDerivedTrainingStages.FailPolicy.LogAndContinue,
                    logTag = TAG,
                )
            }

            // Schedule heavy dose-response re-extract (WorkManager). Non-blocking: only enqueues
            // unique work; the worker enforces RE_EXTRACT_MIN_COMPLETED_WORKOUTS and does not
            // delay this return.
            responseModelingScheduler.enqueueRefresh()

            // Best-effort Health Connect enqueue — never fail finalize after Room persist.
            try {
                healthConnectScheduler.enqueueSync(completed.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                loggingPort.error(
                    TAG,
                    "Health Connect sync enqueue failed for ${completed.id.value}",
                    error,
                )
            }

            FinalizeTrainingSessionResult.Success(completed)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(
                TAG,
                "Failed to finalize training session ${sessionId.value}: ${t.message}",
                t,
            )
            FinalizeTrainingSessionResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "FinalizeTrainingSession"
    }
}

/** Default no-op for JVM unit tests that construct finalize without Hilt. */
private object NoOpResponseModelingScheduler : ResponseModelingSchedulerPort {
    override fun enqueueRefresh() = Unit

    override fun enqueueForceExtract(replaceExisting: Boolean) = Unit
}

/** Default no-op for JVM unit tests that construct finalize without Hilt. */
private object NoOpHealthConnectScheduler : HealthConnectSchedulerPort {
    override suspend fun enqueueSync(sessionId: TrainingSessionId) = Unit
}
