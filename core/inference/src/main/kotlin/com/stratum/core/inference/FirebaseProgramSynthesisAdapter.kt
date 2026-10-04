package com.stratum.core.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.storage.FirebaseStorage
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisException
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisPort
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult
import com.stratum.domain.program.generation.synthesis.wire.ProgramSynthesisWire
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import com.stratum.domain.telemetry.AnalyticsPort
import com.stratum.domain.telemetry.ClientStage
import com.stratum.domain.telemetry.ClientStageCorrelation
import com.stratum.domain.telemetry.SynthesisTelemetry
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed [ProgramSynthesisPort]. Encodes the CP-SAT request, uploads it to Storage under
 * `synthesis/{uid}/{jobId}/request.json`, starts the job via `startProgramSynthesis`, polls
 * `getProgramSynthesisStatus` until terminal, then downloads and decodes the result JSON.
 *
 * Client poll ceiling is 5 minutes (must stay ≤ server stale reclaim window of 6 minutes).
 * Auth and monthly fair-use (starts) are enforced by the callables.
 */
@Singleton
class FirebaseProgramSynthesisAdapter @Inject constructor(
    private val loggingPort: LoggingPort,
    analyticsPort: AnalyticsPort,
) : ProgramSynthesisPort {
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val telemetry = SynthesisTelemetry(analyticsPort::logEvent)

    override suspend fun synthesize(
        weeklyTargets: WeeklyVolumeTargets,
        context: ProgramGenerationContext,
    ): ProgramSynthesisResult {
        val uid =
            auth.currentUser?.uid
                ?: throw ProgramSynthesisException(MESSAGE_SIGN_IN_REQUIRED)
        val jobId = UUID.randomUUID().toString()
        val requestStoragePath = buildSynthesisRequestStoragePath(uid, jobId)
        // STR-13 trace: client-observed start, sent to the server for the job doc trace.
        val trace = FirebasePerformance.getInstance().newTrace(TRACE_PROGRAM_SYNTHESIS)
        var outcome = synthesisTraceOutcome(status = null, timedOut = false)
        trace.start()
        val telemetryStartMs = telemetry.synthesisStarted(jobId)

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
                        "clientStartMs" to telemetryStartMs,
                    ),
                )
                .await()

            // STR-22: transient getProgramSynthesisStatus failures (INTERNAL, UNAVAILABLE,
            // DEADLINE_EXCEEDED) must not kill a minutes-long generation; they are
            // retried inside pollStatusWithTransientRetry and polling continues. The
            // 5-minute ceiling still bounds total wait, and the consecutive-failure
            // cap fails fast on a sustained outage.
            val snapshot =
                try {
                    pollUntil(
                        timeoutMs = POLL_TIMEOUT_MS,
                        isTerminal = { it.status.isTerminal() },
                        poll = {
                            pollStatusWithTransientRetry(
                                fetch = { fetchStatusSnapshot(jobId) },
                                loggingPort = loggingPort,
                                jobId = jobId,
                            )
                        },
                    )
                } catch (_: IllegalStateException) {
                    outcome = synthesisTraceOutcome(status = null, timedOut = true)
                    throw ProgramSynthesisException(MESSAGE_TIMEOUT)
                }

            return when (snapshot.status) {
                SynthesisRemoteStatus.Ready -> {
                    val resultPath =
                        snapshot.resultStoragePath
                            ?: throw ProgramSynthesisException(MESSAGE_GENERATION_FAILED)
                    requireOwnedResultPath(uid, resultPath)
                    val bytes =
                        storage.reference
                            .child(resultPath)
                            .getBytes(MAX_DOWNLOAD_BYTES)
                            .await()
                    outcome = synthesisTraceOutcome(snapshot.status, timedOut = false)
                    // STR-13 trace: client observed the terminal Ready state.
                    telemetry.synthesisReadyObserved(jobId, telemetryStartMs)
                    // STR-19 trace: time the result decode. result.json is small
                    // (~25KB observed), so this should be milliseconds — if it
                    // is not, the wire decoder is the bottleneck.
                    val decodeTimer =
                        telemetry.startStage(
                            ClientStageCorrelation.Job(jobId),
                            ClientStage.DECODE_RESULT,
                        )
                    val decoded = ProgramSynthesisWire.decodeResult(bytes.decodeToString())
                    val decodeMs = decodeTimer.stop()
                    loggingPort.info(
                        TAG,
                        "synthesis client trace: job=$jobId " +
                            "stage=${ClientStage.DECODE_RESULT.wireName} " +
                            "duration_ms=$decodeMs " +
                            "bytes=${bytes.size}",
                    )
                    decoded
                }
                SynthesisRemoteStatus.Failed,
                SynthesisRemoteStatus.Rejected,
                -> {
                    outcome = synthesisTraceOutcome(snapshot.status, timedOut = false)
                    throw ProgramSynthesisException(
                        // Prefer server-provided user-safe copy when present; never surface raw infra.
                        snapshot.errorMessage
                            ?.takeIf { it.isUserSafeErrorMessage() }
                            ?: MESSAGE_GENERATION_FAILED,
                    )
                }
                SynthesisRemoteStatus.Pending,
                SynthesisRemoteStatus.Running,
                ->
                    throw ProgramSynthesisException(MESSAGE_GENERATION_FAILED)
            }
        } catch (error: ProgramSynthesisException) {
            throw error
        } catch (error: FirebaseFunctionsException) {
            FirebaseCallableErrorLogger.logFailure(loggingPort, TAG, START_CALLABLE, error)
            throw ProgramSynthesisException(
                message = mapFunctionsExceptionToUserMessage(error.code.name),
                cause = error,
            )
        } catch (error: Throwable) {
            loggingPort.error(TAG, "Program synthesis failed: ${error.message}", error)
            throw ProgramSynthesisException(
                message = MESSAGE_GENERATION_FAILED,
                cause = error,
            )
        } finally {
            trace.putAttribute(TRACE_ATTR_OUTCOME, outcome)
            trace.stop()
        }
    }

    private suspend fun fetchStatusSnapshot(jobId: String): SynthesisJobSnapshot {
        val result =
            functions
                .getHttpsCallable(STATUS_CALLABLE)
                .call(mapOf("jobId" to jobId))
                .await()
        val snapshot = parseSynthesisStatusResponse(result.data)
        snapshot.progressPhase?.let { phase ->
            loggingPort.info(
                TAG,
                "synthesis job=$jobId phase=$phase message=${snapshot.progressMessage}",
            )
        }
        return snapshot
    }

    private companion object {
        const val START_CALLABLE = "startProgramSynthesis"
        const val MAX_DOWNLOAD_BYTES = 10L * 1024L * 1024L
        /** Must stay ≤ server SYNTHESIS_STALE_JOB_MS (6 min). */
        const val POLL_TIMEOUT_MS = 5 * 60 * 1000L
    }
}

