package com.stratum.core.infrastructure.subscription

import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.stratum.application.auth.AuthManager
import com.stratum.domain.auth.AuthState
import com.stratum.domain.di.ApplicationScope
import com.stratum.domain.logging.LoggingPort
import com.stratum.application.subscription.BillingPort
import com.stratum.domain.subscription.BillingPurchaseResult
import com.stratum.domain.subscription.SubscriptionCatalog
import com.stratum.domain.subscription.SubscriptionProduct
import com.stratum.domain.subscription.SubscriptionPurchase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages the connection to Google Play Billing, queries subscription product details,
 * launches the purchase bottom sheet, and handles purchase acknowledgments.
 */
@Singleton
class PlayBillingDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val billingHostContext: BillingHostContext,
    private val authManager: AuthManager,
    private val loggingPort: LoggingPort,
    @ApplicationScope private val scope: CoroutineScope,
) : BillingPort, PurchasesUpdatedListener {

    private val connectionMutex = Mutex()
    private var startConnectionInFlight = false

    private val _activePurchases = MutableStateFlow<List<SubscriptionPurchase>>(emptyList())
    override val activePurchases: Flow<List<SubscriptionPurchase>> = _activePurchases.asStateFlow()

    private val _purchaseResults = MutableSharedFlow<BillingPurchaseResult>(extraBufferCapacity = 16)
    override val purchaseResults: Flow<BillingPurchaseResult> = _purchaseResults.asSharedFlow()

    private var cachedProductDetails: Map<String, ProductDetails> = emptyMap()

    private val pendingPurchasesParams =
        PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .enablePrepaidPlans()
            .build()

    private val billingClient: BillingClient =
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingPurchasesParams)
            .enableAutoServiceReconnection()
            .build()

    init {
        scope.launch {
            runCatching {
                if (ensureConnected()) {
                    queryAndPublishActivePurchases()
                }
            }.onFailure { error ->
                loggingPort.warning(TAG, "Initial purchase query failed: ${error.message}")
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        emitPurchaseResult(billingResult, purchases)
        scope.launch {
            runCatching { queryAndPublishActivePurchases() }
                .onFailure { error ->
                    loggingPort.warning(TAG, "Failed refreshing purchases after update: ${error.message}")
                }
        }
    }

    override suspend fun querySubscriptionProducts(): Result<List<SubscriptionProduct>> = runCatching {
        if (!ensureConnected()) {
            error("Failed to connect to Google Play Billing service")
        }

        val productList = listOf(
            SubscriptionCatalog.ANNUAL_PRODUCT_ID,
            SubscriptionCatalog.MONTHLY_PRODUCT_ID,
        ).map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val productDetailsResult = billingClient.queryProductDetails(params)
        if (productDetailsResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            error("Error querying product details: ${productDetailsResult.billingResult.debugMessage}")
        }

        val detailsList = productDetailsResult.productDetailsList ?: emptyList()
        cachedProductDetails = detailsList.associateBy { it.productId }

        detailsList.mapNotNull(PlaySubscriptionMapper::toDomain)
            .filter { SubscriptionCatalog.isKnownProductId(it.productId) }
            .sortedBy { product ->
                if (product.productId == SubscriptionCatalog.ANNUAL_PRODUCT_ID) 0 else 1
            }
    }

    override suspend fun launchBillingFlow(product: SubscriptionProduct): Result<Unit> = runCatching {
        if (!ensureConnected()) {
            error("Failed to connect to Google Play Billing service")
        }
        if (product.offerToken.isBlank()) {
            error("Offer token is blank for ${product.productId}")
        }

        val uid = (authManager.authState.value as? AuthState.Authenticated)?.user?.uid
            ?: error("Google Play purchase requires a signed-in Firebase user.")
        val obfuscatedAccountId = uid.take(OBFUSCATED_ACCOUNT_ID_MAX_LENGTH)
        require(obfuscatedAccountId.isNotBlank()) {
            "Google Play purchase requires a signed-in Firebase user."
        }

        val details = cachedProductDetails[product.productId]
            ?: querySubscriptionProducts().getOrThrow().let {
                cachedProductDetails[product.productId]
                    ?: error("Product details not available for ${product.productId}")
            }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(product.offerToken)
                .build(),
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .setObfuscatedAccountId(obfuscatedAccountId)
            .build()

        val activity = billingHostContext.requireActivity()
        val launchResult = billingClient.launchBillingFlow(activity, billingFlowParams)
        if (launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
            error("Failed to launch billing flow: ${launchResult.debugMessage} (code: ${launchResult.responseCode})")
        }
    }

    override suspend fun restorePurchases(): Result<Unit> = runCatching {
        queryAndPublishActivePurchases()
    }

    private fun emitPurchaseResult(billingResult: BillingResult, purchases: List<Purchase>?) {
        val result = when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.USER_CANCELED -> BillingPurchaseResult.Canceled
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                when {
                    list.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } ->
                        BillingPurchaseResult.Purchased
                    list.any { it.purchaseState == Purchase.PurchaseState.PENDING } ->
                        BillingPurchaseResult.Pending
                    else -> BillingPurchaseResult.Failed(
                        message = billingResult.debugMessage.ifBlank { "No purchases in billing update" },
                        responseCode = billingResult.responseCode,
                    )
                }
            }
            else -> BillingPurchaseResult.Failed(
                message = billingResult.debugMessage.ifBlank { "Billing flow failed" },
                responseCode = billingResult.responseCode,
            )
        }
        if (!_purchaseResults.tryEmit(result)) {
            loggingPort.warning(TAG, "Dropped billing purchase result")
        }
    }

    private suspend fun queryAndPublishActivePurchases() {
        if (!ensureConnected()) {
            error("Failed to connect to Google Play Billing service")
        }

        val queried = querySubsPurchases()
        acknowledgeUnacknowledged(queried)
        val refreshed = querySubsPurchases()
        _activePurchases.value = refreshed
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .filter { purchase -> purchase.products.any(SubscriptionCatalog::isKnownProductId) }
            .map(PlaySubscriptionMapper::toDomain)
    }

    private suspend fun querySubsPurchases(): List<Purchase> {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            error("Failed querying active purchases: ${result.billingResult.debugMessage}")
        }
        return result.purchasesList
    }

    private suspend fun acknowledgeUnacknowledged(purchases: List<Purchase>) {
        val toAcknowledge = purchases.filter { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                !purchase.isAcknowledged &&
                purchase.products.any(SubscriptionCatalog::isKnownProductId)
        }
        for (purchase in toAcknowledge) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            var ackResult = billingClient.acknowledgePurchase(ackParams)
            if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                loggingPort.warning(TAG, "Acknowledge failed (will retry) orderId=${purchase.orderId}")
                ackResult = billingClient.acknowledgePurchase(ackParams)
            }
            if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                loggingPort.debug(TAG, "Acknowledged purchase orderId=${purchase.orderId}")
            } else {
                loggingPort.warning(TAG, "Acknowledge retry failed orderId=${purchase.orderId}")
            }
        }
    }

    private suspend fun ensureConnected(): Boolean = connectionMutex.withLock {
        if (billingClient.isReady) return@withLock true
        if (startConnectionInFlight) return@withLock false
        startConnectionInFlight = true
        try {
            awaitConnectionLocked()
        } finally {
            startConnectionInFlight = false
        }
    }

    private suspend fun awaitConnectionLocked(): Boolean =
        suspendCancellableCoroutine { continuation ->
            val resumed = AtomicBoolean(false)
            fun resumeOnce(value: Boolean) {
                if (resumed.compareAndSet(false, true)) {
                    continuation.resume(value)
                }
            }
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        loggingPort.info(TAG, "BillingClient connected successfully")
                        resumeOnce(true)
                    } else {
                        loggingPort.warning(
                            TAG,
                            "BillingClient setup failed: ${billingResult.debugMessage} (code: ${billingResult.responseCode})",
                        )
                        resumeOnce(false)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    loggingPort.warning(TAG, "BillingClient disconnected, scheduling reconnect")
                    resumeOnce(false)
                    scope.launch {
                        delay(RECONNECT_DELAY_MS)
                        runCatching {
                            if (ensureConnected()) {
                                queryAndPublishActivePurchases()
                            }
                        }.onFailure { error ->
                            loggingPort.warning(TAG, "Reconnect purchase refresh failed: ${error.message}")
                        }
                    }
                }
            })
        }

    companion object {
        private const val TAG = "PlayBilling"
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val OBFUSCATED_ACCOUNT_ID_MAX_LENGTH = 64
    }
}
