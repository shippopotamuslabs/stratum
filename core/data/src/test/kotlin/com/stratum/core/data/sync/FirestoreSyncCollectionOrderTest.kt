package com.stratum.core.data.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirestoreSyncCollectionOrderTest {
    @Test
    fun `parents precede children in hydrate order`() {
        val order = SyncCollection.HYDRATE_ORDER.map { it.firestorePath }
        fun indexOf(name: String) = order.indexOf(name)

        assertTrue(indexOf("lifts") < indexOf("increment_tiers"))
        assertTrue(indexOf("programs") < indexOf("workouts"))
        assertTrue(indexOf("workouts") < indexOf("workout_lifts"))
        assertTrue(indexOf("workout_lifts") < indexOf("lift_sets"))
        assertTrue(indexOf("training_sessions") < indexOf("logged_sets"))
        assertTrue(indexOf("programs") < indexOf("program_change_events"))
    }

    @Test
    fun `allowlist matches hydrate order set`() {
        assertEquals(
            SyncCollection.HYDRATE_ORDER.map { it.firestorePath }.toSet(),
            SyncCollection.ALLOWLIST,
        )
    }
}
