package com.godtap.dictionary.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import com.godtap.dictionary.service.TextSelectionAccessibilityService

/**
 * Broadcast receiver for notification actions
 * Handles enable/disable toggle from notification
 */
class NotificationActionReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "NotificationAction"
        const val ACTION_TOGGLE_SERVICE = "com.godtap.dictionary.ACTION_TOGGLE_SERVICE"
        private const val PREFS_NAME = "dictionary_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_SERVICE -> {
                Log.d(TAG, "Toggle service action received")
                toggleService(context)
            }
        }
    }
    
    private fun toggleService(context: Context) {
        val prefs = getPreferences(context)
        val currentState = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        val newState = !currentState
        
        Log.d(TAG, "Toggling service: $currentState -> $newState")
        
        // Save new state
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, newState).apply()
        
        // Update notification
        TextSelectionAccessibilityService.updateNotification(context, newState)
    }
    
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
