package com.stratum.application.subscription

import kotlinx.coroutines.flow.Flow
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.domain.subscription.SubscriptionProduct
import com.stratum.domain.subscription.SubscriptionPurchase

/**
 * A billing operation failure recorded as data (§10 diagnostics channel).
 * The use-case boundary drains each diagnostic through `LoggingPort.error`
 * with the throwable attached; diagnostics never reach UI state.
 */
sealed interface BillingDiagnostic {
    data class OperationFailed(
        val cause: Throwable,
    ) : BillingDiagnostic
}

/** Outcome of querying the subscription product catalog at the [BillingPort]. */
sealed interface SubscriptionProductsOutcome {
    data class Success(val products: List<SubscriptionProduct>) : SubscriptionProductsOutcome

    data class Failed(
        val message: String?,
        val diagnostics: List<BillingDiagnostic>,
    ) : SubscriptionProductsOutcome
}

/** Outcome of launching the billing flow at the [BillingPort]. */
sealed interface BillingFlowOutcome {
    data object Success : BillingFlowOutcome

    data class Failed(
        val message: String?,
        val diagnostics: List<BillingDiagnostic>,
    ) : BillingFlowOutcome
}

/** Outcome of restoring purchases at the [BillingPort]. */
sealed interface RestorePurchasesOutcome {
    data object Success : RestorePurchasesOutcome

    data class Failed(
        val message: String?,
        val diagnostics: List<BillingDiagnostic>,
    ) : RestorePurchasesOutcome
}

/**
 * Google Play Billing boundary (STR-243 remediation, Sean's E1 ruling).
 * Operations return their sealed outcomes: no exception crosses this port in
 * place of an outcome (coroutine cancellation still propagates). The adapter
 * does not log these failures — the use-case boundary drains the [Failed]
 * branch's diagnostics exactly once.
 */
interface BillingPort {
    val activePurchases: Flow<List<SubscriptionPurchase>>

    val purchaseResults: Flow<BillingPurchaseResult>

    suspend fun querySubscriptionProducts(): SubscriptionProductsOutcome

    suspend fun launchBillingFlow(product: SubscriptionProduct): BillingFlowOutcome

    suspend fun restorePurchases(): RestorePurchasesOutcome
}
