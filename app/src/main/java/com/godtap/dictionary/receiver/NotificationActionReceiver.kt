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
        const val ACTION_TOGGLE_UNDERLINE = "com.godtap.dictionary.ACTION_TOGGLE_UNDERLINE"
        private const val PREFS_NAME = "dictionary_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_UNDERLINE_ENABLED = "underline_enabled"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_SERVICE -> {
                Log.d(TAG, "Toggle service action received")
                toggleService(context)
            }
            ACTION_TOGGLE_UNDERLINE -> {
                Log.d(TAG, "Toggle underline action received")
                toggleUnderline(context)
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
    
    private fun toggleUnderline(context: Context) {
        val prefs = getPreferences(context)
        val currentState = prefs.getBoolean(KEY_UNDERLINE_ENABLED, false)
        val newState = !currentState
        
        Log.d(TAG, "Toggling underline: $currentState -> $newState")
        
        // Save new state
        prefs.edit().putBoolean(KEY_UNDERLINE_ENABLED, newState).apply()
        
        // Update underline state via service
        TextSelectionAccessibilityService.updateUnderlineNotification(context, newState)
    }
    
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
