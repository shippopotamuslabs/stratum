package com.stratum.core.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramGenerationProgress
import com.stratum.domain.program.ProgramGenerationProgressCallback
import com.stratum.domain.program.ProgramGenerationProgressPhase
import com.stratum.domain.program.ProgramGeneratorPort
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

/**
 * Thin Firebase Callable client for the `generateProgram` endpoint (OS-7). Calls the Callable with
 * the serialized onboarding payload and parses the normalized `Program` JSON returned by the server
 * post-pass into domain models. Assigns UUID value-class ids client-side at parse time (D1) — the
 * server returns structure only, no ids.
 *
 * Polls `getProgramGenerationStatus` in parallel so the UI can show model-reported progress while
 * the blocking generation request is in flight (mirrors historical import polling).
 */
@Singleton
class FirebaseProgramGenerator @Inject constructor(
    private val loggingPort: LoggingPort,
) : ProgramGeneratorPort {

    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun generateProgram(
        payload: String,
        onProgress: ProgramGenerationProgressCallback,
    ): Result<Program> {
        lastLoggedProgressPhase = null
        requireAuth()
        validatePayloadJson(payload)
        val callable =
            functions
                .getHttpsCallable(CALLABLE_NAME)
                .withTimeout(CALLABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val progressJob =
            CoroutineScope(currentCoroutineContext() + SupervisorJob()).launch {
                pollGenerationProgress { progress ->
                    logProgressIfChanged(progress)
                    onProgress(progress)
                }
            }
        return try {
            val callableStartMs = System.currentTimeMillis()
            invokeCallable(callable, payload).recoverCatching { error ->
                if (!ProgramGenerationCallableRecovery.isRetriableAfterClientTimeout(error)) {
                    throw error
                }
                loggingPort.warning(
                    TAG,
                    "Program generation callable timed out; retrying once after ${TIMEOUT_RETRY_DELAY_MS}ms",
                )
                delay(TIMEOUT_RETRY_DELAY_MS.milliseconds)
                invokeCallable(callable, payload).getOrThrow()
            }.also { result ->
                val elapsedMs = System.currentTimeMillis() - callableStartMs
                result.onSuccess { program ->
                    val workoutCount = program.workouts.size
                    val liftCount = program.workouts.sumOf { workout -> workout.workoutLifts.size }
                    loggingPort.info(
                        TAG,
                        "Program generation callable completed in ${elapsedMs}ms " +
                            "workouts=$workoutCount lifts=$liftCount",
                    )
                }.onFailure {
                    loggingPort.warning(
                        TAG,
                        "Program generation callable failed after ${elapsedMs}ms",
                    )
                }
            }
        } finally {
            progressJob.cancel()
        }
    }

    private suspend fun invokeCallable(
        callable: HttpsCallableReference,
        payload: String,
    ): Result<Program> =
        runCatching {
            val result = callable.call(mapOf("payload" to payload)).await()
            parseProgram(result.data)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                if (error is CancellationException) {
                    throw error
                }
                FirebaseCallableErrorLogger.logFailure(loggingPort, TAG, CALLABLE_NAME, error)
                Result.failure(
                    ProgramGenerationException(
                        message = ProgramGenerationErrorMapper.userMessage(error),
                        cause = error,
                    ),
                )
            },
        )

    private var lastLoggedProgressPhase: ProgramGenerationProgressPhase? = null

    private fun logProgressIfChanged(progress: ProgramGenerationProgress) {
        if (progress.phase == lastLoggedProgressPhase) {
            return
        }
        lastLoggedProgressPhase = progress.phase
        val detailSuffix = progress.detail?.let { " detail=${it.take(DETAIL_COUNT)}" }.orEmpty()
        loggingPort.info(TAG, "Program generation progress: phase=${progress.phase.name}$detailSuffix")
    }

    private suspend fun pollGenerationProgress(
        onProgress: ProgramGenerationProgressCallback,
    ) {
        val statusCallable =
            functions
                .getHttpsCallable(STATUS_CALLABLE_NAME)
                .withTimeout(POLL_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        var intervalMs = INITIAL_POLL_INTERVAL_MS
        var elapsedMs = 0L
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
            pollStatusSnapshot(statusCallable)?.let { snapshot ->
                snapshot.toProgress()?.let { onProgress(it) }
                if (snapshot.status == "completed") {
                    return
                }
            }
            delay(intervalMs.milliseconds)
            elapsedMs += intervalMs
            if (elapsedMs > BACKOFF_AFTER_MS) {
                intervalMs = SLOW_POLL_INTERVAL_MS
            }
        }
    }

    private suspend fun pollStatusSnapshot(
        statusCallable: HttpsCallableReference,
    ): GenerationStatusSnapshot? =
        runCatching {
            val result = statusCallable.call(emptyMap<String, String>()).await()
            parseStatusSnapshot(result.data)
        }.getOrNull()

    private fun parseStatusSnapshot(data: Any?): GenerationStatusSnapshot {
        val map = data as? Map<*, *> ?: error("Invalid getProgramGenerationStatus response")
        return GenerationStatusSnapshot(
            status = map["status"] as? String,
            progressPhase = map["progressPhase"] as? String,
            progressMessage = map["progressMessage"] as? String,
        )
    }

    private fun requireAuth() {
        checkNotNull(auth.currentUser) { "Sign-in required for program generation." }
    }

    private data class GenerationStatusSnapshot(
        val status: String?,
        val progressPhase: String?,
        val progressMessage: String?,
    ) {
        fun toProgress(): ProgramGenerationProgress? =
            mapGenerationStatusToProgress(progressPhase, progressMessage)
    }

    internal companion object {
        private const val TAG = "StratumProgramGenerator"
        private const val CALLABLE_NAME = "generateProgram"
        private const val STATUS_CALLABLE_NAME = "getProgramGenerationStatus"
        const val CALLABLE_TIMEOUT_SECONDS = 600L
        private const val INITIAL_POLL_INTERVAL_MS = 1_500L
        private const val SLOW_POLL_INTERVAL_MS = 3_000L
        private const val BACKOFF_AFTER_MS = 30_000L
        private const val POLL_TIMEOUT_MS = CALLABLE_TIMEOUT_SECONDS * 1_000L
        private const val POLL_REQUEST_TIMEOUT_SECONDS = 10L
        private const val TIMEOUT_RETRY_DELAY_MS = 2_000L
        private const val DETAIL_COUNT =  120

        /**
         * Maps Firestore `progressPhase` / `progressMessage` from `getProgramGenerationStatus` to
         * domain progress. The UI headline comes from [ProgramGenerationProgress.phase], not [detail].
         */
        internal fun mapGenerationStatusToProgress(
            progressPhase: String?,
            progressMessage: String?,
        ): ProgramGenerationProgress? {
            val phase =
                when (progressPhase) {
                    "reviewing_profile" -> ProgramGenerationProgressPhase.ReviewingProfile
                    "validating_program" -> ProgramGenerationProgressPhase.ValidatingProgram
                    "drafting_program" -> ProgramGenerationProgressPhase.DraftingProgram
                    "refining_program" -> ProgramGenerationProgressPhase.RefiningProgram
                    "split_planning" -> ProgramGenerationProgressPhase.SplitPlanning
                    "building_workouts" -> ProgramGenerationProgressPhase.BuildingWorkouts
                    "integrating" -> ProgramGenerationProgressPhase.Integrating
                    "failed" -> ProgramGenerationProgressPhase.Failed
                    null -> null
                    else -> null
                } ?: return null
            return ProgramGenerationProgress(phase = phase, detail = progressMessage)
        }

        internal fun validatePayloadJson(payload: String) {
            try {
                Json.parseToJsonElement(payload)
            } catch (error: Exception) {
                throw ProgramGenerationException(
                    message = "Program setup could not be serialized. Please review your onboarding answers and try again.",
                    cause = error,
                )
            }
        }

        /** Parses the Callable response into a domain [Program], assigning UUID ids at parse time. */
        fun parseProgram(data: Any?): Program = ProgramResponseParser.parse(data)
    }
}
