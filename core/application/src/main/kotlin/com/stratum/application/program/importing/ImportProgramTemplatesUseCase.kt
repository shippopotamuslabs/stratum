package com.stratum.application.program.importing

import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.application.importing.toOperationalDiagnostic
import com.stratum.domain.di.DefaultDispatcher
import com.stratum.application.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.application.onboarding.UserTrainingProfileRepository
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.identity.ProgramId
import com.stratum.application.program.importing.NormalizedProgramTemplateJson.parseAll
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.application.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.stratum.domain.program.importing.ProgramImportActivation
import com.stratum.application.program.importing.ProgramImportDedupe
import com.stratum.domain.program.importing.ProgramTemplateImportJobId
import com.stratum.domain.program.importing.ProgramImportRollbackDiagnostic
import com.stratum.domain.program.importing.ProgramTemplateImportOptions
import com.stratum.domain.program.importing.ProgramTemplateImportPhase
import com.stratum.domain.program.importing.ProgramTemplateImportResult
import com.stratum.domain.program.importing.ProgramTemplateJobSnapshot
import com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout

typealias ProgramTemplateImportProgressCallback = suspend (ProgramTemplateImportPhase) -> Unit

/**
 * The server's rejection of the uploaded file as not a workout program. Since
 * STR-243 the rejection crosses as [ProgramTemplateImportOutcome.Rejected];
 * this type is constructed only at presentation boundaries that need an
 * exception value for a log record of the rejection.
 */
class ProgramTemplateRejectedException(message: String) : Exception(message)

/**
 * Outcome of [ImportProgramTemplatesUseCase] (STR-243).
 *
 * [Success] carries the import result plus any non-fatal degradation diagnostics
 * (§10): a lift-name embedding batch failure during template resolution degrades
 * matching to pure BM25 but does not fail the import, so its real cause rides
 * [Success.diagnostics] as an [OperationalDiagnostic] for the boundary to record
 * exactly once (STR-240 chunk 8b). The terminal server statuses are typed
 * branches carrying the facts consumers branch on: [Rejected] and [Failed]
 * carry the server-provided message (or the use case's default copy),
 * [ReadyWithoutPath] is a Ready job with no template to download,
 * [NonTerminal] carries the status the job ended in, and [TimedOut] is the
 * poll deadline expiring. [PersistFailed] is the phase-2 persist failure: it
 * carries the primary failure's message (the user-facing copy source, as when
 * the primary crossed as a Throwable) and the STR-256 rollback diagnostics to
 * drain at the boundary. [OperationalFailed] covers the remaining operational
 * failures (upload / poll / download / parse / pre-persist reads) and carries
 * them as [OperationalDiagnostic]s (legacy tag + rendered message, real
 * throwable as cause) for the boundary to drain exactly once (STR-237
 * Route (i)). Coroutine cancellation — including a
 * cancellation carried as a port [Result] failure value (STR-241 F6) — is not
 * a branch: it propagates as a throw, with any rollback evidence attached
 * (STR-256).
 */
sealed interface ProgramTemplateImportOutcome {
    data class Success(
        val result: ProgramTemplateImportResult,
        val diagnostics: List<OperationalDiagnostic> = emptyList(),
    ) : ProgramTemplateImportOutcome

    data class Rejected(val message: String) : ProgramTemplateImportOutcome

    data class Failed(val message: String) : ProgramTemplateImportOutcome

    data object ReadyWithoutPath : ProgramTemplateImportOutcome

    data class NonTerminal(val status: RemoteProgramTemplateJobStatus) : ProgramTemplateImportOutcome

    data object TimedOut : ProgramTemplateImportOutcome

    data class PersistFailed(
        val message: String?,
        val diagnostics: List<ProgramImportRollbackDiagnostic>,
        /**
         * The degraded resolve's embedding diagnostic when resolution was
         * degraded before the persist failed (Fix 7): carried here so the
         * boundary records it exactly once on this path too, converted via
         * the canonical [com.stratum.application.importing.toOperationalDiagnostic]
         * rendering — the [Success] path's drain is unchanged and never runs
         * for the same import. Empty when the resolve was not degraded.
         */
        val embeddingDiagnostics: List<OperationalDiagnostic> = emptyList(),
    ) : ProgramTemplateImportOutcome

