package com.stratum.core.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.FirebaseStorage
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisException
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult
import com.stratum.domain.program.generation.synthesis.wire.ProgramSynthesisWire
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed [ProgramSynthesisPort]. Encodes the CP-SAT request, uploads it to Storage under
 * `synthesis/{uid}/{jobId}/`, starts the job via `startProgramSynthesis`, polls
 * `getProgramSynthesisStatus` until terminal, then downloads and decodes the result JSON.
 *
 * Mirrors the historical / program-template import job loop (1.5s → 3s after 30s). Client timeout
 * is 5 minutes to cover cold start + the 120s solver deadline.
 *
 * Auth, monthly fair-use quota, and single-active-job rules are enforced by the callables;
 * failures surface as [ProgramSynthesisException] with user-safe messages.
 */
@Singleton
class FirebaseProgramSynthesisAdapter @Inject constructor(
    private val loggingPort: LoggingPort,
) : ProgramSynthesisPort {
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun synthesize(
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
    ): ProgramSynthesisResult {
        val uid =
            auth.currentUser?.uid
                ?: throw ProgramSynthesisException(MESSAGE_SIGN_IN_REQUIRED)
        val jobId = UUID.randomUUID().toString()
        val requestStoragePath = buildSynthesisRequestStoragePath(uid, jobId)

        try {
            val requestJson = ProgramSynthesisWire.encodeRequest(weeklyTargets, context)
            storage.reference
                .child(requestStoragePath)
                .putBytes(requestJson.toByteArray(Charsets.UTF_8))
                .await()

            functions
                .getHttpsCallable(START_CALLABLE)
                .call(
                    mapOf(
                        "jobId" to jobId,
                        "requestStoragePath" to requestStoragePath,
                    ),
                )
                .await()

            val snapshot = pollUntilTerminal(jobId)
            return when (snapshot.status) {
                SynthesisRemoteStatus.Ready -> {
                    val resultPath =
                        snapshot.resultStoragePath
                            ?: throw ProgramSynthesisException(
                                "Program generation finished but the result path was missing.",
                            )
                    val bytes =
                        storage.reference
                            .child(resultPath)
                            .getBytes(MAX_DOWNLOAD_BYTES)
                            .await()
                    ProgramSynthesisWire.decodeResult(bytes.decodeToString())
                }
                SynthesisRemoteStatus.Failed,
                SynthesisRemoteStatus.Rejected,
                ->
                    throw ProgramSynthesisException(
                        snapshot.errorMessage?.takeIf { it.isNotBlank() }
                            ?: MESSAGE_GENERATION_FAILED,
                    )
                SynthesisRemoteStatus.Pending,
                SynthesisRemoteStatus.Running,
                ->
                    throw ProgramSynthesisException(
                        "Program generation ended in an unexpected state: ${snapshot.status}.",
                    )
            }
        } catch (error: ProgramSynthesisException) {
            throw error
        } catch (error: FirebaseFunctionsException) {
            FirebaseCallableErrorLogger.logFailure(loggingPort, TAG, START_CALLABLE, error)
            throw ProgramSynthesisException(
                message = mapFunctionsExceptionToUserMessage(error.code.name, error.message),
                cause = error,
            )
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Program synthesis failed: ${error.message}", error)
            throw ProgramSynthesisException(
                message = error.message?.takeIf { it.isNotBlank() } ?: MESSAGE_GENERATION_FAILED,
                cause = error,
            )
        }
    }

    private suspend fun pollUntilTerminal(jobId: String): SynthesisJobSnapshot {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        var elapsedMs = 0L
        var interval = INITIAL_POLL_INTERVAL_MS
        while (System.currentTimeMillis() < deadline) {
            val snapshot =
                try {
                    pollStatus(jobId)
                } catch (error: FirebaseFunctionsException) {
                    FirebaseCallableErrorLogger.logFailure(
                        loggingPort,
                        TAG,
                        STATUS_CALLABLE,
                        error,
                    )
                    throw ProgramSynthesisException(
                        message = mapFunctionsExceptionToUserMessage(error.code.name, error.message),
                        cause = error,
                    )
                }
            snapshot.progressPhase?.let { phase ->
                loggingPort.info(
                    TAG,
                    "synthesis job=$jobId phase=$phase message=${snapshot.progressMessage}",
                )
            }
            when (snapshot.status) {
                SynthesisRemoteStatus.Ready,
                SynthesisRemoteStatus.Failed,
                SynthesisRemoteStatus.Rejected,
                -> return snapshot
                SynthesisRemoteStatus.Pending,
                SynthesisRemoteStatus.Running,
                -> {
                    delay(interval.milliseconds)
                    elapsedMs += interval
                    if (elapsedMs > BACKOFF_AFTER_MS) {
                        interval = SLOW_POLL_INTERVAL_MS
                    }
                }
            }
        }
        throw ProgramSynthesisException(MESSAGE_TIMEOUT)
    }

    private suspend fun pollStatus(jobId: String): SynthesisJobSnapshot {
        val result =
            functions
                .getHttpsCallable(STATUS_CALLABLE)
                .call(mapOf("jobId" to jobId))
                .await()
        return parseSynthesisStatusResponse(result.data)
    }

    private companion object {
        const val TAG = "ProgramSynthesis"
        const val START_CALLABLE = "startProgramSynthesis"
        const val STATUS_CALLABLE = "getProgramSynthesisStatus"
        const val MAX_DOWNLOAD_BYTES = 10L * 1024L * 1024L
        const val INITIAL_POLL_INTERVAL_MS = 1_500L
        const val SLOW_POLL_INTERVAL_MS = 3_000L
        const val BACKOFF_AFTER_MS = 30_000L
        /** Covers 120s solve + Cloud Run cold start. */
        const val POLL_TIMEOUT_MS = 300_000L
    }
}

