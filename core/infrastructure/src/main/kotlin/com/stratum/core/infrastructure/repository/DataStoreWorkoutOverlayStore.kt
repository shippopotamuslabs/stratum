package com.stratum.core.infrastructure.repository

import android.content.Context
import com.stratum.core.infrastructure.workoutoverlay.toDomain
import com.stratum.core.infrastructure.workoutoverlay.toDto
import com.stratum.core.infrastructure.workoutoverlay.workoutOverlayDataStore
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.application.workout.WorkoutOverlayStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed implementation of [WorkoutOverlayStore]. Persists the transient
 * [WorkoutOverlay] to a single JSON file so a training session survives an app process death.
 *
 * The record is tagged with the active training session's id; [load] returns the overlay only
 * when the stored session id matches, so a stale record left over from a different (e.g.
 * previously abandoned) session is ignored — not restored — into the resumed session.
 */
@Singleton
class DataStoreWorkoutOverlayStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : WorkoutOverlayStore {

    override suspend fun load(sessionId: TrainingSessionId): WorkoutOverlay? {
        val record = context.workoutOverlayDataStore.data.first()
        if (record.sessionId != sessionId.value) return null
        return record.overlay.toDomain()
    }

    override suspend fun save(sessionId: TrainingSessionId, overlay: WorkoutOverlay) {
        context.workoutOverlayDataStore.updateData { current ->
            current.copy(sessionId = sessionId.value, overlay = overlay.toDto())
        }
    }

    override suspend fun clear() {
        context.workoutOverlayDataStore.updateData { _ ->
            com.stratum.core.infrastructure.workoutoverlay.WorkoutOverlayRecord()
        }
    }
}