    data class OperationalFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : ProgramTemplateImportOutcome
}

/** Marker for the poll deadline inside [ImportProgramTemplatesUseCase]; never crosses the use case. */
private class ImportPollTimeoutException : Exception("Timed out waiting for import to finish.")

/**
 * Phase-2 persist failure where at least one rollback delete also failed (STR-256).
 *
 * Internal carrier inside [ImportProgramTemplatesUseCase]: at the use case's edge it is
 * mapped onto [ProgramTemplateImportOutcome.PersistFailed], which carries the same
 * [message] and [diagnostics]. The rollback shape stays fire-and-forget per id: every
 * persisted id still gets its delete attempt. When one or more deletes fail, the evidence
 * rides this failure object instead of being discarded: [message] is exactly the primary
 * persist failure's message and [cause] is the primary itself, so user-facing copy derived
 * from the failure is unchanged; [diagnostics] carries each failed delete with its program
 * id. Each delete cause is also attached via [addSuppressed] so boundaries that log the
 * failure object emit the full evidence in that single emission. Thrown only when at least
 * one rollback failed — a clean rollback rethrows the primary bare. A
 * [CancellationException] primary is never wrapped: it is rethrown as-is with the rollback
 * causes suppressed on it.
 */
class ProgramImportRollbackException(
    cause: Throwable,
    val diagnostics: List<ProgramImportRollbackDiagnostic>,
) : Exception(cause.message, cause) {
    init {
        require(diagnostics.isNotEmpty()) {
            "ProgramImportRollbackException requires at least one rollback diagnostic."
        }
        diagnostics.forEach { addSuppressed(it.cause) }
    }
}

/**
 * Core program-import pipeline: upload → poll → download → parse (single or multi) →
 * resolve lifts → persist per [options] → optional CompleteOnboardingUseCase.
 */
