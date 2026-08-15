package com.stratum.navigation

import androidx.navigation3.runtime.NavKey
import com.stratum.domain.subscription.EntitlementSource
import com.stratum.domain.subscription.EntitlementState
import com.stratum.feature.onboarding.navigation.OnboardingKey
import com.stratum.feature.program.navigation.CreateProgramKey
import com.stratum.feature.settings.navigation.SettingsKey
import com.stratum.feature.subscription.navigation.PaywallKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EntitlementGateTest {

    @Test
    @DisplayName("Destination mapping: loading or unknown completeness stays Loading")
    fun loadingAndUnknownMapToLoading() {
        assertEquals(
            AppGateDestination.Loading,
            resolveAppGateDestination(isComplete = null, EntitlementState.Loading),
        )
        assertEquals(
            AppGateDestination.Loading,
            resolveAppGateDestination(isComplete = true, EntitlementState.Loading),
        )
        assertEquals(
            AppGateDestination.Loading,
            resolveAppGateDestination(
                isComplete = null,
                EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription),
            ),
        )
        assertEquals(
            AppGateDestination.Loading,
            resolveAppGateDestination(isComplete = false, EntitlementState.Loading),
        )
    }

    @Test
    @DisplayName("Destination mapping: NotEntitled is Paywall before onboarding")
    fun notEntitledMapsToPaywall() {
        assertEquals(
            AppGateDestination.Paywall,
            resolveAppGateDestination(isComplete = false, EntitlementState.NotEntitled()),
        )
        assertEquals(
            AppGateDestination.Paywall,
            resolveAppGateDestination(isComplete = true, EntitlementState.NotEntitled()),
        )
    }

    @Test
    @DisplayName("Destination mapping: Entitled routes by onboarding completeness")
    fun entitledRoutesByOnboarding() {
        val play = EntitlementState.Entitled(EntitlementSource.GooglePlaySubscription)
        val complimentary = EntitlementState.Entitled(EntitlementSource.ComplimentaryAccess)
        assertEquals(AppGateDestination.Onboarding, resolveAppGateDestination(false, play))
        assertEquals(AppGateDestination.Main, resolveAppGateDestination(true, play))
        assertEquals(AppGateDestination.Onboarding, resolveAppGateDestination(false, complimentary))
        assertEquals(AppGateDestination.Main, resolveAppGateDestination(true, complimentary))
    }

    @Test
    @DisplayName("startKey is hard paywall, onboarding, or main — never Loading")
    fun startKeyMatchesDestination() {
        assertEquals(PaywallKey(isDismissable = false), AppGateDestination.Paywall.toStartNavKey())
        assertEquals(OnboardingKey, AppGateDestination.Onboarding.toStartNavKey())
        assertEquals(MainTabKey(isPreviewMode = false), AppGateDestination.Main.toStartNavKey())
        assertThrows<IllegalStateException> { AppGateDestination.Loading.toStartNavKey() }
    }

    @Test
    @DisplayName("System back does not pop size 1 or a non-dismissable paywall")
    fun backDoesNotPopHardPaywall() {
        assertFalse(shouldPopAppGateBackStack(listOf(PaywallKey(isDismissable = false))))
        assertFalse(shouldPopAppGateBackStack(listOf(OnboardingKey)))
        assertFalse(
            shouldPopAppGateBackStack(
                listOf(MainTabKey(), PaywallKey(isDismissable = false)),
            ),
        )
        assertTrue(
            shouldPopAppGateBackStack(
                listOf(MainTabKey(), SettingsKey, PaywallKey(isDismissable = true)),
            ),
        )
        assertTrue(shouldPopAppGateBackStack(listOf(MainTabKey(), SettingsKey)))
    }

    @Test
    @DisplayName("NotEntitled replaces any stack with a lone hard paywall")
    fun notEntitledClearsStackToHardPaywall() {
        val stack = mutableListOf(
            MainTabKey(),
            SettingsKey,
            PaywallKey(isDismissable = true),
        )
        reconcileAppGateBackStack(stack, AppGateDestination.Paywall)
        assertEquals(listOf(PaywallKey(isDismissable = false)), stack)
    }

    @Test
    @DisplayName("Hard paywall is left in place when already the only destination")
    fun hardPaywallIdempotent() {
        val stack = mutableListOf<NavKey>(PaywallKey(isDismissable = false))
        reconcileAppGateBackStack(stack, AppGateDestination.Paywall)
        assertEquals(listOf(PaywallKey(isDismissable = false)), stack)
    }

    @Test
    @DisplayName("Becoming entitled from hard paywall lands on onboarding when incomplete")
    fun entitledIncompleteReplacesHardPaywallWithOnboarding() {
        val stack = mutableListOf<NavKey>(PaywallKey(isDismissable = false))
        reconcileAppGateBackStack(stack, AppGateDestination.Onboarding)
        assertEquals(listOf(OnboardingKey), stack)
    }

    @Test
    @DisplayName("Onboarding stack is left in place while still incomplete")
    fun onboardingStackIdempotent() {
        val stack = mutableListOf(
            OnboardingKey,
            CreateProgramKey(startInScratch = true, finishOnboarding = true),
        )
        reconcileAppGateBackStack(stack, AppGateDestination.Onboarding)
        assertEquals(
            listOf(
                OnboardingKey,
                CreateProgramKey(startInScratch = true, finishOnboarding = true),
            ),
            stack,
        )
    }

    @Test
    @DisplayName("Becoming entitled from hard paywall lands on main when onboarding is complete")
    fun entitledCompleteReplacesHardPaywallWithMain() {
        val stack = mutableListOf<NavKey>(PaywallKey(isDismissable = false))
        reconcileAppGateBackStack(stack, AppGateDestination.Main)
        assertEquals(listOf(MainTabKey(isPreviewMode = false)), stack)
    }

    @Test
    @DisplayName("Completing onboarding lands on preview main")
    fun completingOnboardingUsesPreviewMain() {
        val stack = mutableListOf<NavKey>(OnboardingKey)
        reconcileAppGateBackStack(stack, AppGateDestination.Main)
        assertEquals(listOf(MainTabKey(isPreviewMode = true)), stack)
    }

    @Test
    @DisplayName("Main plus dismissable paywall is left in place while still entitled")
    fun mainWithManagePaywallIdempotent() {
        val stack = mutableListOf(
            MainTabKey(),
            SettingsKey,
            PaywallKey(isDismissable = true),
        )
        reconcileAppGateBackStack(stack, AppGateDestination.Main)
        assertEquals(
            listOf(MainTabKey(), SettingsKey, PaywallKey(isDismissable = true)),
            stack,
        )
    }
}
