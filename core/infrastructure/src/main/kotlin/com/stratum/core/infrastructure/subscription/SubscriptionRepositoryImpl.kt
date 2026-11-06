package com.stratum.core.infrastructure.subscription

import com.stratum.application.auth.AuthManager
import com.stratum.domain.auth.AuthState
import com.stratum.domain.di.ApplicationScope
import com.stratum.application.subscription.BillingPort
import com.stratum.application.subscription.EntitlementRepository
import com.stratum.domain.subscription.ComplimentaryAccess
import com.stratum.domain.subscription.EntitlementDiagnostic
import com.stratum.domain.subscription.EntitlementState
import com.stratum.application.subscription.PlayEntitlementSyncPort
import com.stratum.domain.subscription.SubscriptionPurchase
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SubscriptionRepositoryImpl @Inject constructor(
    private val billingPort: BillingPort,
    private val complimentaryAccessStore: ComplimentaryAccessStore,
    private val authManager: AuthManager,
    private val playEntitlementSync: PlayEntitlementSyncPort,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : EntitlementRepository {

    // Diagnostics must never be silently dropped for capacity: the single app-boundary
    // drain starts at process start and sync failures are rare.
    private val diagnosticsChannel = Channel<EntitlementDiagnostic>(Channel.UNLIMITED)

    override fun observeEntitlementDiagnostics(): Flow<EntitlementDiagnostic> =
        diagnosticsChannel.receiveAsFlow()

    override fun observeEntitlement(): Flow<EntitlementState> =
        authManager.authState.flatMapLatest { auth ->
            when (auth) {
                AuthState.Loading,
                AuthState.Unauthenticated,
                is AuthState.Error -> flowOf(
                    EntitlementState.resolve(auth, complimentary = ComplimentaryAccess.NotGranted, purchases = emptyList()),
                )
                is AuthState.Authenticated -> combine(
                    complimentaryAccessStore.observeComplimentaryAccess(auth.user.uid),
                    billingPort.activePurchases.onEach { purchases ->
                        syncActivePurchaseTokens(purchases)
                    },
                ) { complimentary, purchases ->
                    EntitlementState.resolve(auth, complimentary, purchases)
                }
            }
        }.distinctUntilChanged()

    override suspend fun refreshEntitlement(): EntitlementState {
        billingPort.restorePurchases()
        val auth = authManager.authState.value
        val purchases = billingPort.activePurchases.first()
        val complimentary = if (auth is AuthState.Authenticated) {
            complimentaryAccessStore.getComplimentaryAccess(auth.user.uid)
        } else {
            ComplimentaryAccess.NotGranted
        }
        if (auth is AuthState.Authenticated) {
            syncActivePurchaseTokens(purchases)
        }
        val state = EntitlementState.resolve(auth, complimentary, purchases)
        // A one-shot refresh must answer definitively: when the complimentary read
        // failed and no Play purchase entitles, Loading would tell the caller the
        // restore found nothing. Fail instead so the caller can show a retry (STR-280).
        check(state !is EntitlementState.Loading || auth !is AuthState.Authenticated) {
            "Entitlement refresh could not verify complimentary access"
        }
        return state
    }

    private fun syncActivePurchaseTokens(purchases: List<SubscriptionPurchase>) {
        for (purchase in purchases) {
            val token = purchase.purchaseToken
            if (token.isBlank()) continue
            applicationScope.launch {
                try {
                    playEntitlementSync.syncPurchaseToken(token)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    diagnosticsChannel.trySend(EntitlementDiagnostic.PlaySyncFailed(error))
                }
            }
        }
    }
}
