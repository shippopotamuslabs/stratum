package com.stratum.application.subscription

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import com.stratum.application.diagnostics.OperationalDiagnostic
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.domain.subscription.EntitlementDiagnostic
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.subscription.SubscriptionProduct

/** Shared log tag for entitlement boundary drains. */
const val SUBSCRIPTION_REPO_TAG = "SubscriptionRepo"

/**
 * Outcome of loading the subscription product catalog (STR-243; STR-237
 * Route (i)). On failure the port's diagnostics are re-rendered as
 * [OperationalDiagnostic]s on the [Failed] branch; the presentation boundary
 * drains them exactly once.
 */
sealed interface GetSubscriptionProductsResult {
    data class Success(val products: List<SubscriptionProduct>) : GetSubscriptionProductsResult

    data class Failed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : GetSubscriptionProductsResult
}

/**
 * Outcome of launching the billing flow (STR-243). [Failed] carries the
 * adapter's message where one exists, following the shipped
 * [BillingPurchaseResult.Failed] pattern — presentation falls back to its own
 * copy when it is null.
 */
sealed interface PurchaseSubscriptionResult {
    data object Success : PurchaseSubscriptionResult

    data class Failed(
        val message: String?,
        val diagnostics: List<OperationalDiagnostic>,
    ) : PurchaseSubscriptionResult
}

/** Outcome of restoring purchases (STR-243); same message pattern as [PurchaseSubscriptionResult]. */
sealed interface RestorePurchasesResult {
    data class Success(val entitlement: EntitlementState) : RestorePurchasesResult

    data class Failed(
        val message: String?,
        val diagnostics: List<OperationalDiagnostic>,
    ) : RestorePurchasesResult
}

class ObserveEntitlementUseCase @Inject constructor(
    private val repository: EntitlementRepository,
) {
    operator fun invoke(): Flow<EntitlementState> =
        repository.observeEntitlement()
}

class ObserveEntitlementDiagnosticsUseCase @Inject constructor(
    private val repository: EntitlementRepository,
) {
    operator fun invoke(): Flow<EntitlementDiagnostic> =
        repository.observeEntitlementDiagnostics()
}

class GetSubscriptionProductsUseCase @Inject constructor(
    private val billingPort: BillingPort,
) {
    suspend operator fun invoke(): GetSubscriptionProductsResult =
        when (val outcome = billingPort.querySubscriptionProducts()) {
            is SubscriptionProductsOutcome.Success ->
                GetSubscriptionProductsResult.Success(outcome.products)
            is SubscriptionProductsOutcome.Failed ->
                GetSubscriptionProductsResult.Failed(
                    diagnostics = outcome.diagnostics.map { diagnostic ->
                        when (diagnostic) {
                            is BillingDiagnostic.OperationFailed ->
                                OperationalDiagnostic(
                                    tag = TAG,
                                    message = "Failed to load subscription products: " +
                                        "${diagnostic.cause.message}",
                                    cause = diagnostic.cause,
                                )
                        }
                    },
                )
        }

    private companion object {
        const val TAG = "GetSubscriptionProducts"
    }
}

class PurchaseSubscriptionUseCase @Inject constructor(
    private val billingPort: BillingPort,
) {
    suspend operator fun invoke(product: SubscriptionProduct): PurchaseSubscriptionResult =
        when (val outcome = billingPort.launchBillingFlow(product)) {
            BillingFlowOutcome.Success -> PurchaseSubscriptionResult.Success
            is BillingFlowOutcome.Failed ->
                PurchaseSubscriptionResult.Failed(
                    message = outcome.message,
                    diagnostics = outcome.diagnostics.map { diagnostic ->
                        when (diagnostic) {
                            is BillingDiagnostic.OperationFailed ->
                                OperationalDiagnostic(
                                    tag = TAG,
                                    message = "Failed to launch billing flow: " +
                                        "${diagnostic.cause.message}",
                                    cause = diagnostic.cause,
                                )
                        }
                    },
                )
        }

    private companion object {
        const val TAG = "PurchaseSubscription"
    }
}

class RestorePurchasesUseCase @Inject constructor(
    private val billingPort: BillingPort,
    private val repository: EntitlementRepository,
) {
    suspend operator fun invoke(): RestorePurchasesResult {
        when (val outcome = billingPort.restorePurchases()) {
            RestorePurchasesOutcome.Success -> Unit
            is RestorePurchasesOutcome.Failed -> return restoreFailed(outcome.diagnostics, outcome.message)
        }
        return when (val outcome = repository.refreshEntitlement()) {
            is RefreshEntitlementOutcome.Success ->
                RestorePurchasesResult.Success(outcome.state)
            is RefreshEntitlementOutcome.Failed ->
                restoreFailed(outcome.diagnostics, null)
        }
    }

    private fun restoreFailed(
        diagnostics: List<BillingDiagnostic>,
        message: String?,
    ): RestorePurchasesResult {
        var failureMessage = message
        val operationalDiagnostics = diagnostics.map { diagnostic ->
            when (diagnostic) {
                is BillingDiagnostic.OperationFailed -> {
                    if (failureMessage == null) failureMessage = diagnostic.cause.message
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to restore purchases: ${diagnostic.cause.message}",
                        cause = diagnostic.cause,
                    )
                }
            }
        }
        return RestorePurchasesResult.Failed(failureMessage, operationalDiagnostics)
    }

    private companion object {
        const val TAG = "RestorePurchases"
    }
}

class ObserveBillingPurchaseResultsUseCase @Inject constructor(
    private val billingPort: BillingPort,
) {
    operator fun invoke(): Flow<BillingPurchaseResult> = billingPort.purchaseResults
}
