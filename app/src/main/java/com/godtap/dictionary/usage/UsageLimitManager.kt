package com.godtap.dictionary.usage

import android.content.Context
import android.util.Log
import com.godtap.dictionary.billing.BillingManager
import com.godtap.dictionary.util.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tracks user usage to enforce free tier limits
 * 
 * Free Tier Limits:
 * - 20 word lookups per day
 * - 10 TTS uses per day
 * 
 * Pro Tier:
 * - Unlimited lookups
 * - Unlimited TTS
 */
class UsageLimitManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UsageLimitManager"
        
        // Free tier daily limits
        const val FREE_DAILY_LOOKUP_LIMIT = 20
        const val FREE_DAILY_TTS_LIMIT = 10
        
        // Trigger points for upgrade prompts
        const val LOOKUP_PROMPT_THRESHOLD = 18 // Show prompt at 18/20 lookups
        const val TTS_PROMPT_THRESHOLD = 8 // Show prompt at 8/10 TTS uses
        
        @Volatile
        private var INSTANCE: UsageLimitManager? = null
        
        fun getInstance(context: Context): UsageLimitManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UsageLimitManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val preferencesManager = PreferencesManager.getInstance(context)
    private val billingManager = BillingManager.getInstance(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    /**
     * Get current date string (for daily reset)
     */
    private fun getTodayString(): String {
        return dateFormat.format(Date())
    }
    
    /**
     * Reset counters if new day
     */
    private fun checkAndResetDaily() {
        val today = getTodayString()
        val lastResetDate = preferencesManager.getLastUsageResetDate()
        
        if (lastResetDate != today) {
            Log.d(TAG, "New day detected, resetting usage counters")
            preferencesManager.resetDailyUsage(today)
        }
    }
    
    /**
     * Check if user can perform a lookup
     * Returns: true if allowed, false if limit reached
     */
    fun canLookupWord(): Boolean {
        // Pro users have unlimited lookups
        if (billingManager.isProUser()) {
            return true
        }
        
        checkAndResetDaily()
        
        val currentCount = preferencesManager.getDailyLookupCount()
        val canLookup = currentCount < FREE_DAILY_LOOKUP_LIMIT
        
        Log.d(TAG, "Lookup check: $currentCount/$FREE_DAILY_LOOKUP_LIMIT (allowed: $canLookup)")
        return canLookup
    }
    
    /**
     * Increment lookup counter
     * Returns: new count
     */
    fun incrementLookupCount(): Int {
        if (billingManager.isProUser()) {
            return -1 // Unlimited for Pro
        }
        
        checkAndResetDaily()
        val newCount = preferencesManager.incrementDailyLookupCount()
        Log.d(TAG, "Lookup count incremented: $newCount/$FREE_DAILY_LOOKUP_LIMIT")
        return newCount
    }
    
    /**
     * Check if user can use TTS
     * Returns: true if allowed, false if limit reached
     */
    fun canUseTts(): Boolean {
        // Pro users have unlimited TTS
        if (billingManager.isProUser()) {
            return true
        }
        
        checkAndResetDaily()
        
        val currentCount = preferencesManager.getDailyTtsCount()
        val canUse = currentCount < FREE_DAILY_TTS_LIMIT
        
        Log.d(TAG, "TTS check: $currentCount/$FREE_DAILY_TTS_LIMIT (allowed: $canUse)")
        return canUse
    }
    
    /**
     * Increment TTS counter
     * Returns: new count
     */
    fun incrementTtsCount(): Int {
        if (billingManager.isProUser()) {
            return -1 // Unlimited for Pro
        }
        
        checkAndResetDaily()
        val newCount = preferencesManager.incrementDailyTtsCount()
        Log.d(TAG, "TTS count incremented: $newCount/$FREE_DAILY_TTS_LIMIT")
        return newCount
    }
    
    /**
     * Get remaining lookups for today
     */
    fun getRemainingLookups(): Int {
        if (billingManager.isProUser()) {
            return Int.MAX_VALUE // Unlimited
        }
        
        checkAndResetDaily()
        val used = preferencesManager.getDailyLookupCount()
        return (FREE_DAILY_LOOKUP_LIMIT - used).coerceAtLeast(0)
    }
    
    /**
     * Get remaining TTS uses for today
     */
    fun getRemainingTts(): Int {
        if (billingManager.isProUser()) {
            return Int.MAX_VALUE // Unlimited
        }
        
        checkAndResetDaily()
        val used = preferencesManager.getDailyTtsCount()
        return (FREE_DAILY_TTS_LIMIT - used).coerceAtLeast(0)
    }
    
    /**
     * Check if should show upgrade prompt for lookups
     */
    fun shouldShowLookupUpgradePrompt(): Boolean {
        if (billingManager.isProUser()) return false
        
        val count = preferencesManager.getDailyLookupCount()
        return count >= LOOKUP_PROMPT_THRESHOLD
    }
    
    /**
     * Check if should show upgrade prompt for TTS
     */
    fun shouldShowTtsUpgradePrompt(): Boolean {
        if (billingManager.isProUser()) return false
        
        val count = preferencesManager.getDailyTtsCount()
        return count >= TTS_PROMPT_THRESHOLD
    }
    
    /**
     * Get usage summary for display
     */
    fun getUsageSummary(): UsageSummary {
        return if (billingManager.isProUser()) {
            UsageSummary(
                lookupsUsed = 0,
                lookupsLimit = Int.MAX_VALUE,
                ttsUsed = 0,
                ttsLimit = Int.MAX_VALUE,
                isPro = true
            )
        } else {
            checkAndResetDaily()
            UsageSummary(
                lookupsUsed = preferencesManager.getDailyLookupCount(),
                lookupsLimit = FREE_DAILY_LOOKUP_LIMIT,
                ttsUsed = preferencesManager.getDailyTtsCount(),
                ttsLimit = FREE_DAILY_TTS_LIMIT,
                isPro = false
            )
        }
    }
}

data class UsageSummary(
    val lookupsUsed: Int,
    val lookupsLimit: Int,
    val ttsUsed: Int,
    val ttsLimit: Int,
    val isPro: Boolean
)
