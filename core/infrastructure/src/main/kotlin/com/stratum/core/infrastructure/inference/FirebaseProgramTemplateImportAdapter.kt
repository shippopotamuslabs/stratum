package com.stratum.core.infrastructure.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.importing.ProgramTemplateImportJobId
import com.stratum.domain.program.importing.ProgramTemplateImportPhase
import com.stratum.application.program.importing.ProgramTemplateImportPort
import com.stratum.domain.program.importing.ProgramTemplateJobSnapshot
import com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed [ProgramTemplateImportPort]. Uploads the raw program-template file to the
 * `programImports/` Storage prefix, drives the job via the `startProgramTemplateImport` /
 * `getProgramTemplateJobStatus` Callable Functions, and downloads the normalized template JSON.
 *
 * Mirrors [FirebaseHistoricalImportAdapter] but targets the program-template job collection and
 * Storage prefix. All Callable invocations require an authenticated user. Every failure is logged
 * via [LoggingPort] (full detail, including Firebase error codes) so the UI can show a generic
 * user-friendly message without surfacing internal error text.
 */
@Singleton
class FirebaseProgramTemplateImportAdapter @Inject constructor(
    private val loggingPort: LoggingPort,
) : ProgramTemplateImportPort {
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun uploadAndStart(
        jobId: ProgramTemplateImportJobId,
        bytes: ByteArray,
        fileName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val rawPath = "programImports/$uid/${jobId.value}/raw/$fileName"
        storage.reference.child(rawPath).putBytes(bytes).await()
        functions
            .getHttpsCallable(START_CALLABLE)
            .call(
                mapOf(
                    "jobId" to jobId.value,
                    "rawStoragePath" to rawPath,
                    "fileName" to fileName,
                ),
            ).await()
        Unit
    }.onFailure { error ->
        FirebaseCallableErrorLogger.logFailure(loggingPort, TAG, START_CALLABLE, error)
    }

    override suspend fun pollJobStatus(
        jobId: ProgramTemplateImportJobId,
    ): Result<ProgramTemplateJobSnapshot> = runCatching {
        val result =
            functions
                .getHttpsCallable(STATUS_CALLABLE)
                .call(mapOf("jobId" to jobId.value))
                .await()
        val map = result.data as? Map<*, *> ?: error("Invalid $STATUS_CALLABLE response")
        ProgramTemplateJobSnapshot(
            status = parseRemoteStatus(map["status"] as? String),
            progressPhase = parseProgressPhase(map["progressPhase"] as? String),
            progressMessage = map["progressMessage"] as? String,
            programTemplateStoragePath = map["programTemplateStoragePath"] as? String,
            errorMessage = map["errorMessage"] as? String,
        )
    }.onFailure { error ->
        FirebaseCallableErrorLogger.logFailure(loggingPort, TAG, STATUS_CALLABLE, error)
    }

    override suspend fun downloadProgramTemplate(storagePath: String): Result<String> =
        runCatching {
            val bytes = storage.reference.child(storagePath).getBytes(MAX_DOWNLOAD_BYTES).await()
            bytes.decodeToString()
        }.onFailure { error ->
            loggingPort.error(TAG, "downloadProgramTemplate failed for $storagePath", error)
        }

    private fun requireUid(): String =
        auth.currentUser?.uid ?: error("Sign-in required for program import.")

    private fun parseRemoteStatus(value: String?): RemoteProgramTemplateJobStatus = when (value) {
        "pending" -> RemoteProgramTemplateJobStatus.Pending
        "running" -> RemoteProgramTemplateJobStatus.Running
        "ready" -> RemoteProgramTemplateJobStatus.Ready
        "rejected" -> RemoteProgramTemplateJobStatus.Rejected
        "failed" -> RemoteProgramTemplateJobStatus.Failed
        else -> RemoteProgramTemplateJobStatus.Pending
    }

    private fun parseProgressPhase(value: String?): ProgramTemplateImportPhase? = when (value) {
        "sniffing" -> ProgramTemplateImportPhase.Sniffing
        "analyzing" -> ProgramTemplateImportPhase.Analyzing
        "converting" -> ProgramTemplateImportPhase.Converting
        "parsing" -> ProgramTemplateImportPhase.Parsing
        "failed" -> ProgramTemplateImportPhase.Failed
        null -> null
        else -> null
    }

    private companion object {
        const val TAG = "ProgramTemplateImport"
        const val START_CALLABLE = "startProgramTemplateImport"
        const val STATUS_CALLABLE = "getProgramTemplateJobStatus"
        const val MAX_DOWNLOAD_BYTES = 10L * 1024L * 1024L
    }
}
