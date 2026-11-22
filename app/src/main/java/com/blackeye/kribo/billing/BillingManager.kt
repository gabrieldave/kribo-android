package com.blackeye.kribo.billing

import android.app.Activity
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BillingUiState(
    val connected: Boolean = false,
    val formattedPrice: String? = null,
    val owned: Boolean = false,
    val message: String? = null,
)

/**
 * Base Play Billing manager.
 *
 * - Never hardcodes prices: the UI renders [BillingUiState.formattedPrice] from Play.
 * - Refreshes purchases and acknowledges them on every onResume (see MainActivity).
 * - Fails gracefully when Play is not available (debug builds, sideloaded APKs, no network).
 */
class BillingManager(context: Context) {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private var billingClient: BillingClient? = null
    private var proProduct: ProductDetails? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when {
            result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null -> {
                purchases.forEach(::handlePurchase)
                queryPurchases()
            }

            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED -> Unit

            else -> _state.update { it.copy(message = result.debugMessage.takeIf(String::isNotBlank)) }
        }
    }

    private val connectionListener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(result: BillingResult) {
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _state.update { it.copy(connected = true) }
                queryProducts()
                queryPurchases()
            } else {
                _state.update { it.copy(connected = false) }
            }
        }

        override fun onBillingServiceDisconnected() {
            _state.update { it.copy(connected = false) }
        }
    }

    @Synchronized
    fun start() {
        val client = billingClient ?: buildClient().also { billingClient = it }
        if (!client.isReady) {
            client.startConnection(connectionListener)
        }
    }

    /** Safe to call from onResume; reconnects if needed and re-queries entitlements. */
    fun refresh() {
        val client = billingClient
        if (client == null) {
            start()
            return
        }
        if (!client.isReady) {
            client.startConnection(connectionListener)
            return
        }
        queryProducts()
        queryPurchases()
    }

    fun launchPurchase(activity: Activity) {
        val client = billingClient ?: return
        val product = proProduct ?: return
        if (!client.isReady) return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    private fun buildClient(): BillingClient =
        BillingClient.newBuilder(appContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build(),
            )
            .enableAutoServiceReconnection()
            .build()

    private fun queryProducts() {
        val client = billingClient ?: return
        if (!client.isReady) return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, productDetailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val product = productDetailsResult.productDetailsList
                    .firstOrNull { it.productId == PRO_PRODUCT_ID }
                proProduct = product
                _state.update {
                    it.copy(formattedPrice = product?.oneTimePurchaseOfferDetails?.formattedPrice)
                }
            }
        }
    }

    private fun queryPurchases() {
        val client = billingClient ?: return
        if (!client.isReady) return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        it.products.contains(PRO_PRODUCT_ID)
                }
                purchases
                    .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach(::acknowledge)
                _state.update { it.copy(owned = owned, connected = true) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.products.contains(PRO_PRODUCT_ID)) return
        _state.update { it.copy(owned = true) }
        acknowledge(purchase)
    }

    private fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _state.update { it.copy(owned = true) }
            }
        }
    }

    companion object {
        const val PRO_PRODUCT_ID = "kribo_pro"
    }
}
