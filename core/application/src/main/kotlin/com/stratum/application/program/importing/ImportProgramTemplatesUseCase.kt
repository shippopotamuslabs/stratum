package com.stratum.application.program.importing

import com.stratum.domain.coroutines.runCatchingCancellable
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

/** Carried as a [Result] failure value when the server rejects the uploaded file as not a workout program. */
class ProgramTemplateRejectedException(message: String) : Exception(message)

/**
 * Phase-2 persist failure where at least one rollback delete also failed (STR-256).
 *
 * The rollback shape stays fire-and-forget per id: every persisted id still gets its delete
 * attempt. When one or more deletes fail, the evidence rides this failure object instead of
 * being discarded: [message] is exactly the primary persist failure's message and [cause] is
 * the primary itself, so user-facing copy derived from the failure is unchanged; [diagnostics]
 * carries each failed delete with its program id. Each delete cause is also attached via
 * [addSuppressed] so boundaries that log the failure object emit the full evidence in that
 * single emission. Thrown only when at least one rollback failed — a clean rollback rethrows
 * the primary bare. A [CancellationException] primary is never wrapped: it is rethrown as-is
 * with the rollback causes suppressed on it.
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
    ): Result<ProgramTemplateImportResult> {
        // Rollback diagnostics collected inside; re-attached below when the surfaced
        // failure is a cancellation the platform normalized (see below).
        val rollbackFailures = mutableListOf<ProgramImportRollbackDiagnostic>()
        val result = runImportBlock(rollbackFailures) {
            withContext(importDispatcher) {
            onProgress(ProgramTemplateImportPhase.Uploading)
            val jobId = ProgramTemplateImportJobId(UUID.randomUUID().toString())
            importPort.uploadAndStart(jobId, bytes, fileName)
                .getOrElse { return@withContext Result.failure(it) }

            val snapshot = pollUntilTerminal(jobId, onProgress)
                .getOrElse { return@withContext Result.failure(it) }
            // Expected terminal outcomes are returned as failure values, never thrown:
            // the exception types are constructed as Result payloads (STR-241).
            when (snapshot.status) {
                RemoteProgramTemplateJobStatus.Rejected ->
                    return@withContext Result.failure(
                        ProgramTemplateRejectedException(
                            snapshot.errorMessage ?: "This file does not look like a workout program.",
                        ),
                    )
                RemoteProgramTemplateJobStatus.Failed ->
                    return@withContext Result.failure(
                        IllegalStateException(snapshot.errorMessage ?: "Import failed."),
                    )
                RemoteProgramTemplateJobStatus.Ready -> Unit
                else ->
                    return@withContext Result.failure(
                        IllegalStateException("Import ended in non-terminal status: ${snapshot.status}"),
                    )
            }

            val templateStoragePath =
                snapshot.programTemplateStoragePath
                    ?: return@withContext Result.failure(
                        IllegalStateException("Ready job has no template path."),
                    )

            onProgress(ProgramTemplateImportPhase.MatchingCatalog)
            val templateJson = importPort.downloadProgramTemplate(templateStoragePath)
                .getOrElse { return@withContext Result.failure(it) }
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

            for (item in parsed) {
                require(item.template.workouts.isNotEmpty()) { "Imported template has no workouts" }
                val resolved = resolver.invoke(item.template)
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
                throw resolveRollbackThrow(error, importedIds, rollbackFailures)
            }

            if (options.completeOnboarding && importedIds.isNotEmpty()) {
                val profile = profileRepository.observeProfile().first() ?: UserTrainingProfile.Empty
                completeOnboarding(profile)
            }
            onProgress(ProgramTemplateImportPhase.Complete)
            Result.success(
                ProgramTemplateImportResult(
                    importedProgramIds = importedIds,
                    skippedProgramCount = skipped,
                ),
            )
            }
        }
        // Flatten: the block returns expected failures as Result values; the envelope's
        // own failures (operational throws caught by runCatchingCancellable) pass through.
        return result.getOrElse { return Result.failure(it) }
    }

    /**
     * Runs [block] as the import's Result envelope (STR-239): ordinary failures return
     * as a [Result]; a cancellation propagates as a throw with the rollback evidence
     * re-attached (STR-256, see [withRollbackEvidence]).
     */
    private suspend fun <T> runImportBlock(
        rollbackFailures: List<ProgramImportRollbackDiagnostic>,
        block: suspend () -> T,
    ): Result<T> =
        try {
            runCatchingCancellable(block)
        } catch (cancelled: CancellationException) {
            throw cancelled.withRollbackEvidence(rollbackFailures)
        }

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
     * returns the primary bare. The in-catch attach is repeated after runCatchingCancellable because
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

    private suspend fun pollUntilTerminal(
        jobId: ProgramTemplateImportJobId,
        onProgress: ProgramTemplateImportProgressCallback,
    ): Result<ProgramTemplateJobSnapshot> {
        // Direct Result propagation (STR-241): a poll failure is returned as a value,
        // never unwrapped into a throw. Cancellation from the port or the delay
        // propagates as cancellation, exactly as the previous envelope did.
        var pollFailure: Throwable? = null
        val terminal = withTimeoutOrNull(POLL_TIMEOUT_MS) {
            var snapshot: ProgramTemplateJobSnapshot? = null
            while (snapshot == null || !snapshot.status.isTerminal()) {
                delay(POLL_INTERVAL_MS)
                val polled = importPort.pollJobStatus(jobId)
                val failure = polled.exceptionOrNull()
                if (failure != null) {
                    pollFailure = failure
                    return@withTimeoutOrNull snapshot
                }
                snapshot = polled.getOrNull()
                snapshot?.progressPhase?.let { onProgress(it) }
            }
            snapshot
        }
        pollFailure?.let { return Result.failure(it) }
        return if (terminal != null) {
            Result.success(terminal)
        } else {
            Result.failure(IllegalStateException("Timed out waiting for import to finish."))
        }
    }

    private fun RemoteProgramTemplateJobStatus.isTerminal(): Boolean =
        this == RemoteProgramTemplateJobStatus.Ready ||
            this == RemoteProgramTemplateJobStatus.Rejected ||
            this == RemoteProgramTemplateJobStatus.Failed

    private companion object {
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
