package com.stratum.core.inference

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.program.generation.naming.ProgramNamingPort
import com.stratum.domain.program.generation.naming.ProgramNamingRequest
import com.stratum.domain.program.generation.naming.ProgramNamingResult
import com.stratum.domain.program.generation.naming.ProgramNamingWorkoutName
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Thin Firebase Callable client for the `nameGeneratedProgram` endpoint.
 */
@Singleton
class FirebaseProgramNamingAdapter @Inject constructor(
    private val loggingPort: LoggingPort,
) : ProgramNamingPort {
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun nameProgram(request: ProgramNamingRequest): Result<ProgramNamingResult> =
        runCatching {
            requireAuth()
            val callable =
                functions
                    .getHttpsCallable(CALLABLE_NAME)
                    .withTimeout(CALLABLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val payload = encodeRequest(request)
            val result = callable.call(payload).await()
            parseNameProgramResponse(result.data)
        }.onFailure { error ->
            FirebaseCallableErrorLogger.logFailure(loggingPort, TAG, CALLABLE_NAME, error)
        }

    private fun requireAuth() {
        checkNotNull(auth.currentUser) { "Sign-in required for program naming." }
    }

    private fun encodeRequest(request: ProgramNamingRequest): Map<String, Any?> =
        mapOf("namingPayload" to FirebaseCallablePayloadEncoder.encodeNamingPayload(request.namingPayload))

    private companion object {
        private const val TAG = "StratumProgramNaming"
        const val CALLABLE_NAME = "nameGeneratedProgram"
        const val CALLABLE_TIMEOUT_SECONDS = 30L
    }
}

@Suppress("UNCHECKED_CAST")
internal fun parseNameProgramResponse(data: Any?): ProgramNamingResult {
    val map = data as? Map<*, *> ?: return ProgramNamingResult(programName = null, workoutNames = emptyList())
    val programName = (map["programName"] as? String)?.takeIf { it.isNotBlank() }
    val workoutsRaw = map["workouts"] as? List<*> ?: return ProgramNamingResult(programName, emptyList())
    val workouts =
        workoutsRaw.mapNotNull { entry ->
            val row = entry as? Map<*, *> ?: return@mapNotNull null
            val position = (row["position"] as? Number)?.toInt() ?: return@mapNotNull null
            val name = (row["name"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ProgramNamingWorkoutName(position = position, name = name)
        }
    return ProgramNamingResult(programName = programName, workoutNames = workouts)
}
