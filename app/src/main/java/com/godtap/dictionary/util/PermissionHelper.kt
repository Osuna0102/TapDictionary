package com.godtap.dictionary.util

import android.content.Context
import android.provider.Settings
import com.godtap.dictionary.service.TextSelectionAccessibilityService

object PermissionHelper {
    
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/${TextSelectionAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        return enabledServices.contains(service)
    }
}