open class ImportProgramTemplatesUseCase @Inject constructor(
    private val importPort: ProgramTemplateImportPort,
    private val resolver: ResolveProgramTemplateLiftsUseCase,
    private val repository: ProgramRepository,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val profileRepository: UserTrainingProfileRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
    @DefaultDispatcher private val importDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    open suspend operator fun invoke(
        bytes: ByteArray,
        fileName: String,
        options: ProgramTemplateImportOptions,
        onProgress: ProgramTemplateImportProgressCallback = {},
    ): ProgramTemplateImportOutcome {
        // Rollback diagnostics collected inside; re-attached below when the surfaced
        // failure is a cancellation the platform normalized (see below).
        val rollbackFailures = mutableListOf<ProgramImportRollbackDiagnostic>()
        return try {
            withContext(importDispatcher) {
            onProgress(ProgramTemplateImportPhase.Uploading)
            val jobId = ProgramTemplateImportJobId(UUID.randomUUID().toString())
            importPort.uploadAndStart(jobId, bytes, fileName)
                .getOrElseRethrowingCancellation { error ->
                    return@withContext ProgramTemplateImportOutcome.OperationalFailed(
                        operationalDiagnostics(error),
                    )
                }

            // A poll failure (or the deadline) throws out of pollUntilTerminal and
            // surfaces through the envelope below as OperationalFailed / TimedOut.
            val snapshot = pollUntilTerminal(jobId, onProgress)
            // Expected terminal outcomes are returned as outcome branches, never thrown
            // (STR-241 constructed them as Result payloads; STR-243 makes them branches).
            when (snapshot.status) {
                RemoteProgramTemplateJobStatus.Rejected ->
                    return@withContext ProgramTemplateImportOutcome.Rejected(
                        snapshot.errorMessage ?: "This file does not look like a workout program.",
                    )
                RemoteProgramTemplateJobStatus.Failed ->
                    return@withContext ProgramTemplateImportOutcome.Failed(
                        snapshot.errorMessage ?: "Import failed.",
                    )
                RemoteProgramTemplateJobStatus.Ready -> Unit
                else ->
                    return@withContext ProgramTemplateImportOutcome.NonTerminal(snapshot.status)
            }

            val templateStoragePath =
                snapshot.programTemplateStoragePath
                    ?: return@withContext ProgramTemplateImportOutcome.ReadyWithoutPath

            onProgress(ProgramTemplateImportPhase.MatchingCatalog)
            val templateJson = importPort.downloadProgramTemplate(templateStoragePath)
                .getOrElseRethrowingCancellation { error ->
                    return@withContext ProgramTemplateImportOutcome.OperationalFailed(
                        operationalDiagnostics(error),
                    )
                }
            val parsed = parseAll(templateJson)
            require(parsed.isNotEmpty()) { "Imported template has no programs" }

            val existingAtStart = repository.getAllUserPrograms()
            // Phase 1: resolve + dedupe only — no repository writes yet.
            data class PreparedImport(
                val program: com.stratum.domain.program.Program,
                val activate: Boolean,
            )
            val prepared = mutableListOf<PreparedImport>()
            val resolvedThisBatch = mutableListOf<com.stratum.domain.program.Program>()
            var skipped = 0
            var activatedFirst = false
            // STR-240 chunk 8b: the first embedding-batch failure in this import is
            // carried on the Success outcome (below) for the boundary to record
            // exactly once per import; later templates in the same file degrade the
            // same way and do not re-carry it.
            var embeddingDiagnostic: OperationalDiagnostic? = null

            for (item in parsed) {
                require(item.template.workouts.isNotEmpty()) { "Imported template has no workouts" }
                val resolved = resolver.invoke(item.template)
                embeddingDiagnostic = firstEmbeddingDiagnostic(embeddingDiagnostic)
                // Prefer stable export id when present. Resolve mints a random Program.id and
                // stamps it on every Workout.programId — rebind the whole hierarchy so Room FKs
                // (workouts.program_id → programs.id) stay consistent.
                val programId =
                    item.exportedId?.let { ProgramId(it) } ?: resolved.id
                val candidate = rebindProgramId(resolved, programId)
                val known = existingAtStart + resolvedThisBatch
                // Names after resolve so newly created custom lifts are included.
                val liftNames = resolver.catalogNamesById()
                if (ProgramImportDedupe.shouldSkip(candidate, known, item.exportedId, liftNames)) {
                    skipped++
                    continue
                }
                val activate =
                    when (options.activation) {
                        ProgramImportActivation.LibraryOnly -> false
                        ProgramImportActivation.ActivateAsActive -> {
                            if (!activatedFirst) {
                                activatedFirst = true
                                true
                            } else {
                                false
                            }
                        }
                    }
                prepared += PreparedImport(program = candidate, activate = activate)
                resolvedThisBatch += candidate
            }

            // Phase 2: persist. On any failure, delete every program saved in this batch.
            val importedIds = mutableListOf<ProgramId>()
            try {
                for (item in prepared) {
                    val programId = item.program.id
                    if (item.activate) {
                        repository.saveProgram(item.program.activate())
                    } else {
                        repository.saveToLibrary(item.program.deactivate())
                    }
                    if (options.appendProgramRegeneratedEvent) {
                        programChangeEventRepository.append(
                            ProgramChangeEvent(
                                id = ProgramChangeEventId(UUID.randomUUID().toString()),
                                programId = programId,
                                eventType = ProgramChangeEventType.PROGRAM_REGENERATED,
                                occurredAt = Instant.now(),
                                payload = ProgramChangeEventPayload.ProgramRegenerated,
                            ),
                        )
                    }
                    importedIds += programId
                }
            } catch (error: Throwable) {
                val thrown = resolveRollbackThrow(error, importedIds, rollbackFailures)
                // A cancellation primary propagates as a throw (STR-256); any other
                // primary surfaces as the PersistFailed branch, with the rollback
                // wrapper's diagnostics when deletes also failed.
                if (thrown is CancellationException) throw thrown
                return@withContext ProgramTemplateImportOutcome.PersistFailed(
                    message = thrown.message,
                    diagnostics =
                        (thrown as? ProgramImportRollbackException)?.diagnostics ?: emptyList(),
                    embeddingDiagnostics = listOfNotNull(embeddingDiagnostic),
                )
            }

            if (options.completeOnboarding && importedIds.isNotEmpty()) {
                val profile = profileRepository.observeProfile().first() ?: UserTrainingProfile.Empty
                completeOnboarding(profile)
            }
            onProgress(ProgramTemplateImportPhase.Complete)
            ProgramTemplateImportOutcome.Success(
                ProgramTemplateImportResult(
                    importedProgramIds = importedIds,
                    skippedProgramCount = skipped,
                ),
                diagnostics = listOfNotNull(embeddingDiagnostic),
            )
            }
        } catch (cancelled: CancellationException) {
            // A cancellation propagates as a throw with the rollback evidence
            // re-attached (STR-256, see [withRollbackEvidence]).
            throw cancelled.withRollbackEvidence(rollbackFailures)
        } catch (_: ImportPollTimeoutException) {
            // Flatten: the block returns expected failures as outcome branches; the
            // envelope's own operational throws surface as OperationalFailed.
            ProgramTemplateImportOutcome.TimedOut
        } catch (error: Throwable) {
            ProgramTemplateImportOutcome.OperationalFailed(operationalDiagnostics(error))
        }
    }

    /**
     * Builds the [ProgramTemplateImportOutcome.OperationalFailed] diagnostics
     * for an operational failure (STR-237 Route (i)): the legacy boundary
     * record (tag + rendered message, real throwable as cause) rides the
     * branch; consumers drain it exactly once and do not log it again.
     */
    private fun operationalDiagnostics(error: Throwable): List<OperationalDiagnostic> =
        listOf(
            OperationalDiagnostic(
                tag = TAG,
                message = "Program import failed: ${error.message}",
                cause = error,
            ),
        )

    /**
     * Keeps the first embedding-batch failure seen in this import (STR-240 chunk
     * 8b): reads the resolver's post-invoke diagnostic while [current] is still
     * null, and returns [current] unchanged once one is carried — one record per
     * import, however many templates the file held. The diagnostic is rendered
     * through the canonical [toOperationalDiagnostic] conversion with this
     * flow's tag — the same conversion the historical-import boundary renders
     * for the same failure, so both flows' records are byte-identical apart
     * from the tag. The two flows are distinct invocations — a historical
     * import records its own failure on its summary channel and a template
     * import records its own here — so no failure is recorded twice across
     * flows.
     */
    private fun firstEmbeddingDiagnostic(
        current: OperationalDiagnostic?,
    ): OperationalDiagnostic? =
        current ?: resolver.embeddingBatchFailureDiagnostic()?.toOperationalDiagnostic(TAG)

    /**
     * Re-attaches rollback evidence to a surfaced cancellation (STR-256): coroutine
     * machinery may normalize the rethrown instance (message-only copy) once the block has
     * suspended, so the in-catch attach in [resolveRollbackThrow] alone would be silently
     * dropped in practice. Non-cancellations never reach this catch.
     */
    private fun CancellationException.withRollbackEvidence(
        rollbackFailures: List<ProgramImportRollbackDiagnostic>,
    ): CancellationException {
        rollbackFailures.forEach { diagnostic ->
            if (suppressed.none { it === diagnostic.cause }) {
                addSuppressed(diagnostic.cause)
            }
        }
        return this
    }

    /**
     * Decides what the Phase-2 rollback catch propagates (STR-256): every persisted id gets
     * its delete attempt via [collectRollbackFailures], then a [CancellationException] primary
     * is returned as-is with the rollback causes attached (never wrapped), a persist failure
     * with failed deletes becomes a [ProgramImportRollbackException], and a clean rollback
     * returns the primary bare. The in-catch attach is repeated at the invoke boundary catch because
     * coroutine machinery may normalize the rethrown instance (message-only copy) once the
     * block has suspended — the in-catch attach alone would be silently dropped in practice.
     */
    private suspend fun resolveRollbackThrow(
        error: Throwable,
        importedIds: List<ProgramId>,
        rollbackFailures: MutableList<ProgramImportRollbackDiagnostic>,
    ): Throwable {
        rollbackFailures += collectRollbackFailures(importedIds)
        if (error is CancellationException) {
            rollbackFailures.forEach { error.addSuppressed(it.cause) }
            return error
        }
        return if (rollbackFailures.isNotEmpty()) {
            ProgramImportRollbackException(cause = error, diagnostics = rollbackFailures.toList())
        } else {
            error
        }
    }

    /**
     * Attempts the rollback delete for every id persisted in this batch (STR-256), collecting
     * each failure as typed data instead of discarding it. A [CancellationException] from a
     * delete aborts the remaining attempts and propagates — cancellation wins.
     */
    private suspend fun collectRollbackFailures(
        importedIds: List<ProgramId>,
    ): List<ProgramImportRollbackDiagnostic> {
        val failures = mutableListOf<ProgramImportRollbackDiagnostic>()
        for (id in importedIds) {
            try {
                repository.deleteProgram(id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (rollbackError: Throwable) {
                failures +=
                    ProgramImportRollbackDiagnostic.RollbackDeleteFailed(
                        programId = id,
                        cause = rollbackError,
                    )
            }
        }
        return failures
    }

    /**
     * [Result.getOrElse] with the cancellation contract closed: a
     * [CancellationException] carried as a failure VALUE is rethrown as
     * cancellation instead of being returned as an ordinary failure. Today's sole
     * adapter builds its Results with `runCatchingCancellable` and can never
     * produce one, but direct Result propagation must not silently absorb a
     * future adapter's cancellation.
     */
    private inline fun <T> Result<T>.getOrElseRethrowingCancellation(
        onFailure: (Throwable) -> T,
    ): T =
        getOrElse { error ->
            if (error is CancellationException) throw error
            onFailure(error)
        }

    private suspend fun pollUntilTerminal(
        jobId: ProgramTemplateImportJobId,
        onProgress: ProgramTemplateImportProgressCallback,
    ): ProgramTemplateJobSnapshot {
        // Direct propagation (STR-241): a mid-poll failure wins over the timeout and
        // over any non-terminal snapshot, so a poll failure value is rethrown here and
        // surfaces through the invoke envelope. A CancellationException thrown by the
        // port or the delay propagates as cancellation (no catch here), and one carried
        // as a poll Result failure value is rethrown for the same reason. The deadline
        // surfaces as [ImportPollTimeoutException].
        val pollOutcome =
            withTimeoutOrNull(POLL_TIMEOUT_MS) {
                var terminal: ProgramTemplateJobSnapshot? = null
                while (terminal == null) {
                    delay(POLL_INTERVAL_MS)
                    val result = importPort.pollJobStatus(jobId)
                    val failure = result.exceptionOrNull()
                    if (failure != null) {
                        throw failure
                    }
                    val snapshot = result.getOrNull()
                    snapshot?.progressPhase?.let { onProgress(it) }
                    if (snapshot != null && snapshot.status.isTerminal()) {
                        terminal = snapshot
                    }
                }
                terminal
            }
        return pollOutcome ?: throw ImportPollTimeoutException()
    }

    private fun RemoteProgramTemplateJobStatus.isTerminal(): Boolean =
        this == RemoteProgramTemplateJobStatus.Ready ||
            this == RemoteProgramTemplateJobStatus.Rejected ||
            this == RemoteProgramTemplateJobStatus.Failed

    private companion object {
        const val TAG = "ProgramTemplateImport"
        const val POLL_INTERVAL_MS = 2_000L
        const val POLL_TIMEOUT_MS = 5 * 60 * 1_000L
    }
}

/**
 * Applies [programId] to the program and every nested workout so hierarchy FKs stay valid.
 * Exposed for unit tests that assert export-id re-import rebinding.
 */
internal fun rebindProgramId(
    program: com.stratum.domain.program.Program,
    programId: ProgramId,
): com.stratum.domain.program.Program {
    if (program.id == programId && program.workouts.all { it.programId == programId }) {
        return program.deactivate()
    }
    return program
        .copy(
            id = programId,
            workouts = program.workouts.map { workout -> workout.copy(programId = programId) },
        )
        .deactivate()
}
