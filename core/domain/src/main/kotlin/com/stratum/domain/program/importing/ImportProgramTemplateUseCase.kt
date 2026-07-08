package com.stratum.domain.program.importing

import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.UserTrainingProfileRepository
import com.stratum.domain.program.ProgramRepository
import com.stratum.domain.program.importing.NormalizedProgramTemplateJson.parse
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventRepository
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

typealias ProgramTemplateImportProgressCallback = suspend (ProgramTemplateImportPhase) -> Unit

/**
 * Orchestrates importing a program-template file end-to-end:
 *
 * 1. Upload the file and start the server job via [ProgramTemplateImportPort].
 * 2. Poll the job until it reaches a terminal status (Ready / Rejected / Failed).
 * 3. Download the normalized template JSON and parse it.
 * 4. Resolve lift names against the catalog via [ResolveProgramTemplateLiftsUseCase].
 * 5. Persist the resulting [com.stratum.domain.program.Program] via [ProgramRepository.saveProgram]
 *    and mark onboarding complete.
 *
 * Mirrors [com.stratum.domain.program.GenerateProgramUseCase] but sources the program from an
 * uploaded file instead of the generation pipeline. Parsing on the server is deterministic — the
 * two supported formats (Alpha Progression plan CSV, MacroFactor program XLSX) convert in
 * milliseconds, and any other file is rejected as unsupported. A Rejected job (unsupported or
 * unrecognized file) throws [ProgramTemplateRejectedException] so the UI can show a distinct message.
 *
 * @throws ProgramTemplateRejectedException when the server rejects the file as unsupported.
 */
class ImportProgramTemplateUseCase @Inject constructor(
    private val importPort: ProgramTemplateImportPort,
    private val resolver: ResolveProgramTemplateLiftsUseCase,
    private val repository: ProgramRepository,
    private val completeOnboarding: CompleteOnboardingUseCase,
    private val profileRepository: UserTrainingProfileRepository,
    private val programChangeEventRepository: ProgramChangeEventRepository,
) {
    suspend operator fun invoke(
        bytes: ByteArray,
        fileName: String,
        onProgress: ProgramTemplateImportProgressCallback = {},
    ): Result<Unit> = runCatching {
        onProgress(ProgramTemplateImportPhase.Uploading)
        val jobId = ProgramTemplateImportJobId(UUID.randomUUID().toString())
        importPort.uploadAndStart(jobId, bytes, fileName).getOrThrow()

        // Poll until the job reaches a terminal status. Bounded by [POLL_TIMEOUT_MS] so a stuck
        // job never hangs onboarding indefinitely.
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

        val templateStoragePath = snapshot.programTemplateStoragePath
            ?: error("Ready job has no template path.")

        onProgress(ProgramTemplateImportPhase.MatchingCatalog)
        val templateJson = importPort.downloadProgramTemplate(templateStoragePath).getOrThrow()
        val template = parse(templateJson)
        require(template.workouts.isNotEmpty()) { "Imported template has no workouts" }

        val program = resolver.invoke(template)
        repository.saveProgram(program)
        programChangeEventRepository.append(
            ProgramChangeEvent(
                id = ProgramChangeEventId(UUID.randomUUID().toString()),
                programId = program.id,
                eventType = ProgramChangeEventType.PROGRAM_REGENERATED,
                occurredAt = Instant.now(),
                payload = ProgramChangeEventPayload.ProgramRegenerated,
            ),
        )
        val profile = profileRepository.observeProfile().first() ?: UserTrainingProfile.Empty
        completeOnboarding(profile)
        onProgress(ProgramTemplateImportPhase.Complete)
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
        const val POLL_TIMEOUT_MS = 60_000L
    }
}

/** Thrown when the server rejects the uploaded file as not a workout program. */
class ProgramTemplateRejectedException(message: String) : Exception(message)
