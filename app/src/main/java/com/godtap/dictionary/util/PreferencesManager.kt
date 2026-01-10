package com.godtap.dictionary.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages SharedPreferences for app settings and usage tracking
 */
class PreferencesManager private constructor(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "TapDictionaryPrefs"
        
        // Keys
        private const val KEY_IS_PRO = "is_pro_user"
        private const val KEY_DAILY_LOOKUP_COUNT = "daily_lookup_count"
        private const val KEY_DAILY_TTS_COUNT = "daily_tts_count"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_AD_VIEW_COUNT = "ad_view_count"
        private const val KEY_SHOW_UNDERLINE = "show_underline"
        
        @Volatile
        private var INSTANCE: PreferencesManager? = null
        
        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // ====================
    // Pro Status
    // ====================
    
    fun isProUser(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_PRO, false)
    }
    
    fun setProUser(isPro: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_IS_PRO, isPro).apply()
    }
    
    // ====================
    // Daily Usage Tracking
    // ====================
    
    fun getDailyLookupCount(): Int {
        return sharedPreferences.getInt(KEY_DAILY_LOOKUP_COUNT, 0)
    }
    
    fun incrementDailyLookupCount(): Int {
        val current = getDailyLookupCount()
        val newCount = current + 1
        sharedPreferences.edit().putInt(KEY_DAILY_LOOKUP_COUNT, newCount).apply()
        return newCount
    }
    
    fun getDailyTtsCount(): Int {
        return sharedPreferences.getInt(KEY_DAILY_TTS_COUNT, 0)
    }
    
    fun incrementDailyTtsCount(): Int {
        val current = getDailyTtsCount()
        val newCount = current + 1
        sharedPreferences.edit().putInt(KEY_DAILY_TTS_COUNT, newCount).apply()
        return newCount
    }
    
    fun getLastUsageResetDate(): String {
        return sharedPreferences.getString(KEY_LAST_RESET_DATE, "") ?: ""
    }
    
    fun resetDailyUsage(newDate: String) {
        sharedPreferences.edit()
            .putInt(KEY_DAILY_LOOKUP_COUNT, 0)
            .putInt(KEY_DAILY_TTS_COUNT, 0)
            .putString(KEY_LAST_RESET_DATE, newDate)
            .apply()
    }
    
    // ====================
    // Ad Tracking
    // ====================
    
    fun getAdViewCount(): Int {
        return sharedPreferences.getInt(KEY_AD_VIEW_COUNT, 0)
    }
    
    fun incrementAdViewCount(): Int {
        val current = getAdViewCount()
        val newCount = current + 1
        sharedPreferences.edit().putInt(KEY_AD_VIEW_COUNT, newCount).apply()
        return newCount
    }
    
    // ====================
    // App Settings
    // ====================
    
    fun isUnderlineEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_UNDERLINE, false)
    }
    
    fun setUnderlineEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_UNDERLINE, enabled).apply()
    }
}
