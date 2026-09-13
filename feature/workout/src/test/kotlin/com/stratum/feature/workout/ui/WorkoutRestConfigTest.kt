package com.stratum.feature.workout.ui

import androidx.compose.runtime.mutableStateMapOf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("hasUnsavedRestChanges")
class WorkoutRestConfigTest {

    @Test
    @DisplayName("empty maps have no unsaved changes")
    fun emptyMapsHaveNoChanges() {
        assertFalse(hasUnsavedRestChanges(emptyMap(), emptyMap()))
    }

    @Test
    @DisplayName("identical Map contents have no unsaved changes")
    fun identicalMapsHaveNoChanges() {
        val baseline = mapOf("slot-1" to 120, "slot-2" to 90)
        val pending = mapOf("slot-1" to 120, "slot-2" to 90)
        assertFalse(hasUnsavedRestChanges(baseline, pending))
    }

    @Test
    @DisplayName("SnapshotStateMap with identical contents does not trigger false positive unsaved changes")
    fun snapshotStateMapWithIdenticalContentsHasNoChanges() {
        val baseline = mapOf("slot-1" to 120, "slot-2" to 90)
        val pending = mutableStateMapOf<String, Int>().apply { putAll(baseline) }
        assertFalse(hasUnsavedRestChanges(baseline, pending))
    }

    @Test
    @DisplayName("modified rest duration triggers unsaved changes")
    fun modifiedRestValueHasChanges() {
        val baseline = mapOf("slot-1" to 120, "slot-2" to 90)
        val pending = mutableStateMapOf<String, Int>().apply { putAll(baseline) }
        pending["slot-1"] = 150
        assertTrue(hasUnsavedRestChanges(baseline, pending))
    }

    @Test
    @DisplayName("reverting rest duration back to baseline clears unsaved changes")
    fun revertedRestValueHasNoChanges() {
        val baseline = mapOf("slot-1" to 120, "slot-2" to 90)
        val pending = mutableStateMapOf<String, Int>().apply { putAll(baseline) }
        pending["slot-1"] = 150
        assertTrue(hasUnsavedRestChanges(baseline, pending))
        pending["slot-1"] = 120
        assertFalse(hasUnsavedRestChanges(baseline, pending))
    }

    @Test
    @DisplayName("differing map sizes report unsaved changes")
    fun differingSizesHaveChanges() {
        val baseline = mapOf("slot-1" to 120)
        val pending = mapOf("slot-1" to 120, "slot-2" to 90)
        assertTrue(hasUnsavedRestChanges(baseline, pending))
        assertTrue(hasUnsavedRestChanges(pending, baseline))
    }
}