internal const val TAG = "ProgramSynthesis"
internal const val STATUS_CALLABLE = "getProgramSynthesisStatus"
/**
 * Consecutive transient getProgramSynthesisStatus failures tolerated before failing
 * the generation. A single blip (the STR-22 incident: one INTERNAL mid-poll) is
 * retried; a sustained outage fails fast instead of polling pointlessly until
 * the 5-minute ceiling.
 */
internal const val MAX_CONSECUTIVE_TRANSIENT_STATUS_FAILURES = 10

internal enum class SynthesisRemoteStatus {
    Pending,
    Running,
    Ready,
    Failed,
    Rejected,
    ;

    fun isTerminal(): Boolean =
        this == Ready || this == Failed || this == Rejected
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

internal fun requireOwnedResultPath(uid: String, path: String) {
    val prefix = "synthesis/$uid/"
    if (!path.startsWith(prefix) || path.contains("..") || path.contains("//")) {
        throw ProgramSynthesisException(MESSAGE_GENERATION_FAILED)
    }
}

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

internal const val TRACE_PROGRAM_SYNTHESIS = "program_synthesis"
internal const val TRACE_ATTR_OUTCOME = "outcome"

/**
 * Outcome label for the `program_synthesis` Performance trace. Kept pure so it stays
 * unit-testable without Firebase: unexpected states collapse to `"failed"`.
 */
internal fun synthesisTraceOutcome(
    status: SynthesisRemoteStatus?,
    timedOut: Boolean,
): String =
    when {
        timedOut -> "timeout"
        status == SynthesisRemoteStatus.Ready -> "ready"
        status == SynthesisRemoteStatus.Rejected -> "rejected"
        else -> "failed"
    }

/**
 * Maps Firebase Functions [code] names (e.g. `RESOURCE_EXHAUSTED`) to user-safe copy.
 * Never forwards arbitrary server exception text.
 */
internal fun mapFunctionsExceptionToUserMessage(codeName: String?): String =
    when (codeName) {
        "RESOURCE_EXHAUSTED" -> MESSAGE_MONTHLY_LIMIT
        "FAILED_PRECONDITION" -> MESSAGE_ALREADY_IN_PROGRESS
        "UNAUTHENTICATED" -> MESSAGE_SIGN_IN_REQUIRED
        else -> MESSAGE_GENERATION_FAILED
    }

private const val MAX_USER_SAFE_ERROR_CHARS = 200

/** Server-authored messages we trust enough to show; reject stack traces / paths. */
internal fun String.isUserSafeErrorMessage(): Boolean {
    if (isBlank() || length > MAX_USER_SAFE_ERROR_CHARS) return false
    if (contains("gs://") || contains("Exception")) return false
    if (contains("http") || contains("\n")) return false
    return true
}

/**
 * Firebase Functions codes worth riding out inside the status poll loop. A single
 * INTERNAL / UNAVAILABLE / DEADLINE_EXCEEDED must not kill a minutes-long generation;
 * the 5-minute poll ceiling still bounds total wait. Everything else (NOT_FOUND,
 * PERMISSION_DENIED, UNAUTHENTICATED, …) fails the generation immediately.
 */
internal fun FirebaseFunctionsException.Code.isTransientStatusCode(): Boolean =
    when (this) {
        FirebaseFunctionsException.Code.INTERNAL,
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        -> true
        else -> false
    }

/**
 * Fetches one status snapshot, retrying transient [FirebaseFunctionsException]s
 * ([isTransientStatusCode]) inline so the poll loop survives blips without a fake
 * non-terminal stand-in: a transient failure logs a warning and retries after
 * [retryDelayMs], a permanent failure throws [ProgramSynthesisException] at once,
 * and more than [maxConsecutiveTransients] consecutive transient failures fail
 * the generation instead of retrying forever.
 *
 * STR-55: retries are spaced (never a hot loop) so Doze simply defers them, and
 * failures separated by a device-sleep gap reset the consecutive budget — a night
 * of deferred retries is not one sustained outage. Tightly-spaced failures still
 * fail fast exactly as before.
 *
 * Transient failures use `warning()`, not [FirebaseCallableErrorLogger]: a blip must
 * not become a Crashlytics non-fatal (see STR-25).
 */
internal suspend fun pollStatusWithTransientRetry(
    fetch: suspend () -> SynthesisJobSnapshot,
    loggingPort: LoggingPort,
    jobId: String,
    maxConsecutiveTransients: Int = MAX_CONSECUTIVE_TRANSIENT_STATUS_FAILURES,
    retryDelayMs: Long = TRANSIENT_RETRY_DELAY_MS,
    nowMs: () -> Long = System::currentTimeMillis,
): SynthesisJobSnapshot {
    var consecutiveTransients = 0
    var lastFailureMs = 0L
    while (true) {
        try {
            return fetch()
        } catch (error: FirebaseFunctionsException) {
            if (!error.code.isTransientStatusCode()) {
                FirebaseCallableErrorLogger.logFailure(loggingPort, TAG, STATUS_CALLABLE, error)
                throw ProgramSynthesisException(
                    message = mapFunctionsExceptionToUserMessage(error.code.name),
                    cause = error,
                )
            }
            val now = nowMs()
            if (lastFailureMs != 0L && now - lastFailureMs > SUSPENSION_GAP_THRESHOLD_MS) {
                consecutiveTransients = 0
            }
            lastFailureMs = now
            consecutiveTransients++
            if (consecutiveTransients > maxConsecutiveTransients) {
                throw ProgramSynthesisException(MESSAGE_GENERATION_FAILED)
            }
            loggingPort.warning(
                TAG,
                "$STATUS_CALLABLE transient failure (${error.code.name}); " +
                    "continuing to poll job=$jobId",
            )
            delay(retryDelayMs)
        }
    }
}
