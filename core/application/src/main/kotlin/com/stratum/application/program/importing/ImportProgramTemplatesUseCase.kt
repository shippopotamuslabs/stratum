package com.stratum.application.program.importing

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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.stratum.domain.program.importing.ProgramImportActivation
import com.stratum.application.program.importing.ProgramImportDedupe
import com.stratum.domain.program.importing.ProgramTemplateImportJobId
import com.stratum.domain.program.importing.ProgramTemplateImportOptions
import com.stratum.domain.program.importing.ProgramTemplateImportPhase
import com.stratum.domain.program.importing.ProgramTemplateImportResult
import com.stratum.domain.program.importing.ProgramTemplateJobSnapshot
import com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout

typealias ProgramTemplateImportProgressCallback = suspend (ProgramTemplateImportPhase) -> Unit

/** Thrown when the server rejects the uploaded file as not a workout program. */
class ProgramTemplateRejectedException(message: String) : Exception(message)

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
    ): Result<ProgramTemplateImportResult> = runCatching {
        withContext(importDispatcher) {
            onProgress(ProgramTemplateImportPhase.Uploading)
            val jobId = ProgramTemplateImportJobId(UUID.randomUUID().toString())
            importPort.uploadAndStart(jobId, bytes, fileName).getOrThrow()

            val snapshot = pollUntilTerminal(jobId, onProgress).getOrThrow()
            when (snapshot.status) {
                RemoteProgramTemplateJobStatus.Rejected ->
                    throw ProgramTemplateRejectedException(
                        snapshot.errorMessage ?: "This file does not look like a workout program.",
                    )
                RemoteProgramTemplateJobStatus.Failed ->
                    error(snapshot.errorMessage ?: "Import failed.")
                RemoteProgramTemplateJobStatus.Ready -> Unit
                else ->
                    error("Import ended in non-terminal status: ${snapshot.status}")
            }

            val templateStoragePath =
                snapshot.programTemplateStoragePath
                    ?: error("Ready job has no template path.")

            onProgress(ProgramTemplateImportPhase.MatchingCatalog)
            val templateJson = importPort.downloadProgramTemplate(templateStoragePath).getOrThrow()
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
                for (id in importedIds) {
                    runCatching { repository.deleteProgram(id) }
                }
                throw error
            }

            if (options.completeOnboarding && importedIds.isNotEmpty()) {
                val profile = profileRepository.observeProfile().first() ?: UserTrainingProfile.Empty
                completeOnboarding(profile)
            }
            onProgress(ProgramTemplateImportPhase.Complete)
            ProgramTemplateImportResult(
                importedProgramIds = importedIds,
                skippedProgramCount = skipped,
            )
        }
    }

    private suspend fun pollUntilTerminal(
        jobId: ProgramTemplateImportJobId,
        onProgress: ProgramTemplateImportProgressCallback,
    ): Result<ProgramTemplateJobSnapshot> = runCatching {
        val terminal = withTimeoutOrNull(POLL_TIMEOUT_MS) {
            var snapshot: ProgramTemplateJobSnapshot? = null
            while (snapshot == null || !snapshot.status.isTerminal()) {
                delay(POLL_INTERVAL_MS)
                snapshot = importPort.pollJobStatus(jobId).getOrThrow()
                snapshot.progressPhase?.let { onProgress(it) }
            }
            snapshot
        } ?: error("Timed out waiting for import to finish.")
        terminal
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
