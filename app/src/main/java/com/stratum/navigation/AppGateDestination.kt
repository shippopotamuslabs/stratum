package com.stratum.navigation

import androidx.navigation3.runtime.NavKey
import com.stratum.domain.subscription.EntitlementState
import com.stratum.feature.onboarding.navigation.OnboardingKey
import com.stratum.feature.program.navigation.CreateProgramKey
import com.stratum.feature.subscription.navigation.PaywallKey

/**
 * Top-level destination after Firebase Auth. Mapped in [EntitlementGateViewModel] so
 * [StratumNavHost] never reads domain entitlement types.
 */
enum class AppGateDestination {
    Loading,
    Paywall,
    Onboarding,
    Main,
}

internal fun resolveAppGateDestination(
    isComplete: Boolean?,
    entitlement: EntitlementState,
): AppGateDestination =
    when {
        isComplete == null || entitlement is EntitlementState.Loading ->
            AppGateDestination.Loading
        entitlement is EntitlementState.NotEntitled ->
            AppGateDestination.Paywall
        isComplete == false ->
            AppGateDestination.Onboarding
        else ->
            AppGateDestination.Main
    }

internal fun AppGateDestination.toStartNavKey(): NavKey =
    when (this) {
        AppGateDestination.Loading ->
            error("startKey requested while the entitlement gate is still loading")
        AppGateDestination.Paywall -> PaywallKey(isDismissable = false)
        AppGateDestination.Onboarding -> OnboardingKey
        AppGateDestination.Main -> MainTabKey(isPreviewMode = false)
    }

internal fun shouldPopAppGateBackStack(backStack: List<NavKey>): Boolean {
    if (backStack.size <= 1) return false
    val top = backStack.last()
    return top !is PaywallKey || top.isDismissable
}

internal fun reconcileAppGateBackStack(
    backStack: MutableList<NavKey>,
    destination: AppGateDestination,
) {
    when (destination) {
        AppGateDestination.Loading -> Unit
        AppGateDestination.Paywall -> {
            if (backStack.isOnlyHardPaywall()) return
            backStack.replaceWith(PaywallKey(isDismissable = false))
        }
        AppGateDestination.Onboarding -> {
            if (!backStack.hasHardPaywall() && backStack.isOnboardingGate()) return
            backStack.replaceWith(OnboardingKey)
        }
        AppGateDestination.Main -> {
            if (!backStack.hasHardPaywall() && backStack.any { it is MainTabKey }) return
            val preview = backStack.isOnboardingGate()
            backStack.replaceWith(MainTabKey(isPreviewMode = preview))
        }
    }
}

private fun MutableList<NavKey>.replaceWith(key: NavKey) {
    removeAll { true }
    add(key)
}

private fun List<NavKey>.hasHardPaywall(): Boolean =
    any { it is PaywallKey && !it.isDismissable }

private fun List<NavKey>.isOnlyHardPaywall(): Boolean =
    size == 1 && hasHardPaywall()

private fun List<NavKey>.isOnboardingGate(): Boolean =
    any { it is OnboardingKey } ||
        any { it is CreateProgramKey && it.finishOnboarding }
