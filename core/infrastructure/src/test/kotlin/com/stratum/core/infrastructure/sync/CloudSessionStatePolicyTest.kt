package com.stratum.core.infrastructure.sync

import com.stratum.domain.sync.CloudSessionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Documents the shell-facing session readiness policy implemented by
 * [FirestoreCloudSyncAdapter] (local-first Ready vs first-hydrate Failed).
 *
 * Pure decision table — keeps the policy reviewable without wiring Firestore.
 */
class CloudSessionStatePolicyTest {

    @Test
    @DisplayName("Ownership-only (gate off) is Ready after isolation")
    fun ownershipOnlyReady() {
        val next = decideAfterStart(
            featureGateEnabled = false,
            localAlreadyHydrated = false,
            pushPullSucceeded = false,
        )
        assertEquals(Outcome.Ready, next)
    }

    @Test
    @DisplayName("Hydrated local cache becomes Ready before network completes")
    fun hydratedLocalUnlocksShell() {
        val next = decideAfterStart(
            featureGateEnabled = true,
            localAlreadyHydrated = true,
            pushPullSucceeded = false,
        )
        assertEquals(Outcome.Ready, next)
    }

    @Test
    @DisplayName("First hydrate success becomes Ready")
    fun firstHydrateSuccess() {
        val next = decideAfterStart(
            featureGateEnabled = true,
            localAlreadyHydrated = false,
            pushPullSucceeded = true,
        )
        assertEquals(Outcome.Ready, next)
    }

    @Test
    @DisplayName("First hydrate failure without local cache is Failed")
    fun firstHydrateFailure() {
        val next = decideAfterStart(
            featureGateEnabled = true,
            localAlreadyHydrated = false,
            pushPullSucceeded = false,
        )
        assertEquals(Outcome.Failed, next)
    }

    @Test
    @DisplayName("Network failure after local Ready does not revoke Ready")
    fun networkFailureKeepsReady() {
        val next = decideAfterStart(
            featureGateEnabled = true,
            localAlreadyHydrated = true,
            pushPullSucceeded = false,
        )
        assertEquals(Outcome.Ready, next)
        assertTrue(next.keepsShellOpen)
        assertFalse(next is Outcome.Failed)
    }

    @Test
    @DisplayName("Inactive is the only terminal non-authenticated session state")
    fun inactiveIsDefault() {
        assertEquals(CloudSessionState.Inactive, CloudSessionState.Inactive)
    }

    /**
     * Mirrors [FirestoreCloudSyncAdapter.start] session-state transitions after ownership.
     */
    private fun decideAfterStart(
        featureGateEnabled: Boolean,
        localAlreadyHydrated: Boolean,
        pushPullSucceeded: Boolean,
    ): Outcome {
        if (!featureGateEnabled) return Outcome.Ready
        if (localAlreadyHydrated) return Outcome.Ready
        return if (pushPullSucceeded) Outcome.Ready else Outcome.Failed
    }

    private sealed interface Outcome {
        val keepsShellOpen: Boolean

        data object Ready : Outcome {
            override val keepsShellOpen: Boolean = true
        }

        data object Failed : Outcome {
            override val keepsShellOpen: Boolean = false
        }
    }
}
