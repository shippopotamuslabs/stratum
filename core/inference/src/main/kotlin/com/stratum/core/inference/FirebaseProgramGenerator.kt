package com.stratum.core.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramGeneratorPort
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/**
 * Thin Firebase Callable client for the `generateProgram` endpoint (OS-7). Calls the Callable with
 * the serialized onboarding payload and parses the normalized `Program` JSON returned by the server
 * post-pass into domain models. Assigns UUID value-class ids client-side at parse time (D1) — the
 * server returns structure only, no ids.
 *
 * Mirrors [FirebaseCustomLiftInferenceAdapter] for auth + callable shape + failure logging. Requires
 * an authenticated Firebase session (OS-0 gate) and a valid App Check token when the callable
 * enforces App Check server-side.
 */
@Singleton
class FirebaseProgramGenerator @Inject constructor(
    private val loggingPort: LoggingPort,
) : ProgramGeneratorPort {
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun generateProgram(payload: String): Result<Program> =
        runCatching {
            requireAuth()
            Companion.validatePayloadJson(payload)
            val callable =
                functions
                    .getHttpsCallable(CALLABLE_NAME)
                    .withTimeout(CALLABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val result = callable.call(mapOf("payload" to payload)).await()
            parseProgram(result.data)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                FirebaseCallableErrorLogger.logFailure(loggingPort, TAG, CALLABLE_NAME, error)
                Result.failure(
                    ProgramGenerationException(
                        message = ProgramGenerationErrorMapper.userMessage(error),
                        cause = error,
                    ),
                )
            },
        )

    private fun requireAuth() {
        checkNotNull(auth.currentUser) { "Sign-in required for program generation." }
    }

    internal companion object {
        private const val TAG = "StratumProgramGenerator"
        private const val CALLABLE_NAME = "generateProgram"
        const val CALLABLE_TIMEOUT_SECONDS = 600L

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
