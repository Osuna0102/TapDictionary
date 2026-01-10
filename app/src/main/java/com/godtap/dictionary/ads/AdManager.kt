package com.godtap.dictionary.ads

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.godtap.dictionary.R
import com.godtap.dictionary.billing.BillingManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

/**
 * Manages AdMob advertisements for free tier users
 * 
 * Features:
 * - Banner ads in dictionary popup
 * - Only shows for non-Pro users
 * - Respects Google AdMob policies
 */
class AdManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AdManager"
        
        // Test Ad Unit ID (use real ID from AdMob console for production)
        private const val TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        
        // Production Ad Unit ID (TODO: Replace with your actual AdMob ID)
        private const val PROD_AD_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX"
        
        // Use test ads during development
        private const val USE_TEST_ADS = true // Set to false for production
        
        @Volatile
        private var INSTANCE: AdManager? = null
        
        fun getInstance(context: Context): AdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val billingManager = BillingManager.getInstance(context)
    private var isInitialized = false
    
    init {
        initializeAds()
    }
    
    /**
     * Initialize Google Mobile Ads SDK
     */
    private fun initializeAds() {
        MobileAds.initialize(context) { initializationStatus ->
            isInitialized = true
            Log.d(TAG, "✓ AdMob initialized: ${initializationStatus.adapterStatusMap}")
        }
    }
    
    /**
     * Create and load banner ad for dictionary popup
     * Returns AdView if user is free tier, null if Pro
     */
    fun createBannerAdForPopup(container: ViewGroup): AdView? {
        // Don't show ads for Pro users
        if (billingManager.isProUser()) {
            Log.d(TAG, "User is Pro, skipping ad")
            return null
        }
        
        if (!isInitialized) {
            Log.w(TAG, "⚠️ AdMob not initialized yet")
            return null
        }
        
        // Create AdView
        val adView = AdView(context).apply {
            adUnitId = if (USE_TEST_ADS) TEST_AD_UNIT_ID else PROD_AD_UNIT_ID
            setAdSize(AdSize.BANNER) // 320x50
        }
        
        // Set layout parameters
        val layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        adView.layoutParams = layoutParams
        
        // Load ad
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        
        Log.d(TAG, "✓ Banner ad created and loading")
        return adView
    }
    
    /**
     * Check if ads should be shown (user is not Pro)
     */
    fun shouldShowAds(): Boolean {
        return !billingManager.isProUser()
    }
    
    /**
     * Destroy ad view to prevent memory leaks
     */
    fun destroyAdView(adView: AdView?) {
        adView?.destroy()
    }
}
