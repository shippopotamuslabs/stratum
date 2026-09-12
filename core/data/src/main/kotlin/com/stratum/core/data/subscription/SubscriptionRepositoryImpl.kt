package com.stratum.core.data.subscription

import com.stratum.domain.auth.AuthManager
import com.stratum.domain.auth.AuthState
import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.subscription.BillingPort
import com.stratum.domain.subscription.EntitlementRepository
import com.stratum.domain.subscription.EntitlementState
import com.stratum.domain.subscription.PlayEntitlementSyncPort
import com.stratum.domain.subscription.SubscriptionPurchase
import com.stratum.domain.subscription.resolveEntitlement
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val billingPort: BillingPort,
    private val complimentaryAccessStore: ComplimentaryAccessStore,
    private val authManager: AuthManager,
    private val loggingPort: LoggingPort,
    private val playEntitlementSync: PlayEntitlementSyncPort,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : EntitlementRepository {

    override fun observeEntitlement(): Flow<EntitlementState> =
        authManager.authState.flatMapLatest { auth ->
            when (auth) {
                AuthState.Loading,
                AuthState.Unauthenticated,
                is AuthState.Error -> flowOf(
                    resolveEntitlement(auth, complimentary = false, purchases = emptyList()),
                )
                is AuthState.Authenticated -> combine(
                    complimentaryAccessStore.observeComplimentaryAccess(auth.user.uid),
                    billingPort.activePurchases.onEach { purchases ->
                        syncActivePurchaseTokens(purchases)
                    },
                ) { complimentary, purchases ->
                    resolveEntitlement(auth, complimentary, purchases)
                }
            }
        }.distinctUntilChanged()
            .onEach { state ->
                loggingPort.debug(TAG, "Entitlement changed: $state")
            }

    override suspend fun refreshEntitlement(): Result<EntitlementState> {
        val restoreResult = billingPort.restorePurchases()
        if (restoreResult.isFailure) {
            return Result.failure(
                restoreResult.exceptionOrNull() ?: IllegalStateException("Failed to restore purchases"),
            )
        }
        return runCatching {
            val auth = authManager.authState.value
            val purchases = billingPort.activePurchases.first()
            val complimentary = if (auth is AuthState.Authenticated) {
                complimentaryAccessStore.getComplimentaryAccess(auth.user.uid)
            } else {
                false
            }
            if (auth is AuthState.Authenticated) {
                syncActivePurchaseTokens(purchases)
            }
            resolveEntitlement(auth, complimentary, purchases)
        }
    }

    private fun syncActivePurchaseTokens(purchases: List<SubscriptionPurchase>) {
        for (purchase in purchases) {
            val token = purchase.purchaseToken
            if (token.isBlank()) continue
            applicationScope.launch {
                playEntitlementSync.syncPurchaseToken(token)
                    .onFailure { error ->
                        loggingPort.error(TAG, "Play entitlement sync failed", error)
                    }
            }
        }
    }

    companion object {
        private const val TAG = "SubscriptionRepo"
    }
}
