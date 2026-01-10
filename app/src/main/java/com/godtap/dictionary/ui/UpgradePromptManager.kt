package com.godtap.dictionary.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.godtap.dictionary.R
import com.godtap.dictionary.billing.BillingManager
import com.godtap.dictionary.usage.UsageLimitManager

/**
 * Shows upgrade prompts to encourage Pro purchase
 * 
 * Triggers:
 * - After 20 lookups/day (limit reached)
 * - After 10 TTS uses/day (limit reached)
 * - After viewing 5 ads
 * - Manual upgrade button
 */
object UpgradePromptManager {
    
    /**
     * Show limit reached dialog
     */
    fun showLimitReachedDialog(
        activity: Activity,
        limitType: LimitType,
        onUpgradeClicked: () -> Unit
    ) {
        val billingManager = BillingManager.getInstance(activity)
        val price = billingManager.getProPrice()
        
        val (title, message) = when (limitType) {
            LimitType.LOOKUP -> Pair(
                "Daily Lookup Limit Reached",
                "You've reached your daily limit of ${UsageLimitManager.FREE_DAILY_LOOKUP_LIMIT} word lookups.\n\n" +
                "Upgrade to Pro for unlimited lookups, ad-free experience, and advanced features!"
            )
            LimitType.TTS -> Pair(
                "Daily TTS Limit Reached",
                "You've reached your daily limit of ${UsageLimitManager.FREE_DAILY_TTS_LIMIT} text-to-speech uses.\n\n" +
                "Upgrade to Pro for unlimited pronunciation playback and more!"
            )
            LimitType.ADS -> Pair(
                "Remove Ads",
                "Enjoying TapDictionary?\n\n" +
                "Upgrade to Pro to remove ads and support development!"
            )
        }
        
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Upgrade to Pro ($price)") { _, _ ->
                onUpgradeClicked()
            }
            .setNegativeButton("Maybe Later", null)
            .setCancelable(true)
            .show()
    }
    
    /**
     * Show approaching limit warning
     */
    fun showApproachingLimitWarning(
        activity: Activity,
        remainingCount: Int,
        limitType: LimitType,
        onUpgradeClicked: () -> Unit
    ) {
        val message = when (limitType) {
            LimitType.LOOKUP -> "You have $remainingCount lookups remaining today."
            LimitType.TTS -> "You have $remainingCount TTS uses remaining today."
            else -> return
        }
        
        val billingManager = BillingManager.getInstance(activity)
        val price = billingManager.getProPrice()
        
        AlertDialog.Builder(activity)
            .setTitle("⚠️ Almost at Daily Limit")
            .setMessage("$message\n\nUpgrade to Pro ($price) for unlimited access!")
            .setPositiveButton("Upgrade Now") { _, _ ->
                onUpgradeClicked()
            }
            .setNegativeButton("Continue", null)
            .setCancelable(true)
            .show()
    }
    
    /**
     * Show Pro features dialog (when user taps "Upgrade" button)
     */
    fun showProFeaturesDialog(
        activity: Activity,
        onUpgradeClicked: () -> Unit
    ) {
        val billingManager = BillingManager.getInstance(activity)
        val price = billingManager.getProPrice()
        
        val features = """
            ✅ Unlimited word lookups
            ✅ Unlimited text-to-speech
            ✅ Ad-free experience
            ✅ Advanced dictionaries (250,000+ entries)
            ✅ Example sentences
            ✅ JLPT level indicators
            ✅ Word frequency analytics
            ✅ Export to Anki flashcards
            ✅ Custom dictionaries
            ✅ Priority support
            ✅ Early access to new features
        """.trimIndent()
        
        AlertDialog.Builder(activity)
            .setTitle("🌟 TapDictionary Pro")
            .setMessage("Unlock all premium features:\n\n$features")
            .setPositiveButton("Upgrade ($price)") { _, _ ->
                onUpgradeClicked()
            }
            .setNegativeButton("Not Now", null)
            .setCancelable(true)
            .show()
    }
    
    enum class LimitType {
        LOOKUP,
        TTS,
        ADS
    }
}
