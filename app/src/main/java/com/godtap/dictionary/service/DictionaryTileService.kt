package com.godtap.dictionary.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.godtap.dictionary.R

class DictionaryTileService : TileService() {
    
    companion object {
        private const val TAG = "DictionaryTileService"
    }
    
    override fun onStartListening() {
        super.onStartListening()
        Log.d(TAG, "onStartListening() called")
        updateTileState()
    }
    
    override fun onClick() {
        super.onClick()
        Log.d(TAG, "onClick() called - Tile clicked!")
        
        val isActive = TextSelectionAccessibilityService.isRunning
        Log.d(TAG, "Service is currently ${if (isActive) "ACTIVE" else "INACTIVE"}")
        
        if (isActive) {
            // Service is running - open MainActivity for settings
            try {
                val intent = Intent(this, com.godtap.dictionary.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivityAndCollapse(intent)
                Log.d(TAG, "Service is active - opened MainActivity")
            } catch (e: Exception) {
                Log.e(TAG, "Error launching MainActivity", e)
            }
        } else {
            // Service is not running - open accessibility settings
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivityAndCollapse(intent)
                Log.d(TAG, "Service is inactive - opened accessibility settings")
            } catch (e: Exception) {
                Log.e(TAG, "Error opening accessibility settings", e)
            }
        }
        
        updateTileState()
    }
    
    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "onStopListening() called")
    }
    
    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "onTileAdded() - Tile added to Quick Settings")
    }
    
    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.d(TAG, "onTileRemoved() - Tile removed from Quick Settings")
    }
    
    private fun updateTileState() {
        val tile = qsTile
        if (tile == null) {
            Log.w(TAG, "updateTileState() - qsTile is null")
            return
        }
        
        val isActive = TextSelectionAccessibilityService.isRunning
        Log.d(TAG, "updateTileState() - Service active: $isActive")
        
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_service_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_dictionary)
        tile.updateTile()
        
        Log.d(TAG, "Tile updated with state: ${if (isActive) "ACTIVE" else "INACTIVE"}")
    }
}
