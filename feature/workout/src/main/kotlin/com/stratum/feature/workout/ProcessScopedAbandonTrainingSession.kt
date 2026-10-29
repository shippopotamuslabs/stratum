package com.stratum.feature.workout

import com.stratum.domain.di.ApplicationScope
import com.stratum.application.training.AbandonTrainingSessionUseCase
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.feature.workout.notification.LiveWorkoutSurfaces
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Abandons a training session on the process [ApplicationScope] so navigation cannot cancel
 * the domain delete, and eagerly clears rest/active notification surfaces.
 */
@Singleton
class ProcessScopedAbandonTrainingSession @Inject constructor(
    private val abandonTrainingSession: AbandonTrainingSessionUseCase,
    private val liveWorkoutSurfaces: LiveWorkoutSurfaces,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    /**
     * Clears live surfaces immediately, then deletes [sessionId] under [NonCancellable].
     * Invokes [onResult] on [applicationScope] (Main.immediate in production).
     */
    fun abandon(
        sessionId: TrainingSessionId,
        onResult: (Result<Unit>) -> Unit,
    ) {
        liveWorkoutSurfaces.clearSession()
        applicationScope.launch {
            val result =
                withContext(NonCancellable) {
                    abandonTrainingSession(sessionId)
                }
            onResult(result)
        }
    }
}
