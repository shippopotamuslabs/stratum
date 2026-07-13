package com.stratum.feature.workout.notification

import com.stratum.domain.training.ObserveCycleProgressUseCase
import com.stratum.domain.training.TrainingSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Derives process-scoped active-workout surfaces from the domain active session.
 *
 * - Domain session present → publish [ActiveWorkoutTracker] (for background notifications).
 * - Domain session null → [LiveWorkoutSurfaces.clearSession] (rest + active notifs).
 *
 * Rest timer is still started from the live workout UI; this only owns the **active snapshot**
 * and full clear on session end. Discard from Programs no longer needs feature→feature deps.
 */
@Singleton
class DomainActiveSessionSurfaceSync @Inject constructor(
    private val observeCycleProgress: ObserveCycleProgressUseCase,
    private val liveWorkoutSurfaces: LiveWorkoutSurfaces,
) {
    fun start(scope: CoroutineScope) {
        start(
            scope = scope,
            domainActiveSession = observeCycleProgress().map { progress -> progress.activeSession },
        )
    }

    /** Testable entry: full set/clear from a domain-active-session stream. */
    internal fun start(scope: CoroutineScope, domainActiveSession: Flow<TrainingSession?>) {
        scope.launch {
            domainActiveSession
                .distinctUntilChanged { old, new -> old?.id == new?.id && old?.workoutId == new?.workoutId }
                .collect { domainSession ->
                    if (domainSession == null) {
                        liveWorkoutSurfaces.clearSession()
                    } else {
                        publishActive(domainSession)
                    }
                }
        }
    }

    private fun publishActive(session: TrainingSession) {
        val workoutId = session.workoutId?.value ?: session.id.value
        val workoutName = session.workoutNameSnapshot?.takeIf { it.isNotBlank() } ?: "Workout"
        liveWorkoutSurfaces.activeWorkout.setActive(
            workoutId = workoutId,
            workoutName = workoutName,
            startedAtEpochMs = session.startedAt.toEpochMilli(),
        )
    }
}
