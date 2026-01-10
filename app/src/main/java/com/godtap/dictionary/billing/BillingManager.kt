package com.godtap.dictionary.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.godtap.dictionary.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Manages Google Play Billing for TapDictionary Pro subscription
 * 
 * Features:
 * - One-time purchase: TapDictionary Pro ($4.99)
 * - Handles purchase flow
 * - Verifies ownership
 * - Restores purchases
 */
class BillingManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BillingManager"
        
        // Product ID for Pro version (must match Google Play Console)
        const val PRODUCT_ID_PRO = "tapdictionary_pro"
        
        @Volatile
        private var INSTANCE: BillingManager? = null
        
        fun getInstance(context: Context): BillingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val preferencesManager = PreferencesManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null
    
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady
    
    init {
        initializeBillingClient()
        // Check cached Pro status
        _isPro.value = preferencesManager.isProUser()
    }
    
    /**
     * Initialize Google Play Billing client
     */
    private fun initializeBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                handlePurchase(billingResult, purchases)
            }
            .enablePendingPurchases()
            .build()
        
        // Connect to Google Play
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "✓ Billing client connected")
                    _isReady.value = true
                    
                    // Query product details
                    queryProductDetails()
                    
                    // Verify existing purchases
                    verifyPurchases()
                } else {
                    Log.e(TAG, "⊘ Billing setup failed: ${billingResult.debugMessage}")
                }
            }
            
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "⚠️ Billing service disconnected, will retry...")
                _isReady.value = false
                // Implement retry logic if needed
            }
        })
    }
    
    /**
     * Query available product details from Google Play
     */
    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_PRO)
                .setProductType(BillingClient.ProductType.INAPP) // One-time purchase
                .build()
        )
        
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        
        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = productDetailsList.firstOrNull()
                Log.d(TAG, "✓ Product details loaded: ${productDetails?.name} - ${productDetails?.oneTimePurchaseOfferDetails?.formattedPrice}")
            } else {
                Log.e(TAG, "⊘ Failed to query product details: ${billingResult.debugMessage}")
            }
        }
    }
    
    /**
     * Launch purchase flow for Pro version
     */
    fun launchPurchaseFlow(activity: Activity) {
        if (billingClient == null || !_isReady.value) {
            Log.e(TAG, "⊘ Billing client not ready")
            return
        }
        
        val currentProductDetails = productDetails
        if (currentProductDetails == null) {
            Log.e(TAG, "⊘ Product details not loaded")
            return
        }
        
        val offerToken = currentProductDetails.oneTimePurchaseOfferDetails?.let {
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(currentProductDetails)
                .build()
        }
        
        if (offerToken == null) {
            Log.e(TAG, "⊘ No purchase offer available")
            return
        }
        
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(offerToken))
            .build()
        
        val responseCode = billingClient?.launchBillingFlow(activity, flowParams)?.responseCode
        Log.d(TAG, "Purchase flow launched: $responseCode")
    }
    
    /**
     * Handle purchase result
     */
    private fun handlePurchase(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    // Verify and acknowledge purchase
                    acknowledgePurchase(purchase)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled purchase")
        } else {
            Log.e(TAG, "Purchase failed: ${billingResult.debugMessage}")
        }
    }
    
    /**
     * Acknowledge purchase (required by Google Play)
     */
    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            
            scope.launch {
                val result = billingClient?.acknowledgePurchase(params)
                if (result?.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "✓ Purchase acknowledged")
                    // Grant Pro access
                    grantProAccess()
                }
            }
        } else {
            // Already acknowledged, grant access
            grantProAccess()
        }
    }
    
    /**
     * Verify existing purchases on app start
     */
    fun verifyPurchases() {
        if (billingClient == null || !_isReady.value) return
        
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        
        billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPro = purchases.any { 
                    it.products.contains(PRODUCT_ID_PRO) && 
                    it.purchaseState == Purchase.PurchaseState.PURCHASED 
                }
                
                if (hasPro) {
                    Log.d(TAG, "✓ Pro purchase verified")
                    grantProAccess()
                } else {
                    Log.d(TAG, "⊘ No Pro purchase found")
                    revokeProAccess()
                }
            } else {
                Log.e(TAG, "⊘ Failed to query purchases: ${billingResult.debugMessage}")
            }
        }
    }
    
    /**
     * Grant Pro access to user
     */
    private fun grantProAccess() {
        preferencesManager.setProUser(true)
        _isPro.value = true
        Log.d(TAG, "🌟 Pro access granted")
    }
    
    /**
     * Revoke Pro access (e.g., refund)
     */
    private fun revokeProAccess() {
        preferencesManager.setProUser(false)
        _isPro.value = false
        Log.d(TAG, "Pro access revoked")
    }
    
    /**
     * Get formatted price string
     */
    fun getProPrice(): String {
        return productDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: "$4.99"
    }
    
    /**
     * Check if user is Pro (cached, instant)
     */
    fun isProUser(): Boolean {
        return _isPro.value
    }
    
    /**
     * Clean up resources
     */
    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }
}
