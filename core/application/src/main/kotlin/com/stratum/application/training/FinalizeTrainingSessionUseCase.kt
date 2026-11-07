package com.stratum.application.training

import com.stratum.domain.di.ApplicationScope
import com.stratum.application.healthconnect.HealthConnectSchedulerPort
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.program.ProgramRepository
import com.stratum.application.response.ResponseModelingSchedulerPort
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * actual status). The originating throwable of a failed finalize rides the
 * [PersistenceFailed] branch as an [OperationalDiagnostic] (legacy tag +
 * rendered message, real throwable as cause); the presentation boundary
 * drains it exactly once through its module's DiagnosticDrain. A best-effort
 * Health Connect enqueue failure after the durable finalize rides
 * [Success.diagnostics] the same way. Coroutine cancellation is not a branch:
 * it propagates.
 */
sealed interface FinalizeTrainingSessionResult {
    data class Success(
        val session: TrainingSession,
        val diagnostics: List<OperationalDiagnostic> = emptyList(),
    ) : FinalizeTrainingSessionResult

    data class SessionNotFound(val sessionId: TrainingSessionId) : FinalizeTrainingSessionResult

    data class SessionNotInProgress(
        val sessionId: TrainingSessionId,
        val status: TrainingSessionStatus,
    ) : FinalizeTrainingSessionResult

    /** The finalize write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : FinalizeTrainingSessionResult
}

class FinalizeTrainingSessionUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val programRepository: ProgramRepository,
    private val computePersonalRecords: ComputePersonalRecordsUseCase,
    private val localDerivedStages: LocalDerivedTrainingStages,
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
    /**
     * @param onRefreshDiagnostics boundary-supplied diagnostics callback for the
     * post-finalize derived-data refresh (CollectLatestRestarting precedent: a
     * boundary-owned callback, NOT a logging capability in application — the
     * caller drains the delivered diagnostics through its own DiagnosticDrain).
     * No default: every caller wires it explicitly. See the shared
     * [launchDerivedRefresh] launcher for the exact delivery convention.
     */
    suspend operator fun invoke(
        sessionId: TrainingSessionId,
        onRefreshDiagnostics: (List<OperationalDiagnostic>) -> Unit,
    ): FinalizeTrainingSessionResult {
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
            // navigates) immediately; the dashboard tables refresh behind the user.
            // STR-240 chunk 7: the refresh's stage-failure diagnostics are
            // delivered via [onRefreshDiagnostics] — see the shared
            // [launchDerivedRefresh] launcher.
            refreshScope.launchDerivedRefresh(localDerivedStages, TAG, onRefreshDiagnostics)

            // Schedule heavy dose-response re-extract (WorkManager). Non-blocking: only enqueues
            // unique work; the worker enforces RE_EXTRACT_MIN_COMPLETED_WORKOUTS and does not
            // delay this return.
            responseModelingScheduler.enqueueRefresh()

            // Best-effort Health Connect enqueue — never fail finalize after Room persist.
            val enqueueDiagnostics =
                try {
                    healthConnectScheduler.enqueueSync(completed.id)
                    emptyList()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    listOf(
                        OperationalDiagnostic(
                            tag = TAG,
                            message = "Health Connect sync enqueue failed for ${completed.id.value}",
                            cause = error,
                        ),
                    )
                }

            FinalizeTrainingSessionResult.Success(completed, diagnostics = enqueueDiagnostics)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            FinalizeTrainingSessionResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to finalize training session ${sessionId.value}: ${t.message}",
                        cause = t,
                    ),
                ),
            )
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
