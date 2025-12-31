package com.godtap.dictionary.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.godtap.dictionary.R

class DictionaryTileService : TileService() {
    
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }
    
    override fun onClick() {
        super.onClick()
        
        val isActive = TextSelectionAccessibilityService.isRunning
        
        // Note: We can't actually toggle the accessibility service from here
        // This tile just shows the status
        // User needs to toggle in Accessibility Settings
        
        updateTileState()
    }
    
    private fun updateTileState() {
        val tile = qsTile ?: return
        val isActive = TextSelectionAccessibilityService.isRunning
        
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_service_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_dictionary)
        tile.updateTile()
    }
}
