package com.stratum.core.data.repository

import com.stratum.domain.lift.MAX_DISMISSED_EQUIPMENT_PROPOSAL_FINGERPRINTS
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Robolectric tests for [DataStoreEquipmentProposalPreferences]. Shared DataStore file is
 * process-scoped — each test clears dismissals in [setup].
 */
@ExtendWith(RobolectricExtension::class)
@DisplayName("DataStoreEquipmentProposalPreferences")
class DataStoreEquipmentProposalPreferencesTest {

    private lateinit var store: DataStoreEquipmentProposalPreferences

    @BeforeEach
    fun setup() = runTest {
        store = DataStoreEquipmentProposalPreferences(RuntimeEnvironment.getApplication())
        store.clearAll()
    }

    @Test
    @DisplayName("isDismissed is false by default")
    fun defaultNotDismissed() = runTest {
        assertFalse(store.isDismissed("cable|lat-pulldown|2.5"))
    }

    @Test
    @DisplayName("enabled is true by default and setEnabled persists")
    fun enabledToggle() = runTest {
        assertTrue(store.isEnabled())
        store.setEnabled(false)
        assertFalse(store.isEnabled())
        store.setEnabled(true)
        assertTrue(store.isEnabled())
    }

    @Test
    @DisplayName("dismiss persists and isDismissed returns true")
    fun dismissPersists() = runTest {
        val fp = "cable|lat-pulldown|2.5"
        store.dismiss(fp)
        assertTrue(store.isDismissed(fp))
        assertFalse(store.isDismissed("other"))
    }

    @Test
    @DisplayName("clearDismiss removes a fingerprint")
    fun clearDismissWorks() = runTest {
        val fp = "cable|lat-pulldown|2.5"
        store.dismiss(fp)
        store.clearDismiss(fp)
        assertFalse(store.isDismissed(fp))
    }

    @Test
    @DisplayName("dismiss caps retained fingerprints at max (oldest dropped)")
    fun dismissCapsAtMax() = runTest {
        val total = MAX_DISMISSED_EQUIPMENT_PROPOSAL_FINGERPRINTS + 5
        repeat(total) { i ->
            store.dismiss("fp-$i")
        }
        // Oldest should be gone.
        assertFalse(store.isDismissed("fp-0"))
        assertFalse(store.isDismissed("fp-4"))
        // Newest retained.
        assertTrue(store.isDismissed("fp-${total - 1}"))
        assertTrue(store.isDismissed("fp-${total - MAX_DISMISSED_EQUIPMENT_PROPOSAL_FINGERPRINTS}"))
    }
}
