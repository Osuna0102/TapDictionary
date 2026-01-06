package com.godtap.dictionary.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Manages app filtering for the dictionary service
 * Allows users to enable/disable the dictionary for specific apps
 */
class AppFilterManager(context: Context) {
    
    companion object {
        private const val TAG = "AppFilterManager"
        private const val PREFS_NAME = "app_filter_prefs"
        private const val KEY_FILTER_MODE = "filter_mode"
        private const val KEY_ALLOWED_APPS = "allowed_apps"
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        
        // Filter modes
        const val MODE_ALL_APPS = 0        // Dictionary works in all apps (default)
        const val MODE_WHITELIST = 1       // Only works in apps on the allowed list
        const val MODE_BLACKLIST = 2       // Works in all apps except those on the blocked list
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Get the current filter mode
     */
    fun getFilterMode(): Int {
        return prefs.getInt(KEY_FILTER_MODE, MODE_ALL_APPS)
    }
    
    /**
     * Set the filter mode
     */
    fun setFilterMode(mode: Int) {
        prefs.edit().putInt(KEY_FILTER_MODE, mode).apply()
        Log.d(TAG, "Filter mode changed to: $mode")
    }
    
    /**
     * Get the list of allowed apps (whitelist)
     */
    fun getAllowedApps(): Set<String> {
        return prefs.getStringSet(KEY_ALLOWED_APPS, emptySet()) ?: emptySet()
    }
    
    /**
     * Get the list of blocked apps (blacklist)
     */
    fun getBlockedApps(): Set<String> {
        return prefs.getStringSet(KEY_BLOCKED_APPS, emptySet()) ?: emptySet()
    }
    
    /**
     * Add an app to the allowed list (whitelist)
     */
    fun addAllowedApp(packageName: String) {
        val currentSet = getAllowedApps().toMutableSet()
        currentSet.add(packageName)
        prefs.edit().putStringSet(KEY_ALLOWED_APPS, currentSet).apply()
        Log.d(TAG, "Added to allowed apps: $packageName")
    }
    
    /**
     * Remove an app from the allowed list
     */
    fun removeAllowedApp(packageName: String) {
        val currentSet = getAllowedApps().toMutableSet()
        currentSet.remove(packageName)
        prefs.edit().putStringSet(KEY_ALLOWED_APPS, currentSet).apply()
        Log.d(TAG, "Removed from allowed apps: $packageName")
    }
    
    /**
     * Add an app to the blocked list (blacklist)
     */
    fun addBlockedApp(packageName: String) {
        val currentSet = getBlockedApps().toMutableSet()
        currentSet.add(packageName)
        prefs.edit().putStringSet(KEY_BLOCKED_APPS, currentSet).apply()
        Log.d(TAG, "Added to blocked apps: $packageName")
    }
    
    /**
     * Remove an app from the blocked list
     */
    fun removeBlockedApp(packageName: String) {
        val currentSet = getBlockedApps().toMutableSet()
        currentSet.remove(packageName)
        prefs.edit().putStringSet(KEY_BLOCKED_APPS, currentSet).apply()
        Log.d(TAG, "Removed from blocked apps: $packageName")
    }
    
    /**
     * Toggle an app in the allowed list
     * @return true if app is now in the list, false if removed
     */
    fun toggleAllowedApp(packageName: String): Boolean {
        val currentSet = getAllowedApps().toMutableSet()
        val isInList = if (currentSet.contains(packageName)) {
            currentSet.remove(packageName)
            false
        } else {
            currentSet.add(packageName)
            true
        }
        prefs.edit().putStringSet(KEY_ALLOWED_APPS, currentSet).apply()
        Log.d(TAG, "Toggled allowed app $packageName: $isInList")
        return isInList
    }
    
    /**
     * Toggle an app in the blocked list
     * @return true if app is now in the list, false if removed
     */
    fun toggleBlockedApp(packageName: String): Boolean {
        val currentSet = getBlockedApps().toMutableSet()
        val isInList = if (currentSet.contains(packageName)) {
            currentSet.remove(packageName)
            false
        } else {
            currentSet.add(packageName)
            true
        }
        prefs.edit().putStringSet(KEY_BLOCKED_APPS, currentSet).apply()
        Log.d(TAG, "Toggled blocked app $packageName: $isInList")
        return isInList
    }
    
    /**
     * Check if the dictionary should be enabled for this app
     * @param packageName The package name to check
     * @return true if dictionary should work for this app
     */
    fun shouldProcessApp(packageName: String): Boolean {
        return when (getFilterMode()) {
            MODE_ALL_APPS -> true
            MODE_WHITELIST -> {
                val allowed = getAllowedApps().contains(packageName)
                Log.d(TAG, "Whitelist mode: $packageName allowed=$allowed")
                allowed
            }
            MODE_BLACKLIST -> {
                val blocked = getBlockedApps().contains(packageName)
                Log.d(TAG, "Blacklist mode: $packageName blocked=$blocked")
                !blocked
            }
            else -> true
        }
    }
    
    /**
     * Clear all app filters
     */
    fun clearAllFilters() {
        prefs.edit()
            .remove(KEY_ALLOWED_APPS)
            .remove(KEY_BLOCKED_APPS)
            .putInt(KEY_FILTER_MODE, MODE_ALL_APPS)
            .apply()
        Log.d(TAG, "Cleared all app filters")
    }
}