internal enum class SynthesisRemoteStatus {
    Pending,
    Running,
    Ready,
    Failed,
    Rejected,
}

internal data class SynthesisJobSnapshot(
    val status: SynthesisRemoteStatus,
    val progressPhase: String?,
    val progressMessage: String?,
    val resultStoragePath: String?,
    val errorMessage: String?,
)

internal fun buildSynthesisRequestStoragePath(uid: String, jobId: String): String =
    "synthesis/$uid/$jobId/request.json"

internal fun parseSynthesisRemoteStatus(value: String?): SynthesisRemoteStatus = when (value) {
    "pending" -> SynthesisRemoteStatus.Pending
    "running" -> SynthesisRemoteStatus.Running
    "ready" -> SynthesisRemoteStatus.Ready
    "failed" -> SynthesisRemoteStatus.Failed
    "rejected" -> SynthesisRemoteStatus.Rejected
    // Unknown status must not hang the poller as Pending.
    else -> SynthesisRemoteStatus.Failed
}

@Suppress("UNCHECKED_CAST")
internal fun parseSynthesisStatusResponse(data: Any?): SynthesisJobSnapshot {
    val map = data as? Map<*, *> ?: error("Invalid getProgramSynthesisStatus response")
    return SynthesisJobSnapshot(
        status = parseSynthesisRemoteStatus(map["status"] as? String),
        progressPhase = map["progressPhase"] as? String,
        progressMessage = map["progressMessage"] as? String,
        resultStoragePath = map["resultStoragePath"] as? String,
        errorMessage = map["errorMessage"] as? String,
    )
}

internal const val MESSAGE_SIGN_IN_REQUIRED = "Sign-in required to generate a program."
internal const val MESSAGE_MONTHLY_LIMIT =
    "You've reached this month's program generation limit. Try again next month."
internal const val MESSAGE_ALREADY_IN_PROGRESS =
    "Another program generation is already in progress. Please wait for it to finish."
internal const val MESSAGE_GENERATION_FAILED = "Program generation failed. Please try again."
internal const val MESSAGE_TIMEOUT =
    "Program generation timed out. Please try again."

/**
 * Maps Firebase Functions [code] names (e.g. `RESOURCE_EXHAUSTED`) to user-safe copy.
 * Takes the enum [name] rather than the SDK type so JVM unit tests stay Firebase-free.
 */
internal fun mapFunctionsExceptionToUserMessage(
    codeName: String?,
    message: String?,
): String =
    when (codeName) {
        "RESOURCE_EXHAUSTED" -> MESSAGE_MONTHLY_LIMIT
        "FAILED_PRECONDITION" -> MESSAGE_ALREADY_IN_PROGRESS
        "UNAUTHENTICATED" -> MESSAGE_SIGN_IN_REQUIRED
        else ->
            message?.takeIf { it.isNotBlank() }
                ?: MESSAGE_GENERATION_FAILED
    }
