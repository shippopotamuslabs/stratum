package com.stratum.core.data.repository

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.LiveSetRecommendation
import com.stratum.domain.workout.LoggedSetValue
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.WorkoutOverlay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Robolectric tests for [DataStoreWorkoutOverlayStore]. Verifies save/load round-trip,
 * session-id guarding (a stale record from a different session is not restored), and that
 * [clear] discards the stored overlay.
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("DataStoreWorkoutOverlayStore")
class DataStoreWorkoutOverlayStoreTest {

    private lateinit var store: DataStoreWorkoutOverlayStore

    @BeforeEach
    fun setup() {
        store = DataStoreWorkoutOverlayStore(RuntimeEnvironment.getApplication())
    }

    @Test
    @DisplayName("load returns null when nothing is stored for the session")
    fun loadEmptyReturnsNull() = runTest {
        assertNull(store.load(TrainingSessionId("session-1")))
    }

    @Test
    @DisplayName("save then load returns the overlay for the same session id")
    fun saveLoadRoundTrip() = runTest {
        val sessionId = TrainingSessionId("session-1")
        val slotId = SetSlotId.Template(LiftSetId("ls-1"))
        val overlay = WorkoutOverlay(
            loggedSets = mapOf(
                slotId to LoggedSetValue(slotId, Weight(225.0, Weight.WeightUnit.Lb), reps = 5, rir = 1),
            ),
            swappedLifts = mapOf(WorkoutLiftId("wl-1") to LiftId("front-squat")),
        )

        store.save(sessionId, overlay)
        val restored = store.load(sessionId)

        assertNotNull(restored)
        assertEquals(overlay, restored)
    }

    @Test
    @DisplayName("save then load preserves live recommendations")
    fun saveLoadPreservesRecommendations() = runTest {
        val sessionId = TrainingSessionId("session-recs")
        val slotId = SetSlotId.Template(LiftSetId("ls-3"))
        val overlay = WorkoutOverlay(
            recommendations = mapOf(
                slotId to LiveSetRecommendation(Weight(315.0, Weight.WeightUnit.Lb), reps = 5),
            ),
        )
        store.save(sessionId, overlay)
        val restored = store.load(sessionId)
        assertNotNull(restored)
        assertEquals(overlay.recommendations, restored!!.recommendations)
    }

    @Test
    @DisplayName("load returns null when the stored session id differs (stale record guard)")
    fun loadDifferentSessionReturnsNull() = runTest {
        val overlay = WorkoutOverlay(
            swappedLifts = mapOf(WorkoutLiftId("wl-1") to LiftId("front-squat")),
        )
        store.save(TrainingSessionId("session-A"), overlay)

        assertNull(store.load(TrainingSessionId("session-B")))
    }

    @Test
    @DisplayName("clear drops the stored overlay")
    fun clearDropsOverlay() = runTest {
        val sessionId = TrainingSessionId("session-1")
        store.save(sessionId, WorkoutOverlay(swappedLifts = mapOf(WorkoutLiftId("wl-1") to LiftId("x"))))

        store.clear()

        assertNull(store.load(sessionId))
    }
}
